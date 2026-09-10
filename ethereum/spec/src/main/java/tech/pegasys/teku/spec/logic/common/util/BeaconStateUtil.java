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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;

public class BeaconStateUtil {

  private final SpecConfig specConfig;

  private final Predicates predicates;
  private final MiscHelpers miscHelpers;
  private final BeaconStateAccessors beaconStateAccessors;

  public BeaconStateUtil(
      final SpecConfig specConfig,
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  public boolean isValidGenesisState(final UInt64 genesisTime, final int activeValidatorCount) {
    return isItMinGenesisTimeYet(genesisTime)
        && isThereEnoughNumberOfValidators(activeValidatorCount);
  }

  private boolean isThereEnoughNumberOfValidators(final int activeValidatorCount) {
    return activeValidatorCount >= specConfig.getMinGenesisActiveValidatorCount();
  }

  private boolean isItMinGenesisTimeYet(final UInt64 genesisTime) {
    return genesisTime.compareTo(specConfig.getMinGenesisTime()) >= 0;
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    final UInt64 currentEpoch = miscHelpers.computeEpochAtSlot(slot);
    return miscHelpers.computeStartSlotAtEpoch(currentEpoch).equals(slot)
        ? currentEpoch
        : currentEpoch.plus(1);
  }

  public Bytes32 getPreviousDutyDependentRoot(final BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getPreviousEpoch(state));
  }

  public Bytes32 getCurrentDutyDependentRoot(final BeaconState state) {
    return getDutyDependentRoot(state, beaconStateAccessors.getCurrentEpoch(state));
  }

  /**
   * Returns the current duty dependent root for the specified block if it is available from
   * ReadOnlyForkChoiceStrategy.
   *
   * <p>If the specified block is or descends from the current justified block, the previous
   * dependent root is either available or is the parent root of the current finalized block
   * because:
   *
   * <ul>
   *   <li>The previous dependent root is the block root from the last slot of the epoch prior to
   *       the previous one
   *   <li>The justified block can be at most the first block of the current epoch
   *   <li>The finalized block must be at least one epoch older than the justified block
   *   <li>ForkChoiceStrategy has all blocks from the finalized block onwards
   * </ul>
   *
   * Thus, the worst case is that finalized block is the first slot of the previous epoch, and it's
   * parent must then be the block in effect in the previous slot.
   */
  public Optional<Bytes32> getPreviousDutyDependentRoot(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final MinimalBeaconBlockSummary block) {
    return getDutyDependentRoot(
        forkChoiceStrategy,
        block.getRoot(),
        miscHelpers.computeEpochAtSlot(block.getSlot()).minusMinZero(1));
  }

  /**
   * Returns the current duty dependent root for the specified block if it is available from
   * ReadOnlyForkChoiceStrategy.
   *
   * <p>The current dependent root is always available if the specified block is or descends from
   * the current justified block because:
   *
   * <ul>
   *   <li>The current dependent root is the block root from the last slot of the previous epoch
   *   <li>The justified block can be at most the first block of the current epoch
   *   <li>The finalized block must be at least one epoch older than the justified block
   *   <li>ForkChoiceStrategy has all blocks from the finalized block onwards
   * </ul>
   */
  public Optional<Bytes32> getCurrentDutyDependentRoot(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final MinimalBeaconBlockSummary block) {
    return getDutyDependentRoot(
        forkChoiceStrategy, block.getRoot(), miscHelpers.computeEpochAtSlot(block.getSlot()));
  }

  public List<UInt64> getEffectiveActiveUnslashedBalances(final BeaconState state) {
    return BeaconStateCache.getTransitionCaches(state)
        .getEffectiveBalances()
        .get(
            beaconStateAccessors.getCurrentEpoch(state),
            epoch ->
                state.getValidators().stream()
                    .map(
                        validator ->
                            predicates.isActiveValidator(validator, epoch) && !validator.isSlashed()
                                ? validator.getEffectiveBalance()
                                : UInt64.ZERO)
                    .toList());
  }

  public boolean all(final SszBitvector bitvector, final int start, final int end) {
    for (int i = start; i < end; i++) {
      if (!bitvector.getBit(i)) {
        return false;
      }
    }
    return true;
  }

  public UInt64 getAttestersTotalEffectiveBalance(final BeaconState state, final UInt64 slot) {
    beaconStateAccessors.validateStateForCommitteeQuery(state, slot);
    return BeaconStateCache.getTransitionCaches(state)
        .getAttestersTotalBalance()
        .get(
            slot,
            __ -> {
              final UInt64 committeeCount =
                  beaconStateAccessors.getCommitteeCountPerSlot(
                      state, miscHelpers.computeEpochAtSlot(slot));
              return UInt64.range(UInt64.ZERO, committeeCount)
                  .flatMap(committee -> streamEffectiveBalancesForCommittee(state, slot, committee))
                  .reduce(UInt64.ZERO, UInt64::plus);
            });
  }

  private Stream<UInt64> streamEffectiveBalancesForCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 committeeIndex) {
    return beaconStateAccessors
        .getBeaconCommittee(state, slot, committeeIndex)
        .intStream()
        .mapToObj(
            validatorIndex -> state.getValidators().get(validatorIndex).getEffectiveBalance());
  }

  public int computeSubnetForAttestation(final BeaconState state, final Attestation attestation) {
    final UInt64 attestationSlot = attestation.getData().getSlot();
    final UInt64 committeeIndex = attestation.getFirstCommitteeIndex();
    return computeSubnetForCommittee(state, attestationSlot, committeeIndex);
  }

  public int computeSubnetForCommittee(
      final UInt64 attestationSlot, final UInt64 committeeIndex, final UInt64 committeesPerSlot) {
    final UInt64 slotsSinceEpochStart = attestationSlot.mod(specConfig.getSlotsPerEpoch());
    final UInt64 committeesSinceEpochStart = committeesPerSlot.times(slotsSinceEpochStart);
    return committeesSinceEpochStart
        .plus(committeeIndex)
        .mod(specConfig.getAttestationSubnetCount())
        .intValue();
  }

  private int computeSubnetForCommittee(
      final BeaconState state, final UInt64 attestationSlot, final UInt64 committeeIndex) {
    return computeSubnetForCommittee(
        attestationSlot,
        committeeIndex,
        beaconStateAccessors.getCommitteeCountPerSlot(
            state, miscHelpers.computeEpochAtSlot(attestationSlot)));
  }

  private Optional<Bytes32> getDutyDependentRoot(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 chainHead,
      final UInt64 epoch) {
    final UInt64 slot = getDutyDependentRootSlot(epoch);
    return forkChoiceStrategy.getAncestor(chainHead, slot);
  }

  private Bytes32 getDutyDependentRoot(final BeaconState state, final UInt64 epoch) {
    final UInt64 slot = getDutyDependentRootSlot(epoch);
    return slot.equals(state.getSlot())
        // The state's own slot has no entry in block_roots yet: its latest block header, with the
        // state root filled in, is that block. At genesis this is the genesis block root, whatever
        // the genesis fork, so no block body schema (which may belong to a later fork) is needed.
        ? BeaconBlockHeader.fromState(state).getRoot()
        : beaconStateAccessors.getBlockRootAtSlot(state, slot);
  }

  private UInt64 getDutyDependentRootSlot(final UInt64 epoch) {
    return miscHelpers.computeStartSlotAtEpoch(epoch).minusMinZero(1);
  }
}
