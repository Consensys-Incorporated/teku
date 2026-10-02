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

import com.google.common.base.Suppliers;
import java.time.Duration;
import java.util.function.Supplier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;

class ScoringConfig {
  private static final double MAX_IN_MESH_SCORE = 10.0;
  private static final double MAX_FIRST_MESSAGE_DELIVERIES_SCORE = 40.0;
  private static final double BEACON_BLOCK_TOPIC_WEIGHT = 0.5;
  private static final double BEACON_AGGREGATE_PROOF_TOPIC_WEIGHT = 0.5;
  private static final double ATTESTATION_SUBNET_CUMULATIVE_TOPIC_WEIGHT = 1.0;
  private static final double VOLUNTARY_EXIT_TOPIC_WEIGHT = 0.05;
  private static final double PROPOSER_SLASHING_TOPIC_WEIGHT = 0.05;
  private static final double ATTESTER_SLASHING_TOPIC_WEIGHT = 0.05;
  // Not part of the max positive score, so adding it leaves existing topic scoring unchanged
  private static final double EXECUTION_PAYLOAD_TOPIC_WEIGHT = 0.05;

  private static final double GOSSIP_THRESHOLD = -4000.0;
  private static final double PUBLISH_THRESHOLD = -8000.0;
  private static final double GRAYLIST_THRESHOLD = -16000.0;
  private static final double ACCEPT_PEER_EXCHANGE_THRESHOLD = 100.0;
  private static final double OPPORTUNISTIC_GRAFT_THRESHOLD = 5.0;

  private static final double DECAY_TO_ZERO = .01;

  private final Supplier<Double> maxScoreCalculator =
      Suppliers.memoize(this::calculateMaxPositiveScore);

  private final Spec spec;
  private final SpecConfig genesisConfig;
  private final SpecVersion genesisSpec;
  private final Duration slotDuration;
  private final Duration epochDuration;

  // The gossip param D - the desired number of peers in each topic mesh
  private final int d;
  private final Duration decayInterval;
  private final Duration targetScoringDuration;

  private ScoringConfig(final Spec spec, final int d) {
    this.spec = spec;
    this.genesisConfig = spec.getGenesisSpecConfig();
    this.genesisSpec = spec.getGenesisSpec();
    this.d = d;

    this.slotDuration = Duration.ofSeconds(this.genesisConfig.getSecondsPerSlot());
    this.epochDuration = slotDuration.multipliedBy(this.genesisConfig.getSlotsPerEpoch());
    this.decayInterval = slotDuration;
    this.targetScoringDuration = epochDuration.multipliedBy(100);
  }

  public static ScoringConfig create(final Spec spec, final int gossipDParam) {
    return new ScoringConfig(spec, gossipDParam);
  }

  public double getMaxInMeshScore() {
    return MAX_IN_MESH_SCORE;
  }

  public double getMaxFirstMessageDeliveriesScore() {
    return MAX_FIRST_MESSAGE_DELIVERIES_SCORE;
  }

  public double getBeaconBlockTopicWeight() {
    return BEACON_BLOCK_TOPIC_WEIGHT;
  }

  public double getBeaconAggregateProofTopicWeight() {
    return BEACON_AGGREGATE_PROOF_TOPIC_WEIGHT;
  }

  public double getVoluntaryExitTopicWeight() {
    return VOLUNTARY_EXIT_TOPIC_WEIGHT;
  }

  public double getProposerSlashingTopicWeight() {
    return PROPOSER_SLASHING_TOPIC_WEIGHT;
  }

  public double getAttesterSlashingTopicWeight() {
    return ATTESTER_SLASHING_TOPIC_WEIGHT;
  }

  public double getExecutionPayloadTopicWeight() {
    return EXECUTION_PAYLOAD_TOPIC_WEIGHT;
  }

  public double getGossipThreshold() {
    return GOSSIP_THRESHOLD;
  }

  public double getPublishThreshold() {
    return PUBLISH_THRESHOLD;
  }

  public double getGraylistThreshold() {
    return GRAYLIST_THRESHOLD;
  }

  public double getAcceptPeerExchangeThreshold() {
    return ACCEPT_PEER_EXCHANGE_THRESHOLD;
  }

  public double getOpportunisticGraftThreshold() {
    return OPPORTUNISTIC_GRAFT_THRESHOLD;
  }

  public Duration getDecayInterval() {
    return decayInterval;
  }

  public int getSlotsPerEpoch() {
    return genesisConfig.getSlotsPerEpoch();
  }

  public int convertEpochsToSlots(final int epochs) {
    return getSlotsPerEpoch() * epochs;
  }

  public double getDecayToZero() {
    return DECAY_TO_ZERO;
  }

  public Duration getSlotDuration() {
    return slotDuration;
  }

  public Duration getEpochDuration() {
    return epochDuration;
  }

