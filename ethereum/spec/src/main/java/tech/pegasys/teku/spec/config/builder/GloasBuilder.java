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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.GasLimitScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigGloasImpl;

public class GloasBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigFulu, SpecConfigGloas> {
  private Integer aggregateDueBpsGloas;
  private Integer attestationDueBpsGloas;
  private Integer contributionDueBpsGloas;
  private Integer maxRequestPayloads;
  private Integer minBuilderWithdrawabilityDelay;
  private Integer payloadAttestationDueBps;
  private Integer payloadDueBps;
  private Integer syncMessageDueBpsGloas;

  // gloas preset
  private Integer ptcSize;
  private Integer maxPayloadAttestations;
  private Integer maxBuilderDepositRequestsPerPayload;
  private Integer maxBuilderExitRequestsPerPayload;
  private Long builderRegistryLimit;
  private Long builderPendingWithdrawalsLimit;
  private Integer maxBuildersPerWithdrawalsSweep;

  // EIP-8061: Increase exit and consolidation churn
  private Integer churnLimitQuotientGloas;
  private Integer consolidationChurnLimitQuotient;
  private UInt64 maxPerEpochActivationChurnLimitGloas;

  // EIP-8261: Gas limit schedule
  private final List<GasLimitScheduleEntry> gasLimitSchedule = new ArrayList<>();

  GloasBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigGloas> build(
      final SpecConfigAndParent<SpecConfigFulu> specConfigAndParent) {
    verifyGasLimitScheduleEpochs(specConfigAndParent.specConfig().getGloasForkEpoch());
    return SpecConfigAndParent.of(
        new SpecConfigGloasImpl(
            specConfigAndParent.specConfig(),
            aggregateDueBpsGloas,
            attestationDueBpsGloas,
            contributionDueBpsGloas,
            maxBuilderDepositRequestsPerPayload,
            maxBuilderExitRequestsPerPayload,
            builderRegistryLimit,
            builderPendingWithdrawalsLimit,
            maxBuildersPerWithdrawalsSweep,
            maxPayloadAttestations,
            maxRequestPayloads,
            minBuilderWithdrawabilityDelay,
            payloadAttestationDueBps,
            payloadDueBps,
            ptcSize,
            syncMessageDueBpsGloas,
            churnLimitQuotientGloas,
            consolidationChurnLimitQuotient,
            maxPerEpochActivationChurnLimitGloas,
            gasLimitSchedule),
        specConfigAndParent);
  }

  public GloasBuilder aggregateDueBpsGloas(final Integer aggregateDueBpsGloas) {
    checkNotNull(aggregateDueBpsGloas);
    this.aggregateDueBpsGloas = aggregateDueBpsGloas;
    return this;
  }

  public GloasBuilder attestationDueBpsGloas(final Integer attestationDueBpsGloas) {
    checkNotNull(attestationDueBpsGloas);
    this.attestationDueBpsGloas = attestationDueBpsGloas;
    return this;
  }

  public GloasBuilder contributionDueBpsGloas(final Integer contributionDueBpsGloas) {
    checkNotNull(contributionDueBpsGloas);
    this.contributionDueBpsGloas = contributionDueBpsGloas;
    return this;
  }

  public GloasBuilder maxRequestPayloads(final Integer maxRequestPayloads) {
    checkNotNull(maxRequestPayloads);
    this.maxRequestPayloads = maxRequestPayloads;
    return this;
  }

  public GloasBuilder minBuilderWithdrawabilityDelay(final Integer minBuilderWithdrawabilityDelay) {
    checkNotNull(minBuilderWithdrawabilityDelay);
    this.minBuilderWithdrawabilityDelay = minBuilderWithdrawabilityDelay;
    return this;
  }

  public GloasBuilder payloadAttestationDueBps(final Integer payloadAttestationDueBps) {
    checkNotNull(payloadAttestationDueBps);
    this.payloadAttestationDueBps = payloadAttestationDueBps;
    return this;
  }

  public GloasBuilder payloadDueBps(final Integer payloadDueBps) {
    checkNotNull(payloadDueBps);
    this.payloadDueBps = payloadDueBps;
    return this;
  }

  public GloasBuilder syncMessageDueBpsGloas(final Integer syncMessageDueBpsGloas) {
    checkNotNull(syncMessageDueBpsGloas);
    this.syncMessageDueBpsGloas = syncMessageDueBpsGloas;
    return this;
  }

  public GloasBuilder maxPayloadAttestations(final Integer maxPayloadAttestations) {
    checkNotNull(maxPayloadAttestations);
    this.maxPayloadAttestations = maxPayloadAttestations;
    return this;
  }

  public GloasBuilder ptcSize(final Integer ptcSize) {
    checkNotNull(ptcSize);
    this.ptcSize = ptcSize;
    return this;
  }

  public GloasBuilder builderRegistryLimit(final Long builderRegistryLimit) {
    checkNotNull(builderRegistryLimit);
    this.builderRegistryLimit = builderRegistryLimit;
    return this;
  }

  public GloasBuilder builderPendingWithdrawalsLimit(final Long builderPendingWithdrawalsLimit) {
    checkNotNull(builderPendingWithdrawalsLimit);
    this.builderPendingWithdrawalsLimit = builderPendingWithdrawalsLimit;
    return this;
  }

