// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.engine.script.test

import java.io.File
import java.nio.file.Files
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import com.daml.bazeltools.BazelRunfiles._
import com.daml.cliopts.Logging.LogEncoder
import com.daml.grpc.adapter.{AkkaExecutionSequencerPool, ExecutionSequencerFactory}
import com.daml.http.util.Logging.{InstanceUUID, instanceUUIDLogCtx}
import com.daml.http.{HttpService, StartSettings, nonrepudiation}
import com.daml.jwt.domain.DecodedJwt
import com.daml.jwt.{HMAC256Verifier, JwtSigner}
import com.daml.ledger.api.auth.{AuthServiceJWT, AuthServiceJWTCodec, AuthServiceJWTPayload}
import com.daml.ledger.api.domain.LedgerId
import com.daml.ledger.api.refinements.ApiTypes.ApplicationId
import com.daml.ledger.api.testing.utils.{
  OwnedResource,
  SuiteResource,
  SuiteResourceManagementAroundAll,
  Resource => TestResource,
}
import com.daml.ledger.api.tls.TlsConfiguration
import com.daml.ledger.resources.{Resource, ResourceContext, ResourceOwner}
import com.daml.lf.archive.{Dar, DarDecoder}
import com.daml.lf.data.Ref._
import com.daml.lf.engine.script._
import com.daml.lf.engine.script.ledgerinteraction.{
  JsonLedgerClient,
  ScriptLedgerClient,
  ScriptTimeMode,
}
import com.daml.lf.iface.EnvironmentInterface
import com.daml.lf.iface.reader.InterfaceReader
import com.daml.lf.language.Ast.Package
import com.daml.lf.speedy.SValue
import com.daml.lf.speedy.SValue._
import com.daml.lf.value.json.ApiCodecCompressed
import com.daml.logging.LoggingContextOf
import com.daml.platform.apiserver.services.GrpcClientResource
import com.daml.platform.common.LedgerIdMode
import com.daml.platform.sandbox.config.SandboxConfig
import com.daml.platform.sandbox.services.TestCommands
import com.daml.platform.sandbox.{AbstractSandboxFixture, SandboxServer}
import com.daml.ports.Port
import io.grpc.Channel
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import scalaz.syntax.traverse._
import scalaz.{-\/, \/-}
import spray.json._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import com.daml.metrics.{Metrics, MetricsReporter}
import com.codahale.metrics.MetricRegistry

