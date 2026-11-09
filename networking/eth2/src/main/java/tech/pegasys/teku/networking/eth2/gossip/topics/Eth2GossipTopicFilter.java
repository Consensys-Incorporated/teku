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

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics.getAllTopics;

import com.google.common.base.Suppliers;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.storage.client.RecentChainData;

public class Eth2GossipTopicFilter implements GossipTopicFilter {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final P2PConfig p2pConfig;
  private final Supplier<Set<String>> relevantTopics;

  public Eth2GossipTopicFilter(
      final RecentChainData recentChainData,
      final GossipEncoding gossipEncoding,
      final Spec spec,
      final P2PConfig p2pConfig) {
    this.spec = spec;
    this.p2pConfig = p2pConfig;
    relevantTopics =
        Suppliers.memoize(() -> computeRelevantTopics(recentChainData, gossipEncoding));
  }

  @Override
  public boolean isRelevantTopic(final String topic) {
    final boolean allowed = relevantTopics.get().contains(topic);
    if (!allowed) {
      LOG.debug("Ignoring subscription request for topic {}", topic);
    }
    return allowed;
  }

  private Set<String> computeRelevantTopics(
      final RecentChainData recentChainData, final GossipEncoding gossipEncoding) {
    final Bytes4 forkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    final SpecMilestone milestone =
        recentChainData.getMilestoneByForkDigest(forkDigest).orElseThrow();
    final Set<String> topics = getAllTopics(gossipEncoding, forkDigest, spec, milestone, p2pConfig);
    final UInt64 forkEpoch =
        recentChainData
            .getBpoForkByForkDigest(forkDigest)
            .map(BlobParameters::epoch)
            .orElseGet(() -> spec.getForkSchedule().getFork(milestone).getEpoch());
    spec.getForkSchedule().getForks().stream()
        .filter(fork -> fork.getEpoch().isGreaterThan(forkEpoch))
        .forEach(
            futureFork -> {
              final SpecMilestone futureMilestone =
                  spec.getForkSchedule().getSpecMilestoneAtEpoch(futureFork.getEpoch());
              final Bytes4 futureForkDigest =
                  recentChainData.getForkDigestByMilestone(futureMilestone).orElseThrow();
              topics.addAll(
                  getAllTopics(gossipEncoding, futureForkDigest, spec, futureMilestone, p2pConfig));
            });
    // BPO
    spec.getBpoForks().stream()
        .filter(bpoFork -> bpoFork.epoch().isGreaterThan(forkEpoch))
        .forEach(
            futureBpoFork -> {
              final SpecMilestone futureMilestone =
                  spec.getForkSchedule().getSpecMilestoneAtEpoch(futureBpoFork.epoch());
              final Bytes4 futureForkDigest =
                  recentChainData.getForkDigestByBpoFork(futureBpoFork).orElseThrow();
              topics.addAll(
                  getAllTopics(gossipEncoding, futureForkDigest, spec, futureMilestone, p2pConfig));
            });
    return topics;
  }
}
