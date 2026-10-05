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

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitGossipManager extends AbstractGossipManager<SignedVoluntaryExit> {

  public VoluntaryExitGossipManager(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<SignedVoluntaryExit> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.VOLUNTARY_EXIT,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        SignedVoluntaryExit.SSZ_SCHEMA,
        exit -> Optional.empty(),
        // The exit's epoch is only the earliest epoch it can be processed at, so an exit naming an
        // epoch from an earlier fork is still valid. Always consider exits to match the fork of the
        // topic they arrived on (ie disable fork checking at this level) and leave validation to
        // the processor.
        exit -> forkInfo.getFork().getEpoch(),
        networkingConfig,
        GossipFailureLogger.createNonSuppressing(GossipTopicName.VOLUNTARY_EXIT.toString()),
        debugDataDumper);
  }

  public void publishVoluntaryExit(final SignedVoluntaryExit message) {
    publishMessage(message);
  }
}
