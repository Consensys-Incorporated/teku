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

package tech.pegasys.teku.statetransition.synccommittee;

import static tech.pegasys.teku.spec.config.Constants.VALID_CONTRIBUTION_AND_PROOF_SET_SIZE;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import com.google.errorprone.annotations.FormatMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AsyncBatchBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.statetransition.util.SeenAggregatesCache;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedContributionAndProofValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final Set<SourceUniquenessKey> seenIndices =
      LimitedSet.createSynchronizedLRU(VALID_CONTRIBUTION_AND_PROOF_SET_SIZE);
  private final SeenAggregatesCache<TargetUniquenessKey> seenAggregatesCache =
      new SeenAggregatesCache<>(VALID_CONTRIBUTION_AND_PROOF_SET_SIZE);
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final GossipValidationHelper gossipValidationHelper;

  private final RecentChainData recentChainData;

  public SignedContributionAndProofValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    this.signatureVerifier = signatureVerifier;
    this.recentChainData = recentChainData;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  public SafeFuture<InternalValidationResult> validate(final SignedContributionAndProof proof) {
    final ContributionAndProof contributionAndProof = proof.getMessage();
    final SyncCommitteeContribution contribution = contributionAndProof.getContribution();

    // [IGNORE] The sync committee contribution is the first valid contribution received for the
    // aggregator with index contribution_and_proof.aggregator_index for the slot contribution.slot.
    // (this requires maintaining a cache of size `SYNC_COMMITTEE_SIZE` for this topic that can be
    // flushed after each slot).
    final SourceUniquenessKey sourceUniquenessKey =
        getUniquenessKey(contributionAndProof, contribution);
    if (seenIndices.contains(sourceUniquenessKey)) {
      return SafeFuture.completedFuture(ignore("Duplicate contribution from validator"));
    }
    final TargetUniquenessKey targetUniquenessKey =
        new TargetUniquenessKey(
            contribution.getSlot(),
            contribution.getBeaconBlockRoot(),
            contribution.getSubcommitteeIndex());
    if (seenAggregatesCache.isAlreadySeen(targetUniquenessKey, contribution.getAggregationBits())) {
      return SafeFuture.completedFuture(ignore("Redundant aggregate"));
    }

    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(contribution.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      return futureFailureResult(
          "Rejecting proof because the fork active at slot %s does not support sync committees",
          contribution.getSlot());
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    if (proof.getMessage().getContribution().getAggregationBits().getBitCount() == 0) {
      return SafeFuture.completedFuture(reject("Rejecting proof because participant set is empty"));
    }

    // [IGNORE] The contribution's slot is for the current slot (with a
    // `MAXIMUM_GOSSIP_CLOCK_DISPARITY` allowance), i.e. `contribution.slot == current_slot`.
    if (!gossipValidationHelper.isSlotCurrent(contribution.getSlot())) {
      LOG.trace(
          "Ignoring proof from aggregator {}, "
              + "because it is not from the current slot "
              + "(contribution slot: {}, current slot: {})",
          contributionAndProof::getAggregatorIndex,
          contribution::getSlot,
          recentChainData::getCurrentSlot);
      return SafeFuture.completedFuture(ignore("Not from current slot"));
    }

    // [REJECT] The subcommittee index is in the allowed range
    // i.e. contribution.subcommittee_index < SYNC_COMMITTEE_SUBNET_COUNT.
    if (contribution.getSubcommitteeIndex().isGreaterThanOrEqualTo(SYNC_COMMITTEE_SUBNET_COUNT)) {
      return futureFailureResult(
          "Rejecting proof because subcommittee index %s is too big",
          contribution.getSubcommitteeIndex());
    }

    return syncCommitteeStateUtils
        .getStateForSyncCommittee(contribution.getSlot())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace("Ignoring proof because state is not available or not from Altair fork");
                return SafeFuture.completedFuture(ignore("No Altair state available"));
              }
              return validateWithState(
                  proof,
                  contributionAndProof,
                  contribution,
                  syncCommitteeUtil,
                  sourceUniquenessKey,
                  targetUniquenessKey,
                  maybeState.get());
            });
  }

  @FormatMethod
  private SafeFuture<InternalValidationResult> futureFailureResult(
      final String message, final Object... args) {
    return SafeFuture.completedFuture(reject(message, args));
  }

  private SafeFuture<InternalValidationResult> validateWithState(
      final SignedContributionAndProof proof,
      final ContributionAndProof contributionAndProof,
      final SyncCommitteeContribution contribution,
      final SyncCommitteeUtil syncCommitteeUtil,
      final SourceUniquenessKey sourceUniquenessKey,
      final TargetUniquenessKey targetUniquenessKey,
      final BeaconStateAltair state) {
    final BeaconStateAccessors beaconStateAccessors =
        spec.atSlot(contribution.getSlot()).beaconStateAccessors();

    final Optional<BLSPublicKey> aggregatorPublicKey =
        beaconStateAccessors.getValidatorPubKey(state, contributionAndProof.getAggregatorIndex());
    if (aggregatorPublicKey.isEmpty()) {
      return futureFailureResult(
          "Rejecting proof because aggregator index %s is an unknown validator",
          contributionAndProof.getAggregatorIndex());
    }
    final UInt64 contributionEpoch =
        syncCommitteeUtil.getEpochForDutiesAtSlot(contribution.getSlot());

    // [REJECT] The aggregator's validator index is within the current sync subcommittee
    // i.e. state.validators[aggregate_and_proof.aggregator_index].pubkey in
    // state.current_sync_committee.pubkeys.
    if (!isInSyncSubcommittee(
        syncCommitteeUtil,
        contribution,
        state,
        contributionEpoch,
        contributionAndProof.getAggregatorIndex())) {
      return futureFailureResult(
          "Rejecting proof because aggregator index %s is not in the current sync subcommittee",
          contributionAndProof.getAggregatorIndex());
    }

    // [REJECT] contribution_and_proof.selection_proof selects the validator as an
    // aggregator for the slot -- i.e. is_sync_committee_aggregator(state,
    // contribution.slot, contribution_and_proof.selection_proof) returns True.
    if (!syncCommitteeUtil.isSyncCommitteeAggregator(contributionAndProof.getSelectionProof())) {
      return futureFailureResult(
          "Rejecting proof because selection proof %s is not an aggregator",
          contributionAndProof.getSelectionProof());
    }

    final AsyncBatchBLSSignatureVerifier signatureVerifier =
        new AsyncBatchBLSSignatureVerifier(this.signatureVerifier);

    final ForkInfo contributionSignatureForkInfo =
        new ForkInfo(
            spec.fork(spec.computeEpochAtSlot(contribution.getSlot())),
            state.getGenesisValidatorsRoot());

    // [REJECT] The contribution_and_proof.selection_proof is a valid signature of the
    // contribution.slot by the validator with index
    // contribution_and_proof.aggregator_index.
    final Bytes signingRoot =
        syncCommitteeUtil.getSyncAggregatorSelectionDataSigningRoot(
            syncCommitteeUtil.createSyncAggregatorSelectionData(
                contribution.getSlot(), contribution.getSubcommitteeIndex()),
            contributionSignatureForkInfo);
    if (!signatureVerifier.verify(
        aggregatorPublicKey.get(), signingRoot, contributionAndProof.getSelectionProof())) {
      return futureFailureResult(
          "Rejecting proof at slot %s for subcommittee index %s because selection proof is invalid",
          contribution.getSlot(), contribution.getSubcommitteeIndex());
    }

    // [REJECT] The aggregator signature, signed_contribution_and_proof.signature, is
    // valid.
    if (!signatureVerifier.verify(
        aggregatorPublicKey.get(),
        syncCommitteeUtil.getContributionAndProofSigningRoot(
            contributionAndProof, contributionSignatureForkInfo),
        proof.getSignature())) {
      return futureFailureResult(
          "Rejecting proof %s because aggregator signature is invalid", proof.getSignature());
    }

    final SpecConfigAltair config =
        SpecConfigAltair.required(spec.getSpecConfig(contributionEpoch));
    final SyncCommittee syncCommittee =
        syncCommitteeUtil.getSyncCommittee(state, contributionEpoch);
    final int subcommitteeSize = config.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;

    // [REJECT] The aggregate signature is valid for the message beacon_block_root and
    // aggregate pubkey derived from the participation info in aggregation_bits for the
    // subcommittee specified by the subcommittee_index.
    final List<BLSPublicKey> contributorPublicKeys =
        contribution
            .getAggregationBits()
            .streamAllSetBits()
            .mapToObj(
                participantIndex ->
                    getParticipantPublicKey(
                        state, syncCommittee, contribution, subcommitteeSize, participantIndex))
            .toList();

    if (!signatureVerifier.verify(
        contributorPublicKeys,
        syncCommitteeUtil.getSyncCommitteeMessageSigningRoot(
            contribution.getBeaconBlockRoot(), contributionEpoch, contributionSignatureForkInfo),
        contribution.getSignature())) {
      return futureFailureResult(
          "Rejecting proof because aggregate signature %s is invalid", contribution.getSignature());
    }

    return signatureVerifier
        .batchVerify()
        .thenApply(
            signatureValid -> {
              if (!signatureValid) {
                return reject(
                    "Rejecting proof with signature %s because batch signature check failed",
                    contribution.getSignature());
              }

              if (!seenIndices.add(sourceUniquenessKey)) {
                // Got added by another thread while we were validating it
                return ignore("Already seen contribution from validator");
              }

              if (!seenAggregatesCache.add(
                  targetUniquenessKey, contribution.getAggregationBits())) {
                return ignore("Redundant aggregate");
              }
              return ACCEPT;
            });
  }

  private BLSPublicKey getParticipantPublicKey(
      final BeaconStateAltair state,
      final SyncCommittee syncCommittee,
      final SyncCommitteeContribution contribution,
      final int subcommitteeSize,
      final int participantIndex) {
    final int committeeIndex =
        contribution.getSubcommitteeIndex().intValue() * subcommitteeSize + participantIndex;
    return spec.getSyncCommitteeUtilRequired(state.getSlot())
        .getSyncCommitteeParticipantPubKey(state, syncCommittee, committeeIndex);
  }

  private boolean isInSyncSubcommittee(
      final SyncCommitteeUtil syncCommitteeUtil,
      final SyncCommitteeContribution contribution,
      final BeaconState state,
      final UInt64 contributionEpoch,
      final UInt64 aggregatorIndex) {
    return syncCommitteeUtil
        .getSubcommitteeAssignments(state, contributionEpoch, aggregatorIndex)
        .getAssignedSubcommittees()
        .contains(contribution.getSubcommitteeIndex().intValue());
  }

  private SourceUniquenessKey getUniquenessKey(
      final ContributionAndProof contributionAndProof,
      final SyncCommitteeContribution contribution) {
    return new SourceUniquenessKey(
        contributionAndProof.getAggregatorIndex(),
        contribution.getSlot(),
        contribution.getSubcommitteeIndex());
  }

  private static class SourceUniquenessKey {
    private final UInt64 aggregatorIndex;
    private final UInt64 slot;
    private final UInt64 subcommitteeIndex;

    private SourceUniquenessKey(
        final UInt64 aggregatorIndex, final UInt64 slot, final UInt64 subcommitteeIndex) {
      this.aggregatorIndex = aggregatorIndex;
      this.slot = slot;
      this.subcommitteeIndex = subcommitteeIndex;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SourceUniquenessKey that = (SourceUniquenessKey) o;
      return Objects.equals(aggregatorIndex, that.aggregatorIndex)
          && Objects.equals(slot, that.slot)
          && Objects.equals(subcommitteeIndex, that.subcommitteeIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(aggregatorIndex, slot, subcommitteeIndex);
    }
  }

  private static class TargetUniquenessKey {
    private final UInt64 slot;
    private final Bytes32 blockRoot;
    private final UInt64 subcommitteeIndex;

    private TargetUniquenessKey(
        final UInt64 slot, final Bytes32 blockRoot, final UInt64 subcommitteeIndex) {
      this.slot = slot;
      this.blockRoot = blockRoot;
      this.subcommitteeIndex = subcommitteeIndex;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TargetUniquenessKey that = (TargetUniquenessKey) o;
      return Objects.equals(slot, that.slot)
          && Objects.equals(blockRoot, that.blockRoot)
          && Objects.equals(subcommitteeIndex, that.subcommitteeIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(slot, blockRoot, subcommitteeIndex);
    }
  }
}
