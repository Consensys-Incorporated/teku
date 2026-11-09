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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class MetricsCountersByIntervals {
  static final String INTERVAL_LABEL = "interval";

  private final Map<List<String>, TreeMap<UInt64, String>> labelsToBoundariesToIntervalLabels;
  private final List<Integer> labelValuesListSizes;
  private final LabelledMetric<Counter> labelledMetricCounter;

  @SuppressWarnings("NonApiType")
  private MetricsCountersByIntervals(
      final Map<List<String>, TreeMap<UInt64, String>> labelsToBoundariesToIntervalLabels,
      final List<Integer> labelValuesListSizes,
      final LabelledMetric<Counter> labelledMetricCounter) {
    this.labelsToBoundariesToIntervalLabels = labelsToBoundariesToIntervalLabels;
    this.labelValuesListSizes = labelValuesListSizes;
    this.labelledMetricCounter = labelledMetricCounter;
  }

  public static MetricsCountersByIntervals create(
      final MetricCategory category,
      final MetricsSystem metricsSystem,
      final String counterName,
      final String counterHelp,
      final List<String> customLabelsNames,
      final Map<List<String>, List<Long>> labelsValuesToBoundaries) {

    // calculate listOfValues -> treeMapOfBoundariesToIntervalLabels
    // i.e.
    // [
    //   ("val1","val2") -> [0->"[0,10),10->"[10,20)",...],
    //   ("val3") -> [0->"[0,50),50->"[50,120)",...]
    // ]
    final Map<List<String>, TreeMap<UInt64, String>> labelsToBoundariesToIntervalLabels =
        labelsValuesToBoundaries.entrySet().stream()
            .map(
                listListEntry ->
                    Map.entry(
                        listListEntry.getKey(),
                        boundariesToIntervalLabels(listListEntry.getValue())))
            .collect(Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue));

    // calculate the labels sizes tests used to determine a boundary set (reverse order do give
    // priority to the most specific values
    final List<Integer> labelValuesListSizes =
        labelsValuesToBoundaries.keySet().stream()
            .map(List::size)
            .sorted(Comparator.reverseOrder())
            .distinct()
            .toList();

    final String[] labels =
        Stream.concat(customLabelsNames.stream(), Stream.of(INTERVAL_LABEL)).toArray(String[]::new);

    final LabelledMetric<Counter> labelledMetricCounter =
        metricsSystem.createLabelledCounter(category, counterName, counterHelp, labels);

    return new MetricsCountersByIntervals(
        labelsToBoundariesToIntervalLabels, labelValuesListSizes, labelledMetricCounter);
  }

  public void recordValue(final long value, final String... customLabelValues) {
    updateCounterMetric(UInt64.valueOf(value), customLabelValues);
  }

  public void recordValue(final UInt64 value, final String... customLabelValues) {
    updateCounterMetric(value, customLabelValues);
  }

  /**
   * before adding the value to the counter we need to resolve the INTERVAL_LABEL value by searching
   * for a matching {@code labelsValuesToBoundaries} key from the given {@code customLabelValues}.
   * It will start from the most specific (the maximum number of matching values) to the minimum.
   *
   * @param value to count
   * @param customLabelValues metric labels values associated to the value
   */
  private void updateCounterMetric(final UInt64 value, final String... customLabelValues) {
    final List<String> customLabelValuesList = Arrays.asList(customLabelValues);
    final Optional<TreeMap<UInt64, String>> intervalLabels =
        lookupIntervalLabels(customLabelValuesList);

    if (intervalLabels.isEmpty()) {
      return;
    }

    labelledMetricCounter
        .labels(
            Stream.concat(
                    customLabelValuesList.stream(),
                    Stream.of(intervalLabels.get().floorEntry(value).getValue()))
                .toArray(String[]::new))
        .inc();
  }

  public void initCounters(final List<String> customLabelValuesList) {
    final Optional<TreeMap<UInt64, String>> intervalLabels =
        lookupIntervalLabels(customLabelValuesList);

    if (intervalLabels.isEmpty()) {
      return;
    }

    intervalLabels
        .get()
        .values()
        .forEach(
            s ->
                labelledMetricCounter.labels(
                    Stream.concat(customLabelValuesList.stream(), Stream.of(s))
                        .toArray(String[]::new)));
  }

  @SuppressWarnings("NonApiType")
  private Optional<TreeMap<UInt64, String>> lookupIntervalLabels(
      final List<String> customLabelValuesList) {
    return labelValuesListSizes.stream()
        .map(
            size ->
                Optional.ofNullable(
                    labelsToBoundariesToIntervalLabels.get(customLabelValuesList.subList(0, size))))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  @SuppressWarnings("NonApiType")
  protected static TreeMap<UInt64, String> boundariesToIntervalLabels(final List<Long> boundaries) {
    UInt64 previous = UInt64.ZERO;
    final TreeMap<UInt64, String> boundariesToLabels = new TreeMap<>();

    for (Long boundary : boundaries) {
      if (previous.isGreaterThanOrEqualTo(boundary)) {
        throw new IllegalArgumentException(
            "boundaries must be grater than 0 and strictly increasing");
      }
      boundariesToLabels.put(previous, "[" + previous + "," + boundary + ")");
      previous = UInt64.valueOf(boundary);
    }
    boundariesToLabels.put(previous, "[" + previous + ",∞)");
    return boundariesToLabels;
  }
}
