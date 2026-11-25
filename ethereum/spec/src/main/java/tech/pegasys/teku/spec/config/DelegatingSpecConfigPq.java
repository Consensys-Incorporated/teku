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

import java.util.Optional;

public class DelegatingSpecConfigPq extends DelegatingSpecConfigEip8483 implements SpecConfigPq {
  private final SpecConfigPq delegate;

  public DelegatingSpecConfigPq(final SpecConfigPq specConfig) {
    super(specConfig);
    this.delegate = SpecConfigPq.required(specConfig);
  }

  @Override
  public int getMaxCommitteesPerSlotPq() {
    return delegate.getMaxCommitteesPerSlotPq();
  }

  @Override
  public int getMinAttestationInclusionDelayPq() {
    return delegate.getMinAttestationInclusionDelayPq();
  }

  @Override
  public int getMaxStarkProofSize() {
    return delegate.getMaxStarkProofSize();
  }

  @Override
  public int getMaxAttestationsPq() {
    return delegate.getMaxAttestationsPq();
  }

  @Override
  public int getMaxValidatorPreregistrationRequestsPerPayload() {
    return delegate.getMaxValidatorPreregistrationRequestsPerPayload();
  }

  @Override
  public int getMaxBuilderPreregistrationRequestsPerPayload() {
    return delegate.getMaxBuilderPreregistrationRequestsPerPayload();
  }

  @Override
  public int getMaxVoluntaryExitRequestsPerPayload() {
    return delegate.getMaxVoluntaryExitRequestsPerPayload();
  }

  @Override
  public int getPreregistrationsLimit() {
    return delegate.getPreregistrationsLimit();
  }

  @Override
  public int getPreregistrationExpirySlots() {
    return delegate.getPreregistrationExpirySlots();
  }

  @Override
  public int getMaxSignedAggregateAndProofSizePq() {
    return delegate.getMaxSignedAggregateAndProofSizePq();
  }

  @Override
  public int getMaxAttesterSlashingSizePq() {
    return delegate.getMaxAttesterSlashingSizePq();
  }

  @Override
  public int getMaxSignedExecutionPayloadBidSizePq() {
    return delegate.getMaxSignedExecutionPayloadBidSizePq();
  }

  @Override
  public int getUnregisteredValidatorWithdrawabilityDelay() {
    return delegate.getUnregisteredValidatorWithdrawabilityDelay();
  }

  @Override
  public int getAttestationSubnetCountPq() {
    return delegate.getAttestationSubnetCountPq();
  }

  @Override
  public int getAggregationCutoffBps() {
    return delegate.getAggregationCutoffBps();
  }

  @Override
  public int getAggregateDueBpsPq() {
    return delegate.getAggregateDueBpsPq();
  }

  @Override
  public int getAggregatorDelayBps() {
    return delegate.getAggregatorDelayBps();
  }

  @Override
  public int getXMaxCommitteeCountPerSlot() {
    return delegate.getXMaxCommitteeCountPerSlot();
  }

  @Override
  public Optional<SpecConfigPq> toVersionPq() {
    return Optional.of(this);
  }
}
