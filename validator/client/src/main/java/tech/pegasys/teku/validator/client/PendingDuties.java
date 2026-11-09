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

package tech.pegasys.teku.validator.client;

import static java.util.Collections.emptySet;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class PendingDuties {
  private static final Logger LOG = LogManager.getLogger();

  private final List<Consumer<ScheduledDuties>> pendingActions = new ArrayList<>();
  private final DutyLoader<?> dutyLoader;
  private final UInt64 epoch;
  private SafeFuture<? extends Optional<? extends ScheduledDuties>> scheduledDuties =
      new SafeFuture<>();
  private Optional<Bytes32> pendingHeadUpdate = Optional.empty();
  private final LabelledMetric<Counter> dutiesPerformedCounter;

  private PendingDuties(
      final MetricsSystem metricsSystem,
      final DutyLoader<? extends ScheduledDuties> dutyLoader,
      final UInt64 epoch) {
    this.dutyLoader = dutyLoader;
    this.epoch = epoch;
    dutiesPerformedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            "duties_performed_total",
            "Count of the failed duties, by duty type",
            "type",
            "result");
  }

  public static <T extends ScheduledDuties> PendingDuties calculateDuties(
      final MetricsSystem metricsSystem, final DutyLoader<T> dutyLoader, final UInt64 epoch) {
    final PendingDuties duties = new PendingDuties(metricsSystem, dutyLoader, epoch);
    duties.recalculate();
    return duties;
  }

  public void onProductionDue(final UInt64 slot) {
    execute(
        duties ->
            duties
                .performProductionDuty(slot)
                .finish(
                    result -> reportDutySuccess(result, duties.getProductionType(), slot),
                    error -> reportDutyFailure(error, duties.getProductionType(), slot)));
  }

  public void onAggregationDue(final UInt64 slot) {
    execute(
        duties ->
            duties
                .performAggregationDuty(slot)
                .finish(
                    result -> reportDutySuccess(result, duties.getAggregationType(), slot),
                    error -> reportDutyFailure(error, duties.getAggregationType(), slot)));
  }

  private void reportDutyFailure(
      final Throwable error, final String producedType, final UInt64 slot) {
    dutiesPerformedCounter.labels(producedType, "failed").inc();
    VALIDATOR_LOGGER.dutyFailed(producedType, slot, emptySet(), error);
  }

  private void reportDutySuccess(
      final DutyResult result, final String producedType, final UInt64 slot) {
    dutiesPerformedCounter.labels(producedType, "success").inc(result.getSuccessCount());
    dutiesPerformedCounter.labels(producedType, "failed").inc(result.getFailureCount());
    result.report(producedType, slot, VALIDATOR_LOGGER);
  }

  public int countDuties() {
    return getCurrentDuties().map(ScheduledDuties::countDuties).orElse(0);
  }

  public synchronized void recalculate() {
    LOG.trace("Recalculating {} duties, epoch {}", dutyLoader::getDutyType, () -> epoch);
    scheduledDuties.cancel(false);
    // We need to ensure the duties future is completed before .
    scheduledDuties = dutyLoader.loadDutiesForEpoch(epoch);
    scheduledDuties.finish(
        this::processPendingActions,
        error -> {
          if (!(Throwables.getRootCause(error) instanceof CancellationException)) {
            LOG.error("Failed to load {} duties", dutyLoader::getDutyType, () -> error);
          } else {
            LOG.trace("Loading {} duties cancelled", dutyLoader::getDutyType, () -> error);
          }
        });
  }

  public Optional<? extends ScheduledDuties> getScheduledDuties() {
    if (scheduledDuties.isCompletedNormally()) {
      return scheduledDuties.getImmediately();
    } else {
      return Optional.empty();
    }
  }

  public synchronized void cancel() {
    scheduledDuties.cancel(false);
    pendingActions.clear();
  }

  private synchronized void processPendingActions(
      final Optional<? extends ScheduledDuties> scheduledDuties) {
    if (pendingHeadUpdate.isPresent()
        && scheduledDuties.isPresent()
        && scheduledDuties.get().requiresRecalculation(pendingHeadUpdate.get())) {
      pendingHeadUpdate = Optional.empty();
      recalculate();
      return;
    }
    pendingHeadUpdate = Optional.empty();
    scheduledDuties.ifPresent(duties -> pendingActions.forEach(action -> action.accept(duties)));
    pendingActions.clear();
  }

  protected synchronized void execute(final Consumer<ScheduledDuties> action) {
    getCurrentDuties().ifPresentOrElse(action, () -> pendingActions.add(action));
  }

  private synchronized Optional<? extends ScheduledDuties> getCurrentDuties() {
    if (!scheduledDuties.isCompletedNormally()) {
      return Optional.empty();
    }
    return scheduledDuties.join();
  }

  public synchronized void onHeadUpdate(final Bytes32 dependentRoot) {
    LOG.trace(
        "Received head update for {} duties,  dependentRoot {}",
        dutyLoader::getDutyType,
        () -> dependentRoot);
    getCurrentDuties()
        .ifPresentOrElse(
            duties -> {
              if (duties.requiresRecalculation(dependentRoot)) {
                recalculate();
              }
            },
            () -> pendingHeadUpdate = Optional.of(dependentRoot));
  }
}
