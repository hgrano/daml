// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.metrics

import java.time.Instant

import com.codahale.metrics.MetricRegistry.MetricSupplier
import com.codahale.metrics._

final class Metrics(val registry: MetricRegistry) {

  private[metrics] def register(name: MetricName, gaugeSupplier: MetricSupplier[Gauge[_]]): Unit =
    registerGauge(name, gaugeSupplier, registry)

  object test {
    private val Prefix: MetricName = MetricName("test")

    val db: DatabaseMetrics = new DatabaseMetrics(registry, Prefix, "db")
  }

  object daml {
    private val Prefix: MetricName = MetricName.DAML

    object commands {
      private val Prefix: MetricName = daml.Prefix :+ "commands"

      val validation: Timer = registry.timer(Prefix :+ "validation")
      val submissions: Timer = registry.timer(Prefix :+ "submissions")
      val submissionsRunning: Meter = registry.meter(Prefix :+ "submissions_running")

      val failedCommandInterpretations: Meter =
        registry.meter(Prefix :+ "failed_command_interpretations")
      val deduplicatedCommands: Meter =
        registry.meter(Prefix :+ "deduplicated_commands")
      val delayedSubmissions: Meter =
        registry.meter(Prefix :+ "delayed_submissions")
      val validSubmissions: Meter =
        registry.meter(Prefix :+ "valid_submissions")

      def inputBufferLength(firstParty: String): Counter =
        registry.counter(Prefix :+ firstParty :+ "input_buffer_length")
      def inputBufferCapacity(firstParty: String): Counter =
        registry.counter(Prefix :+ firstParty :+ "input_buffer_capacity")
      def inputBufferDelay(firstParty: String): Timer =
        registry.timer(Prefix :+ firstParty :+ "input_buffer_delay")
      def maxInFlightLength(firstParty: String): Counter =
        registry.counter(Prefix :+ firstParty :+ "max_in_flight_length")
      def maxInFlightCapacity(firstParty: String): Counter =
        registry.counter(Prefix :+ firstParty :+ "max_in_flight_capacity")
    }

    object execution {
      private val Prefix: MetricName = daml.Prefix :+ "execution"

      val lookupActiveContract: Timer = registry.timer(Prefix :+ "lookup_active_contract")
      val lookupActiveContractPerExecution: Timer =
        registry.timer(Prefix :+ "lookup_active_contract_per_execution")
      val lookupActiveContractCountPerExecution: Histogram =
        registry.histogram(Prefix :+ "lookup_active_contract_count_per_execution")
      val lookupContractKey: Timer = registry.timer(Prefix :+ "lookup_contract_key")
      val lookupContractKeyPerExecution: Timer =
        registry.timer(Prefix :+ "lookup_contract_key_per_execution")
      val lookupContractKeyCountPerExecution: Histogram =
        registry.histogram(Prefix :+ "lookup_contract_key_count_per_execution")
      val getLfPackage: Timer = registry.timer(Prefix :+ "get_lf_package")
      val retry: Meter = registry.meter(Prefix :+ "retry")

      // Total time for command execution (including data fetching)
      val total: Timer = registry.timer(Prefix :+ "total")
      val totalRunning: Meter = registry.meter(Prefix :+ "total_running")

      // Commands being executed by the engine (not currently fetching data)
      val engineRunning: Meter = registry.meter(Prefix :+ "engine_running")

      object cache {
        private val Prefix: MetricName = execution.Prefix :+ "cache"

        val keyState: CacheMetrics = new CacheMetrics(registry, Prefix :+ "key_state")
        val contractState: CacheMetrics =
          new CacheMetrics(registry, Prefix :+ "contract_state")

        val registerCacheUpdate: Timer = registry.timer(Prefix :+ "register_update")

        val dispatcherLag: Timer = registry.timer(Prefix :+ "dispatcher_lag")

        val resolveDivulgenceLookup: Counter =
          registry.counter(Prefix :+ "resolve_divulgence_lookup")

        val resolveFullLookup: Counter =
          registry.counter(Prefix :+ "resolve_full_lookup")

        val readThroughNotFound: Counter = registry.counter(Prefix :+ "read_through_not_found")

        val indexSequentialId = new VarGauge[Long](0L)
        registry.register(
          Prefix :+ "index_sequential_id",
          indexSequentialId,
        )
      }
    }

    object kvutils {
      private val Prefix: MetricName = daml.Prefix :+ "kvutils"

      object committer {
        private val Prefix: MetricName = kvutils.Prefix :+ "committer"

        // Timer (and count) of how fast submissions have been processed.
        val runTimer: Timer = registry.timer(Prefix :+ "run_timer")

        // Counter to monitor how many at a time and when kvutils is processing a submission.
        val processing: Counter = registry.counter(Prefix :+ "processing")

