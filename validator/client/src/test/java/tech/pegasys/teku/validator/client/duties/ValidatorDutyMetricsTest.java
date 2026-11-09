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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;

class ValidatorDutyMetricsTest {

  private Duty duty;
  private OperationTimer operationTimer;
  private OperationTimer.TimingContext timingContext;

  private ValidatorDutyMetrics validatorDutyMetrics;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    duty = mock(Duty.class);
    when(duty.getType()).thenReturn(DutyType.ATTESTATION_PRODUCTION);

    final LabelledMetric<OperationTimer> dutyMetric = mock(LabelledMetric.class);
    final MetricsSystem metricsSystem = mock(MetricsSystem.class);
    when(metricsSystem.createLabelledTimer(
            eq(TekuMetricCategory.VALIDATOR_DUTY), eq("timer"), any(), eq("type"), eq("step")))
        .thenReturn(dutyMetric);

    operationTimer = mock(OperationTimer.class);
    timingContext = mock(OperationTimer.TimingContext.class);
    when(dutyMetric.labels(eq(DutyType.ATTESTATION_PRODUCTION.getName()), eq("total")))
        .thenReturn(operationTimer);
    doReturn(timingContext).when(operationTimer).startTimer();

    validatorDutyMetrics = ValidatorDutyMetrics.create(metricsSystem);
  }

  @Test
  public void shouldRecordDutyTimeWhenDutySucceeds() {
    final SafeFuture<DutyResult> dutyFuture = new SafeFuture<>();
    when(duty.performDuty()).thenReturn(dutyFuture);

    final SafeFuture<DutyResult> dutyResultSafeFuture =
        validatorDutyMetrics.performDutyWithMetrics(duty);

    verify(operationTimer).startTimer();
    verify(timingContext, never()).stopTimer();

    assertThatSafeFuture(dutyResultSafeFuture).isNotDone();

    dutyFuture.complete(DutyResult.NOOP);

    assertThatSafeFuture(dutyResultSafeFuture).isCompleted();

    verify(timingContext).stopTimer();
    verify(timingContext, never()).close();
  }

  @Test
  public void shouldRecordDutyTimeEvenWhenDutyFails() {
    final SafeFuture<DutyResult> dutyFuture = new SafeFuture<>();
    when(duty.performDuty()).thenReturn(dutyFuture);

    final SafeFuture<DutyResult> dutyResultSafeFuture =
        validatorDutyMetrics.performDutyWithMetrics(duty);

    verify(operationTimer).startTimer();
    verify(timingContext, never()).stopTimer();

    assertThatSafeFuture(dutyResultSafeFuture).isNotDone();

    dutyFuture.completeExceptionally(new RuntimeException("Error"));

    assertThatSafeFuture(dutyResultSafeFuture).isCompletedExceptionallyWith(RuntimeException.class);

    verify(timingContext).stopTimer();
    verify(timingContext, never()).close();
  }
}
