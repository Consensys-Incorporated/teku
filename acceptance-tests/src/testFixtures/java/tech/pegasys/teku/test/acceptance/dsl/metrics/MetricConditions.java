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

import com.google.common.math.DoubleMath;
import java.util.Map;

public class MetricConditions {

  public static final double DOUBLE_COMPARE_TOLERANCE = 0.000001;

  @FunctionalInterface
  public interface MetricNameCondition {

    boolean test(final String name);
  }

  @FunctionalInterface
  public interface MetricLabelsCondition {

    boolean test(final Map<String, String> labels);
  }

  @FunctionalInterface
  public interface MetricValuesCondition {

    boolean test(final Double value);
  }

  public static MetricNameCondition withAnyName() {
    return (n) -> true;
  }

  public static MetricNameCondition withNameEqualsTo(final String name) {
    return (n) -> n.equals(name);
  }

  public static MetricLabelsCondition withAnyLabels() {
    return (l) -> true;
  }

  public static MetricLabelsCondition withLabelsContaining(final Map<String, String> labels) {
    return (actualLabels) ->
        labels.entrySet().stream()
            .allMatch(
                entry ->
                    actualLabels.containsKey(entry.getKey())
                        && actualLabels.get(entry.getKey()).equals(entry.getValue()));
  }

  public static MetricLabelsCondition withLabelValueSubstring(
      final String labelName, final String labelValueSubstring) {
    return actualLabels -> {
      final String actual = actualLabels.get(labelName);
      return actual != null && actual.contains(labelValueSubstring);
    };
  }

  public static MetricValuesCondition withValueEqualTo(final double value) {
    return (actualValue) -> DoubleMath.fuzzyEquals(actualValue, value, DOUBLE_COMPARE_TOLERANCE);
  }

  public static MetricValuesCondition withValueGreaterThan(final double value) {
    return (actualValue) ->
        DoubleMath.fuzzyCompare(actualValue, value, DOUBLE_COMPARE_TOLERANCE) > 0;
  }

  public static MetricValuesCondition withValueGreaterThanOrEqualTo(final double value) {
    return (actualValue) ->
        DoubleMath.fuzzyCompare(actualValue, value, DOUBLE_COMPARE_TOLERANCE) >= 0;
  }

  public static MetricValuesCondition withValueLessThan(final double value) {
    return (actualValue) ->
        DoubleMath.fuzzyCompare(actualValue, value, DOUBLE_COMPARE_TOLERANCE) < 0;
  }

  public static MetricValuesCondition withValueLessThanOrEqualTo(final double value) {
    return (actualValue) ->
        DoubleMath.fuzzyCompare(actualValue, value, DOUBLE_COMPARE_TOLERANCE) <= 0;
  }

  public static MetricValuesCondition withAnyValue() {
    return (actualValue) -> true;
  }
}
