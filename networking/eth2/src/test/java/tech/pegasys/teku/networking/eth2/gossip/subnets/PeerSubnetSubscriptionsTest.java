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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.networking.eth2.SubnetSubscriptionService;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;

class PeerSubnetSubscriptionsTest {
  private static final String BEACON_BLOCK_SUBNET_TOPIC = "beacon_block";
  private static final String ATTESTATION_SUBNET_TOPIC_PREFIX = "attestation_";
  private static final String SYNC_COMMITTEE_SUBNET_TOPIC_PREFIX = "sync_committee_";
  private static final String DATA_COLUMN_SIDECAR_SUBNET_TOPIC_PREFIX = "data_column_sidecar_";

  private static final NodeId PEER1 = new MockNodeId(1);
  private static final NodeId PEER2 = new MockNodeId(2);
  private static final NodeId PEER3 = new MockNodeId(3);
  private static final int TARGET_SUBSCRIBER_COUNT = 2;

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final SettableLabelledGauge subnetPeerCountGauge = mock(SettableLabelledGauge.class);
  private final SyncCommitteeSubnetPeerCountLogger syncCommitteeSubnetPeerCountLogger =
      mock(SyncCommitteeSubnetPeerCountLogger.class);
  final Supplier<SpecVersion> currentSpecVersionSupplier = spec::getGenesisSpec;
  private final SchemaDefinitionsSupplier currentSchemaDefinitions =
      spec::getGenesisSchemaDefinitions;

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Peer> gossipNetwork = mock(P2PNetwork.class);

  private final PeerLookup peerLookup = mock(PeerLookup.class);

  private final AttestationSubnetTopicProvider attestationTopicProvider =
      mock(AttestationSubnetTopicProvider.class);
  private final SyncCommitteeSubnetTopicProvider syncCommitteeTopicProvider =
      mock(SyncCommitteeSubnetTopicProvider.class);
  private final DataColumnSidecarSubnetTopicProvider dataColumnSidecarSubnetTopicProvider =
      mock(DataColumnSidecarSubnetTopicProvider.class);
  private final SubnetSubscriptionService syncnetSubscriptions = new SubnetSubscriptionService();
  private final SubnetSubscriptionService dataColumnSubscriptions = new SubnetSubscriptionService();