        def runTimer(committerName: String): Timer =
          registry.timer(Prefix :+ committerName :+ "run_timer")
        def preExecutionRunTimer(committerName: String): Timer =
          registry.timer(Prefix :+ committerName :+ "preexecution_run_timer")
        def stepTimer(committerName: String, stepName: String): Timer =
          registry.timer(Prefix :+ committerName :+ "step_timers" :+ stepName)

        object last {
          private val Prefix: MetricName = committer.Prefix :+ "last"

          val lastRecordTimeGauge = new VarGauge[String]("<none>")
          registry.register(Prefix :+ "record_time", lastRecordTimeGauge)

          val lastEntryIdGauge = new VarGauge[String]("<none>")
          registry.register(Prefix :+ "entry_id", lastEntryIdGauge)

          val lastParticipantIdGauge = new VarGauge[String]("<none>")
          registry.register(Prefix :+ "participant_id", lastParticipantIdGauge)

          val lastExceptionGauge = new VarGauge[String]("<none>")
          registry.register(Prefix :+ "exception", lastExceptionGauge)
        }

        object config {
          private val Prefix: MetricName = committer.Prefix :+ "config"

          val accepts: Counter = registry.counter(Prefix :+ "accepts")
          val rejections: Counter = registry.counter(Prefix :+ "rejections")
        }

        object packageUpload {
          private val Prefix: MetricName = committer.Prefix :+ "package_upload"

          val validateTimer: Timer = registry.timer(Prefix :+ "validate_timer")
          val preloadTimer: Timer = registry.timer(Prefix :+ "preload_timer")
          val decodeTimer: Timer = registry.timer(Prefix :+ "decode_timer")
          val accepts: Counter = registry.counter(Prefix :+ "accepts")
          val rejections: Counter = registry.counter(Prefix :+ "rejections")

          def loadedPackages(value: () => Int): Unit = {
            register(Prefix :+ "loaded_packages", () => () => value())
          }
        }

        object partyAllocation {
          private val Prefix: MetricName = committer.Prefix :+ "party_allocation"

          val accepts: Counter = registry.counter(Prefix :+ "accepts")
          val rejections: Counter = registry.counter(Prefix :+ "rejections")
        }

        object transaction {
          private val Prefix: MetricName = committer.Prefix :+ "transaction"

          val runTimer: Timer = registry.timer(Prefix :+ "run_timer")
          val interpretTimer: Timer = registry.timer(Prefix :+ "interpret_timer")
          val accepts: Counter = registry.counter(Prefix :+ "accepts")

          def rejection(name: String): Counter =
            registry.counter(Prefix :+ s"rejections_$name")
        }
      }

      object reader {
        private val Prefix: MetricName = kvutils.Prefix :+ "reader"

        val openEnvelope: Timer = registry.timer(Prefix :+ "open_envelope")
        val parseUpdates: Timer = registry.timer(Prefix :+ "parse_updates")
      }

      object submission {
        private val Prefix: MetricName = kvutils.Prefix :+ "submission"

        object conversion {
          private val Prefix: MetricName = submission.Prefix :+ "conversion"

          val transactionOutputs: Timer =
            registry.timer(Prefix :+ "transaction_outputs")
          val transactionToSubmission: Timer =
            registry.timer(Prefix :+ "transaction_to_submission")
          val archivesToSubmission: Timer =
            registry.timer(Prefix :+ "archives_to_submission")
          val partyToSubmission: Timer =
            registry.timer(Prefix :+ "party_to_submission")
          val configurationToSubmission: Timer =
            registry.timer(Prefix :+ "configuration_to_submission")
        }

        object validator {
          private val Prefix: MetricName = submission.Prefix :+ "validator"

          val openEnvelope: Timer = registry.timer(Prefix :+ "open_envelope")
          val fetchInputs: Timer = registry.timer(Prefix :+ "fetch_inputs")
          val validate: Timer = registry.timer(Prefix :+ "validate")
          val commit: Timer = registry.timer(Prefix :+ "commit")
          val transformSubmission: Timer = registry.timer(Prefix :+ "transform_submission")

          val acquireTransactionLock: Timer = registry.timer(Prefix :+ "acquire_transaction_lock")
          val failedToAcquireTransaction: Timer =
            registry.timer(Prefix :+ "failed_to_acquire_transaction")
          val releaseTransactionLock: Timer = registry.timer(Prefix :+ "release_transaction_lock")

          val stateValueCache = new CacheMetrics(registry, Prefix :+ "state_value_cache")

          // The below metrics are only generated during parallel validation.
          // The counters track how many submissions we're processing in parallel.
          val batchSizes: Histogram = registry.histogram(Prefix :+ "batch_sizes")
          val receivedBatchSubmissionBytes: Histogram =
            registry.histogram(Prefix :+ "received_batch_submission_bytes")
          val receivedSubmissionBytes: Histogram =
            registry.histogram(Prefix :+ "received_submission_bytes")

