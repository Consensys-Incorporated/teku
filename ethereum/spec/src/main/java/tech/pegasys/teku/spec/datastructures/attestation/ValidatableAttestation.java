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

package tech.pegasys.teku.spec.datastructures.attestation;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ValidatableAttestation {
  private final Spec spec;
  private final Optional<SignedAggregateAndProof> maybeAggregate;
  private final Supplier<Bytes32> hashTreeRoot;
  private final AtomicBoolean gossiped = new AtomicBoolean(false);
  private final boolean producedLocally;
  private final OptionalInt receivedSubnetId;

  private final Attestation unconvertedAttestation;

  @NotNull // will help us not forget to initialize if a new constructor is added
  private volatile Attestation attestation;

  private volatile boolean isValidIndexedAttestation = false;
  private volatile boolean acceptedAsGossip = false;

  private volatile Optional<IndexedAttestationLight> indexedAttestation = Optional.empty();
  private volatile Optional<Bytes32> committeeShufflingSeed = Optional.empty();
  private volatile Optional<Int2IntMap> committeesSize = Optional.empty();

  public void convertToAggregatedFormatFromSingleAttestation(
      final Attestation aggregatedFormatFromSingleAttestation) {
    checkState(
        attestation.isSingleAttestation(),
        "Attestation must be a single attestation to convert to aggregated format");
    this.attestation = aggregatedFormatFromSingleAttestation;
  }

  public static ValidatableAttestation from(final Spec spec, final Attestation attestation) {
    return new ValidatableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.empty(), false);
  }

  @VisibleForTesting
  public static ValidatableAttestation from(
      final Spec spec, final Attestation attestation, final Int2IntMap committeeSizes) {
    return new ValidatableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.empty(), false, committeeSizes);
  }

  public static ValidatableAttestation fromValidator(
      final Spec spec, final Attestation attestation) {
    return new ValidatableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.empty(), true);
  }

  public static ValidatableAttestation fromNetwork(
      final Spec spec, final Attestation attestation, final int receivedSubnetId) {
    return new ValidatableAttestation(
        spec, attestation, Optional.empty(), OptionalInt.of(receivedSubnetId), false);
  }

  public static ValidatableAttestation fromReorgedBlock(
      final Spec spec, final Attestation attestation) {
    final ValidatableAttestation validatableAttestation =
        new ValidatableAttestation(spec, attestation, Optional.empty(), OptionalInt.empty(), false);
    // An indexed attestation from a reorged block is valid because it already
    // has been validated when the block was part of the canonical chain
    validatableAttestation.setValidIndexedAttestation();
    validatableAttestation.setAcceptedAsGossip();
    return validatableAttestation;
  }

  public static ValidatableAttestation aggregateFromValidator(
      final Spec spec, final SignedAggregateAndProof attestation) {
    return new ValidatableAttestation(
        spec,
        attestation.getMessage().getAggregate(),
        Optional.of(attestation),
        OptionalInt.empty(),
        true);
  }

  public static ValidatableAttestation aggregateFromNetwork(
      final Spec spec, final SignedAggregateAndProof attestation) {
    return new ValidatableAttestation(
        spec,
        attestation.getMessage().getAggregate(),
        Optional.of(attestation),
        OptionalInt.empty(),
        false);
  }

  private ValidatableAttestation(
      final Spec spec,
      final Attestation attestation,
      final Optional<SignedAggregateAndProof> aggregateAndProof,
      final OptionalInt receivedSubnetId,
      final boolean producedLocally) {
    this.spec = spec;
    this.maybeAggregate = aggregateAndProof;
    this.attestation = attestation;
    this.unconvertedAttestation = attestation;
    this.receivedSubnetId = receivedSubnetId;
    this.hashTreeRoot = Suppliers.memoize(unconvertedAttestation::hashTreeRoot);
    this.producedLocally = producedLocally;
  }

  private ValidatableAttestation(
      final Spec spec,
      final Attestation attestation,
      final Optional<SignedAggregateAndProof> aggregateAndProof,
      final OptionalInt receivedSubnetId,
      final boolean producedLocally,
      final Int2IntMap committeeSizes) {
    this.spec = spec;
    this.maybeAggregate = aggregateAndProof;
    this.attestation = attestation;
    this.unconvertedAttestation = attestation;
    this.receivedSubnetId = receivedSubnetId;
    this.hashTreeRoot = Suppliers.memoize(unconvertedAttestation::hashTreeRoot);
    this.producedLocally = producedLocally;
    this.committeesSize = Optional.of(committeeSizes);
  }

  public boolean isProducedLocally() {
    return producedLocally;
  }

  public boolean isValidIndexedAttestation() {
    return isValidIndexedAttestation;
  }

  public void setValidIndexedAttestation() {
    this.isValidIndexedAttestation = true;
  }

  public boolean isAcceptedAsGossip() {
    return acceptedAsGossip;
  }

  public void setAcceptedAsGossip() {
    this.acceptedAsGossip = true;
  }

  public Optional<IndexedAttestationLight> getIndexedAttestation() {
    return indexedAttestation;
  }

  public Optional<Bytes32> getCommitteeShufflingSeed() {
    return committeeShufflingSeed;
  }

  public Optional<Int2IntMap> getCommitteesSize() {
    return committeesSize;
  }

  public OptionalInt getReceivedSubnetId() {
    return receivedSubnetId;
  }

  public void setIndexedAttestation(final IndexedAttestationLight indexedAttestation) {
    this.indexedAttestation = Optional.of(indexedAttestation);
  }

  public void saveCommitteeShufflingSeedAndCommitteesSize(final BeaconState state) {
    saveCommitteeShufflingSeed(state);
    saveCommitteesSize(state);
  }

  private void saveCommitteeShufflingSeed(final BeaconState state) {
    if (committeeShufflingSeed.isPresent()) {
      return;
    }
    final Bytes32 committeeShufflingSeed =
        spec.getSeed(
            state,
            spec.computeEpochAtSlot(attestation.getData().getSlot()),
            Domain.BEACON_ATTESTER);
    this.committeeShufflingSeed = Optional.of(committeeShufflingSeed);
  }

  public void saveCommitteesSize(final BeaconState state) {
    if (committeesSize.isPresent()) {
      return;
    }

    if (!(attestation.isSingleAttestation() || attestation.requiresCommitteeBits())) {
      // it isn't a PECTRA attestation, do nothing
      return;
    }

    final Int2IntMap committeesSize =
        spec.getBeaconCommitteesSize(state, attestation.getData().getSlot());
    this.committeesSize = Optional.of(committeesSize);
  }

  public boolean isGossiped() {
    return gossiped.get();
  }

  public boolean markGossiped() {
    return gossiped.compareAndSet(false, true);
  }

  public boolean isAggregate() {
    return maybeAggregate.isPresent();
  }

  @NotNull
  public Attestation getAttestation() {
    return attestation;
  }

  public Attestation getUnconvertedAttestation() {
    return unconvertedAttestation;
  }

  public SignedAggregateAndProof getSignedAggregateAndProof() {
    return maybeAggregate.orElseThrow(
        () -> new UnsupportedOperationException("ValidatableAttestation is not an aggregate."));
  }

  public AttestationData getData() {
    return attestation.getData();
  }

  public UInt64 getEarliestSlotForForkChoiceProcessing() {
    return attestation.getEarliestSlotForForkChoiceProcessing(spec);
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return attestation.getDependentBlockRoots();
  }

  public Bytes32 hashTreeRoot() {
    return hashTreeRoot.get();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ValidatableAttestation that)) {
      return false;
    }
    return Objects.equal(getAttestation(), that.getAttestation())
        && Objects.equal(maybeAggregate, that.maybeAggregate);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getAttestation(), maybeAggregate);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("spec", spec)
        .add("attestation", attestation)
        .add("maybeAggregate", maybeAggregate)
        .add("hashTreeRoot", hashTreeRoot)
        .add("gossiped", gossiped)
        .add("producedLocally", producedLocally)
        .add("isValidIndexedAttestation", isValidIndexedAttestation)
        .add("indexedAttestation", indexedAttestation)
        .add("committeeShufflingSeed", committeeShufflingSeed)
        .add("committeesSize", committeesSize)
        .add("receivedSubnetId", receivedSubnetId)
        .add("unconvertedAttestation", unconvertedAttestation)
        .toString();
  }
}
