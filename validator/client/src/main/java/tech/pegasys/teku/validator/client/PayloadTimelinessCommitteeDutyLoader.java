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

package tech.pegasys.teku.validator.client;

import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.payloadattestations.PayloadAttestationProductionDuty;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class PayloadTimelinessCommitteeDutyLoader
    extends AbstractDutyLoader<PayloadTimelinessCommitteeDuties, SlotBasedScheduledDuties<?, ?>> {

  private final ValidatorApiChannel validatorApiChannel;
  private final Function<Bytes32, SlotBasedScheduledDuties<PayloadAttestationProductionDuty, Duty>>
      scheduledDutiesFactory;

  public PayloadTimelinessCommitteeDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final Function<Bytes32, SlotBasedScheduledDuties<PayloadAttestationProductionDuty, Duty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
  }

  @Override
  protected SafeFuture<Optional<PayloadTimelinessCommitteeDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getPayloadTimelinessCommitteeDuties(epoch, validatorIndices);
  }

  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final PayloadTimelinessCommitteeDuties duties) {
    final SlotBasedScheduledDuties<PayloadAttestationProductionDuty, Duty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.dependentRoot());

    duties.duties().forEach(duty -> scheduleDuty(scheduledDuties, duty));

    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final SlotBasedScheduledDuties<PayloadAttestationProductionDuty, Duty> scheduledDuties,
      final PtcDuty duty) {
    validators
        .getValidator(duty.publicKey())
        .ifPresent(
            validator ->
                scheduledDuties.scheduleProduction(
                    duty.slot(),
                    validator,
                    d -> {
                      d.addValidator(validator, duty.validatorIndex());
                      return null;
                    }));
  }

  @Override
  public String getDutyType() {
    return "PayloadTimelinessCommitteeDuties";
  }
}
