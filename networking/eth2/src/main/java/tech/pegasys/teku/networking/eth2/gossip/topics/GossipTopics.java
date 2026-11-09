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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.constants.NetworkConstants;

/**
 * Helpers for getting the full topic strings formatted like: /eth2/ForkDigestValue/Name/Encoding
 */
public class GossipTopics {
  private static final String DOMAIN_PREFIX = "eth2";

  public static String getTopic(
      final Bytes4 forkDigest,
      final GossipTopicName topicName,
      final GossipEncoding gossipEncoding) {
    return getTopic(forkDigest, topicName.toString(), gossipEncoding);
  }

  public static String getTopic(
      final Bytes4 forkDigest, final String topicName, final GossipEncoding gossipEncoding) {
    return "/"
        + DOMAIN_PREFIX
        + "/"
        + forkDigest.toUnprefixedHexString()
        + "/"
        + topicName
        + "/"
        + gossipEncoding.getName();
  }

  public static String getAttestationSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getAttestationSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getSyncCommitteeSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getSyncCommitteeSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getBlobSidecarSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getBlobSidecarSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getDataColumnSidecarSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getDataColumnSidecarSubnetTopicName(subnetId), gossipEncoding);
  }

  public static String getExecutionProofSubnetTopic(
      final Bytes4 forkDigest, final int subnetId, final GossipEncoding gossipEncoding) {
    return getTopic(
        forkDigest, GossipTopicName.getExecutionProofSubnetTopicName(subnetId), gossipEncoding);
  }

  public static Set<String> getAllDataColumnSidecarSubnetTopics(
      final GossipEncoding gossipEncoding, final Bytes4 forkDigest, final Spec spec) {

    return spec.getNumberOfDataColumnSubnets()
        .map(
            subnetCount ->
                IntStream.range(0, subnetCount)
                    .mapToObj(i -> getDataColumnSidecarSubnetTopic(forkDigest, i, gossipEncoding))
                    .collect(Collectors.toSet()))
        .orElse(Collections.emptySet());
  }

  public static Set<String> getAllTopics(
      final GossipEncoding gossipEncoding,
      final Bytes4 forkDigest,
      final Spec spec,
      final SpecMilestone specMilestone,
      final P2PConfig p2pConfig) {
    final Set<String> topics = new HashSet<>();

    for (int i = 0; i < spec.getNetworkingConfig().getAttestationSubnetCount(); i++) {
      topics.add(getAttestationSubnetTopic(forkDigest, i, gossipEncoding));
    }
    for (int i = 0; i < NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      topics.add(getSyncCommitteeSubnetTopic(forkDigest, i, gossipEncoding));
    }
    spec.forMilestone(specMilestone)
        .getConfig()
        .toVersionDeneb()
        .ifPresent(
            config ->
                addBlobSidecarSubnetTopics(
                    config.getBlobSidecarSubnetCount(), topics, forkDigest, gossipEncoding));

    topics.addAll(getAllDataColumnSidecarSubnetTopics(gossipEncoding, forkDigest, spec));

    if (p2pConfig.isExecutionProofTopicEnabled()) {
      for (int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS; i++) {
        topics.add(getExecutionProofSubnetTopic(forkDigest, i, gossipEncoding));
      }
    }

    for (GossipTopicName topicName : GossipTopicName.values()) {
      topics.add(GossipTopics.getTopic(forkDigest, topicName, gossipEncoding));
    }

    return topics;
  }

  /**
   * @param topic The topic string
   * @return The forkDigest embedded in the topic string
   * @throws IllegalArgumentException Throws if the topic string is not formatted as expected
   */
  public static Bytes4 extractForkDigest(final String topic) throws IllegalArgumentException {
    // Fork digest starts after domain prefix + slash separators
    final int beginIndex = DOMAIN_PREFIX.length() + 2;
    final int endIndex = topic.indexOf("/", beginIndex);
    final String forkDigest = topic.substring(beginIndex, endIndex);

    return Bytes4.fromHexString(forkDigest);
  }

  private static void addBlobSidecarSubnetTopics(
      final int blobSidecarSubnetCount,
      final Set<String> topics,
      final Bytes4 forkDigest,
      final GossipEncoding gossipEncoding) {
    for (int i = 0; i < blobSidecarSubnetCount; i++) {
      topics.add(getBlobSidecarSubnetTopic(forkDigest, i, gossipEncoding));
    }
  }
}
