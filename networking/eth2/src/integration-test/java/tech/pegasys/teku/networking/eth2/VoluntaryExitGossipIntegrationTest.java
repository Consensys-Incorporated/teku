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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.VoluntaryExitGenerator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class VoluntaryExitGossipIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipVoluntaryExitToPeers() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    final SpecConfig config = spec.getGenesisSpecConfig();
    final UInt64 blockSlot =
        config.getShardCommitteePeriod().plus(2).times(config.getSlotsPerEpoch());

    // Set up publishers & consumers
    Set<SignedVoluntaryExit> receivedVoluntaryExits = new HashSet<>();
    final OperationProcessor<SignedVoluntaryExit> operationProcessor =
        (voluntaryExit, __) -> {
          receivedVoluntaryExits.add(voluntaryExit);
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        };

    // Setup network 1
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder = b -> b.gossipEncoding(gossipEncoding);
    NodeManager node1 = createNodeManager(networkBuilder);
    node1.getChainUpdater().setCurrentSlot(blockSlot);

    // Setup network 2
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> b.gossipEncoding(gossipEncoding).gossipedVoluntaryExitProcessor(operationProcessor);
    NodeManager node2 = createNodeManager(networkBuilder2);
    node2.getChainUpdater().setCurrentSlot(blockSlot);

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Create voluntary exit
    final SignedBlockAndState blockAndState =
        node1.getChainBuilder().generateBlockAtSlot(blockSlot);
    node1.getChainUpdater().updateBestBlock(blockAndState);
    final SafeFuture<Optional<BeaconState>> stateFuture =
        node1.storageClient().getStore().retrieveBlockState(blockAndState.getRoot());
    assertThat(stateFuture).isCompleted();
    final BeaconState state = safeJoin(stateFuture).orElseThrow();
    final VoluntaryExitGenerator exitGenerator =
        new VoluntaryExitGenerator(spec, node1.getChainBuilder().getValidatorKeys());
    final SignedVoluntaryExit voluntaryExit = exitGenerator.valid(state, 0);

    // Publish voluntary exit
    node1.network().publishVoluntaryExit(voluntaryExit);

    // Verify the expected exit was gossiped across the network
    Waiter.waitFor(() -> assertThat(receivedVoluntaryExits).containsExactly(voluntaryExit));
  }

  private NodeManager createNodeManager(final Consumer<Eth2P2PNetworkBuilder> networkBuilder)
      throws Exception {
    return NodeManager.create(spec, asyncRunner, networkFactory, validatorKeys, networkBuilder);
  }
}
