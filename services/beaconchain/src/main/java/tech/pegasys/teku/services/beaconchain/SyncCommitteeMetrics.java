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

package tech.pegasys.teku.services.beaconchain;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeMetrics implements SlotEventsChannel, ChainHeadChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;

  private final SettableGauge previousLiveSyncCommittee;
  private final SettableGauge headLiveSyncCommittee;

  private UInt64 lastProcessedEpoch;

  public SyncCommitteeMetrics(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;

    previousLiveSyncCommittee =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_live_sync_committee",
            "Number of sync committee participant signatures that were included on chain during previous epoch");

    headLiveSyncCommittee =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "head_live_sync_committee",
            "Number of sync committee participant signatures included in the current head block");
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    recentChainData
        .retrieveBlockByRoot(bestBlockRoot)
        .thenAccept(
            maybeBlock ->
                maybeBlock
                    .flatMap(block -> block.getBody().toVersionAltair())
                    .ifPresent(
                        body ->
                            headLiveSyncCommittee.set(
                                body.getSyncAggregate().getSyncCommitteeBits().getBitCount())))
        .finishError(LOG);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    recentChainData
        .getChainHead()
        .ifPresent(
            head ->
                head.getState()
                    .finish(
                        state -> updateSlotBasedMetrics(slot, state),
                        error ->
                            LOG.error(
                                "Unable to update sync committee metrics because chain head state was unavailable.",
                                error)));
  }

  public void updateSlotBasedMetrics(final UInt64 slot, final BeaconState chainHead) {
    final UInt64 previousEpoch = spec.computeEpochAtSlot(slot).minusMinZero(1);
    final UInt64 previousEpochStartSlot = spec.computeStartSlotAtEpoch(previousEpoch);
    if ((lastProcessedEpoch != null && previousEpoch.isLessThanOrEqualTo(lastProcessedEpoch))
        || chainHead.toVersionAltair().isEmpty()) {
      return;
    }

    if (chainHead.getSlot().isLessThan(previousEpochStartSlot)) {
      // There were no blocks in the epoch so can't have included any signatures
      previousLiveSyncCommittee.set(0);
      return;
    }
    SafeFuture.collectAll(
            UInt64.range(
                    previousEpochStartSlot.min(chainHead.getSlot()),
                    spec.computeStartSlotAtEpoch(previousEpoch.plus(1)).min(chainHead.getSlot()))
                .map(slotInEpoch -> getBlockAtSlotExact(chainHead, slotInEpoch)))
        .finish(
            this::updateSyncCommitteeMetrics,
            error ->
                LOG.warn(
                    "Unable to update sync committee metrics for epoch {}", previousEpoch, error));
    lastProcessedEpoch = previousEpoch;
  }

  private SafeFuture<Optional<BeaconBlock>> getBlockAtSlotExact(
      final BeaconState chainHead, final UInt64 slot) {
    final Bytes32 blockRoot = spec.getBlockRootAtSlot(chainHead, slot);
    final Optional<UInt64> blockSlot = recentChainData.getSlotForBlockRoot(blockRoot);
    if (blockSlot.isPresent() && blockSlot.get().equals(slot)) {
      return recentChainData.retrieveBlockByRoot(blockRoot);
    } else {
      return SafeFuture.completedFuture(Optional.empty());
    }
  }

  private void updateSyncCommitteeMetrics(final List<Optional<BeaconBlock>> blocks) {
    final int totalIncludedSignatures =
        blocks.stream()
            .flatMap(Optional::stream)
            .flatMap(block -> block.getBody().toVersionAltair().stream())
            .mapToInt(
                blockBody -> blockBody.getSyncAggregate().getSyncCommitteeBits().getBitCount())
            .sum();
    previousLiveSyncCommittee.set(totalIncludedSignatures);
  }
}
