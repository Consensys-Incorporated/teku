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

package tech.pegasys.teku.infrastructure.metrics.Validator;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

public class ValidatorDutyMetricUtils {
  public static LabelledMetric<OperationTimer> createValidatorDutyMetric(
      final MetricsSystem metricsSystem) {
    return metricsSystem.createLabelledTimer(
        TekuMetricCategory.VALIDATOR_DUTY,
        "timer",
        "Timer recording the time taken to perform a duty",
        "type",
        "step");
  }

  public static OperationTimer.TimingContext startTimer(
      final LabelledMetric<OperationTimer> labelledTimer,
      final String dutyType,
      final String step) {
    final OperationTimer timer = labelledTimer.labels(dutyType, step);
    return timer.startTimer();
  }
}
