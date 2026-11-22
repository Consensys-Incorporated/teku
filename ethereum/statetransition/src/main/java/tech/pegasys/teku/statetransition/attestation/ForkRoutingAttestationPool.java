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

package tech.pegasys.teku.statetransition.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Keeps attestations from before PQ in the BLS attestation pool, and those from PQ on, which carry
 * XMSS signatures and STARK proofs, in {@link AggregatingAttestationPoolPq}.
 */
public class ForkRoutingAttestationPool extends AggregatingAttestationPool {

  private final AggregatingAttestationPool preForkPool;
  private final AggregatingAttestationPool pqPool;

  public ForkRoutingAttestationPool(
      final Spec spec,
      final RecentChainData recentChainData,
      final AggregatingAttestationPool preForkPool,
      final AggregatingAttestationPool pqPool) {
    super(spec, recentChainData);
    this.preForkPool = preForkPool;
    this.pqPool = pqPool;
  }

  private AggregatingAttestationPool poolForSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.PQ)
        ? pqPool
        : preForkPool;
  }

  @Override
  public int getSize() {
    return preForkPool.getSize() + pqPool.getSize();
  }

  /** PQ attestations are routed by type: around the fork, either kind may be valid. */
  private static boolean isPq(final Attestation attestation) {
    return attestation instanceof AttestationPq || attestation instanceof SingleAttestationPq;
  }

  private AggregatingAttestationPool poolFor(final Attestation attestation) {
    return isPq(attestation) ? pqPool : preForkPool;
  }

  @Override
  public void add(final ValidatableAttestation attestation) {
    poolFor(attestation.getAttestation()).add(attestation);
  }

  @Override
  public SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    return poolForSlot(stateAtBlockSlot.getSlot())
        .getAttestationsForBlock(stateAtBlockSlot, forkChecker);
  }

  @Override
  public PqBlockAttestations getPqAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    return pqPool.getPqAttestationsForBlock(stateAtBlockSlot, forkChecker);
  }

  @Override
  public Optional<Attestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {
    return pqPool
        .createAggregateFor(attestationHashTreeRoot, committeeIndex)
        .or(() -> preForkPool.createAggregateFor(attestationHashTreeRoot, committeeIndex));
  }

  @Override
  public SafeFuture<Optional<PqAggregate>> createPqAggregateFor(
      final Bytes32 attestationHashTreeRoot, final UInt64 committeeIndex) {
    return pqPool.createPqAggregateFor(attestationHashTreeRoot, committeeIndex);
  }

  @Override
  public void onLocalAggregationDuty(final UInt64 slot, final UInt64 committeeIndex) {
    poolForSlot(slot).onLocalAggregationDuty(slot, committeeIndex);
  }

  @Override
  public List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    final List<Attestation> attestations =
        new ArrayList<>(preForkPool.getAttestations(maybeSlot, maybeCommitteeIndex));
    attestations.addAll(pqPool.getAttestations(maybeSlot, maybeCommitteeIndex));
    return attestations;
  }

  @Override
  public void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    final List<Attestation> preFork = new ArrayList<>();
    final List<Attestation> pq = new ArrayList<>();
    StreamSupport.stream(attestations.spliterator(), false)
        .forEach(attestation -> (isPq(attestation) ? pq : preFork).add(attestation));
    if (!preFork.isEmpty()) {
      preForkPool.onAttestationsIncludedInBlock(slot, preFork);
    }
    if (!pq.isEmpty()) {
      pqPool.onAttestationsIncludedInBlock(slot, pq);
    }
  }

  @Override
  public void onReorg(final UInt64 commonAncestorSlot) {
    preForkPool.onReorg(commonAncestorSlot);
    pqPool.onReorg(commonAncestorSlot);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    preForkPool.onSlot(slot);
    pqPool.onSlot(slot);
  }
}