          val validateAndCommit: Timer = registry.timer(Prefix :+ "validate_and_commit")
          val decode: Timer = registry.timer(Prefix :+ "decode")
          val detectConflicts: Timer = registry.timer(Prefix :+ "detect_conflicts")

          val decodeRunning: Counter = registry.counter(Prefix :+ "decode_running")
          val fetchInputsRunning: Counter = registry.counter(Prefix :+ "fetch_inputs_running")
          val validateRunning: Counter = registry.counter(Prefix :+ "validate_running")
          val commitRunning: Counter = registry.counter(Prefix :+ "commit_running")

          // The below metrics are only generated for pre-execution.
          val validatePreExecute: Timer = registry.timer(Prefix :+ "validate_pre_execute")
          val generateWriteSets: Timer = registry.timer(Prefix :+ "generate_write_sets")

          val validatePreExecuteRunning: Counter =
            registry.counter(Prefix :+ "validate_pre_execute_running")
        }
      }

      object writer {
        private val Prefix: MetricName = kvutils.Prefix :+ "writer"

        val commit: Timer = registry.timer(Prefix :+ "commit")

        val preExecutedCount: Counter = registry.counter(Prefix :+ "pre_executed_count")
        val preExecutedInterpretationCosts: Histogram =
          registry.histogram(Prefix :+ "pre_executed_interpretation_costs")
        val committedCount: Counter = registry.counter(Prefix :+ "committed_count")
        val committedInterpretationCosts: Histogram =
          registry.histogram(Prefix :+ "committed_interpretation_costs")
      }

      object conflictdetection {
        private val Prefix = kvutils.Prefix :+ "conflict_detection"

        val accepted: Counter =
          registry.counter(Prefix :+ "accepted")

        val conflicted: Counter =
          registry.counter(Prefix :+ "conflicted")

        val removedTransientKey: Counter =
          registry.counter(Prefix :+ "removed_transient_key")

        val recovered: Counter =
          registry.counter(Prefix :+ "recovered")

        val dropped: Counter =
          registry.counter(Prefix :+ "dropped")
      }
    }

    object lapi {
      private val Prefix: MetricName = daml.Prefix :+ "lapi"

      def forMethod(name: String): Timer = registry.timer(Prefix :+ name)

      object threadpool {
        private val Prefix: MetricName = lapi.Prefix :+ "threadpool"

        val apiServices: MetricName = Prefix :+ "api-services"
      }

      object streams {
        private val Prefix: MetricName = lapi.Prefix :+ "streams"

        val transactionTrees: Counter = registry.counter(Prefix :+ "transaction_trees_sent")
        val transactions: Counter = registry.counter(Prefix :+ "transactions_sent")
        val completions: Counter = registry.counter(Prefix :+ "completions_sent")
        val acs: Counter = registry.counter(Prefix :+ "acs_sent")
      }
    }

    object ledger {
      private val Prefix: MetricName = daml.Prefix :+ "ledger"

      object database {
        private val Prefix: MetricName = ledger.Prefix :+ "database"

        object queries {
          private val Prefix: MetricName = database.Prefix :+ "queries"

          val selectLatestLogEntryId: Timer = registry.timer(Prefix :+ "select_latest_log_entry_id")
          val selectFromLog: Timer = registry.timer(Prefix :+ "select_from_log")
          val selectStateValuesByKeys: Timer =
            registry.timer(Prefix :+ "select_state_values_by_keys")
          val updateOrRetrieveLedgerId: Timer =
            registry.timer(Prefix :+ "update_or_retrieve_ledger_id")
          val insertRecordIntoLog: Timer = registry.timer(Prefix :+ "insert_record_into_log")
          val updateState: Timer = registry.timer(Prefix :+ "update_state")
          val truncate: Timer = registry.timer(Prefix :+ "truncate")
        }

        object transactions {
          private val Prefix: MetricName = database.Prefix :+ "transactions"

          def acquireConnection(name: String): Timer =
            registry.timer(Prefix :+ name :+ "acquire_connection")
          def run(name: String): Timer =
            registry.timer(Prefix :+ name :+ "run")
        }
      }

      object log {
        private val Prefix: MetricName = ledger.Prefix :+ "log"

        val append: Timer = registry.timer(Prefix :+ "append")
        val read: Timer = registry.timer(Prefix :+ "read")
      }

      object state {
        private val Prefix: MetricName = ledger.Prefix :+ "state"

        val read: Timer = registry.timer(Prefix :+ "read")
        val write: Timer = registry.timer(Prefix :+ "write")
      }
    }

    object index {
      private val Prefix = daml.Prefix :+ "index"

