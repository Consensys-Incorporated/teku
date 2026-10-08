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
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class LightClientUpdateGossipManager<T extends SszData> extends AbstractGossipManager<T> {

  private LightClientUpdateGossipManager(
      final RecentChainData recentChainData,
      final GossipTopicName topicName,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<T> processor,
      final SszSchema<T> schema,
      final Function<T, UInt64> getAttestedSlot,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    super(
        recentChainData,
        topicName,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        schema,
        message -> Optional.of(getAttestedSlot.apply(message)),
        message -> recentChainData.getSpec().computeEpochAtSlot(getAttestedSlot.apply(message)),
        networkingConfig,
        GossipFailureLogger.createSuppressing(topicName.toString()),
        debugDataDumper);
  }

  public static LightClientUpdateGossipManager<LightClientFinalityUpdate> createFinality(
      final RecentChainData recentChainData,
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<LightClientFinalityUpdate> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    return new LightClientUpdateGossipManager<>(
        recentChainData,
        GossipTopicName.LIGHT_CLIENT_FINALITY_UPDATE,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        schemaDefinitions.getLightClientFinalityUpdateSchema(),
        update -> update.getAttestedHeader().getBeacon().getSlot(),
        networkingConfig,
        debugDataDumper);
  }

  public static LightClientUpdateGossipManager<LightClientOptimisticUpdate> createOptimistic(
      final RecentChainData recentChainData,
      final SchemaDefinitionsAltair schemaDefinitions,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<LightClientOptimisticUpdate> processor,
      final NetworkingSpecConfig networkingConfig,
      final DebugDataDumper debugDataDumper) {
    return new LightClientUpdateGossipManager<>(
        recentChainData,
        GossipTopicName.LIGHT_CLIENT_OPTIMISTIC_UPDATE,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        forkDigest,
        processor,
        schemaDefinitions.getLightClientOptimisticUpdateSchema(),
        update -> update.getAttestedHeader().getBeacon().getSlot(),
        networkingConfig,
        debugDataDumper);
  }

  public void publish(final T message) {
    publishMessage(message);
  }
}
