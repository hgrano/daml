// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.services.tracking

import com.daml.ledger.api.v1.command_service.SubmitAndWaitRequest
import com.daml.ledger.client.services.commands.tracker.CompletionResponse.{
  CompletionFailure,
  CompletionSuccess,
}
import com.daml.logging.LoggingContext

import scala.concurrent.{ExecutionContext, Future}

private[tracking] trait Tracker extends AutoCloseable {

  def track(request: SubmitAndWaitRequest)(implicit
      ec: ExecutionContext,
      loggingContext: LoggingContext,
  ): Future[Either[CompletionFailure, CompletionSuccess]]

}

private[tracking] object Tracker {

  class WithLastSubmission(delegate: Tracker) extends Tracker {

    override def close(): Unit = delegate.close()

    @volatile private var lastSubmission = System.nanoTime()

    def getLastSubmission: Long = lastSubmission

    override def track(request: SubmitAndWaitRequest)(implicit
        ec: ExecutionContext,
        loggingContext: LoggingContext,
    ): Future[Either[CompletionFailure, CompletionSuccess]] = {
      lastSubmission = System.nanoTime()
      delegate.track(request)
    }
  }

  object WithLastSubmission {
    def apply(delegate: Tracker): WithLastSubmission = new WithLastSubmission(delegate)
  }
}
