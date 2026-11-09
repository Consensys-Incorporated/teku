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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricLabelsCondition;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricNameCondition;
import tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.MetricValuesCondition;

public class MetricMatcher {

  public static List<MetricValue> allMatching(
      final List<MetricValue> metrics,
      final MetricNameCondition nameCondition,
      final MetricLabelsCondition labelsCondition,
      final MetricValuesCondition valueCondition) {
    return metrics.stream()
        .filter(metric -> nameCondition.test(metric.getName()))
        .filter(metric -> labelsCondition.test(metric.getLabels()))
        .filter(metric -> valueCondition.test(metric.getValue()))
        .collect(Collectors.toList());
  }

  public static Optional<MetricValue> anyMatching(
      final List<MetricValue> metrics,
      final MetricNameCondition nameCondition,
      final MetricLabelsCondition labelsCondition,
      final MetricValuesCondition valueCondition) {
    return metrics.stream()
        .filter(metric -> nameCondition.test(metric.getName()))
        .filter(metric -> labelsCondition.test(metric.getLabels()))
        .filter(metric -> valueCondition.test(metric.getValue()))
        .findAny();
  }
}
