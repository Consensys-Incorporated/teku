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

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.EnumMap;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

/**
 * Coordinates per-milestone {@link TimeBasedEventAdapter} instances stored in an {@link EnumMap}
 * keyed by the milestone that introduces a timing change (PHASE0, ALTAIR, GLOAS). Only three timing
 * profiles exist today:
 *
 * <ul>
 *   <li>PHASE0 (attestation, aggregation)
 *   <li>ALTAIR–FULU (adds sync committee, contribution)
 *   <li>GLOAS (changes offsets, adds payload attestation)
 * </ul>
 *
 * <p>Each adapter schedules all of its events (including the slot event) with an expiry at the next
 * timing-changing fork boundary. When the slot event expires, its {@code onLastSlot} callback
 * activates the next adapter in the chain for the first slot of the new era. Duty events expire
 * silently without triggering a transition.
 */
public class ForkAwareTimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final GenesisDataProvider genesisDataProvider;
  private final TimeProvider timeProvider;
  private final Spec spec;

  private UInt64 genesisTimeMillis;

  // Keyed by the milestone that introduces a timing change: PHASE0, ALTAIR, GLOAS
  private final EnumMap<SpecMilestone, TimeBasedEventAdapter> adapters =
      new EnumMap<>(SpecMilestone.class);

  public ForkAwareTimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    this.genesisDataProvider = genesisDataProvider;
    this.timeProvider = timeProvider;
    this.spec = spec;

    adapters.put(
        SpecMilestone.PHASE0,
        new Phase0TimeBasedEventAdapter(
            taskScheduler, validatorTimingChannel, this::activateAltair, spec));

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      adapters.put(
          SpecMilestone.ALTAIR,
          new AltairTimeBasedEventAdapter(
              taskScheduler, validatorTimingChannel, this::activateGloas, spec));
    }

    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      adapters.put(
          SpecMilestone.GLOAS,
          new GloasTimeBasedEventAdapter(taskScheduler, validatorTimingChannel, () -> {}, spec));
    }
  }

  private void activatePhase0() {
    activateAdapter(
        adapters.get(SpecMilestone.PHASE0),
        Optional.ofNullable(adapters.get(SpecMilestone.ALTAIR)));
  }

  private void activateAltair() {
    activateAdapter(
        adapters.get(SpecMilestone.ALTAIR), Optional.ofNullable(adapters.get(SpecMilestone.GLOAS)));
  }

  private void activateGloas() {
    activateAdapter(adapters.get(SpecMilestone.GLOAS), Optional.empty());
  }

  @Override
  public SafeFuture<Void> start() {
    genesisDataProvider.getGenesisTime().thenAccept(this::startWithGenesisTime).finishError(LOG);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }

  void startWithGenesisTime(final UInt64 genesisTime) {
    this.genesisTimeMillis = secondsToMillis(genesisTime);
    final UInt64 nextSlot = getCurrentNextSlot();
    final SpecMilestone currentMilestone = spec.atSlot(nextSlot).getMilestone();

    // Set genesis time on all adapters
    adapters.values().forEach(adapter -> adapter.setGenesisTimeMillis(genesisTimeMillis));

    switch (currentMilestone) {
      case PHASE0 -> activatePhase0();
      case ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU -> activateAltair();
      case GLOAS, HEZE -> activateGloas();
    }
  }

  private void activateAdapter(
      final TimeBasedEventAdapter adapter, final Optional<TimeBasedEventAdapter> maybeNextAdapter) {
    final UInt64 nextSlot = getCurrentNextSlot();
    final UInt64 nextSlotStartMillis = getSlotStartTimeMillis(nextSlot);
    final Optional<UInt64> expiryMillis =
        maybeNextAdapter.map(nextAdapter -> getSlotStartTimeMillis(nextAdapter.getFirstSlot()));
    adapter.scheduleDuties(nextSlotStartMillis, expiryMillis);
  }

  private UInt64 getCurrentNextSlot() {
    return spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis)
        .increment();
  }

  private UInt64 getSlotStartTimeMillis(final UInt64 slot) {
    return spec.computeTimeMillisAtSlot(slot, genesisTimeMillis);
  }
}
