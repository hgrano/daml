// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend

import com.daml.ledger.api.domain.{LedgerId, ParticipantId}
import com.daml.lf.data.Ref
import com.daml.platform.common.MismatchException
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.{Failure, Success}

final class StorageBackendPostgresInitializationSpec
    extends AsyncFlatSpec
    with StorageBackendPostgresSpec
    with Matchers {

  behavior of "StorageBackend (initialization)"

  it should "correctly handle repeated initialization" in {
    val ledgerId = LedgerId("ledger")
    val participantId = ParticipantId(Ref.ParticipantId.assertFromString("participant"))
    val otherLedgerId = LedgerId("otherLedger")
    val otherParticipantId = ParticipantId(Ref.ParticipantId.assertFromString("otherParticipant"))

    for {
      result1 <- executeSql(
        storageBackend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = participantId,
          )
        )
      )
      result2 <- executeSql(
        storageBackend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = otherLedgerId,
            participantId = participantId,
          )
        )
      )
      result3 <- executeSql(
        storageBackend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = otherParticipantId,
          )
        )
      )
      result4 <- executeSql(
        storageBackend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = otherLedgerId,
            participantId = otherParticipantId,
          )
        )
      )
      result5 <- executeSql(
        storageBackend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = participantId,
          )
        )
      )
    } yield {
      result1 should matchPattern { case Success(_) => }
      result2 should matchPattern { case Failure(MismatchException.LedgerId(_, _)) => }
      result3 should matchPattern { case Failure(MismatchException.ParticipantId(_, _)) => }
      result4 should matchPattern { case Failure(MismatchException.ParticipantId(_, _)) => }
      result5 should matchPattern { case Success(_) => }
    }
  }

  it should "not allow duplicate initialization" in {
    // This test only works if the storage backend supports exclusive locks
    assume(storageBackend.dbLockSupported)

    val params = StorageBackend.IdentityParams(
      ledgerId = LedgerId("ledger"),
      participantId = ParticipantId(Ref.ParticipantId.assertFromString("participant")),
    )
    val n: Int = 64
    val lockId = storageBackend.lock(1)
    val lockMode = DBLockStorageBackend.LockMode.Exclusive

    for {
      result <- Future.sequence(
        Vector.fill(n)(
          // Note: the StorageBackend.initializeParameters() call is not save to call concurrently,
          // we need an external mechanism to prevent duplicate initialization
          executeSql { conn =>
            storageBackend
              .tryAcquire(lockId, lockMode)(conn)
              .map(lock => {
                val result = storageBackend.initializeParameters(params)(conn)
                storageBackend.release(lock)(conn)
                result
              })
          }
        )
      )
    } yield {
      result.collect { case Some(Failure(_)) =>
        true
      } should have length 0
    }
  }
}
