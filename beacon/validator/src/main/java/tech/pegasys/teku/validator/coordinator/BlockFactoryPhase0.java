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

package tech.pegasys.teku.validator.coordinator;

import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;

public class BlockFactoryPhase0 implements BlockFactory {

  protected final Spec spec;
  protected final BlockOperationSelectorFactory operationSelector;

  public BlockFactoryPhase0(
      final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    this.spec = spec;
    this.operationSelector = operationSelector;
  }

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    return createNewUnsignedBlock(blockProductionContext)
        .thenApply(
            blockAndState ->
                createBlockContainerAndMetaDataBuilder(blockAndState.getState())
                    .blockContainer(blockAndState.getBlock())
                    .build());
  }

  protected SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    final BeaconState blockSlotState = blockProductionContext.blockSlotState();
    final UInt64 proposalSlot = blockProductionContext.proposalSlot();
    return spec.createNewUnsignedBlock(
        proposalSlot,
        spec.getBeaconProposerIndex(blockSlotState, proposalSlot),
        blockSlotState,
        blockProductionContext.parentRoot(),
        operationSelector.createSelector(blockProductionContext),
        blockProductionContext.blockProductionPerformance());
  }

  protected BlockContainerAndMetaData.Builder createBlockContainerAndMetaDataBuilder(
      final BeaconState state) {
    final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(state);
    return BlockContainerAndMetaData.builder()
        .milestone(spec.atSlot(state.getSlot()).getMilestone())
        .executionPayloadValue(slotCaches.getBlockExecutionValue())
        .consensusBlockValue(
            GWEI_TO_WEI.multiply(slotCaches.getBlockProposerRewards().longValue()));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    if (maybeBlindedBlock.isBlinded()) {
      return spec.unblindSignedBeaconBlock(
          maybeBlindedBlock.getSignedBlock(),
          operationSelector.createBlockUnblinderSelector(blockPublishingPerformance));
    }
    return SafeFuture.completedFuture(Optional.of(maybeBlindedBlock));
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    return Collections.emptyList();
  }

  @Override
  public List<DataColumnSidecar> createDataColumnSidecars(
      final SignedBlockContainer blockContainer) {
    return Collections.emptyList();
  }
}
