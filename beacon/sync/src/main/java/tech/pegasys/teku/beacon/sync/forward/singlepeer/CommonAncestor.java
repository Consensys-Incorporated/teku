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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.client.RecentChainData;

public class CommonAncestor {
  private static final Logger LOG = LogManager.getLogger();
  private static final int DEFAULT_MAX_ATTEMPTS = 6;
  static final UInt64 BLOCK_COUNT_PER_ATTEMPT = UInt64.valueOf(16);
  static final UInt64 SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE = UInt64.valueOf(100);
  // We will try to find a common block downloading 16 blocks per attempt.
  // We will try 6 times, each time jumping back 100 * 2^attempt slots.
  // Which means we will jump back 100 slots, then 200 from previous 100,
  // then 400, and so on up to 3200.
  // So max attempted distance from initial slot will be 6300 slots.
  // We will download max 96 blocks in total, before defaulting to firstNonFinalSlot

  private final RecentChainData recentChainData;
  private final int maxAttempts;
  private final OptionalInt maxDistanceFromHead;

  public CommonAncestor(
      final RecentChainData recentChainData, final OptionalInt maxDistanceFromHead) {
    this(recentChainData, DEFAULT_MAX_ATTEMPTS, maxDistanceFromHead);
  }

  @VisibleForTesting
  CommonAncestor(
      final RecentChainData recentChainData,
      final int maxAttempts,
      final OptionalInt maxDistanceFromHead) {
    this.recentChainData = recentChainData;
    this.maxAttempts = maxAttempts;
    this.maxDistanceFromHead = maxDistanceFromHead;
  }

  public SafeFuture<UInt64> getCommonAncestor(
      final SyncSource peer, final UInt64 firstNonFinalSlot, final UInt64 peerHeadSlot) {
    final UInt64 ourHeadSlot = recentChainData.getHeadSlot();
    final UInt64 lowestHeadSlot = ourHeadSlot.min(peerHeadSlot);

    final UInt64 localNonFinalisedSlotCount = lowestHeadSlot.minusMinZero(firstNonFinalSlot);

    LOG.debug(
        "Local head slot {}. Have {} non finalized slots, peer head is {}",
        ourHeadSlot,
        localNonFinalisedSlotCount,
        peerHeadSlot);

    return getCommonAncestor(
        peer,
        lowestHeadSlot.minusMinZero(BLOCK_COUNT_PER_ATTEMPT.decrement()),
        firstNonFinalSlot,
        0);
  }

  private SafeFuture<UInt64> getCommonAncestor(
      final SyncSource peer,
      final UInt64 firstRequestedSlot,
      final UInt64 firstNonFinalSlot,
      final int attempt) {
    final UInt64 lastSlot = firstRequestedSlot.plus(BLOCK_COUNT_PER_ATTEMPT);

    if (maxDistanceFromHeadReached(lastSlot)) {
      return SafeFuture.failedFuture(new RuntimeException("Max distance from head reached"));
    }
    if (attempt >= maxAttempts || firstRequestedSlot.isLessThanOrEqualTo(firstNonFinalSlot)) {
      return SafeFuture.completedFuture(firstNonFinalSlot);
    }

    LOG.debug("Sampling ahead from {} to {}.", firstRequestedSlot, lastSlot);

    final BestBlockListener blockResponseListener = new BestBlockListener(recentChainData);
    final PeerSyncBlockListener blockListener =
        new PeerSyncBlockListener(
            SafeFuture.COMPLETE,
            firstRequestedSlot,
            BLOCK_COUNT_PER_ATTEMPT,
            blockResponseListener);

    return peer.requestBlocksByRange(firstRequestedSlot, BLOCK_COUNT_PER_ATTEMPT, blockListener)
        .thenCompose(
            __ ->
                blockResponseListener
                    .getBestSlot()
                    .<SafeFuture<UInt64>>map(
                        bestSlot -> {
                          if (maxDistanceFromHeadReached(bestSlot)) {
                            return SafeFuture.failedFuture(
                                new RuntimeException("Max distance from head reached"));
                          } else {
                            return SafeFuture.completedFuture(bestSlot);
                          }
                        })
                    .orElseGet(
                        () ->
                            getCommonAncestor(
                                peer,
                                firstRequestedSlot.minusMinZero(
                                    SLOTS_TO_JUMP_BACK_EXPONENTIAL_BASE.times(1L << attempt)),
                                firstNonFinalSlot,
                                attempt + 1)));
  }

  private boolean maxDistanceFromHeadReached(final UInt64 slot) {
    if (maxDistanceFromHead.isEmpty()) {
      return false;
    }
    final UInt64 oldestAcceptedSlotFromHead =
        recentChainData.getHeadSlot().minusMinZero(maxDistanceFromHead.getAsInt());
    return slot.isLessThan(oldestAcceptedSlotFromHead);
  }

  private static class BestBlockListener implements RpcResponseListener<SignedBeaconBlock> {
    private final RecentChainData recentChainData;
    private Optional<UInt64> bestSlot;

    BestBlockListener(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      this.bestSlot = Optional.empty();
    }

    private Optional<UInt64> getBestSlot() {
      return bestSlot;
    }

    @Override
    public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
      if (recentChainData.containsBlock(block.getRoot())) {
        bestSlot =
            bestSlot
                .map(uInt64 -> uInt64.max(block.getSlot()))
                .or(() -> Optional.of(block.getSlot()));
      }

      return SafeFuture.COMPLETE;
    }
  }
}