trait JsonApiFixture
    extends AbstractSandboxFixture
    with SuiteResource[(SandboxServer, Channel, ServerBinding)] {
  self: Suite =>

  override protected def darFile = new File(rlocation("daml-script/test/script-test.dar"))

  protected val darFileNoLedger = new File(rlocation("daml-script/test/script-test-no-ledger.dar"))

  protected def server: SandboxServer = suiteResource.value._1

  override protected def serverPort: Port = server.port
  override protected def channel: Channel = suiteResource.value._2
  override protected def config: SandboxConfig =
    super.config
      .copy(
        ledgerIdMode = LedgerIdMode.Static(LedgerId("MyLedger")),
        authService = Some(
          AuthServiceJWT(
            HMAC256Verifier(secret).valueOr(err =>
              sys.error(s"Failed to create HMAC256 verifierd $err")
            )
          )
        ),
      )
  def httpPort: Int = suiteResource.value._3.localAddress.getPort
  protected val secret: String = "secret"

  // We have to use a different actorsystem for the JSON API since package reloading
  // blocks everything so it will timeout as sandbox cannot make progress simultaneously.
  private val jsonApiActorSystem: ActorSystem = ActorSystem("json-api")
  private val jsonApiMaterializer: Materializer = Materializer(system)
  private val jsonApiExecutionSequencerFactory: ExecutionSequencerFactory =
    new AkkaExecutionSequencerPool(poolName = "json-api", actorCount = 1)
  private val jsonAccessTokenFile = Files.createTempFile("http-jsn", "auth")

  override protected def afterAll(): Unit = {
    jsonApiExecutionSequencerFactory.close()
    materializer.shutdown()
    Await.result(jsonApiActorSystem.terminate(), 30.seconds)
    try {
      Files.delete(jsonAccessTokenFile)
    } catch {
      case NonFatal(_) =>
    }
    super.afterAll()
  }

  protected def getToken(actAs: List[String], readAs: List[String], admin: Boolean): String = {
    val payload = AuthServiceJWTPayload(
      ledgerId = Some("MyLedger"),
      participantId = None,
      exp = None,
      applicationId = Some("foobar"),
      actAs = actAs,
      readAs = readAs,
      admin = admin,
    )
    val header = """{"alg": "HS256", "typ": "JWT"}"""
    val jwt = DecodedJwt[String](header, AuthServiceJWTCodec.writeToString(payload))
    JwtSigner.HMAC256.sign(jwt, secret) match {
      case -\/(e) => throw new IllegalStateException(e.toString)
      case \/-(a) => a.value
    }
  }

  override protected lazy val suiteResource
      : TestResource[(SandboxServer, Channel, ServerBinding)] = {
    implicit val context: ResourceContext = ResourceContext(system.dispatcher)
    new OwnedResource[ResourceContext, (SandboxServer, Channel, ServerBinding)](
      for {
        jdbcUrl <- database
          .fold[ResourceOwner[Option[String]]](ResourceOwner.successful(None))(
            _.map(info => Some(info.jdbcUrl))
          )
        server <- SandboxServer.owner(config.copy(jdbcUrl = jdbcUrl))
        channel <- GrpcClientResource.owner(server.port)
        httpService <- new ResourceOwner[ServerBinding] {
          override def acquire()(implicit context: ResourceContext): Resource[ServerBinding] = {
            implicit val lc: LoggingContextOf[InstanceUUID] = instanceUUIDLogCtx(
              identity(_)
            )
            Resource[ServerBinding] {
              Files.write(jsonAccessTokenFile, getToken(List(), List(), false).getBytes())
              val config = new StartSettings.Default {
                override val ledgerHost = "localhost"
                override val ledgerPort = server.port.value
                override val address = "localhost"
                override val httpPort = 0
                override val portFile = None
                override val tlsConfig = TlsConfiguration(enabled = false, None, None, None)
                override val wsConfig = None
                override val accessTokenFile = Some(jsonAccessTokenFile)
                override val allowNonHttps = true
                override val nonRepudiation = nonrepudiation.Configuration.Cli.Empty
                override val logLevel = None
                override val logEncoder = LogEncoder.Plain
                override val metricsReporter: Option[MetricsReporter] = None
                override val metricsReportingInterval: FiniteDuration = 10.seconds
              }
              HttpService
                .start(config)(
                  jsonApiActorSystem,
                  jsonApiMaterializer,
                  jsonApiExecutionSequencerFactory,
                  jsonApiActorSystem.dispatcher,
                  lc,
                  metrics = new Metrics(new MetricRegistry()),
                )
                .flatMap({
                  case -\/(e) => Future.failed(new IllegalStateException(e.toString))
                  case \/-(a) => Future.successful(a._1)
                })
            }((binding: ServerBinding) => binding.unbind().map(_ => ()))
          }
        }
      } yield (server, channel, httpService)
    )
  }
}

