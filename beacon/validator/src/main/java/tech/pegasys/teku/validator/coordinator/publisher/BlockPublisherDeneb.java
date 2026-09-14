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

import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherDeneb extends BlockPublisherPhase0 {
  private static final Logger LOG = LogManager.getLogger();

  private final AsyncRunner asyncRunner;
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final BlobSidecarGossipChannel blobSidecarGossipChannel;
  private final boolean gossipBlobsAfterBlock;

  public BlockPublisherDeneb(
      final AsyncRunner asyncRunner,
      final BlockFactory blockFactory,
      final BlockImportChannel blockImportChannel,
      final BlockGossipChannel blockGossipChannel,
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final BlobSidecarGossipChannel blobSidecarGossipChannel,
      final DutyMetrics dutyMetrics,
      final boolean gossipBlobsAfterBlock) {
    super(blockFactory, blockGossipChannel, blockImportChannel, dutyMetrics);
    this.asyncRunner = asyncRunner;
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.blobSidecarGossipChannel = blobSidecarGossipChannel;
    this.gossipBlobsAfterBlock = gossipBlobsAfterBlock;
  }

  @Override
  void importBlobSidecars(
      final Supplier<List<BlobSidecar>> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    blobSidecars
        .get()
        .forEach(
            blobSidecar ->
                blockBlobSidecarsTrackersPool.onNewBlobSidecar(
                    blobSidecar, RemoteOrigin.LOCAL_PROPOSAL));
    blockPublishingPerformance.blobSidecarsImportCompleted();
  }

  @Override
  void importBlobSidecarsAsync(
      final Supplier<List<BlobSidecar>> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance,
      final UInt64 slot) {
    asyncRunner
        .runAsync(() -> importBlobSidecars(blobSidecars, blockPublishingPerformance))
        .finish(error -> LOG.error("Failed to import blob sidecars for slot {}", slot, error));
  }

  @Override
  void publishBlockAndSidecars(
      final SignedBeaconBlock block,
      final Supplier<List<BlobSidecar>> blobSidecars,
      final Supplier<List<DataColumnSidecar>> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    if (gossipBlobsAfterBlock) {
      publishBlock(block, blockPublishingPerformance)
          .always(() -> publishBlobSidecars(blobSidecars.get(), blockPublishingPerformance));
    } else {
      publishBlock(block, blockPublishingPerformance).finishStackTrace();
      publishBlobSidecars(blobSidecars.get(), blockPublishingPerformance);
    }
  }

  void publishBlobSidecars(
      final List<BlobSidecar> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    blockPublishingPerformance.blobSidecarsPublishingInitiated();
    blobSidecarGossipChannel.publishBlobSidecars(blobSidecars).finishStackTrace();
  }

  @Override
  String getPublishingType() {
    return "block and blob sidecars";
  }
}
