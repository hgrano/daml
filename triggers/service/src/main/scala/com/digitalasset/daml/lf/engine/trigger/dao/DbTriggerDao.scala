// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.engine.trigger.dao

import java.util.UUID
import cats.effect.{ContextShift, IO}
import cats.syntax.functor._
import com.daml.daml_lf_dev.DamlLf
import com.daml.http.dbbackend.ConnectionPool.PoolSize._
import com.daml.http.dbbackend.ConnectionPool.PoolSize
import com.daml.http.dbbackend.{ConnectionPool, JdbcConfig}
import com.daml.ledger.api.refinements.ApiTypes.{ApplicationId, Party}
import com.daml.lf.archive.{ArchivePayloadParser, Dar}
import com.daml.lf.data.Ref.{Identifier, PackageId}
import com.daml.lf.engine.trigger.RunningTrigger
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.{Get, log}
import doobie.{Fragment, Put}
import scalaz.Tag
import java.io.{Closeable, IOException}

import com.daml.auth.middleware.api.Tagged.{AccessToken, RefreshToken}
import com.daml.doobie.logging.Slf4jLogHandler
import javax.sql.DataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.language.existentials
import scala.util.control.NonFatal

abstract class DbTriggerDao protected (
    dataSource: DataSource with Closeable,
    xa: ConnectionPool.T,
    migrationsDir: String,
) extends RunningTriggerDao {

  protected implicit def uuidPut: Put[UUID]
  protected implicit def uuidGet: Get[UUID]

  implicit val partyPut: Put[Party] = Tag.subst(implicitly[Put[String]])

  implicit val partyGet: Get[Party] = Tag.subst(implicitly[Get[String]])

  implicit val appIdPut: Put[ApplicationId] = Tag.subst(implicitly[Put[String]])

  implicit val appIdGet: Get[ApplicationId] = Tag.subst(implicitly[Get[String]])

  implicit val accessTokenPut: Put[AccessToken] = Tag.subst(implicitly[Put[String]])

  implicit val accessTokenGet: Get[AccessToken] = Tag.subst(implicitly[Get[String]])

  implicit val refreshTokenPut: Put[RefreshToken] = Tag.subst(implicitly[Put[String]])

  implicit val refreshTokenGet: Get[RefreshToken] = Tag.subst(implicitly[Get[String]])

  implicit val identifierPut: Put[Identifier] = implicitly[Put[String]].contramap(_.toString)

  implicit val identifierGet: Get[Identifier] =
    implicitly[Get[String]].map(Identifier.assertFromString(_))

  private implicit val logHandler: log.LogHandler = Slf4jLogHandler(classOf[DbTriggerDao])

  private[this] val flywayMigrations = new DbFlywayMigrations(dataSource, migrationsDir)

  private def createTables: ConnectionIO[Unit] =
    flywayMigrations.migrate()

  // NOTE(RJR) Interpolation in `sql` literals:
  // Doobie provides a `Put` typeclass that allows us to interpolate values of various types in our
  // SQL query strings. This includes basic types like `String` and `UUID` as well as unary case
  // classes wrapping these types. Doobie also does some formatting of these
  // values, e.g. single quotes around `String` and `UUID` values. This is NOT the case if you use
  // `Fragment.const` which will try to use a raw string as a SQL query.

  private def insertRunningTrigger(t: RunningTrigger): ConnectionIO[Unit] = {
    val insert: Fragment = sql"""
        insert into running_triggers
          (trigger_instance, trigger_party, full_trigger_name, access_token, refresh_token, application_id)
        values
          (${t.triggerInstance}, ${t.triggerParty}, ${t.triggerName}, ${t.triggerAccessToken}, ${t.triggerRefreshToken}, ${t.triggerApplicationId})
      """
    insert.update.run.void
  }

  private def queryRunningTrigger(triggerInstance: UUID): ConnectionIO[Option[RunningTrigger]] = {
    val select: Fragment = sql"""
        select trigger_instance, full_trigger_name, trigger_party, application_id, access_token, refresh_token from running_triggers
        where trigger_instance = $triggerInstance
      """
    select
      .query[(UUID, Identifier, Party, ApplicationId, Option[AccessToken], Option[RefreshToken])]
      .map(RunningTrigger.tupled)
      .option
  }

  private def setRunningTriggerToken(
      triggerInstance: UUID,
      accessToken: AccessToken,
      refreshToken: Option[RefreshToken],
  ) = {
    val update: Fragment =
      sql"""
        update running_triggers
        set access_token = $accessToken, refresh_token = $refreshToken
        where trigger_instance = $triggerInstance
      """
    update.update.run.void
  }

  // trigger_instance is the primary key on running_triggers so this deletes
  // at most one row. Return whether or not it deleted.
  private def deleteRunningTrigger(triggerInstance: UUID): ConnectionIO[Boolean] = {
    val delete = sql"delete from running_triggers where trigger_instance = $triggerInstance"
    delete.update.run.map(_ == 1)
  }

  private def selectRunningTriggers(party: Party): ConnectionIO[Vector[UUID]] = {
    val select: Fragment = sql"""
        select trigger_instance from running_triggers
        where trigger_party = $party
      """
    // We do not use an `order by` clause because we sort the UUIDs afterwards using Scala's
    // comparison of UUIDs (which is different to Postgres).
    select.query[UUID].to[Vector]
  }

  // Insert a package to the `dalfs` table. Do nothing if the package already exists.
  // We specify this in the `insert` since `packageId` is the primary key on the table.
  protected def insertPackage(
      packageId: PackageId,
      pkg: DamlLf.ArchivePayload,
  ): ConnectionIO[Unit]

  private def selectPackages: ConnectionIO[List[(String, Array[Byte])]] = {
    val select: Fragment = sql"select * from dalfs order by package_id"
    select.query[(String, Array[Byte])].to[List]
  }

  private def parsePackage(
      pkgIdString: String,
      pkgPayload: Array[Byte],
  ): Either[String, (PackageId, DamlLf.ArchivePayload)] =
    for {
      pkgId <- PackageId.fromString(pkgIdString)
      payload <- ArchivePayloadParser
        .fromByteArray(pkgPayload)
        .left
        .map(err => s"Failed to parse package with id $pkgId.\n" + err.toString)
    } yield (pkgId, payload)

  private def selectAllTriggers: ConnectionIO[Vector[RunningTrigger]] = {
    val select: Fragment = sql"""
      select trigger_instance, full_trigger_name, trigger_party, application_id, access_token, refresh_token from running_triggers order by trigger_instance
    """
    select
      .query[(UUID, Identifier, Party, ApplicationId, Option[AccessToken], Option[RefreshToken])]
      .map(RunningTrigger.tupled)
      .to[Vector]
  }

  // Drop all tables and other objects associated with the database.
  // Only used between tests for now.
  private def dropTables: ConnectionIO[Unit] = flywayMigrations.clean()

  final class DatabaseError(errorContext: String, e: Throwable)
      extends RuntimeException(errorContext + "\n" + e.toString)

  private def run[T](query: ConnectionIO[T], errorContext: String = "")(implicit
      ec: ExecutionContext
  ): Future[T] = {
    query.transact(xa).unsafeToFuture().recoverWith { case NonFatal(e) =>
      Future.failed(new DatabaseError(errorContext, e))
    }
  }

  override def addRunningTrigger(t: RunningTrigger)(implicit ec: ExecutionContext): Future[Unit] =
    run(insertRunningTrigger(t))

  override def getRunningTrigger(triggerInstance: UUID)(implicit
      ec: ExecutionContext
  ): Future[Option[RunningTrigger]] =
    run(queryRunningTrigger(triggerInstance))

  override def updateRunningTriggerToken(
      triggerInstance: UUID,
      accessToken: AccessToken,
      refreshToken: Option[RefreshToken],
  )(implicit ec: ExecutionContext): Future[Unit] =
    run(setRunningTriggerToken(triggerInstance, accessToken, refreshToken))

  override def removeRunningTrigger(triggerInstance: UUID)(implicit
      ec: ExecutionContext
  ): Future[Boolean] =
    run(deleteRunningTrigger(triggerInstance))

  override def listRunningTriggers(
      party: Party
  )(implicit ec: ExecutionContext): Future[Vector[UUID]] = {
    // Note(RJR): Postgres' ordering of UUIDs is different to Scala/Java's.
    // We sort them after the query to be consistent with the ordering when not using a database.
    run(selectRunningTriggers(party)).map(_.sorted)
  }

  // Write packages to the `dalfs` table so we can recover state after a shutdown.
  override def persistPackages(
      dar: Dar[(PackageId, DamlLf.ArchivePayload)]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    import cats.implicits._ // needed for traverse
    val insertAll = dar.all.traverse_((insertPackage _).tupled)
    run(insertAll)
  }

  class InvalidPackage(s: String) extends RuntimeException(s"Invalid package: $s")

  def readPackages(implicit
      ec: ExecutionContext
  ): Future[List[(PackageId, DamlLf.ArchivePayload)]] = {
    import cats.implicits._ // needed for traverse
    run(selectPackages, "Failed to read packages from database").flatMap(
      _.traverse { case (pkgId, pkg) =>
        parsePackage(pkgId, pkg)
          .fold(err => Future.failed(new InvalidPackage(err)), Future.successful(_))
      }
    )
  }

  def readRunningTriggers(implicit ec: ExecutionContext): Future[Vector[RunningTrigger]] =
    run(selectAllTriggers, "Failed to read running triggers from database")

  def initialize(implicit ec: ExecutionContext): Future[Unit] =
    run(createTables, "Failed to initialize database.")

  private[trigger] def destroy(implicit ec: ExecutionContext): Future[Unit] =
    run(dropTables, "Failed to remove database objects.")

  private[trigger] def destroyPermanently(): Try[Unit] =
    Try(dataSource.close())

  @throws[IOException]
  override def close() =
    destroyPermanently().fold(
      {
        case e: IOException => throw e
        case e => throw new IOException(e)
      },
      identity,
    )
}

