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
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherPhase0 extends AbstractBlockPublisher {

  private final BlockGossipChannel blockGossipChannel;
  private final BlockImportChannel blockImportChannel;

  public BlockPublisherPhase0(
      final BlockFactory blockFactory,
      final BlockGossipChannel blockGossipChannel,
      final BlockImportChannel blockImportChannel,
      final DutyMetrics dutyMetrics) {
    super(blockFactory, dutyMetrics);
    this.blockGossipChannel = blockGossipChannel;
    this.blockImportChannel = blockImportChannel;
  }

  @Override
  SafeFuture<BlockImportAndBroadcastValidationResults> handleMissingBlockAfterUnblinding() {
    return SafeFuture.failedFuture(
        new IllegalStateException("Block must be present after unblinding"));
  }

  @Override
  SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      final SignedBeaconBlock block, final BroadcastValidationLevel broadcastValidationLevel) {
    return blockImportChannel.importBlock(block, broadcastValidationLevel);
  }

  @Override
  void importBlobSidecars(
      final Supplier<List<BlobSidecar>> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    // NOOP for Phase 0
  }

  @Override
  void importBlobSidecarsInParallel(
      final Supplier<List<BlobSidecar>> blobSidecars,
      final BlockPublishingPerformance blockPublishingPerformance,
      final UInt64 slot) {
    // NOOP for Phase 0
  }

  @Override
  void publishBlockAndSidecars(
      final SignedBeaconBlock block,
      final Supplier<List<BlobSidecar>> blobSidecars,
      final Supplier<List<DataColumnSidecar>> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance) {
    publishBlock(block, blockPublishingPerformance).finishStackTrace();
  }

  protected SafeFuture<Void> publishBlock(
      final SignedBeaconBlock block, final BlockPublishingPerformance blockPublishingPerformance) {
    blockPublishingPerformance.blockPublishingInitiated();
    return blockGossipChannel.publishBlock(block);
  }

  @Override
  String getPublishingType() {
    return "block";
  }
}
