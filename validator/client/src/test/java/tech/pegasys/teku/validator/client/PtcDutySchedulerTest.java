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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
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
  private static final Bytes32 HEAD_BLOCK_ROOT = Bytes32.repeat((byte) 1);
  private static final Bytes32 PREVIOUS_DUTY_DEPENDENT_ROOT = Bytes32.repeat((byte) 2);
  private static final Bytes32 CURRENT_DUTY_DEPENDENT_ROOT = Bytes32.repeat((byte) 3);

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
  void shouldNotPerformProductionForASlotWhichIsNotTheCurrentOne() {
    dutyScheduler = createDutyScheduler(spec);
    final UInt64 epoch = spec.computeEpochAtSlot(SLOT);
    dutyScheduler.onSlot(SLOT);
    requestedDutiesByEpoch.get(epoch).complete(Optional.of(duties));

    // a payload becoming available for an earlier slot, for example while the beacon node catches
    // up, produces an attestation which peers would ignore
    dutyScheduler.onPayloadAttestationCreationDue(SLOT.minus(1));

    verify(duties, never()).performProductionDuty(any());
  }

  @Test
  void shouldNotPerformProductionBeforeTheCurrentSlotIsKnown() {
    dutyScheduler = createDutyScheduler(spec);

    dutyScheduler.onPayloadAttestationCreationDue(SLOT);

    verify(duties, never()).performProductionDuty(any());
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
  void shouldNotRequestGloasForkEpochDutiesBeforeTheFork() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(3);
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch);
    final PayloadTimelinessCommitteeDutyScheduler transitionDutyScheduler =
        createDutyScheduler(transitionSpec);

    // the PTC of the fork epoch is only known once the state has been upgraded
    transitionDutyScheduler.onSlot(
        transitionSpec.computeStartSlotAtEpoch(gloasForkEpoch).decrement());
    assertThat(requestedDutiesByEpoch).isEmpty();

    transitionDutyScheduler.onSlot(transitionSpec.computeStartSlotAtEpoch(gloasForkEpoch));
    assertThat(requestedDutiesByEpoch).containsOnlyKeys(gloasForkEpoch, gloasForkEpoch.increment());
  }

  @Test
  void shouldExpectNextEpochDependentRootForGloasForkEpochDuties() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(3);
    final PayloadTimelinessCommitteeDutyScheduler transitionDutyScheduler =
        createDutyScheduler(TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch));

    // head in the fork epoch: next_epoch_dependent_root, the root at the last slot before the fork
    assertThat(
            transitionDutyScheduler.getExpectedDependentRoot(
                HEAD_BLOCK_ROOT,
                PREVIOUS_DUTY_DEPENDENT_ROOT,
                CURRENT_DUTY_DEPENDENT_ROOT,
                gloasForkEpoch,
                gloasForkEpoch))
        .isEqualTo(CURRENT_DUTY_DEPENDENT_ROOT);
    // head before the fork: the head block is the last block before the fork
    assertThat(
            transitionDutyScheduler.getExpectedDependentRoot(
                HEAD_BLOCK_ROOT,
                PREVIOUS_DUTY_DEPENDENT_ROOT,
                CURRENT_DUTY_DEPENDENT_ROOT,
                gloasForkEpoch.decrement(),
                gloasForkEpoch))
        .isEqualTo(HEAD_BLOCK_ROOT);
  }

  @Test
  void shouldExpectUsualDependentRootsForEpochsAfterGloasForkEpoch() {
    final UInt64 gloasForkEpoch = UInt64.valueOf(3);
    final UInt64 dutyEpoch = gloasForkEpoch.increment();
    final PayloadTimelinessCommitteeDutyScheduler transitionDutyScheduler =
        createDutyScheduler(TestSpecFactory.createMinimalWithGloasForkEpoch(gloasForkEpoch));

    assertThat(
            transitionDutyScheduler.getExpectedDependentRoot(
                HEAD_BLOCK_ROOT,
                PREVIOUS_DUTY_DEPENDENT_ROOT,
                CURRENT_DUTY_DEPENDENT_ROOT,
                dutyEpoch,
                dutyEpoch))
        .isEqualTo(PREVIOUS_DUTY_DEPENDENT_ROOT);
    assertThat(
            transitionDutyScheduler.getExpectedDependentRoot(
                HEAD_BLOCK_ROOT,
                PREVIOUS_DUTY_DEPENDENT_ROOT,
                CURRENT_DUTY_DEPENDENT_ROOT,
                gloasForkEpoch,
                dutyEpoch))
        .isEqualTo(CURRENT_DUTY_DEPENDENT_ROOT);
  }

  @Test
  void shouldTreatGenesisEpochAsGloasForkEpochWhenGloasAtGenesis() {
    dutyScheduler = createDutyScheduler(spec);

    // both dependent roots of a genesis epoch head underflow to the genesis block root
    assertThat(
            dutyScheduler.getExpectedDependentRoot(
                HEAD_BLOCK_ROOT,
                PREVIOUS_DUTY_DEPENDENT_ROOT,
                CURRENT_DUTY_DEPENDENT_ROOT,
                UInt64.ZERO,
                UInt64.ZERO))
        .isEqualTo(CURRENT_DUTY_DEPENDENT_ROOT);
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
