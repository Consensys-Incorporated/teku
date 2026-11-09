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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public abstract class AbstractTopicHandlerTest<T> {

  public static final UInt64 BELLATRIX_FORK_EPOCH = UInt64.valueOf(2);
  protected final Spec spec =
      TestSpecFactory.createMinimalWithAltairAndBellatrixForkEpoch(
          UInt64.ONE, BELLATRIX_FORK_EPOCH);
  protected final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  protected final StorageSystem storageSystem = createStorageSystem();

  protected final ChainBuilder chainBuilder = storageSystem.chainBuilder();

  protected final RecentChainData recentChainData = storageSystem.recentChainData();
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  protected final OperationProcessor<T> processor = mock(OperationProcessor.class);

  protected Eth2TopicHandler<?> topicHandler;

  protected ForkInfo forkInfo;
  protected Bytes4 forkDigest;
  protected UInt64 validSlot = spec.computeStartSlotAtEpoch(BELLATRIX_FORK_EPOCH);
  protected UInt64 wrongForkSlot = UInt64.ONE;

  @BeforeEach
  public void setup() throws Exception {
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem
        .chainUpdater()
        .updateBestBlock(storageSystem.chainUpdater().advanceChainUntil(validSlot));
    this.forkInfo = recentChainData.getForkInfo(BELLATRIX_FORK_EPOCH).orElseThrow();
    this.forkDigest = recentChainData.getForkDigest(BELLATRIX_FORK_EPOCH);
    this.topicHandler = createHandler();
  }

  protected abstract Eth2TopicHandler<?> createHandler();

  protected StorageSystem createStorageSystem() {
    return InMemoryStorageSystemBuilder.buildDefault(spec);
  }

  protected StateAndBlockSummary getChainHead() {
    return safeJoin(recentChainData.getChainHead().orElseThrow().asStateAndBlockSummary());
  }
}
