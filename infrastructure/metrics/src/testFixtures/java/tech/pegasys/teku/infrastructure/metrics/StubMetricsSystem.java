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

package tech.pegasys.teku.infrastructure.metrics;

import static java.util.Arrays.asList;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.ExternalSummary;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

public class StubMetricsSystem implements MetricsSystem {

  private final Map<MetricCategory, Map<String, StubLabelledCounter>> counters =
      new ConcurrentHashMap<>();
  private final Map<MetricCategory, Map<String, StubGauge>> gauges = new ConcurrentHashMap<>();
  private final Map<MetricCategory, Map<String, StubLabelledGauge>> labelledGauges =
      new ConcurrentHashMap<>();
  private final Map<MetricCategory, Map<String, StubLabelledOperationTimer>>
      labelledOperationTimers = new ConcurrentHashMap<>();

  private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
  private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

  /** The constant NO_OP_HISTOGRAM. */
  public static final Histogram NO_OP_HISTOGRAM = d -> {};

  @Override
  public LabelledMetric<Counter> createLabelledCounter(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    validateMetricName(name);
    validateLabelName(labelNames);
    return counters
        .computeIfAbsent(category, __ -> new ConcurrentHashMap<>())
        .computeIfAbsent(name, __ -> new StubLabelledCounter());
  }

  /* Not implemented nor used in tests but required by the new interface definition */
  @Override
  public LabelledSuppliedMetric createLabelledSuppliedCounter(
      final MetricCategory metricCategory,
      final String s,
      final String s1,
      final String... strings) {
    return NoOpMetricsSystem.getLabelledSuppliedMetric(strings.length);
  }

