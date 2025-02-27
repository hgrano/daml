// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf
package engine

import com.daml.lf.data._
import com.daml.lf.testing.parser.Implicits._
import com.daml.lf.transaction.{GenTransaction, GlobalKey, Node, NodeId}
import com.daml.lf.transaction.test.TransactionBuilder
import com.daml.lf.value.Value
import com.daml.lf.value.Value.ContractId
import org.scalatest.Inside.inside
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.language.implicitConversions

class ContractDiscriminatorFreshnessCheckSpec
    extends AnyWordSpec
    with Matchers
    with TableDrivenPropertyChecks {

  private val pkg = p"""
         module Mod {
           record @serializable Key = {
             party: Party,
             idx: Int64
           };

           record @serializable Contract = {
             key: Mod:Key,
             cids: List (ContractId Mod:Contract)
           };

           val @noPartyLiterals keyParties: (Mod:Key -> List Party) =
             \(key: Mod:Key) ->
               Cons @Party [Mod:Key {party} key] (Nil @Party);

           val @noPartyLiterals contractParties : (Mod:Contract -> List Party) =
             \(contract: Mod:Contract) ->
               Mod:keyParties (Mod:Contract {key} contract);

           template (this : Contract) =  {
              precondition True,
              signatories Mod:contractParties this,
              observers Mod:contractParties this,
              agreement "Agreement",
              choices {
                choice @nonConsuming Noop (self) (u: Unit) : Unit,
                  controllers
                    Mod:contractParties this
                  to
                    upure @Unit (),
                choice @nonConsuming Identity (self) (cid: ContractId Mod:Contract) : ContractId Mod:Contract,
                  controllers
                    Mod:contractParties this
                  to
                    upure @(ContractId Mod:Contract) cid,
                choice @nonConsuming LookupByKey (self) (key: Mod:Key) : Option (ContractId Mod:Contract),
                  controllers
                    Mod:contractParties this
                  to
                    lookup_by_key @Mod:Contract key,
                choice @nonConsuming Create (self) (contract: Mod:Contract): (ContractId Mod:Contract),
                  controllers
                    Mod:contractParties this
                  to
                    create @Mod:Contract contract
              },
              key @Mod:Key (Mod:Contract {key} this) Mod:keyParties
            };
         }
         """
  private val pkgId = defaultParserParameters.defaultPackageId
  private val pkgs = Map(pkgId -> pkg).lift

  private val keyId = Ref.Identifier(pkgId, Ref.QualifiedName.assertFromString("Mod:Key"))
  private val tmplId = Ref.Identifier(pkgId, Ref.QualifiedName.assertFromString("Mod:Contract"))

  private def contractId(discriminator: crypto.Hash, suffix: Bytes): Value.ContractId =
    Value.ContractId.V1.assertBuild(discriminator, suffix)

  private def keyRecord(party: Ref.Party, idx: Int) =
    Value.ValueRecord(
      Some(keyId),
      ImmArray(
        Some[Ref.Name]("party") -> Value.ValueParty(party),
        Some[Ref.Name]("idx") -> Value.ValueInt64(idx.toLong),
      ),
    )

  private def contractRecord(party: Ref.Party, idx: Int, cids: List[ContractId]) =
    Value.ValueRecord(
      Some(tmplId),
      ImmArray(
        Some[Ref.Name]("key") -> keyRecord(party, idx),
        Some[Ref.Name]("cids") -> Value.ValueList(FrontStack(cids.map(Value.ValueContractId(_)))),
      ),
    )

  private val hash1 = crypto.Hash.hashPrivateKey("hash1")
  private val hash2 = crypto.Hash.hashPrivateKey("hash2")
  private val suffix1 = Utf8.getBytes("suffix")
  private val suffix2 = Utf8.getBytes("extension")
  private val suffix3 = Utf8.getBytes("final-addition")

  private def contractInstance(party: Ref.Party, idx: Int, cids: List[ContractId]) =
    Value.ContractInst(
      tmplId,
      TransactionBuilder.assertAsVersionedValue(contractRecord(party, idx, cids)),
      "Agreement",
    )

  private def globalKey(party: Ref.Party, idx: Int) =
    GlobalKey(tmplId, keyRecord(party, idx))

  private val alice = Ref.Party.assertFromString("Alice")
  private val participant = Ref.ParticipantId.assertFromString("participant")

  private val submissionSeed = crypto.Hash.hashPrivateKey(getClass.getName)
  private val let = Time.Timestamp.MinValue
  private val transactionSeed = crypto.Hash.deriveTransactionSeed(
    submissionSeed,
    participant,
    let,
  )

  private def submit(
      cmds: ImmArray[command.ApiCommand],
      pcs: Value.ContractId => Option[Value.ContractInst[Value.VersionedValue[ContractId]]],
      keys: GlobalKey => Option[ContractId],
  ) =
    engine
      .submit(
        submitters = Set(alice),
        readAs = Set.empty,
        cmds = command.Commands(
          commands = cmds,
          ledgerEffectiveTime = let,
          commandsReference = "test",
        ),
        participantId = participant,
        submissionSeed = submissionSeed,
      )
      .consume(
        pcs,
        pkgs,
        keyWithMaintainers => keys(keyWithMaintainers.globalKey),
      )

  val engine = Engine.DevEngine()

  "conflict freshness check" should {

    "fails when a global contract conflicts with a local contract previously created" ignore {

      val conflictingCid = {
        val createNodeSeed = crypto.Hash.deriveNodeSeed(transactionSeed, 0)
        val conflictingDiscriminator =
          crypto.Hash.deriveContractDiscriminator(createNodeSeed, let, Set(alice))
        contractId(conflictingDiscriminator, suffix3)
      }

      val exercisedCid1 = contractId(hash1, suffix1)
      val exercisedCid2 = contractId(hash2, suffix2)
      val contractsData =
        List(
          (exercisedCid1, 1, List.empty),
          (exercisedCid2, 2, List(conflictingCid)),
          (conflictingCid, 3, List.empty),
        )
      val contractLookup =
        contractsData
          .map { case (cid, idx, cids) =>
            cid -> contractInstance(alice, idx, cids)
          }
          .toMap
          .lift
      val keyLookup = contractsData
        .map { case (cid, idx, _) =>
          globalKey(alice, idx) -> cid
        }
        .toMap
        .lift

      def run(cmd: command.ApiCommand) =
        submit(
          ImmArray(
            command.CreateCommand(tmplId, contractRecord(alice, 0, List.empty)),
            cmd,
          ),
          pcs = contractLookup,
          keys = keyLookup,
        )

      val negativeTestCases = Table(
        "successful commands",
        command.ExerciseCommand(tmplId, exercisedCid1, "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 1), "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, -1)),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 1)),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 2)),
      )

      val positiveTestCases = Table(
        "failing commands",
        command.ExerciseCommand(tmplId, conflictingCid, "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid2, "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 2), "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 3), "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 3)),
      )

      forAll(negativeTestCases) { cmd =>
        run(cmd) shouldBe a[Right[_, _]]
      }

      forAll(positiveTestCases) { cmd =>
        val r = run(cmd)
        r shouldBe a[Left[_, _]]
        r.left.exists(_.message.contains("Conflicting discriminators")) shouldBe true
      }

    }

    "fails when a local conflicts with a global contract previously fetched" in {

      val conflictingCid = {
        val createNodeSeed = crypto.Hash.deriveNodeSeed(transactionSeed, 1)
        val conflictingDiscriminator =
          crypto.Hash.deriveContractDiscriminator(createNodeSeed, let, Set(alice))
        contractId(conflictingDiscriminator, suffix3)
      }

      val exercisedCid1 = contractId(hash1, suffix1)
      val exercisedCid2 = contractId(hash2, suffix2)
      val contractsData =
        List(
          (exercisedCid1, 1, List.empty),
          (exercisedCid2, 2, List(conflictingCid)),
          (conflictingCid, 3, List.empty),
        )
      val contractLookup =
        contractsData
          .map { case (cid, idx, cids) =>
            cid -> contractInstance(alice, idx, cids)
          }
          .toMap
          .lift
      val keyLookup = contractsData
        .map { case (cid, idx, _) =>
          globalKey(alice, idx) -> cid
        }
        .toMap
        .lift

      def run(cmd: command.ApiCommand) =
        submit(
          ImmArray(
            cmd,
            command.CreateCommand(tmplId, contractRecord(alice, 0, List.empty)),
          ),
          pcs = contractLookup,
          keys = keyLookup,
        )

      val negativeTestCases = Table(
        "successful commands",
        command.ExerciseCommand(tmplId, exercisedCid1, "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 1), "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, -1)),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 1)),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 2)),
      )

      val positiveTestCases = Table(
        "failing commands",
        command.ExerciseCommand(tmplId, conflictingCid, "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid2, "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 2), "Noop", Value.ValueUnit),
        command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 3), "Noop", Value.ValueUnit),
        command.ExerciseCommand(tmplId, exercisedCid1, "LookupByKey", keyRecord(alice, 3)),
      )

      forAll(negativeTestCases) { cmd =>
        Right(cmd) shouldBe a[Right[_, _]]
      }

      forAll(positiveTestCases) { cmd =>
        val r = run(cmd)
        r shouldBe a[Left[_, _]]
        r.left.exists(_.message.contains("Conflicting discriminators")) shouldBe true
      }

    }

    "fail when preprocessing a transaction when a cid appear before before being created" in {

      val cid0: ContractId = ContractId.V1(crypto.Hash.hashPrivateKey("test"), Bytes.Empty)

      val Right((tx, _)) = submit(
        ImmArray(
          command.CreateAndExerciseCommand(
            tmplId,
            contractRecord(alice, 1, List.empty),
            "Identity",
            Value.ValueContractId(cid0),
          ),
          command.CreateCommand(tmplId, contractRecord(alice, 2, List.empty)),
        ),
        pcs = (cid => if (cid == cid0) Some(contractInstance(alice, 0, List.empty)) else None),
        keys = _ => None,
      )

      val lastCreatedCid = tx.fold(cid0) {
        case (_, (_, create: Node.NodeCreate[ContractId])) => create.coid
        case (acc, _) => acc
      }

      assert(lastCreatedCid != cid0)

      val newNodes = tx.fold(tx.nodes) {
        case (nodes, (nid, exe: Node.NodeExercises[NodeId, ContractId]))
            if exe.choiceId == "Identity" =>
          nodes.updated(nid, exe.copy(chosenValue = Value.ValueContractId(lastCreatedCid)))
        case (acc, _) => acc
      }

      val result =
        new preprocessing.Preprocessor(ConcurrentCompiledPackages(speedy.Compiler.Config.Dev))
          .translateTransactionRoots(GenTransaction(newNodes, tx.roots))
          .consume(_ => None, pkgs, _ => None)

      inside(result) { case Left(Error.Preprocessing(err)) =>
        err shouldBe a[Error.Preprocessing.ContractIdFreshness]
      }

    }

    "does not fail when replaying an exercise by key of a non-root local Contract" in {
      // regression test for https://discuss.daml.com/t/ledger-api-error-message-conflicting-discriminators-between-a-global-and-local-contract-id/2416/3
      val Right((tx, txMeta)) = submit(
        ImmArray(
          command.CreateAndExerciseCommand(
            tmplId,
            contractRecord(alice, 0, List.empty),
            "Create",
            contractRecord(alice, 1, List.empty),
          ),
          command.ExerciseByKeyCommand(tmplId, keyRecord(alice, 1), "Noop", Value.ValueUnit),
        ),
        pcs = _ => None,
        keys = _ => None,
      )
      engine
        .replay(
          submitters = Set(alice),
          tx,
          ledgerEffectiveTime = txMeta.submissionTime,
          participantId = participant,
          submissionTime = txMeta.submissionTime,
          submissionSeed = txMeta.submissionSeed.get,
        )
        .consume(_ => None, pkgs, _ => None) shouldBe a[Right[_, _]]
    }

  }

  private implicit def toName(s: String): Ref.Name = Ref.Name.assertFromString(s)

}
