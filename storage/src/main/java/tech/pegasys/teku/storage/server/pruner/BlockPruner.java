/*
 * Copyright Consensys Software Inc., 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.pruner;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class BlockPruner extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final Duration pruneInterval;
  private final int pruneLimit;
  private final SettableLabelledGauge pruningTimingsLabelledGauge;
  private final SettableLabelledGauge pruningActiveLabelledGauge;
  private final String pruningMetricsType;
  private final Duration pruningWarnTimeout;

  private Optional<Cancellable> scheduledPruner = Optional.empty();

  public BlockPruner(
      final Spec spec,
      final Database database,
      final AsyncRunner asyncRunner,
      final Duration pruneInterval,
      final int pruneLimit,
      final String pruningMetricsType,
      final SettableLabelledGauge pruningTimingsLabelledGauge,
      final SettableLabelledGauge pruningActiveLabelledGauge,
      final Duration pruningWarnTimeout) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.pruneInterval = pruneInterval;
    this.pruningMetricsType = pruningMetricsType;
    this.pruningTimingsLabelledGauge = pruningTimingsLabelledGauge;
    this.pruningActiveLabelledGauge = pruningActiveLabelledGauge;
    this.pruneLimit = pruneLimit;
    this.pruningWarnTimeout = pruningWarnTimeout;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    scheduledPruner =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                () -> {
                  pruningActiveLabelledGauge.set(1, pruningMetricsType);
                  final long start = System.currentTimeMillis();
                  pruneBlocks();
                  final long elapsed = System.currentTimeMillis() - start;
                  pruningTimingsLabelledGauge.set(elapsed, pruningMetricsType);
                  pruningActiveLabelledGauge.set(0, pruningMetricsType);
                  if (elapsed > pruningWarnTimeout.toMillis()) {
                    LOG.warn(
                        "Pruning task for {} took {} ms, exceeding the warn threshold of {} ms",
                        pruningMetricsType,
                        elapsed,
                        pruningWarnTimeout.toMillis());
                  }
                },
                Duration.ZERO,
                pruneInterval,
                error -> LOG.error("Failed to prune old blocks", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    scheduledPruner.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  private void pruneBlocks() {
    final Optional<Checkpoint> finalizedCheckpoint = database.getFinalizedCheckpoint();
    if (finalizedCheckpoint.isEmpty()) {
      LOG.debug("Not pruning as no finalized checkpoint is available.");
      return;
    }
    final UInt64 finalizedEpoch = finalizedCheckpoint.get().getEpoch();
    final UInt64 earliestEpochToKeep =
        finalizedEpoch.minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
    final UInt64 earliestSlotToKeep = spec.computeStartSlotAtEpoch(earliestEpochToKeep);
    final UInt64 checkpointEarliestSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    if (earliestSlotToKeep.isZero()) {
      LOG.debug("Pruning is not performed as the epochs to retain include the genesis epoch.");
      return;
    }
    LOG.debug("Initiating pruning of finalized blocks prior to slot {}.", earliestSlotToKeep);
    try {
      final long start = System.currentTimeMillis();
      final UInt64 lastPrunedSlot =
          database.pruneFinalizedBlocks(
              earliestSlotToKeep.decrement(), pruneLimit, checkpointEarliestSlot);
      LOG.debug(
          "Pruned finalized blocks prior to slot {}, last pruned slot was {}, completed in {} ms.",
          earliestSlotToKeep,
          lastPrunedSlot,
          System.currentTimeMillis() - start);
    } catch (final ShuttingDownException | RejectedExecutionException ex) {
      LOG.debug("Shutting down", ex);
    }
  }
}