  @Override
  public LabelledSuppliedMetric createLabelledSuppliedGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    validateMetricName(name);
    validateLabelName(labelNames);
    return labelledGauges
        .computeIfAbsent(category, __ -> new ConcurrentHashMap<>())
        .computeIfAbsent(name, __ -> new StubLabelledGauge(category, name, help));
  }

  public long getCounterValue(final MetricCategory category, final String name) {
    return getLabelledCounterValue(category, name);
  }

  public long getLabelledCounterValue(
      final MetricCategory category, final String name, final String... labels) {
    final Map<String, StubLabelledCounter> counters = this.counters.get(category);
    final StubLabelledCounter labelledCounter = counters.get(name);
    if (labelledCounter == null) {
      throw new IllegalArgumentException("Unknown counter: " + name);
    }
    final StubCounter metric = labelledCounter.getMetric(labels);
    if (metric == null) {
      return 0;
    }
    return metric.getValue();
  }

  @Override
  public void createGauge(
      final MetricCategory category,
      final String name,
      final String help,
      final DoubleSupplier valueSupplier) {
    validateMetricName(name);
    final StubGauge gauge = new StubGauge(category, name, help, valueSupplier);
    final Map<String, StubGauge> gaugesInCategory =
        gauges.computeIfAbsent(category, key -> new ConcurrentHashMap<>());

    if (gaugesInCategory.putIfAbsent(name, gauge) != null) {
      throw new IllegalArgumentException("Attempting to create two gauges with the same name");
    }
  }

  @Override
  public LabelledMetric<Histogram> createLabelledHistogram(
      final MetricCategory category,
      final String name,
      final String help,
      final double[] buckets,
      final String... labelNames) {
    return NoOpMetricsSystem.getHistogramLabelledMetric(labelNames.length);
  }

  @Override
  public LabelledSuppliedSummary createLabelledSuppliedSummary(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return NoOpMetricsSystem.getLabelledSuppliedSummary(labelNames.length);
  }

  @Override
  public Set<MetricCategory> getEnabledCategories() {
    return Set.of();
  }

  @Override
  public void createGuavaCacheCollector(
      final MetricCategory category, final String name, final Cache<?, ?> cache) {}

  @Override
  public LabelledMetric<OperationTimer> createLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    validateMetricName(name);
    validateLabelName(labelNames);
    return labelledOperationTimers
        .computeIfAbsent(category, __ -> new ConcurrentHashMap<>())
        .computeIfAbsent(name, __ -> new StubLabelledOperationTimer(category, name, help));
  }

  @Override
  public LabelledMetric<OperationTimer> createSimpleLabelledTimer(
      final MetricCategory category,
      final String name,
      final String help,
      final String... labelNames) {
    return createLabelledTimer(category, name, help, labelNames);
  }

  public StubGauge getGauge(final MetricCategory category, final String name) {
    validateMetricName(name);
    return Optional.ofNullable(gauges.get(category))
        .map(categoryGauges -> categoryGauges.get(name))
        .orElseThrow(() -> new IllegalArgumentException("Unknown gauge: " + category + " " + name));
  }

  public StubLabelledGauge getLabelledGauge(final MetricCategory category, final String name) {
    validateMetricName(name);
    return Optional.ofNullable(labelledGauges.get(category))
        .map(categoryGauges -> categoryGauges.get(name))
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown labelled gauge: " + category + " " + name));
  }

  public StubLabelledCounter getCounter(final MetricCategory category, final String name) {
    validateMetricName(name);
    return Optional.ofNullable(counters.get(category))
        .map(categoryCounters -> categoryCounters.get(name))
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown counter: " + category + " " + name));
  }

  public StubLabelledOperationTimer getLabelledOperationTimer(
      final MetricCategory category, final String name) {
    validateMetricName(name);
    return Optional.ofNullable(labelledOperationTimers.get(category))
        .map(categoryTimers -> categoryTimers.get(name))
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown labelled timer: " + category + " " + name));
  }

  private void validateMetricName(final String metricName) {
    if (!METRIC_NAME_PATTERN.matcher(metricName).matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid metric name %s. Must match the regex %s",
              metricName, METRIC_NAME_PATTERN.pattern()));
    }
  }

  private void validateLabelName(final String... labelNames) {
    for (String labelName : labelNames) {
      if (!LABEL_NAME_PATTERN.matcher(labelName).matches()) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid label name %s. Must match the regex %s",
                labelName, LABEL_NAME_PATTERN.pattern()));
      }
    }
  }

  public static class LabelCountingNoOpMetric<T> implements LabelledMetric<T> {

    /** The Label count. */
    final int labelCount;

    /** The Fake metric. */
    final T fakeMetric;

    /**
     * Instantiates a new Label counting NoOp metric.
     *
     * @param labelCount the label count
     * @param fakeMetric the fake metric
     */
    LabelCountingNoOpMetric(final int labelCount, final T fakeMetric) {
      this.labelCount = labelCount;
      this.fakeMetric = fakeMetric;
    }

    @Override
    public T labels(final String... labels) {
      Preconditions.checkArgument(
          labels.length == labelCount,
          "The count of labels used must match the count of labels expected.");
      return fakeMetric;
    }
  }

  public static class LabelledSuppliedNoOpMetric
      implements LabelledSuppliedMetric, LabelledSuppliedSummary {
    /** The Label count. */
    final int labelCount;

    /** The Label values cache. */
    final List<String> labelValuesCache = new ArrayList<>();

    /**
     * Instantiates a new Labelled supplied NoOp metric.
     *
     * @param labelCount the label count
     */
    public LabelledSuppliedNoOpMetric(final int labelCount) {
      this.labelCount = labelCount;
    }

    @Override
    public void labels(final DoubleSupplier valueSupplier, final String... labelValues) {
      internalLabels(valueSupplier, labelValues);
    }

    @Override
    public void labels(
        final Supplier<ExternalSummary> summarySupplier, final String... labelValues) {
      internalLabels(summarySupplier, labelValues);
    }

    private void internalLabels(final Object valueSupplier, final String... labelValues) {
      final String labelValuesString = String.join(",", labelValues);
      Preconditions.checkArgument(
          !labelValuesCache.contains(labelValuesString),
          "Received label values that were already in use %s",
          labelValuesString);
      Preconditions.checkArgument(
          labelValues.length == labelCount,
          "The count of labels used must match the count of labels expected.");
      Preconditions.checkNotNull(valueSupplier, "No valueSupplier specified");
      labelValuesCache.add(labelValuesString);
    }
  }

  public static class StubLabelledCounter implements LabelledMetric<Counter> {
    private final Map<List<String>, StubCounter> metrics = new ConcurrentHashMap<>();

    @Override
    public Counter labels(final String... labels) {
      return metrics.computeIfAbsent(asList(labels), key -> new StubCounter());
    }

    private StubCounter getMetric(final String... labels) {
      return metrics.get(asList(labels));
    }
  }

  public static class StubCounter implements Counter {
    private long value = 0;

    @Override
    public void inc() {
      value++;
    }

    @Override
    public void inc(final long amount) {
      value += amount;
    }

    public long getValue() {
      return value;
    }
  }
}
