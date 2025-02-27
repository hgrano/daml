// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.testtool.suites

import java.util.regex.Pattern
import com.daml.ledger.api.testtool.infrastructure.Allocation._
import com.daml.ledger.api.testtool.infrastructure.Assertions._
import com.daml.ledger.api.testtool.infrastructure.Eventually.eventually
import com.daml.ledger.api.testtool.infrastructure.LedgerTestSuite
import com.daml.ledger.api.testtool.infrastructure.Synchronize.synchronize
import com.daml.ledger.api.testtool.infrastructure.TransactionHelpers._
import com.daml.ledger.api.v1.value.{Record, RecordField, Value}
import com.daml.ledger.client.binding.Primitive.ContractId
import com.daml.ledger.test.model.DA.Types.Tuple2
import com.daml.ledger.test.model.Test
import io.grpc.Status
import scalaz.Tag

final class ContractKeysIT extends LedgerTestSuite {
  test(
    "CKFetchOrLookup",
    "Divulged contracts cannot be fetched or looked up by key by non-stakeholders",
    allocate(SingleParty, SingleParty),
  )(implicit ec => { case Participants(Participant(alpha, owner), Participant(beta, delegate)) =>
    import Test.{Delegated, Delegation, ShowDelegated}
    val key = alpha.nextKeyId()
    for {
      // create contracts to work with
      delegated <- alpha.create(owner, Delegated(owner, key))
      delegation <- alpha.create(owner, Delegation(owner, delegate))
      showDelegated <- alpha.create(owner, ShowDelegated(owner, delegate))

      // divulge the contract
      _ <- alpha.exercise(owner, showDelegated.exerciseShowIt(_, delegated))

      // fetch delegated
      _ <- eventually {
        beta.exercise(delegate, delegation.exerciseFetchDelegated(_, delegated))
      }

      // fetch by key should fail during interpretation
      // Reason: Only stakeholders see the result of fetchByKey, beta is neither stakeholder nor divulgee
      fetchFailure <- beta
        .exercise(delegate, delegation.exerciseFetchByKeyDelegated(_, owner, key))
        .mustFail("fetching by key with a party that cannot see the contract")

      // lookup by key delegation is should fail during validation
      // Reason: During command interpretation, the lookup did not find anything due to privacy rules,
      // but validation determined that this result is wrong as the contract is there.
      lookupByKeyFailure <- beta
        .exercise(delegate, delegation.exerciseLookupByKeyDelegated(_, owner, key))
        .mustFail("looking up by key with a party that cannot see the contract")
    } yield {
      assertGrpcError(fetchFailure, Status.Code.INVALID_ARGUMENT, "couldn't find key")
      assertGrpcError(
        lookupByKeyFailure,
        Status.Code.ABORTED,
        Some(Pattern.compile("Inconsistent")),
      )
    }
  })

  test(
    "CKNoFetchUndisclosed",
    "Contract Keys should reject fetching an undisclosed contract",
    allocate(SingleParty, SingleParty),
  )(implicit ec => { case Participants(Participant(alpha, owner), Participant(beta, delegate)) =>
    import Test.{Delegated, Delegation}
    val key = alpha.nextKeyId()
    for {
      // create contracts to work with
      delegated <- alpha.create(owner, Delegated(owner, key))
      delegation <- alpha.create(owner, Delegation(owner, delegate))

      _ <- synchronize(alpha, beta)

      // fetch should fail
      // Reason: contract not divulged to beta
      fetchFailure <- beta
        .exercise(delegate, delegation.exerciseFetchDelegated(_, delegated))
        .mustFail("fetching a contract with a party that cannot see it")

      // fetch by key should fail
      // Reason: Only stakeholders see the result of fetchByKey, beta is only a divulgee
      fetchByKeyFailure <- beta
        .exercise(delegate, delegation.exerciseFetchByKeyDelegated(_, owner, key))
        .mustFail("fetching a contract by key with a party that cannot see it")

      // lookup by key should fail
      // Reason: During command interpretation, the lookup did not find anything due to privacy rules,
      // but validation determined that this result is wrong as the contract is there.
      lookupByKeyFailure <- beta
        .exercise(delegate, delegation.exerciseLookupByKeyDelegated(_, owner, key))
        .mustFail("looking up a contract by key with a party that cannot see it")
    } yield {
      assertGrpcError(
        fetchFailure,
        Status.Code.ABORTED,
        "Contract could not be found",
      )
      assertGrpcError(fetchByKeyFailure, Status.Code.INVALID_ARGUMENT, "couldn't find key")
      assertGrpcError(
        lookupByKeyFailure,
        Status.Code.ABORTED,
        Some(Pattern.compile("Inconsistent")),
      )
    }
  })

