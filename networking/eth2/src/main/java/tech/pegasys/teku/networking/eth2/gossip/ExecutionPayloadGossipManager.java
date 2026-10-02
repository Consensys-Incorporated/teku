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

import java.time.Duration;
import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadGossipManager
    extends AbstractGossipManager<SignedExecutionPayloadEnvelope> {

  // Honest nodes see about one envelope per slot, but each one can be large and costly to
  // validate. Keep the limit small so they can't pile up in memory (worst case 8 x 10 MiB) or take
  // over the P2P workers. A message holds a slot for at most one slot duration.
  static final int MAX_IN_FLIGHT_MESSAGES = 8;

  public ExecutionPayloadGossipManager(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<SignedExecutionPayloadEnvelope> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        GossipTopicName.EXECUTION_PAYLOAD,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        SchemaDefinitionsGloas.required(
                spec.atEpoch(forkInfo.getFork().getEpoch()).getSchemaDefinitions())
            .getSignedExecutionPayloadEnvelopeSchema(),
        message -> Optional.of(message.getMessage().getSlot()),
        message -> recentChainData.getSpec().computeEpochAtSlot(message.getMessage().getSlot()),
        networkingConfig,
        GossipFailureLogger.createSuppressing(GossipTopicName.EXECUTION_PAYLOAD.toString()),
        debugDataDumper,
        MAX_IN_FLIGHT_MESSAGES,
        Duration.ofMillis(
            spec.atEpoch(forkInfo.getFork().getEpoch()).getConfig().getSlotDurationMillis()),
        metricsSystem);
  }

  public SafeFuture<Void> publish(final SignedExecutionPayloadEnvelope message) {
    return publishMessageWithFeedback(message);
  }
}
