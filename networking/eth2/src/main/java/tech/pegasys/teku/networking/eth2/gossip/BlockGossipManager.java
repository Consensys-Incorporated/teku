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

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.GossipInFlightLimiter;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockGossipManager extends AbstractGossipManager<SignedBeaconBlock> {

  // Limits the total uncompressed size of blocks waiting to be validated or being validated.
  // Honest nodes validate about one block per slot, and this still leaves room for several blocks
  // of the maximum gossip size. A block of a million empty transactions holds about 1.5x its
  // uncompressed size once decoded, so this keeps such blocks to about 60 MiB, where the shared P2P
  // queue alone would hold thousands of them.
  static final int MAX_IN_FLIGHT_BYTES_MAX_PAYLOAD_SIZE_MULTIPLIER = 4;

  public BlockGossipManager(
      final RecentChainData recentChainData,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<SignedBeaconBlock> processor,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.BEACON_BLOCK,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        spec.atEpoch(forkInfo.getFork().getEpoch())
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema(),
        block -> Optional.of(block.getSlot()),
        block -> spec.computeEpochAtSlot(block.getSlot()),
        spec.getNetworkingConfig(),
        GossipFailureLogger.createNonSuppressing(GossipTopicName.BEACON_BLOCK.toString()),
        debugDataDumper,
        new FarFutureBlockFilter(spec, recentChainData, metricsSystem),
        new GossipInFlightLimiter(
            metricsSystem,
            GossipTopicName.BEACON_BLOCK.toString(),
            (long) MAX_IN_FLIGHT_BYTES_MAX_PAYLOAD_SIZE_MULTIPLIER
                * spec.getNetworkingConfig().getMaxPayloadSize()));
  }

  public SafeFuture<Void> publishBlock(final SignedBeaconBlock message) {
    return publishMessageWithFeedback(message);
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }
}
