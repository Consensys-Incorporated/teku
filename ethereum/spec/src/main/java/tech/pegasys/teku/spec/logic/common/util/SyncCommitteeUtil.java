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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.bytesToUInt64;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class SyncCommitteeUtil {

  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final ValidatorsUtil validatorsUtil;
  private final SpecConfigAltair specConfig;
  private final MiscHelpers miscHelpers;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;

  public SyncCommitteeUtil(
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final ValidatorsUtil validatorsUtil,
      final SpecConfigAltair specConfig,
      final MiscHelpers miscHelpers,
      final SchemaDefinitionsAltair schemaDefinitionsAltair) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.validatorsUtil = validatorsUtil;
    this.specConfig = specConfig;
    this.miscHelpers = miscHelpers;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
  }

  public boolean isStateUsableForCommitteeCalculationAtEpoch(
      final BeaconState state, final UInt64 epoch) {
    if (state.toVersionAltair().isEmpty()) {
      return false;
    }
    final UInt64 syncCommitteePeriod = computeSyncCommitteePeriod(epoch);
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 currentSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch);
    final UInt64 nextSyncCommitteePeriod = currentSyncCommitteePeriod.increment();

    return syncCommitteePeriod.equals(currentSyncCommitteePeriod)
        || syncCommitteePeriod.equals(nextSyncCommitteePeriod);
  }

  public Map<UInt64, SyncSubcommitteeAssignments> getSyncSubcommittees(
      final BeaconState state, final UInt64 epoch) {
    final UInt64 syncCommitteePeriod = computeSyncCommitteePeriod(epoch);
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 currentSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch);
    checkArgument(
        isStateUsableForCommitteeCalculationAtEpoch(state, epoch),
        "State must be in the same or previous sync committee period. Cannot calculate epoch %s from state at slot %s",
        epoch,
        state.getSlot());
    final BeaconStateAltair altairState = BeaconStateAltair.required(state);
    return BeaconStateCache.getTransitionCaches(altairState)
        .getSyncCommitteeCache()
        .get(
            syncCommitteePeriod,
            period -> {
              final SyncCommittee syncCommittee;
              if (syncCommitteePeriod.equals(currentSyncCommitteePeriod)) {
                syncCommittee = altairState.getCurrentSyncCommittee();
              } else {
                syncCommittee = altairState.getNextSyncCommittee();
              }
              final int subcommitteeSize = getSubcommitteeSize();
              final SszVector<SszPublicKey> pubkeys = syncCommittee.getPubkeys();
              final Map<UInt64, SyncSubcommitteeAssignments.Builder> subcommitteeAssignments =
                  new HashMap<>();
              for (int index = 0; index < pubkeys.size(); index++) {
                final BLSPublicKey pubkey = pubkeys.get(index).getBLSPublicKey();
                final UInt64 validatorIndex =
                    UInt64.valueOf(
                        validatorsUtil
                            .getValidatorIndex(altairState, pubkey)
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "Unknown validator assigned to sync committee: "
                                            + pubkey)));
                final int subcommitteeIndex = index / subcommitteeSize;
                final int subcommitteeParticipationIndex =
                    index - (subcommitteeIndex * subcommitteeSize);

                // Note we're using plain HashMap here instead of a concurrent alternative as they
                // are created once here and then never modified so safe to access from multiple
                // threads.
                subcommitteeAssignments
                    .computeIfAbsent(validatorIndex, __ -> SyncSubcommitteeAssignments.builder())
                    .addAssignment(subcommitteeIndex, subcommitteeParticipationIndex)
                    .addCommitteeIndex(index);
              }
              return subcommitteeAssignments.entrySet().stream()
                  .collect(toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().build()));
            });
  }

  public SyncSubcommitteeAssignments getSubcommitteeAssignments(
      final BeaconState state, final UInt64 epoch, final UInt64 validatorIndex) {
    return getSyncSubcommittees(state, epoch)
        .getOrDefault(validatorIndex, SyncSubcommitteeAssignments.NONE);
  }

  public int getCurrentSyncCommitteeParticipantValidatorIndex(
      final BeaconStateAltair state, final int committeeIndex) {
    final BLSPublicKey uncachedPubkey =
        state.getCurrentSyncCommittee().getPubkeys().get(committeeIndex).getBLSPublicKey();
    return validatorsUtil
        .getValidatorIndex(state, uncachedPubkey)
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown validator found in sync committee"));
  }

  public BLSPublicKey getSyncCommitteeParticipantPubKey(
      final BeaconStateAltair state, final SyncCommittee syncCommittee, final int committeeIndex) {
    final BLSPublicKey uncachedPublicKey =
        syncCommittee.getPubkeys().get(committeeIndex).getBLSPublicKey();
    final int validatorIndex =
        validatorsUtil
            .getValidatorIndex(state, uncachedPublicKey)
            .orElseThrow(
                () -> new IllegalStateException("Unknown validator found in sync committee"));
    return beaconStateAccessors
        .getValidatorPubKey(state, UInt64.valueOf(validatorIndex))
        .orElseThrow(
            () -> new IllegalStateException("Validator in sync committee has no public key"));
  }

  public int getSubcommitteeSize() {
    return specConfig.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;
  }

  public SyncCommittee getSyncCommittee(final BeaconState state, final UInt64 epoch) {
    final UInt64 syncCommitteePeriod = computeSyncCommitteePeriod(epoch);
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final UInt64 currentSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch);
    final UInt64 nextSyncCommitteePeriod = currentSyncCommitteePeriod.increment();
    checkArgument(
        syncCommitteePeriod.equals(currentSyncCommitteePeriod)
            || syncCommitteePeriod.equals(nextSyncCommitteePeriod),
        "State must be in the same or previous sync committee period. "
            + "State epoch %s (period: %s), requested epoch %s (period: %s)",
        currentEpoch,
        currentSyncCommitteePeriod,
        epoch,
        syncCommitteePeriod);
    final BeaconStateAltair altairState = BeaconStateAltair.required(state);
    return syncCommitteePeriod.equals(currentSyncCommitteePeriod)
        ? altairState.getCurrentSyncCommittee()
        : altairState.getNextSyncCommittee();
  }

  public boolean isSyncCommitteeAggregator(final BLSSignature signature) {
    final int modulo =
        Math.max(
            1,
            specConfig.getSyncCommitteeSize()
                / SYNC_COMMITTEE_SUBNET_COUNT
                / ValidatorConstants.TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE);
    return bytesToUInt64(Hash.sha256(signature.toSSZBytes()).slice(0, 8)).mod(modulo).isZero();
  }

  public IntSet getCommitteeIndices(
      final BeaconState state, final UInt64 epoch, final UInt64 validatorIndex) {
    final SyncSubcommitteeAssignments assignments =
        getSyncSubcommittees(state, epoch)
            .getOrDefault(validatorIndex, SyncSubcommitteeAssignments.NONE);
    return assignments.getCommitteeIndices();
  }

  public IntSet getSyncSubcommittees(final IntSet committeeIndices) {
    final int subcommitteeSize = getSubcommitteeSize();
    return IntOpenHashSet.toSet(
        committeeIndices.intStream().map(index -> index / subcommitteeSize));
  }

  public Bytes32 getSyncCommitteeMessageSigningRoot(
      final Bytes32 blockRoot, final UInt64 epoch, final ForkInfo forkInfo) {
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.SYNC_COMMITTEE, epoch, forkInfo.getFork(), forkInfo.getGenesisValidatorsRoot());
    return miscHelpers.computeSigningRoot(blockRoot, domain);
  }

  public Bytes getContributionAndProofSigningRoot(
      final ContributionAndProof contributionAndProof, final ForkInfo forkInfo) {
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.CONTRIBUTION_AND_PROOF,
            miscHelpers.computeEpochAtSlot(contribution.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return miscHelpers.computeSigningRoot(contributionAndProof, domain);
  }

  public Bytes getSyncAggregatorSelectionDataSigningRoot(
      final SyncAggregatorSelectionData selectionData, final ForkInfo forkInfo) {
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.SYNC_COMMITTEE_SELECTION_PROOF,
            miscHelpers.computeEpochAtSlot(selectionData.getSlot()),
            forkInfo.getFork(),
            forkInfo.getGenesisValidatorsRoot());
    return miscHelpers.computeSigningRoot(selectionData, domain);
  }

  public SyncAggregatorSelectionData createSyncAggregatorSelectionData(
      final UInt64 slot, final UInt64 subcommitteeIndex) {
    return schemaDefinitionsAltair
        .getSyncAggregatorSelectionDataSchema()
        .create(slot, subcommitteeIndex);
  }

  public SyncCommitteeContribution createSyncCommitteeContribution(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 subcommitteeIndex,
      final IntIterable setParticipationBits,
      final BLSSignature signature) {
    final SyncCommitteeContributionSchema schema =
        schemaDefinitionsAltair.getSyncCommitteeContributionSchema();
    return schema.create(
        slot,
        beaconBlockRoot,
        subcommitteeIndex,
        schema.getAggregationBitsSchema().ofBits(setParticipationBits),
        signature);
  }

  public ContributionAndProof createContributionAndProof(
      final UInt64 aggregatorIndex,
      final SyncCommitteeContribution contribution,
      final BLSSignature selectionProof) {
    return schemaDefinitionsAltair
        .getContributionAndProofSchema()
        .create(aggregatorIndex, contribution, selectionProof);
  }

  public SignedContributionAndProof createSignedContributionAndProof(
      final ContributionAndProof message, final BLSSignature signature) {
    return schemaDefinitionsAltair.getSignedContributionAndProofSchema().create(message, signature);
  }

  /**
   * Returns the epoch that should be used for determining sync committee duties at a given slot.
   *
   * <p>Sync committee duties are a bit weird, duties should be performed from one slot before the
   * start of the sync committee period, to one slot before the end. This is so that when the
   * signatures wind up included in a block at the next slot, it is inside the target sync committee
   * period.
   *
   * @param slot the slot to perform duties for
   * @return the epoch to use when calculating which duties to perform
   */
  public UInt64 getEpochForDutiesAtSlot(final UInt64 slot) {
    return miscHelpers.computeEpochAtSlot(slot.plus(1));
  }

  public UInt64 getMinEpochForSyncCommitteeAssignments(final UInt64 requiredEpoch) {
    // Can look-ahead one sync committee period.
    final UInt64 requiredSyncCommitteePeriod =
        computeSyncCommitteePeriod(requiredEpoch).minusMinZero(1);
    return requiredSyncCommitteePeriod
        .times(specConfig.getEpochsPerSyncCommitteePeriod())
        // But can't use a state from before the Altair fork
        .max(specConfig.getAltairForkEpoch());
  }

  public UInt64 computeFirstEpochOfCurrentSyncCommitteePeriod(final UInt64 currentEpoch) {
    final UInt64 currentSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch);
    return currentSyncCommitteePeriod.times(specConfig.getEpochsPerSyncCommitteePeriod());
  }

  public UInt64 computeLastEpochOfCurrentSyncCommitteePeriod(final UInt64 currentEpoch) {
    return computeFirstEpochOfNextSyncCommitteePeriod(currentEpoch).minusMinZero(1);
  }

  public UInt64 computeFirstEpochOfNextSyncCommitteePeriod(final UInt64 currentEpoch) {
    final UInt64 nextSyncCommitteePeriod = computeSyncCommitteePeriod(currentEpoch).plus(1);
    return nextSyncCommitteePeriod.times(specConfig.getEpochsPerSyncCommitteePeriod());
  }

  public UInt64 computeLastEpochOfNextSyncCommitteePeriod(final UInt64 currentEpoch) {
    final UInt64 nextSyncCommitteePeriodStart =
        computeFirstEpochOfNextSyncCommitteePeriod(currentEpoch);
    return nextSyncCommitteePeriodStart.plus(specConfig.getEpochsPerSyncCommitteePeriod()).minus(1);
  }

  public UInt64 computeSyncCommitteePeriod(final UInt64 epoch) {
    return epoch.dividedBy(specConfig.getEpochsPerSyncCommitteePeriod());
  }

  public SyncAggregate createSyncAggregate(
      final Iterable<SyncCommitteeContribution> contributions) {
    final SyncAggregateSchema schema =
        BeaconBlockBodySchemaAltair.required(schemaDefinitionsAltair.getBeaconBlockBodySchema())
            .getSyncAggregateSchema();

    final IntList participantIndices = new IntArrayList();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (SyncCommitteeContribution contribution : contributions) {
      final int subcommitteeIndex = contribution.getSubcommitteeIndex().intValue();
      final int subcommitteeOffset = getSubcommitteeSize() * subcommitteeIndex;
      contribution
          .getAggregationBits()
          .streamAllSetBits()
          .forEach(index -> participantIndices.add(subcommitteeOffset + index));
      signatures.add(contribution.getSignature());
    }

    if (signatures.isEmpty()) {
      return schema.createEmpty();
    }
    final BLSSignature aggregatedSignature = BLS.aggregate(signatures);
    if (aggregatedSignature.isInfinity()) {
      // An aggregate signature at the point at infinity is invalid with non-empty participation:
      // reset the participation to empty
      return schema.createEmpty();
    }
    return schema.create(participantIndices, aggregatedSignature);
  }

  public void setGenesisStateSyncCommittees(final MutableBeaconState state) {
    final MutableBeaconStateAltair stateAltair = MutableBeaconStateAltair.required(state);

    // avoid failures in case of empty validators
    if (state.getValidators().size() == 0) {
      return;
    }

    // Note: A duplicate committee is assigned for the current and next committee at the fork
    // boundary
    final SyncCommittee syncCommittee = beaconStateAccessors.getNextSyncCommittee(state);
    stateAltair.setCurrentSyncCommittee(syncCommittee);
    stateAltair.setNextSyncCommittee(syncCommittee);
  }
}
