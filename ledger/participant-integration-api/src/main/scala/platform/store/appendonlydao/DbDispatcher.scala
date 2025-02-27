// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.appendonlydao

import java.sql.Connection
import java.util.concurrent.{Executor, Executors, TimeUnit}

import com.codahale.metrics.{InstrumentedExecutorService, Timer}
import com.daml.ledger.api.health.{HealthStatus, ReportsHealth}
import com.daml.ledger.resources.ResourceOwner
import com.daml.logging.LoggingContext.withEnrichedLoggingContext
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.{DatabaseMetrics, Metrics}
import com.daml.platform.configuration.ServerRole
import com.google.common.util.concurrent.ThreadFactoryBuilder
import javax.sql.DataSource

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

private[platform] final class DbDispatcher private (
    connectionProvider: JdbcConnectionProvider,
    executor: Executor,
    overallWaitTimer: Timer,
    overallExecutionTimer: Timer,
) extends ReportsHealth {

  private val logger = ContextualizedLogger.get(this.getClass)

  private val executionContext = ExecutionContext.fromExecutor(executor)

  override def currentHealth(): HealthStatus =
    connectionProvider.currentHealth()

  /** Runs an SQL statement in a dedicated Executor. The whole block will be run in a single database transaction.
    *
    * The isolation level by default is the one defined in the JDBC driver, it can be however overridden per query on
    * the Connection. See further details at: https://docs.oracle.com/cd/E19830-01/819-4721/beamv/index.html
    */
  def executeSql[T](databaseMetrics: DatabaseMetrics)(
      sql: Connection => T
  )(implicit loggingContext: LoggingContext): Future[T] =
    withEnrichedLoggingContext("metric" -> databaseMetrics.name) { implicit loggingContext =>
      val startWait = System.nanoTime()
      Future {
        val waitNanos = System.nanoTime() - startWait
        logger.trace(s"Waited ${(waitNanos / 1e6).toLong} ms to acquire connection")
        databaseMetrics.waitTimer.update(waitNanos, TimeUnit.NANOSECONDS)
        overallWaitTimer.update(waitNanos, TimeUnit.NANOSECONDS)
        val startExec = System.nanoTime()
        try {
          // Actual execution
          val result = connectionProvider.runSQL(databaseMetrics)(sql)
          result
        } catch {
          case NonFatal(e) =>
            logger.error("Exception while executing SQL query. Rolled back.", e)
            throw e
          // fatal errors don't make it for some reason to the setUncaughtExceptionHandler
          case t: Throwable =>
            logger.error("Fatal error!", t)
            throw t
        } finally {
          // decouple metrics updating from sql execution above
          try {
            val execNanos = System.nanoTime() - startExec
            logger.trace(s"Executed query in ${(execNanos / 1e6).toLong} ms")
            databaseMetrics.executionTimer.update(execNanos, TimeUnit.NANOSECONDS)
            overallExecutionTimer.update(execNanos, TimeUnit.NANOSECONDS)
          } catch {
            case NonFatal(e) =>
              logger.error("Got an exception while updating timer metrics. Ignoring.", e)
          }
        }
      }(executionContext)
    }
}

private[platform] object DbDispatcher {
  private val logger = ContextualizedLogger.get(this.getClass)

  def owner(
      dataSource: DataSource,
      serverRole: ServerRole,
      connectionPoolSize: Int,
      connectionTimeout: FiniteDuration,
      metrics: Metrics,
  )(implicit loggingContext: LoggingContext): ResourceOwner[DbDispatcher] =
    for {
      hikariDataSource <- new HikariDataSourceOwner(
        dataSource,
        serverRole,
        connectionPoolSize,
        connectionPoolSize,
        connectionTimeout,
        Some(metrics.registry),
      )
      connectionProvider <- DataSourceConnectionProvider.owner(hikariDataSource)
      threadPoolName = s"daml.index.db.threadpool.connection.${serverRole.threadPoolSuffix}"
      executor <- ResourceOwner.forExecutorService(() =>
        new InstrumentedExecutorService(
          Executors.newFixedThreadPool(
            connectionPoolSize,
            new ThreadFactoryBuilder()
              .setNameFormat(s"$threadPoolName-%d")
              .setUncaughtExceptionHandler((_, e) =>
                logger.error("Uncaught exception in the SQL executor.", e)
              )
              .build(),
          ),
          metrics.registry,
          threadPoolName,
        )
      )
    } yield new DbDispatcher(
      connectionProvider = connectionProvider,
      executor = executor,
      overallWaitTimer = metrics.daml.index.db.waitAll,
      overallExecutionTimer = metrics.daml.index.db.execAll,
    )
}
