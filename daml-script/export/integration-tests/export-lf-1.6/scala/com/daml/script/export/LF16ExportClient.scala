// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.script.export

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import akka.actor.ActorSystem
import com.daml.ledger.client.LedgerClient
import com.daml.ledger.client.configuration.{
  CommandClientConfiguration,
  LedgerClientConfiguration,
  LedgerIdRequirement,
}
import com.daml.fs.Utils.deleteRecursively
import com.daml.grpc.adapter.{AkkaExecutionSequencerPool, ExecutionSequencerFactory}
import com.daml.ledger.api.v1.command_service
import com.daml.ledger.api.v1.commands
import com.daml.ledger.api.v1.value
import com.daml.lf.archive.DarDecoder

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

case class LF16ExportClientConfig(
    darPath: File,
    targetPort: Int,
    ledgerId: String,
    outputZip: Path,
)

object LF16ExportClientConfig {
  def parse(args: Array[String]): Option[LF16ExportClientConfig] =
    parser.parse(
      args,
      LF16ExportClientConfig(
        darPath = null,
        targetPort = -1,
        ledgerId = null,
        outputZip = null,
      ),
    )

  private def parseExportOut(
      envVar: String
  ): Either[String, LF16ExportClientConfig => LF16ExportClientConfig] = {
    envVar.split(" ").map(s => Paths.get(s)) match {
      case Array(output_zip) =>
        Right(c =>
          c.copy(
            outputZip = output_zip
          )
        )
      case _ => Left("Environment variable EXPORT_OUT must contain one path")
    }
  }

  private val parser = new scopt.OptionParser[LF16ExportClientConfig]("lf16-export-client") {
    help("help")
      .text("Show this help message.")
    opt[Int]("target-port")
      .required()
      .action((x, c) => c.copy(targetPort = x))
      .text("Daml ledger port to connect to.")
    opt[String]("ledgerid")
      .required()
      .action((x, c) => c.copy(ledgerId = x))
      .text("Daml ledger identifier.")
    opt[String]("output")
      .hidden()
      .withFallback(() => sys.env.getOrElse("EXPORT_OUT", ""))
      .validate(x => parseExportOut(x).map(_ => ()))
      .action { (x, c) =>
        parseExportOut(x) match {
          case Left(msg) =>
            throw new RuntimeException(s"Failed to validate EXPORT_OUT environment variable: $msg")
          case Right(f) => f(c)
        }
      }
    arg[File]("dar")
      .required()
      .action((f, c) => c.copy(darPath = f))
      .text("Path to the dar file containing the initialization script")
  }
}

object LF16ExportClient {
  def main(args: Array[String]): Unit = {
    LF16ExportClientConfig.parse(args) match {
      case Some(clientConfig) => main(clientConfig)
      case None => sys.exit(1)
    }
  }
  def main(clientConfig: LF16ExportClientConfig): Unit = {
    setupLedger(clientConfig.targetPort, clientConfig.ledgerId, clientConfig.darPath)
    generateExport(
      clientConfig.targetPort,
      clientConfig.outputZip.toFile,
    )
  }

