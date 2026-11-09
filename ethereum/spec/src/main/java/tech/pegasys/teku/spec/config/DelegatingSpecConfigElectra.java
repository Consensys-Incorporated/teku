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

public class DelegatingSpecConfigElectra extends DelegatingSpecConfigDeneb
    implements SpecConfigElectra {
  private final SpecConfigElectra specConfigElectra;

  public DelegatingSpecConfigElectra(final SpecConfigElectra specConfig) {
    super(specConfig);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public UInt64 getMinPerEpochChurnLimitElectra() {
    return specConfigElectra.getMinPerEpochChurnLimitElectra();
  }

  @Override
  public UInt64 getMinActivationBalance() {
    return specConfigElectra.getMinActivationBalance();
  }

  @Override
  public UInt64 getMaxEffectiveBalanceElectra() {
    return specConfigElectra.getMaxEffectiveBalanceElectra();
  }

  @Override
  public int getPendingDepositsLimit() {
    return specConfigElectra.getPendingDepositsLimit();
  }

  @Override
  public int getPendingPartialWithdrawalsLimit() {
    return specConfigElectra.getPendingPartialWithdrawalsLimit();
  }

  @Override
  public int getPendingConsolidationsLimit() {
    return specConfigElectra.getPendingConsolidationsLimit();
  }

  @Override
  public int getMinSlashingPenaltyQuotientElectra() {
    return specConfigElectra.getMinSlashingPenaltyQuotientElectra();
  }

  @Override
  public int getWhistleblowerRewardQuotientElectra() {
    return specConfigElectra.getWhistleblowerRewardQuotientElectra();
  }

  @Override
  public int getMaxAttesterSlashingsElectra() {
    return specConfigElectra.getMaxAttesterSlashingsElectra();
  }

  @Override
  public int getMaxAttestations() {
    return getMaxAttestationsElectra();
  }

  @Override
  public int getMaxAttestationsElectra() {
    return specConfigElectra.getMaxAttestationsElectra();
  }

  @Override
  public int getMaxConsolidationRequestsPerPayload() {
    return specConfigElectra.getMaxConsolidationRequestsPerPayload();
  }

  @Override
  public int getMaxDepositRequestsPerPayload() {
    return specConfigElectra.getMaxDepositRequestsPerPayload();
  }

  @Override
  public int getMaxWithdrawalRequestsPerPayload() {
    return specConfigElectra.getMaxWithdrawalRequestsPerPayload();
  }

  @Override
  public int getMaxPendingPartialsPerWithdrawalsSweep() {
    return specConfigElectra.getMaxPendingPartialsPerWithdrawalsSweep();
  }

  @Override
  public int getMaxPendingDepositsPerEpoch() {
    return specConfigElectra.getMaxPendingDepositsPerEpoch();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DelegatingSpecConfigElectra that = (DelegatingSpecConfigElectra) o;
    return Objects.equals(specConfigElectra, that.specConfigElectra);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specConfigElectra);
  }
}
