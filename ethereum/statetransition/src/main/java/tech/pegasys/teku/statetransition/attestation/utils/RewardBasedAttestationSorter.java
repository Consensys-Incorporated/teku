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

package tech.pegasys.teku.statetransition.attestation.utils;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_HEAD_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_SOURCE_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_TARGET_WEIGHT;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_HEAD_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_SOURCE_FLAG_INDEX;
import static tech.pegasys.teku.spec.constants.ParticipationFlags.TIMELY_TARGET_FLAG_INDEX;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.statetransition.attestation.PooledAttestationWithData;

public class RewardBasedAttestationSorter {
  private static final Logger LOG = LogManager.getLogger();

  // The NOOP sorter just has to apply the limit.
  public static final RewardBasedAttestationSorter NOOP =
      new RewardBasedAttestationSorter(null, null, null, null, false) {
        @Override
        public List<PooledAttestationWithRewardInfo> sort(
            final List<PooledAttestationWithData> attestations, final int maxAttestations) {
          return attestations.stream()
              .map(PooledAttestationWithRewardInfo::empty)
              .limit(maxAttestations)
              .toList();
        }
      };

  static final Comparator<PooledAttestationWithRewardInfo> REWARD_COMPARATOR =
      Comparator.<PooledAttestationWithRewardInfo>comparingLong(
              value -> value.rewardNumerator.longValue())
          .reversed();

  private final Spec spec;
  private final BeaconStateAltair state;
  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final MiscHelpersAltair miscHelpers;

  private MutableEpochParticipation currentEpochParticipation;
  private MutableEpochParticipation previousEpochParticipation;

  private final boolean forceSorting;

  public static RewardBasedAttestationSorter create(final Spec spec, final BeaconState state) {
    final SpecVersion specVersion = spec.atSlot(state.getSlot());

    if (specVersion.getMilestone().isLessThan(SpecMilestone.ALTAIR)) {
      // sorter only supports altair and later
      return NOOP;
    }

    return new RewardBasedAttestationSorter(
        spec,
        BeaconStateAltair.required(state),
        BeaconStateAccessorsAltair.required(specVersion.beaconStateAccessors()),
        specVersion.miscHelpers().toVersionAltair().orElseThrow(),
        false);
  }

  /*
   * Creates a sorter for testing purposes, which will always sort the attestations (which means it calculates rewards).
   */
  public static RewardBasedAttestationSorter createForReferenceTest(
      final Spec spec, final BeaconState state) {
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    return new RewardBasedAttestationSorter(
        spec,
        BeaconStateAltair.required(state),
        BeaconStateAccessorsAltair.required(specVersion.beaconStateAccessors()),
        specVersion.miscHelpers().toVersionAltair().orElseThrow(),
        true);
  }

  protected RewardBasedAttestationSorter(
      final Spec spec,
      final BeaconStateAltair state,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final MiscHelpersAltair miscHelpers,
      final boolean forceSorting) {
    this.spec = spec;
    this.state = state;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
    this.forceSorting = forceSorting;
  }

  private MutableEpochParticipation getCurrentEpochParticipation() {
    if (currentEpochParticipation == null) {
      currentEpochParticipation =
          MutableEpochParticipation.create(state.getCurrentEpochParticipation());
    }
    return currentEpochParticipation;
  }

  private MutableEpochParticipation getPreviousEpochParticipation() {
    if (previousEpochParticipation == null) {
      previousEpochParticipation =
          MutableEpochParticipation.create(state.getPreviousEpochParticipation());
    }
    return previousEpochParticipation;
  }

  private MutableEpochParticipation getEpochParticipation(
      final PooledAttestationWithRewardInfo attestation) {
    return attestation.isCurrentEpoch
        ? getCurrentEpochParticipation()
        : getPreviousEpochParticipation();
  }

