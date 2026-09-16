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
import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DelegatingSpecConfigGloas extends DelegatingSpecConfigFulu implements SpecConfigGloas {
  private final SpecConfigGloas delegate;

  public DelegatingSpecConfigGloas(final SpecConfigGloas specConfig) {
    super(specConfig);
    this.delegate = SpecConfigGloas.required(specConfig);
  }

  @Override
  public int getAggregateDueBps() {
    return delegate.getAggregateDueBps();
  }

  @Override
  public int getAttestationDueBps() {
    return delegate.getAttestationDueBps();
  }

  @Override
  public int getContributionDueBps() {
    return delegate.getContributionDueBps();
  }

  @Override
  public int getMaxRequestPayloads() {
    return delegate.getMaxRequestPayloads();
  }

  @Override
  public int getMaxSignedAggregateAndProofSize() {
    return delegate.getMaxSignedAggregateAndProofSize();
  }

  @Override
  public int getMaxAttesterSlashingSize() {
    return delegate.getMaxAttesterSlashingSize();
  }

  @Override
  public int getMaxSignedExecutionPayloadBidSize() {
    return delegate.getMaxSignedExecutionPayloadBidSize();
  }

  @Override
  public int getMinBuilderWithdrawabilityDelay() {
    return delegate.getMinBuilderWithdrawabilityDelay();
  }

  @Override
  public int getPayloadAttestationDueBps() {
    return delegate.getPayloadAttestationDueBps();
  }

  @Override
  public int getPayloadDueBps() {
    return delegate.getPayloadDueBps();
  }

  @Override
  public int getPtcSize() {
    return delegate.getPtcSize();
  }

  @Override
  public int getMaxPayloadAttestations() {
    return delegate.getMaxPayloadAttestations();
  }

  @Override
  public int getMaxBuilderDepositRequestsPerPayload() {
    return delegate.getMaxBuilderDepositRequestsPerPayload();
  }

  @Override
  public int getMaxBuilderExitRequestsPerPayload() {
    return delegate.getMaxBuilderExitRequestsPerPayload();
  }

  @Override
  public long getBuilderRegistryLimit() {
    return delegate.getBuilderRegistryLimit();
  }

  @Override
  public long getBuilderPendingWithdrawalsLimit() {
    return delegate.getBuilderPendingWithdrawalsLimit();
  }

  @Override
  public int getMaxBuildersPerWithdrawalsSweep() {
    return delegate.getMaxBuildersPerWithdrawalsSweep();
  }

  @Override
  public int getChurnLimitQuotientGloas() {
    return delegate.getChurnLimitQuotientGloas();
  }

  @Override
  public int getConsolidationChurnLimitQuotient() {
    return delegate.getConsolidationChurnLimitQuotient();
  }

  @Override
  public UInt64 getMaxPerEpochActivationChurnLimitGloas() {
    return delegate.getMaxPerEpochActivationChurnLimitGloas();
  }

  @Override
  public List<GasLimitScheduleEntry> getGasLimitSchedule() {
    return delegate.getGasLimitSchedule();
  }

  @Override
  public Optional<SpecConfigGloas> toVersionGloas() {
    return delegate.toVersionGloas();
  }

  @Override
  public int getSyncMessageDueBps() {
    return delegate.getSyncMessageDueBps();
  }
}
