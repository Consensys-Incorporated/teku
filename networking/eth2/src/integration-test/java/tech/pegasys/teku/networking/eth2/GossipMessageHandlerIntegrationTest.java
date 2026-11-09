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
import static tech.pegasys.teku.infrastructure.async.Waiter.ensureConditionRemainsMet;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkFactory.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class GossipMessageHandlerIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldGossipBlocksAcrossToIndirectlyConnectedPeers() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    final UInt64 blockSlot = UInt64.valueOf(2L);

    // Setup network 1
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder = b -> b.gossipEncoding(gossipEncoding);
    NodeManager node1 = createNodeManager(networkBuilder);
    node1.getChainUpdater().setCurrentSlot(blockSlot);

    // Setup network 2
    Set<SignedBeaconBlock> node2ReceivedBlocks = new HashSet<>();
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b ->
            b.gossipEncoding(gossipEncoding)
                .gossipedBlockProcessor(
                    (block, arrivalTimestamp) -> {
                      node2ReceivedBlocks.add(block);
                      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
                    });
    NodeManager node2 = createNodeManager(networkBuilder2);
    node2.getChainUpdater().setCurrentSlot(blockSlot);

    // Setup network 3
    Set<SignedBeaconBlock> node3ReceivedBlocks = new HashSet<>();
    final Consumer<Eth2P2PNetworkBuilder> networkBuilder3 =
        b ->
            b.gossipEncoding(gossipEncoding)
                .gossipedBlockProcessor(
                    (block, arrivalTimestamp) -> {
                      node3ReceivedBlocks.add(block);
                      return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
                    });
    NodeManager node3 = createNodeManager(networkBuilder3);
    node3.getChainUpdater().setCurrentSlot(blockSlot);

    // Connect networks 1 -> 2 -> 3
    waitFor(node1.connect(node2));
    waitFor(node2.connect(node3));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(2);
          assertThat(node3.network().getPeerCount()).isEqualTo(1);
        });
    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Propagate block from network 1
    final SignedBlockAndState newBlock = node1.getChainBuilder().generateBlockAtSlot(blockSlot);
    node1.gossipBlock(newBlock.getBlock());

    // Verify the expected block was gossiped across the network
    Waiter.waitFor(
        () -> {
          assertThat(node2ReceivedBlocks).containsExactly(newBlock.getBlock());
          assertThat(node3ReceivedBlocks).containsExactly(newBlock.getBlock());
        });
  }

  @Test
  public void shouldNotGossipInvalidBlocks() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    final UInt64 blockSlot = UInt64.valueOf(2L);

    final Set<SignedBeaconBlock> node3ReceivedBlocks = new HashSet<>();

    // Setup network 1
    NodeManager node1 = createNodeManager(b -> b.gossipEncoding(gossipEncoding));
    node1.getChainUpdater().setCurrentSlot(blockSlot);

    // Setup network 2
    NodeManager node2 =
        createNodeManager(
            b1 ->
                b1.gossipEncoding(gossipEncoding)
                    .gossipedBlockProcessor(
                        (block, arrivalTimestamp) -> {
                          // Report block as invalid
                          return SafeFuture.completedFuture(InternalValidationResult.reject("No"));
                        }));
    node2.getChainUpdater().setCurrentSlot(blockSlot);

    // Setup network 3
    NodeManager node3 =
        createNodeManager(
            b ->
                b.gossipEncoding(gossipEncoding)
                    .gossipedBlockProcessor(
                        (block, arrivalTimestamp) -> {
                          node3ReceivedBlocks.add(block);
                          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
                        }));
    node3.getChainUpdater().setCurrentSlot(blockSlot);

    // Connect networks 1 -> 2 -> 3
    waitFor(node1.connect(node2));
    waitFor(node2.connect(node3));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(node1.network().getPeerCount()).isEqualTo(1);
          assertThat(node2.network().getPeerCount()).isEqualTo(2);
          assertThat(node3.network().getPeerCount()).isEqualTo(1);
        });

    // Wait for subscriptions to complete (jvm-libp2p does this asynchronously)
    Thread.sleep(2000);

    // Propagate invalid block from network 1
    final SignedBlockAndState newBlockAndState =
        node1
            .getChainBuilder()
            .generateBlockAtSlot(blockSlot, BlockOptions.create().setWrongProposer(true));
    node1.getChainUpdater().updateBestBlock(newBlockAndState);
    node1.gossipBlock(newBlockAndState.getBlock());

    // Wait for blocks to propagate
    assertThat(node1.network().getPeerCount()).isEqualTo(1);
    // Node2 receives the block from node1 but doesn't not gossip it on to node3 as it's invalid
    ensureConditionRemainsMet(() -> assertThat(node3ReceivedBlocks).isEmpty(), 10000);
  }

  @Test
  public void shouldNotGossipAttestationsAcrossPeersThatAreNotOnTheSameSubnet() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidatableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation, __) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2P2PNetwork network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2P2PNetwork network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary bestBlockAndState = getChainHead(node1);
    Attestation validAttestation = attestationGenerator.validAttestation(bestBlockAndState);
    processedAttestationSubscribers.forEach(
        s -> s.accept(ValidatableAttestation.from(spec, validAttestation)));

    ensureConditionRemainsMet(() -> assertThat(node2attestations).isEmpty());
  }

  @Test
  public void shouldGossipAttestationsAcrossPeersThatAreOnTheSameSubnet() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidatableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation, __) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2P2PNetwork network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2P2PNetwork network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary bestBlockAndState = getChainHead(node1);
    ValidatableAttestation validAttestation =
        ValidatableAttestation.from(spec, attestationGenerator.validAttestation(bestBlockAndState));

    final int subnetId =
        spec.computeSubnetForAttestation(
            bestBlockAndState.getState(), validAttestation.getAttestation());
    node1.network().subscribeToAttestationSubnetId(subnetId);
    node2.network().subscribeToAttestationSubnetId(subnetId);

    waitForTopicRegistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    Waiter.waitFor(
        () -> {
          assertThat(node2attestations.size()).isEqualTo(1);
          assertThat(node2attestations.get(0)).isEqualTo(validAttestation);
        });
  }

  @Test
  public void shouldNotGossipAttestationsWhenPeerDeregistersFromTopic() throws Exception {
    final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    List<ValidatableAttestation> node2attestations = new ArrayList<>();
    Subscribers<ProcessedAttestationListener> processedAttestationSubscribers =
        Subscribers.create(false);

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder1 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (__, ___) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
          b.processedAttestationSubscriptionProvider(processedAttestationSubscribers::subscribe);
        };

    final Consumer<Eth2P2PNetworkBuilder> networkBuilder2 =
        b -> {
          b.gossipEncoding(gossipEncoding);
          b.gossipedAttestationProcessor(
              (attestation, __) -> {
                node2attestations.add(attestation);
                return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
              });
        };

    // Setup network 1
    final NodeManager node1 = createNodeManager(networkBuilder1);
    final Eth2P2PNetwork network1 = node1.network();

    // Setup network 2
    final NodeManager node2 = createNodeManager(networkBuilder2);
    final Eth2P2PNetwork network2 = node2.network();

    // Connect networks 1 -> 2
    waitFor(node1.connect(node2));
    // Wait for connections to get set up
    Waiter.waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    // Propagate attestation from network 1
    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary bestBlockAndState = getChainHead(node1);
    Attestation attestation = attestationGenerator.validAttestation(bestBlockAndState);

    final int subnetId =
        spec.computeSubnetForAttestation(bestBlockAndState.getState(), attestation);

    ValidatableAttestation validAttestation =
        ValidatableAttestation.fromNetwork(spec, attestation, subnetId);

    node1.network().subscribeToAttestationSubnetId(subnetId);
    node2.network().subscribeToAttestationSubnetId(subnetId);

    waitForTopicRegistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    waitForMessageToBeDelivered();

    assertThat(node2attestations.size()).isEqualTo(1);
    assertThat(node2attestations.get(0)).isEqualTo(validAttestation);

    node1.network().unsubscribeFromAttestationSubnetId(subnetId);
    node2.network().unsubscribeFromAttestationSubnetId(subnetId);

    waitForTopicDeregistration();

    processedAttestationSubscribers.forEach(s -> s.accept(validAttestation));

    ensureConditionRemainsMet(
        () -> {
          assertThat(node2attestations.size()).isEqualTo(1);
          assertThat(node2attestations.get(0)).isEqualTo(validAttestation);
        });
  }

  private NodeManager createNodeManager(final Consumer<Eth2P2PNetworkBuilder> networkBuilder)
      throws Exception {
    return NodeManager.create(
        spec,
        asyncRunner,
        networkFactory,
        validatorKeys,
        c -> {
          c.spec(spec);
          networkBuilder.accept(c);
        });
  }

  private void waitForTopicRegistration() throws Exception {
    Thread.sleep(1000);
  }

  private void waitForTopicDeregistration() throws Exception {
    Thread.sleep(1000);
  }

  private void waitForMessageToBeDelivered() throws Exception {
    Thread.sleep(1000);
  }

  private StateAndBlockSummary getChainHead(final NodeManager node1) {
    return safeJoin(node1.storageClient().getChainHead().orElseThrow().asStateAndBlockSummary());
  }
}
