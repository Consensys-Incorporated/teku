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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigElectraImpl;

public class ElectraBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigDeneb, SpecConfigElectra> {
  private static final Logger LOG = LogManager.getLogger();

  private UInt64 minPerEpochChurnLimitElectra;

  private UInt64 minActivationBalance;
  private UInt64 maxEffectiveBalanceElectra;
  private Integer minSlashingPenaltyQuotientElectra;
  private Integer whistleblowerRewardQuotientElectra;
  private Integer pendingDepositsLimit;
  private Integer pendingPartialWithdrawalsLimit;
  private Integer pendingConsolidationsLimit;
  private Integer maxAttesterSlashingsElectra;
  private Integer maxAttestationsElectra;
  private Integer maxDepositRequestsPerPayload;
  private Integer maxWithdrawalRequestsPerPayload;
  private Integer maxConsolidationRequestsPerPayload;
  private Integer maxPendingPartialsPerWithdrawalsSweep;
  private Integer maxPendingDepositsPerEpoch;
  private Integer maxBlobsPerBlockElectra;
  private Integer maxRequestBlobSidecarsElectra;
  private Integer blobSidecarSubnetCountElectra;

  ElectraBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigElectra> build(
      final SpecConfigAndParent<SpecConfigDeneb> specConfigAndParent) {
    if (maxBlobsPerBlockElectra != null) {
      final Integer newMaxRequestBlobSidecarsElectra =
          computeMaxRequestBlobSidecars(
              specConfigAndParent.specConfig().getMaxRequestBlocksDeneb());
      LOG.debug(
          "Setting maxRequestBlobSidecarsElectra to {} (was {})",
          newMaxRequestBlobSidecarsElectra,
          maxRequestBlobSidecarsElectra);
      maxRequestBlobSidecarsElectra = newMaxRequestBlobSidecarsElectra;
    }
    return SpecConfigAndParent.of(
        new SpecConfigElectraImpl(
            specConfigAndParent.specConfig(),
            minPerEpochChurnLimitElectra,
            minActivationBalance,
            maxEffectiveBalanceElectra,
            minSlashingPenaltyQuotientElectra,
            whistleblowerRewardQuotientElectra,
            pendingDepositsLimit,
            pendingPartialWithdrawalsLimit,
            pendingConsolidationsLimit,
            maxAttesterSlashingsElectra,
            maxAttestationsElectra,
            maxDepositRequestsPerPayload,
            maxWithdrawalRequestsPerPayload,
            maxConsolidationRequestsPerPayload,
            maxPendingPartialsPerWithdrawalsSweep,
            maxPendingDepositsPerEpoch,
            maxBlobsPerBlockElectra,
            maxRequestBlobSidecarsElectra,
            blobSidecarSubnetCountElectra),
        specConfigAndParent);
  }

  public ElectraBuilder minPerEpochChurnLimitElectra(final UInt64 minPerEpochChurnLimitElectra) {
    checkNotNull(minPerEpochChurnLimitElectra);
    this.minPerEpochChurnLimitElectra = minPerEpochChurnLimitElectra;
    return this;
  }

  public ElectraBuilder minActivationBalance(final UInt64 minActivationBalance) {
    checkNotNull(minActivationBalance);
    this.minActivationBalance = minActivationBalance;
    return this;
  }

  public ElectraBuilder maxEffectiveBalanceElectra(final UInt64 maxEffectiveBalanceElectra) {
    checkNotNull(maxEffectiveBalanceElectra);
    this.maxEffectiveBalanceElectra = maxEffectiveBalanceElectra;
    return this;
  }

  public ElectraBuilder minSlashingPenaltyQuotientElectra(
      final Integer minSlashingPenaltyQuotientElectra) {
    checkNotNull(minSlashingPenaltyQuotientElectra);
    this.minSlashingPenaltyQuotientElectra = minSlashingPenaltyQuotientElectra;
    return this;
  }

  public ElectraBuilder whistleblowerRewardQuotientElectra(
      final Integer whistleblowerRewardQuotientElectra) {
    checkNotNull(whistleblowerRewardQuotientElectra);
    this.whistleblowerRewardQuotientElectra = whistleblowerRewardQuotientElectra;
    return this;
  }

  public ElectraBuilder pendingDepositsLimit(final Integer pendingDepositsLimit) {
    checkNotNull(pendingDepositsLimit);
    this.pendingDepositsLimit = pendingDepositsLimit;
    return this;
  }

  public ElectraBuilder pendingPartialWithdrawalsLimit(
      final Integer pendingPartialWithdrawalsLimit) {
    checkNotNull(pendingPartialWithdrawalsLimit);
    this.pendingPartialWithdrawalsLimit = pendingPartialWithdrawalsLimit;
    return this;
  }

  public ElectraBuilder pendingConsolidationsLimit(final Integer pendingConsolidationsLimit) {
    checkNotNull(pendingConsolidationsLimit);
    this.pendingConsolidationsLimit = pendingConsolidationsLimit;
    return this;
  }

