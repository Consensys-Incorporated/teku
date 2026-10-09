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

package tech.pegasys.teku.networking.eth2.peers;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.network.p2p.peer.StubPeer;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy.Shuffler;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class Eth2PeerSelectionStrategyTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final SchemaDefinitions SCHEMA_DEFINITIONS = SPEC.getGenesisSchemaDefinitions();
  private static final Optional<EnrForkId> ENR_FORK_ID = Optional.empty();
  private static final PeerAddress PEER1 = new PeerAddress(new MockNodeId(1));
  private static final PeerAddress PEER2 = new PeerAddress(new MockNodeId(2));
  private static final PeerAddress PEER3 = new PeerAddress(new MockNodeId(3));
  private static final PeerAddress PEER4 = new PeerAddress(new MockNodeId(4));
  private static final DiscoveryPeer DISCOVERY_PEER1 = createDiscoveryPeer(PEER1, 1);
  private static final DiscoveryPeer DISCOVERY_PEER2 = createDiscoveryPeer(PEER2, 2);
  private static final DiscoveryPeer DISCOVERY_PEER3 = createDiscoveryPeer(PEER3, 3);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> network = mock(P2PNetwork.class);

  private final PeerPools peerPools = new PeerPools();
  private final StubPeerScorer peerScorer = new StubPeerScorer();
  private final PeerSubnetSubscriptions peerSubnetSubscriptions =
      mock(PeerSubnetSubscriptions.class);
  private final PeerSubnetSubscriptions.Factory peerSubnetSubscriptionsFactory =
      network -> peerSubnetSubscriptions;
  private final ReputationManager reputationManager = mock(ReputationManager.class);

  private Shuffler shuffler = list -> {};

  @BeforeEach
  void setUp() {
    when(peerSubnetSubscriptions.createScorer()).thenReturn(peerScorer);
    when(reputationManager.isConnectionInitiationAllowed(any())).thenReturn(true);
    when(network.createPeerAddress(any(DiscoveryPeer.class)))
        .thenAnswer(
            invocation -> {
              final DiscoveryPeer peer = invocation.getArgument(0);
              return Optional.of(new PeerAddress(new MockNodeId(peer.getPublicKey())));
            });
  }

  @Test
  void selectPeersToConnect_shouldNotConnectToPeersWithBadReputation() {
    final Eth2PeerSelectionStrategy strategy = createStrategy();
    when(reputationManager.isConnectionInitiationAllowed(PEER1)).thenReturn(false);
    when(reputationManager.isConnectionInitiationAllowed(PEER2)).thenReturn(true);

    assertThat(
            strategy.selectPeersToConnect(
                network, peerPools, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2)))
        .containsExactly(PEER2);
  }

  @Test
  void selectPeersToConnect_shouldLimitNumberOfNewConnections() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(1, 2, 0);

    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER1, PEER2);
  }

  @Test
  void selectPeersToConnect_shouldConnectToNewPeersWhenSubnetsNeedsMoreSubscribers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(1, 2, 0);
    when(network.getPeerCount()).thenReturn(2); // At upper bound of peers
    when(peerSubnetSubscriptions.getSubscribersRequired()).thenReturn(2);
    peerScorer.setScore(
        DISCOVERY_PEER1.getPersistentAttestationSubnets(),
        DISCOVERY_PEER1.getSyncCommitteeSubnets(),
        0);
    peerScorer.setScore(
        DISCOVERY_PEER2.getPersistentAttestationSubnets(),
        DISCOVERY_PEER1.getSyncCommitteeSubnets(),
        200);
    peerScorer.setScore(
        DISCOVERY_PEER3.getPersistentAttestationSubnets(),
        DISCOVERY_PEER1.getSyncCommitteeSubnets(),
        150);

    // Connect to additional peers to try to fill subnets even though it goes over the target
    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactlyInAnyOrder(PEER2, PEER3);
  }

  @Test
  void selectPeersToConnect_shouldConnectToHighestScoringPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(2, 2, 0);

    final DiscoveryPeer discoveryPeer1 = createDiscoveryPeer(PEER1, 1);
    final DiscoveryPeer discoveryPeer2 = createDiscoveryPeer(PEER2, 2);
    final DiscoveryPeer discoveryPeer3 = createDiscoveryPeer(PEER3, 3);
    final DiscoveryPeer discoveryPeer4 = createDiscoveryPeer(PEER4, 4);
    peerScorer.setScore(
        discoveryPeer1.getPersistentAttestationSubnets(),
        discoveryPeer1.getSyncCommitteeSubnets(),
        100);
    peerScorer.setScore(
        discoveryPeer2.getPersistentAttestationSubnets(),
        discoveryPeer2.getSyncCommitteeSubnets(),
        200);
    peerScorer.setScore(
        discoveryPeer3.getPersistentAttestationSubnets(),
        discoveryPeer3.getSyncCommitteeSubnets(),
        150);
    peerScorer.setScore(
        discoveryPeer4.getPersistentAttestationSubnets(),
        discoveryPeer4.getSyncCommitteeSubnets(),
        500);

    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(discoveryPeer1, discoveryPeer2, discoveryPeer3, discoveryPeer4)))
        .containsExactlyInAnyOrder(PEER2, PEER4);
  }

  @Test
  void selectPeersToConnect_shouldNotConnectToAlreadyConnectedPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy();
    when(network.isConnected(PEER1)).thenReturn(true);
    when(network.isConnected(PEER3)).thenReturn(true);

    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER2);
  }

  @Test
  void selectPeersToConnect_shouldSkipCandidateWithNoDialableAddress() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(1, 1, 0);
    when(network.createPeerAddress(DISCOVERY_PEER1)).thenReturn(Optional.empty());

    assertThat(
            strategy.selectPeersToConnect(
                network, peerPools, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2)))
        .containsExactly(PEER2);
  }

  @Test
  void selectPeersToConnect_shouldSkipRandomCandidateWithNoDialableAddress() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 4, 1);
    withShuffleOrder(DISCOVERY_PEER1, DISCOVERY_PEER2);
    when(network.createPeerAddress(DISCOVERY_PEER1)).thenReturn(Optional.empty());

    assertThat(
            strategy.selectPeersToConnect(
                network, peerPools, () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2)))
        .containsExactly(PEER2);
    assertThat(peerPools.getPeerConnectionType(PEER1.getId()))
        .isNotEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
    assertThat(peerPools.getPeerConnectionType(PEER2.getId()))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
  }

  @Test
  void selectPeersToConnect_shouldConnectToRandomPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 4, 2);
    withShuffleOrder(DISCOVERY_PEER3, DISCOVERY_PEER1, DISCOVERY_PEER2);
    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER3, PEER1);
    assertThat(peerPools.getPeerConnectionType(PEER3.getId()))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
    assertThat(peerPools.getPeerConnectionType(PEER1.getId()))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
  }

  @Test
  void selectPeersToConnect_shouldExcludeRandomlySelectedPeersWhenSelectingBasedOnScore() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(3, 4, 2);
    withShuffleOrder(DISCOVERY_PEER3, DISCOVERY_PEER1, DISCOVERY_PEER2);
    assertThat(
            strategy.selectPeersToConnect(
                network,
                peerPools,
                () -> List.of(DISCOVERY_PEER1, DISCOVERY_PEER2, DISCOVERY_PEER3)))
        .containsExactly(PEER3, PEER1, PEER2);

    assertThat(peerPools.getPeerConnectionType(PEER1.getId()))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
    assertThat(peerPools.getPeerConnectionType(PEER2.getId()))
        .isEqualTo(PeerConnectionType.SCORE_BASED);
    assertThat(peerPools.getPeerConnectionType(PEER3.getId()))
        .isEqualTo(PeerConnectionType.RANDOMLY_SELECTED);
  }

  @Test
  void selectPeersToConnect_shouldFillPeerTargetWhenRandomlySelectedPeersAlreadyConnected() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(10, 20, 5);
    final List<StubPeer> existingRandomlySelectedPeers =
        IntStream.range(0, 5).mapToObj(index -> new StubPeer(new MockNodeId(20 + index))).toList();
    existingRandomlySelectedPeers.forEach(
        peer -> peerPools.addPeerToPool(peer.getId(), PeerConnectionType.RANDOMLY_SELECTED));
    when(network.getPeerCount()).thenReturn(9);
    when(network.streamPeers()).thenAnswer(__ -> existingRandomlySelectedPeers.stream());

    final List<DiscoveryPeer> candidatePeers =
        IntStream.range(0, 12)
            .mapToObj(index -> createDiscoveryPeer(new PeerAddress(new MockNodeId(index + 1))))
            .toList();

    assertThat(strategy.selectPeersToConnect(network, peerPools, () -> candidatePeers)).hasSize(11);
  }

  @Test
  void selectPeersToConnect_shouldFillRandomTargetWhenInboundPeersAlreadyFillPeerTarget() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(10, 20, 5);
    final List<Peer> inboundPeers =
        IntStream.range(0, 20)
            .mapToObj(index -> (Peer) new InboundStubPeer(new MockNodeId(20 + index)))
            .toList();
    inboundPeers
        .subList(0, 5)
        .forEach(
            peer -> peerPools.addPeerToPool(peer.getId(), PeerConnectionType.RANDOMLY_SELECTED));
    when(network.getPeerCount()).thenReturn(20);
    when(network.streamPeers()).thenAnswer(__ -> inboundPeers.stream());

    final List<DiscoveryPeer> candidatePeers =
        IntStream.range(0, 5)
            .mapToObj(index -> createDiscoveryPeer(new PeerAddress(new MockNodeId(index + 1))))
            .toList();

    assertThat(strategy.selectPeersToConnect(network, peerPools, () -> candidatePeers)).hasSize(5);
  }

  @Test
  void selectPeersToDisconnect_shouldDisconnectLowestScoringPeersWhenPeerCountExceedsUpperBound() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 1, 0);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    peerScorer.setScore(peer1.getId(), 100);
    peerScorer.setScore(peer2.getId(), 200);
    peerScorer.setScore(peer3.getId(), 150);

    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(peer1, peer3);
  }

  @Test
  void selectPeersToDisconnect_shouldNotDisconnectFromStaticPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 0, 0);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    peerPools.addPeerToPool(peer2.getId(), PeerConnectionType.STATIC);
    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(peer1, peer3);
  }

  @Test
  void selectPeersToDisconnect_shouldNotDisconnectRandomlySelectedPeersBasedOnScore() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(2, 2, 1);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    peerPools.addPeerToPool(peer2.getId(), PeerConnectionType.RANDOMLY_SELECTED);
    peerScorer.setScore(peer1.getId(), 100);
    peerScorer.setScore(peer2.getId(), 0);
    peerScorer.setScore(peer3.getId(), 50);
    // peer2 has the lowest score but is safe because it's in the randomly selected pool
    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(peer3);
  }

  @Test
  void selectPeersToDisconnect_shouldMoveExcessRandomlySelectedPeersBackToScoreBasedPool() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(2, 2, 1);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    peerPools.addPeerToPool(peer1.getId(), PeerConnectionType.RANDOMLY_SELECTED);
    peerPools.addPeerToPool(peer2.getId(), PeerConnectionType.RANDOMLY_SELECTED);

    peerScorer.setScore(peer1.getId(), 100);
    peerScorer.setScore(peer2.getId(), 200);
    peerScorer.setScore(peer3.getId(), 50);
    withShuffleOrder(peer2, peer1, peer3);

    // Peer2 was dropped from the random pool but had a better score than peer3 so was kept
    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(peer3);
    assertThat(peerPools.getPeerConnectionType(peer2.getId()))
        .isEqualTo(PeerConnectionType.SCORE_BASED);
  }

  @Test
  void selectPeersToDisconnect_shouldDisconnectExcessRandomlySelectedPeersWhenOutscored() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(2, 2, 1);
    final StubPeer peer1 = new StubPeer(new MockNodeId(1));
    final StubPeer peer2 = new StubPeer(new MockNodeId(2));
    final StubPeer peer3 = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers()).thenReturn(Stream.of(peer1, peer2, peer3));

    peerPools.addPeerToPool(peer1.getId(), PeerConnectionType.RANDOMLY_SELECTED);
    peerPools.addPeerToPool(peer2.getId(), PeerConnectionType.RANDOMLY_SELECTED);

    peerScorer.setScore(peer1.getId(), 100);
    peerScorer.setScore(peer2.getId(), 50);
    peerScorer.setScore(peer3.getId(), 250);
    withShuffleOrder(peer2, peer1, peer3);

    // Peer2 was dropped from the random pool and had the worst score so got dropped
    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(peer2);
  }

  @Test
  void selectPeersToDisconnect_shouldReserveSlotsForMinimumRandomlySelectedOutboundPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(10, 20, 5);
    final List<Peer> inboundPeers =
        IntStream.range(0, 20)
            .mapToObj(index -> (Peer) new InboundStubPeer(new MockNodeId(20 + index)))
            .toList();
    when(network.getPeerCount()).thenReturn(20);
    when(network.streamPeers()).thenAnswer(__ -> inboundPeers.stream());

    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .hasSize(5)
        .allMatch(Peer::connectionInitiatedRemotely);
  }

  @Test
  void selectPeersToDisconnect_shouldReserveSlotsForRandomAndSubnetRequiredOutboundPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(10, 20, 5);
    final List<Peer> inboundPeers =
        IntStream.range(0, 20)
            .mapToObj(index -> (Peer) new InboundStubPeer(new MockNodeId(20 + index)))
            .toList();
    when(network.getPeerCount()).thenReturn(20);
    when(network.streamPeers()).thenAnswer(__ -> inboundPeers.stream());
    when(peerSubnetSubscriptions.getSubscribersRequired()).thenReturn(3);

    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .hasSize(8)
        .allMatch(Peer::connectionInitiatedRemotely);
  }

  @Test
  void selectPeersToDisconnect_shouldNotProtectRemotelyInitiatedRandomlySelectedPeers() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 2, 1);
    final StubPeer locallyInitiatedRandomPeer = new StubPeer(new MockNodeId(1));
    final StubPeer remotelyInitiatedRandomPeer = new InboundStubPeer(new MockNodeId(2));
    final StubPeer scoreBasedPeer = new StubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers())
        .thenReturn(
            Stream.of(locallyInitiatedRandomPeer, remotelyInitiatedRandomPeer, scoreBasedPeer));

    peerPools.addPeerToPool(
        locallyInitiatedRandomPeer.getId(), PeerConnectionType.RANDOMLY_SELECTED);
    peerPools.addPeerToPool(
        remotelyInitiatedRandomPeer.getId(), PeerConnectionType.RANDOMLY_SELECTED);

    peerScorer.setScore(locallyInitiatedRandomPeer.getId(), 0);
    peerScorer.setScore(remotelyInitiatedRandomPeer.getId(), 50);
    peerScorer.setScore(scoreBasedPeer.getId(), 100);

    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(remotelyInitiatedRandomPeer);
  }

  @Test
  void selectPeersToDisconnect_shouldDropRemotelyInitiatedPeersFirstWhenRemoteLimitExceeded() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(0, 2, 1);
    final StubPeer locallyInitiatedScoreBasedPeer = new StubPeer(new MockNodeId(1));
    final StubPeer remotelyInitiatedScoreBasedPeer1 = new InboundStubPeer(new MockNodeId(2));
    final StubPeer remotelyInitiatedScoreBasedPeer2 = new InboundStubPeer(new MockNodeId(3));
    when(network.getPeerCount()).thenReturn(3);
    when(network.streamPeers())
        .thenReturn(
            Stream.of(
                locallyInitiatedScoreBasedPeer,
                remotelyInitiatedScoreBasedPeer1,
                remotelyInitiatedScoreBasedPeer2));

    peerScorer.setScore(locallyInitiatedScoreBasedPeer.getId(), 0);
    peerScorer.setScore(remotelyInitiatedScoreBasedPeer1.getId(), 100);
    peerScorer.setScore(remotelyInitiatedScoreBasedPeer2.getId(), 200);

    assertThat(strategy.selectPeersToDisconnect(network, peerPools))
        .containsExactlyInAnyOrder(remotelyInitiatedScoreBasedPeer1);
  }

  @Test
  void selectPeersToDisconnect_shouldNotDropRemotelyInitiatedPeersWhenPeerCountIsBelowLowerBound() {
    final Eth2PeerSelectionStrategy strategy = createStrategy(25, 35, 25);
    final List<Peer> remotelyInitiatedPeers =
        IntStream.range(0, 12)
            .mapToObj(index -> (Peer) new InboundStubPeer(new MockNodeId(index)))
            .toList();
    final List<Peer> locallyInitiatedPeers =
        IntStream.range(12, 18)
            .mapToObj(index -> (Peer) new StubPeer(new MockNodeId(index)))
            .toList();
    when(network.getPeerCount()).thenReturn(18);
    when(network.streamPeers())
        .thenAnswer(
            __ -> Stream.concat(remotelyInitiatedPeers.stream(), locallyInitiatedPeers.stream()));
    when(peerSubnetSubscriptions.getSubscribersRequired()).thenReturn(1);

    assertThat(strategy.selectPeersToDisconnect(network, peerPools)).isEmpty();
  }

  private Eth2PeerSelectionStrategy createStrategy() {
    return createStrategy(10, 20, 0);
  }

  private Eth2PeerSelectionStrategy createStrategy(
      final int peerCountLowerBound, final int peerCountUpperBound, final int minimumRandomPeers) {
    return new Eth2PeerSelectionStrategy(
        new TargetPeerRange(peerCountLowerBound, peerCountUpperBound, minimumRandomPeers),
        peerSubnetSubscriptionsFactory,
        reputationManager,
        list -> shuffler.shuffle(list));
  }

  private void withShuffleOrder(final Object... values) {
    final List<?> shuffledOrder = asList(values);
    shuffler = list -> list.sort(Comparator.comparingInt(shuffledOrder::indexOf));
  }

  private static DiscoveryPeer createDiscoveryPeer(final PeerAddress peer, final int... attnets) {
    return createDiscoveryPeer(peer.getId().toBytes(), attnets);
  }

  private static DiscoveryPeer createDiscoveryPeer(final Bytes peerId, final int... attnets) {
    return new DiscoveryPeer(
        peerId,
        peerId,
        Optional.of(
            new InetSocketAddress(
                InetAddress.getLoopbackAddress(), peerId.trimLeadingZeros().toInt())),
        Optional.empty(),
        ENR_FORK_ID,
        SCHEMA_DEFINITIONS.getAttnetsENRFieldSchema().ofBits(attnets),
        SCHEMA_DEFINITIONS.getSyncnetsENRFieldSchema().getDefault(),
        Optional.empty(),
        Optional.empty());
  }

  private static class InboundStubPeer extends StubPeer {
    public InboundStubPeer(final MockNodeId nodeId) {
      super(nodeId);
    }

    @Override
    public boolean connectionInitiatedLocally() {
      return false;
    }

    @Override
    public boolean connectionInitiatedRemotely() {
      return true;
    }
  }
}
