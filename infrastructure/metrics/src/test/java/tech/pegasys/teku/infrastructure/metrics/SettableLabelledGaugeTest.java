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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class SettableLabelledGaugeTest {

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @Test
  void shouldSetValuesForGaugeWithOneLabel() {
    final SettableLabelledGauge labelledGauge =
        SettableLabelledGauge.create(
            metricsSystem, TekuMetricCategory.BEACON, "test", "Help text", "type");
    labelledGauge.set(1d, "type1");
    labelledGauge.set(2d, "type2");
    labelledGauge.set(3d, "type3");

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.BEACON, "test");
    assertThat(gauge.getValue("type1")).isEqualTo(OptionalDouble.of(1d));
    assertThat(gauge.getValue("type2")).isEqualTo(OptionalDouble.of(2d));
    assertThat(gauge.getValue("type3")).isEqualTo(OptionalDouble.of(3d));
    assertThat(gauge.getValue("type4")).isEqualTo(OptionalDouble.empty());

    labelledGauge.set(0d, "type1");
    assertThat(gauge.getValue("type1")).isEqualTo(OptionalDouble.of(0d));
    assertThat(gauge.getValue("type2")).isEqualTo(OptionalDouble.of(2d));
    assertThat(gauge.getValue("type3")).isEqualTo(OptionalDouble.of(3d));
  }

  @Test
  void shouldSetValuesForGaugeWithMultipleLabels() {
    final SettableLabelledGauge labelledGauge =
        SettableLabelledGauge.create(
            metricsSystem, TekuMetricCategory.BEACON, "test", "Help text", "a", "b");
    labelledGauge.set(11d, "a1", "b1");
    labelledGauge.set(12d, "a1", "b2");
    labelledGauge.set(21d, "a2", "b1");
    labelledGauge.set(22d, "a2", "b2");

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.BEACON, "test");
    assertThat(gauge.getValue("a1", "b1")).isEqualTo(OptionalDouble.of(11d));
    assertThat(gauge.getValue("a1", "b2")).isEqualTo(OptionalDouble.of(12d));
    assertThat(gauge.getValue("a2", "b1")).isEqualTo(OptionalDouble.of(21d));
    assertThat(gauge.getValue("a2", "b2")).isEqualTo(OptionalDouble.of(22d));

    labelledGauge.set(33d, "a2", "b2");
    assertThat(gauge.getValue("a1", "b1")).isEqualTo(OptionalDouble.of(11d));
    assertThat(gauge.getValue("a1", "b2")).isEqualTo(OptionalDouble.of(12d));
    assertThat(gauge.getValue("a2", "b1")).isEqualTo(OptionalDouble.of(21d));
    assertThat(gauge.getValue("a2", "b2")).isEqualTo(OptionalDouble.of(33d));
  }
}
