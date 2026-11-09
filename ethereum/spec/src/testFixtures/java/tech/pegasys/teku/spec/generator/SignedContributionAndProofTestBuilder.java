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

package tech.pegasys.teku.spec.generator;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.signatures.Signer;

public class SignedContributionAndProofTestBuilder {

  private Spec spec;
  private SyncCommitteeUtil syncCommitteeUtil;
  private UInt64 aggregatorIndex;
  private Signer aggregatorSigner;
  private BLSSignature selectionProof;
  private Optional<BLSSignature> signedContributionAndProofSignature = Optional.empty();
  private Bytes32 beaconBlockRoot;
  private UInt64 slot;
  private final IntList subcommitteeParticipationIndices = new IntArrayList();
  private final List<BLSSignature> syncSignatures = new ArrayList<>();
  private int subcommitteeIndex;
  private BeaconStateAltair state;
  private IntFunction<Signer> signerProvider;

  public SignedContributionAndProofTestBuilder spec(final Spec spec) {
    this.spec = spec;
    return this;
  }

  public SignedContributionAndProofTestBuilder signerProvider(
      final IntFunction<Signer> signerProvider) {
    this.signerProvider = signerProvider;
    return this;
  }

  public SignedContributionAndProofTestBuilder syncCommitteeUtil(
      final SyncCommitteeUtil syncCommitteeUtil) {
    this.syncCommitteeUtil = syncCommitteeUtil;
    return this;
  }

  public SignedContributionAndProofTestBuilder subcommitteeIndex(final int subcommitteeIndex) {
    this.subcommitteeIndex = subcommitteeIndex;
    return this;
  }

  public SignedContributionAndProofTestBuilder aggregator(
      final UInt64 aggregatorIndex, final Signer signer) {
    this.aggregatorSigner = signer;
    return aggregatorIndex(aggregatorIndex).addParticipant(aggregatorIndex, signer);
  }

  public SignedContributionAndProofTestBuilder aggregatorIndex(final UInt64 aggregatorIndex) {
    this.aggregatorIndex = aggregatorIndex;
    return this;
  }

  public SignedContributionAndProofTestBuilder aggregatorNotInSyncSubcommittee() {
    final Map<UInt64, SyncSubcommitteeAssignments> subcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.computeEpochAtSlot(slot));
    for (int validatorIndex = 0; validatorIndex < 10_000; validatorIndex++) {
      final SyncSubcommitteeAssignments assignments =
          subcommittees.get(UInt64.valueOf(validatorIndex));
      if (assignments == null
          || !assignments.getAssignedSubcommittees().contains(subcommitteeIndex)) {
        this.aggregatorIndex = UInt64.valueOf(validatorIndex);
        this.aggregatorSigner = signerProvider.apply(validatorIndex);
        return this;
      } else {
        for (int candidateSubcommitteeIndex = 0;
            candidateSubcommitteeIndex < NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
            candidateSubcommitteeIndex++) {
          if (!assignments.getAssignedSubcommittees().contains(candidateSubcommitteeIndex)) {
            this.aggregatorIndex = UInt64.valueOf(validatorIndex);
            this.aggregatorSigner = signerProvider.apply(validatorIndex);
            this.subcommitteeIndex = candidateSubcommitteeIndex;
            return this;
          }
        }
      }
    }
    throw new IllegalStateException("Could not find validator not in the sync committee");
  }

  public SignedContributionAndProofTestBuilder selectionProof(final BLSSignature selectionProof) {
    this.selectionProof = selectionProof;
    return this;
  }

  public SignedContributionAndProofTestBuilder beaconBlockRoot(final Bytes32 beaconBlockRoot) {
    this.beaconBlockRoot = beaconBlockRoot;
    return this;
  }

  public SignedContributionAndProofTestBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public SignedContributionAndProofTestBuilder state(final BeaconState state) {
    this.state = BeaconStateAltair.required(state);
    return this;
  }

  public SignedContributionAndProofTestBuilder signedContributionAndProofSignature(
      final BLSSignature signedContributionAndProofSignature) {
    this.signedContributionAndProofSignature = Optional.of(signedContributionAndProofSignature);
    return this;
  }

  public SignedContributionAndProofTestBuilder addParticipantSignature(
      final BLSSignature signature) {
    syncSignatures.add(signature);
    return this;
  }

  public SignedContributionAndProofTestBuilder addParticipant(
      final UInt64 validatorIndex, final Signer signer) {
    final SyncSubcommitteeAssignments assignments =
        syncCommitteeUtil
            .getSyncSubcommittees(state, syncCommitteeUtil.getEpochForDutiesAtSlot(slot))
            .get(validatorIndex);
    checkArgument(
        assignments != null && assignments.getAssignedSubcommittees().contains(subcommitteeIndex),
        "Validator %s is not assigned to subcommittee %s",
        validatorIndex,
        subcommitteeIndex);
    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(spec.computeEpochAtSlot(slot)), state.getGenesisValidatorsRoot());

    final BLSSignature syncSignature =
        signer.signSyncCommitteeMessage(slot, beaconBlockRoot, forkInfo).join();
    final IntSet participationIndices = assignments.getParticipationBitIndices(subcommitteeIndex);
    // Have to add signature once for each time the validator appears in the subcommittee
    syncSignatures.addAll(Collections.nCopies(participationIndices.size(), syncSignature));
    subcommitteeParticipationIndices.addAll(participationIndices);
    return this;
  }

  public SignedContributionAndProofTestBuilder addAllParticipants(
      final Function<UInt64, Signer> getSigner) {
    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(
            state, syncCommitteeUtil.getEpochForDutiesAtSlot(slot));
    removeAllParticipants();
    syncSubcommittees.forEach(
        (validatorIndex, assignments) -> {
          if (assignments.getAssignedSubcommittees().contains(subcommitteeIndex)) {
            addParticipant(validatorIndex, getSigner.apply(validatorIndex));
          }
        });
    return this;
  }

  public SignedContributionAndProofTestBuilder resetParticipantsToOnlyAggregator() {
    syncSignatures.clear();
    subcommitteeParticipationIndices.clear();
    return addParticipant(aggregatorIndex, aggregatorSigner);
  }

  public SignedContributionAndProofTestBuilder removeAllParticipants() {
    syncSignatures.clear();
    subcommitteeParticipationIndices.clear();
    return this;
  }

  public SignedContributionAndProof build() {
    final BLSSignature aggregateSignature =
        syncSignatures.isEmpty() ? BLSSignature.infinity() : BLS.aggregate(syncSignatures);

    final SyncCommitteeContribution syncCommitteeContribution =
        syncCommitteeUtil.createSyncCommitteeContribution(
            slot,
            beaconBlockRoot,
            UInt64.valueOf(subcommitteeIndex),
            subcommitteeParticipationIndices,
            aggregateSignature);
    final ContributionAndProof contributionAndProof =
        syncCommitteeUtil.createContributionAndProof(
            aggregatorIndex, syncCommitteeContribution, selectionProof);

    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(spec.computeEpochAtSlot(slot)), state.getGenesisValidatorsRoot());

    final BLSSignature signature =
        signedContributionAndProofSignature.orElseGet(
            () -> aggregatorSigner.signContributionAndProof(contributionAndProof, forkInfo).join());
    return syncCommitteeUtil.createSignedContributionAndProof(contributionAndProof, signature);
  }
}
