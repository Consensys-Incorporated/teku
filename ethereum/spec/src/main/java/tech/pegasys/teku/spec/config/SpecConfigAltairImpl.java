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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigAltairImpl extends DelegatingSpecConfig implements SpecConfigAltair {

  // Updated penalties
  private final UInt64 inactivityPenaltyQuotientAltair;
  private final int minSlashingPenaltyQuotientAltair;
  private final int proportionalSlashingMultiplierAltair;

  // Misc
  private final int syncCommitteeSize;
  private final UInt64 inactivityScoreBias;
  private final UInt64 inactivityScoreRecoveryRate;

  // Time
  private final int epochsPerSyncCommitteePeriod;
  private final int syncMessageDueBps;
  private final int contributionDueBps;

  // Sync protocol
  private final int minSyncCommitteeParticipants;
  private final int updateTimeout;

  public SpecConfigAltairImpl(
      final SpecConfig specConfig,
      final UInt64 inactivityPenaltyQuotientAltair,
      final int altairMinSlashingPenaltyQuotient,
      final int proportionalSlashingMultiplierAltair,
      final int syncCommitteeSize,
      final UInt64 inactivityScoreBias,
      final UInt64 inactivityScoreRecoveryRate,
      final int epochsPerSyncCommitteePeriod,
      final int minSyncCommitteeParticipants,
      final int updateTimeout,
      final int syncMessageDueBps,
      final int contributionDueBps) {
    super(specConfig);
    this.inactivityPenaltyQuotientAltair = inactivityPenaltyQuotientAltair;
    this.minSlashingPenaltyQuotientAltair = altairMinSlashingPenaltyQuotient;
    this.proportionalSlashingMultiplierAltair = proportionalSlashingMultiplierAltair;
    this.syncCommitteeSize = syncCommitteeSize;
    this.inactivityScoreBias = inactivityScoreBias;
    this.inactivityScoreRecoveryRate = inactivityScoreRecoveryRate;
    this.epochsPerSyncCommitteePeriod = epochsPerSyncCommitteePeriod;
    this.minSyncCommitteeParticipants = minSyncCommitteeParticipants;
    this.updateTimeout = updateTimeout;
    this.syncMessageDueBps = syncMessageDueBps;
    this.contributionDueBps = contributionDueBps;
  }

  public static SpecConfigAltair required(final SpecConfig specConfig) {
    return specConfig
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected altair spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  @Override
  public UInt64 getInactivityPenaltyQuotientAltair() {
    return inactivityPenaltyQuotientAltair;
  }

  @Override
  public int getMinSlashingPenaltyQuotientAltair() {
    return minSlashingPenaltyQuotientAltair;
  }

  @Override
  public int getProportionalSlashingMultiplierAltair() {
    return proportionalSlashingMultiplierAltair;
  }

  @Override
  public int getSyncCommitteeSize() {
    return syncCommitteeSize;
  }

  @Override
  public UInt64 getInactivityScoreBias() {
    return inactivityScoreBias;
  }

  @Override
  public UInt64 getInactivityScoreRecoveryRate() {
    return inactivityScoreRecoveryRate;
  }

  @Override
  public int getEpochsPerSyncCommitteePeriod() {
    return epochsPerSyncCommitteePeriod;
  }

  @Override
  public int getMinSyncCommitteeParticipants() {
    return minSyncCommitteeParticipants;
  }

  @Override
  public int getUpdateTimeout() {
    return updateTimeout;
  }

  @Override
  public int getSyncMessageDueBps() {
    return syncMessageDueBps;
  }

  @Override
  public int getContributionDueBps() {
    return contributionDueBps;
  }

  @Override
  public Optional<SpecConfigAltair> toVersionAltair() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.ALTAIR;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigAltairImpl that = (SpecConfigAltairImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && minSlashingPenaltyQuotientAltair == that.minSlashingPenaltyQuotientAltair
        && proportionalSlashingMultiplierAltair == that.proportionalSlashingMultiplierAltair
        && syncCommitteeSize == that.syncCommitteeSize
        && Objects.equals(inactivityScoreBias, that.inactivityScoreBias)
        && Objects.equals(inactivityScoreRecoveryRate, that.inactivityScoreRecoveryRate)
        && epochsPerSyncCommitteePeriod == that.epochsPerSyncCommitteePeriod
        && minSyncCommitteeParticipants == that.minSyncCommitteeParticipants
        && Objects.equals(inactivityPenaltyQuotientAltair, that.inactivityPenaltyQuotientAltair);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        inactivityPenaltyQuotientAltair,
        minSlashingPenaltyQuotientAltair,
        proportionalSlashingMultiplierAltair,
        syncCommitteeSize,
        inactivityScoreBias,
        inactivityScoreRecoveryRate,
        epochsPerSyncCommitteePeriod,
        minSyncCommitteeParticipants);
  }
}
