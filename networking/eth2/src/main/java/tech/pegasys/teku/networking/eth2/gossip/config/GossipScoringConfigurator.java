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

import java.time.Duration;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicScoringConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.spec.Spec;

/**
 * These calculations have been derived following the gossip scoring implementation from Lighthouse
 * (which is Apache 2.0 licensed). Big thanks to the Lighthouse team!
 *
 * @see <a
 *     href="https://github.com/sigp/lighthouse/blob/f183af20e3fb44773397c9e26540703ee94ec366/beacon_node/eth2_libp2p/src/behaviour/gossipsub_scoring_parameters.rs">Sigma
 *     Prime's Lighthouse gossip scoring logic</a>
 */
class GossipScoringConfigurator implements GossipConfigurator {
  private static final int GOSSIP_D = 8;
  private final ScoringConfig scoringConfig;

  public GossipScoringConfigurator(final Spec spec) {
    this.scoringConfig = ScoringConfig.create(spec, GOSSIP_D);
  }

  @Override
  public void configure(
      final GossipConfig.Builder gossipConfigBuilder, final Eth2Context eth2Context) {
    gossipConfigBuilder.d(GOSSIP_D).dLow(6).scoring(b -> configureScoring(b, eth2Context));
  }

  @Override
  public void configureAllTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context) {
    final TopicConfigurator topicConfigurator = new TopicConfigurator(scoringConfig, eth2Context);
    topicConfigurator.configureAllTopics(topicsConfigBuilder);
  }

  @Override
  public void configureDynamicTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context) {
    final TopicConfigurator topicConfigurator = new TopicConfigurator(scoringConfig, eth2Context);
    topicConfigurator.configureDynamicTopics(topicsConfigBuilder);
  }

  private void configureScoring(
      final GossipScoringConfig.Builder builder, final Eth2Context eth2Context) {
    final double maxPositiveScore = scoringConfig.getMaxPositiveScore();

    final double behaviorPenaltyThreshold = 6.0;
    final double behaviorPenaltyDecayFactor =
        scoringConfig.calculateDecayFactor(scoringConfig.getEpochDuration().multipliedBy(10));
    final double targetValue =
        scoringConfig.calculateDecayConvergence(
                behaviorPenaltyDecayFactor, 10.0 / scoringConfig.getSlotsPerEpoch())
            - behaviorPenaltyThreshold;
    final double behaviorPenaltyWeight =
        scoringConfig.getGossipThreshold() / (targetValue * targetValue);

    builder
        .gossipThreshold(scoringConfig.getGossipThreshold())
        .publishThreshold(scoringConfig.getPublishThreshold())
        .graylistThreshold(scoringConfig.getGraylistThreshold())
        .acceptPXThreshold(scoringConfig.getAcceptPeerExchangeThreshold())
        .opportunisticGraftThreshold(scoringConfig.getOpportunisticGraftThreshold())
        .peerScoring(
            p ->
                p.decayInterval(scoringConfig.getSlotDuration())
                    .decayToZero(scoringConfig.getDecayToZero())
                    .retainScore(scoringConfig.getTargetScoreRetention())
                    .ipColocationFactorWeight(-0.5 * maxPositiveScore)
                    .ipColocationFactorThreshold(3)
                    .behaviourPenaltyWeight(behaviorPenaltyWeight)
                    .behaviourPenaltyThreshold(behaviorPenaltyThreshold)
                    .behaviourPenaltyDecay(behaviorPenaltyDecayFactor)
                    .topicScoreCap(0.5 * maxPositiveScore));

    // Configure topics
    final TopicConfigurator topicConfigurator = new TopicConfigurator(scoringConfig, eth2Context);
    builder.topicScoring(topicConfigurator::configureAllTopics);
  }

  private static class TopicConfigurator {
    private final ScoringConfig scoringConfig;
    private final Eth2Context eth2Context;
    private final boolean isConfigurable;
    private final Bytes4 forkDigest;

    TopicConfigurator(final ScoringConfig scoringConfig, final Eth2Context eth2Context) {
      this.scoringConfig = scoringConfig;
      this.eth2Context = eth2Context;
      this.forkDigest = eth2Context.getForkDigest().orElse(null);
      this.isConfigurable = this.forkDigest != null;
    }

    public void configureAllTopics(final GossipTopicsScoringConfig.Builder builder) {
      if (!isConfigurable) {
        return;
      }

      configureVoluntaryExitTopic(builder);
      configureAttesterSlashingTopic(builder);
      configureProposerSlashingTopic(builder);
      configureDynamicTopics(builder);
    }

    // Configure topics that rely on changing values (active validators, current slot) and must be
    // updated periodically
    public void configureDynamicTopics(final GossipTopicsScoringConfig.Builder builder) {
      configureBlockTopic(builder);
      configureExecutionPayloadTopic(builder);
      configureAggregateTopic(builder);
      configureAttestationSubnetTopics(builder);
    }

    private void configureVoluntaryExitTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest, GossipTopicName.VOLUNTARY_EXIT, eth2Context.getGossipEncoding());
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getVoluntaryExitTopicWeight(),
                  4.0 / scoringConfig.getSlotsPerEpoch(),
                  scoringConfig.getTargetScoreDecayFactor()));
    }

    private void configureAttesterSlashingTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest, GossipTopicName.ATTESTER_SLASHING, eth2Context.getGossipEncoding());
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getAttesterSlashingTopicWeight(),
                  1.0 / 5.0 / scoringConfig.getSlotsPerEpoch(),
                  scoringConfig.getTargetScoreDecayFactor()));
    }

    private void configureProposerSlashingTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest, GossipTopicName.PROPOSER_SLASHING, eth2Context.getGossipEncoding());
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getProposerSlashingTopicWeight(),
                  1.0 / 5.0 / scoringConfig.getSlotsPerEpoch(),
                  scoringConfig.getTargetScoreDecayFactor()));
    }

    private void configureBlockTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest, GossipTopicName.BEACON_BLOCK, eth2Context.getGossipEncoding());
      final MessageDeliveriesOptions msgDeliveryOptions =
          MessageDeliveriesOptions.create(
              scoringConfig.getEpochDuration(), 3.0, scoringConfig.convertEpochsToSlots(5));
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getBeaconBlockTopicWeight(),
                  1.0,
                  scoringConfig.calculateDecayFactor(
                      scoringConfig.getEpochDuration().multipliedBy(20)),
                  Optional.of(msgDeliveryOptions)));
    }

    private void configureExecutionPayloadTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest, GossipTopicName.EXECUTION_PAYLOAD, eth2Context.getGossipEncoding());
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getExecutionPayloadTopicWeight(),
                  1.0,
                  scoringConfig.getTargetScoreDecayFactor()));
    }

    private void configureAggregateTopic(final GossipTopicsScoringConfig.Builder builder) {
      final String topic =
          GossipTopics.getTopic(
              forkDigest,
              GossipTopicName.BEACON_AGGREGATE_AND_PROOF,
              eth2Context.getGossipEncoding());
      final MessageDeliveriesOptions msgDeliveryOptions =
          MessageDeliveriesOptions.create(
              scoringConfig.getEpochDuration(), 4.0, scoringConfig.convertEpochsToSlots(2));
      builder.topicScoring(
          topic,
          b ->
              configureTopic(
                  b,
                  scoringConfig.getBeaconAggregateProofTopicWeight(),
                  scoringConfig.getAggregatorsPerSlot(eth2Context.getActiveValidatorCount()),
                  scoringConfig.calculateDecayFactor(scoringConfig.getEpochDuration()),
                  Optional.of(msgDeliveryOptions)));
    }

    private void configureAttestationSubnetTopics(final GossipTopicsScoringConfig.Builder builder) {
      final double slotsPerEpoch = scoringConfig.getSlotsPerEpoch();
      final double subnetCount = scoringConfig.getAttestationSubnetCount();
      final double subnetWeight = scoringConfig.getAttestationSubnetTopicWeight();
      final double messageRate =
          (double) eth2Context.getActiveValidatorCount() / subnetCount / slotsPerEpoch;
      final boolean multipleBurstsPerSubnetPerEpoch =
          scoringConfig.getCommitteesPerSlot(eth2Context.getActiveValidatorCount())
              >= subnetCount * 2 / slotsPerEpoch;
      final Duration timeToDecay =
          multipleBurstsPerSubnetPerEpoch
              ? scoringConfig.getEpochDuration()
              : scoringConfig.getEpochDuration().multipliedBy(4);

      final int decaySlots =
          multipleBurstsPerSubnetPerEpoch
              ? scoringConfig.convertEpochsToSlots(4)
              : scoringConfig.convertEpochsToSlots(16);
      final Duration activationWindow =
          multipleBurstsPerSubnetPerEpoch
              ? scoringConfig
                  .getSlotDuration()
                  .multipliedBy(scoringConfig.getSlotsPerEpoch() / 2 + 1)
              : scoringConfig.getEpochDuration().multipliedBy(3);
      final MessageDeliveriesOptions msgDeliveryOptions =
          MessageDeliveriesOptions.create(activationWindow, 16.0, decaySlots);

      for (int i = 0; i < scoringConfig.getAttestationSubnetCount(); i++) {
        final String subnetName = GossipTopicName.getAttestationSubnetTopicName(i);
        final String topic =
            GossipTopics.getTopic(forkDigest, subnetName, eth2Context.getGossipEncoding());
        builder.topicScoring(
            topic,
            b ->
                configureTopic(
                    b,
                    subnetWeight,
                    messageRate,
                    scoringConfig.calculateDecayFactor(timeToDecay),
                    Optional.of(msgDeliveryOptions)));
      }
    }

    private void configureTopic(
        final GossipTopicScoringConfig.Builder builder,
        final double topicWeight,
        final double expectedMessageRate,
        final double firstMessageDeliveryDecay) {
      configureTopic(
          builder, topicWeight, expectedMessageRate, firstMessageDeliveryDecay, Optional.empty());
    }

    private void configureTopic(
        final GossipTopicScoringConfig.Builder builder,
        final double topicWeight,
        final double expectedMessageRate,
        final double firstMessageDeliveryDecay,
        final Optional<MessageDeliveriesOptions> messageDeliveryOptions) {
      final double timeInMeshCap =
          Math.max(1., Duration.ofHours(1).dividedBy(scoringConfig.getSlotDuration()));
      final double firstMessageDeliveriesCap =
          scoringConfig.calculateDecayConvergence(
              firstMessageDeliveryDecay, 2.0 * expectedMessageRate / scoringConfig.getD());

      builder
          .topicWeight(topicWeight)
          .timeInMeshQuantum(scoringConfig.getSlotDuration())
          .timeInMeshCap(timeInMeshCap)
          .timeInMeshWeight(scoringConfig.getMaxInMeshScore() / timeInMeshCap)
          .firstMessageDeliveriesDecay(firstMessageDeliveryDecay)
          .firstMessageDeliveriesCap(firstMessageDeliveriesCap)
          .firstMessageDeliveriesWeight(
              scoringConfig.getMaxFirstMessageDeliveriesScore() / firstMessageDeliveriesCap)
          .invalidMessageDeliveriesWeight(-scoringConfig.getMaxPositiveScore() / topicWeight)
          .invalidMessageDeliveriesDecay(
              scoringConfig.calculateDecayFactor(
                  scoringConfig.getEpochDuration().multipliedBy(50)));

      messageDeliveryOptions.ifPresentOrElse(
          options -> {
            final double decayFactor =
                scoringConfig.calculateDecayFactor(
                    scoringConfig.getSlotDuration().multipliedBy(options.getDecaySlots()));
            final double threshold =
                scoringConfig.calculateDecayConvergence(
                        decayFactor, expectedMessageRate * options.getMessageRateFactor())
                    * decayFactor;
            final double cap = Math.max(2.0, options.getCapFactor() * threshold);
            final double weight =
                -scoringConfig.getMaxPositiveScore() / (topicWeight * Math.pow(threshold, 2.0));
            builder
                .meshMessageDeliveriesWeight(weight)
                .meshMessageDeliveriesThreshold(threshold)
                .meshMessageDeliveriesDecay(decayFactor)
                .meshMessageDeliveriesCap(cap)
                .meshMessageDeliveryWindow(options.getDeliveriesWindow())
                .meshMessageDeliveriesActivation(options.getActivationWindow())
                .meshFailurePenaltyDecay(decayFactor)
                .meshFailurePenaltyWeight(weight);

            if (eth2Context.getCurrentSlot().isLessThan(options.getDecaySlots())) {
              // Disable mesh delivery scoring component until after decaySlots have been processed
              // by the chain. This prevents us from de-scoring all of our peers during the period
              // between the genesis block being known and the chain actually starting up at
              // genesisTime.
              builder.meshMessageDeliveriesThreshold(0.0).meshMessageDeliveriesWeight(0.0);
            }
          },
          () -> {
            // Disable mesh delivery options
            builder
                .meshMessageDeliveriesWeight(0.0)
                .meshMessageDeliveriesThreshold(0.0)
                .meshMessageDeliveriesDecay(0.0)
                .meshMessageDeliveriesCap(0.0)
                .meshMessageDeliveryWindow(Duration.ofSeconds(0))
                .meshMessageDeliveriesActivation(Duration.ofSeconds(0))
                .meshFailurePenaltyDecay(0.0)
                .meshFailurePenaltyWeight(0.0);
          });
    }
  }
}
