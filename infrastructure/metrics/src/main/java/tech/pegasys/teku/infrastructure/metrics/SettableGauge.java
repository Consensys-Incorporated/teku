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

import com.google.common.util.concurrent.AtomicDouble;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

public class SettableGauge {

  private final AtomicDouble valueHolder;

  private SettableGauge(final AtomicDouble valueHolder) {
    this.valueHolder = valueHolder;
  }

  public static SettableGauge create(
      final MetricsSystem metricsSystem,
      final MetricCategory category,
      final String name,
      final String help) {
    AtomicDouble valueHolder = new AtomicDouble();
    metricsSystem.createGauge(category, name, help, valueHolder::get);
    return new SettableGauge(valueHolder);
  }

  public void set(final double value) {
    valueHolder.set(value);
  }
}
