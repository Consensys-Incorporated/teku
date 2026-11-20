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

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProver;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * The attestation pool from PQ on. A PQ attestation carries no signature: a block's attestations
 * are proven by the merged STARK proofs of the network aggregates behind them, and an aggregator
 * proves the XMSS signatures of the single attestations it aggregates.
 *
 * <p>So for each attestation data and committee, the pool keeps the single attestations' XMSS
 * signatures, by position in the committee, and the network aggregates with their proofs. An
 * aggregate can't be merged with another, since each proof covers exactly its own attesters: a
 * block takes one aggregate per committee, the one adding the most attesters not yet on chain.
 */
public class AggregatingAttestationPoolPq extends AggregatingAttestationPool {

  /** Network aggregates kept per committee, none a subset of another. */
  static final int MAX_AGGREGATES_PER_COMMITTEE = 4;

  private final StarkProver starkProver;
  private final NavigableMap<UInt64, Set<Bytes32>> dataRootsBySlot = new TreeMap<>();
  private final Map<Bytes32, Map<Integer, CommitteeAttestations>> committeesByDataRoot =
      new HashMap<>();
  private int size = 0;

  public AggregatingAttestationPoolPq(
      final Spec spec, final RecentChainData recentChainData, final StarkProver starkProver) {
    super(spec, recentChainData);
    this.starkProver = starkProver;
  }

  @Override
  public synchronized int getSize() {
    return size;
  }

  /**
   * Keeps a validated single attestation's XMSS signature, or a validated network aggregate with
   * its proof. Attestations without either, such as those from blocks, can't be proven again and
   * are not kept.
   */
  @Override
  public synchronized void add(final ValidatableAttestation attestation) {
    if (!(attestation.getAttestation() instanceof AttestationPq aggregate)) {
      return;
    }
    final Optional<Bytes32> shufflingSeed = attestation.getCommitteeShufflingSeed();
    if (shufflingSeed.isEmpty()) {
      return;
    }
    final AttestationData data = aggregate.getData();
    final Bytes32 dataRoot = data.hashTreeRoot();
    final int committeeIndex = aggregate.getFirstCommitteeIndex().intValue();
    final SszBitlist bits = aggregate.getAggregationBits();
    final CommitteeAttestations committee =
        committeesByDataRoot
            .computeIfAbsent(
                dataRoot,
                __ -> {
                  dataRootsBySlot
                      .computeIfAbsent(data.getSlot(), ___ -> new HashSet<>())
                      .add(dataRoot);
                  return new HashMap<>();
                })
            .computeIfAbsent(
                committeeIndex,
                __ ->
                    new CommitteeAttestations(
                        data, committeeIndex, bits.size(), shufflingSeed.get()));
    if (bits.size() != committee.committeeSize
        || !committee.shufflingSeed.equals(shufflingSeed.get())) {
      return;
    }
    if (attestation.getUnconvertedAttestation() instanceof SingleAttestationPq single) {
      final int position = bits.streamAllSetBits().findFirst().orElseThrow();
      if (committee.singleSignatures.putIfAbsent(position, single.getSignature()) == null) {
        size++;
      }
    } else {
      attestation
          .getAggregateProof()
          .ifPresent(proof -> size += committee.addAggregate(new ProvenAggregate(bits, proof)));
    }
  }

