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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AggregateGossipManager extends AbstractGossipManager<SignedAggregateAndProof> {

  public AggregateGossipManager(
      final Spec spec,
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<ValidatableAttestation> processor,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.BEACON_AGGREGATE_AND_PROOF,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        (proofMessage, arrivalTimestamp) ->
            processor.process(
                ValidatableAttestation.aggregateFromNetwork(
                    recentChainData.getSpec(), proofMessage),
                arrivalTimestamp),
        spec.atEpoch(forkInfo.getFork().getEpoch())
            .getSchemaDefinitions()
            .getSignedAggregateAndProofSchema(),
        message -> Optional.of(message.getMessage().getAggregate().getData().getSlot()),
        message -> spec.computeEpochAtSlot(message.getMessage().getAggregate().getData().getSlot()),
        spec.getNetworkingConfig(),
        GossipFailureLogger.createSuppressing(
            GossipTopicName.BEACON_AGGREGATE_AND_PROOF.toString()),
        debugDataDumper);
  }

  public void onNewAggregate(final ValidatableAttestation validatableAttestation) {
    if (!validatableAttestation.isAggregate() || !validatableAttestation.markGossiped()) {
      return;
    }
    publishMessage(validatableAttestation.getSignedAggregateAndProof());
  }
}
