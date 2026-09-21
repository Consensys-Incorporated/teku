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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

public class ValidatorTimingActions implements ValidatorTimingChannel {
  private final ValidatorIndexProvider validatorIndexProvider;
  private final Collection<ValidatorTimingChannel> delegates;
  private final Spec spec;

  private final SettableGauge validatorCurrentEpoch;

  private final Optional<SlashingRiskAction> maybeValidatorSlashedAction;

  public ValidatorTimingActions(
      final ValidatorIndexProvider validatorIndexProvider,
      final Collection<ValidatorTimingChannel> delegates,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final Optional<SlashingRiskAction> maybeValidatorSlashedAction) {
    this.validatorIndexProvider = validatorIndexProvider;
    this.delegates = delegates;
    this.spec = spec;
    this.maybeValidatorSlashedAction = maybeValidatorSlashedAction;

    this.validatorCurrentEpoch =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "current_epoch",
            "Current epoch of the validator client");
  }

  @Override
  public void onSlot(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onSlot(slot));
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    validatorCurrentEpoch.set(epoch.doubleValue());
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(epoch);
    if (slot.equals(firstSlotOfEpoch.plus(1))) {
      validatorIndexProvider.lookupValidators();
    }
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    delegates.forEach(
        delegate ->
            delegate.onHeadUpdate(
                slot, previousDutyDependentRoot, currentDutyDependentRoot, headBlockRoot));
  }

  @Override
  public void onPossibleMissedEvents() {
    delegates.forEach(ValidatorTimingChannel::onPossibleMissedEvents);
  }

  @Override
  public void onValidatorsAdded() {
    delegates.forEach(ValidatorTimingChannel::onValidatorsAdded);
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onBlockProductionDue(slot));
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onAttestationCreationDue(slot));
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    delegates.forEach(delegates -> delegates.onAttestationAggregationDue(slot));
  }

  @Override
  public void onSyncCommitteeCreationDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onSyncCommitteeCreationDue(slot));
  }

  @Override
  public void onContributionCreationDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onContributionCreationDue(slot));
  }

  @Override
  public void onPayloadAttestationCreationDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onPayloadAttestationCreationDue(slot));
  }

  @Override
  public void onInclusionListCreationDue(final UInt64 slot) {
    delegates.forEach(delegate -> delegate.onInclusionListCreationDue(slot));
  }

  @Override
  public void onInclusionListDue(final UInt64 slot) {
    delegates.forEach(delegates -> delegates.onInclusionListDue(slot));
  }

  @Override
  public void onAttesterSlashing(final AttesterSlashing attesterSlashing) {
    delegates.forEach(delegates -> delegates.onAttesterSlashing(attesterSlashing));
    maybeValidatorSlashedAction.ifPresent(
        validatorSlashingAction -> {
          final Set<UInt64> slashedIndices = attesterSlashing.getIntersectingValidatorIndices();
          final List<BLSPublicKey> slashedPublicKeys =
              slashedIndices.stream()
                  .map(slashedIndex -> validatorIndexProvider.getPublicKey(slashedIndex.intValue()))
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .collect(Collectors.toList());
          validatorSlashingAction.perform(slashedPublicKeys);
        });
  }

  @Override
  public void onProposerSlashing(final ProposerSlashing proposerSlashing) {
    delegates.forEach(delegates -> delegates.onProposerSlashing(proposerSlashing));
    maybeValidatorSlashedAction.ifPresent(
        validatorSlashedAction -> {
          final UInt64 slashedIndex = proposerSlashing.getHeader1().getMessage().getProposerIndex();
          validatorIndexProvider
              .getPublicKey(slashedIndex.intValue())
              .ifPresent(validatorSlashedAction::perform);
        });
  }

  @Override
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {
    delegates.forEach(
        delegates ->
            delegates.onUpdatedValidatorStatuses(newValidatorStatuses, possibleMissingEvents));
    maybeValidatorSlashedAction.ifPresent(
        validatorSlashedAction ->
            validatorSlashedAction.perform(getSlashedOwnedValidatorsPubKeys(newValidatorStatuses)));
  }

  private List<BLSPublicKey> getSlashedOwnedValidatorsPubKeys(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses) {
    return newValidatorStatuses.entrySet().stream()
        .filter(
            validatorStatusEntry ->
                validatorStatusEntry.getValue().equals(ValidatorStatus.exited_slashed)
                    || validatorStatusEntry.getValue().equals(ValidatorStatus.active_slashed))
        .map(Map.Entry::getKey)
        .filter(validatorIndexProvider::containsPublicKey)
        .toList();
  }

  @Override
  public void onProposerDutiesLoaded(final UInt64 epoch, final ProposerDuties proposerDuties) {
    delegates.forEach(delegate -> delegate.onProposerDutiesLoaded(epoch, proposerDuties));
  }
}
