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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.HdrHistogram.SynchronizedHistogram;
import org.hyperledger.besu.metrics.prometheus.PrometheusMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.ExternalSummary;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

/**
 * A histogram metric that automatically selects bucket sizes based on simple configuration and the
 * values actually received. Only records values when the metrics system is a {@link
 * PrometheusMetricsSystem}.
 *
 * <p>Backing is an HdrHistogram.
 *
 * @see <a href="https://github.com/HdrHistogram/HdrHistogram">HdrHistogram docs</a>
 */
public class MetricsQuantileHistogram {
  static final String QUANTILE_LABEL = "quantile";
  static final double LABEL_50 = 0.5;
  static final double LABEL_95 = 0.95;
  static final double LABEL_99 = 0.99;
  static final double LABEL_1 = 1;

  private final Map<List<String>, SynchronizedHistogram> histogramMap = new ConcurrentHashMap<>();
  private final List<String> labels;
  private final Optional<Long> highestTrackableValue;
  private final int numberOfSignificantValueDigits;

  protected MetricsQuantileHistogram(
      final int numberOfSignificantValueDigits,
      final Optional<Long> highestTrackableValue,
      final List<String> customLabelsNames) {
    this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    this.highestTrackableValue = highestTrackableValue;
    this.labels = Stream.concat(customLabelsNames.stream(), Stream.of(QUANTILE_LABEL)).toList();
  }

  /**
   * Create a new histogram metric which auto-resizes to fit any values supplied and maintains at
   * least {@code numberOfSignificantValueDigits} of precision.
   *
   * @param category the metrics category
   * @param metricsSystem the metrics system to register with
   * @param name the name of the metric
   * @param help the help text describing the metric
   * @param numberOfSignificantValueDigits the number of digits of precision to preserve
   * @return the new metric
   */
  public static MetricsQuantileHistogram create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final int numberOfSignificantValueDigits,
      final List<String> customLabelsNames) {

    return createMetric(
        category,
        metricsSystem,
        name,
        help,
        numberOfSignificantValueDigits,
        Optional.empty(),
        customLabelsNames);
  }

  /**
   * Create a new histogram metric with a fixed maximum value, maintaining at least {@code
   * numberOfSignificantValueDigits} of precision.
   *
   * <p>Values above the specified highestTrackableValue will be recorded as being equal to that
   * value.
   *
   * @param category the metrics category
   * @param metricsSystem the metrics system to register with
   * @param name the name of the metric
   * @param help the help text describing the metric
   * @param numberOfSignificantValueDigits the number of digits of precision to preserve
   * @param highestTrackableValue the highest value that can be recorded by this histogram
   * @return the new metric
   */
  public static MetricsQuantileHistogram create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final int numberOfSignificantValueDigits,
      final long highestTrackableValue,
      final List<String> customLabelsNames) {

    return createMetric(
        category,
        metricsSystem,
        name,
        help,
        numberOfSignificantValueDigits,
        Optional.of(highestTrackableValue),
        customLabelsNames);
  }

  private static MetricsQuantileHistogram createMetric(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String name,
      final String help,
      final int numberOfSignificantValueDigits,
      final Optional<Long> highestTrackableValue,
      final List<String> customLabelsNames) {

    final MetricsQuantileHistogram histogram =
        new MetricsQuantileHistogram(
            numberOfSignificantValueDigits, highestTrackableValue, customLabelsNames);
    if (metricsSystem instanceof PrometheusMetricsSystem) {
      final String summaryMetricName = category.toString().toLowerCase(Locale.ROOT) + "_" + name;
      metricsSystem.createSummary(
          category, summaryMetricName, help, histogram::histogramToCollector);
    }
    return histogram;
  }

  public void recordValue(final long value, final String... customLabelValues) {
    checkArgument(
        labels.size() == customLabelValues.length + 1,
        "customLabelsNames and customLabelsValues must have the same size");

    final SynchronizedHistogram histogram =
        histogramMap.computeIfAbsent(
            Arrays.asList(customLabelValues),
            __ ->
                highestTrackableValue
                    .map(aLong -> new SynchronizedHistogram(aLong, numberOfSignificantValueDigits))
                    .orElseGet(() -> new SynchronizedHistogram(numberOfSignificantValueDigits)));

    if (histogram.isAutoResize()) {
      histogram.recordValue(value);
    } else {
      histogram.recordValue(Math.min(histogram.getHighestTrackableValue(), value));
    }
  }

  protected ExternalSummary histogramToCollector() {

    final List<ExternalSummary.Quantile> quantiles =
        histogramMap.entrySet().stream()
            .flatMap(
                entry ->
                    Stream.of(
                        new ExternalSummary.Quantile(
                            LABEL_50, entry.getValue().getValueAtPercentile(50)),
                        new ExternalSummary.Quantile(
                            LABEL_95, entry.getValue().getValueAtPercentile(95)),
                        new ExternalSummary.Quantile(
                            LABEL_99, entry.getValue().getValueAtPercentile(99)),
                        new ExternalSummary.Quantile(
                            LABEL_1, entry.getValue().getValueAtPercentile(100))))
            .toList();

    // return sum 0 as we currently don't use it
    return new ExternalSummary(histogramMap.size(), 0, quantiles);
  }
}
