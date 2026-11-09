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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;

public class ForkSchedule {
  private static final Logger LOG = LogManager.getLogger();
  private final NavigableMap<UInt64, SpecMilestone> epochToMilestone;
  private final NavigableMap<UInt64, SpecMilestone> slotToMilestone;
  private final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone;
  private final Map<Bytes4, SpecMilestone> forkVersionToMilestone;
  private final EnumMap<SpecMilestone, Fork> milestoneToFork;
  private final EnumMap<SpecMilestone, Fork> fullMilestoneToForkMap;
  private final Fork genesisFork;

  private ForkSchedule(
      final Fork genesisFork,
      final NavigableMap<UInt64, SpecMilestone> epochToMilestone,
      final NavigableMap<UInt64, SpecMilestone> slotToMilestone,
      final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone,
      final Map<Bytes4, SpecMilestone> forkVersionToMilestone,
      final EnumMap<SpecMilestone, Fork> milestoneToFork,
      final EnumMap<SpecMilestone, Fork> fullMilestoneToForkMap) {
    this.genesisFork = genesisFork;
    this.epochToMilestone = epochToMilestone;
    this.slotToMilestone = slotToMilestone;
    this.genesisOffsetToMilestone = genesisOffsetToMilestone;
    this.forkVersionToMilestone = forkVersionToMilestone;
    this.milestoneToFork = milestoneToFork;
    this.fullMilestoneToForkMap = fullMilestoneToForkMap;
  }

  public int size() {
    return epochToMilestone.size();
  }

  public Stream<TekuPair<SpecMilestone, UInt64>> streamMilestoneBoundarySlots() {
    return slotToMilestone.entrySet().stream().map(e -> TekuPair.of(e.getValue(), e.getKey()));
  }

  /**
   * @return Milestones that are supported. Includes milestones that may be eclipsed by later
   *     milestones which are activated at the same epoch.
   */
  public List<SpecMilestone> getSupportedMilestones() {
    return SpecMilestone.getMilestonesUpTo(getHighestSupportedMilestone());
  }

  /**
   * @return The latest milestone that is supported
   */
  public SpecMilestone getHighestSupportedMilestone() {
    return epochToMilestone.lastEntry().getValue();
  }

  /**
   * @return Milestones that are actively transitioned to. Does not include milestones that are
   *     immediately eclipsed by later milestones that activate at the same epoch.
   */
  public List<ForkAndSpecMilestone> getActiveMilestones() {
    return milestoneToFork.entrySet().stream()
        .map(entry -> new ForkAndSpecMilestone(entry.getValue(), entry.getKey()))
        .sorted(Comparator.comparing(f -> f.getFork().getEpoch()))
        .toList();
  }

  public Fork getFork(final UInt64 epoch) {
    return milestoneToFork.get(getSpecMilestoneAtEpoch(epoch));
  }

  public Fork getFork(final SpecMilestone milestone) {
    return Optional.ofNullable(fullMilestoneToForkMap.get(milestone))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Milestone %s is not a part of fork schedule", milestone)));
  }

  public Fork getGenesisFork() {
    return genesisFork;
  }

  public Optional<Fork> getNextFork(final UInt64 epoch) {
    return Optional.ofNullable(epochToMilestone.ceilingEntry(epoch.plus(1)))
        .map(Map.Entry::getValue)
        .map(milestoneToFork::get);
  }

  /**
   * @return Include only active forks that have been configured. If 2 forks are activated at the
   *     same epoch, only the later milestone will be in the result.
   */
  public List<Fork> getForks() {
    return epochToMilestone.values().stream().map(milestoneToFork::get).toList();
  }

  /**
   * @return Includes all forks that have been configured. If 2 forks are defined at the same epoch,
   *     both will be listed in this result.
   */
  public List<Fork> getFullForkList() {
    return fullMilestoneToForkMap.values().stream().toList();
  }

