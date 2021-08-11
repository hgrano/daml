// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.http.perf.scenario

import com.daml.http.perf.scenario.MultiUserQueryScenario._
import io.gatling.core.Predef._
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Random

private[scenario] trait HasRandomCurrency {
  private val rng = new scala.util.Random(123456789)
  final val currencies = List("USD", "GBP", "EUR", "CHF", "AUD")

  def randomCurrency(): String = {
    this.synchronized { rng.shuffle(currencies).head }
  }
}

object MultiUserQueryScenario {
  sealed trait RunMode { def name: String }
  case object PopulateCache extends RunMode { val name = "populateCache" }
  case object FetchByKey extends RunMode { val name = "fetchByKey" }
  case object FetchByQuery extends RunMode { val name = "fetchByQuery" }
}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class MultiUserQueryScenario
    extends Simulation
    with SimulationConfig
    with HasRandomAmount
    with HasRandomCurrency {

  private val logger = LoggerFactory.getLogger(getClass)

  private def getEnvValueAsInt(key: String, default: Int) = {
    sys.env.get(key).map(_.toInt).getOrElse(default)
  }

  //hardcoded for now , needs to be made configurable on cli
  private val numRecords = getEnvValueAsInt("NUM_RECORDS", 100000)
  private val numQueries = getEnvValueAsInt("NUM_QUERIES", 1000)
  private val numWriters = getEnvValueAsInt("NUM_WRITERS", 10)
  private val numReaders = getEnvValueAsInt("NUM_READERS", 100)
  private val runModeString = sys.env.getOrElse("RUN_MODE", "populateCache")

  def runMode(mode: String): RunMode = {
    mode match {
      case PopulateCache.name => PopulateCache
      case FetchByKey.name => FetchByKey
      case FetchByQuery.name => FetchByQuery
    }
  }

  private val msgIds = Range(0, numRecords).toList
  private val msgIdIter = msgIds.iterator

  def incrementalId = msgIdIter.next()
  def queryId: Int = Random.shuffle(msgIds).head

  private val createRequest =
    http("CreateCommand")
      .post("/v1/create")
      .body(StringBody("""{
  "templateId": "LargeAcs:KeyedIou",
  "payload": {
    "id": "${id}",
    "issuer": "Alice",
    "owner": "Alice",
    "currency": "${currency}",
    "amount": "${amount}",
    "observers": []
  }
}"""))

  private val queryRequest =
    http("SyncIdQueryRequest")
      .post("/v1/query")
      .body(StringBody("""{
          "templateIds": ["LargeAcs:KeyedIou"],
          "query": {"id": "${id}"}
      }"""))

  private val writeScn = scenario("Write100kContracts")
    .repeat(numRecords / numWriters) {
      feed(
        Iterator.continually(
          Map(
            "id" -> String.valueOf(incrementalId),
            "currency" -> randomCurrency(),
            "amount" -> randomAmount(),
          )
        )
      )
        .exec(createRequest)
    }

  private def idReadScn(numRecords: Int = numRecords, numReaders: Int = numReaders) = {
    val iter = msgIds.iterator
    scenario("MultipleReadersQueryScenario")
      .repeat(numRecords / numReaders) {
        feed(Iterator.continually(Map("id" -> String.valueOf(iter.next()))))
          .exec(queryRequest)
      }
  }

  private val fetchRequest =
    http("SyncFetchRequest")
      .post("/v1/fetch")
      .body(StringBody("""{
          "templateId": "LargeAcs:KeyedIou",
          "key": {
            "_1": "Alice",
            "_2": "${id}"
          }
      }"""))

  private val currencyQueryRequest =
    http("SyncCurrQueryRequest")
      .post("/v1/query")
      .body(StringBody("""{
          "templateIds": ["LargeAcs:KeyedIou"],
          "query": {"currency": "${currency}"}
      }"""))

  // Scenario to fetch a subset of the ACS population
  private def currQueryScn(
      numIterations: Int = numQueries / numReaders,
      curr: () => String = () => randomCurrency(),
  ) =
    scenario("MultipleReadersCurrQueryScenario")
      .repeat(numIterations) {
        feed(Iterator.continually(Map("currency" -> curr())))
          .exec(currencyQueryRequest)
      }

  //fetch by key scenario
  private val fetchScn = scenario("MultipleReadersFetchScenario")
    .repeat(numQueries / numReaders) {
      feed(Iterator.continually(Map("id" -> String.valueOf(queryId))))
        .exec(fetchRequest)
    }

  def getPopulationBuilder(runMode: RunMode): PopulationBuilder = {
    runMode match {
      case PopulateCache =>
        val currIter = currencies.iterator
        writeScn
          .inject(atOnceUsers(numWriters))
          .andThen(
            currQueryScn(numIterations = currencies.size, () => currIter.next())
              .inject(
                nothingFor(2.seconds),
                atOnceUsers(1),
              )
          )
      case FetchByKey =>
        fetchScn.inject(
          nothingFor(5.seconds),
          atOnceUsers(numReaders / 2),
          rampUsers(numReaders / 2).during(10.seconds),
        )
      case FetchByQuery =>
        currQueryScn(numIterations = numQueries / numReaders).inject(
          nothingFor(2.seconds),
          atOnceUsers(numReaders),
        )
    }
  }

  logger.debug(s"Scenarios defined ${idReadScn()} ${currQueryScn()} $writeScn")
  setUp(
    getPopulationBuilder(runMode(runModeString))
  ).protocols(httpProtocol)
}