  test(
    "CKMaintainerScoped",
    "Contract keys should be scoped by maintainer",
    allocate(SingleParty, SingleParty),
  )(implicit ec => { case Participants(Participant(alpha, alice), Participant(beta, bob)) =>
    import Test.{TextKey, TextKeyOperations, MaintainerNotSignatory}
    val key1 = alpha.nextKeyId()
    val key2 = alpha.nextKeyId()
    val unknownKey = alpha.nextKeyId()

    for {
      // create contracts to work with
      tk1 <- alpha.create(alice, TextKey(alice, key1, List(bob)))
      tk2 <- alpha.create(alice, TextKey(alice, key2, List(bob)))
      aliceTKO <- alpha.create(alice, TextKeyOperations(alice))
      bobTKO <- beta.create(bob, TextKeyOperations(bob))

      _ <- synchronize(alpha, beta)

      // creating a contract with a duplicate key should fail
      duplicateKeyFailure <- alpha
        .create(alice, TextKey(alice, key1, List(bob)))
        .mustFail("creating a contract with a duplicate key")

      // trying to lookup an unauthorized key should fail
      bobLooksUpTextKeyFailure <- beta
        .exercise(bob, bobTKO.exerciseTKOLookup(_, Tuple2(alice, key1), Some(tk1)))
        .mustFail("looking up a contract with an unauthorized key")

      // trying to lookup an unauthorized non-existing key should fail
      bobLooksUpBogusTextKeyFailure <- beta
        .exercise(bob, bobTKO.exerciseTKOLookup(_, Tuple2(alice, unknownKey), None))
        .mustFail("looking up a contract with an unauthorized, non-existing key")

      // successful, authorized lookup
      _ <- alpha.exercise(alice, aliceTKO.exerciseTKOLookup(_, Tuple2(alice, key1), Some(tk1)))

      // successful fetch
      _ <- alpha.exercise(alice, aliceTKO.exerciseTKOFetch(_, Tuple2(alice, key1), tk1))

      // successful, authorized lookup of non-existing key
      _ <- alpha.exercise(alice, aliceTKO.exerciseTKOLookup(_, Tuple2(alice, unknownKey), None))

      // failing fetch
      aliceFailedFetch <- alpha
        .exercise(alice, aliceTKO.exerciseTKOFetch(_, Tuple2(alice, unknownKey), tk1))
        .mustFail("fetching a contract by an unknown key")

      // now we exercise the contract, thus archiving it, and then verify
      // that we cannot look it up anymore
      _ <- alpha.exercise(alice, tk1.exerciseTextKeyChoice)
      _ <- alpha.exercise(alice, aliceTKO.exerciseTKOLookup(_, Tuple2(alice, key1), None))

      // lookup the key, consume it, then verify we cannot look it up anymore
      _ <- alpha.exercise(
        alice,
        aliceTKO.exerciseTKOConsumeAndLookup(_, tk2, Tuple2(alice, key2)),
      )

      // failing create when a maintainer is not a signatory
      maintainerNotSignatoryFailed <- alpha
        .create(alice, MaintainerNotSignatory(alice, bob))
        .mustFail("creating a contract where a maintainer is not a signatory")
    } yield {
      assertGrpcError(
        duplicateKeyFailure,
        Status.Code.ABORTED,
        Some(Pattern.compile("Inconsistent")),
      )
      assertGrpcError(
        bobLooksUpTextKeyFailure,
        Status.Code.INVALID_ARGUMENT,
        "requires authorizers",
      )
      assertGrpcError(
        bobLooksUpBogusTextKeyFailure,
        Status.Code.INVALID_ARGUMENT,
        "requires authorizers",
      )
      assertGrpcError(aliceFailedFetch, Status.Code.INVALID_ARGUMENT, "couldn't find key")
      assertGrpcError(
        maintainerNotSignatoryFailed,
        Status.Code.INVALID_ARGUMENT,
        "are not a subset of the signatories",
      )
    }
  })

