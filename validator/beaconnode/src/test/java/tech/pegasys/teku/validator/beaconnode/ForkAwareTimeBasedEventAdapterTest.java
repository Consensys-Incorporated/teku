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

package tech.pegasys.teku.validator.beaconnode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class ForkAwareTimeBasedEventAdapterTest {

  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private final ValidatorTimingChannel validatorTimingChannel = mock(ValidatorTimingChannel.class);

  @Test
  void shouldScheduleEventsOnceGenesisIsKnown() {
    final Spec spec = TestSpecFactory.createMinimalAltair();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final SafeFuture<UInt64> genesisTimeFuture = new SafeFuture<>();
    when(genesisDataProvider.getGenesisTime()).thenReturn(genesisTimeFuture);

    final SafeFuture<Void> startResult = eventAdapter.start();
    // Returned future completes immediately so startup can complete pre-genesis
    assertThat(startResult).isCompleted();
    // But no slot timings have been scheduled yet
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // Once we get the genesis time, we can schedule the slot events
    genesisTimeFuture.complete(UInt64.ONE);
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
  }

  static Stream<Arguments> forkTransitions() {
    return Stream.of(
        Arguments.of(
            "Phase0 to Altair",
            (Supplier<Spec>) () -> TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE)),
        Arguments.of(
            "Altair to Gloas",
            (Supplier<Spec>) () -> TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE)),
        Arguments.of(
            "Gloas to Heze",
            (Supplier<Spec>) () -> TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("forkTransitions")
  void shouldTransitionAtForkBoundary(final String name, final Supplier<Spec> specFactory) {
    final Spec spec = specFactory.get();
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    // Fork at epoch 1 → slot 8 (minimal: 8 slots/epoch). Start 2 slots before boundary.
    final UInt64 preForkSlot = UInt64.valueOf(7);
    final UInt64 postForkSlot = UInt64.valueOf(8);
    final int preForkMillisPerSlot = spec.atSlot(preForkSlot).getConfig().getSlotDurationMillis();
    timeProvider.advanceTimeBySeconds(secondsPerSlot * 6L);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(validatorTimingChannel);

    // --- Pre-fork slot (7) ---
    timeProvider.advanceTimeByMillis(preForkMillisPerSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(preForkSlot);
    verify(validatorTimingChannel).onBlockProductionDue(preForkSlot);
    final long preForkAdvancedMillis =
        SlotEventVerifierTestUtil.verifySlotEvents(
            spec, timeProvider, asyncRunner, validatorTimingChannel, preForkSlot);

    // --- Post-fork slot (8) ---
    timeProvider.advanceTimeByMillis(preForkMillisPerSlot - preForkAdvancedMillis);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(postForkSlot);
    verify(validatorTimingChannel).onBlockProductionDue(postForkSlot);
    SlotEventVerifierTestUtil.verifySlotEvents(
        spec, timeProvider, asyncRunner, validatorTimingChannel, postForkSlot);
  }

  @Test
  void shouldUseHezeSlotDurationAfterEip8198SlotDurationChange() {
    // 6000 ms slots, then 5000 ms slots from Heze at epoch 1 (slot 8)
    final Spec spec =
        TestSpecFactory.createMinimalHeze(
            builder -> builder.hezeForkEpoch(UInt64.ONE).slotDurationMsHeze(5000));

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);
    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(timeProvider.getTimeInSeconds()));

    timeProvider.advanceTimeBySeconds(6L * 6);
    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoInteractions(validatorTimingChannel);

    // slot 7 starts after a 6000 ms slot
    timeProvider.advanceTimeByMillis(6000);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(7));

    // slot 8, the first Heze slot, starts 6000 ms later
    timeProvider.advanceTimeByMillis(5999);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onSlot(UInt64.valueOf(8));
    timeProvider.advanceTimeByMillis(1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(8));
    final long advancedMillis =
        SlotEventVerifierTestUtil.verifySlotEvents(
            spec, timeProvider, asyncRunner, validatorTimingChannel, UInt64.valueOf(8));

    // slot 9 starts only 5000 ms after slot 8
    timeProvider.advanceTimeByMillis(5000 - advancedMillis - 1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onSlot(UInt64.valueOf(9));
    timeProvider.advanceTimeByMillis(1);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(9));
  }

  static Stream<Arguments> singleMilestoneStartups() {
    return Stream.of(
        Arguments.of(
            "Phase0-only network", (Supplier<Spec>) TestSpecFactory::createMinimalPhase0, 25L),
        Arguments.of(
            "Mid-chain Altair with Gloas scheduled",
            (Supplier<Spec>)
                () -> TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(3)),
            11L),
        Arguments.of(
            "Gloas from genesis", (Supplier<Spec>) TestSpecFactory::createMinimalGloas, 25L),
        Arguments.of("Heze from genesis", (Supplier<Spec>) TestSpecFactory::createMinimalHeze, 25L),
        Arguments.of(
            "Electra no Gloas", (Supplier<Spec>) TestSpecFactory::createMinimalElectra, 25L),
        Arguments.of(
            "Pre-Gloas ALTAIR slot",
            (Supplier<Spec>) () -> TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE),
            2L),
        Arguments.of(
            "Starting at ALTAIR fork boundary",
            (Supplier<Spec>) () -> TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE),
            8L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("singleMilestoneStartups")
  void shouldFireCorrectEventsForSlotInMilestone(
      final String name, final Supplier<Spec> specFactory, final long nextSlot) {
    final Spec spec = specFactory.get();
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RepeatingTaskScheduler scheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);

    final ForkAwareTimeBasedEventAdapter eventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            genesisDataProvider, scheduler, timeProvider, validatorTimingChannel, spec);

    final UInt64 genesisTime = timeProvider.getTimeInSeconds();
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(genesisTime));

    final UInt64 nextSlotUInt = UInt64.valueOf(nextSlot);
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    assertThat(eventAdapter.start()).isCompleted();
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Advance to slot start — slot + block production should fire
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(nextSlotUInt);
    verify(validatorTimingChannel).onBlockProductionDue(nextSlotUInt);

    SlotEventVerifierTestUtil.verifySlotEvents(
        spec, timeProvider, asyncRunner, validatorTimingChannel, nextSlotUInt);
  }
}
