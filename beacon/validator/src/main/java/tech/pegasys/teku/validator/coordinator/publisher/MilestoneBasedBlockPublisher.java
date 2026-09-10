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

package tech.pegasys.teku.validator.coordinator.publisher;

import com.google.common.base.Suppliers;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Supplier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class MilestoneBasedBlockPublisher implements BlockPublisher {

  private final Spec spec;
  private final Map<SpecMilestone, BlockPublisher> registeredPublishers =
      new EnumMap<>(SpecMilestone.class);

  public MilestoneBasedBlockPublisher(
      final AsyncRunner asyncRunner,
      final Spec spec,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final BlobSidecarGossipChannel blobSidecarGossipChannel,
      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel,
      final DutyMetrics dutyMetrics,
      final CustodyGroupCountManager custodyGroupCountManager,
      final OptionalInt dasPublishWithholdColumnsEverySlots,
      final boolean gossipBlobsAfterBlock) {
    this.spec = spec;
    final BlockPublisherPhase0 blockPublisherPhase0 =
        new BlockPublisherPhase0(blockFactory, blockGossipChannel, blockImportChannel, dutyMetrics);

    // Not needed for all milestones
    final Supplier<BlockPublisherDeneb> blockAndBlobSidecarsPublisherSupplier =
        Suppliers.memoize(
            () ->
                new BlockPublisherDeneb(
                    asyncRunner,
                    blockFactory,
                    blockImportChannel,
                    blockGossipChannel,
                    blockBlobSidecarsTrackersPool,
                    blobSidecarGossipChannel,
                    dutyMetrics,
                    gossipBlobsAfterBlock));
    final Supplier<BlockPublisherFulu> blockAndDataColumnSidecarsPublisherSupplier =
        Suppliers.memoize(
            () ->
                new BlockPublisherFulu(
                    blockFactory,
                    blockImportChannel,
                    blockGossipChannel,
                    dataColumnSidecarGossipChannel,
                    dutyMetrics,
                    custodyGroupCountManager,
                    dasPublishWithholdColumnsEverySlots,
                    gossipBlobsAfterBlock));

    // Populate forks publishers
    spec.getEnabledMilestones()
        .forEach(
            forkAndSpecMilestone -> {
              final SpecMilestone milestone = forkAndSpecMilestone.getSpecMilestone();
              if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
                // in Gloas, phase0 can be reused
                registeredPublishers.put(milestone, blockPublisherPhase0);
              } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
                registeredPublishers.put(
                    milestone, blockAndDataColumnSidecarsPublisherSupplier.get());
              } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
                registeredPublishers.put(milestone, blockAndBlobSidecarsPublisherSupplier.get());
              } else {
                registeredPublishers.put(milestone, blockPublisherPhase0);
              }
            });
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    final SpecMilestone blockMilestone = spec.atSlot(blockContainer.getSlot()).getMilestone();
    return registeredPublishers
        .get(blockMilestone)
        .sendSignedBlock(blockContainer, broadcastValidationLevel, blockPublishingPerformance);
  }
}