  test("CKRecreate", "Contract keys can be recreated in single transaction", allocate(SingleParty))(
    implicit ec => { case Participants(Participant(ledger, owner)) =>
      import Test.Delegated
      val key = ledger.nextKeyId()
      for {
        delegated1TxTree <- ledger
          .submitAndWaitForTransactionTree(
            ledger.submitAndWaitRequest(owner, Delegated(owner, key).create.command)
          )
        delegated1Id = com.daml.ledger.client.binding.Primitive
          .ContractId[Delegated](delegated1TxTree.eventsById.head._2.getCreated.contractId)

        delegated2TxTree <- ledger.exercise(owner, delegated1Id.exerciseRecreate)
      } yield {
        assert(delegated2TxTree.eventsById.size == 2)
        val event = delegated2TxTree.eventsById.filter(_._2.kind.isCreated).head._2
        assert(
          Tag.unwrap(delegated1Id) != event.getCreated.contractId,
          "New contract was not created",
        )
        assert(
          event.getCreated.contractKey == delegated1TxTree.eventsById.head._2.getCreated.contractKey,
          "Contract keys did not match",
        )

      }
    }
  )

  test(
    "CKTransients",
    "Contract keys created by transient contracts are properly archived",
    allocate(SingleParty),
  )(implicit ec => { case Participants(Participant(ledger, owner)) =>
    import Test.{Delegated, Delegation}
    val key = ledger.nextKeyId()
    val key2 = ledger.nextKeyId()

    for {
      delegation <- ledger.create(owner, Delegation(owner, owner))
      delegated <- ledger.create(owner, Delegated(owner, key))

      failedFetch <- ledger
        .exercise(owner, delegation.exerciseFetchByKeyDelegated(_, owner, key2))
        .mustFail("fetching a contract with an unknown key")

      // Create a transient contract with a key that is created and archived in same transaction.
      _ <- ledger.exercise(owner, delegated.exerciseCreateAnotherAndArchive(_, key2))

      // Try it again, expecting it to succeed.
      _ <- ledger.exercise(owner, delegated.exerciseCreateAnotherAndArchive(_, key2))

    } yield {
      assertGrpcError(failedFetch, Status.Code.INVALID_ARGUMENT, "couldn't find key")
    }
  })

  test(
    "CKExposedByTemplate",
    "The contract key should be exposed if the template specifies one",
    allocate(SingleParty),
  )(implicit ec => { case Participants(Participant(ledger, party)) =>
    import Test.TextKey
    val expectedKey = ledger.nextKeyId()
    for {
      _ <- ledger.create(party, TextKey(party, expectedKey, List.empty))
      transactions <- ledger.flatTransactions(party)
    } yield {
      val contract = assertSingleton("CKExposedByTemplate", transactions.flatMap(createdEvents))
      assertEquals(
        "CKExposedByTemplate",
        contract.getContractKey.getRecord.fields,
        Seq(
          RecordField("_1", Some(Value(Value.Sum.Party(Tag.unwrap(party))))),
          RecordField("_2", Some(Value(Value.Sum.Text(expectedKey)))),
        ),
      )
    }
  })

  test(
    "CKExerciseByKey",
    "Exercising by key should be possible only when the corresponding contract is available",
    allocate(SingleParty),
  )(implicit ec => { case Participants(Participant(ledger, party)) =>
    import Test.TextKey
    val keyString = ledger.nextKeyId()
    val expectedKey = Value(
      Value.Sum.Record(
        Record(
          fields = Seq(
            RecordField("_1", Some(Value(Value.Sum.Party(Tag.unwrap(party))))),
            RecordField("_2", Some(Value(Value.Sum.Text(keyString)))),
          )
        )
      )
    )
    for {
      failureBeforeCreation <- ledger
        .exerciseByKey(
          party,
          TextKey.id,
          expectedKey,
          "TextKeyChoice",
          Value(Value.Sum.Record(Record())),
        )
        .mustFail("exercising before creation")
      _ <- ledger.create(party, TextKey(party, keyString, List.empty))
      _ <- ledger.exerciseByKey(
        party,
        TextKey.id,
        expectedKey,
        "TextKeyChoice",
        Value(Value.Sum.Record(Record())),
      )
      failureAfterConsuming <- ledger
        .exerciseByKey(
          party,
          TextKey.id,
          expectedKey,
          "TextKeyChoice",
          Value(Value.Sum.Record(Record())),
        )
        .mustFail("exercising after consuming")
    } yield {
      assertGrpcError(
        failureBeforeCreation,
        Status.Code.INVALID_ARGUMENT,
        "dependency error: couldn't find key",
      )
      assertGrpcError(
        failureAfterConsuming,
        Status.Code.INVALID_ARGUMENT,
        "dependency error: couldn't find key",
      )
    }
  })

