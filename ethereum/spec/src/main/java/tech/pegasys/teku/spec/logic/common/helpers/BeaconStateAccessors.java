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

package tech.pegasys.teku.spec.logic.common.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.collections.TekuPair;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;

public abstract class BeaconStateAccessors {
  protected final SpecConfig config;
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;

  protected BeaconStateAccessors(
      final SpecConfig config, final Predicates predicates, final MiscHelpers miscHelpers) {
    this.config = config;
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
  }

  public UInt64 getCurrentEpoch(final BeaconState state) {
    return miscHelpers.computeEpochAtSlot(state.getSlot());
  }

  public UInt64 getPreviousEpoch(final BeaconState state) {
    UInt64 currentEpoch = getCurrentEpoch(state);
    return currentEpoch.equals(GENESIS_EPOCH) ? GENESIS_EPOCH : currentEpoch.minus(UInt64.ONE);
  }

  public UInt64 getAttestationParentSlot(final BeaconState state) {
    return state.getLatestBlockHeader().getSlot();
  }

  public UInt64 getValidatorChurnLimit(final BeaconState state) {
    final int activeValidatorCount =
        getActiveValidatorIndices(state, getCurrentEpoch(state)).size();
    return getValidatorChurnLimit(activeValidatorCount);
  }

  public UInt64 getValidatorChurnLimit(final int activeValidatorCount) {
    return UInt64.valueOf(config.getMinPerEpochChurnLimit())
        .max(UInt64.valueOf(activeValidatorCount / config.getChurnLimitQuotient()));
  }

  public UInt64 getValidatorActivationChurnLimit(final BeaconState state) {
    return getValidatorChurnLimit(state);
  }

  public Optional<BLSPublicKey> getValidatorPubKey(
      final BeaconState state, final UInt64 validatorIndex) {
    if (validatorIndex.isGreaterThanOrEqualTo(state.getValidators().size())
        || validatorIndex.longValue() < 0) {
      return Optional.empty();
    }
    return Optional.of(
        BeaconStateCache.getTransitionCaches(state)
            .getValidatorsPubKeys()
            .get(
                validatorIndex,
                i -> {
                  final BLSPublicKey pubKey =
                      state.getValidators().get(i.intValue()).getPublicKey();

                  // eagerly pre-cache pubKey => validatorIndex mapping
                  BeaconStateCache.getTransitionCaches(state)
                      .getValidatorIndexCache()
                      .invalidateWithNewValue(pubKey, i.intValue());
                  return pubKey;
                }));
  }

