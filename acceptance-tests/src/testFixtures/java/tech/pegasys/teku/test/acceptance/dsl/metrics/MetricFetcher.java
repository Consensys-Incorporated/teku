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

package tech.pegasys.teku.test.acceptance.dsl.metrics;

import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withAnyLabels;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withAnyName;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withAnyValue;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricMatcher.allMatching;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tech.pegasys.teku.test.acceptance.dsl.SimpleHttpClient;

public class MetricFetcher {

  private final SimpleHttpClient httpClient;
  private final URI metricsEndpoint;

  public MetricFetcher(final SimpleHttpClient httpClient, final URI metricsEndpoint) {
    this.httpClient = httpClient;
    this.metricsEndpoint = metricsEndpoint;
  }

  public List<MetricValue> getAllMetrics() {
    return allMatching(
        fetchAllMetricsFromEndpoint(), withAnyName(), withAnyLabels(), withAnyValue());
  }

  public List<MetricValue> getMetricsByName(final String name) {
    return allMatching(
        fetchAllMetricsFromEndpoint(), withNameEqualsTo(name), withAnyLabels(), withAnyValue());
  }

  /**
   * Search a single metric with the specified name. If more than one metric is found (usually due
   * to different labels) an error is thrown.
   *
   * @param name Name of the metric
   * @return a {@link MetricValue} for the metric
   */
  public MetricValue getSingleMetricByName(final String name) {
    final List<MetricValue> metrics = getMetricsByName(name);
    if (metrics.size() == 0) {
      throw new RuntimeException("Can't find metrics with name " + name);
    }

    if (metrics.size() > 1) {
      throw new RuntimeException(
          "Multiple entries found for metric "
              + name
              + ". Consider using getSingleMetricValueByNameAndLabels(..) in your test.");
    }

    return metrics.get(0);
  }

  private List<MetricValue> fetchAllMetricsFromEndpoint() {
    try {
      final String allMetrics = httpClient.get(metricsEndpoint, "/metrics");
      try (BufferedReader reader = new BufferedReader(new StringReader(allMetrics))) {
        return reader
            .lines()
            .filter(l -> !l.startsWith("#")) // removing comments from response
            .map(MetricFetcher::parse)
            .collect(Collectors.toList());
      }
    } catch (final Exception e) {
      throw new RuntimeException(
          "Error getting metric values from " + metricsEndpoint + "/metrics", e);
    }
  }

  public static MetricValue parse(final String line) {
    if (line.contains("{")) {
      final String metricName = line.substring(0, line.indexOf("{"));

      final Double metricValue = Double.parseDouble(line.substring(line.indexOf("} ") + 1));

      final Map<String, String> metricLabels = new HashMap<>();
      final String labelsString = line.substring(line.indexOf("{") + 1, line.indexOf("}"));
      final Pattern p = Pattern.compile("(.*?)=\"(.*?)\",?");
      final Matcher m = p.matcher(labelsString);
      while (m.find()) {
        metricLabels.put(m.group(1), m.group(2));
      }

      return new MetricValue(metricName, metricLabels, metricValue);
    } else {
      final String name = line.split("\\s", -1)[0];
      final Double value = Double.parseDouble(line.split("\\s", -1)[1]);

      return new MetricValue(name, null, value);
    }
  }
}
