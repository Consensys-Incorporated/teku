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

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class StubMetricSystemTest {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  private final MetricCategory metricCategory =
      new MetricCategory() {
        @Override
        public String getName() {
          return "dummyMetricCategory";
        }

        @Override
        public Optional<String> getApplicationPrefix() {
          return Optional.empty();
        }
      };

  @ParameterizedTest
  @ValueSource(
      strings = {
        "correctname",
        ":correctname",
        "_correctname",
        "correctNAME",
        "correct_name",
        "correct_name__",
        "correct_name::123"
      })
  public void mustAcceptValidMetricNames(final String input) {
    metricsSystem.createLabelledCounter(metricCategory, input, "", "correct_label");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1incorrect_name", "$incorrect_name", "incorrect-name"})
  public void mustRejectInvalidMetricNames(final String input) {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> metricsSystem.createLabelledCounter(metricCategory, input, "", "correct_label"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"correctlabel", "correctLabel", "correct_label", "_correct_label"})
  public void mustAcceptValidLabelNames(final String input) {
    metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", input);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1incorrect_label", "@incorrect_label", "incorrect-label"})
  public void mustRejectInvalidLabelNames() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> metricsSystem.createLabelledCounter(metricCategory, "correct_name", "", ""));
  }
}
