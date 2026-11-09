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

public class SpecConfigElectraImpl extends DelegatingSpecConfigDeneb implements SpecConfigElectra {

  private final UInt64 minPerEpochChurnLimitElectra;

  private final UInt64 minActivationBalance;
  private final UInt64 maxEffectiveBalanceElectra;
  private final int minSlashingPenaltyQuotientElectra;
  private final int whistleblowerRewardQuotientElectra;
  private final int pendingDepositsLimit;
  private final int pendingPartialWithdrawalsLimit;
  private final int pendingConsolidationsLimit;
  private final int maxAttesterSlashingsElectra;
  private final int maxAttestationsElectra;
  private final int maxDepositRequestsPerPayload;
  private final int maxWithdrawalRequestsPerPayload;
  private final int maxConsolidationRequestsPerPayload;
  private final int maxPendingPartialsPerWithdrawalsSweep;
  private final int maxPendingDepositsPerEpoch;
  private final int maxBlobsPerBlockElectra;
  private final int maxRequestBlobSidecarsElectra;
  private final int blobSidecarSubnetCountElectra;

  public SpecConfigElectraImpl(
      final SpecConfigDeneb specConfig,
      final UInt64 minPerEpochChurnLimitElectra,
      final UInt64 minActivationBalance,
      final UInt64 maxEffectiveBalanceElectra,
      final int minSlashingPenaltyQuotientElectra,
      final int whistleblowerRewardQuotientElectra,
      final int pendingDepositsLimit,
      final int pendingPartialWithdrawalsLimit,
      final int pendingConsolidationsLimit,
      final int maxAttesterSlashingsElectra,
      final int maxAttestationsElectra,
      final int maxDepositRequestsPerPayload,
      final int maxWithdrawalRequestsPerPayload,
      final int maxConsolidationRequestsPerPayload,
      final int maxPendingPartialsPerWithdrawalsSweep,
      final int maxPendingDepositsPerEpoch,
      final int maxBlobsPerBlockElectra,
      final int maxRequestBlobSidecarsElectra,
      final int blobSidecarSubnetCountElectra) {
    super(specConfig);
    this.minPerEpochChurnLimitElectra = minPerEpochChurnLimitElectra;
    this.minActivationBalance = minActivationBalance;
    this.maxEffectiveBalanceElectra = maxEffectiveBalanceElectra;
    this.minSlashingPenaltyQuotientElectra = minSlashingPenaltyQuotientElectra;
    this.whistleblowerRewardQuotientElectra = whistleblowerRewardQuotientElectra;
    this.pendingDepositsLimit = pendingDepositsLimit;
    this.pendingPartialWithdrawalsLimit = pendingPartialWithdrawalsLimit;
    this.pendingConsolidationsLimit = pendingConsolidationsLimit;
    this.maxAttesterSlashingsElectra = maxAttesterSlashingsElectra;
    this.maxAttestationsElectra = maxAttestationsElectra;
    this.maxDepositRequestsPerPayload = maxDepositRequestsPerPayload;
    this.maxWithdrawalRequestsPerPayload = maxWithdrawalRequestsPerPayload;
    this.maxConsolidationRequestsPerPayload = maxConsolidationRequestsPerPayload;
    this.maxPendingPartialsPerWithdrawalsSweep = maxPendingPartialsPerWithdrawalsSweep;
    this.maxPendingDepositsPerEpoch = maxPendingDepositsPerEpoch;
    this.maxBlobsPerBlockElectra = maxBlobsPerBlockElectra;
    this.maxRequestBlobSidecarsElectra = maxRequestBlobSidecarsElectra;
    this.blobSidecarSubnetCountElectra = blobSidecarSubnetCountElectra;
  }

  @Override
  public UInt64 getMinPerEpochChurnLimitElectra() {
    return minPerEpochChurnLimitElectra;
  }

  @Override
  public UInt64 getMinActivationBalance() {
    return minActivationBalance;
  }

  @Override
  public UInt64 getMaxEffectiveBalanceElectra() {
    return maxEffectiveBalanceElectra;
  }

  @Override
  public int getMinSlashingPenaltyQuotientElectra() {
    return minSlashingPenaltyQuotientElectra;
  }

  @Override
  public int getWhistleblowerRewardQuotientElectra() {
    return whistleblowerRewardQuotientElectra;
  }

  @Override
  public int getPendingDepositsLimit() {
    return pendingDepositsLimit;
  }

  @Override
  public int getPendingPartialWithdrawalsLimit() {
    return pendingPartialWithdrawalsLimit;
  }

  @Override
  public int getPendingConsolidationsLimit() {
    return pendingConsolidationsLimit;
  }

  @Override
  public int getMaxAttesterSlashingsElectra() {
    return maxAttesterSlashingsElectra;
  }

  @Override
  public int getMaxAttestationsElectra() {
    return maxAttestationsElectra;
  }

  @Override
  public int getMaxDepositRequestsPerPayload() {
    return maxDepositRequestsPerPayload;
  }

  @Override
  public int getMaxWithdrawalRequestsPerPayload() {
    return maxWithdrawalRequestsPerPayload;
  }

  @Override
  public int getMaxConsolidationRequestsPerPayload() {
    return maxConsolidationRequestsPerPayload;
  }

  @Override
  public int getMaxPendingPartialsPerWithdrawalsSweep() {
    return maxPendingPartialsPerWithdrawalsSweep;
  }

  @Override
  public int getMaxPendingDepositsPerEpoch() {
    return maxPendingDepositsPerEpoch;
  }

  @Override
  public int getMaxBlobsPerBlock() {
    return maxBlobsPerBlockElectra;
  }

  @Override
  public int getBlobSidecarSubnetCount() {
    return blobSidecarSubnetCountElectra;
  }

  @Override
  public int getMaxRequestBlobSidecars() {
    return maxRequestBlobSidecarsElectra;
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.ELECTRA;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigElectraImpl that = (SpecConfigElectraImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(minPerEpochChurnLimitElectra, that.minPerEpochChurnLimitElectra)
        && Objects.equals(minActivationBalance, that.minActivationBalance)
        && Objects.equals(maxEffectiveBalanceElectra, that.maxEffectiveBalanceElectra)
        && minSlashingPenaltyQuotientElectra == that.minSlashingPenaltyQuotientElectra
        && whistleblowerRewardQuotientElectra == that.whistleblowerRewardQuotientElectra
        && pendingDepositsLimit == that.pendingDepositsLimit
        && pendingPartialWithdrawalsLimit == that.pendingPartialWithdrawalsLimit
        && pendingConsolidationsLimit == that.pendingConsolidationsLimit
        && maxAttesterSlashingsElectra == that.maxAttesterSlashingsElectra
        && maxAttestationsElectra == that.maxAttestationsElectra
        && maxDepositRequestsPerPayload == that.maxDepositRequestsPerPayload
        && maxWithdrawalRequestsPerPayload == that.maxWithdrawalRequestsPerPayload
        && maxConsolidationRequestsPerPayload == that.maxConsolidationRequestsPerPayload
        && maxPendingPartialsPerWithdrawalsSweep == that.maxPendingPartialsPerWithdrawalsSweep
        && maxPendingDepositsPerEpoch == that.maxPendingDepositsPerEpoch
        && maxBlobsPerBlockElectra == that.maxBlobsPerBlockElectra
        && maxRequestBlobSidecarsElectra == that.maxRequestBlobSidecarsElectra
        && blobSidecarSubnetCountElectra == that.blobSidecarSubnetCountElectra;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
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
        blobSidecarSubnetCountElectra);
  }
}