      val decodeStateEvent: Timer = registry.timer(Prefix :+ "decode_state_event")
      val lookupContract: Timer = registry.timer(Prefix :+ "lookup_contract")
      val lookupKey: Timer = registry.timer(Prefix :+ "lookup_key")
      val lookupFlatTransactionById: Timer =
        registry.timer(Prefix :+ "lookup_flat_transaction_by_id")
      val lookupTransactionTreeById: Timer =
        registry.timer(Prefix :+ "lookup_transaction_tree_by_id")
      val lookupLedgerConfiguration: Timer = registry.timer(Prefix :+ "lookup_ledger_configuration")
      val lookupMaximumLedgerTime: Timer = registry.timer(Prefix :+ "lookup_maximum_ledger_time")
      val getParties: Timer = registry.timer(Prefix :+ "get_parties")
      val listKnownParties: Timer = registry.timer(Prefix :+ "list_known_parties")
      val listLfPackages: Timer = registry.timer(Prefix :+ "list_lf_packages")
      val getLfArchive: Timer = registry.timer(Prefix :+ "get_lf_archive")
      val getLfPackage: Timer = registry.timer(Prefix :+ "get_lf_package")
      val deduplicateCommand: Timer = registry.timer(Prefix :+ "deduplicate_command")
      val removeExpiredDeduplicationData: Timer =
        registry.timer(Prefix :+ "remove_expired_deduplication_data")
      val stopDeduplicatingCommand: Timer =
        registry.timer(Prefix :+ "stop_deduplicating_command")
      val prune: Timer = registry.timer(Prefix :+ "prune")

      val publishTransaction: Timer = registry.timer(Prefix :+ "publish_transaction")
      val publishPartyAllocation: Timer = registry.timer(Prefix :+ "publish_party_allocation")
      val uploadPackages: Timer = registry.timer(Prefix :+ "upload_packages")
      val publishConfiguration: Timer = registry.timer(Prefix :+ "publish_configuration")

      val decodeTransactionLogUpdate: Timer =
        registry.timer(Prefix :+ "transaction_log_update_decode")
      val transactionLogUpdatesBufferSize: Counter =
        registry.counter(Prefix :+ "transaction_log_updates_buffer_size")

      val contractStateEventsBufferSize: Counter =
        registry.counter(Prefix :+ "contract_state_events_buffer_size")

      // FIXME Name mushing and inconsistencies here, tracked by https://github.com/digital-asset/daml/issues/5926
      object db {
        private val Prefix: MetricName = index.Prefix :+ "db"

        val storePartyEntry: Timer = registry.timer(Prefix :+ "store_party_entry")
        val storeInitialState: Timer = registry.timer(Prefix :+ "store_initial_state")
        val storePackageEntry: Timer = registry.timer(Prefix :+ "store_package_entry")

        val storeTransaction: Timer = registry.timer(Prefix :+ "store_ledger_entry")
        val storeTransactionCombined: Timer =
          registry.timer(Prefix :+ "store_ledger_entry_combined")
        val storeTransactionEvents: Timer = registry.timer(Prefix :+ "store_ledger_entry_events")
        val storeTransactionState: Timer = registry.timer(Prefix :+ "store_ledger_entry_state")
        val storeTransactionCompletion: Timer =
          registry.timer(Prefix :+ "store_ledger_entry_completion")

        val storeRejection: Timer = registry.timer(Prefix :+ "store_rejection")
        val storeConfigurationEntry: Timer = registry.timer(Prefix :+ "store_configuration_entry")

        val lookupLedgerId: Timer = registry.timer(Prefix :+ "lookup_ledger_id")
        val lookupParticipantId: Timer = registry.timer(Prefix :+ "lookup_participant_id")
        val lookupLedgerEnd: Timer = registry.timer(Prefix :+ "lookup_ledger_end")
        val lookupLedgerEndSequentialId: Timer =
          registry.timer(Prefix :+ "lookup_ledger_end_sequential_id")
        val lookupTransaction: Timer = registry.timer(Prefix :+ "lookup_transaction")
        val lookupLedgerConfiguration: Timer =
          registry.timer(Prefix :+ "lookup_ledger_configuration")
        val lookupKey: Timer = registry.timer(Prefix :+ "lookup_key")
        val lookupActiveContract: Timer = registry.timer(Prefix :+ "lookup_active_contract")
        val lookupMaximumLedgerTime: Timer = registry.timer(Prefix :+ "lookup_maximum_ledger_time")
        val getParties: Timer = registry.timer(Prefix :+ "get_parties")
        val listKnownParties: Timer = registry.timer(Prefix :+ "list_known_parties")
        val listLfPackages: Timer = registry.timer(Prefix :+ "list_lf_packages")
        val getLfArchive: Timer = registry.timer(Prefix :+ "get_lf_archive")
        val deduplicateCommand: Timer = registry.timer(Prefix :+ "deduplicate_command")
        val removeExpiredDeduplicationData: Timer =
          registry.timer(Prefix :+ "remove_expired_deduplication_data")
        val stopDeduplicatingCommand: Timer =
          registry.timer(Prefix :+ "stop_deduplicating_command")
        val prune: Timer = registry.timer(Prefix :+ "prune")

