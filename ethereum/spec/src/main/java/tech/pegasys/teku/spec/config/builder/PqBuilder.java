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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.constants.PqConstants.AGGREGATORS_PER_COMMITTEE;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigEip8483;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.config.SpecConfigPqImpl;

public class PqBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigEip8483, SpecConfigPq> {

  private Integer maxCommitteesPerSlotPq;
  private Integer minAttestationInclusionDelayPq;
  private Integer maxStarkProofSize;
  private Integer maxAttestationsPq;
  private Integer maxValidatorPreregistrationRequestsPerPayload;
  private Integer maxBuilderPreregistrationRequestsPerPayload;
  private Integer maxVoluntaryExitRequestsPerPayload;
  private Integer preregistrationsLimit;
  private Integer preregistrationExpirySlots;
  private Integer maxSignedAggregateAndProofSizePq;
  private Integer maxAttesterSlashingSizePq;
  private Integer maxSignedExecutionPayloadBidSizePq;
  private Integer unregisteredValidatorWithdrawabilityDelay;
  private Integer attestationSubnetCountPq;
  // The staggered aggregator timing (consensus-specs 4595f0fc) defaults to the specification's
  // values, since PQ devnet configs written before it don't have them
  private Integer aggregationCutoffBps = 4000;
  private Integer aggregateDueBpsPq = 6000;
  private Integer aggregatorDelayBps = 500;
  // Temporary and only for testing (consensus-specs af898d07); a config without it, as PQ devnet
  // configs written before it are, has the preset's MAX_COMMITTEES_PER_SLOT_PQ
  private Integer xMaxCommitteeCountPerSlot;

  PqBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigPq> build(
      final SpecConfigAndParent<SpecConfigEip8483> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigPqImpl(
            specConfigAndParent.specConfig(),
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
            aggregatorDelayBps,
            xMaxCommitteeCountPerSlot),
        specConfigAndParent);
  }

  public PqBuilder maxCommitteesPerSlotPq(final Integer maxCommitteesPerSlotPq) {
    checkNotNull(maxCommitteesPerSlotPq);
    this.maxCommitteesPerSlotPq = maxCommitteesPerSlotPq;
    return this;
  }

  public PqBuilder minAttestationInclusionDelayPq(final Integer minAttestationInclusionDelayPq) {
    checkNotNull(minAttestationInclusionDelayPq);
    this.minAttestationInclusionDelayPq = minAttestationInclusionDelayPq;
    return this;
  }

  public PqBuilder maxStarkProofSize(final Integer maxStarkProofSize) {
    checkNotNull(maxStarkProofSize);
    this.maxStarkProofSize = maxStarkProofSize;
    return this;
  }

  public PqBuilder maxAttestationsPq(final Integer maxAttestationsPq) {
    checkNotNull(maxAttestationsPq);
    this.maxAttestationsPq = maxAttestationsPq;
    return this;
  }

  public PqBuilder maxValidatorPreregistrationRequestsPerPayload(
      final Integer maxValidatorPreregistrationRequestsPerPayload) {
    checkNotNull(maxValidatorPreregistrationRequestsPerPayload);
    this.maxValidatorPreregistrationRequestsPerPayload =
        maxValidatorPreregistrationRequestsPerPayload;
    return this;
  }

  public PqBuilder maxBuilderPreregistrationRequestsPerPayload(
      final Integer maxBuilderPreregistrationRequestsPerPayload) {
    checkNotNull(maxBuilderPreregistrationRequestsPerPayload);
    this.maxBuilderPreregistrationRequestsPerPayload = maxBuilderPreregistrationRequestsPerPayload;
    return this;
  }

  public PqBuilder maxVoluntaryExitRequestsPerPayload(
      final Integer maxVoluntaryExitRequestsPerPayload) {
    checkNotNull(maxVoluntaryExitRequestsPerPayload);
    this.maxVoluntaryExitRequestsPerPayload = maxVoluntaryExitRequestsPerPayload;
    return this;
  }

  public PqBuilder preregistrationsLimit(final Integer preregistrationsLimit) {
    checkNotNull(preregistrationsLimit);
    this.preregistrationsLimit = preregistrationsLimit;
    return this;
  }

  public PqBuilder preregistrationExpirySlots(final Integer preregistrationExpirySlots) {
    checkNotNull(preregistrationExpirySlots);
    this.preregistrationExpirySlots = preregistrationExpirySlots;
    return this;
  }

  public PqBuilder maxSignedAggregateAndProofSizePq(
      final Integer maxSignedAggregateAndProofSizePq) {
    checkNotNull(maxSignedAggregateAndProofSizePq);
    this.maxSignedAggregateAndProofSizePq = maxSignedAggregateAndProofSizePq;
    return this;
  }

  public PqBuilder maxAttesterSlashingSizePq(final Integer maxAttesterSlashingSizePq) {
    checkNotNull(maxAttesterSlashingSizePq);
    this.maxAttesterSlashingSizePq = maxAttesterSlashingSizePq;
    return this;
  }

  public PqBuilder maxSignedExecutionPayloadBidSizePq(
      final Integer maxSignedExecutionPayloadBidSizePq) {
    checkNotNull(maxSignedExecutionPayloadBidSizePq);
    this.maxSignedExecutionPayloadBidSizePq = maxSignedExecutionPayloadBidSizePq;
    return this;
  }

  public PqBuilder unregisteredValidatorWithdrawabilityDelay(
      final Integer unregisteredValidatorWithdrawabilityDelay) {
    checkNotNull(unregisteredValidatorWithdrawabilityDelay);
    this.unregisteredValidatorWithdrawabilityDelay = unregisteredValidatorWithdrawabilityDelay;
    return this;
  }

  public PqBuilder attestationSubnetCountPq(final Integer attestationSubnetCountPq) {
    checkNotNull(attestationSubnetCountPq);
    this.attestationSubnetCountPq = attestationSubnetCountPq;
    return this;
  }

  public PqBuilder aggregationCutoffBps(final Integer aggregationCutoffBps) {
    checkNotNull(aggregationCutoffBps);
    this.aggregationCutoffBps = aggregationCutoffBps;
    return this;
  }

  public PqBuilder aggregateDueBpsPq(final Integer aggregateDueBpsPq) {
    checkNotNull(aggregateDueBpsPq);
    this.aggregateDueBpsPq = aggregateDueBpsPq;
    return this;
  }

  public PqBuilder aggregatorDelayBps(final Integer aggregatorDelayBps) {
    checkNotNull(aggregatorDelayBps);
    this.aggregatorDelayBps = aggregatorDelayBps;
    return this;
  }

  public PqBuilder xMaxCommitteeCountPerSlot(final Integer xMaxCommitteeCountPerSlot) {
    checkNotNull(xMaxCommitteeCountPerSlot);
    this.xMaxCommitteeCountPerSlot = xMaxCommitteeCountPerSlot;
    return this;
  }

  @Override
  public void validate() {
    if (xMaxCommitteeCountPerSlot == null) {
      xMaxCommitteeCountPerSlot = maxCommitteesPerSlotPq;
    }
    defaultValuesIfRequired(this);
    validateConstants();
    // The config invariants of consensus-specs 4595f0fc: each aggregator stops collecting before it
    // broadcasts, and the last one broadcasts within the slot
    checkArgument(
        aggregationCutoffBps < aggregateDueBpsPq,
        "AGGREGATION_CUTOFF_BPS must be less than AGGREGATE_DUE_BPS_PQ");
    checkArgument(
        aggregateDueBpsPq + (AGGREGATORS_PER_COMMITTEE - 1) * aggregatorDelayBps <= 10_000,
        "The last aggregator's AGGREGATE_DUE_BPS_PQ plus AGGREGATOR_DELAY_BPS offset must be"
            + " within the slot");
    if (maxCommitteesPerSlotPq > 0) {
      // It sizes no type, so it must fit within CommitteeBits
      checkArgument(
          xMaxCommitteeCountPerSlot >= 1 && xMaxCommitteeCountPerSlot <= maxCommitteesPerSlotPq,
          "X_MAX_COMMITTEE_COUNT_PER_SLOT must be between 1 and MAX_COMMITTEES_PER_SLOT_PQ (%s)",
          maxCommitteesPerSlotPq);
    }
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("maxCommitteesPerSlotPq", maxCommitteesPerSlotPq);
    constants.put("minAttestationInclusionDelayPq", minAttestationInclusionDelayPq);
    constants.put("maxStarkProofSize", maxStarkProofSize);
    constants.put("maxAttestationsPq", maxAttestationsPq);
    constants.put(
        "maxValidatorPreregistrationRequestsPerPayload",
        maxValidatorPreregistrationRequestsPerPayload);
    constants.put(
        "maxBuilderPreregistrationRequestsPerPayload", maxBuilderPreregistrationRequestsPerPayload);
    constants.put("maxVoluntaryExitRequestsPerPayload", maxVoluntaryExitRequestsPerPayload);
    constants.put("preregistrationsLimit", preregistrationsLimit);
    constants.put("preregistrationExpirySlots", preregistrationExpirySlots);
    constants.put("maxSignedAggregateAndProofSizePq", maxSignedAggregateAndProofSizePq);
    constants.put("maxAttesterSlashingSizePq", maxAttesterSlashingSizePq);
    constants.put("maxSignedExecutionPayloadBidSizePq", maxSignedExecutionPayloadBidSizePq);
    constants.put(
        "unregisteredValidatorWithdrawabilityDelay", unregisteredValidatorWithdrawabilityDelay);
    constants.put("attestationSubnetCountPq", attestationSubnetCountPq);
    constants.put("aggregationCutoffBps", aggregationCutoffBps);
    constants.put("aggregateDueBpsPq", aggregateDueBpsPq);
    constants.put("aggregatorDelayBps", aggregatorDelayBps);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