  public ElectraBuilder maxAttesterSlashingsElectra(final Integer maxAttesterSlashingsElectra) {
    checkNotNull(maxAttesterSlashingsElectra);
    this.maxAttesterSlashingsElectra = maxAttesterSlashingsElectra;
    return this;
  }

  public ElectraBuilder maxAttestationsElectra(final Integer maxAttestationsElectra) {
    checkNotNull(maxAttestationsElectra);
    this.maxAttestationsElectra = maxAttestationsElectra;
    return this;
  }

  public ElectraBuilder maxDepositRequestsPerPayload(final Integer maxDepositRequestsPerPayload) {
    checkNotNull(maxDepositRequestsPerPayload);
    this.maxDepositRequestsPerPayload = maxDepositRequestsPerPayload;
    return this;
  }

  public ElectraBuilder maxWithdrawalRequestsPerPayload(
      final Integer maxWithdrawalRequestsPerPayload) {
    checkNotNull(maxWithdrawalRequestsPerPayload);
    this.maxWithdrawalRequestsPerPayload = maxWithdrawalRequestsPerPayload;
    return this;
  }

  public ElectraBuilder maxConsolidationRequestsPerPayload(
      final Integer maxConsolidationRequestsPerPayload) {
    checkNotNull(maxConsolidationRequestsPerPayload);
    this.maxConsolidationRequestsPerPayload = maxConsolidationRequestsPerPayload;
    return this;
  }

  public ElectraBuilder maxPendingPartialsPerWithdrawalsSweep(
      final Integer maxPendingPartialsPerWithdrawalsSweep) {
    checkNotNull(maxPendingPartialsPerWithdrawalsSweep);
    this.maxPendingPartialsPerWithdrawalsSweep = maxPendingPartialsPerWithdrawalsSweep;
    return this;
  }

  public ElectraBuilder maxPendingDepositsPerEpoch(final Integer maxPendingDepositsPerEpoch) {
    checkNotNull(maxPendingDepositsPerEpoch);
    this.maxPendingDepositsPerEpoch = maxPendingDepositsPerEpoch;
    return this;
  }

  public ElectraBuilder maxBlobsPerBlockElectra(final Integer maxBlobsPerBlockElectra) {
    checkNotNull(maxBlobsPerBlockElectra);
    this.maxBlobsPerBlockElectra = maxBlobsPerBlockElectra;
    return this;
  }

  @Deprecated
  public ElectraBuilder maxRequestBlobSidecarsElectra(final Integer maxRequestBlobSidecarsElectra) {
    checkNotNull(maxRequestBlobSidecarsElectra);
    this.maxRequestBlobSidecarsElectra = maxRequestBlobSidecarsElectra;
    return this;
  }

  @Deprecated
  public ElectraBuilder blobSidecarSubnetCountElectra(final Integer blobSidecarSubnetCountElectra) {
    checkNotNull(blobSidecarSubnetCountElectra);
    this.blobSidecarSubnetCountElectra = blobSidecarSubnetCountElectra;
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("minPerEpochChurnLimitElectra", minPerEpochChurnLimitElectra);
    constants.put("minActivationBalance", minActivationBalance);
    constants.put("maxEffectiveBalanceElectra", maxEffectiveBalanceElectra);
    constants.put("pendingDepositsLimit", pendingDepositsLimit);
    constants.put("pendingPartialWithdrawalsLimit", pendingPartialWithdrawalsLimit);
    constants.put("pendingConsolidationsLimit", pendingConsolidationsLimit);
    constants.put("minSlashingPenaltyQuotientElectra", minSlashingPenaltyQuotientElectra);
    constants.put("whistleblowerRewardQuotientElectra", whistleblowerRewardQuotientElectra);
    constants.put("maxAttesterSlashingsElectra", maxAttesterSlashingsElectra);
    constants.put("maxAttestationsElectra", maxAttestationsElectra);
    constants.put("maxConsolidationRequestsPerPayload", maxConsolidationRequestsPerPayload);
    constants.put("maxDepositRequestsPerPayload", maxDepositRequestsPerPayload);
    constants.put("maxWithdrawalRequestsPerPayload", maxWithdrawalRequestsPerPayload);
    constants.put("maxPendingPartialsPerWithdrawalsSweep", maxPendingPartialsPerWithdrawalsSweep);
    constants.put("maxPendingDepositsPerEpoch", maxPendingDepositsPerEpoch);
    constants.put("maxBlobsPerBlockElectra", maxBlobsPerBlockElectra);
    constants.put("blobSidecarSubnetCountElectra", blobSidecarSubnetCountElectra);

    return constants;
  }

  // compute_max_request_blob_sidecars
  private Integer computeMaxRequestBlobSidecars(final Integer maxRequestBlocksDeneb) {
    return computeMaxRequestBlobSidecars(maxRequestBlocksDeneb, maxBlobsPerBlockElectra);
  }

  public static Integer computeMaxRequestBlobSidecars(
      final Integer maxRequestBlocksDeneb, final Integer maxBlobsPerBlockElectra) {
    return maxRequestBlocksDeneb * maxBlobsPerBlockElectra;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
