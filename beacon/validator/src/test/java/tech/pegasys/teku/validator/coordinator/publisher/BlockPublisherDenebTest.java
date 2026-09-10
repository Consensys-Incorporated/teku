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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

class BlockPublisherDenebTest {
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  private final BlobSidecarGossipChannel blobSidecarGossipChannel =
      mock(BlobSidecarGossipChannel.class);
  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final BlockPublisherDeneb blockPublisherDeneb =
      new BlockPublisherDeneb(
          mock(AsyncRunner.class),
          blockFactory,
          blockImportChannel,
          blockGossipChannel,
          blockBlobSidecarsTrackersPool,
          blobSidecarGossipChannel,
          mock(DutyMetrics.class),
          true);

  private final BlobSidecar blobSidecar = mock(BlobSidecar.class);
  private final List<BlobSidecar> blobSidecars = List.of(blobSidecar);

  @BeforeEach
  void setUp() {
    when(blobSidecarGossipChannel.publishBlobSidecars(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void importBlobSidecars_shouldTrackBlobSidecars() {
    blockPublisherDeneb.importBlobSidecars(() -> blobSidecars, BlockPublishingPerformance.NOOP);

    verify(blockBlobSidecarsTrackersPool)
        .onNewBlobSidecar(blobSidecar, RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  void publishBlobSidecars_shouldPublishBlobSidecars() {
    blockPublisherDeneb.publishBlobSidecars(blobSidecars, BlockPublishingPerformance.NOOP);

    verify(blobSidecarGossipChannel).publishBlobSidecars(blobSidecars);
  }

  @Test
  void publishBlockAndSidecars_shouldPublishBlobsAfterBlockWhenOptionIsEnabled() {
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    final SafeFuture<Void> publishBlockFuture = new SafeFuture<>();
    when(blockGossipChannel.publishBlock(block)).thenReturn(publishBlockFuture);

    blockPublisherDeneb.publishBlockAndSidecars(
        block, () -> blobSidecars, List::of, BlockPublishingPerformance.NOOP);

    verify(blockGossipChannel).publishBlock(block);
    verify(blobSidecarGossipChannel, never()).publishBlobSidecars(any());

    publishBlockFuture.complete(null);

    verify(blobSidecarGossipChannel).publishBlobSidecars(blobSidecars);
  }

  @Test
  void sendSignedBlock_shouldPublishBlockAndBlobSidecars() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBlockContainer blockContainer = dataStructureUtil.randomSignedBlockContents();
    final SignedBeaconBlock block = blockContainer.getSignedBlock();

    when(blockFactory.unblindSignedBlockIfBlinded(block, BlockPublishingPerformance.NOOP))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(blockFactory.createBlobSidecars(blockContainer)).thenReturn(blobSidecars);
    when(blockGossipChannel.publishBlock(block)).thenReturn(SafeFuture.COMPLETE);
    when(blockImportChannel.importBlock(block, BroadcastValidationLevel.NOT_REQUIRED))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(
                    SafeFuture.completedFuture(BlockImportResult.successful(block)))));

    assertThatSafeFuture(
            blockPublisherDeneb.sendSignedBlock(
                blockContainer,
                BroadcastValidationLevel.NOT_REQUIRED,
                BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(SendSignedBlockResult.success(blockContainer.getRoot()));

    verify(blockGossipChannel).publishBlock(block);
    verify(blockFactory).createBlobSidecars(blockContainer);
    verify(blobSidecarGossipChannel).publishBlobSidecars(blobSidecars);
  }
}
