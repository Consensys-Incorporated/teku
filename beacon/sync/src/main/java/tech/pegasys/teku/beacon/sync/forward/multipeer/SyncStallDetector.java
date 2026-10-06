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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import com.google.common.base.MoreObjects;
import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncStallDetector extends Service {
  private static final Logger LOG = LogManager.getLogger();

  static final Duration STALL_CHECK_INTERVAL = Duration.ofSeconds(15);
  // Time periods are fairly long because sync stalls should be rare and we might be rate limited
  // if we have to request blocks from a small number of peers.
  static final int MAX_SECONDS_BETWEEN_IMPORTS = 300;
  static final int MAX_SECONDS_BETWEEN_IMPORT_PROGRESS = 300;

  private final Spec spec;
  private final EventThread eventThread;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final SyncController syncController;
  private final BatchSync sync;
  private Cancellable cancellable;

  private Optional<BatchImportData> currentBatchImport = Optional.empty();
  private final RecentChainData recentChainData;

  public SyncStallDetector(
      final Spec spec,
      final EventThread eventThread,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final SyncController syncController,
      final BatchSync sync,
      final RecentChainData recentChainData) {
    this.spec = spec;
    this.eventThread = eventThread;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.syncController = syncController;
    this.sync = sync;
    this.recentChainData = recentChainData;
  }

  /**
   * We consider a sync stalled if it has not made progress in X seconds. Progress is tracked based
   * on getting new blocks onto the chain.
   */
  private void performStallCheck() {
    eventThread.checkOnEventThread();
    if (!syncController.isSyncActive()) {
      LOG.debug("Not performing sync stall check as sync is not active");
      return;
    }
    // Check if a new batch has started importing since the last stall check
    final Optional<Batch> newImportingBatch = sync.getImportingBatch();
    if (!currentBatchImport.map(d -> d.importingBatch).equals(newImportingBatch)) {
      LOG.debug("Detected new batch being imported, sync is not stalled.");
      // New batch being imported so we must be making progress
      currentBatchImport = newImportingBatch.map(BatchImportData::new);
      return;
    }

    // Stall case 1 - no import started more than X seconds after last one completed
    final UInt64 secondsSinceLastCompletedImport =
        timeProvider.getTimeInSeconds().minusMinZero(sync.getLastImportTimerStartPointSeconds());
    if (currentBatchImport.isEmpty()
        && secondsSinceLastCompletedImport.isGreaterThan(MAX_SECONDS_BETWEEN_IMPORTS)) {
      LOG.warn(
          "Sync has not started a batch importing for {} seconds after the previous batch import completed. Considering it stalled and restarting sync.",
          secondsSinceLastCompletedImport);
      sync.abort();
      return;
    }

    // Stall case 2 - an import was started but hasn't imported a new block for more than X seconds
    currentBatchImport.ifPresent(
        currentImport -> {
          final UInt64 timeSinceLastProgress = currentImport.calculateSecondsSinceLastProgress();
          if (timeSinceLastProgress.isGreaterThan(MAX_SECONDS_BETWEEN_IMPORT_PROGRESS)) {
            LOG.warn(
                "Sync has not made progress importing batch {} for {} seconds after the previous batch import completed. Considering it stalled and restarting sync.",
                currentImport,
                secondsSinceLastCompletedImport);
            sync.abort();
          }
        });
  }

  @Override
  protected SafeFuture<?> doStart() {
    cancellable =
        asyncRunner.runWithFixedDelay(
            () -> eventThread.execute(this::performStallCheck),
            STALL_CHECK_INTERVAL,
            error -> LOG.error("Failed to check for sync stalls", error));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    cancellable.cancel();
    return SafeFuture.COMPLETE;
  }

  private class BatchImportData {
    private final Batch importingBatch;
    private Optional<Bytes32> lastImportedBlockRoot = Optional.empty();
    private UInt64 lastProgressSeconds;

    private BatchImportData(final Batch importingBatch) {
      this.importingBatch = importingBatch;
      this.lastProgressSeconds = timeProvider.getTimeInSeconds();
    }

    public UInt64 calculateSecondsSinceLastProgress() {
      final Optional<Bytes32> newLastImportedBlockRoot = findLastImportedBlock();
      if (
      // No blocks imported from this batch at all
      (lastImportedBlockRoot.isEmpty() && newLastImportedBlockRoot.isEmpty())
          // No new blocks imported from this batch
          || lastImportedBlockRoot.equals(newLastImportedBlockRoot)) {
        return timeProvider.getTimeInSeconds().minusMinZero(lastProgressSeconds);
      } else {
        lastImportedBlockRoot = newLastImportedBlockRoot;
        lastProgressSeconds = timeProvider.getTimeInSeconds();
        return UInt64.ZERO;
      }
    }

    private Optional<Bytes32> findLastImportedBlock() {
      Optional<Bytes32> lastImportedBlock = Optional.empty();
      for (SignedBeaconBlock block : importingBatch.getBlocks()) {
        if (!recentChainData.containsBlock(block.getRoot())
            && !recentChainData
                .getFinalizedEpoch()
                .isGreaterThanOrEqualTo(spec.computeEpochAtSlot(block.getSlot()))) {
          return lastImportedBlock;
        }
        lastImportedBlock = Optional.of(block.getRoot());
      }
      return lastImportedBlock;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("importingBatch", importingBatch)
          .add("lastImportedBlockRoot", lastImportedBlockRoot)
          .add("lastProgressSeconds", lastProgressSeconds)
          .toString();
    }
  }
}