  public List<PooledAttestationWithRewardInfo> sort(
      final List<PooledAttestationWithData> attestations, final int maxAttestations) {

    if (!forceSorting && attestations.size() <= maxAttestations) {
      LOG.debug(
          "Skipping sorting as the number of attestations is less than or equal to the limit.");
      return attestations.stream()
          .map(PooledAttestationWithRewardInfo::empty)
          .collect(Collectors.toList());
    }

    final long start = System.nanoTime();
    final List<PooledAttestationWithRewardInfo> finalSortedAttestations =
        new ArrayList<>(maxAttestations);

    final PriorityQueue<PooledAttestationWithRewardInfo> attestationQueue =
        new PriorityQueue<>(REWARD_COMPARATOR);

    attestations.stream()
        .map(this::initializeRewardInfo)
        .peek(this::computeRewards)
        .forEach(attestationQueue::add);

    if (attestationQueue.isEmpty()) {
      return finalSortedAttestations;
    }

    final long initializationEnded = System.nanoTime();
    LOG.debug(
        "Sorting initialization took {} ms.", () -> (initializationEnded - start) / 1_000_000);

    while (true) {
      final PooledAttestationWithRewardInfo bestAttestation = attestationQueue.poll();
      finalSortedAttestations.add(bestAttestation);

      // we reached the limit or there are no more attestations to process
      if (finalSortedAttestations.size() >= maxAttestations || attestationQueue.isEmpty()) {
        LOG.debug(
            "Sorting took: {} ms", () -> (System.nanoTime() - initializationEnded) / 1_000_000);
        return finalSortedAttestations;
      }

      // apply participation changes
      final MutableEpochParticipation affectedParticipation =
          getEpochParticipation(bestAttestation);
      if (bestAttestation.updatesEpochParticipation.isEmpty()) {
        // no changes to participation
        continue;
      }

      bestAttestation.updatesEpochParticipation.forEach(affectedParticipation::setParticipation);

      final List<PooledAttestationWithRewardInfo> toReAdd = new ArrayList<>();

      // recalculate rewards for affected attestations
      for (final PooledAttestationWithRewardInfo potentiallyAffected : attestationQueue) {
        if (potentiallyAffected.isCurrentEpoch == bestAttestation.isCurrentEpoch) {
          computeRewards(potentiallyAffected);
          toReAdd.add(potentiallyAffected);
        }
      }

      if (!toReAdd.isEmpty()) {
        // make sure PriorityQueue reevaluates the order
        attestationQueue.removeAll(toReAdd);
        attestationQueue.addAll(toReAdd);
      }
    }
  }

  // Base rewards are priced at the attestation's target epoch (EIP-8198), which is either the
  // current or the previous epoch
  private final Int2ObjectOpenHashMap<UInt64> currentEpochBaseRewardCache =
      new Int2ObjectOpenHashMap<>();
  private final Int2ObjectOpenHashMap<UInt64> previousEpochBaseRewardCache =
      new Int2ObjectOpenHashMap<>();

  private UInt64 getValidatorBaseRewards(final int index, final boolean isCurrentEpoch) {
    final Int2ObjectOpenHashMap<UInt64> cache =
        isCurrentEpoch ? currentEpochBaseRewardCache : previousEpochBaseRewardCache;
    return cache.computeIfAbsent(
        index,
        k ->
            beaconStateAccessors.getBaseReward(
                state,
                k,
                isCurrentEpoch
                    ? beaconStateAccessors.getCurrentEpoch(state)
                    : beaconStateAccessors.getPreviousEpoch(state)));
  }

  private PooledAttestationWithRewardInfo initializeRewardInfo(
      final PooledAttestationWithData attestation) {
    final boolean isCurrentEpoch =
        attestation.data().getTarget().getEpoch().equals(spec.getCurrentEpoch(state));

    final List<Integer> attestationParticipationFlagIndices =
        beaconStateAccessors.getAttestationParticipationFlagIndices(
            state, attestation.data(), state.getSlot().minusMinZero(attestation.data().getSlot()));

    return new PooledAttestationWithRewardInfo(
        attestation,
        attestationParticipationFlagIndices.contains(TIMELY_SOURCE_FLAG_INDEX),
        attestationParticipationFlagIndices.contains(TIMELY_TARGET_FLAG_INDEX),
        attestationParticipationFlagIndices.contains(TIMELY_HEAD_FLAG_INDEX),
        Map.of(),
        isCurrentEpoch,
        UInt64.ZERO);
  }