        private val createDbMetrics: String => DatabaseMetrics =
          new DatabaseMetrics(registry, Prefix, _)

        private val overall = createDbMetrics("all")
        val waitAll: Timer = overall.waitTimer
        val execAll: Timer = overall.executionTimer

        val getCompletions: DatabaseMetrics = createDbMetrics("get_completions")
        val getLedgerId: DatabaseMetrics = createDbMetrics("get_ledger_id")
        val getParticipantId: DatabaseMetrics = createDbMetrics("get_participant_id")
        val getLedgerEnd: DatabaseMetrics = createDbMetrics("get_ledger_end")
        val getLedgerEndOffsetAndSequentialId: DatabaseMetrics = createDbMetrics(
          "get_ledger_end_offset_and_sequential_id"
        )
        val getInitialLedgerEnd: DatabaseMetrics = createDbMetrics("get_initial_ledger_end")
        val initializeLedgerParameters: DatabaseMetrics = createDbMetrics(
          "initialize_ledger_parameters"
        )
        val initializeParticipantId: DatabaseMetrics = createDbMetrics("initialize_participant_id")
        val lookupConfiguration: DatabaseMetrics = createDbMetrics("lookup_configuration")
        val loadConfigurationEntries: DatabaseMetrics = createDbMetrics(
          "load_configuration_entries"
        )
        val storeConfigurationEntryDbMetrics: DatabaseMetrics = createDbMetrics(
          "store_configuration_entry"
        ) // FIXME Base name conflicts with storeConfigurationEntry
        val storePartyEntryDbMetrics: DatabaseMetrics = createDbMetrics(
          "store_party_entry"
        ) // FIXME Base name conflicts with storePartyEntry
        val loadPartyEntries: DatabaseMetrics = createDbMetrics("load_party_entries")
        val getTransactionLogUpdates: DatabaseMetrics = createDbMetrics(
          "get_transaction_log_updates"
        )

        object storeTransactionDbMetrics
            extends DatabaseMetrics(registry, Prefix, "store_ledger_entry") {
          // outside of SQL transaction
          val prepareBatches: Timer = registry.timer(dbPrefix :+ "prepare_batches")

          // in order within SQL transaction
          val commitValidation: Timer = registry.timer(dbPrefix :+ "commit_validation")
          val eventsBatch: Timer = registry.timer(dbPrefix :+ "events_batch")
          val deleteContractWitnessesBatch: Timer =
            registry.timer(dbPrefix :+ "delete_contract_witnesses_batch")
          val deleteContractsBatch: Timer = registry.timer(dbPrefix :+ "delete_contracts_batch")
          val nullifyPastKeysBatch: Timer =
            registry.timer(dbPrefix :+ "nullify_contract_keys_batch")
          val insertContractsBatch: Timer = registry.timer(dbPrefix :+ "insert_contracts_batch")
          val insertContractWitnessesBatch: Timer =
            registry.timer(dbPrefix :+ "insert_contract_witnesses_batch")

          val insertCompletion: Timer = registry.timer(dbPrefix :+ "insert_completion")
          val updateLedgerEnd: Timer = registry.timer(dbPrefix :+ "update_ledger_end")
        }
        val storeRejectionDbMetrics: DatabaseMetrics = createDbMetrics(
          "store_rejection"
        ) // FIXME Base name conflicts with storeRejection
        val storeInitialStateFromScenario: DatabaseMetrics = createDbMetrics(
          "store_initial_state_from_scenario"
        )
        val loadParties: DatabaseMetrics = createDbMetrics("load_parties")
        val loadAllParties: DatabaseMetrics = createDbMetrics("load_all_parties")
        val loadPackages: DatabaseMetrics = createDbMetrics("load_packages")
        val loadArchive: DatabaseMetrics = createDbMetrics("load_archive")
        val storePackageEntryDbMetrics: DatabaseMetrics = createDbMetrics(
          "store_package_entry"
        ) // FIXME Base name conflicts with storePackageEntry
        val loadPackageEntries: DatabaseMetrics = createDbMetrics("load_package_entries")
        val deduplicateCommandDbMetrics: DatabaseMetrics = createDbMetrics(
          "deduplicate_command"
        ) // FIXME Base name conflicts with deduplicateCommand
        val removeExpiredDeduplicationDataDbMetrics: DatabaseMetrics = createDbMetrics(
          "remove_expired_deduplication_data"
        ) // FIXME Base name conflicts with removeExpiredDeduplicationData
        val stopDeduplicatingCommandDbMetrics: DatabaseMetrics = createDbMetrics(
          "stop_deduplicating_command"
        ) // FIXME Base name conflicts with stopDeduplicatingCommand
        val pruneDbMetrics: DatabaseMetrics = createDbMetrics(
          "prune"
        ) // FIXME Base name conflicts with prune
        val truncateAllTables: DatabaseMetrics = createDbMetrics("truncate_all_tables")
        val lookupActiveContractDbMetrics: DatabaseMetrics = createDbMetrics(
          "lookup_active_contract"
        ) // FIXME Base name conflicts with lookupActiveContract
        val lookupContractByKeyDbMetrics: DatabaseMetrics = createDbMetrics(
          "lookup_contract_by_key"
        )
        val lookupMaximumLedgerTimeDbMetrics: DatabaseMetrics = createDbMetrics(
          "lookup_maximum_ledger_time"
        ) // FIXME Base name conflicts with lookupActiveContract
        val getFlatTransactions: DatabaseMetrics = createDbMetrics("get_flat_transactions")
        val lookupFlatTransactionById: DatabaseMetrics = createDbMetrics(
          "lookup_flat_transaction_by_id"
        )
        val getTransactionTrees: DatabaseMetrics = createDbMetrics("get_transaction_trees")
        val lookupTransactionTreeById: DatabaseMetrics = createDbMetrics(
          "lookup_transaction_tree_by_id"
        )
        val getActiveContracts: DatabaseMetrics = createDbMetrics("get_active_contracts")
        val getEventSeqIdRange: DatabaseMetrics = createDbMetrics("get_event_sequential_id_range")
        val getAcsEventSeqIdRange: DatabaseMetrics =
          createDbMetrics("get_acs_event_sequential_id_range")
        val getContractStateEvents: DatabaseMetrics = createDbMetrics(
          "get_contract_state_events"
        )

