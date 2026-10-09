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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUpdateGossipManagerTest {

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final TopicChannel topicChannel = mock(TopicChannel.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private RecentChainData recentChainData;
  private SchemaDefinitionsAltair schemaDefinitions;
  private ForkInfo forkInfo;
  private Bytes4 forkDigest;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    recentChainData = InMemoryStorageSystemBuilder.buildDefault(spec).recentChainData();
    schemaDefinitions = SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions());
    forkInfo = new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());
    forkDigest = dataStructureUtil.randomBytes4();
    when(topicChannel.gossip(any())).thenReturn(SafeFuture.COMPLETE);
    doReturn(topicChannel).when(gossipNetwork).subscribe(any(), any());
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  void shouldSubscribeAndPublishFinalityUpdate() {
    final LightClientUpdateGossipManager<LightClientFinalityUpdate> gossipManager =
        LightClientUpdateGossipManager.createFinality(
            recentChainData,
            schemaDefinitions,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            mock(OperationProcessor.class),
            spec.getNetworkingConfig(),
            DebugDataDumper.NOOP);
    gossipManager.subscribe();
    verify(gossipNetwork)
        .subscribe(
            endsWith("/" + GossipTopicName.LIGHT_CLIENT_FINALITY_UPDATE + "/ssz_snappy"), any());

    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(UInt64.ONE);
    gossipManager.publish(update);

    verify(topicChannel).gossip(gossipEncoding.encode(update));
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  void shouldSubscribeAndPublishOptimisticUpdate() {
    final LightClientUpdateGossipManager<LightClientOptimisticUpdate> gossipManager =
        LightClientUpdateGossipManager.createOptimistic(
            recentChainData,
            schemaDefinitions,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            mock(OperationProcessor.class),
            spec.getNetworkingConfig(),
            DebugDataDumper.NOOP);
    gossipManager.subscribe();
    verify(gossipNetwork)
        .subscribe(
            endsWith("/" + GossipTopicName.LIGHT_CLIENT_OPTIMISTIC_UPDATE + "/ssz_snappy"), any());

    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);
    gossipManager.publish(update);

    verify(topicChannel).gossip(gossipEncoding.encode(update));
  }
}
