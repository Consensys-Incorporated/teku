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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger.DEBUG_TIME_MILLIS;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger.WARNING_TIME_MILLIS;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.storage.client.ChainHead;

class ForkChoiceTriggerTest {

  protected final ForkChoice forkChoice = mock(ForkChoice.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);
  private final ForkChoiceTrigger trigger =
      new ForkChoiceTrigger(
          forkChoice,
          Eth2NetworkConfiguration.DEFAULT_ATTESTATION_WAIT_TIMEOUT_MILLIS,
          timeProvider);

  @BeforeEach
  void setUp() {
    when(forkChoice.processHead(any())).thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldProcessHeadOnSlotStartedWhileSyncing() {
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    trigger.onSlotStartedWhileSyncing(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
  }

  @Test
  void shouldFailToWaitWithTimeout() {
    final SafeFuture<Optional<ChainHead>> future = new SafeFuture<>();
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    when(forkChoice.processHead(UInt64.ONE)).thenReturn(future);

    timeProvider.advanceTimeByMillis(2000);
    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceTrigger.class)) {
      trigger.onAttestationsDueForSlot(UInt64.ONE);
      assertThat(logCaptor.getWarnLogs())
          .anyMatch(log -> log.contains("Timeout waiting for fork choice to complete for slot 1"));
    }
  }

  @Test
  void shouldSucceedWithWarning() throws Exception {
    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceTrigger.class)) {
      timedLoggingTest(WARNING_TIME_MILLIS);
      assertThat(logCaptor.getWarnLogs().getFirst())
          .containsIgnoringCase("Took " + WARNING_TIME_MILLIS + " ms");
    }
  }

  @Test
  void shouldSucceedWithDebug() throws Exception {
    final int duration = WARNING_TIME_MILLIS - 1;
    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceTrigger.class)) {
      timedLoggingTest(duration);
      assertThat(logCaptor.getDebugLogs().getFirst())
          .containsIgnoringCase("Took " + duration + " ms");
    }
  }

  @Test
  void shouldSucceedNoDebugOrWarn() throws Exception {
    try (final LogCaptor logCaptor = LogCaptor.forClass(ForkChoiceTrigger.class)) {
      timedLoggingTest(Math.max(DEBUG_TIME_MILLIS - 1, 0));
      assertThat(logCaptor.getDebugLogs()).isEmpty();
      assertThat(logCaptor.getWarnLogs()).isEmpty();
    }
  }

  // at different durations, different logs will appear, simulating that here.
  private void timedLoggingTest(final int durationMillis) throws Exception {
    final UInt64 startTimeMillis = UInt64.valueOf(1_000_000);
    final TimeProvider localTime = mock(TimeProvider.class);
    final ForkChoiceTrigger localTrigger =
        new ForkChoiceTrigger(
            forkChoice,
            Eth2NetworkConfiguration.DEFAULT_ATTESTATION_WAIT_TIMEOUT_MILLIS,
            localTime);
    final SafeFuture<Optional<ChainHead>> processHeadFuture = new SafeFuture<>();
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    when(localTime.getTimeInMillis())
        .thenReturn(startTimeMillis, startTimeMillis.plus(durationMillis));
    when(forkChoice.processHead(UInt64.ONE)).thenReturn(processHeadFuture);

    final CompletableFuture<Void> attestationsDueFuture =
        SafeFuture.runAsync(() -> localTrigger.onAttestationsDueForSlot(UInt64.ONE));
    processHeadFuture.complete(Optional.empty());

    attestationsDueFuture.get(5, TimeUnit.SECONDS);
  }

  @Test
  void shouldProcessHeadOnAttestationDue() {
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    trigger.onAttestationsDueForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
  }

  @Test
  void shouldRunForkChoicePriorToBlockProduction() {
    final SafeFuture<ChainHead> prepareFuture = new SafeFuture<>();
    when(forkChoice.prepareForBlockProduction(any(), any())).thenReturn(prepareFuture);
    final SafeFuture<ChainHead> result =
        trigger.prepareForBlockProduction(UInt64.ONE, BlockProductionPerformance.NOOP);
    assertThatSafeFuture(result).isNotDone();
    verify(forkChoice).prepareForBlockProduction(UInt64.ONE, BlockProductionPerformance.NOOP);

    prepareFuture.complete(null);
    assertThatSafeFuture(result).isCompleted();
  }
}