        object translation {
          private val Prefix: MetricName = db.Prefix :+ "translation"
          val cache = new CacheMetrics(registry, Prefix :+ "cache")
          val getLfPackage: Timer = registry.timer(Prefix :+ "get_lf_package")
        }

        object compression {
          private val Prefix: MetricName = db.Prefix :+ "compression"

          val createArgumentCompressed: Histogram =
            registry.histogram(Prefix :+ "create_argument_compressed")
          val createArgumentUncompressed: Histogram =
            registry.histogram(Prefix :+ "create_argument_uncompressed")
          val createKeyValueCompressed: Histogram =
            registry.histogram(Prefix :+ "create_key_value_compressed")
          val createKeyValueUncompressed: Histogram =
            registry.histogram(Prefix :+ "create_key_value_uncompressed")
          val exerciseArgumentCompressed: Histogram =
            registry.histogram(Prefix :+ "exercise_argument_compressed")
          val exerciseArgumentUncompressed: Histogram =
            registry.histogram(Prefix :+ "exercise_argument_uncompressed")
          val exerciseResultCompressed: Histogram =
            registry.histogram(Prefix :+ "exercise_result_compressed")
          val exerciseResultUncompressed: Histogram =
            registry.histogram(Prefix :+ "exercise_result_uncompressed")

        }

      }
    }

    object indexer {
      private val Prefix: MetricName = daml.Prefix :+ "indexer"

      val lastReceivedRecordTime = new VarGauge[Long](0)
      registry.register(Prefix :+ "last_received_record_time", lastReceivedRecordTime)

      val lastReceivedOffset = new VarGauge[String]("<none>")
      registry.register(Prefix :+ "last_received_offset", lastReceivedOffset)

      registerGauge(
        Prefix :+ "current_record_time_lag",
        () => () => Instant.now().toEpochMilli - lastReceivedRecordTime.getValue,
        registry,
      )

      val stateUpdateProcessing: Timer = registry.timer(Prefix :+ "processed_state_updates")

      val ledgerEndSequentialId = new VarGauge[Long](0L)
      registry.register(Prefix :+ "ledger_end_sequential_id", ledgerEndSequentialId)
    }

    // TODO append-only: streamline metrics upon cleanup
    object parallelIndexer {
      private val Prefix: MetricName = daml.Prefix :+ "parallel_indexer"

      val initialization = new DatabaseMetrics(registry, Prefix, "initialization")

      // Number of state updates persisted to the database
      // (after the effect of the corresponding Update is persisted into the database,
      // and before this effect is visible via moving the ledger end forward)
      val updates: Counter = registry.counter(Prefix :+ "updates")

      // The size of the queue before the indexer
      val inputBufferLength: Counter = registry.counter(Prefix :+ "input_buffer_length")

