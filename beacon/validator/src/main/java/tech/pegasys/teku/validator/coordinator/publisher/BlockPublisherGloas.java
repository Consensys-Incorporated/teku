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
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.builder.rest.StakedBuilderClientProvider;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public class BlockPublisherGloas extends BlockPublisherPhase0 {
  private static final Logger LOG = LogManager.getLogger();

  private final StakedBuilderClientProvider stakedBuilderClientProvider;

  public BlockPublisherGloas(
      final BlockFactory blockFactory,
      final BlockGossipChannel blockGossipChannel,
      final BlockImportChannel blockImportChannel,
      final DutyMetrics dutyMetrics,
      final StakedBuilderClientProvider stakedBuilderClientProvider) {
    super(blockFactory, blockGossipChannel, blockImportChannel, dutyMetrics);
    this.stakedBuilderClientProvider = stakedBuilderClientProvider;
  }

  // no need for unblinding in Gloas, so this method can be simplified
  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance,
      final Optional<String> builderUrl) {
    return gossipAndImportUnblindedSignedBlockAndSidecars(
            blockContainer.getSignedBlock(),
            List::of,
            List::of,
            broadcastValidationLevel,
            blockPublishingPerformance,
            builderUrl)
        .thenCompose(result -> calculateResult(blockContainer, result, blockPublishingPerformance));
  }

  @Override
  void publishBlockAndSidecars(
      final SignedBeaconBlock block,
      final Supplier<List<BlobSidecar>> blobSidecars,
      final Supplier<List<DataColumnSidecar>> dataColumnSidecars,
      final BlockPublishingPerformance blockPublishingPerformance,
      final Optional<String> builderUrl) {
    publishBlock(block, blockPublishingPerformance).finishStackTrace();
    builderUrl.ifPresent(url -> sendBlockToBuilder(url, block));
  }

  // The builder SHOULD help disseminate the block, we just log a warning in case of exceptions
  // because it is not critical
  private void sendBlockToBuilder(final String url, final SignedBeaconBlock block) {
    stakedBuilderClientProvider.getClient(url).submitSignedBeaconBlock(block).finishWarn(LOG);
  }
}
