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
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedContributionAndProofGossipManager
    extends AbstractGossipManager<SignedContributionAndProof> {

  public SignedContributionAndProofGossipManager(
      final RecentChainData recentChainData,
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<SignedContributionAndProof> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        schemaDefinitions.getSignedContributionAndProofSchema(),
        message -> Optional.of(message.getMessage().getContribution().getSlot()),
        message ->
            recentChainData
                .getSpec()
                .computeEpochAtSlot(message.getMessage().getContribution().getSlot()),
        networkingConfig,
        GossipFailureLogger.createSuppressing(
            GossipTopicName.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF.toString()),
        debugDataDumper);
  }

  public void publishContribution(final SignedContributionAndProof message) {
    publishMessage(message);
  }
}