  private void computeRewards(final PooledAttestationWithRewardInfo attestation) {
    final MutableEpochParticipation epochParticipation = getEpochParticipation(attestation);
    final Int2ByteOpenHashMap updatesEpochParticipation = new Int2ByteOpenHashMap();

    UInt64 proposerRewardNumerator = UInt64.ZERO;

    for (final UInt64 attestingIndex :
        attestation.getAttestation().pooledAttestation().validatorIndices().orElseThrow()) {
      final int attestingIndexInt = attestingIndex.intValue();
      final byte previousParticipationFlags =
          epochParticipation.getParticipation(attestingIndexInt);
      byte newParticipationFlags = 0;

      final UInt64 baseReward =
          getValidatorBaseRewards(attestingIndexInt, attestation.isCurrentEpoch);

      if (attestation.timelySource
          && !miscHelpers.hasFlag(previousParticipationFlags, TIMELY_SOURCE_FLAG_INDEX)) {
        newParticipationFlags =
            miscHelpers.addFlag(newParticipationFlags, TIMELY_SOURCE_FLAG_INDEX);
        proposerRewardNumerator =
            proposerRewardNumerator.plus(baseReward.times(TIMELY_SOURCE_WEIGHT));
      }

      if (attestation.timelyTarget
          && !miscHelpers.hasFlag(previousParticipationFlags, TIMELY_TARGET_FLAG_INDEX)) {
        newParticipationFlags =
            miscHelpers.addFlag(newParticipationFlags, TIMELY_TARGET_FLAG_INDEX);
        proposerRewardNumerator =
            proposerRewardNumerator.plus(baseReward.times(TIMELY_TARGET_WEIGHT));
      }

      if (attestation.timelyHead
          && !miscHelpers.hasFlag(previousParticipationFlags, TIMELY_HEAD_FLAG_INDEX)) {
        newParticipationFlags = miscHelpers.addFlag(newParticipationFlags, TIMELY_HEAD_FLAG_INDEX);
        proposerRewardNumerator =
            proposerRewardNumerator.plus(baseReward.times(TIMELY_HEAD_WEIGHT));
      }

      if (newParticipationFlags != 0) {
        updatesEpochParticipation.put(
            attestingIndexInt,
            miscHelpers.addFlags(previousParticipationFlags, newParticipationFlags));
      }
    }

    attestation.rewardNumerator = proposerRewardNumerator;
    attestation.updatesEpochParticipation = updatesEpochParticipation;
  }

  private record MutableEpochParticipation(
      SszList<SszByte> epochParticipation, Int2ByteOpenHashMap epochParticipationCacheWithChanges) {

    static MutableEpochParticipation create(final SszList<SszByte> epochParticipation) {
      return new MutableEpochParticipation(epochParticipation, new Int2ByteOpenHashMap());
    }

    byte getParticipation(final int index) {
      return epochParticipationCacheWithChanges.computeIfAbsent(
          index, i -> epochParticipation.get(i).get());
    }

    void setParticipation(final int index, final byte value) {
      epochParticipationCacheWithChanges.put(index, value);
    }
  }

  public static class PooledAttestationWithRewardInfo {
    private final PooledAttestationWithData attestation;
    private final boolean timelySource;
    private final boolean timelyTarget;
    private final boolean timelyHead;
    private final boolean isCurrentEpoch;

    private Map<Integer, Byte> updatesEpochParticipation;
    private UInt64 rewardNumerator;

    public static PooledAttestationWithRewardInfo empty(
        final PooledAttestationWithData attestation) {
      return new PooledAttestationWithRewardInfo(
          attestation, false, false, false, Map.of(), false, UInt64.ZERO);
    }

    public PooledAttestationWithRewardInfo withAttestation(
        final PooledAttestationWithData attestation) {
      return new PooledAttestationWithRewardInfo(
          attestation,
          this.timelySource,
          this.timelyTarget,
          this.timelyHead,
          this.updatesEpochParticipation,
          this.isCurrentEpoch,
          this.rewardNumerator);
    }

    private PooledAttestationWithRewardInfo(
        final PooledAttestationWithData attestation,
        final boolean timelySource,
        final boolean timelyTarget,
        final boolean timelyHead,
        final Map<Integer, Byte> updatesEpochParticipation,
        final boolean isCurrentEpoch,
        final UInt64 rewardNumerator) {
      this.attestation = attestation;
      this.timelySource = timelySource;
      this.timelyTarget = timelyTarget;
      this.timelyHead = timelyHead;
      this.updatesEpochParticipation = updatesEpochParticipation;
      this.isCurrentEpoch = isCurrentEpoch;
      this.rewardNumerator = rewardNumerator;
    }

    public PooledAttestationWithData getAttestation() {
      return attestation;
    }

    public UInt64 getRewardNumerator() {
      return rewardNumerator;
    }
  }

  @FunctionalInterface
  public interface RewardBasedAttestationSorterFactory {
    RewardBasedAttestationSorterFactory DEFAULT = RewardBasedAttestationSorter::create;

    RewardBasedAttestationSorterFactory NOOP = (spec, state) -> RewardBasedAttestationSorter.NOOP;

    RewardBasedAttestationSorter create(final Spec spec, final BeaconState state);
  }
}