  public GloasBuilder maxBuildersPerWithdrawalsSweep(final Integer maxBuildersPerWithdrawalsSweep) {
    checkNotNull(maxBuildersPerWithdrawalsSweep);
    this.maxBuildersPerWithdrawalsSweep = maxBuildersPerWithdrawalsSweep;
    return this;
  }

  public GloasBuilder maxBuilderDepositRequestsPerPayload(
      final Integer maxBuilderDepositRequestsPerPayload) {
    checkNotNull(maxBuilderDepositRequestsPerPayload);
    this.maxBuilderDepositRequestsPerPayload = maxBuilderDepositRequestsPerPayload;
    return this;
  }

  public GloasBuilder maxBuilderExitRequestsPerPayload(
      final Integer maxBuilderExitRequestsPerPayload) {
    checkNotNull(maxBuilderExitRequestsPerPayload);
    this.maxBuilderExitRequestsPerPayload = maxBuilderExitRequestsPerPayload;
    return this;
  }

  public GloasBuilder churnLimitQuotientGloas(final Integer churnLimitQuotientGloas) {
    checkNotNull(churnLimitQuotientGloas);
    this.churnLimitQuotientGloas = churnLimitQuotientGloas;
    return this;
  }

  public GloasBuilder consolidationChurnLimitQuotient(
      final Integer consolidationChurnLimitQuotient) {
    checkNotNull(consolidationChurnLimitQuotient);
    this.consolidationChurnLimitQuotient = consolidationChurnLimitQuotient;
    return this;
  }

  public GloasBuilder maxPerEpochActivationChurnLimitGloas(
      final UInt64 maxPerEpochActivationChurnLimitGloas) {
    checkNotNull(maxPerEpochActivationChurnLimitGloas);
    this.maxPerEpochActivationChurnLimitGloas = maxPerEpochActivationChurnLimitGloas;
    return this;
  }

  public GloasBuilder gasLimitSchedule(final List<GasLimitScheduleEntry> gasLimitSchedule) {
    checkNotNull(gasLimitSchedule);
    verifyGasLimitSchedule(gasLimitSchedule);
    this.gasLimitSchedule.clear();
    gasLimitSchedule.stream()
        .sorted(Comparator.comparing(GasLimitScheduleEntry::epoch))
        .forEach(this.gasLimitSchedule::add);
    return this;
  }

  /** The schedule is sorted by epoch, so only the lowest epoch needs checking. */
  private void verifyGasLimitScheduleEpochs(final UInt64 gloasForkEpoch) {
    if (gasLimitSchedule.isEmpty() || gloasForkEpoch.equals(SpecConfig.FAR_FUTURE_EPOCH)) {
      // nothing to check, or Gloas is not scheduled yet on this network
      return;
    }
    final UInt64 lowestEpoch = gasLimitSchedule.getFirst().epoch();
    if (lowestEpoch.isLessThan(gloasForkEpoch)) {
      throw new IllegalArgumentException(
          String.format(
              "Gas limit schedule contains an entry for epoch %s, which is before the Gloas fork epoch %s.",
              lowestEpoch, gloasForkEpoch));
    }
  }

  private void verifyGasLimitSchedule(final List<GasLimitScheduleEntry> gasLimitSchedule) {
    final Set<UInt64> seenEpochs = new HashSet<>();
    for (final GasLimitScheduleEntry entry : gasLimitSchedule) {
      if (!seenEpochs.add(entry.epoch())) {
        throw new IllegalArgumentException(
            String.format(
                "There are duplicate entries for epoch %s in gas limit schedule.", entry.epoch()));
      }
    }
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("aggregateDueBpsGloas", aggregateDueBpsGloas);
    constants.put("attestationDueBpsGloas", attestationDueBpsGloas);
    constants.put("contributionDueBpsGloas", contributionDueBpsGloas);
    constants.put("maxRequestPayloads", maxRequestPayloads);
    constants.put("minBuilderWithdrawabilityDelay", minBuilderWithdrawabilityDelay);
    constants.put("payloadAttestationDueBps", payloadAttestationDueBps);
    constants.put("payloadDueBps", payloadDueBps);
    constants.put("syncMessageDueBpsGloas", syncMessageDueBpsGloas);

    constants.put("builderRegistryLimit", builderRegistryLimit);
    constants.put("builderPendingWithdrawalsLimit", builderPendingWithdrawalsLimit);
    constants.put("maxBuildersPerWithdrawalsSweep", maxBuildersPerWithdrawalsSweep);
    constants.put("maxBuilderDepositRequestsPerPayload", maxBuilderDepositRequestsPerPayload);
    constants.put("maxBuilderExitRequestsPerPayload", maxBuilderExitRequestsPerPayload);
    constants.put("ptcSize", ptcSize);
    constants.put("maxPayloadAttestations", maxPayloadAttestations);
    constants.put("churnLimitQuotientGloas", churnLimitQuotientGloas);
    constants.put("consolidationChurnLimitQuotient", consolidationChurnLimitQuotient);
    constants.put("maxPerEpochActivationChurnLimitGloas", maxPerEpochActivationChurnLimitGloas);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