      // Input mapping stage
      // Translating state updates to data objects corresponding to individual SQL insert statements
      object inputMapping {
        private val Prefix: MetricName = parallelIndexer.Prefix :+ "inputmapping"

        // Bundle of metrics coming from instrumentation of the underlying thread-pool
        val executor: MetricName = Prefix :+ "executor"

        // The latency, which during an update element is residing in the mapping-stage.
        // Since batches are involved, this duration is divided by the batch size.
        val duration: Timer = registry.timer(Prefix :+ "duration")

        // The batch size, i.e., the number of state updates per database submission
        val batchSize: Histogram = registry.histogram(Prefix :+ "batch_size")
      }

      // Batching stage
      // Translating batch data objects to db-specific DTO batches
      object batching {
        private val Prefix: MetricName = parallelIndexer.Prefix :+ "batching"

        // Bundle of metrics coming from instrumentation of the underlying thread-pool
        val executor: MetricName = Prefix :+ "executor"

        // The latency, which during an update element is residing in the batching-stage.
        // Since batches are involved, this duration is divided by the batch size.
        val duration: Timer = registry.timer(Prefix :+ "duration")
      }

      // Ingestion stage
      // Parallel ingestion of prepared data into the database
      object ingestion extends DatabaseMetrics(registry, Prefix, "ingestion") {
        private val Prefix: MetricName = parallelIndexer.Prefix :+ "ingestion"

        // The latency, which during an update element is residing in the ingestion.
        // Since batches are involved, this duration is divided by the batch size.
        val duration: Timer = registry.timer(Prefix :+ "duration")
      }

      // Tail ingestion stage
      // The throttled update of ledger end parameters
      val tailIngestion = new DatabaseMetrics(registry, Prefix, "tail_ingestion")
    }

    object services {
      private val Prefix: MetricName = daml.Prefix :+ "services"

      object index {
        private val Prefix: MetricName = services.Prefix :+ "index"

        val listLfPackages: Timer = registry.timer(Prefix :+ "list_lf_packages")
        val getLfArchive: Timer = registry.timer(Prefix :+ "get_lf_archive")
        val getLfPackage: Timer = registry.timer(Prefix :+ "get_lf_package")
        val packageEntries: Timer = registry.timer(Prefix :+ "package_entries")
        val getLedgerConfiguration: Timer = registry.timer(Prefix :+ "get_ledger_configuration")
        val currentLedgerEnd: Timer = registry.timer(Prefix :+ "current_ledger_end")
        val getCompletions: Timer = registry.timer(Prefix :+ "get_completions")
        val transactions: Timer = registry.timer(Prefix :+ "transactions")
        val transactionTrees: Timer = registry.timer(Prefix :+ "transaction_trees")
        val getTransactionById: Timer = registry.timer(Prefix :+ "get_transaction_by_id")
        val getTransactionTreeById: Timer = registry.timer(Prefix :+ "get_transaction_tree_by_id")
        val getActiveContracts: Timer = registry.timer(Prefix :+ "get_active_contracts")
        val lookupActiveContract: Timer = registry.timer(Prefix :+ "lookup_active_contract")
        val lookupContractKey: Timer = registry.timer(Prefix :+ "lookup_contract_key")
        val lookupMaximumLedgerTime: Timer = registry.timer(Prefix :+ "lookup_maximum_ledger_time")
        val getLedgerId: Timer = registry.timer(Prefix :+ "get_ledger_id")
        val getParticipantId: Timer = registry.timer(Prefix :+ "get_participant_id")
        val getParties: Timer = registry.timer(Prefix :+ "get_parties")
        val listKnownParties: Timer = registry.timer(Prefix :+ "list_known_parties")
        val partyEntries: Timer = registry.timer(Prefix :+ "party_entries")
        val lookupConfiguration: Timer = registry.timer(Prefix :+ "lookup_configuration")
        val configurationEntries: Timer = registry.timer(Prefix :+ "configuration_entries")
        val deduplicateCommand: Timer = registry.timer(Prefix :+ "deduplicate_command")
        val stopDeduplicateCommand: Timer = registry.timer(Prefix :+ "stop_deduplicating_command")
        val prune: Timer = registry.timer(Prefix :+ "prune")

        object streamsBuffer {
          private val Prefix: MetricName = index.Prefix :+ "streams_buffer"

          def push(qualifier: String): Timer = registry.timer(Prefix :+ qualifier :+ "push")
          def slice(qualifier: String): Timer = registry.timer(Prefix :+ qualifier :+ "slice")
          def prune(qualifier: String): Timer = registry.timer(Prefix :+ qualifier :+ "prune")

          val transactionTreesTotal: Counter =
            registry.counter(Prefix :+ "transaction_trees_total")
          val transactionTreesBuffered: Counter =
            registry.counter(Prefix :+ "transaction_trees_buffered")

