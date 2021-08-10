// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend.h2

import java.sql.Connection
import java.time.Instant

import anorm.{NamedParameter, SQL, SqlStringInterpolation}
import anorm.SqlParser.get
import com.daml.ledger.api.v1.command_completion_service.CompletionStreamResponse
import com.daml.ledger.offset.Offset
import com.daml.lf.data.Ref
import com.daml.logging.LoggingContext
import com.daml.platform.store.appendonlydao.events.{ContractId, Key}
import com.daml.platform.store.backend.EventStorageBackend.FilterParams
import com.daml.platform.store.backend.common.{
  AppendOnlySchema,
  CommonStorageBackend,
  EventStorageBackendTemplate,
  EventStrategy,
  InitHookDataSourceProxy,
  TemplatedStorageBackend,
}
import com.daml.platform.store.backend.{
  DBLockStorageBackend,
  DataSourceStorageBackend,
  DbDto,
  StorageBackend,
  common,
}
import javax.sql.DataSource

import scala.util.matching.Regex

private[backend] object H2StorageBackend
    extends StorageBackend[AppendOnlySchema.Batch]
    with CommonStorageBackend[AppendOnlySchema.Batch]
    with EventStorageBackendTemplate {

  override def reset(connection: Connection): Unit = {
    SQL("""set referential_integrity false;
        |truncate table configuration_entries;
        |truncate table package_entries;
        |truncate table parameters;
        |truncate table participant_command_completions;
        |truncate table participant_command_submissions;
        |truncate table participant_events_divulgence;
        |truncate table participant_events_create;
        |truncate table participant_events_consuming_exercise;
        |truncate table participant_events_non_consuming_exercise;
        |truncate table parties;
        |truncate table party_entries;
        |set referential_integrity true;""".stripMargin)
      .execute()(connection)
    ()
  }

  override def duplicateKeyError: String = "Unique index or primary key violation"

  val SQL_INSERT_COMMAND: String =
    """merge into participant_command_submissions pcs
      |using dual on deduplication_key = {deduplicationKey}
      |when not matched then
      |  insert (deduplication_key, deduplicate_until)
      |  values ({deduplicationKey}, {deduplicateUntil})
      |when matched and pcs.deduplicate_until < {submittedAt} then
      |  update set deduplicate_until={deduplicateUntil}""".stripMargin

  def upsertDeduplicationEntry(
      key: String,
      submittedAt: Instant,
      deduplicateUntil: Instant,
  )(connection: Connection): Int =
    SQL(SQL_INSERT_COMMAND)
      .on(
        "deduplicationKey" -> key,
        "submittedAt" -> submittedAt,
        "deduplicateUntil" -> deduplicateUntil,
      )
      .executeUpdate()(connection)

  override def batch(dbDtos: Vector[DbDto]): AppendOnlySchema.Batch =
    H2Schema.schema.prepareData(dbDtos)

  override def insertBatch(connection: Connection, batch: AppendOnlySchema.Batch): Unit =
    H2Schema.schema.executeUpdate(batch, connection)

  def commandCompletions(
      startExclusive: Offset,
      endInclusive: Offset,
      applicationId: Ref.ApplicationId,
      parties: Set[Ref.Party],
  )(connection: Connection): List[CompletionStreamResponse] =
    TemplatedStorageBackend.commandCompletions(
      startExclusive = startExclusive,
      endInclusive = endInclusive,
      applicationId = applicationId,
      submittersInPartiesClause = arrayIntersectionWhereClause("submitters", parties),
    )(connection)

  def activeContractWithArgument(readers: Set[Ref.Party], contractId: ContractId)(
      connection: Connection
  ): Option[StorageBackend.RawContract] =
    TemplatedStorageBackend.activeContractWithArgument(
      treeEventWitnessesWhereClause = arrayIntersectionWhereClause("tree_event_witnesses", readers),
      contractId = contractId,
    )(connection)

  def activeContractWithoutArgument(readers: Set[Ref.Party], contractId: ContractId)(
      connection: Connection
  ): Option[String] =
    TemplatedStorageBackend.activeContractWithoutArgument(
      treeEventWitnessesWhereClause = arrayIntersectionWhereClause("tree_event_witnesses", readers),
      contractId = contractId,
    )(connection)

  def contractKey(readers: Set[Ref.Party], key: Key)(
      connection: Connection
  ): Option[ContractId] =
    TemplatedStorageBackend.contractKey(
      flatEventWitnesses = columnPrefix =>
        arrayIntersectionWhereClause(s"$columnPrefix.flat_event_witnesses", readers),
      key = key,
    )(connection)

  // TODO FIXME: this is for postgres not for H2
  def maxEventSeqIdForOffset(offset: Offset)(connection: Connection): Option[Long] = {
    import com.daml.platform.store.Conversions.OffsetToStatement
    // This query could be: "select max(event_sequential_id) from participant_events where event_offset <= ${range.endInclusive}"
    // however tests using PostgreSQL 12 with tens of millions of events have shown that the index
    // on `event_offset` is not used unless we _hint_ at it by specifying `order by event_offset`
    SQL"select max(event_sequential_id) from participant_events where event_offset <= $offset group by event_offset order by event_offset desc limit 1"
      .as(get[Long](1).singleOpt)(connection)
  }

  object H2EventStrategy extends EventStrategy {
    override def filteredEventWitnessesClause(
        witnessesColumnName: String,
        parties: Set[Ref.Party],
    ): (String, List[NamedParameter]) =
      (
        s"array_intersection($witnessesColumnName, {partiesArrayfewc})",
        List("partiesArrayfewc" -> parties.view.map(_.toString).toArray),
      )

    override def submittersArePartiesClause(
        submittersColumnName: String,
        parties: Set[Ref.Party],
    ): (String, List[NamedParameter]) =
      (
        s"(${arrayIntersectionWhereClause(submittersColumnName, parties)})",
        Nil,
      )

    override def witnessesWhereClause(
        witnessesColumnName: String,
        filterParams: FilterParams,
    ): (String, List[NamedParameter]) = {
      val (wildCardClause, wildCardParams) = filterParams.wildCardParties match {
        case wildCardParties if wildCardParties.isEmpty => (Nil, Nil)
        case wildCardParties =>
          (
            List(s"(${arrayIntersectionWhereClause(witnessesColumnName, wildCardParties)})"),
            Nil,
          )
      }
      val (partiesTemplatesClauses, partiesTemplatesParams) =
        filterParams.partiesAndTemplates.iterator.zipWithIndex
          .map { case ((parties, templateIds), index) =>
            (
              s"( (${arrayIntersectionWhereClause(witnessesColumnName, parties)}) AND (template_id = ANY({templateIdsArraywwc$index})) )",
              List[NamedParameter](
                s"templateIdsArraywwc$index" -> templateIds.view.map(_.toString).toArray
              ),
            )
          }
          .toList
          .unzip
      (
        (wildCardClause ::: partiesTemplatesClauses).mkString("(", " OR ", ")"),
        wildCardParams ::: partiesTemplatesParams.flatten,
      )
    }
  }

  override def eventStrategy: common.EventStrategy = H2EventStrategy

  // TODO append-only: remove as part of ContractStorageBackend consolidation, use the data-driven one
  private def arrayIntersectionWhereClause(arrayColumn: String, parties: Set[Ref.Party]): String =
    if (parties.isEmpty)
      "false"
    else
      parties.view.map(p => s"array_contains($arrayColumn, '$p')").mkString("(", " or ", ")")

  override def createDataSource(
      jdbcUrl: String,
      dataSourceConfig: DataSourceStorageBackend.DataSourceConfig,
      connectionInitHook: Option[Connection => Unit],
  )(implicit loggingContext: LoggingContext): DataSource = {
    val h2DataSource = new org.h2.jdbcx.JdbcDataSource()

    // H2 (org.h2.jdbcx.JdbcDataSource) does not support setting the user/password within the jdbcUrl, so remove
    // those properties from the url if present and set them separately. Note that Postgres and Oracle support
    // user/password in the URLs, so we don't bother exposing user/password configs separately from the url just for h2
    // which is anyway not supported for production. (This also helps run canton h2 participants that set user and
    // password.)
    val (urlNoUserNoPassword, user, password) = extractUserPasswordAndRemoveFromUrl(jdbcUrl)
    user.foreach(h2DataSource.setUser)
    password.foreach(h2DataSource.setPassword)
    h2DataSource.setUrl(urlNoUserNoPassword)

    InitHookDataSourceProxy(h2DataSource, connectionInitHook.toList)
  }

  def extractUserPasswordAndRemoveFromUrl(
      jdbcUrl: String
  ): (String, Option[String], Option[String]) = {
    def setKeyValueAndRemoveFromUrl(url: String, key: String): (String, Option[String]) = {
      val regex = new Regex(s".*(;${key}=([^;]*)).*")
      url match {
        case regex(keyAndValue, value) =>
          (url.replace(keyAndValue, ""), Some(value))
        case _ => (url, None)
      }
    }

    val (urlNoUser, user) = setKeyValueAndRemoveFromUrl(jdbcUrl, "user")
    val (urlNoUserNoPassword, password) = setKeyValueAndRemoveFromUrl(urlNoUser, "password")
    (urlNoUserNoPassword, user, password)
  }

  override def tryAcquire(
      lockId: DBLockStorageBackend.LockId,
      lockMode: DBLockStorageBackend.LockMode,
  )(connection: Connection): Option[DBLockStorageBackend.Lock] =
    throw new UnsupportedOperationException("db level locks are not supported for H2")

  override def release(lock: DBLockStorageBackend.Lock)(connection: Connection): Boolean =
    throw new UnsupportedOperationException("db level locks are not supported for H2")

  override def lock(id: Int): DBLockStorageBackend.LockId =
    throw new UnsupportedOperationException("db level locks are not supported for H2")

  override def dbLockSupported: Boolean = false
}