  @BeforeEach
  public void setUp() {
    when(attestationTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> ATTESTATION_SUBNET_TOPIC_PREFIX + invocation.getArgument(0));
    when(syncCommitteeTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(invocation -> SYNC_COMMITTEE_SUBNET_TOPIC_PREFIX + invocation.getArgument(0));
    when(dataColumnSidecarSubnetTopicProvider.getTopicForSubnet(anyInt()))
        .thenAnswer(
            invocation -> DATA_COLUMN_SIDECAR_SUBNET_TOPIC_PREFIX + invocation.getArgument(0));
    when(gossipNetwork.streamPeers()).thenAnswer(invocation -> Stream.empty());
  }

  @Test
  public void create_shouldSetUpExpectedSubscriptions() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    final Map<String, Collection<NodeId>> subscribersByTopic =
        ImmutableMap.<String, Collection<NodeId>>builder()
            .put(ATTESTATION_SUBNET_TOPIC_PREFIX + "0", Set.of(PEER1, PEER2, PEER3))
            .put(ATTESTATION_SUBNET_TOPIC_PREFIX + "1", Set.of(PEER1))
            .put(ATTESTATION_SUBNET_TOPIC_PREFIX + "2", Set.of(PEER1, PEER3))
            .put(SYNC_COMMITTEE_SUBNET_TOPIC_PREFIX + "1", Set.of(PEER2))
            .put(BEACON_BLOCK_SUBNET_TOPIC, Set.of(PEER1, PEER2, PEER3))
            .build();
    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(0)).isEqualTo(3);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(2)).isEqualTo(2);

    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(0)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(1);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(2)).isEqualTo(0);

    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER1))
        .isEqualTo(createAttnetsBitvector(0, 1, 2));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER2))
        .isEqualTo(createAttnetsBitvector(0));
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER3))
        .isEqualTo(createAttnetsBitvector(0, 2));

    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER1))
        .isEqualTo(createSyncnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER2))
        .isEqualTo(createSyncnetsBitvector(1));
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER3))
        .isEqualTo(createSyncnetsBitvector());
  }

  @Test
  public void create_withNoSubscriptions() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscriberCountForAttestationSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getSubscriberCountForSyncCommitteeSubnet(1)).isEqualTo(0);
    assertThat(subscriptions.getAttestationSubnetSubscriptions(PEER1))
        .isEqualTo(createAttnetsBitvector());
    assertThat(subscriptions.getSyncCommitteeSubscriptions(PEER1))
        .isEqualTo(createSyncnetsBitvector());
  }

  @Test
  public void create_shouldExcludePeersWithoutDiscoveryNodeIdFromDataColumnSubnets() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(0));

    final NodeId peerWithDiscoveryNodeId = new MockNodeId(1);
    final NodeId peerWithoutDiscoveryNodeId = new MockNodeId(2);
    // streamPeers() yields raw libp2p peers (as in production), not Eth2Peers; discovery node id
    // presence is resolved via the Eth2 peer lookup.
    when(gossipNetwork.streamPeers())
        .thenAnswer(
            __ ->
                Stream.of(mockPeer(peerWithDiscoveryNodeId), mockPeer(peerWithoutDiscoveryNodeId)));
    final Eth2Peer eth2PeerWithNodeId = mockEth2Peer(Optional.of(UInt256.ONE));
    final Eth2Peer eth2PeerWithoutNodeId = mockEth2Peer(Optional.empty());
    when(peerLookup.getConnectedPeer(peerWithDiscoveryNodeId))
        .thenReturn(Optional.of(eth2PeerWithNodeId));
    when(peerLookup.getConnectedPeer(peerWithoutDiscoveryNodeId))
        .thenReturn(Optional.of(eth2PeerWithoutNodeId));

    when(gossipNetwork.getSubscribersByTopic())
        .thenReturn(
            Map.of(
                DATA_COLUMN_SIDECAR_SUBNET_TOPIC_PREFIX + "0",
                Set.of(peerWithDiscoveryNodeId, peerWithoutDiscoveryNodeId)));

    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    // The peer without a derivable discovery node id is not credited as a data column subscriber.
    assertThat(subscriptions.getSubscriberCountForDataColumnSidecarSubnet(0)).isEqualTo(1);
    assertThat(
            subscriptions
                .getDataColumnSidecarSubnetSubscriptions(peerWithDiscoveryNodeId)
                .getBitCount())
        .isEqualTo(1);
    assertThat(
            subscriptions
                .getDataColumnSidecarSubnetSubscriptions(peerWithoutDiscoveryNodeId)
                .getBitCount())
        .isZero();
  }

  private Peer mockPeer(final NodeId nodeId) {
    final Peer peer = mock(Peer.class);
    when(peer.getId()).thenReturn(nodeId);
    return peer;
  }

  private Eth2Peer mockEth2Peer(final Optional<UInt256> discoveryNodeId) {
    final Eth2Peer peer = mock(Eth2Peer.class);
    when(peer.getDiscoveryNodeId()).thenReturn(discoveryNodeId);
    return peer;
  }

  @Test
  public void getSubscribersRequired_withNoSubscriptions() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscribersRequired()).isEqualTo(TARGET_SUBSCRIBER_COUNT);
  }

  @Test
  public void getSubscribersRequired_allSubnetsAreJustBelowTarget() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(1);
    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT - 1);
  }

  @Test
  public void getSubscribersRequired_allSubnetsHaveExactlyEnoughSubscribers() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT);
    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isZero();
  }

  @Test
  public void getSubscribersRequired_allSubnetsOverlyFull() {
    // Set up some sync committee subnets we should be participating in
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT + 1);
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.getSubscribersRequired()).isEqualTo(0);
  }

  @Test
  public void getSubscribersRequired_onlySyncnetsAreBelowTarget() {
    // Set up attnets to be full, syncnets to be empty
    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT);
    syncnetSubscriptions.setSubscriptions(IntList.of(0, 1));

    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(TARGET_SUBSCRIBER_COUNT);
  }

  @Test
  public void getSubscribersRequired_attnetsAreFull_noRequiredSyncnets() {
    withSubscriberCountForAllSubnets(TARGET_SUBSCRIBER_COUNT + 1);

    final int requiredPeers = createPeerSubnetSubscriptions().getSubscribersRequired();
    assertThat(requiredPeers).isEqualTo(0);
  }

  @Test
  public void isSyncCommitteeSubnetRelevant() {
    syncnetSubscriptions.setSubscriptions(IntList.of(1, 3));
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(0)).isFalse();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(1)).isTrue();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(2)).isFalse();
    assertThat(subscriptions.isSyncCommitteeSubnetRelevant(3)).isTrue();
  }

  @Test
  public void isAttestationSubnetRelevant() {
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    final int attSubnetsCount = currentSchemaDefinitions.getAttnetsENRFieldSchema().getLength();
    for (int i = 0; i < attSubnetsCount; i++) {
      assertThat(subscriptions.isAttestationSubnetRelevant(i)).isTrue();
    }

    assertThat(subscriptions.isAttestationSubnetRelevant(attSubnetsCount)).isFalse();
    assertThat(subscriptions.isAttestationSubnetRelevant(attSubnetsCount + 1)).isFalse();
  }

  @Test
  public void isDataColumnSidecarSubnetRelevant() {
    dataColumnSubscriptions.setSubscriptions(IntList.of(1, 2, 3));
    final PeerSubnetSubscriptions subscriptions = createPeerSubnetSubscriptions();

    assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(0)).isFalse();
    assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(1)).isTrue();
    assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(2)).isTrue();
    assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(3)).isTrue();
    assertThat(subscriptions.isDataColumnSidecarSubnetRelevant(4)).isFalse();
  }

  @Test
  public void shouldCreatePeerCountPerSubnetMetrics() {
    createPeerSubnetSubscriptions();

    verify(
            subnetPeerCountGauge,
            times(currentSchemaDefinitions.getAttnetsENRFieldSchema().getLength()))
        .set(anyDouble(), matches(ATTESTATION_SUBNET_TOPIC_PREFIX));
    verify(
            subnetPeerCountGauge,
            times(currentSchemaDefinitions.getSyncnetsENRFieldSchema().getLength()))
        .set(anyDouble(), matches(SYNC_COMMITTEE_SUBNET_TOPIC_PREFIX));
    verify(
            subnetPeerCountGauge,
            times(
                SpecConfigFulu.required(spec.getGenesisSpecConfig())
                    .getDataColumnSidecarSubnetCount()))
        .set(anyDouble(), matches(DATA_COLUMN_SIDECAR_SUBNET_TOPIC_PREFIX));
  }

  private PeerSubnetSubscriptions createPeerSubnetSubscriptions() {
    return PeerSubnetSubscriptions.create(
        currentSpecVersionSupplier.get(),
        NodeIdToDataColumnSidecarSubnetsCalculator.NOOP,
        gossipNetwork,
        peerLookup,
        attestationTopicProvider,
        syncCommitteeTopicProvider,
        syncnetSubscriptions,
        dataColumnSidecarSubnetTopicProvider,
        dataColumnSubscriptions,
        TARGET_SUBSCRIBER_COUNT,
        subnetPeerCountGauge,
        syncCommitteeSubnetPeerCountLogger);
  }

  private void withSubscriberCountForAllSubnets(final int subscriberCount) {
    // Set up subscribers
    final List<NodeId> subscribers = new ArrayList<>();
    IntStream.range(0, subscriberCount).mapToObj(MockNodeId::new).forEach(subscribers::add);
    // Set up attestation topic subscriptions
    final Map<String, Collection<NodeId>> subscribersByTopic = new HashMap<>();
    IntStream.range(0, spec.getNetworkingConfig().getAttestationSubnetCount())
        .forEach(
            subnetId ->
                subscribersByTopic.put(
                    attestationTopicProvider.getTopicForSubnet(subnetId), subscribers));
    // Set up sync committee topic subscriptions
    syncnetSubscriptions
        .getSubnets()
        .forEach(
            subnetId ->
                subscribersByTopic.put(
                    syncCommitteeTopicProvider.getTopicForSubnet(subnetId), subscribers));

    when(gossipNetwork.getSubscribersByTopic()).thenReturn(subscribersByTopic);
  }

  private SszBitvector createAttnetsBitvector(final int... subnets) {
    return currentSchemaDefinitions.getAttnetsENRFieldSchema().ofBits(subnets);
  }

  private SszBitvector createSyncnetsBitvector(final int... subnets) {
    return currentSchemaDefinitions.getSyncnetsENRFieldSchema().ofBits(subnets);
  }
}