  @Override
  public SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    return getPqAttestationsForBlock(stateAtBlockSlot, forkChecker).attestations();
  }

  /**
   * The attestations for a PQ block at the state's slot, and the proof of their signatures
   * (stark.MergeProofs of each committee's proof, in body order).
   *
   * <p>Attestations are included {@code MIN_ATTESTATION_INCLUSION_DELAY_PQ} slots or more after
   * their slot, those at exactly that delay first, since only they earn the timely head reward.
   * Committees with equal data share an attestation (compute_on_chain_aggregate). The proof stays
   * within {@code MAX_STARK_PROOF_SIZE}.
   */
  @Override
  public synchronized PqBlockAttestations getPqAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 blockSlot = stateAtBlockSlot.getSlot();
    final SpecConfigPq config = SpecConfigPq.required(spec.atSlot(blockSlot).getConfig());
    final SchemaDefinitionsPq schemaDefinitions =
        SchemaDefinitionsPq.required(spec.atSlot(blockSlot).getSchemaDefinitions());
    final List<DataCandidate> candidates = new ArrayList<>();
    final int inclusionDelay = config.getMinAttestationInclusionDelayPq();
    if (blockSlot.isGreaterThanOrEqualTo(inclusionDelay)) {
      final UInt64 timelySlot = blockSlot.minus(inclusionDelay);
      final UInt64 earliestSlot =
          spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(blockSlot).minusMinZero(1));
      dataRootsBySlot
          .subMap(earliestSlot, true, timelySlot, true)
          .values()
          .forEach(
              dataRoots ->
                  dataRoots.forEach(
                      dataRoot ->
                          createDataCandidate(
                                  stateAtBlockSlot,
                                  forkChecker,
                                  timelySlot,
                                  committeesByDataRoot.get(dataRoot))
                              .ifPresent(candidates::add)));
    }
    candidates.sort(
        Comparator.comparing(DataCandidate::isTimely)
            .thenComparingInt(DataCandidate::newAttesterCount)
            .reversed());

    final AttestationPqSchema attestationSchema =
        schemaDefinitions.getPqAttestationSchemas().getAttestationSchema();
    final List<Attestation> attestations = new ArrayList<>();
    final List<Bytes> proofs = new ArrayList<>();
    long remainingProofSize = config.getMaxStarkProofSize();
    for (final DataCandidate candidate : candidates) {
      if (attestations.size() >= config.getMaxAttestationsPq()) {
        break;
      }
      final List<CommitteeCandidate> included = new ArrayList<>();
      for (final CommitteeCandidate committee : candidate.committees()) {
        final long proofSize = committee.proofSize();
        if (proofSize <= remainingProofSize) {
          included.add(committee);
          remainingProofSize -= proofSize;
        }
      }
      if (included.isEmpty()) {
        continue;
      }
      attestations.add(createOnChainAggregate(attestationSchema, candidate.data(), included));
      included.forEach(committee -> proofs.add(committee.proof(stateAtBlockSlot)));
    }
    return new PqBlockAttestations(
        schemaDefinitions
            .getBeaconBlockBodySchema()
            .getAttestationsSchema()
            .createFromElements(attestations),
        starkProver.mergeProofs(proofs));
  }

  private Optional<DataCandidate> createDataCandidate(
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final UInt64 timelySlot,
      final Map<Integer, CommitteeAttestations> committees) {
    if (committees == null || committees.isEmpty()) {
      return Optional.empty();
    }
    final AttestationData data = committees.values().iterator().next().data;
    if (spec.validateAttestation(stateAtBlockSlot, data).isPresent()) {
      return Optional.empty();
    }
    final List<CommitteeCandidate> committeeCandidates = new ArrayList<>();
    int newAttesters = 0;
    for (final CommitteeAttestations committee : new TreeMap<>(committees).values()) {
      if (!forkChecker.isFromCorrectFork(committee.shufflingSeed)) {
        continue;
      }
      final Optional<CommitteeCandidate> best = committee.getBestCandidate(true);
      if (best.isPresent()) {
        committeeCandidates.add(best.get());
        newAttesters += best.get().newAttesterCount();
      }
    }
    if (committeeCandidates.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new DataCandidate(
            data, data.getSlot().equals(timelySlot), newAttesters, committeeCandidates));
  }

  /** compute_on_chain_aggregate: the committees' aggregation bits, in committee order. */
  private AttestationPq createOnChainAggregate(
      final AttestationPqSchema schema,
      final AttestationData data,
      final List<CommitteeCandidate> committees) {
    final IntList setBits = new IntArrayList();
    final int[] committeeIndices = new int[committees.size()];
    int offset = 0;
    for (int i = 0; i < committees.size(); i++) {
      final CommitteeCandidate committee = committees.get(i);
      committeeIndices[i] = committee.committee().committeeIndex;
      final int committeeOffset = offset;
      committee.bits().stream().forEach(position -> setBits.add(committeeOffset + position));
      offset += committee.committee().committeeSize;
    }
    return schema.create(
        schema.getAggregationBitsSchema().ofBits(offset, setBits.toIntArray()),
        data,
        schema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndices));
  }

  @Override
  public synchronized Optional<Attestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {
    return findCommittee(attestationHashTreeRoot, committeeIndex)
        .flatMap(committee -> committee.getBestCandidate(false))
        .map(candidate -> candidate.toAttestation(getAttestationSchema(candidate)));
  }

  /**
   * The best aggregate for the data and committee with the proof of its signatures, as an
   * aggregator publishes it: a network aggregate, or one built from the single attestations.
   */
  @Override
  public Optional<PqAggregate> createPqAggregateFor(
      final Bytes32 attestationHashTreeRoot, final UInt64 committeeIndex) {
    final Optional<CommitteeCandidate> maybeCandidate;
    synchronized (this) {
      maybeCandidate =
          findCommittee(attestationHashTreeRoot, Optional.of(committeeIndex))
              .flatMap(committee -> committee.getBestCandidate(false));
    }
    if (maybeCandidate.isEmpty()) {
      return Optional.empty();
    }
    final CommitteeCandidate candidate = maybeCandidate.get();
    return retrieveStateForAttestation(candidate.committee().data)
        .map(
            state ->
                new PqAggregate(
                    candidate.toAttestation(getAttestationSchema(candidate)),
                    candidate.proof(state)));
  }

  private Optional<CommitteeAttestations> findCommittee(
      final Bytes32 dataRoot, final Optional<UInt64> committeeIndex) {
    final Map<Integer, CommitteeAttestations> committees = committeesByDataRoot.get(dataRoot);
    if (committees == null) {
      return Optional.empty();
    }
    if (committeeIndex.isPresent()) {
      return Optional.ofNullable(committees.get(committeeIndex.get().intValue()));
    }
    return committees.values().stream()
        .max(
            Comparator.comparingInt(
                committee ->
                    committee
                        .getBestCandidate(false)
                        .map(CommitteeCandidate::attesterCount)
                        .orElse(0)));
  }

  private AttestationPqSchema getAttestationSchema(final CommitteeCandidate candidate) {
    return SchemaDefinitionsPq.required(
            spec.atSlot(candidate.committee().data.getSlot()).getSchemaDefinitions())
        .getPqAttestationSchemas()
        .getAttestationSchema();
  }

  @Override
  public synchronized List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {
    final List<Attestation> attestations = new ArrayList<>();
    final Map<UInt64, Set<Bytes32>> dataRoots =
        maybeSlot
            .<Map<UInt64, Set<Bytes32>>>map(slot -> dataRootsBySlot.subMap(slot, true, slot, true))
            .orElse(dataRootsBySlot);
    dataRoots
        .values()
        .forEach(
            roots ->
                roots.forEach(
                    root ->
                        committeesByDataRoot.get(root).values().stream()
                            .filter(
                                committee ->
                                    maybeCommitteeIndex
                                        .map(index -> index.intValue() == committee.committeeIndex)
                                        .orElse(true))
                            .flatMap(committee -> committee.getAllCandidates().stream())
                            .forEach(
                                candidate ->
                                    attestations.add(
                                        candidate.toAttestation(
                                            getAttestationSchema(candidate))))));
    return attestations;
  }

  /** Records the attesters a block included, so that later blocks prefer new ones. */
  @Override
  public synchronized void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    for (final Attestation attestation : attestations) {
      if (!(attestation instanceof AttestationPq aggregate)) {
        continue;
      }
      final Map<Integer, CommitteeAttestations> committees =
          committeesByDataRoot.get(aggregate.getData().hashTreeRoot());
      if (committees == null) {
        continue;
      }
      final SszBitlist bits = aggregate.getAggregationBits();
      int offset = 0;
      for (final UInt64 committeeIndex : aggregate.getCommitteeIndicesRequired()) {
        final CommitteeAttestations committee = committees.get(committeeIndex.intValue());
        if (committee == null) {
          // Its size is unknown, so the later committees' bits can't be found
          break;
        }
        for (int i = 0; i < committee.committeeSize; i++) {
          if (offset + i < bits.size() && bits.getBit(offset + i)) {
            committee.included.set(i);
          }
        }
        offset += committee.committeeSize;
      }
    }
  }

  /** The included attesters may have been on the abandoned branch, so they are forgotten. */
  @Override
  public synchronized void onReorg(final UInt64 commonAncestorSlot) {
    committeesByDataRoot
        .values()
        .forEach(committees -> committees.values().forEach(c -> c.included.clear()));
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    if (slot.isLessThan(ATTESTATION_RETENTION_SLOTS)) {
      return;
    }
    final NavigableMap<UInt64, Set<Bytes32>> expired =
        dataRootsBySlot.headMap(slot.minus(ATTESTATION_RETENTION_SLOTS), false);
    expired
        .values()
        .forEach(
            roots ->
                roots.forEach(
                    root -> {
                      final Map<Integer, CommitteeAttestations> committees =
                          committeesByDataRoot.remove(root);
                      if (committees != null) {
                        committees.values().forEach(committee -> size -= committee.size());
                      }
                    }));
    expired.clear();
  }

  private record ProvenAggregate(SszBitlist bits, Bytes proof) {}

  private record DataCandidate(
      AttestationData data,
      boolean isTimely,
      int newAttesterCount,
      List<CommitteeCandidate> committees) {}

  /**
   * One committee's attesters for an attestation: a network aggregate with its proof, or the single
   * attestations, whose proof is built when needed.
   */
  private final class CommitteeCandidate {
    private final CommitteeAttestations committee;
    private final BitSet bits;
    private final Optional<Bytes> networkProof;
    private final int newAttesterCount;

    private CommitteeCandidate(
        final CommitteeAttestations committee,
        final BitSet bits,
        final Optional<Bytes> networkProof) {
      this.committee = committee;
      this.bits = bits;
      this.networkProof = networkProof;
      final BitSet newBits = (BitSet) bits.clone();
      newBits.andNot(committee.included);
      this.newAttesterCount = newBits.cardinality();
    }

    CommitteeAttestations committee() {
      return committee;
    }

    BitSet bits() {
      return bits;
    }

    int attesterCount() {
      return bits.cardinality();
    }

    int newAttesterCount() {
      return newAttesterCount;
    }

    long proofSize() {
      return networkProof
          .map(proof -> (long) proof.size())
          .orElseGet(() -> starkProver.proofSizeOf(attesterCount()));
    }

    Bytes proof(final BeaconState state) {
      if (networkProof.isPresent()) {
        return networkProof.get();
      }
      final AttestationPq attestation = toAttestation(getAttestationSchema(this));
      final List<XmssAggregateStatement> statements =
          ((BeaconStateAccessorsPq) spec.atSlot(state.getSlot()).beaconStateAccessors())
              .getAttestationStatements(state, attestation);
      final List<Bytes> signatures = new ArrayList<>();
      bits.stream().forEach(position -> signatures.add(committee.singleSignatures.get(position)));
      return starkProver.proveAggregates(statements, List.of(signatures));
    }

    AttestationPq toAttestation(final AttestationPqSchema schema) {
      return schema.create(
          schema
              .getAggregationBitsSchema()
              .ofBits(committee.committeeSize, bits.stream().toArray()),
          committee.data,
          schema.getCommitteeBitsSchema().orElseThrow().ofBits(committee.committeeIndex));
    }
  }

  private final class CommitteeAttestations {
    private final AttestationData data;
    private final int committeeIndex;
    private final int committeeSize;
    private final Bytes32 shufflingSeed;
    private final Int2ObjectSortedMap<Bytes> singleSignatures = new Int2ObjectAVLTreeMap<>();
    private final List<ProvenAggregate> aggregates = new ArrayList<>();
    private final BitSet included = new BitSet();

    private CommitteeAttestations(
        final AttestationData data,
        final int committeeIndex,
        final int committeeSize,
        final Bytes32 shufflingSeed) {
      this.data = data;
      this.committeeIndex = committeeIndex;
      this.committeeSize = committeeSize;
      this.shufflingSeed = shufflingSeed;
    }

    int size() {
      return singleSignatures.size() + aggregates.size();
    }

    /** Adds an aggregate unless one already kept covers it, and returns the change in size. */
    int addAggregate(final ProvenAggregate aggregate) {
      if (aggregates.stream().anyMatch(kept -> kept.bits().isSuperSetOf(aggregate.bits()))) {
        return 0;
      }
      final int before = aggregates.size();
      aggregates.removeIf(kept -> aggregate.bits().isSuperSetOf(kept.bits()));
      aggregates.add(aggregate);
      if (aggregates.size() > MAX_AGGREGATES_PER_COMMITTEE) {
        aggregates.sort(Comparator.comparingInt(kept -> -kept.bits().getBitCount()));
        aggregates.subList(MAX_AGGREGATES_PER_COMMITTEE, aggregates.size()).clear();
      }
      return aggregates.size() - before;
    }

    List<CommitteeCandidate> getAllCandidates() {
      final List<CommitteeCandidate> candidates = new ArrayList<>();
      if (!singleSignatures.isEmpty()) {
        final BitSet bits = new BitSet(committeeSize);
        singleSignatures.keySet().forEach(bits::set);
        candidates.add(new CommitteeCandidate(this, bits, Optional.empty()));
      }
      for (final ProvenAggregate aggregate : aggregates) {
        final BitSet bits = new BitSet(committeeSize);
        aggregate.bits().streamAllSetBits().forEach(bits::set);
        candidates.add(new CommitteeCandidate(this, bits, Optional.of(aggregate.proof())));
      }
      return candidates;
    }

    /**
     * The candidate with the most attesters, or the most not yet on chain. A network aggregate wins
     * a tie, as its proof is already built.
     */
    Optional<CommitteeCandidate> getBestCandidate(final boolean newAttestersOnly) {
      return getAllCandidates().stream()
          .filter(candidate -> !newAttestersOnly || candidate.newAttesterCount() > 0)
          .max(
              Comparator.comparingInt(
                      (CommitteeCandidate candidate) ->
                          newAttestersOnly
                              ? candidate.newAttesterCount()
                              : candidate.attesterCount())
                  .thenComparing(candidate -> candidate.networkProof.isPresent()));
    }
  }
}
