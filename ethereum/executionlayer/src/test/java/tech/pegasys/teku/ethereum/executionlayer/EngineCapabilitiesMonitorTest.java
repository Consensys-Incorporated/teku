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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.ethereum.executionlayer.EngineCapabilitiesMonitor.SLOT_IN_THE_EPOCH_TO_RUN_MONITORING;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class EngineCapabilitiesMonitorTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final EventLogger eventLogger = mock(EventLogger.class);
  private final EngineJsonRpcMethodsResolver engineMethodsResolver =
      mock(EngineJsonRpcMethodsResolver.class);
  private final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  private final List<String> capabilities = List.of("method1", "method2");

  private EngineCapabilitiesMonitor engineCapabilitiesMonitor;

  @BeforeEach
  public void setUp() {
    when(engineMethodsResolver.getCapabilities()).thenReturn(new HashSet<>(capabilities));
    mockEngineCapabilitiesResponse(capabilities);
    engineCapabilitiesMonitor =
        new EngineCapabilitiesMonitor(
            spec, eventLogger, engineMethodsResolver, executionEngineClient);
  }

  @Test
  public void logsWarningIfEngineDoesNotSupportCapabilities() {
    // engine only supports one of the methods
    mockEngineCapabilitiesResponse(List.of("method1"));

    // 3rd slot in epoch
    engineCapabilitiesMonitor.onSlot(UInt64.valueOf(2));

    verify(eventLogger).missingEngineApiCapabilities(List.of("method2"));
  }

  @Test
  public void doesNotLogWarningIfEngineSupportsAllRequiredCapabilities() {
    // 3rd slot in epoch
    engineCapabilitiesMonitor.onSlot(UInt64.valueOf(2));

    verifyNoInteractions(eventLogger);
  }

  @Test
  public void doesNotRunMonitoringIfNotAtRequiredEpoch() {
    // one run at epoch 0
    engineCapabilitiesMonitor.onSlot(UInt64.valueOf(2));
    verify(executionEngineClient, times(1)).exchangeCapabilities(anyList());

    clearInvocations(executionEngineClient);

    // should not run next epoch
    engineCapabilitiesMonitor.onSlot(computeApplicableSlotForEpoch(UInt64.ONE));
    verify(executionEngineClient, never()).exchangeCapabilities(anyList());

    // should not run at epoch 9
    engineCapabilitiesMonitor.onSlot(computeApplicableSlotForEpoch(UInt64.valueOf(9)));
    verify(executionEngineClient, never()).exchangeCapabilities(anyList());

    // should run again at epoch 10
    engineCapabilitiesMonitor.onSlot(computeApplicableSlotForEpoch(UInt64.valueOf(10)));
    verify(executionEngineClient, times(1)).exchangeCapabilities(anyList());

    clearInvocations(executionEngineClient);

    // should not run at epoch 12
    engineCapabilitiesMonitor.onSlot(computeApplicableSlotForEpoch(UInt64.valueOf(12)));
    verify(executionEngineClient, never()).exchangeCapabilities(anyList());
  }

  @Test
  public void runsMonitoringFirstTimeRegardlessOfSlot() {
    engineCapabilitiesMonitor.onSlot(UInt64.ZERO);
    verify(executionEngineClient).exchangeCapabilities(anyList());
  }

  @Test
  public void doesNotRunMonitoringIfNotAtRequiredSlot() {
    // run once to bypass hasNeverRun()
    engineCapabilitiesMonitor.onSlot(UInt64.ZERO);
    verify(executionEngineClient).exchangeCapabilities(anyList());

    clearInvocations(executionEngineClient);

    // should not run monitoring on not applicable slots
    final UInt64 nextApplicableSlot = computeApplicableSlotForEpoch(UInt64.valueOf(10));
    engineCapabilitiesMonitor.onSlot(nextApplicableSlot.minus(1));
    engineCapabilitiesMonitor.onSlot(nextApplicableSlot.plus(1));
    engineCapabilitiesMonitor.onSlot(nextApplicableSlot.plus(2));
    verifyNoInteractions(executionEngineClient);

    // should run on applicable slot
    engineCapabilitiesMonitor.onSlot(nextApplicableSlot);
    verify(executionEngineClient).exchangeCapabilities(anyList());
  }

  private void mockEngineCapabilitiesResponse(final List<String> engineCapabilities) {
    when(executionEngineClient.exchangeCapabilities(capabilities))
        .thenReturn(
            SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(engineCapabilities)));
  }

  private UInt64 computeApplicableSlotForEpoch(final UInt64 epoch) {
    return spec.computeStartSlotAtEpoch(epoch)
        .plus(SLOT_IN_THE_EPOCH_TO_RUN_MONITORING.minusMinZero(1));
  }
}
