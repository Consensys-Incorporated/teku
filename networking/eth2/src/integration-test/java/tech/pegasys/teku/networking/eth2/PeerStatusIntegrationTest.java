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

import java.time.Duration;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class PeerStatusIntegrationTest {

  private static final int VALIDATOR_COUNT = 16;
  private final Spec spec =
      TestSpecFactory.createMinimalPhase0(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
  private final StorageSystem storageSystem = createStorageSystem();

  private final RecentChainData recentChainData1 = storageSystem.recentChainData();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnection() throws Exception {
    final StorageSystem system2 = createStorageSystem();
    final RecentChainData recentChainData2 = system2.recentChainData();
    assertThat(recentChainData1.getBestBlockRoot()).isEqualTo(recentChainData2.getBestBlockRoot());

    final Eth2P2PNetwork network1 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData1)
            .startNetwork();
    final Eth2P2PNetwork network2 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData2)
            .startNetwork();

    waitFor(network1.connect(network1.createPeerAddress(network2.getNodeAddresses().get(0))));
    waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    final Eth2Peer network1ViewOfPeer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
    assertStatusMatchesStorage(recentChainData2, network1ViewOfPeer2.getStatus());
    // When the finalized epoch is genesis we should use a zero finalized root (from the state)
    // This differs from what recentChainData.getFinalizedCheckpoint will have at genesis
    assertThat(network1ViewOfPeer2.getStatus().getFinalizedRoot()).isEqualTo(Bytes32.ZERO);
  }

  @Test
  public void shouldExchangeStatusMessagesOnConnectionAfterFinalization() throws Exception {
    final StorageSystem system2 = createStorageSystem();
    final RecentChainData recentChainData2 = system2.recentChainData();
    assertThat(recentChainData1.getBestBlockRoot()).isEqualTo(recentChainData2.getBestBlockRoot());

    storageSystem.chainUpdater().finalizeCurrentChain();
    system2.chainUpdater().syncWith(storageSystem.chainBuilder());

    assertThat(recentChainData1.getBestBlockRoot()).isEqualTo(recentChainData2.getBestBlockRoot());
    assertThat(recentChainData1.getFinalizedCheckpoint())
        .isEqualTo(recentChainData2.getFinalizedCheckpoint());

    assertThat(recentChainData1.getFinalizedCheckpoint())
        .contains(safeJoin(recentChainData1.getBestState().orElseThrow()).getFinalizedCheckpoint());

    final Eth2P2PNetwork network1 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData1)
            .startNetwork();
    final Eth2P2PNetwork network2 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData2)
            .startNetwork();

    waitFor(network1.connect(network1.createPeerAddress(network2.getNodeAddresses().get(0))));
    waitFor(
        () -> {
          assertThat(network1.getPeerCount()).isEqualTo(1);
          assertThat(network2.getPeerCount()).isEqualTo(1);
        });

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    final Eth2Peer network1ViewOfPeer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
    assertStatusMatchesStorage(recentChainData2, network1ViewOfPeer2.getStatus());
  }

  @Test
  public void shouldUpdatePeerStatus() throws Exception {
    final Eth2P2PNetwork network1 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData1)
            .startNetwork();

    final StorageSystem storageSystem2 = createStorageSystem();
    final RecentChainData recentChainData2 = storageSystem2.recentChainData();
    final Eth2P2PNetwork network2 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .recentChainData(recentChainData2)
            .peer(network1)
            .startNetwork();

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    // Peer 1 advances
    this.storageSystem.chainUpdater().advanceChain(10);

    final PeerStatus updatedStatusData = waitFor(network2ViewOfPeer1.sendStatus());
    assertStatusMatchesStorage(recentChainData1, updatedStatusData);
    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());
  }

  @Test
  public void shouldUpdatePeerStatusPeriodically() throws Exception {
    Duration statusUpdateInterval = Duration.ofSeconds(2);
    final Eth2P2PNetwork network1 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .eth2StatusUpdateInterval(statusUpdateInterval)
            .recentChainData(recentChainData1)
            .startNetwork();

    final StorageSystem storageSystem2 = createStorageSystem();
    final RecentChainData recentChainData2 = storageSystem2.recentChainData();
    final Eth2P2PNetwork network2 =
        networkFactory
            .builder()
            .spec(spec)
            .rpcEncoding(rpcEncoding)
            .eth2StatusUpdateInterval(statusUpdateInterval)
            .recentChainData(recentChainData2)
            .peer(network1)
            .startNetwork();

    final Eth2Peer network2ViewOfPeer1 = network2.getPeer(network1.getNodeId()).orElseThrow();

    assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus());

    // Peer 1 advances
    this.storageSystem.chainUpdater().advanceChain(10);

    waitFor(() -> assertStatusMatchesStorage(recentChainData1, network2ViewOfPeer1.getStatus()));
  }

  private void assertStatusMatchesStorage(
      final RecentChainData storageClient, final PeerStatus status) {
    final BeaconState state = safeJoin(storageClient.getBestState().orElseThrow());
    assertStatus(
        status,
        storageClient.getCurrentForkDigest().orElseThrow(),
        state.getFinalizedCheckpoint().getRoot(),
        state.getFinalizedCheckpoint().getEpoch(),
        storageClient.getBestBlockRoot().orElseThrow(),
        storageClient.getHeadSlot());
  }

  private void assertStatus(
      final PeerStatus status,
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot) {
    assertThat(status.getForkDigest()).isEqualTo(forkDigest);
    assertThat(status.getFinalizedRoot()).isEqualTo(finalizedRoot);
    assertThat(status.getFinalizedEpoch()).isEqualTo(finalizedEpoch);
    assertThat(status.getHeadRoot()).isEqualTo(headRoot);
    assertThat(status.getHeadSlot()).isEqualTo(headSlot);
  }

  private StorageSystem createStorageSystem() {
    final StorageSystem system =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .numberOfValidators(VALIDATOR_COUNT)
            .build();
    system.chainUpdater().initializeGenesis(false);
    return system;
  }
}
