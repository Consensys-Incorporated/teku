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

package tech.pegasys.teku.validator.beaconnode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

class TimeBasedEventAdapterTest {

  private StubTimeProvider timeProvider;
  private StubAsyncRunner asyncRunner;
  private RepeatingTaskScheduler taskScheduler;
  private ValidatorTimingChannel validatorTimingChannel;

  @BeforeEach
  void setUp() {
    timeProvider = StubTimeProvider.withTimeInSeconds(100);
    asyncRunner = new StubAsyncRunner(timeProvider);
    taskScheduler = new RepeatingTaskScheduler(asyncRunner, timeProvider);
    validatorTimingChannel = mock(ValidatorTimingChannel.class);
  }

  static Stream<Arguments> milestones() {
    return Stream.of(
        Arguments.of("PHASE0", (Supplier<Spec>) TestSpecFactory::createMinimalPhase0),
        Arguments.of("ALTAIR", (Supplier<Spec>) TestSpecFactory::createMinimalAltair),
        Arguments.of("GLOAS", (Supplier<Spec>) TestSpecFactory::createMinimalGloas));
  }

  private TimeBasedEventAdapter createAdapter(final Spec spec, final Runnable onLastSlot) {
    final SpecMilestone milestone = spec.atSlot(UInt64.ZERO).getMilestone();
    return switch (milestone) {
      case PHASE0 ->
          new Phase0TimeBasedEventAdapter(taskScheduler, validatorTimingChannel, onLastSlot, spec);
      case ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU ->
          new AltairTimeBasedEventAdapter(taskScheduler, validatorTimingChannel, onLastSlot, spec);
      case GLOAS, HEZE ->
          new GloasTimeBasedEventAdapter(taskScheduler, validatorTimingChannel, onLastSlot, spec);
    };
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("milestones")
  void shouldScheduleExpectedEventsWithCorrectTiming(
      final String name, final Supplier<Spec> specFactory) {
    final Spec spec = specFactory.get();
    final TimeBasedEventAdapter adapter = createAdapter(spec, () -> {});
    final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();
    final UInt64 genesisTimeMillis = timeProvider.getTimeInMillis();
    final long nextSlot = 25;
    final UInt64 nextSlotUInt = UInt64.valueOf(nextSlot);
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    startAdapter(adapter, genesisTimeMillis, spec);

    // Should not fire any events immediately
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("milestones")
  void shouldCallOnLastSlotExactlyOnceWhenEventsExpire(
      final String name, final Supplier<Spec> specFactory) {
    final Spec spec = specFactory.get();
    final Runnable onLastSlotCallback = mock(Runnable.class);
    final TimeBasedEventAdapter adapter = createAdapter(spec, onLastSlotCallback);
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int secondsPerSlot = millisPerSlot / 1000;

    final UInt64 genesisTimeMillis = timeProvider.getTimeInMillis();
    adapter.setGenesisTimeMillis(genesisTimeMillis);

    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot / 2;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    final UInt64 nextSlotStartMillis =
        UInt64.valueOf(genesisTimeMillis.longValue() + (nextSlot * millisPerSlot));
    final UInt64 expiryMillis =
        UInt64.valueOf(genesisTimeMillis.longValue() + ((nextSlot + 1) * millisPerSlot));

    adapter.scheduleDuties(nextSlotStartMillis, Optional.of(expiryMillis));

    // Advance to slot start — slot event fires and then expires, triggering onLastSlot
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel).onSlot(UInt64.valueOf(nextSlot));
    verify(onLastSlotCallback, times(1)).run();

    // Advance through rest of slot — duties fire and expire, but onLastSlot is NOT called again
    timeProvider.advanceTimeBySeconds(secondsPerSlot);
    asyncRunner.executeDueActionsRepeatedly();
    verify(onLastSlotCallback, times(1)).run();
  }

  @Test
  void shouldComputeMillisOffsetsCorrectlyForNonDivisibleSlotDuration() {
    // Gnosis has 5-second slots (not divisible by 3), verifying millis-based offsets
    final Spec gnosisSpec = TestSpecFactory.create(SpecMilestone.ALTAIR, Eth2Network.GNOSIS);
    final int secondsPerSlot = gnosisSpec.getGenesisSpecConfig().getSecondsPerSlot();
    assertThat(secondsPerSlot % 3).isNotZero();

    final TimeBasedEventAdapter adapter =
        new AltairTimeBasedEventAdapter(
            taskScheduler, validatorTimingChannel, () -> {}, gnosisSpec);

    final UInt64 genesisTimeMillis = timeProvider.getTimeInMillis();
    final long nextSlot = 25;
    final int timeUntilNextSlot = secondsPerSlot - 1;
    timeProvider.advanceTimeBySeconds(secondsPerSlot * nextSlot - timeUntilNextSlot);

    startAdapter(adapter, genesisTimeMillis, gnosisSpec);
    asyncRunner.executeDueActionsRepeatedly();
    verifyNoMoreInteractions(validatorTimingChannel);

    // Advance to slot start
    timeProvider.advanceTimeBySeconds(timeUntilNextSlot);
    asyncRunner.executeDueActionsRepeatedly();

    // Attestation should not fire at 1/3 seconds (integer division rounds down)
    timeProvider.advanceTimeBySeconds(secondsPerSlot / 3);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationCreationDue(UInt64.valueOf(nextSlot));

    // But does fire at 1/3 millis (precise offset)
    final long alreadyPassedMillis = (long) (secondsPerSlot / 3) * 1000;
    final long oneThirdMillis = (secondsPerSlot * 1000L) / 3;
    timeProvider.advanceTimeByMillis(oneThirdMillis - alreadyPassedMillis);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationCreationDue(UInt64.valueOf(nextSlot));

    // Aggregation should not fire at 2/3 seconds (integer division rounds down)
    final long twoThirdsSeconds = (long) (secondsPerSlot / 3) * 2 * 1000;
    timeProvider.advanceTimeByMillis(twoThirdsSeconds - oneThirdMillis);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, never()).onAttestationAggregationDue(UInt64.valueOf(nextSlot));

    // But does fire at 2/3 millis (precise offset)
    final long twoThirdsMillis = (secondsPerSlot * 2000L) / 3;
    timeProvider.advanceTimeByMillis(twoThirdsMillis - twoThirdsSeconds);
    asyncRunner.executeDueActionsRepeatedly();
    verify(validatorTimingChannel, times(1)).onAttestationAggregationDue(UInt64.valueOf(nextSlot));
  }

  private void startAdapter(
      final TimeBasedEventAdapter adapter, final UInt64 genesisTimeMillis, final Spec spec) {

    adapter.setGenesisTimeMillis(genesisTimeMillis);

    final UInt64 currentSlot =
        spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis);
    final UInt64 nextSlotStartTimeMillis =
        spec.computeTimeMillisAtSlot(currentSlot.increment(), genesisTimeMillis);

    adapter.scheduleDuties(nextSlotStartTimeMillis, Optional.empty());
  }
}
