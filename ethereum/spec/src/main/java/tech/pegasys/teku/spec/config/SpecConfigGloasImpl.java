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

package tech.pegasys.teku.spec.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigGloasImpl extends DelegatingSpecConfigFulu implements SpecConfigGloas {
  private final int aggregateDueBps;
  private final int attestationDueBps;
  private final int contributionDueBps;
  private final int maxBuilderDepositRequestsPerPayload;
  private final int maxBuilderExitRequestsPerPayload;
  private final long builderRegistryLimit;
  private final long builderPendingWithdrawalsLimit;
  private final int maxBuildersPerWithdrawalsSweep;
  private final int maxPayloadAttestations;
  private final int maxRequestPayloads;
  private final int maxSignedAggregateAndProofSize;
  private final int maxAttesterSlashingSize;
  private final int maxSignedExecutionPayloadBidSize;
  private final int minBuilderWithdrawabilityDelay;
  private final int payloadAttestationDueBps;
  private final int payloadDueBps;
  private final int ptcSize;
  private final int syncMessageDueBps;
  private final int churnLimitQuotientGloas;
  private final int consolidationChurnLimitQuotient;
  private final UInt64 maxPerEpochActivationChurnLimitGloas;
  private final List<GasLimitScheduleEntry> gasLimitSchedule;

  public SpecConfigGloasImpl(
      final SpecConfigFulu specConfig,
      final int aggregateDueBps,
      final int attestationDueBps,
      final int contributionDueBps,
      final int maxBuilderDepositRequestsPerPayload,
      final int maxBuilderExitRequestsPerPayload,
      final long builderRegistryLimit,
      final long builderPendingWithdrawalsLimit,
      final int maxBuildersPerWithdrawalsSweep,
      final int maxPayloadAttestations,
      final int maxRequestPayloads,
      final int minBuilderWithdrawabilityDelay,
      final int payloadAttestationDueBps,
      final int payloadDueBps,
      final int ptcSize,
      final int syncMessageDueBps,
      final int churnLimitQuotientGloas,
      final int consolidationChurnLimitQuotient,
      final UInt64 maxPerEpochActivationChurnLimitGloas,
      final int maxSignedAggregateAndProofSize,
      final int maxAttesterSlashingSize,
      final int maxSignedExecutionPayloadBidSize,
      final List<GasLimitScheduleEntry> gasLimitSchedule) {
    super(specConfig);
    this.aggregateDueBps = aggregateDueBps;
    this.attestationDueBps = attestationDueBps;
    this.contributionDueBps = contributionDueBps;
    this.maxBuilderDepositRequestsPerPayload = maxBuilderDepositRequestsPerPayload;
    this.maxBuilderExitRequestsPerPayload = maxBuilderExitRequestsPerPayload;
    this.builderRegistryLimit = builderRegistryLimit;
    this.builderPendingWithdrawalsLimit = builderPendingWithdrawalsLimit;
    this.maxBuildersPerWithdrawalsSweep = maxBuildersPerWithdrawalsSweep;
    this.maxPayloadAttestations = maxPayloadAttestations;
    this.maxRequestPayloads = maxRequestPayloads;
    this.maxSignedAggregateAndProofSize = maxSignedAggregateAndProofSize;
    this.maxAttesterSlashingSize = maxAttesterSlashingSize;
    this.maxSignedExecutionPayloadBidSize = maxSignedExecutionPayloadBidSize;
    this.ptcSize = ptcSize;
    this.minBuilderWithdrawabilityDelay = minBuilderWithdrawabilityDelay;
    this.payloadAttestationDueBps = payloadAttestationDueBps;
    this.payloadDueBps = payloadDueBps;
    this.syncMessageDueBps = syncMessageDueBps;
    this.churnLimitQuotientGloas = churnLimitQuotientGloas;
    this.consolidationChurnLimitQuotient = consolidationChurnLimitQuotient;
    this.maxPerEpochActivationChurnLimitGloas = maxPerEpochActivationChurnLimitGloas;
    this.gasLimitSchedule = gasLimitSchedule;
  }

  @Override
  public int getAggregateDueBps() {
    return aggregateDueBps;
  }

  @Override
  public int getAttestationDueBps() {
    return attestationDueBps;
  }

  @Override
  public int getContributionDueBps() {
    return contributionDueBps;
  }

  @Override
  public int getMaxRequestPayloads() {
    return maxRequestPayloads;
  }

  @Override
  public int getMaxSignedAggregateAndProofSize() {
    return maxSignedAggregateAndProofSize;
  }

  @Override
  public int getMaxAttesterSlashingSize() {
    return maxAttesterSlashingSize;
  }

  @Override
  public int getMaxSignedExecutionPayloadBidSize() {
    return maxSignedExecutionPayloadBidSize;
  }

  @Override
  public int getMinBuilderWithdrawabilityDelay() {
    return minBuilderWithdrawabilityDelay;
  }

  @Override
  public int getPayloadAttestationDueBps() {
    return payloadAttestationDueBps;
  }

  @Override
  public int getPayloadDueBps() {
    return payloadDueBps;
  }

  @Override
  public int getPtcSize() {
    return ptcSize;
  }

  @Override
  public int getMaxPayloadAttestations() {
    return maxPayloadAttestations;
  }

  @Override
  public int getMaxBuilderDepositRequestsPerPayload() {
    return maxBuilderDepositRequestsPerPayload;
  }

  @Override
  public int getMaxBuilderExitRequestsPerPayload() {
    return maxBuilderExitRequestsPerPayload;
  }

  @Override
  public long getBuilderRegistryLimit() {
    return builderRegistryLimit;
  }

  @Override
  public long getBuilderPendingWithdrawalsLimit() {
    return builderPendingWithdrawalsLimit;
  }

  @Override
  public int getMaxBuildersPerWithdrawalsSweep() {
    return maxBuildersPerWithdrawalsSweep;
  }

  @Override
  public int getSyncMessageDueBps() {
    return syncMessageDueBps;
  }

  @Override
  public int getChurnLimitQuotientGloas() {
    return churnLimitQuotientGloas;
  }

  @Override
  public int getConsolidationChurnLimitQuotient() {
    return consolidationChurnLimitQuotient;
  }

  @Override
  public UInt64 getMaxPerEpochActivationChurnLimitGloas() {
    return maxPerEpochActivationChurnLimitGloas;
  }

  @Override
  public List<GasLimitScheduleEntry> getGasLimitSchedule() {
    return gasLimitSchedule;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.GLOAS;
  }

  @Override
  public Optional<SpecConfigGloas> toVersionGloas() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SpecConfigGloasImpl that = (SpecConfigGloasImpl) o;
    return aggregateDueBps == that.aggregateDueBps
        && attestationDueBps == that.attestationDueBps
        && contributionDueBps == that.contributionDueBps
        && maxPayloadAttestations == that.maxPayloadAttestations
        && maxRequestPayloads == that.maxRequestPayloads
        && maxSignedAggregateAndProofSize == that.maxSignedAggregateAndProofSize
        && maxAttesterSlashingSize == that.maxAttesterSlashingSize
        && maxSignedExecutionPayloadBidSize == that.maxSignedExecutionPayloadBidSize
        && minBuilderWithdrawabilityDelay == that.minBuilderWithdrawabilityDelay
        && payloadAttestationDueBps == that.payloadAttestationDueBps
        && payloadDueBps == that.payloadDueBps
        && ptcSize == that.ptcSize
        && syncMessageDueBps == that.syncMessageDueBps
        && maxBuilderDepositRequestsPerPayload == that.maxBuilderDepositRequestsPerPayload
        && maxBuilderExitRequestsPerPayload == that.maxBuilderExitRequestsPerPayload
        && builderRegistryLimit == that.builderRegistryLimit
        && builderPendingWithdrawalsLimit == that.builderPendingWithdrawalsLimit
        && maxBuildersPerWithdrawalsSweep == that.maxBuildersPerWithdrawalsSweep
        && churnLimitQuotientGloas == that.churnLimitQuotientGloas
        && consolidationChurnLimitQuotient == that.consolidationChurnLimitQuotient
        && Objects.equals(
            maxPerEpochActivationChurnLimitGloas, that.maxPerEpochActivationChurnLimitGloas)
        && Objects.equals(gasLimitSchedule, that.gasLimitSchedule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        aggregateDueBps,
        attestationDueBps,
        contributionDueBps,
        maxBuilderDepositRequestsPerPayload,
        maxBuilderExitRequestsPerPayload,
        builderRegistryLimit,
        builderPendingWithdrawalsLimit,
        maxBuildersPerWithdrawalsSweep,
        maxPayloadAttestations,
        maxRequestPayloads,
        maxSignedAggregateAndProofSize,
        maxAttesterSlashingSize,
        maxSignedExecutionPayloadBidSize,
        minBuilderWithdrawabilityDelay,
        payloadAttestationDueBps,
        payloadDueBps,
        ptcSize,
        syncMessageDueBps,
        churnLimitQuotientGloas,
        consolidationChurnLimitQuotient,
        maxPerEpochActivationChurnLimitGloas,
        gasLimitSchedule);
  }
}
