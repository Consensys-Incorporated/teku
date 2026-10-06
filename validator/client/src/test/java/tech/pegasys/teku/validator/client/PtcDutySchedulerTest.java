/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;

class PtcDutySchedulerTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private final Map<UInt64, SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>>>
      requestedDutiesByEpoch = new HashMap<>();

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final DutyLoader<?> dutyLoader = mock(DutyLoader.class);

  private final SlotBasedScheduledDuties<?, ?> duties = createScheduledDuties();

  private PayloadTimelinessCommitteeDutyScheduler dutyScheduler;

  @BeforeEach
  public void setUp() {
    when(dutyLoader.loadDutiesForEpoch(any()))
        .thenAnswer(
            invocation -> {
              final UInt64 epoch = invocation.getArgument(0);
              return requestedDutiesByEpoch.computeIfAbsent(epoch, __ -> new SafeFuture<>());
            });
  }

  @Test
  void shouldPerformProductionWhenPayloadAttestationCreationDue() {
    dutyScheduler = createDutyScheduler(spec);
    final UInt64 epoch = spec.computeEpochAtSlot(SLOT);
    // calculate pending duties
    dutyScheduler.onSlot(SLOT);

    requestedDutiesByEpoch.get(epoch).complete(Optional.of(duties));

    dutyScheduler.onPayloadAttestationCreationDue(SLOT);

    verify(duties).performProductionDuty(SLOT);
  }

  @Test
  void shouldNotRequestDutiesBeforeGloas() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(3);
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
    final PayloadTimelinessCommitteeDutyScheduler transitionDutyScheduler =
        createDutyScheduler(transitionSpec);

    transitionDutyScheduler.onSlot(transitionSpec.computeStartSlotAtEpoch(UInt64.ONE));

    assertThat(requestedDutiesByEpoch).isEmpty();
  }

  @Test
  void shouldOnlyRequestGloasDutiesWhenLookaheadIncludesGloas() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(3);
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
    final PayloadTimelinessCommitteeDutyScheduler transitionDutyScheduler =
        createDutyScheduler(transitionSpec);

    transitionDutyScheduler.onSlot(
        transitionSpec.computeStartSlotAtEpoch(gloasForkEpoch.decrement()));

    assertThat(requestedDutiesByEpoch).containsOnlyKeys(gloasForkEpoch);
  }

  @Test
  void shouldRequestCurrentAndLookaheadDutiesAtGloas() {
    dutyScheduler = createDutyScheduler(spec);
    final UInt64 gloasForkEpoch = spec.getForkSchedule().getFork(SpecMilestone.GLOAS).getEpoch();

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(gloasForkEpoch));

    assertThat(requestedDutiesByEpoch).containsOnlyKeys(gloasForkEpoch, gloasForkEpoch.increment());
  }

  private PayloadTimelinessCommitteeDutyScheduler createDutyScheduler(final Spec spec) {
    return new PayloadTimelinessCommitteeDutyScheduler(metricsSystem, dutyLoader, spec);
  }

  private SlotBasedScheduledDuties<?, ?> createScheduledDuties() {
    final SlotBasedScheduledDuties<?, ?> duties = mock(SlotBasedScheduledDuties.class);
    when(duties.performProductionDuty(any())).thenReturn(new SafeFuture<>());
    return duties;
  }
}
