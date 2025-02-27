// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.index

import com.daml.api.util.TimestampConversion
import com.daml.lf.data.{BackStack, FrontStack, FrontStackCons, Ref}
import com.daml.lf.data.Relation.Relation
import com.daml.lf.engine.Blinding
import com.daml.lf.transaction.{CommittedTransaction, NodeId, Transaction => Tx}
import com.daml.lf.transaction.Node.{NodeCreate, NodeExercises}
import com.daml.lf
import com.daml.ledger.api.domain
import com.daml.ledger.api.v1.event.Event
import com.daml.ledger.api.v1.transaction.{
  TreeEvent,
  Transaction => ApiTransaction,
  TransactionTree => ApiTransactionTree,
}
import com.daml.lf.ledger.EventId
import com.daml.platform.api.v1.event.EventOps.EventOps
import com.daml.platform.participant.util.LfEngineToApi.{
  assertOrRuntimeEx,
  lfNodeCreateToEvent,
  lfNodeCreateToTreeEvent,
  lfNodeExercisesToEvent,
  lfNodeExercisesToTreeEvent,
}
import com.daml.platform.store.entries.LedgerEntry

import scala.annotation.tailrec

private[platform] object TransactionConversion {

  private type ContractId = lf.value.Value.ContractId
  private type Transaction = CommittedTransaction
  private type Node = Tx.Node
  private type Create = NodeCreate[ContractId]
  private type Exercise = NodeExercises[NodeId, ContractId]

  private def collect[A](tx: Transaction)(pf: PartialFunction[(NodeId, Node), A]): Seq[A] = {
    def handle(acc: Vector[A], nodeId: NodeId, node: Node): Vector[A] =
      pf.lift((nodeId, node)) match {
        case None => acc
        case Some(a) => acc :+ a
      }
    tx.foldInExecutionOrder(Vector.empty[A])(
      exerciseBegin = (acc, nodeId, node) => (handle(acc, nodeId, node), true),
      rollbackBegin = (acc, _, _) => (acc, false),
      leaf = handle,
      exerciseEnd = (acc, _, _) => acc,
      rollbackEnd = (acc, _, _) => acc,
    )
  }

  private def maskCommandId(
      commandId: Option[Ref.CommandId],
      actAs: List[Ref.Party],
      requestingParties: Set[Ref.Party],
  ): String =
    commandId.filter(_ => actAs.exists(requestingParties)).getOrElse("")

  private def toFlatEvent(
      trId: Ref.TransactionId,
      verbose: Boolean,
  ): PartialFunction[(NodeId, Node), Event] = {
    case (nodeId, node: Create) =>
      assertOrRuntimeEx(
        failureContext = "converting a create node to a created event",
        lfNodeCreateToEvent(verbose, trId, nodeId, node),
      )
    case (nodeId, node: Exercise) if node.consuming =>
      assertOrRuntimeEx(
        failureContext = "converting a consuming exercise node to an archived event",
        lfNodeExercisesToEvent(trId, nodeId, node),
      )
  }

  private def permanent(events: Seq[Event]): Set[String] = {
    events.foldLeft(Set.empty[String]) { (contractIds, event) =>
      if (event.isCreated || !contractIds.contains(event.contractId)) {
        contractIds + event.contractId
      } else {
        contractIds - event.contractId
      }
    }
  }

  // `events` must be in creation order
  private[platform] def removeTransient(events: Seq[Event]): Seq[Event] = {
    val toKeep = permanent(events)
    events.filter(event => toKeep(event.contractId))
  }

  def ledgerEntryToFlatTransaction(
      offset: domain.LedgerOffset.Absolute,
      entry: LedgerEntry.Transaction,
      filter: domain.TransactionFilter,
      verbose: Boolean,
  ): Option[ApiTransaction] = {
    val allFlatEvents = collect(entry.transaction)(toFlatEvent(entry.transactionId, verbose))
    val flatEvents = removeTransient(allFlatEvents)
    val filtered = flatEvents.flatMap(EventFilter(_)(filter).toList)
    val requestingParties = filter.filtersByParty.keySet
    val commandId = maskCommandId(entry.commandId, entry.actAs, requestingParties)
    Some(
      ApiTransaction(
        transactionId = entry.transactionId,
        commandId = commandId,
        workflowId = entry.workflowId.getOrElse(""),
        effectiveAt = Some(TimestampConversion.fromInstant(entry.ledgerEffectiveTime)),
        events = filtered,
        offset = offset.value,
      )
    ).filter(tx => tx.events.nonEmpty || tx.commandId.nonEmpty)
  }

  private def disclosureForParties(
      transaction: Transaction,
      parties: Set[Ref.Party],
  ): Option[Relation[NodeId, Ref.Party]] =
    Some(
      Blinding
        .blind(transaction)
        .disclosure
        .flatMap { case (nodeId, disclosure) =>
          List(disclosure.intersect(parties)).collect {
            case disclosure if disclosure.nonEmpty => nodeId -> disclosure
          }
        }
    ).filter(_.nonEmpty)

  private def isCreateOrExercise(n: Node): Boolean = {
    n match {
      case _: Exercise => true
      case _: Create => true
      case _ => false
    }
  }
  private def toTreeEvent(
      verbose: Boolean,
      trId: Ref.LedgerString,
      disclosure: Relation[NodeId, Ref.Party],
      nodes: Map[NodeId, Node],
  ): PartialFunction[(NodeId, Node), (String, TreeEvent)] = {
    case (nodeId, node: Create) if disclosure.contains(nodeId) =>
      val eventId = EventId(trId, nodeId)
      eventId.toLedgerString -> assertOrRuntimeEx(
        failureContext = "converting a create node to a created event",
        lfNodeCreateToTreeEvent(verbose, eventId, disclosure(nodeId), node),
      )
    case (nodeId, node: Exercise) if disclosure.contains(nodeId) =>
      val eventId = EventId(trId, nodeId)
      eventId.toLedgerString -> assertOrRuntimeEx(
        failureContext = "converting an exercise node to an exercise event",
        lfNodeExercisesToTreeEvent(
          verbose = verbose,
          trId = trId,
          eventId = eventId,
          witnessParties = disclosure(nodeId),
          node = node,
          filterChildren = nid => isCreateOrExercise(nodes(nid)),
        ),
      )
  }

  private def newRoots(
      tx: Transaction,
      disclosed: NodeId => Boolean,
  ) = {

    @tailrec
    def go(toProcess: FrontStack[NodeId], acc: BackStack[NodeId]): Seq[NodeId] =
      toProcess match {
        case FrontStackCons(head, tail) =>
          tx.nodes(head) match {
            case _: Create | _: Exercise if disclosed(head) =>
              go(tail, acc :+ head)
            case exe: Exercise =>
              go(exe.children ++: tail, acc)
            case _ =>
              go(tail, acc)
          }
        case FrontStack() =>
          acc.toImmArray.toSeq
      }

    go(FrontStack(tx.roots), BackStack.empty)
  }

  private def applyDisclosure(
      trId: Ref.LedgerString,
      tx: Transaction,
      disclosure: Relation[NodeId, Ref.Party],
      verbose: Boolean,
  ): Option[ApiTransactionTree] =
    Some(collect(tx)(toTreeEvent(verbose, trId, disclosure, tx.nodes))).collect {
      case events if events.nonEmpty =>
        ApiTransactionTree(
          eventsById = events.toMap,
          rootEventIds = newRoots(tx, disclosure.contains).map(EventId(trId, _).toLedgerString),
        )
    }

  def ledgerEntryToTransactionTree(
      offset: domain.LedgerOffset.Absolute,
      entry: LedgerEntry.Transaction,
      requestingParties: Set[Ref.Party],
      verbose: Boolean,
  ): Option[ApiTransactionTree] = {
    val filteredTree =
      for {
        disclosure <- disclosureForParties(
          entry.transaction,
          requestingParties,
        )
        filteredTree <- applyDisclosure(entry.transactionId, entry.transaction, disclosure, verbose)
      } yield filteredTree

    filteredTree.map(
      _.copy(
        transactionId = entry.transactionId,
        commandId = maskCommandId(entry.commandId, entry.actAs, requestingParties),
        workflowId = entry.workflowId.getOrElse(""),
        effectiveAt = Some(TimestampConversion.fromInstant(entry.ledgerEffectiveTime)),
        offset = offset.value,
      )
    )
  }

}