final class DbTriggerDaoPostgreSQL(dataSource: DataSource with Closeable, xa: ConnectionPool.T)
    extends DbTriggerDao(dataSource, xa, "postgres") {
  import doobie.postgres.implicits._

  override val uuidPut: Put[UUID] = implicitly
  override val uuidGet: Get[UUID] = implicitly

  override def insertPackage(
      packageId: PackageId,
      pkg: DamlLf.ArchivePayload,
  ): ConnectionIO[Unit] = {
    val insert: Fragment = sql"""
      insert into dalfs values (${packageId.toString}, ${pkg.toByteArray}) on conflict do nothing
    """
    insert.update.run.void
  }
}

final class DbTriggerDaoOracle(dataSource: DataSource with Closeable, xa: ConnectionPool.T)
    extends DbTriggerDao(dataSource, xa, "oracle") {
  override val uuidPut: Put[UUID] = Put[String].contramap(_.toString)
  override val uuidGet: Get[UUID] = Get[String].map(UUID.fromString(_))

  override def insertPackage(
      packageId: PackageId,
      pkg: DamlLf.ArchivePayload,
  ): ConnectionIO[Unit] = {
    val insert: Fragment = sql"""
      insert /*+  ignore_row_on_dupkey_index ( dalfs ( package_id ) ) */
      into dalfs values (${packageId.toString}, ${pkg.toByteArray})
    """
    insert.update.run.void
  }
}

object DbTriggerDao {

  private val supportedJdbcDrivers
      : Map[String, (DataSource with Closeable, ConnectionPool.T) => DbTriggerDao] = Map(
    "org.postgresql.Driver" -> ((d, xa) => new DbTriggerDaoPostgreSQL(d, xa)),
    "oracle.jdbc.OracleDriver" -> ((d, xa) => new DbTriggerDaoOracle(d, xa)),
  )

  def supportedJdbcDriverNames(available: Set[String]): Set[String] =
    supportedJdbcDrivers.keySet intersect available

  def apply(c: JdbcConfig, poolSize: PoolSize = Production)(implicit
      ec: ExecutionContext
  ): DbTriggerDao = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)
    val (ds, conn) = ConnectionPool.connect(c, poolSize)
    val driver = supportedJdbcDrivers
      .get(c.driver)
      .getOrElse(throw new IllegalArgumentException(s"Unsupported JDBC driver ${c.driver}"))
    driver(ds, conn)
  }
}
