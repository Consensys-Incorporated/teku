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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipTopicsTest {
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @Test
  public void getTopic() {
    final Bytes4 forkDigest = Bytes4.fromHexString("0x01020304");
    final String topicName = "test";

    final String actual = GossipTopics.getTopic(forkDigest, topicName, gossipEncoding);
    assertThat(actual).isEqualTo("/eth2/01020304/test/ssz_snappy");
  }

  @Test
  public void extractForkDigest_valid() {
    final Bytes4 result = GossipTopics.extractForkDigest("/eth2/01020304/test/ssz_snappy");
    assertThat(result).isEqualTo(Bytes4.fromHexString("01020304"));
  }

  @Test
  public void extractForkDigest_invalid() {
    final String topic = "/eth2/wrong/test/ssz_snappy";
    assertThatThrownBy(() -> GossipTopics.extractForkDigest(topic))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void subscriptionLimitsAreLargeEnoughForAllTopics() {
    final SpecMilestone latestMilestone = SpecMilestone.getHighestMilestone();
    final Spec spec = TestSpecFactory.createMainnet(latestMilestone);
    // every optional topic enabled, so the count is the real worst case
    final P2PConfig p2pConfig =
        P2PConfig.builder().specProvider(spec).executionProofTopicEnabled(true).build();

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(32, spec);
    storageSystem.chainUpdater().initializeGenesis();
    final Bytes32 genesisValidatorsRoot =
        storageSystem.recentChainData().getGenesisData().orElseThrow().getGenesisValidatorsRoot();

    // all topics for highest fork, (fork-1) and (fork-2)
    final List<Integer> topicsPerFork =
        SpecMilestone.getMilestonesUpTo(latestMilestone).stream()
            .sorted(Comparator.reverseOrder())
            .limit(3)
            .map(
                milestone -> {
                  final Fork fork = spec.getForkSchedule().getFork(milestone);
                  final Bytes4 forkDigest =
                      spec.forMilestone(milestone)
                          .miscHelpers()
                          .computeForkDigest(fork.getCurrentVersion(), genesisValidatorsRoot);
                  return GossipTopics.getAllTopics(
                          gossipEncoding, forkDigest, spec, milestone, p2pConfig)
                      .size();
                })
            .toList();

    assertThat(topicsPerFork.stream().mapToInt(Integer::intValue).sum())
        .isLessThanOrEqualTo(LibP2PGossipNetworkBuilder.MAX_SUBSCRIBED_TOPICS);
    // a peer announces the topics of two forks in one RPC around a fork
    assertThat(2 * Collections.max(topicsPerFork))
        .isLessThanOrEqualTo(LibP2PParamsFactory.MAX_INBOUND_SUBSCRIPTIONS_PER_RPC);
  }
}
