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

package tech.pegasys.teku.validator.client.duties.attestations;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyFactory;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

public class AttestationDutyFactory
    implements DutyFactory<AttestationProductionDuty, AggregationDuty> {

  private final Spec spec;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorDutyMetrics validatorDutyMetrics;
  private final boolean isDvtEnabled;

  public AttestationDutyFactory(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorDutyMetrics validatorDutyMetrics,
      final boolean isDvtEnabled) {
    this.spec = spec;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorDutyMetrics = validatorDutyMetrics;
    this.isDvtEnabled = isDvtEnabled;
  }

  @Override
  public AttestationProductionDuty createProductionDuty(
      final UInt64 slot, final Validator validator) {
    return new AttestationProductionDuty(
        spec,
        slot,
        forkProvider,
        validatorApiChannel,
        new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedAttestations),
        validatorDutyMetrics);
  }

  @Override
  public AggregationDuty createAggregationDuty(final UInt64 slot, final Validator validator) {
    return new AggregationDuty(
        spec,
        slot,
        validatorApiChannel,
        isDvtEnabled ? new UngroupedAggregators() : new AggregatorsGroupedByCommittee(),
        forkProvider,
        VALIDATOR_LOGGER,
        new BatchAttestationSendingStrategy<>(validatorApiChannel::sendAggregateAndProofs),
        validatorDutyMetrics);
  }

  @Override
  public String getProductionType() {
    return "attestation";
  }

  @Override
  public String getAggregationType() {
    return "aggregate";
  }
}
