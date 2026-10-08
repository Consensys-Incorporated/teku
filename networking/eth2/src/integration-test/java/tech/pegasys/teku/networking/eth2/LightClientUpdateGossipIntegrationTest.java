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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class LightClientUpdateGossipIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipLightClientUpdatesToPeers() throws Exception {
    final Set<LightClientFinalityUpdate> receivedFinalityUpdates = ConcurrentHashMap.newKeySet();
    final Set<LightClientOptimisticUpdate> receivedOptimisticUpdates =
        ConcurrentHashMap.newKeySet();

    final NodeManager node1 =
        createNodeManager(
            b ->
                b.gossipEncoding(GossipEncoding.SSZ_SNAPPY)
                    .gossipedLightClientFinalityUpdateProcessor(OperationProcessor.noop())
                    .gossipedLightClientOptimisticUpdateProcessor(OperationProcessor.noop()));
    final NodeManager node2 =
        createNodeManager(
            b ->
                b.gossipEncoding(GossipEncoding.SSZ_SNAPPY)
                    .gossipedLightClientFinalityUpdateProcessor(accept(receivedFinalityUpdates))
                    .gossipedLightClientOptimisticUpdateProcessor(
                        accept(receivedOptimisticUpdates)));

    waitFor(node1.connect(node2));
    waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    final LightClientFinalityUpdate finalityUpdate =
        dataStructureUtil.randomLightClientFinalityUpdate(UInt64.ONE);
    final LightClientOptimisticUpdate optimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.ONE);
    node1.network().publishLightClientFinalityUpdate(finalityUpdate);
    node1.network().publishLightClientOptimisticUpdate(optimisticUpdate);

    waitFor(
        () -> {
          assertThat(receivedFinalityUpdates).containsExactly(finalityUpdate);
          assertThat(receivedOptimisticUpdates).containsExactly(optimisticUpdate);
        });
  }

  private static <T> OperationProcessor<T> accept(final Set<T> received) {
    return (message, arrivalTimestamp) -> {
      received.add(message);
      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
    };
  }

  private NodeManager createNodeManager(final Consumer<Eth2P2PNetworkBuilder> networkBuilder)
      throws Exception {
    return NodeManager.create(spec, asyncRunner, networkFactory, validatorKeys, networkBuilder);
  }
}
