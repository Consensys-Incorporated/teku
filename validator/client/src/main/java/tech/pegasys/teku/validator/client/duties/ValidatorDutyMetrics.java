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

package tech.pegasys.teku.validator.client.duties;

import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils.startTimer;
import static tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps.TOTAL;

import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;

public class ValidatorDutyMetrics {
  private final LabelledMetric<OperationTimer> dutyMetric;

  private ValidatorDutyMetrics(final LabelledMetric<OperationTimer> dutyMetric) {
    this.dutyMetric = dutyMetric;
  }

  public static ValidatorDutyMetrics create(final MetricsSystem metricsSystem) {
    return new ValidatorDutyMetrics(
        ValidatorDutyMetricUtils.createValidatorDutyMetric(metricsSystem));
  }

  // NOTE: we don't use the AutoCloseable in the try block because we are in an async context
  // if an exception is thrown during the async flow plumbing we can accept to lose the data point

  public SafeFuture<DutyResult> performDutyWithMetrics(final Duty duty) {
    final OperationTimer.TimingContext context =
        startTimer(dutyMetric, getDutyType(duty), TOTAL.getName());
    return duty.performDuty().alwaysRun(context::stopTimer);
  }

  public <T> SafeFuture<T> record(
      final Supplier<SafeFuture<T>> dutyStepFutureSupplier,
      final Duty duty,
      final ValidatorDutyMetricsSteps step) {
    final OperationTimer.TimingContext context =
        startTimer(dutyMetric, getDutyType(duty), step.getName());
    return dutyStepFutureSupplier.get().alwaysRun(context::stopTimer);
  }

  private static String getDutyType(final Duty duty) {
    return duty.getType().getName();
  }
}