  public double getAggregatorsPerSlot(final int activeValidatorCount) {
    final int committeesPerEpoch =
        spec.getGenesisSpec()
            .beaconStateAccessors()
            .getCommitteeCountPerSlot(activeValidatorCount)
            .times(genesisConfig.getSlotsPerEpoch())
            .intValue();

    // Committees can vary in size by one - calculate aggregators per slot accounting for this
    // variance
    final int smallCommitteeSize = activeValidatorCount / committeesPerEpoch;
    final int largeCommitteeSize = smallCommitteeSize + 1;
    final int largeCommitteesPerEpoch =
        activeValidatorCount - (smallCommitteeSize * committeesPerEpoch);
    final int smallCommitteesPerEpoch = committeesPerEpoch - largeCommitteesPerEpoch;

    // Calculate aggregators per epoch
    final int smallAggregatorModulo =
        genesisSpec.getValidatorsUtil().getAggregatorModulo(smallCommitteeSize);
    final int largeAggregatorModulo =
        genesisSpec.getValidatorsUtil().getAggregatorModulo(largeCommitteeSize);
    final int smallCommitteeAggregatorPerEpoch =
        smallCommitteeSize / smallAggregatorModulo * smallCommitteesPerEpoch;
    final int largeCommitteeAggregatorPerEpoch =
        largeCommitteeSize / largeAggregatorModulo * largeCommitteesPerEpoch;

    return ((double) smallCommitteeAggregatorPerEpoch + largeCommitteeAggregatorPerEpoch)
        / genesisConfig.getSlotsPerEpoch();
  }

  public int getCommitteesPerSlot(final int activeValidatorCount) {
    return spec.getGenesisSpec()
        .beaconStateAccessors()
        .getCommitteeCountPerSlot(activeValidatorCount)
        .intValue();
  }

  /**
   * @return The desired time to decay a single scoring event to zero
   */
  public Duration getTargetScoreRetention() {
    return targetScoringDuration;
  }

  /**
   * @return The decay factor to ensure each scoring event decays according to {@link
   *     #getTargetScoreRetention()}
   */
  public double getTargetScoreDecayFactor() {
    return calculateDecayFactor(getTargetScoreRetention());
  }

  /**
   * @return The target degree (number of peers) in each topic mesh
   */
  public int getD() {
    return d;
  }

  public double getMaxPositiveScore() {
    return maxScoreCalculator.get();
  }

  private double calculateMaxPositiveScore() {
    return (getMaxInMeshScore() + getMaxFirstMessageDeliveriesScore())
        * (getBeaconBlockTopicWeight()
            + getBeaconAggregateProofTopicWeight()
            + ATTESTATION_SUBNET_CUMULATIVE_TOPIC_WEIGHT
            + getVoluntaryExitTopicWeight()
            + getProposerSlashingTopicWeight()
            + getAttesterSlashingTopicWeight());
  }

  public double getAttestationSubnetTopicWeight() {
    return ATTESTATION_SUBNET_CUMULATIVE_TOPIC_WEIGHT / getAttestationSubnetCount();
  }

  public int getAttestationSubnetCount() {
    return spec.getNetworkingConfig().getAttestationSubnetCount();
  }

  /**
   * Gossip topic parameters are implemented using counters that are updated whenever an event of
   * interest occurs. The counters decay periodically so that their values are not continuously
   * increasing and ensure that a large positive or negative score isn't sticky for the lifetime of
   * the peer.
   *
   * <p>Each decaying parameter can have its own decay factor, which is a configurable parameter
   * that controls how much the parameter will decay during each decay period.
   *
   * <p>The decay factor is a float in the range of [0.0, 1.0] that will be multiplied with the
   * current counter value at each decay interval update.
   *
   * <p>This helper method calculates a decayFactor that will ensure a single event (count of 1)
   * will decay to zero after the provided {@code decayTime}. This method calculates the number of
   * "ticks" (decayIntervals) in the desired decay time. Using the formula decayFactor^ticks <=
   * decayToZero, we solve for the decayFactor: decayToZero ^ (1 / ticks).
   *
   * @param decayTime The desired duration of time required to decay a count of 1.0 to 0.
   * @return The decay factor corresponding to the desired decay time
   * @see <a
   *     href="https://github.com/libp2p/specs/blob/master/pubsub/gossipsub/gossipsub-v1.1.md#topic-parameter-calculation-and-decay">Gossipsub
   *     v1.1 Spec</a>
   */
  public double calculateDecayFactor(final Duration decayTime) {
    final double ticksPerDecayTime =
        (float) decayTime.toMillis() / (float) getDecayInterval().toMillis();
    return Math.pow(getDecayToZero(), 1.0 / ticksPerDecayTime);
  }

  /**
   * Calculates the value we can expect a counter to converge to given a message rate and decay
   * factor. This is just the formula summarizing a geometric series (eventRate + eventRate *
   * decayFactor + eventRate * decayFactor^2 + ...)
   *
   * @param decayFactor The decay factor for a gossip counter
   * @param eventRate The expected event rate for a gossip counter
   * @return The value the gossip counter should converge to over time
   */
  public double calculateDecayConvergence(final double decayFactor, final double eventRate) {
    return eventRate / (1.0 - decayFactor);
  }
}