  private def setupLedger(ledgerPort: Int, ledgerId: String, darPath: File): Unit = {
    implicit val sys: ActorSystem = ActorSystem("lf16-export-client")
    implicit val ec: ExecutionContext = sys.dispatcher
    implicit val seq: ExecutionSequencerFactory = new AkkaExecutionSequencerPool(
      "lf16-export-client"
    )
    val run: Future[Unit] = for {
      dar <- Future.fromTry(DarDecoder.readArchiveFromFile(darPath).toTry)
      mainPackageId = dar.main._1
      lf16TemplateId = value
        .Identifier()
        .withPackageId(mainPackageId)
        .withModuleName("LF16")
        .withEntityName("LF16")
      lf16IncrementId = value
        .Identifier()
        .withPackageId(mainPackageId)
        .withModuleName("LF16")
        .withEntityName("Increment")
      ledgerConfig = LedgerClientConfiguration(
        applicationId = "lf16-export-client",
        ledgerIdRequirement = LedgerIdRequirement.none,
        commandClient = CommandClientConfiguration.default,
        sslContext = None,
      )
      client <- LedgerClient.singleHost("localhost", ledgerPort, ledgerConfig)
      alice <- client.partyManagementClient.allocateParty(
        hint = Some("Alice"),
        displayName = Some("Alice"),
      )
      _ = System.err.println(s"$alice")
      resp <- client.commandServiceClient.submitAndWaitForTransaction(
        command_service
          .SubmitAndWaitRequest()
          .withCommands(
            commands
              .Commands()
              .withLedgerId(ledgerId)
              .withApplicationId(ledgerConfig.applicationId)
              .withCommandId("create-LF16")
              .withActAs(Seq(alice.party))
              .withCommands(
                Seq(
                  commands
                    .Command()
                    .withCreate(
                      commands
                        .CreateCommand()
                        .withTemplateId(lf16TemplateId)
                        .withCreateArguments(
                          LF.recordRec(
                            lf16TemplateId,
                            "issuer" -> value.Value().withParty(alice.party),
                            "count" -> value.Value().withInt64(0),
                          )
                        )
                    )
                )
              )
          )
      )
      _ = System.err.println(s"${resp}")
      cid = resp.getTransaction.events(0).event.created.get.contractId
      _ = System.err.println(s"ID: $cid")
      resp <- client.commandServiceClient.submitAndWaitForTransaction(
        command_service
          .SubmitAndWaitRequest()
          .withCommands(
            commands
              .Commands()
              .withLedgerId(ledgerId)
              .withApplicationId(ledgerConfig.applicationId)
              .withCommandId("exercise-Lf16-Increment")
              .withActAs(Seq(alice.party))
              .withCommands(
                Seq(
                  commands
                    .Command()
                    .withExercise(
                      commands
                        .ExerciseCommand()
                        .withTemplateId(lf16TemplateId)
                        .withContractId(cid)
                        .withChoice("Increment")
                        .withChoiceArgument(LF.record(lf16IncrementId))
                    )
                )
              )
          )
      )
      _ = System.err.println(s"${resp}")
      cid = resp.getTransaction.events.find(_.event.isCreated).get.event.created.get.contractId
      _ = System.err.println(s"ID: $cid")
      resp <- client.commandServiceClient.submitAndWaitForTransaction(
        command_service
          .SubmitAndWaitRequest()
          .withCommands(
            commands
              .Commands()
              .withLedgerId(ledgerId)
              .withApplicationId(ledgerConfig.applicationId)
              .withCommandId("archive-Lf16")
              .withActAs(Seq(alice.party))
              .withCommands(
                Seq(
                  commands
                    .Command()
                    .withExercise(
                      commands
                        .ExerciseCommand()
                        .withTemplateId(lf16TemplateId)
                        .withContractId(cid)
                        .withChoice("Archive")
                        .withChoiceArgument(LF.record(LF.archiveId))
                    )
                )
              )
          )
      )
      _ = System.err.println(s"${resp}")
      // TODO[AH] Needs two creates followed by an exercise of the first contract to trigger exerciseByKey
      resp <- client.commandServiceClient.submitAndWaitForTransaction(
        command_service
          .SubmitAndWaitRequest()
          .withCommands(
            commands
              .Commands()
              .withLedgerId(ledgerId)
              .withApplicationId(ledgerConfig.applicationId)
              .withCommandId("createAndExercise-exerciseByKey-Lf16-Increment")
              .withActAs(Seq(alice.party))
              .withCommands(
                Seq(
                  commands
                    .Command()
                    .withCreateAndExercise(
                      commands
                        .CreateAndExerciseCommand()
                        .withTemplateId(lf16TemplateId)
                        .withCreateArguments(
                          value
                            .Record()
                            .withFields(
                              Seq(
                                value
                                  .RecordField()
                                  .withLabel("issuer")
                                  .withValue(value.Value().withParty(alice.party)),
                                value
                                  .RecordField()
                                  .withLabel("count")
                                  .withValue(value.Value().withInt64(0)),
                              )
                            )
                        )
                        .withChoice("Increment")
                        .withChoiceArgument(LF.record(lf16IncrementId))
                    ),
                  commands
                    .Command()
                    .withExerciseByKey(
                      commands
                        .ExerciseByKeyCommand()
                        .withTemplateId(lf16TemplateId)
                        .withContractKey(
                          LF.tuple(
                            value.Value().withParty(alice.party),
                            value.Value().withInt64(1),
                          )
                        )
                        .withChoice("Increment")
                        .withChoiceArgument(LF.record(lf16IncrementId))
                    ),
                )
              )
          )
      )
      _ = System.err.println(s"${resp}")
      cid = resp.getTransaction.events.find(_.event.isCreated).get.event.created.get.contractId
      _ = System.err.println(s"ID: $cid")
    } yield ()
    run
      .recoverWith { case NonFatal(fail) => Future { println(fail) } }
      .onComplete(_ => sys.terminate())
    Await.result(sys.whenTerminated, Duration.Inf)
    ()
  }

