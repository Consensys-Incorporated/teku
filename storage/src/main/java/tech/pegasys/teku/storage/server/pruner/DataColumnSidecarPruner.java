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

package tech.pegasys.teku.storage.server.pruner;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.storage.server.Database;

public class DataColumnSidecarPruner extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Database database;
  private final AsyncRunner asyncRunner;
  private final AsyncRunner metricsAsyncRunner;
  private final Duration pruneInterval;
  private final int pruneLimit;
  private final TimeProvider timeProvider;
  private final boolean dataColumnSidecarsStorageCountersEnabled;
  private final SettableLabelledGauge pruningTimingsLabelledGauge;
  private final SettableLabelledGauge pruningActiveLabelledGauge;
  private final String pruningMetricsType;
  private final Duration pruningWarnTimeout;

  private final AtomicLong dataColumnSize = new AtomicLong(0);
  private final AtomicLong earliestDataColumnSidecarSlot = new AtomicLong(-1);
  private Optional<UInt64> genesisTime = Optional.empty();

  private Optional<Cancellable> scheduledPruner = Optional.empty();
  private Optional<Cancellable> scheduledMetricsUpdater = Optional.empty();

  public DataColumnSidecarPruner(
      final Spec spec,
      final Database database,
      final MetricsSystem metricsSystem,
      final AsyncRunner asyncRunner,
      final AsyncRunner metricsAsyncRunner,
      final TimeProvider timeProvider,
      final Duration pruneInterval,
      final int pruneLimit,
      final boolean dataColumnSidecarsStorageCountersEnabled,
      final String pruningMetricsType,
      final SettableLabelledGauge pruningTimingsLabelledGauge,
      final SettableLabelledGauge pruningActiveLabelledGauge,
      final Duration pruningWarnTimeout) {
    this.spec = spec;
    this.database = database;
    this.asyncRunner = asyncRunner;
    this.metricsAsyncRunner = metricsAsyncRunner;
    this.pruneInterval = pruneInterval;
    this.pruneLimit = pruneLimit;
    this.timeProvider = timeProvider;
    this.dataColumnSidecarsStorageCountersEnabled = dataColumnSidecarsStorageCountersEnabled;
    this.pruningMetricsType = pruningMetricsType;
    this.pruningTimingsLabelledGauge = pruningTimingsLabelledGauge;
    this.pruningActiveLabelledGauge = pruningActiveLabelledGauge;
    this.pruningWarnTimeout = pruningWarnTimeout;
    if (dataColumnSidecarsStorageCountersEnabled) {
      LabelledSuppliedMetric labelledGauge =
          metricsSystem.createLabelledSuppliedGauge(
              TekuMetricCategory.STORAGE,
              "data_column_sidecars",
              "Statistics for Data columns stored",
              "type");

      labelledGauge.labels(dataColumnSize::get, "total");
      labelledGauge.labels(earliestDataColumnSidecarSlot::get, "earliest_slot");
    }
  }

  @Override
  protected SafeFuture<?> doStart() {
    scheduledPruner =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::doPruneDataColumnSidecars,
                Duration.ZERO,
                pruneInterval,
                error -> LOG.error("Failed to prune old data column sidecars", error)));
    if (dataColumnSidecarsStorageCountersEnabled) {
      scheduledMetricsUpdater =
          Optional.of(
              metricsAsyncRunner.runWithFixedDelay(
                  this::doUpdateDataColumnSidecarMetrics,
                  Duration.ZERO,
                  pruneInterval,
                  error ->
                      LOG.error("Failed to update data column sidecar storage counters", error)));
    }
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    scheduledPruner.ifPresent(Cancellable::cancel);
    scheduledMetricsUpdater.ifPresent(Cancellable::cancel);
    return SafeFuture.COMPLETE;
  }

  /**
   * NOTE: the column pruning must not update earliestAvailableDataColumnSlot db variable. That
   * variable is fully maintained by DasCustodyBackfiller
   */
  private void doPruneDataColumnSidecars() {
    LOG.debug("Data column sidecars pruner task triggered");
    final Optional<UInt64> genesisTime = getGenesisTime();
    if (genesisTime.isEmpty()) {
      LOG.debug("Not pruning data column sidecars: no genesis time available yet.");
      return;
    }

    pruningActiveLabelledGauge.set(1, pruningMetricsType);
    final long start = System.currentTimeMillis();
    final UInt64 minCustodySlot = calculatePruneSlot();
    LOG.debug("Pruning data column sidecars before slot {}", minCustodySlot);
    database.pruneAllSidecars(minCustodySlot.minusMinZero(1), pruneLimit);
    final long elapsed = System.currentTimeMillis() - start;
    pruningTimingsLabelledGauge.set(elapsed, pruningMetricsType);
    pruningActiveLabelledGauge.set(0, pruningMetricsType);
    LOG.debug("Data column sidecars pruning completed in {} ms", elapsed);
    if (elapsed > pruningWarnTimeout.toMillis()) {
      LOG.warn(
          "Pruning task for {} took {} ms, exceeding the warn threshold of {} ms",
          pruningMetricsType,
          elapsed,
          pruningWarnTimeout.toMillis());
    }
  }

  /**
   * Updates storage counter gauges by performing a full sequential scan of the sidecar column. O(N)
   * — may take minutes on nodes with large DCS datasets.
   */
  private void doUpdateDataColumnSidecarMetrics() {
    LOG.debug("Updating data column sidecar storage counters (full column scan — may be slow)");
    final long start = System.currentTimeMillis();
    dataColumnSize.set(database.getSidecarColumnCount());
    earliestDataColumnSidecarSlot.set(
        database.getEarliestDataColumnSidecarSlot().map(UInt64::longValue).orElse(-1L));
    LOG.debug(
        "Data column sidecar storage counters updated in {} ms: total={}, earliestSlot={}",
        System.currentTimeMillis() - start,
        dataColumnSize.get(),
        earliestDataColumnSidecarSlot.get());
  }

  // Adds one extra epoch beyond custodyPeriodEpochs to avoid the edge case described in
  // BlobSidecarPruner where the slot at the exact epoch boundary could be kept one epoch too long.
  private UInt64 calculatePruneSlot() {
    final UInt64 currentSlot =
        spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime.orElseThrow());
    final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    final int custodyPeriodEpochs =
        spec.getSpecConfig(currentEpoch)
            .toVersionFulu()
            .map(SpecConfigFulu::getMinEpochsForDataColumnSidecarsRequests)
            .orElse(0);
    if (custodyPeriodEpochs == 0) {
      return currentSlot;
    } else {
      final UInt64 minCustodyEpoch = currentEpoch.minusMinZero(custodyPeriodEpochs + 1);
      return spec.computeStartSlotAtEpoch(minCustodyEpoch);
    }
  }

  private Optional<UInt64> getGenesisTime() {
    if (genesisTime.isPresent()) {
      return genesisTime;
    }
    genesisTime = database.getGenesisTime();
    return genesisTime;
  }
}
