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
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigPqImpl extends DelegatingSpecConfigEip8483 implements SpecConfigPq {

  private final int maxCommitteesPerSlotPq;
  private final int minAttestationInclusionDelayPq;
  private final int maxStarkProofSize;
  private final int maxAttestationsPq;
  private final int maxValidatorPreregistrationRequestsPerPayload;
  private final int maxBuilderPreregistrationRequestsPerPayload;
  private final int maxVoluntaryExitRequestsPerPayload;
  private final int preregistrationsLimit;
  private final int preregistrationExpirySlots;
  private final int maxSignedAggregateAndProofSizePq;
  private final int maxAttesterSlashingSizePq;
  private final int maxSignedExecutionPayloadBidSizePq;
  private final int unregisteredValidatorWithdrawabilityDelay;
  private final int attestationSubnetCountPq;
  private final int aggregationCutoffBps;
  private final int aggregateDueBpsPq;
  private final int aggregatorDelayBps;

  public SpecConfigPqImpl(
      final SpecConfigEip8483 specConfig,
      final int maxCommitteesPerSlotPq,
      final int minAttestationInclusionDelayPq,
      final int maxStarkProofSize,
      final int maxAttestationsPq,
      final int maxValidatorPreregistrationRequestsPerPayload,
      final int maxBuilderPreregistrationRequestsPerPayload,
      final int maxVoluntaryExitRequestsPerPayload,
      final int preregistrationsLimit,
      final int preregistrationExpirySlots,
      final int maxSignedAggregateAndProofSizePq,
      final int maxAttesterSlashingSizePq,
      final int maxSignedExecutionPayloadBidSizePq,
      final int unregisteredValidatorWithdrawabilityDelay,
      final int attestationSubnetCountPq,
      final int aggregationCutoffBps,
      final int aggregateDueBpsPq,
      final int aggregatorDelayBps) {
    super(specConfig);
    this.maxCommitteesPerSlotPq = maxCommitteesPerSlotPq;
    this.minAttestationInclusionDelayPq = minAttestationInclusionDelayPq;
    this.maxStarkProofSize = maxStarkProofSize;
    this.maxAttestationsPq = maxAttestationsPq;
    this.maxValidatorPreregistrationRequestsPerPayload =
        maxValidatorPreregistrationRequestsPerPayload;
    this.maxBuilderPreregistrationRequestsPerPayload = maxBuilderPreregistrationRequestsPerPayload;
    this.maxVoluntaryExitRequestsPerPayload = maxVoluntaryExitRequestsPerPayload;
    this.preregistrationsLimit = preregistrationsLimit;
    this.preregistrationExpirySlots = preregistrationExpirySlots;
    this.maxSignedAggregateAndProofSizePq = maxSignedAggregateAndProofSizePq;
    this.maxAttesterSlashingSizePq = maxAttesterSlashingSizePq;
    this.maxSignedExecutionPayloadBidSizePq = maxSignedExecutionPayloadBidSizePq;
    this.unregisteredValidatorWithdrawabilityDelay = unregisteredValidatorWithdrawabilityDelay;
    this.attestationSubnetCountPq = attestationSubnetCountPq;
    this.aggregationCutoffBps = aggregationCutoffBps;
    this.aggregateDueBpsPq = aggregateDueBpsPq;
    this.aggregatorDelayBps = aggregatorDelayBps;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.PQ;
  }

  @Override
  public int getMaxCommitteesPerSlotPq() {
    return maxCommitteesPerSlotPq;
  }

  @Override
  public int getMinAttestationInclusionDelayPq() {
    return minAttestationInclusionDelayPq;
  }

  @Override
  public int getMaxStarkProofSize() {
    return maxStarkProofSize;
  }

  @Override
  public int getMaxAttestationsPq() {
    return maxAttestationsPq;
  }

  @Override
  public int getMaxValidatorPreregistrationRequestsPerPayload() {
    return maxValidatorPreregistrationRequestsPerPayload;
  }

  @Override
  public int getMaxBuilderPreregistrationRequestsPerPayload() {
    return maxBuilderPreregistrationRequestsPerPayload;
  }

  @Override
  public int getMaxVoluntaryExitRequestsPerPayload() {
    return maxVoluntaryExitRequestsPerPayload;
  }

  @Override
  public int getPreregistrationsLimit() {
    return preregistrationsLimit;
  }

  @Override
  public int getPreregistrationExpirySlots() {
    return preregistrationExpirySlots;
  }

  @Override
  public int getMaxSignedAggregateAndProofSizePq() {
    return maxSignedAggregateAndProofSizePq;
  }

  @Override
  public int getMaxAttesterSlashingSizePq() {
    return maxAttesterSlashingSizePq;
  }

  @Override
  public int getMaxSignedExecutionPayloadBidSizePq() {
    return maxSignedExecutionPayloadBidSizePq;
  }

  @Override
  public int getUnregisteredValidatorWithdrawabilityDelay() {
    return unregisteredValidatorWithdrawabilityDelay;
  }

  @Override
  public int getAttestationSubnetCountPq() {
    return attestationSubnetCountPq;
  }

  /** AGGREGATE_DUE_BPS_PQ replaces AGGREGATE_DUE_BPS_GLOAS: the first aggregator's due time. */
  @Override
  public int getAggregateDueBps() {
    return aggregateDueBpsPq;
  }

  @Override
  public int getAggregationCutoffBps() {
    return aggregationCutoffBps;
  }

  @Override
  public int getAggregateDueBpsPq() {
    return aggregateDueBpsPq;
  }

  @Override
  public int getAggregatorDelayBps() {
    return aggregatorDelayBps;
  }

  @Override
  public Optional<SpecConfigPq> toVersionPq() {
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
    final SpecConfigPqImpl that = (SpecConfigPqImpl) o;
    return maxCommitteesPerSlotPq == that.maxCommitteesPerSlotPq
        && minAttestationInclusionDelayPq == that.minAttestationInclusionDelayPq
        && maxStarkProofSize == that.maxStarkProofSize
        && maxAttestationsPq == that.maxAttestationsPq
        && maxValidatorPreregistrationRequestsPerPayload
            == that.maxValidatorPreregistrationRequestsPerPayload
        && maxBuilderPreregistrationRequestsPerPayload
            == that.maxBuilderPreregistrationRequestsPerPayload
        && maxVoluntaryExitRequestsPerPayload == that.maxVoluntaryExitRequestsPerPayload
        && preregistrationsLimit == that.preregistrationsLimit
        && preregistrationExpirySlots == that.preregistrationExpirySlots
        && maxSignedAggregateAndProofSizePq == that.maxSignedAggregateAndProofSizePq
        && maxAttesterSlashingSizePq == that.maxAttesterSlashingSizePq
        && maxSignedExecutionPayloadBidSizePq == that.maxSignedExecutionPayloadBidSizePq
        && unregisteredValidatorWithdrawabilityDelay
            == that.unregisteredValidatorWithdrawabilityDelay
        && attestationSubnetCountPq == that.attestationSubnetCountPq
        && aggregationCutoffBps == that.aggregationCutoffBps
        && aggregateDueBpsPq == that.aggregateDueBpsPq
        && aggregatorDelayBps == that.aggregatorDelayBps;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        maxCommitteesPerSlotPq,
        minAttestationInclusionDelayPq,
        maxStarkProofSize,
        maxAttestationsPq,
        maxValidatorPreregistrationRequestsPerPayload,
        maxBuilderPreregistrationRequestsPerPayload,
        maxVoluntaryExitRequestsPerPayload,
        preregistrationsLimit,
        preregistrationExpirySlots,
        maxSignedAggregateAndProofSizePq,
        maxAttesterSlashingSizePq,
        maxSignedExecutionPayloadBidSizePq,
        unregisteredValidatorWithdrawabilityDelay,
        attestationSubnetCountPq,
        aggregationCutoffBps,
        aggregateDueBpsPq,
        aggregatorDelayBps);
  }
}