  private def generateExport(
      ledgerPort: Int,
      outputZip: File,
  ): Unit = {
    withTemporaryDirectory { outputPath =>
      Main.main(
        Config.Empty.copy(
          ledgerHost = "localhost",
          ledgerPort = ledgerPort,
          parties = Seq("Alice"),
          exportType = Some(
            Config.EmptyExportScript.copy(
              sdkVersion = "0.0.0",
              outputPath = outputPath,
            )
          ),
        )
      )
      createZipArchive(outputPath.toFile, outputZip)
    }
  }

  /** Recursively archives all files contained in a directory.
    *
    * The generated zip archive is reproducible in that the order of entries and their timestamps are deterministic.
    *
    * @param src Archive all files underneath this directory. The paths of the entries will be relative to this directory.
    * @param dst Write the zip archive to this file.
    */
  private def createZipArchive(src: File, dst: File): Unit = {
    val out = new FileOutputStream(dst)
    val zipOut = new ZipOutputStream(out)
    def addFile(file: File): Unit = {
      val path = src.toPath.relativize(file.toPath)
      val entry = new ZipEntry(path.toString)
      entry.setTime(0)
      zipOut.putNextEntry(entry)
      Files.copy(file.toPath, zipOut)
      zipOut.closeEntry()
    }
    def addDirectory(dir: File): Unit = {
      dir
        .listFiles()
        .sorted
        .foreach(f =>
          if (f.isDirectory) { addDirectory(f) }
          else { addFile(f) }
        )
    }
    addDirectory(src)
    zipOut.close
  }

  private def withTemporaryDirectory(f: Path => Unit): Unit = {
    val tmpDir = Files.createTempDirectory("daml-ledger-export")
    try {
      f(tmpDir)
    } finally {
      deleteRecursively(tmpDir)
    }
  }
}

object LF {
  val archiveId = value
    .Identifier()
    .withPackageId("d14e08374fc7197d6a0de468c968ae8ba3aadbf9315476fd39071831f5923662")
    .withModuleName("DA.Internal.Template")
    .withEntityName("Archive")
  def tupleId(n: Int): value.Identifier =
    value
      .Identifier()
      .withPackageId("40f452260bef3f29dede136108fc08a88d5a5250310281067087da6f0baddff7")
      .withModuleName("DA.Types")
      .withEntityName(s"Tuple$n")
  def recordRec(id: value.Identifier, fields: (String, value.Value)*): value.Record =
    value
      .Record()
      .withRecordId(id)
      .withFields(fields.map { case (lbl, v) =>
        value
          .RecordField()
          .withLabel(lbl)
          .withValue(v)
      })
  def record(id: value.Identifier, fields: (String, value.Value)*): value.Value =
    value.Value().withRecord(recordRec(id, fields: _*))
  def tuple(vals: value.Value*): value.Value = {
    record(tupleId(vals.size), vals.zipWithIndex.map { case (v, ix) => (s"_$ix", v) }: _*)
  }
}