  public void reportActivatingMilestones(final UInt64 epoch) {
    final SpecMilestone activatingMilestone = epochToMilestone.get(epoch);
    if (activatingMilestone == null) {
      return;
    }

    final String resourceName = activatingMilestone.name().toLowerCase(Locale.ROOT) + ".txt";
    String banner = "";
    try {
      banner =
          Resources.toString(this.getClass().getResource(resourceName), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      LOG.debug("failed to read resource file", ex);
    }
    EventLogger.EVENT_LOG.networkUpgradeActivated(epoch, activatingMilestone.name(), banner);
  }

  public SpecMilestone getSpecMilestoneAtEpoch(final UInt64 epoch) {
    return epochToMilestone.floorEntry(epoch).getValue();
  }

  public SpecMilestone getSpecMilestoneAtSlot(final UInt64 slot) {
    return slotToMilestone.floorEntry(slot).getValue();
  }

  public SpecMilestone getSpecMilestoneAtTime(final UInt64 genesisTime, final UInt64 currentTime) {
    final UInt64 genesisOffset = currentTime.minusMinZero(genesisTime);
    return genesisOffsetToMilestone.floorEntry(genesisOffset).getValue();
  }

  public Optional<SpecMilestone> getSpecMilestoneAtForkVersion(final Bytes4 forkVersion) {
    return Optional.ofNullable(forkVersionToMilestone.get(forkVersion));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkSchedule that = (ForkSchedule) o;
    return Objects.equals(milestoneToFork, that.milestoneToFork);
  }

  @Override
  public int hashCode() {
    return Objects.hash(milestoneToFork);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final NavigableMap<UInt64, SpecMilestone> epochToMilestone = new TreeMap<>();
    private final NavigableMap<UInt64, SpecMilestone> slotToMilestone = new TreeMap<>();
    private final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone = new TreeMap<>();
    private final Map<Bytes4, SpecMilestone> forkVersionToMilestone = new HashMap<>();
    private final EnumMap<SpecMilestone, Fork> milestoneToFork = new EnumMap<>(SpecMilestone.class);
    private final EnumMap<SpecMilestone, Fork> fullMilestoneToForkMap =
        new EnumMap<>(SpecMilestone.class);

    // Track info on the last processed milestone
    private Optional<Bytes4> prevForkVersion = Optional.empty();
    private Optional<SpecMilestone> prevMilestone = Optional.empty();
    private UInt64 prevMilestoneForkEpoch = UInt64.ZERO;
    private Fork genesisFork;

    private Builder() {}

    public ForkSchedule build() {
      checkState(!epochToMilestone.isEmpty(), "Must configure at least one milestone");
      return new ForkSchedule(
          genesisFork,
          epochToMilestone,
          slotToMilestone,
          genesisOffsetToMilestone,
          forkVersionToMilestone,
          milestoneToFork,
          fullMilestoneToForkMap);
    }

    public Builder addNextMilestone(final SpecVersion spec) {
      processMilestone(spec);
      return this;
    }

    private void processMilestone(final SpecVersion spec) {
      final SpecMilestone milestone = spec.getMilestone();
      final Optional<UInt64> maybeForkEpoch =
          SpecMilestone.getForkEpoch(spec.getConfig(), milestone);
      final Optional<Bytes4> maybeForkVersion =
          SpecMilestone.getForkVersion(spec.getConfig(), milestone);
      if (maybeForkEpoch.isEmpty() || maybeForkVersion.isEmpty()) {
        // This milestone is not enabled
        return;
      }

      // Current fork info
      final UInt64 forkEpoch = maybeForkEpoch.get();
      final Bytes4 forkVersion = maybeForkVersion.get();
      final UInt64 forkSlot = spec.miscHelpers().computeStartSlotAtEpoch(forkEpoch);
      final UInt64 genesisOffset = spec.miscHelpers().computeTimeAtSlot(UInt64.ZERO, forkSlot);
      final Fork fork = new Fork(prevForkVersion.orElse(forkVersion), forkVersion, forkEpoch);

      // Validate against prev fork
      if (epochToMilestone.isEmpty() && !forkSlot.equals(UInt64.ZERO)) {
        throw new IllegalArgumentException("Must provide genesis milestone first.");
      }

      if (forkSlot.equals(UInt64.ZERO)) {
        genesisFork = fork;
      }

      if (forkEpoch.isLessThan(prevMilestoneForkEpoch)) {
        final String msg =
            String.format(
                "Must provide milestones in order. Attempting to add milestone %s at epoch %s which is prior to the previously registered milestone at epoch %s",
                milestone, forkEpoch, prevMilestoneForkEpoch);
        throw new IllegalArgumentException(msg);
      }
      if (prevMilestone.isPresent()
          && !SpecMilestone.areMilestonesInOrder(prevMilestone.get(), milestone)) {
        throw new IllegalArgumentException("Attempt to process milestones out of order");
      }

      if (prevMilestone.isPresent() && prevMilestoneForkEpoch.equals(forkEpoch)) {
        // Clear out previous milestone data that is overshadowed by this milestone
        milestoneToFork.remove(prevMilestone.orElseThrow());
        forkVersionToMilestone.remove(prevForkVersion.orElseThrow());
        // Remaining mappings are naturally overwritten

        if (forkSlot.equals(UInt64.ZERO)) {
          genesisFork = new Fork(forkVersion, forkVersion, forkEpoch);
        }
      }

      // Track milestone
      epochToMilestone.put(forkEpoch, milestone);
      slotToMilestone.put(forkSlot, milestone);
      genesisOffsetToMilestone.put(genesisOffset, milestone);
      forkVersionToMilestone.put(forkVersion, milestone);
      milestoneToFork.put(milestone, fork);
      fullMilestoneToForkMap.put(milestone, fork);

      // Remember what we just processed
      prevMilestone = Optional.of(milestone);
      prevMilestoneForkEpoch = forkEpoch;
      prevForkVersion = Optional.of(forkVersion);
    }
  }
}