          val flatTransactionsTotal: Counter =
            registry.counter(Prefix :+ "flat_transactions_total")
          val flatTransactionsBuffered: Counter =
            registry.counter(Prefix :+ "flat_transactions_buffered")

          val getTransactionTrees: Timer =
            registry.timer(Prefix :+ "get_transaction_trees")
          val getFlatTransactions: Timer =
            registry.timer(Prefix :+ "get_flat_transactions")
        }

        val transactionTreesBufferSize: Counter =
          registry.counter(Prefix :+ "transaction_trees_buffer_size")
        val flatTransactionsBufferSize: Counter =
          registry.counter(Prefix :+ "flat_transactions_buffer_size")

        val getContractStateEventsChunkSize: Histogram =
          registry.histogram(Prefix :+ "get_contract_state_events_chunk_fetch_size")
        val getTransactionLogUpdatesChunkSize: Histogram =
          registry.histogram(Prefix :+ "get_transaction_log_updates_chunk_fetch_size")
      }

      object read {
        private val Prefix: MetricName = services.Prefix :+ "read"

        val getLedgerInitialConditions: Timer =
          registry.timer(Prefix :+ "get_ledger_initial_conditions")
        val stateUpdates: Timer = registry.timer(Prefix :+ "state_updates")
      }

      object write {
        private val Prefix: MetricName = services.Prefix :+ "write"

        val submitTransaction: Timer = registry.timer(Prefix :+ "submit_transaction")
        val submitTransactionRunning: Meter = registry.meter(Prefix :+ "submit_transaction_running")
        val uploadPackages: Timer = registry.timer(Prefix :+ "upload_packages")
        val allocateParty: Timer = registry.timer(Prefix :+ "allocate_party")
        val submitConfiguration: Timer = registry.timer(Prefix :+ "submit_configuration")
        val prune: Timer = registry.timer(Prefix :+ "prune")
      }
    }

    object HttpJsonApi {
      private val Prefix: MetricName = daml.Prefix :+ "http_json_api"

      // Meters how long processing of a command submission request takes
      val commandSubmissionTimer: Timer = registry.timer(Prefix :+ "command_submission_timing")
      // Meters how long processing of a query GET request takes
      val queryAllTimer: Timer = registry.timer(Prefix :+ "query_all_timing")
      // Meters how long processing of a query POST request takes
      val queryMatchingTimer: Timer = registry.timer(Prefix :+ "query_matching_timing")
      // Meters how long processing of a fetch request takes
      val fetchTimer: Timer = registry.timer(Prefix :+ "fetch_timing")
      // Meters how long processing of a get party/parties request takes
      val getPartyTimer: Timer = registry.timer(Prefix :+ "get_party_timing")
      // Meters how long processing of a party management request takes
      val allocatePartyTimer: Timer = registry.timer(Prefix :+ "allocate_party_timing")
      // Meters how long processing of a package download request takes
      val downloadPackageTimer: Timer = registry.timer(Prefix :+ "download_package_timing")
      // Meters how long processing of a package upload request takes
      val uploadPackageTimer: Timer = registry.timer(Prefix :+ "upload_package_timing")
      // Meters how long parsing and decoding of an incoming json payload takes
      val incomingJsonParsingAndValidationTimer: Timer =
        registry.timer(Prefix :+ "incoming_json_parsing_and_validation_timing")
      // Meters how long the construction of the response json payload takes
      val responseCreationTimer: Timer = registry.timer(Prefix :+ "response_creation_timing")
      // Meters how long a find by contract key database operation takes
      val dbFindByContractKey: Timer = registry.timer(Prefix :+ "db_find_by_contract_key_timing")
      // Meters how long a find by contract id database operation takes
      val dbFindByContractId: Timer = registry.timer(Prefix :+ "db_find_by_contract_id_timing")
      // Meters how long processing of the command submission request takes on the ledger
      val commandSubmissionLedgerTimer: Timer =
        registry.timer(Prefix :+ "command_submission_ledger_timing")
      // Meters http requests throughput
      val httpRequestThroughput: Meter = registry.meter(Prefix :+ "http_request_throughput")
      // Meters how many websocket connections are currently active
      val websocketRequestCounter: Counter = registry.counter(Prefix :+ "websocket_request_count")
      // Meters command submissions throughput
      val commandSubmissionThroughput: Meter =
        registry.meter(Prefix :+ "command_submission_throughput")
      // Meters package uploads throughput
      val uploadPackagesThroughput: Meter = registry.meter(Prefix :+ "upload_packages_throughput")
      // Meters party allocation throughput
      val allocatePartyThroughput: Meter = registry.meter(Prefix :+ "allocation_party_throughput")
    }
  }
}
