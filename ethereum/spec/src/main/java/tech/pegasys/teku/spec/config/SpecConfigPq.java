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

/**
 * Config for the PQ fork (consensus-specs {@code pq} branch). PQ builds on EIP-8483; on this branch
 * Heze builds on PQ.
 */
public interface SpecConfigPq extends SpecConfigEip8483 {

  static SpecConfigPq required(final SpecConfig specConfig) {
    return specConfig
        .toVersionPq()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected PQ spec config but got: " + specConfig.getClass().getSimpleName()));
  }

  int getMaxCommitteesPerSlotPq();

  int getMinAttestationInclusionDelayPq();

  int getMaxStarkProofSize();

  int getMaxAttestationsPq();

  int getMaxValidatorPreregistrationRequestsPerPayload();

  int getMaxBuilderPreregistrationRequestsPerPayload();

  int getMaxVoluntaryExitRequestsPerPayload();

  int getPreregistrationsLimit();

  int getPreregistrationExpirySlots();

  int getMaxSignedAggregateAndProofSizePq();

  int getMaxAttesterSlashingSizePq();

  int getMaxSignedExecutionPayloadBidSizePq();

  int getUnregisteredValidatorWithdrawabilityDelay();

  int getAttestationSubnetCountPq();

  @Override
  Optional<SpecConfigPq> toVersionPq();
}
