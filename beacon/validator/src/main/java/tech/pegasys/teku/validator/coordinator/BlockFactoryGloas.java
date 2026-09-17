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

import com.google.common.base.Preconditions;
import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

// Gloas is more similar to BlockFactoryPhase0 than BlockFactoryFulu
public class BlockFactoryGloas extends BlockFactoryPhase0 {

  public BlockFactoryGloas(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
  }

  /**
   * Extends the base block production with Gloas-specific logic: when the proposer is self-building
   * (local EL payload, indicated by a G2-point-at-infinity signature on the bid) and
   * `includePayload` is true, the block is wrapped in a {@link BlockContentsGloas} containing the
   * execution payload envelope, KZG proofs, and blobs so they can be returned together in the V4
   * block-production response.
   *
   * <p>When an external builder's gossip bid is selected the signature is real, and there is no
   * locally-held execution payload to include, so the plain {@link BeaconBlock} is returned as-is.
   */
  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    return createNewUnsignedBlock(blockProductionContext)
        .thenCompose(
            blockAndState -> {
              final BeaconBlock block = blockAndState.getBlock();
              final BeaconState state = blockAndState.getState();
              final SignedExecutionPayloadBid bid =
                  BeaconBlockBodyGloas.required(block.getBody()).getSignedExecutionPayloadBid();
              // G2-point-at-infinity signature means we self-built the payload
              final boolean selfBuilt = bid.getSignature().isInfinity();
              // include_payload=false or builder bid → return beacon block only
              if (!blockProductionContext.includePayload() || !selfBuilt) {
                return SafeFuture.completedFuture(
                    createBlockContainerAndMetaDataBuilder(state)
                        .blockContainer(block)
                        .payloadIncluded(false)
                        .build());
              }
              // include_payload=true and self-built → include full contents
              final UInt64 slot = block.getSlot();
              return operationSelector
                  .getCachedPayloadResultRequired(slot)
                  .getPayloadResponseFutureFromLocalFlowRequired()
                  .thenApply(
                      getPayloadResponse -> {
                        final SchemaDefinitionsGloas schemaDefinitions =
                            SchemaDefinitionsGloas.required(
                                spec.atSlot(slot).getSchemaDefinitions());
                        final BlobsBundle blobsBundle =
                            getPayloadResponse.getBlobsBundle().orElseThrow();
                        final ExecutionPayloadEnvelope envelope =
                            schemaDefinitions
                                .getExecutionPayloadEnvelopeSchema()
                                .create(
                                    getPayloadResponse.getExecutionPayload(),
                                    getPayloadResponse.getExecutionRequests().orElseThrow(),
                                    bid.getMessage().getBuilderIndex(),
                                    block.hashTreeRoot(),
                                    block.getParentRoot());
                        final BlockContainer blockContents =
                            schemaDefinitions
                                .getBlockContentsSchema()
                                .create(
                                    block,
                                    blobsBundle.getProofs(),
                                    blobsBundle.getBlobs(),
                                    Optional.of(envelope));
                        return createBlockContainerAndMetaDataBuilder(state)
                            .blockContainer(blockContents)
                            .payloadIncluded(true)
                            .build();
                      });
            });
  }

  @Override
  protected BlockContainerAndMetaData.Builder createBlockContainerAndMetaDataBuilder(
      final BeaconState state) {
    final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(state);
    return BlockContainerAndMetaData.builder()
        .milestone(spec.atSlot(state.getSlot()).getMilestone())
        .executionPayloadValue(slotCaches.getBlockExecutionValue())
        .consensusBlockValue(GWEI_TO_WEI.multiply(slotCaches.getBlockProposerRewards().longValue()))
        .builderUrl(slotCaches.getBuilderUrl());
  }

  // blocks in ePBS are all unblinded
  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBlockIfBlinded(
      final SignedBeaconBlock maybeBlindedBlock,
      final BlockPublishingPerformance blockPublishingPerformance) {
    Preconditions.checkArgument(
        !maybeBlindedBlock.isBlinded(), "Blocks in ePBS should be all unblinded");
    return SafeFuture.completedFuture(Optional.of(maybeBlindedBlock));
  }
}