final class JsonApiIt
    extends AsyncWordSpec
    with TestCommands
    with JsonApiFixture
    with Matchers
    with SuiteResourceManagementAroundAll
    with TryValues {

  private def readDar(file: File): (Dar[(PackageId, Package)], EnvironmentInterface) = {
    val dar = DarDecoder.assertReadArchiveFromFile(file)
    val ifaceDar = dar.map(pkg => InterfaceReader.readInterface(() => \/-(pkg))._2)
    val envIface = EnvironmentInterface.fromReaderInterfaces(ifaceDar)
    (dar, envIface)
  }

  val (dar, envIface) = readDar(darFile)
  val (darNoLedger, envIfaceNoLedger) = readDar(darFileNoLedger)

  private def getClients(
      parties: List[String] = List(party),
      defaultParty: Option[String] = None,
      admin: Boolean = false,
      applicationId: Option[ApplicationId] = None,
      envIface: EnvironmentInterface = envIface,
  ) = {
    // We give the default participant some nonsense party so the checks for party mismatch fail
    // due to the mismatch and not because the token does not allow inferring a party
    val defaultParticipant =
      ApiParameters(
        "http://localhost",
        httpPort,
        Some(getToken(defaultParty.toList, List(), true)),
        applicationId,
      )
    val partyMap = parties.map(p => (Party.assertFromString(p), Participant(p))).toMap
    val participantMap = parties
      .map(p =>
        (
          Participant(p),
          ApiParameters(
            "http://localhost",
            httpPort,
            Some(getToken(List(p), List(), admin)),
            applicationId,
          ),
        )
      )
      .toMap
    val participantParams = Participants(Some(defaultParticipant), participantMap, partyMap)
    Runner.jsonClients(participantParams, envIface)
  }

  private def getMultiPartyClients(
      parties: List[String],
      readAs: List[String] = List(),
      applicationId: Option[ApplicationId] = None,
      envIface: EnvironmentInterface = envIface,
  ) = {
    // We give the default participant some nonsense party so the checks for party mismatch fail
    // due to the mismatch and not because the token does not allow inferring a party
    val defaultParticipant =
      ApiParameters(
        "http://localhost",
        httpPort,
        Some(getToken(parties, readAs, true)),
        applicationId,
      )
    val participantParams = Participants(Some(defaultParticipant), Map.empty, Map.empty)
    Runner.jsonClients(participantParams, envIface)
  }

  private val party = "Alice"

  private def run(
      clients: Participants[ScriptLedgerClient],
      name: QualifiedName,
      inputValue: Option[JsValue] = Some(JsString(party)),
      dar: Dar[(PackageId, Package)] = dar,
  ): Future[SValue] = {
    val scriptId = Identifier(dar.main._1, name)
    Runner.run(dar, scriptId, inputValue, clients, ScriptTimeMode.WallClock)
  }

  "Daml Script over JSON API" can {
    "Basic" should {
      "return 42" in {
        for {
          clients <- getClients()
          result <- run(clients, QualifiedName.assertFromString("ScriptTest:jsonBasic"))
        } yield {
          assert(result == SInt64(42))
        }
      }
    }
    "CreateAndExercise" should {
      "return 42" in {
        for {
          clients <- getClients()
          result <- run(clients, QualifiedName.assertFromString("ScriptTest:jsonCreateAndExercise"))
        } yield {
          assert(result == SInt64(42))
        }
      }
    }
    "ExerciseByKey" should {
      "return equal contract ids" in {
        for {
          clients <- getClients()
          result <- run(clients, QualifiedName.assertFromString("ScriptTest:jsonExerciseByKey"))
        } yield {
          result match {
            case SRecord(_, _, vals) if vals.size == 2 =>
              assert(vals.get(0) == vals.get(1))
            case _ => fail(s"Expected Tuple2 but got $result")
          }
        }
      }
    }
    "submit with party mismatch fails" in {
      for {
        clients <- getClients(defaultParty = Some("Alice"))
        exception <- recoverToExceptionIf[RuntimeException](
          run(
            clients,
            QualifiedName.assertFromString("ScriptTest:jsonCreate"),
            Some(JsString("Bob")),
          )
        )
      } yield {
        assert(
          exception.getCause.getMessage === "Tried to submit a command with actAs = [Bob] but token provides claims for actAs = [Alice]"
        )
      }
    }
    "application id mismatch" in {
      for {
        exception <- recoverToExceptionIf[RuntimeException](
          getClients(applicationId = Some(ApplicationId("wrong")))
        )
      } yield assert(
        exception.getMessage === "ApplicationId specified in token Some(foobar) must match Some(wrong)"
      )
    }
    "application id correct" in {
      for {
        clients <- getClients(
          defaultParty = Some("Alice"),
          applicationId = Some(ApplicationId("foobar")),
        )
        r <- run(clients, QualifiedName.assertFromString("ScriptTest:jsonCreateAndExercise"))
      } yield assert(r == SInt64(42))
    }
    "query with party mismatch fails" in {
      for {
        clients <- getClients(defaultParty = Some("Alice"))
        exception <- recoverToExceptionIf[RuntimeException](
          run(
            clients,
            QualifiedName.assertFromString("ScriptTest:jsonQuery"),
            Some(JsString("Bob")),
          )
        )
      } yield {
        assert(
          exception.getCause.getMessage === "Tried to query as Bob but token provides claims for Alice"
        )
      }
    }
    "submit with no party fails" in {
      for {
        clients <- getClients(parties = List())
        exception <- recoverToExceptionIf[RuntimeException](
          run(clients, QualifiedName.assertFromString("ScriptTest:jsonCreate"))
        )
      } yield {
        assert(
          exception.getCause.getMessage === "Tried to submit a command with actAs = [Alice] but token contains no actAs parties."
        )
      }
    }
    "submit fails on assertion failure" in {
      for {
        clients <- getClients()
        exception <- recoverToExceptionIf[ScriptF.FailedCmd](
          run(clients, QualifiedName.assertFromString("ScriptTest:jsonFailingCreateAndExercise"))
        )
      } yield {
        exception.cause.getMessage should include(
          "Interpretation error: Error: Unhandled exception: DA.Exception.AssertionFailed:AssertionFailed@3f4deaf1{ message = \"Assertion failed\" }."
        )
      }
    }
    "submitMustFail succeeds on assertion failure" in {
      for {
        clients <- getClients()
        result <- run(
          clients,
          QualifiedName.assertFromString("ScriptTest:jsonExpectedFailureCreateAndExercise"),
        )
      } yield {
        assert(result == SUnit)
      }
    }
    "party management" in {
      for {
        clients <- getClients(parties = List(), admin = true)
        result <- run(
          clients,
          QualifiedName.assertFromString("ScriptTest:jsonAllocateParty"),
          Some(JsString("Eve")),
        )
      } yield {
        assert(result == SParty(Party.assertFromString("Eve")))
      }
    }
    "multi-party" in {
      for {
        clients <- getClients(parties = List("Alice", "Bob"))
        result <- run(
          clients,
          QualifiedName.assertFromString("ScriptTest:jsonMultiParty"),
          Some(JsArray(JsString("Alice"), JsString("Bob"))),
        )
      } yield {
        assert(result == SUnit)
      }
    }
    "missing template id" in {
      for {
        clients <- getClients(envIface = envIfaceNoLedger)
        ex <- recoverToExceptionIf[RuntimeException](
          run(
            clients,
            QualifiedName.assertFromString("ScriptTest:jsonMissingTemplateId"),
            dar = darNoLedger,
          )
        )
      } yield {
        assert(ex.getCause.toString.contains("Cannot resolve template ID"))
      }
    }
    "queryContractId" in {
      for {
        clients <- getClients()
        result <- run(clients, QualifiedName.assertFromString("ScriptTest:jsonQueryContractId"))
      } yield {
        assert(result == SUnit)
      }
    }
    "queryContractKey" in {
      // fresh party to avoid key collisions with other tests
      val party = "jsonQueryContractKey"
      for {
        clients <- getClients(parties = List(party))
        result <- run(
          clients,
          QualifiedName.assertFromString("ScriptTest:jsonQueryContractKey"),
          inputValue = Some(JsString(party)),
        )
      } yield {
        assert(result == SUnit)
      }
    }
    "multiPartyQuery" in {
      // fresh parties to avoid key collisions with other tests
      val party0 = "jsonMultiPartyQuery0"
      val party1 = "jsonMultiPartyQuery1"
      // We need to call Daml script twice since we need per-party tokens for the creates
      // and a single token for the query.
      for {
        clients <- getClients(parties = List(party0, party1))
        cids <- run(
          clients,
          QualifiedName.assertFromString("ScriptTest:multiPartyQueryCreate"),
          inputValue = Some(JsArray(JsString(party0), JsString(party1))),
        )
        multiClients <- getMultiPartyClients(parties = List(party0, party1))
        cids <- run(
          multiClients,
          QualifiedName.assertFromString("ScriptTest:multiPartyQueryQuery"),
          inputValue = Some(
            JsArray(
              JsArray(JsString(party0), JsString(party1)),
              ApiCodecCompressed.apiValueToJsValue(cids.toValue.mapContractId(_.coid)),
            )
          ),
        )
      } yield {
        assert(cids == SUnit)
      }
    }
    "multiPartySubmission" in {
      val party1 = "multiPartySubmission1"
      val party2 = "multiPartySubmission2"
      for {
        clients1 <- getClients(parties = List(party1))
        cidSingle <- run(
          clients1,
          QualifiedName.assertFromString("ScriptTest:jsonMultiPartySubmissionCreateSingle"),
          inputValue = Some(JsString(party1)),
        )
          .map(v => ApiCodecCompressed.apiValueToJsValue(v.toValue.mapContractId(_.coid)))
        clientsBoth <- getMultiPartyClients(List(party1, party2))
        cidBoth <- run(
          clientsBoth,
          QualifiedName.assertFromString("ScriptTest:jsonMultiPartySubmissionCreate"),
          inputValue = Some(JsArray(JsString(party1), JsString(party2))),
        )
          .map(v => ApiCodecCompressed.apiValueToJsValue(v.toValue.mapContractId(_.coid)))
        clients2 <- getMultiPartyClients(List(party2), List(party1))
        r <- run(
          clients2,
          QualifiedName.assertFromString("ScriptTest:jsonMultiPartySubmissionExercise"),
          inputValue = Some(JsArray(JsString(party1), JsString(party2), cidBoth, cidSingle)),
        )
      } yield {
        assert(r == SUnit)
      }
    }
    "invalid response" in {
      def withServer[A](f: ServerBinding => Future[A]) = {
        val bindingF: Future[ServerBinding] = Http().newServerAt("localhost", 0).bind(reject)
        val fa: Future[A] = bindingF.flatMap(f)
        fa.transformWith { ta =>
          Future
            .sequence(
              Seq(
                bindingF.flatMap(_.unbind())
              )
            )
            .transform(_ => ta)
        }
      }
      withServer { binding =>
        val participant = ApiParameters(
          "http://localhost",
          binding.localAddress.getPort,
          Some(getToken(List(party), List(), true)),
          None,
        )
        val participants = Participants(
          Some(participant),
          Map.empty,
          Map.empty,
        )
        for {
          clients <- Runner.jsonClients(participants, envIface)
          exc <- recoverToExceptionIf[ScriptF.FailedCmd](
            run(clients, QualifiedName.assertFromString("ScriptTest:jsonBasic"))
          )
        } yield {
          exc.cause shouldBe a[JsonLedgerClient.FailedJsonApiRequest]
          val cause = exc.cause.asInstanceOf[JsonLedgerClient.FailedJsonApiRequest]
          cause.respStatus shouldBe StatusCodes.NotFound
          cause.errors shouldBe List("The requested resource could not be found.")
        }
      }
    }
  }
}
