/*
 * Copyright Consensys Software Inc., 2022
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

import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public abstract class AbstractDutyScheduler implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final MetricsSystem metricsSystem;
  private final String dutyType;
  private final Spec spec;
  private final DutyLoader<?> epochDutiesScheduler;
  private final int lookAheadEpochs;

  private UInt64 lastProductionSlot;

  protected final NavigableMap<UInt64, PendingDuties> dutiesByEpoch = new ConcurrentSkipListMap<>();
  private Optional<UInt64> currentEpoch = Optional.empty();

  protected AbstractDutyScheduler(
      final MetricsSystem metricsSystem,
      final String dutyType,
      final DutyLoader<?> epochDutiesScheduler,
      final int lookAheadEpochs,
      final Spec spec) {
    this.metricsSystem = metricsSystem;
    this.dutyType = dutyType;
    this.epochDutiesScheduler = epochDutiesScheduler;
    this.lookAheadEpochs = lookAheadEpochs;
    this.spec = spec;
  }

  protected void notifyEpochDuties(
      final BiConsumer<PendingDuties, UInt64> action, final UInt64 slot) {
    final PendingDuties pendingDuties = dutiesByEpoch.get(spec.computeEpochAtSlot(slot));
    if (pendingDuties != null) {
      action.accept(pendingDuties, slot);
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    this.currentEpoch = Optional.of(currentEpoch);
    removePriorEpochs(currentEpoch);
    calculateDuties(currentEpoch);
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    final UInt64 headEpoch = spec.computeEpochAtSlot(slot);
    dutiesByEpoch
        .tailMap(headEpoch, true)
        .forEach(
            (dutyEpoch, duties) ->
                duties.onHeadUpdate(
                    getExpectedDependentRoot(
                        headBlockRoot,
                        previousDutyDependentRoot,
                        currentDutyDependentRoot,
                        headEpoch,
                        dutyEpoch)));
  }

  protected abstract Bytes32 getExpectedDependentRoot(
      Bytes32 headBlockRoot,
      Bytes32 previousDutyDependentRoot,
      Bytes32 currentDutyDependentRoot,
      UInt64 headEpoch,
      UInt64 dutyEpoch);

  @Override
  public void onPossibleMissedEvents() {
    // We may have missed a re-org or head notification so we need to recalculate all duties.
    invalidateEpochs(dutiesByEpoch);
  }

  @Override
  public void onValidatorsAdded() {
    invalidateEpochs(dutiesByEpoch);
  }

  private void calculateDuties(final UInt64 epochNumber) {
    dutiesByEpoch.computeIfAbsent(epochNumber, this::createEpochDuties);
    for (int i = 1; i <= lookAheadEpochs; i++) {
      dutiesByEpoch.computeIfAbsent(epochNumber.plus(i), this::createEpochDuties);
    }
  }

  private PendingDuties createEpochDuties(final UInt64 epochNumber) {
    return PendingDuties.calculateDuties(metricsSystem, epochDutiesScheduler, epochNumber);
  }

  private void removePriorEpochs(final UInt64 epochNumber) {
    final NavigableMap<UInt64, PendingDuties> toRemove = dutiesByEpoch.headMap(epochNumber, false);
    toRemove.values().forEach(PendingDuties::cancel);
    toRemove.clear();
  }

  private void invalidateEpochs(final NavigableMap<UInt64, PendingDuties> toInvalidate) {
    toInvalidate.values().forEach(PendingDuties::recalculate);
  }

  protected Optional<UInt64> getCurrentEpoch() {
    return currentEpoch;
  }

  protected boolean isAbleToVerifyEpoch(final UInt64 slot) {
    if (currentEpoch.isEmpty()) {
      return false;
    }
    final UInt64 signingEpoch = spec.computeEpochAtSlot(slot);
    final UInt64 epoch = currentEpoch.get();
    return !signingEpoch.isGreaterThan(epoch.plus(lookAheadEpochs + 1));
  }

  protected void onProductionDue(final UInt64 slot) {
    // Check slot being null for the edge case of genesis slot (i.e. slot 0)
    if (lastProductionSlot != null && slot.compareTo(lastProductionSlot) <= 0) {
      LOG.debug(
          "Not performing {} duties for slot {} because last production slot {} is equal or beyond that.",
          dutyType,
          slot,
          lastProductionSlot);
      return;
    }

    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "Not performing {} duties for slot {} because epoch {} is too far ahead of the current epoch {}",
          dutyType,
          slot,
          spec.computeEpochAtSlot(slot),
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"));
      return;
    }

    LOG.debug(
        "Performing {} duties for slot {}, last production slot {}",
        dutyType,
        slot,
        lastProductionSlot);

    lastProductionSlot = slot;
    notifyEpochDuties(PendingDuties::onProductionDue, slot);
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    if (!isAbleToVerifyEpoch(slot)) {
      LOG.info(
          "Not performing {} aggregation duties for slot {} because epoch {} is too far ahead of the current epoch {}",
          dutyType,
          slot,
          spec.computeEpochAtSlot(slot),
          getCurrentEpoch().map(UInt64::toString).orElse("UNDEFINED"));
      return;
    }

    notifyEpochDuties(PendingDuties::onAggregationDue, slot);
  }
}
