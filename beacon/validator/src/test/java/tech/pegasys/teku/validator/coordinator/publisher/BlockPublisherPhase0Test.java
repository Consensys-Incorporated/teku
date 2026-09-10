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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

class BlockPublisherPhase0Test {

  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);

  private final BlockPublisherPhase0 blockPublisherPhase0 =
      new BlockPublisherPhase0(
          blockFactory, blockGossipChannel, blockImportChannel, mock(DutyMetrics.class));

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SignedBeaconBlock signedBlock = mock(SignedBeaconBlock.class);

  @BeforeEach
  void setUp() {
    when(blockGossipChannel.publishBlock(signedBlock)).thenReturn(SafeFuture.COMPLETE);
    when(blockImportChannel.importBlock(signedBlock, BroadcastValidationLevel.NOT_REQUIRED))
        .thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  void importBlock_shouldImportBlock() {
    safeJoin(blockPublisherPhase0.importBlock(signedBlock, BroadcastValidationLevel.NOT_REQUIRED));

    verify(blockImportChannel).importBlock(signedBlock, BroadcastValidationLevel.NOT_REQUIRED);
  }

  @Test
  void publishBlock_shouldPublishBlock() {
    safeJoin(blockPublisherPhase0.publishBlock(signedBlock, BlockPublishingPerformance.NOOP));

    verify(blockGossipChannel).publishBlock(signedBlock);
  }

  @Test
  void missingBlockAfterUnblinding_shouldThrowIllegalStateException() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockFactory.unblindSignedBlockIfBlinded(block, BlockPublishingPerformance.NOOP))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThatSafeFuture(
            blockPublisherPhase0.sendSignedBlock(
                block, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP))
        .isCompletedExceptionallyWith(IllegalStateException.class);
  }

  @Test
  void sendSignedBlock_shouldPublishBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockFactory.unblindSignedBlockIfBlinded(block, BlockPublishingPerformance.NOOP))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(blockGossipChannel.publishBlock(block)).thenReturn(SafeFuture.COMPLETE);
    when(blockImportChannel.importBlock(block, BroadcastValidationLevel.NOT_REQUIRED))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(
                    SafeFuture.completedFuture(BlockImportResult.successful(block)))));

    assertThatSafeFuture(
            blockPublisherPhase0.sendSignedBlock(
                block, BroadcastValidationLevel.NOT_REQUIRED, BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));

    verify(blockGossipChannel).publishBlock(block);
  }
}
