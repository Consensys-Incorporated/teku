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

package tech.pegasys.teku.networking.eth2.gossip.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class GossipScoringConfiguratorTest {

  private static final double TOLERANCE = 0.00005;
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final Bytes4 forkDigest = Bytes4.fromHexString("0x01020304");
  private final Spec spec = TestSpecFactory.createMainnetPhase0();
  private final int genesisMinActiveValidators =
      spec.getGenesisSpecConfig().getMinGenesisActiveValidatorCount();
  private final int attestationSubnetCount = spec.getNetworkingConfig().getAttestationSubnetCount();
  private GossipScoringConfigurator configurator;

  @BeforeEach
  public void setup() {
    configurator = new GossipScoringConfigurator(spec);
  }

  @Test
  public void configure_preGenesis() {
    final GossipConfig.Builder builder = GossipConfig.builder();
    configurator.configure(builder, preGenesisEth2Context());
    final GossipConfig config = builder.build();

    final GossipScoringConfig scoringConfig = config.getScoringConfig();
    assertThat(scoringConfig.getGossipThreshold()).isEqualTo(-4000);
    assertThat(scoringConfig.getPublishThreshold()).isEqualTo(-8000);
    assertThat(scoringConfig.getGraylistThreshold()).isEqualTo(-16000);
    assertThat(scoringConfig.getAcceptPXThreshold()).isEqualTo(100);
    assertThat(scoringConfig.getOpportunisticGraftThreshold()).isEqualTo(5);

    final GossipPeerScoringConfig peerScoring = scoringConfig.getPeerScoringConfig();

    assertThat(peerScoring.getTopicScoreCap()).isCloseTo(53.75, within(TOLERANCE));
    assertThat(peerScoring.getIpColocationFactorWeight()).isCloseTo(-53.75, within(TOLERANCE));
    assertThat(peerScoring.getIpColocationFactorThreshold()).isEqualTo(3);
    assertThat(peerScoring.getBehaviourPenaltyDecay()).isCloseTo(0.9857, within(TOLERANCE));
    assertThat(peerScoring.getBehaviourPenaltyWeight()).isCloseTo(-15.8793, within(TOLERANCE));
    assertThat(peerScoring.getBehaviourPenaltyThreshold()).isEqualTo(6.0);
    assertThat(peerScoring.getDecayToZero()).isEqualTo(0.01);
    assertThat(peerScoring.getDecayInterval().toSeconds()).isEqualTo(12);
    assertThat(peerScoring.getRetainScore().toSeconds()).isEqualTo(38400);

    // Check topics
    final Map<String, GossipTopicScoringConfig> topicsConfig =
        scoringConfig.getTopicScoringConfig();
    assertThat(topicsConfig).isEmpty();
  }

  @Test
  public void configureAllTopics_forMatureChain() {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configurator.configureAllTopics(builder, postGenesisEth2Context());
    final Map<String, GossipTopicScoringConfig> allTopics = builder.build().getTopicConfigs();

    final int expectedCount = 6 + attestationSubnetCount;
    assertThat(allTopics.size()).isEqualTo(expectedCount);

    validateVoluntaryExitTopicParams(allTopics);
    validateExecutionPayloadTopicParams(allTopics);
    validateSlashingTopicParams(allTopics);
    validateAggregateTopicParams(allTopics, true);
    validateBlockTopicParams(allTopics, true);
    validateAllAttestationSubnetTopicParams(allTopics, true);
  }

  @Test
  public void configureAllTopics_atGenesis() {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configurator.configureAllTopics(builder, genesisEth2Context());
    final Map<String, GossipTopicScoringConfig> allTopics = builder.build().getTopicConfigs();

    final int expectedCount = 6 + attestationSubnetCount;
    assertThat(allTopics.size()).isEqualTo(expectedCount);

    validateVoluntaryExitTopicParams(allTopics);
    validateExecutionPayloadTopicParams(allTopics);
    validateSlashingTopicParams(allTopics);
    validateAggregateTopicParams(allTopics, false);
    validateBlockTopicParams(allTopics, false);
    validateAllAttestationSubnetTopicParams(allTopics, false);
  }

  private void validateVoluntaryExitTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final GossipTopicScoringConfig params = getVoluntaryExitTopicScoring(allTopics);
    assertMessageRatePenaltiesDisabled(params);

    assertThat(params.getTopicWeight()).isEqualTo(0.05);
    assertThat(params.getTimeInMeshWeight()).isCloseTo(0.03333, within(TOLERANCE));
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThat(params.getFirstMessageDeliveriesWeight()).isCloseTo(1.8407, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesDecay()).isCloseTo(0.99856, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesCap()).isCloseTo(21.73035, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesWeight()).isCloseTo(-2150.0, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesDecay()).isCloseTo(0.99713, within(TOLERANCE));
  }

  private void validateExecutionPayloadTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final GossipTopicScoringConfig params =
        allTopics.get(
            GossipTopics.getTopic(forkDigest, GossipTopicName.EXECUTION_PAYLOAD, gossipEncoding));
    assertMessageRatePenaltiesDisabled(params);

    assertThat(params.getTopicWeight()).isEqualTo(0.05);
    assertThat(params.getInvalidMessageDeliveriesWeight()).isCloseTo(-2150.0, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesDecay()).isCloseTo(0.99713, within(TOLERANCE));
  }

  private void validateSlashingTopicParams(final Map<String, GossipTopicScoringConfig> allTopics) {
    final GossipTopicScoringConfig attesterSlashingParams =
        getAttesterSlashingTopicScoring(allTopics);
    final GossipTopicScoringConfig proposerSlashingParams =
        getProposerSlashingTopicScoring(allTopics);

    validateSlashingTopicParams(attesterSlashingParams);
    validateSlashingTopicParams(proposerSlashingParams);
  }

  private void validateSlashingTopicParams(final GossipTopicScoringConfig params) {
    assertMessageRatePenaltiesDisabled(params);

    assertThat(params.getTopicWeight()).isEqualTo(0.05);
    assertThat(params.getTimeInMeshWeight()).isCloseTo(0.03333, within(TOLERANCE));
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThat(params.getFirstMessageDeliveriesWeight()).isCloseTo(36.81486, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesDecay()).isCloseTo(0.998561, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesCap()).isCloseTo(1.08652, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesWeight()).isCloseTo(-2150.0, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesDecay()).isCloseTo(0.99713, within(TOLERANCE));
  }

  private void validateAggregateTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    final GossipTopicScoringConfig params = getAggregateTopicScoring(allTopics);

    assertThat(params.getTopicWeight()).isEqualTo(0.5);
    assertThat(params.getTimeInMeshWeight()).isCloseTo(0.03333, within(TOLERANCE));
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThat(params.getFirstMessageDeliveriesWeight()).isCloseTo(0.33509, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesDecay()).isCloseTo(0.86596, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesCap()).isCloseTo(119.3712, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesWeight()).isCloseTo(-215.0, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesDecay()).isCloseTo(0.99713, within(TOLERANCE));

    // Check message rate penalty params
    assertThat(params.getMeshMessageDeliveriesDecay()).isCloseTo(0.930572, within(TOLERANCE));
    assertThat(params.getMeshMessageDeliveriesCap()).isCloseTo(68.6255, within(TOLERANCE));
    assertThat(params.getMeshMessageDeliveriesActivation()).isEqualTo(Duration.ofSeconds(384));
    assertThat(params.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThat(params.getMeshFailurePenaltyWeight()).isCloseTo(-0.73044, within(TOLERANCE));
    assertThat(params.getMeshFailurePenaltyDecay()).isCloseTo(0.93057, within(TOLERANCE));

    if (penaltiesActive) {
      assertThat(params.getMeshMessageDeliveriesWeight()).isCloseTo(-0.7304, within(TOLERANCE));
      assertThat(params.getMeshMessageDeliveriesThreshold()).isCloseTo(17.15638, within(TOLERANCE));
    } else {
      assertThat(params.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(params.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void validateAllAttestationSubnetTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    IntStream.range(0, attestationSubnetCount)
        .mapToObj(subnet -> getAttestationSubnetTopicScoring(allTopics, subnet))
        .forEach(params -> validateAttestationSubnetTopicParams(params, penaltiesActive));
  }

  private void validateAttestationSubnetTopicParams(
      final GossipTopicScoringConfig params, final boolean penaltiesActive) {
    assertThat(params.getTopicWeight()).isEqualTo(0.015625);
    assertThat(params.getTimeInMeshWeight()).isCloseTo(0.03333, within(TOLERANCE));
    assertThat(params.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(params.getTimeInMeshCap()).isEqualTo(300.0);
    assertThat(params.getFirstMessageDeliveriesWeight()).isCloseTo(2.6807, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesDecay()).isCloseTo(0.86596, within(TOLERANCE));
    assertThat(params.getFirstMessageDeliveriesCap()).isCloseTo(14.9214, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesWeight()).isCloseTo(-6880.0, within(TOLERANCE));
    assertThat(params.getInvalidMessageDeliveriesDecay()).isCloseTo(0.99713, within(TOLERANCE));

    // Check message rate penalty params
    assertThat(params.getMeshMessageDeliveriesDecay()).isCloseTo(0.96466, within(TOLERANCE));
    assertThat(params.getMeshMessageDeliveriesCap()).isCloseTo(69.88248, within(TOLERANCE));
    assertThat(params.getMeshMessageDeliveriesActivation()).isEqualTo(Duration.ofSeconds(204));
    assertThat(params.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThat(params.getMeshFailurePenaltyWeight()).isCloseTo(-360.6548, within(TOLERANCE));
    assertThat(params.getMeshFailurePenaltyDecay()).isCloseTo(0.96466, within(TOLERANCE));

    if (penaltiesActive) {
      assertThat(params.getMeshMessageDeliveriesWeight()).isCloseTo(-360.6548, within(TOLERANCE));
      assertThat(params.getMeshMessageDeliveriesThreshold()).isCloseTo(4.367655, within(TOLERANCE));
    } else {
      assertThat(params.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(params.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void validateBlockTopicParams(
      final Map<String, GossipTopicScoringConfig> allTopics, final boolean penaltiesActive) {
    final GossipTopicScoringConfig blockTopicScoring = getBlockTopicScoring(allTopics);

    assertThat(blockTopicScoring.getTopicWeight()).isEqualTo(0.5);
    assertThat(blockTopicScoring.getTimeInMeshWeight()).isCloseTo(0.03333, within(TOLERANCE));
    assertThat(blockTopicScoring.getTimeInMeshQuantum()).isEqualTo(Duration.ofSeconds(12));
    assertThat(blockTopicScoring.getTimeInMeshCap()).isEqualTo(300.0);
    assertThat(blockTopicScoring.getFirstMessageDeliveriesWeight())
        .isCloseTo(1.14716, within(TOLERANCE));
    assertThat(blockTopicScoring.getFirstMessageDeliveriesDecay())
        .isCloseTo(0.99283, within(TOLERANCE));
    assertThat(blockTopicScoring.getFirstMessageDeliveriesCap())
        .isCloseTo(34.8687, within(TOLERANCE));
    assertThat(blockTopicScoring.getInvalidMessageDeliveriesWeight())
        .isCloseTo(-215.0, within(TOLERANCE));
    assertThat(blockTopicScoring.getInvalidMessageDeliveriesDecay())
        .isCloseTo(0.99713, within(TOLERANCE));

    // Check message rate penalty params
    assertThat(blockTopicScoring.getMeshMessageDeliveriesDecay())
        .isCloseTo(0.97163, within(TOLERANCE));
    assertThat(blockTopicScoring.getMeshMessageDeliveriesCap())
        .isCloseTo(2.0547574, within(TOLERANCE));
    assertThat(blockTopicScoring.getMeshMessageDeliveriesActivation())
        .isEqualTo(Duration.ofSeconds(384));
    assertThat(blockTopicScoring.getMeshMessageDeliveryWindow()).isEqualTo(Duration.ofSeconds(2));
    assertThat(blockTopicScoring.getMeshFailurePenaltyWeight())
        .isCloseTo(-458.31055, within(TOLERANCE));
    assertThat(blockTopicScoring.getMeshFailurePenaltyDecay())
        .isCloseTo(0.97163, within(TOLERANCE));

    if (penaltiesActive) {
      assertThat(blockTopicScoring.getMeshMessageDeliveriesWeight())
          .isCloseTo(-458.31055, within(TOLERANCE));
      assertThat(blockTopicScoring.getMeshMessageDeliveriesThreshold())
          .isCloseTo(0.68491, within(TOLERANCE));
    } else {
      assertThat(blockTopicScoring.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
      assertThat(blockTopicScoring.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    }
  }

  private void assertMessageRatePenaltiesDisabled(final GossipTopicScoringConfig scoringConfig) {
    assertThat(scoringConfig.getMeshMessageDeliveriesWeight()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesDecay()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesThreshold()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesCap()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshFailurePenaltyWeight()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshFailurePenaltyDecay()).isEqualTo(0.0);
    assertThat(scoringConfig.getMeshMessageDeliveriesActivation().toSeconds()).isEqualTo(0);
    assertThat(scoringConfig.getMeshMessageDeliveryWindow().toSeconds()).isEqualTo(0);
  }

  private GossipTopicScoringConfig getBlockTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        GossipTopics.getTopic(forkDigest, GossipTopicName.BEACON_BLOCK, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getVoluntaryExitTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        GossipTopics.getTopic(forkDigest, GossipTopicName.VOLUNTARY_EXIT, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getProposerSlashingTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        GossipTopics.getTopic(forkDigest, GossipTopicName.PROPOSER_SLASHING, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAttesterSlashingTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        GossipTopics.getTopic(forkDigest, GossipTopicName.ATTESTER_SLASHING, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAggregateTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics) {
    final String topic =
        GossipTopics.getTopic(
            forkDigest, GossipTopicName.BEACON_AGGREGATE_AND_PROOF, gossipEncoding);
    return allTopics.get(topic);
  }

  private GossipTopicScoringConfig getAttestationSubnetTopicScoring(
      final Map<String, GossipTopicScoringConfig> allTopics, final int subnet) {
    final String subnetName = GossipTopicName.getAttestationSubnetTopicName(subnet);
    final String topic = GossipTopics.getTopic(forkDigest, subnetName, gossipEncoding);
    return allTopics.get(topic);
  }

  private Eth2Context preGenesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(GossipEncoding.SSZ_SNAPPY)
        .build();
  }

  private Eth2Context postGenesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(gossipEncoding)
        .forkDigest(forkDigest)
        .currentSlot(UInt64.valueOf(10_000))
        .build();
  }

  private Eth2Context genesisEth2Context() {
    return Eth2Context.builder()
        .activeValidatorCount(genesisMinActiveValidators)
        .gossipEncoding(gossipEncoding)
        .forkDigest(forkDigest)
        .currentSlot(UInt64.ZERO)
        .build();
  }
}
