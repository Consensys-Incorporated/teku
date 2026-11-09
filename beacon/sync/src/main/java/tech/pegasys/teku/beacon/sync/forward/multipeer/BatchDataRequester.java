/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.Batch;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.batches.BatchFactory;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Attempts to create a {@link BatchChain} and download the blocks for each batch.
 *
 * <p>Applies limits to the number of batches awaiting import to avoid excessive memory usage.
 */
public class BatchDataRequester {
  private final EventThread eventThread;
  private final BatchChain activeBatches;
  private final BatchFactory batchFactory;
  private final UInt64 batchSize;
  private final int maxPendingBatches;

  public BatchDataRequester(
      final EventThread eventThread,
      final BatchChain activeBatches,
      final BatchFactory batchFactory,
      final UInt64 batchSize,
      final int maxPendingBatches) {
    this.eventThread = eventThread;
    this.activeBatches = activeBatches;
    this.batchFactory = batchFactory;
    this.batchSize = batchSize;
    this.maxPendingBatches = maxPendingBatches;
  }

  public void fillRetrievingQueue(
      final TargetChain targetChain,
      final UInt64 commonAncestorSlot,
      final Consumer<Batch> requestCompleteCallback) {
    eventThread.checkOnEventThread();

    replaceBatchesFromOldChainsWithNoSources(targetChain);

    final long pendingBatchesCount =
        activeBatches.stream().filter(batch -> !batch.isEmpty() || !batch.isComplete()).count();

    // First check if there are batches that should request more blocks
    activeBatches.stream()
        .filter(batch -> (!batch.isComplete() || batch.isContested()) && !batch.isAwaitingBlocks())
        .forEach(batch -> requestMoreBlocks(batch, requestCompleteCallback));

    // Add more pending batches if there is room
    UInt64 nextBatchStart = getNextSlotToRequest(commonAncestorSlot);
    final UInt64 targetSlot = targetChain.getChainHead().getSlot();
    for (long i = pendingBatchesCount;
        i < maxPendingBatches && nextBatchStart.isLessThanOrEqualTo(targetSlot);
        i++) {
      final UInt64 remainingSlots = targetSlot.minus(nextBatchStart).plus(1);
      final UInt64 count = remainingSlots.min(batchSize);
      final Batch batch = batchFactory.createBatch(targetChain, nextBatchStart, count);
      activeBatches.add(batch);
      requestMoreBlocks(batch, requestCompleteCallback);
      nextBatchStart = batch.getLastSlot().plus(1);
      if (nextBatchStart.isGreaterThan(targetSlot)) {
        break;
      }
    }
  }

  /**
   * All the sync sources on a given target chain may have moved to a new chain or disconnected. To
   * avoid getting stuck attempting and failing to request data when there are no sync sources, find
   * the first batch from an old chain with no sources, and replace all batches after it that are
   * from old chains with batches from our new chain.
   */
  private void replaceBatchesFromOldChainsWithNoSources(final TargetChain targetChain) {
    final Optional<Batch> firstStrandedBatch =
        activeBatches.stream()
            .filter(batch -> incompleteBatchFromOldChainWithNoPeers(targetChain, batch))
            .findFirst();
    firstStrandedBatch.ifPresent(
        strandedBatch -> {
          // We have at least one batch from an old chain which no longer has any target peers to
          // request data from. Remove all batches from that one to where the new chain starts and
          // replace with batches from the new chain.
          final List<Batch> batchesToReplace =
              activeBatches.batchesAfterInclusive(strandedBatch).stream()
                  .takeWhile(batch -> !batch.getTargetChain().equals(targetChain))
                  .toList();
          batchesToReplace.forEach(
              batchToReplace ->
                  activeBatches.replace(
                      batchToReplace,
                      batchFactory.createBatch(
                          targetChain, batchToReplace.getFirstSlot(), batchToReplace.getCount())));
        });
  }

  private boolean incompleteBatchFromOldChainWithNoPeers(
      final TargetChain targetChain, final Batch batch) {
    return !batch.isComplete()
        && !batch.getTargetChain().equals(targetChain)
        && batch.getTargetChain().getPeerCount() == 0;
  }

  private UInt64 getNextSlotToRequest(final UInt64 commonAncestorSlot) {
    final UInt64 lastRequestedSlot =
        activeBatches.last().map(Batch::getLastSlot).orElse(commonAncestorSlot);
    return lastRequestedSlot.plus(1);
  }

  private void requestMoreBlocks(final Batch batch, final Consumer<Batch> requestCompleteCallback) {
    batch.requestMoreBlocks(() -> eventThread.execute(() -> requestCompleteCallback.accept(batch)));
  }
}
