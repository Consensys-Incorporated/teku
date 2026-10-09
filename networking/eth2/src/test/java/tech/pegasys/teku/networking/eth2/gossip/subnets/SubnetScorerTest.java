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
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service.DEFAULT_NODE_RECORD_CONVERTER;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.eth2.peers.PeerScorer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

class SubnetScorerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
  private static final int DATA_COLUMN_SIDECAR_SUBNET_COUNT = 128;

  @Test
  void shouldScoreCandidatePeerWithNoSubnetsAsZero() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));
    assertThat(
            scorer.scoreCandidatePeer(
                createDiscoveryPeer(
                    schemaDefinitions.getAttnetsENRFieldSchema().getDefault(),
                    schemaDefinitions.getSyncnetsENRFieldSchema().getDefault())))
        .isZero();
  }

  @Test
  void shouldScoreExistingPeerWithNoSubnetsAsZero() {
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.createEmpty(
                () -> schemaDefinitions,
                SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT)));
    assertThat(scorer.scoreExistingPeer(new MockNodeId(1))).isZero();
  }

  @Test
  void shouldScoreExistingPeersOnSubnetsWithFewPeersMoreHighly() {
    final MockNodeId node1 = new MockNodeId(0);
    final MockNodeId node2 = new MockNodeId(1);
    final MockNodeId node3 = new MockNodeId(2);
    final MockNodeId node4 = new MockNodeId(3);
    final MockNodeId node5 = new MockNodeId(4);
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(1)
                            .addRelevantSubnet(2)
                            .addRelevantSubnet(3)
                            .addRelevantSubnet(4)

                            // Subnet 1
                            .addSubscriber(1, node1)
                            .addSubscriber(1, node2)
                            .addSubscriber(1, node3)
                            .addSubscriber(1, node4)

                            // Subnet 2
                            .addSubscriber(2, node1)
                            .addSubscriber(2, node2)

                            // Subnet 3
                            .addSubscriber(3, node3)

                            // Irrelevant subnet
                            .addSubscriber(5, node2))
                .syncCommitteeSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(3)
                            // Subnet 4
                            .addSubscriber(3, node1)
                            .addSubscriber(3, node4)
                            // Irrelevant subnet
                            .addSubscriber(2, node3))
                .build());

    assertExistingPeerScores(
        scorer,
        entry(node1, 562),
        entry(node2, 312),
        entry(node3, 1062),
        entry(node4, 312),
        entry(node5, 0));
  }

  @Test
  void shouldScoreCandidatePeersOnSubnetsWithFewPeersMoreHighly() {
    final MockNodeId node1 = new MockNodeId(0);
    final MockNodeId node2 = new MockNodeId(1);
    final MockNodeId node3 = new MockNodeId(2);
    final SubnetScorer scorer =
        SubnetScorer.create(
            PeerSubnetSubscriptions.builder(
                    () -> schemaDefinitions,
                    SszBitvectorSchema.create(DATA_COLUMN_SIDECAR_SUBNET_COUNT))
                .attestationSubnetSubscriptions(
                    b ->
                        b.addRelevantSubnet(1)
                            .addRelevantSubnet(2)
                            .addRelevantSubnet(3)
                            .addRelevantSubnet(5)

                            // Subnet 1
                            .addSubscriber(1, node2)

                            // No subscribers for subnet 2

                            // Subnet 3
                            .addSubscriber(3, node3))
                .syncCommitteeSubnetSubscriptions(
                    b ->
                        b
                            // Tracked subnets
                            .addRelevantSubnet(1)
                            .addRelevantSubnet(2)

                            // Subnet 1
                            .addSubscriber(1, node1)
                            .addSubscriber(1, node2)
                            .addSubscriber(1, node3))
                .nodeIdToDataColumnSidecarSubnetsCalculator(
                    NodeIdToDataColumnSidecarSubnetsCalculator.NOOP)
                .build());

    assertCandidatePeerScores(
        scorer,
        entry(candidateWithSubnets(IntList.of(1, 3), IntList.of(1)), 562),
        entry(candidateWithSubnets(IntList.of(1), IntList.of(1)), 312),
        entry(candidateWithSubnets(IntList.of(2), IntList.of(1)), 1062),
        entry(candidateWithSubnets(IntList.of(3), IntList.of(1)), 312),
        entry(candidateWithSubnets(IntLists.emptyList(), IntLists.emptyList()), 0),
        entry(candidateWithSubnets(IntList.of(5), IntLists.emptyList()), 1000),
        entry(candidateWithSubnets(IntList.of(4), IntLists.emptyList()), 0),
        entry(candidateWithSubnets(IntLists.emptyList(), IntList.of(2)), 1000),
        entry(candidateWithSubnets(IntLists.emptyList(), IntList.of(3)), 0));
  }

  @SafeVarargs
  private void assertExistingPeerScores(
      final PeerScorer scorer, final Map.Entry<NodeId, Integer>... expected) {
    final Map<NodeId, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(Collectors.toMap(Function.identity(), scorer::scoreExistingPeer));
    assertThat(actual).contains(expected);
  }

  @SafeVarargs
  private void assertCandidatePeerScores(
      final PeerScorer scorer,
      final Map.Entry<Pair<SszBitvector, SszBitvector>, Integer>... expected) {
    final Map<Pair<SszBitvector, SszBitvector>, Integer> actual =
        Stream.of(expected)
            .map(Map.Entry::getKey)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    (subscriptions) ->
                        scorer.scoreCandidatePeer(
                            createDiscoveryPeer(
                                subscriptions.getLeft(), subscriptions.getRight()))));
    assertThat(actual).contains(expected);
  }

  private DiscoveryPeer createDiscoveryPeer(
      final SszBitvector attSubnets, final SszBitvector syncSubnets) {
    try {
      Bytes pubKey =
          Bytes.fromHexString(
              "0x03B86ED9F747A7FA99963F39E3B176B45E9E863108A2D145EA3A4E76D8D0935194");
      return new DiscoveryPeer(
          pubKey,
          DEFAULT_NODE_RECORD_CONVERTER.convertPublicKeyToNodeId(pubKey),
          Optional.of(
              new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9000)),
          Optional.empty(),
          Optional.empty(),
          attSubnets,
          syncSubnets,
          Optional.empty(),
          Optional.empty());
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  private Pair<SszBitvector, SszBitvector> candidateWithSubnets(
      final List<Integer> attnets, final List<Integer> syncnets) {
    return Pair.of(
        schemaDefinitions.getAttnetsENRFieldSchema().ofBits(attnets),
        schemaDefinitions.getSyncnetsENRFieldSchema().ofBits(syncnets));
  }
}