  test(
    "CKLocalKeyVisibility",
    "Visibility should be checked for fetch-by-key/lookup-by-key of contracts created in the current transaction",
    allocate(SingleParty, SingleParty),
  )(implicit ec => {
    case Participants(Participant(ledger1, party1), Participant(ledger2, party2)) =>
      import Test.LocalKeyVisibilityOperations
      for {
        ops <- ledger1.create(party1, LocalKeyVisibilityOperations(party1, party2))
        _ <- synchronize(ledger1, ledger2)
        failedLookup <- ledger2
          .exercise(party2, ops.exerciseLocalLookup(_))
          .mustFail("lookup not visible")
        failedFetch <- ledger2
          .exercise(party2, ops.exerciseLocalFetch(_))
          .mustFail("fetch not visible")
        _ <- ledger2.exercise(
          actAs = List(party2),
          readAs = List(party1),
          ops.exerciseLocalLookup(party2),
        )
        _ <- ledger2.exercise(
          actAs = List(party2),
          readAs = List(party1),
          ops.exerciseLocalFetch(party2),
        )
      } yield {
        assertGrpcError(failedLookup, Status.Code.INVALID_ARGUMENT, "not visible")
        assertGrpcError(failedFetch, Status.Code.INVALID_ARGUMENT, "not visible")
      }
  })

  test(
    "CKDisclosedContractKeyReusabilityBasic",
    "Subsequent disclosed contracts can use the same contract key",
    allocate(SingleParty, SingleParty),
  )(implicit ec => {
    case Participants(Participant(ledger1, party1), Participant(ledger2, party2)) =>
      import Test.{WithKey, WithKeyCreator, WithKeyFetcher}
      for {
        // Create a helper contract and exercise a choice creating and disclosing a WithKey contract
        creator1 <- ledger1.create(party1, WithKeyCreator(party1, party2))
        withKey1 <- ledger1.exerciseAndGetContract[WithKey](
          party1,
          creator1.exerciseWithKeyCreator_DiscloseCreate(_, party1),
        )

        // Verify that the withKey1 contract is usable by the party2
        fetcher <- ledger1.create(party1, WithKeyFetcher(party1, party2))

        _ <- synchronize(ledger1, ledger2)

        _ <- ledger2.exercise(party2, fetcher.exerciseWithKeyFetcher_Fetch(_, withKey1))

        // Archive the disclosed contract
        _ <- ledger1.exercise(party1, withKey1.exerciseArchive(_))

        _ <- synchronize(ledger1, ledger2)

        // Verify that fetching the contract is no longer possible after it was archived
        _ <- ledger2
          .exercise(party2, fetcher.exerciseWithKeyFetcher_Fetch(_, withKey1))
          .mustFail("fetching an archived contract")

        // Repeat the same steps for the second time
        creator2 <- ledger1.create(party1, WithKeyCreator(party1, party2))
        _ <- ledger1.exerciseAndGetContract[WithKey](
          party1,
          creator2.exerciseWithKeyCreator_DiscloseCreate(_, party1),
        )

        // Synchronize to verify that the second participant is working
        _ <- synchronize(ledger1, ledger2)
      } yield ()
  })
  import scalaz.syntax.tag._
  test(
    "CKDisclosedContractKeyReusabilityAsSubmitter",
    "Subsequent disclosed contracts can use the same contract key (disclosure because of submitting)",
    allocate(SingleParty, SingleParty),
  )(implicit ec => {
    case Participants(Participant(ledger1, party1), Participant(ledger2, party2)) =>
      import Test.{WithKey, WithKeyCreatorAlternative}
      for {
        // Create a helper contract and exercise a choice creating and disclosing a WithKey contract
        creator1 <- ledger1.create(party1, WithKeyCreatorAlternative(party1, party2))

        _ <- synchronize(ledger1, ledger2)

        _ <- ledger2.exercise(
          party2,
          creator1.exerciseWithKeyCreatorAlternative_DiscloseCreate(_),
        )

        _ <- synchronize(ledger1, ledger2)

        Seq(withKey1Event) <- ledger1.activeContractsByTemplateId(List(WithKey.id.unwrap), party1)
        withKey1 = ContractId.apply[WithKey](withKey1Event.contractId)
        // Archive the disclosed contract
        _ <- ledger1.exercise(party1, withKey1.exerciseArchive(_))

        _ <- synchronize(ledger1, ledger2)

        // Repeat the same steps for the second time
        _ <- ledger2.exercise(
          party2,
          creator1.exerciseWithKeyCreatorAlternative_DiscloseCreate(_),
        )

        _ <- synchronize(ledger1, ledger2)
      } yield ()
  })

}
