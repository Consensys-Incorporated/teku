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

import java.util.Optional;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChains;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncTargetSelector {
  private static final Logger LOG = LogManager.getLogger();

  static final int MIN_PENDING_BLOCKS = 5;
  private final RecentChainData recentChainData;
  private final PendingPool<SignedBeaconBlock> pendingBlocks;
  private final TargetChains finalizedChains;
  private final TargetChains nonfinalizedChains;
  private final int syncThresholdInSlots;

  public SyncTargetSelector(
      final RecentChainData recentChainData,
      final PendingPool<SignedBeaconBlock> pendingBlocks,
      final TargetChains finalizedChains,
      final TargetChains nonfinalizedChains,
      final int syncThresholdInSlots) {
    this.recentChainData = recentChainData;
    this.pendingBlocks = pendingBlocks;
    this.finalizedChains = finalizedChains;
    this.nonfinalizedChains = nonfinalizedChains;
    this.syncThresholdInSlots = syncThresholdInSlots;
  }

  public Optional<SyncTarget> selectSyncTarget(final Optional<SyncTarget> activeSyncTarget) {
    final boolean inSyncMode =
        activeSyncTarget.map(target -> !target.isSpeculativeSync()).orElse(false);
    LOG.debug(
        "Considering sync targets. In sync mode: {}, Active target {}",
        inSyncMode,
        activeSyncTarget);
    final Optional<SyncTarget> finalizedSyncTarget =
        selectTargetChainForSync(
                finalizedChains, activeSyncTarget.filter(SyncTarget::isFinalizedSync), inSyncMode)
            .map(SyncTarget::finalizedTarget);

    if (finalizedSyncTarget.isPresent()) {
      LOG.debug("Selected finalized chain as sync target {}", finalizedSyncTarget);
      return finalizedSyncTarget;
    }

    final Optional<SyncTarget> nonfinalizedSyncTarget =
        selectTargetChainForSync(
                nonfinalizedChains,
                activeSyncTarget.filter(SyncTarget::isNonfinalizedSync),
                inSyncMode)
            .map(SyncTarget::nonfinalizedTarget);
    if (nonfinalizedSyncTarget.isPresent()) {
      LOG.debug("Selected non-finalized chain as sync target {}", nonfinalizedSyncTarget);
      return nonfinalizedSyncTarget;
    }

    return selectSpeculativeTarget(activeSyncTarget);
  }

  private Optional<TargetChain> selectTargetChainForSync(
      final TargetChains targetChains,
      final Optional<SyncTarget> activeSyncTarget,
      final boolean inSyncMode) {
    return targetChains
        .streamChains()
        .findFirst()
        .filter(suitableChainFilter(activeSyncTarget, inSyncMode))
        // Otherwise as long as the existing target still has peers, stick with it.
        .or(
            () ->
                activeSyncTarget
                    .map(SyncTarget::getTargetChain)
                    .filter(chain -> chain.getPeerCount() > 0));
  }

  private Predicate<TargetChain> suitableChainFilter(
      final Optional<SyncTarget> activeSyncTarget, final boolean inSyncMode) {
    final UInt64 minimumSlotForSuitableTargetChain =
        getMinimumSlotForSuitableTargetChain(recentChainData, inSyncMode);
    return chain -> {
      final boolean targetChainAhead = isTargetChainAhead(minimumSlotForSuitableTargetChain, chain);
      final boolean worthSwitching = isWorthSwitching(chain, activeSyncTarget);
      return targetChainAhead && worthSwitching;
    };
  }

  private Optional<SyncTarget> selectSpeculativeTarget(
      final Optional<SyncTarget> activeSyncTarget) {
    LOG.trace("Evaluating chains for speculative sync");
    return nonfinalizedChains
        .streamChains()
        .filter(chain -> !recentChainData.containsBlock(chain.getChainHead().getBlockRoot()))
        .filter(chain -> isWorthSwitching(chain, activeSyncTarget))
        .filter(this::hasEnoughPendingBlocks)
        .findFirst()
        .map(SyncTarget::speculativeTarget);
  }

  private boolean hasEnoughPendingBlocks(final TargetChain candidate) {
    final SlotAndBlockRoot chainHead = candidate.getChainHead();
    int pendingBlockCount = 0;
    Optional<SignedBeaconBlock> block = pendingBlocks.get(chainHead.getBlockRoot());
    while (block.isPresent()) {
      pendingBlockCount++;
      if (pendingBlockCount >= MIN_PENDING_BLOCKS) {
        LOG.trace("{} has sufficient pending blocks to warrant speculative sync", chainHead);
        return true;
      }
      block = pendingBlocks.get(block.get().getParentRoot());
    }
    LOG.trace(
        "Ignoring {} as {} is not enough pending blocks to justify speculative sync",
        chainHead,
        pendingBlockCount);
    return false;
  }

  private boolean isWorthSwitching(
      final TargetChain chain, final Optional<SyncTarget> activeSyncTarget) {
    return activeSyncTarget.isEmpty()
        || isWorthSwitching(chain, activeSyncTarget.get().getTargetChain());
  }

  private boolean isTargetChainAhead(
      final UInt64 minimumSlotForSuitableTargetChain, final TargetChain chain) {
    return !recentChainData.containsBlock(chain.getChainHead().getBlockRoot())
        && chain.getChainHead().getSlot().isGreaterThan(minimumSlotForSuitableTargetChain);
  }

  private UInt64 getMinimumSlotForSuitableTargetChain(
      final RecentChainData recentChainData, final boolean syncInProgress) {
    final UInt64 localHeadSlot = recentChainData.getHeadSlot();
    return syncInProgress ? localHeadSlot : localHeadSlot.plus(syncThresholdInSlots);
  }

  /**
   * Avoids thrashing between different chains with the same number of peers based on which one had
   * the latest block. Sticks with the current sync target unless it is either not available from
   * our pool (possibly switching from finalized to non-finalized or back) or the new chain has more
   * peers (making it more likely to be the canonical chain).
   *
   * @param candidateChain the chain being considered as an option to switch to
   * @param currentTargetChain the current sync target
   * @return true if the candidate chain is worth the potential cost of switching to
   */
  private boolean isWorthSwitching(
      final TargetChain candidateChain, final TargetChain currentTargetChain) {
    return candidateChain.equals(currentTargetChain)
        || candidateChain.getPeerCount() > currentTargetChain.getPeerCount();
  }
}