  /**
   * Get active validator indices at ``epoch``.
   *
   * @param state - Current BeaconState
   * @param epoch - The epoch under consideration.
   * @return A list of indices representing the active validators for the given epoch.
   */
  public IntList getActiveValidatorIndices(final BeaconState state, final UInt64 epoch) {
    final UInt64 stateEpoch = getCurrentEpoch(state);
    final UInt64 maxLookaheadEpoch = getMaxLookaheadEpoch(stateEpoch);
    checkArgument(
        epoch.isLessThanOrEqualTo(maxLookaheadEpoch),
        "Cannot get active validator indices from an epoch beyond the seed lookahead period. Requested epoch %s from state in epoch %s",
        epoch,
        stateEpoch);
    return BeaconStateCache.getTransitionCaches(state)
        .getActiveValidators()
        .get(
            epoch,
            e -> {
              SszList<Validator> validators = state.getValidators();
              return IntList.of(
                  IntStream.range(0, validators.size())
                      .filter(index -> predicates.isActiveValidator(validators.get(index), epoch))
                      .toArray());
            });
  }

  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return getMaxLookaheadEpoch(getCurrentEpoch(state));
  }

  private UInt64 getMaxLookaheadEpoch(final UInt64 stateEpoch) {
    return stateEpoch.plus(config.getMaxSeedLookahead());
  }

  public UInt64 getTotalBalance(final BeaconState state, final Collection<Integer> indices) {
    UInt64 sum = UInt64.ZERO;
    SszList<Validator> validatorRegistry = state.getValidators();
    for (Integer index : indices) {
      sum = sum.plus(validatorRegistry.get(index).getEffectiveBalance());
    }
    return sum.max(config.getEffectiveBalanceIncrement());
  }

  public UInt64 getTotalActiveBalance(final BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getTotalActiveBalance()
        .get(
            getCurrentEpoch(state),
            epoch -> getTotalBalance(state, getActiveValidatorIndices(state, epoch)));
  }

  /** compute_proposer_score */
  public UInt64 getProposerBoostAmount(final BeaconState state) {
    return calculateCommitteeFraction(state, config.getProposerScoreBoost());
  }

  /** Total effective balance of the current epoch's active validators that are not slashed. */
  public UInt64 getTotalActiveUnslashedBalance(final BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getTotalActiveUnslashedBalance()
        .get(
            getCurrentEpoch(state),
            epoch -> {
              final SszList<Validator> validators = state.getValidators();
              UInt64 total = UInt64.ZERO;
              for (final int index : getActiveValidatorIndices(state, epoch)) {
                final Validator validator = validators.get(index);
                if (!validator.isSlashed()) {
                  total = total.plus(validator.getEffectiveBalance());
                }
              }
              return total.max(config.getEffectiveBalanceIncrement());
            });
  }

  public Bytes32 getSeed(final BeaconState state, final UInt64 epoch, final Bytes4 domainType)
      throws IllegalArgumentException {
    final UInt64 randaoIndex =
        epoch.plus(config.getEpochsPerHistoricalVector() - config.getMinSeedLookahead() - 1);
    final Bytes32 mix = getRandaoMix(state, randaoIndex);
    final Bytes epochBytes = uint64ToBytes(epoch);
    return Hash.sha256(domainType.getWrappedBytes(), epochBytes, mix);
  }

  /**
   * calculate_committee_fraction Refer to fork-choice specification.
   *
   * @param beaconState
   * @param committeePercent
   * @return
   */
  public UInt64 calculateCommitteeFraction(
      final BeaconState beaconState, final int committeePercent) {
    final UInt64 committeeWeight =
        getTotalActiveUnslashedBalance(beaconState).dividedBy(config.getSlotsPerEpoch());
    return committeeWeight.times(committeePercent).dividedBy(100);
  }

  /**
   * return the number of committees in each slot for the given `epoch`.
   *
   * @param state
   * @param epoch
   * @return
   */
  public UInt64 getCommitteeCountPerSlot(final BeaconState state, final UInt64 epoch) {
    IntList activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    return getCommitteeCountPerSlot(activeValidatorIndices.size());
  }

  public UInt64 getCommitteeCountPerSlot(final long activeValidatorCount) {
    return UInt64.valueOf(
        Math.max(
            1L,
            Math.min(
                config.getMaxCommitteesPerSlot(),
                Math.floorDiv(
                    Math.floorDiv(activeValidatorCount, config.getSlotsPerEpoch()),
                    config.getTargetCommitteeSize()))));
  }

  public Bytes32 getRandaoMix(final BeaconState state, final UInt64 epoch) {
    int index = epoch.mod(config.getEpochsPerHistoricalVector()).intValue();
    return state.getRandaoMixes().getElement(index);
  }

  public int getBeaconProposerIndex(final BeaconState state) {
    return getBeaconProposerIndex(state, state.getSlot());
  }

  public int getBeaconProposerIndex(final BeaconState state, final UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconProposerIndex()
        .get(
            requestedSlot,
            slot -> {
              final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              final Bytes32 seed =
                  Hash.sha256(getSeed(state, epoch, Domain.BEACON_PROPOSER), uint64ToBytes(slot));
              final IntList indices = getActiveValidatorIndices(state, epoch);
              return miscHelpers.computeProposerIndex(state, indices, seed);
            });
  }

  protected void validateStateCanCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    checkArgument(
        canCalculateProposerIndexAtSlot(state, requestedSlot),
        "get_beacon_proposer_index is only used for requesting a slot in the current epoch (or next epoch in FULU). Requested slot %s, state slot %s",
        requestedSlot,
        state.getSlot());
  }

  public boolean canCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(requestedSlot);
    final UInt64 stateEpoch = getCurrentEpoch(state);
    return epoch.equals(stateEpoch);
  }

  public UInt64 getFinalityDelay(final BeaconState state) {
    return getPreviousEpoch(state).minusMinZero(state.getFinalizedCheckpoint().getEpoch());
  }

  public boolean isInactivityLeak(final UInt64 finalityDelay) {
    return finalityDelay.isGreaterThan(config.getMinEpochsToInactivityPenalty());
  }

  public boolean isInactivityLeak(final BeaconState state) {
    return isInactivityLeak(getFinalityDelay(state));
  }

  public Bytes32 getBlockRootAtSlot(final BeaconState state, final UInt64 slot)
      throws IllegalArgumentException {
    checkArgument(
        isBlockRootAvailableFromState(state, slot),
        "Block at slot %s not available from state at slot %s",
        slot,
        state.getSlot());
    int latestBlockRootIndex = slot.mod(config.getSlotsPerHistoricalRoot()).intValue();
    return state.getBlockRoots().getElement(latestBlockRootIndex);
  }

  public Bytes32 getBlockRoot(final BeaconState state, final UInt64 epoch)
      throws IllegalArgumentException {
    return getBlockRootAtSlot(state, miscHelpers.computeStartSlotAtEpoch(epoch));
  }

  private boolean isBlockRootAvailableFromState(final BeaconState state, final UInt64 slot) {
    UInt64 slotPlusHistoricalRoot = slot.plus(config.getSlotsPerHistoricalRoot());
    return slot.isLessThan(state.getSlot())
        && state.getSlot().isLessThanOrEqualTo(slotPlusHistoricalRoot);
  }

  // Custom accessors

  /**
   * Calculates how many additional attestations from the previous epoch can be accommodated by this
   * state during block processing
   *
   * @param state The state to be processed, should already be at the slot of the block being
   *     processed
   * @return The remaining capacity for attestations from the previous epoch
   */
  public int getPreviousEpochAttestationCapacity(final BeaconState state) {
    // No strict limit in general
    return Integer.MAX_VALUE;
  }

  public IntList getBeaconCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    // Make sure state is within range of the slot being queried
    validateStateForCommitteeQuery(state, slot);
    return getBeaconCommitteeNoValidation(state, slot, index);
  }

  protected IntList getBeaconCommitteeNoValidation(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconCommittee()
        .get(
            TekuPair.of(slot, index),
            __ -> {
              UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              UInt64 committeesPerSlot = getCommitteeCountPerSlot(state, epoch);
              int committeeIndex =
                  slot.mod(config.getSlotsPerEpoch())
                      .times(committeesPerSlot)
                      .plus(index)
                      .intValue();
              int count = committeesPerSlot.times(config.getSlotsPerEpoch()).intValue();
              return miscHelpers.computeCommittee(
                  state,
                  getActiveValidatorIndices(state, epoch),
                  getSeed(state, epoch, Domain.BEACON_ATTESTER),
                  committeeIndex,
                  count);
            });
  }

  public Int2IntMap getBeaconCommitteesSize(final BeaconState state, final UInt64 slot) {
    return BeaconStateCache.getTransitionCaches(state)
        .getBeaconCommitteesSize()
        .get(
            slot,
            __ -> {
              final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
              final UInt64 committees = getCommitteeCountPerSlot(state, epoch);
              final Int2IntMap committeesSize = new Int2IntOpenHashMap(committees.intValue());
              UInt64.range(UInt64.ZERO, committees)
                  .forEach(
                      index -> {
                        final IntList committee = getBeaconCommittee(state, slot, index);
                        committeesSize.put(index.intValue(), committee.size());
                      });
              return committeesSize;
            });
  }

  public void validateStateForCommitteeQuery(final BeaconState state, final UInt64 slot) {
    final UInt64 oldestQueryableSlot =
        miscHelpers.getEarliestQueryableSlotForBeaconCommitteeAtTargetSlot(slot);
    if (state.getSlot().isLessThan(oldestQueryableSlot)) {
      throw new StateTooOldException(state.getSlot(), oldestQueryableSlot);
    }
  }

  public Bytes32 getDomain(final ForkInfo forkInfo, final Bytes4 domainType, final UInt64 epoch) {
    return getDomain(domainType, epoch, forkInfo.getFork(), forkInfo.getGenesisValidatorsRoot());
  }

  public Bytes32 getDomain(
      final Bytes4 domainType,
      final UInt64 epoch,
      final Fork fork,
      final Bytes32 genesisValidatorsRoot) {
    Bytes4 forkVersion =
        (epoch.compareTo(fork.getEpoch()) < 0)
            ? fork.getPreviousVersion()
            : fork.getCurrentVersion();
    return miscHelpers.computeDomain(domainType, forkVersion, genesisValidatorsRoot);
  }

  public Bytes32 getVoluntaryExitDomain(
      final UInt64 epoch, final Fork fork, final Bytes32 genesisValidatorsRoot) {
    return getDomain(Domain.VOLUNTARY_EXIT, epoch, fork, genesisValidatorsRoot);
  }

  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    // NO-OP
    return IntList.of();
  }

  public Optional<BLSPublicKey> getBuilderPubKey(
      final BeaconState state, final UInt64 builderIndex) {
    // NO-OP
    return Optional.empty();
  }

  public Optional<Integer> getBuilderIndex(final BeaconState state, final BLSPublicKey publicKey) {
    // NO-OP
    return Optional.empty();
  }
}
