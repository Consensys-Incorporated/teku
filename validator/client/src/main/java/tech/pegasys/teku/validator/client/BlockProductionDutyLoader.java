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
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BlockProductionDutyLoader
    extends AbstractDutyLoader<ProposerDuties, SlotBasedScheduledDuties<?, ?>> {

  private final ValidatorApiChannel validatorApiChannel;
  private final Function<Bytes32, SlotBasedScheduledDuties<BlockProductionDuty, Duty>>
      scheduledDutiesFactory;
  private final ValidatorTimingChannel validatorTimingChannelPublisher;

  protected BlockProductionDutyLoader(
      final ValidatorApiChannel validatorApiChannel,
      final Function<Bytes32, SlotBasedScheduledDuties<BlockProductionDuty, Duty>>
          scheduledDutiesFactory,
      final OwnedValidators validators,
      final ValidatorIndexProvider validatorIndexProvider,
      final ValidatorTimingChannel validatorTimingChannelPublisher) {
    super(validators, validatorIndexProvider);
    this.validatorApiChannel = validatorApiChannel;
    this.scheduledDutiesFactory = scheduledDutiesFactory;
    this.validatorTimingChannelPublisher = validatorTimingChannelPublisher;
  }

  @Override
  protected SafeFuture<Optional<ProposerDuties>> requestDuties(
      final UInt64 epoch, final IntCollection validatorIndices) {
    if (validatorIndices.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return validatorApiChannel.getProposerDuties(epoch, true);
  }

  @Override
  protected SafeFuture<SlotBasedScheduledDuties<?, ?>> scheduleAllDuties(
      final UInt64 epoch, final ProposerDuties duties) {
    validatorTimingChannelPublisher.onProposerDutiesLoaded(epoch, duties);
    final SlotBasedScheduledDuties<BlockProductionDuty, Duty> scheduledDuties =
        scheduledDutiesFactory.apply(duties.getDependentRoot());
    duties.getDuties().forEach(duty -> scheduleDuty(scheduledDuties, duty));
    return SafeFuture.completedFuture(scheduledDuties);
  }

  private void scheduleDuty(
      final SlotBasedScheduledDuties<BlockProductionDuty, Duty> scheduledDuties,
      final ProposerDuty duty) {
    validators
        .getValidator(duty.getPublicKey())
        .ifPresent(validator -> scheduledDuties.scheduleProduction(duty.getSlot(), validator));
  }

  @Override
  public String getDutyType() {
    return "Block Production";
  }
}
