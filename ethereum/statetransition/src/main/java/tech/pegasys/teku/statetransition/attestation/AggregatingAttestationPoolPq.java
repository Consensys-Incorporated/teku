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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.ProvenStatements;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProver;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.statetransition.attestation.MergedProofCache.Claim;
import tech.pegasys.teku.statetransition.attestation.MergedProofCache.MergedProof;
import tech.pegasys.teku.statetransition.pq.timing.PqTiming;
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
 *
 * <p>Proving and merging take a recursive prover seconds, so they happen outside the pool's lock.
 * With a recursive prover, a block's proof is also merged incrementally: when a local validator
 * proposes the next slot, the pool merges the timely committees' proofs at the start of this one,
 * and block production merges only what's new into that, at most {@link #MAX_PROOFS_PER_MERGE}
 * proofs.
 */
public class AggregatingAttestationPoolPq extends AggregatingAttestationPool {

  private static final Logger LOG = LogManager.getLogger();

  /** Network aggregates kept per committee, none a subset of another. */
  static final int MAX_AGGREGATES_PER_COMMITTEE = 4;

  /**
   * The most proofs a recursive prover merges for a block, counting an earlier merge as one: one
   * leanVM aggregation ({@code LeanVmSidecar.MAX_CHILDREN_PER_AGGREGATE}), about a second on 16
   * cores. Committees beyond it wait for a later block.
   */
  static final int MAX_PROOFS_PER_MERGE = 16;

  private final StarkProver starkProver;
  private final Optional<AsyncRunner> asyncRunner;
  private final int maxProofsPerMerge;
  private final MergedProofCache mergedProofs = new MergedProofCache();
  private final NavigableMap<UInt64, Set<Bytes32>> dataRootsBySlot = new TreeMap<>();
  private final Map<Bytes32, Map<Integer, CommitteeAttestations>> committeesByDataRoot =
      new HashMap<>();
  private int size = 0;

  /** A pool that doesn't merge block proofs ahead of time. */
  public AggregatingAttestationPoolPq(
      final Spec spec, final RecentChainData recentChainData, final StarkProver starkProver) {
    this(spec, recentChainData, starkProver, Optional.empty(), MAX_PROOFS_PER_MERGE);
  }

  /** A pool that merges block proofs ahead of local proposals on {@code asyncRunner}. */
  public AggregatingAttestationPoolPq(
      final Spec spec,
      final RecentChainData recentChainData,
      final StarkProver starkProver,
      final AsyncRunner asyncRunner) {
    this(spec, recentChainData, starkProver, Optional.of(asyncRunner), MAX_PROOFS_PER_MERGE);
  }

  AggregatingAttestationPoolPq(
      final Spec spec,
      final RecentChainData recentChainData,
      final StarkProver starkProver,
      final Optional<AsyncRunner> asyncRunner,
      final int maxProofsPerMerge) {
    super(spec, recentChainData);
    this.starkProver = starkProver;
    this.asyncRunner = asyncRunner;
    this.maxProofsPerMerge = maxProofsPerMerge;
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
      if (committee.singleSignatures.putIfAbsent(
              position,
              new SingleSignature(single.getAttesterIndex().intValue(), single.getSignature()))
          == null) {
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
  public PqBlockAttestations getPqAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 blockSlot = stateAtBlockSlot.getSlot();
    final SchemaDefinitionsPq schemaDefinitions =
        SchemaDefinitionsPq.required(spec.atSlot(blockSlot).getSchemaDefinitions());
    final MergeResult merged = mergeForBlock(stateAtBlockSlot, forkChecker);
    final AttestationPqSchema attestationSchema =
        schemaDefinitions.getPqAttestationSchemas().getAttestationSchema();
    return new PqBlockAttestations(
        schemaDefinitions
            .getBeaconBlockBodySchema()
            .getAttestationsSchema()
            .createFromElements(
                merged.included().stream()
                    .<Attestation>map(
                        data ->
                            createOnChainAggregate(
                                attestationSchema, data.data(), data.committees()))
                    .toList()),
        merged.proof());
  }

  private MergeResult mergeForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 blockSlot = stateAtBlockSlot.getSlot();
    final List<SelectedData> selected;
    synchronized (this) {
      selected = selectForBlock(stateAtBlockSlot, forkChecker);
    }
    final MergeResult merged = merge(blockSlot, selected);
    recordProposalSnapshot(blockSlot, merged);
    return merged;
  }

  private List<SelectedData> selectForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final UInt64 blockSlot = stateAtBlockSlot.getSlot();
    final SpecConfigPq config = SpecConfigPq.required(spec.atSlot(blockSlot).getConfig());
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
                                  committeesByDataRoot.get(dataRoot),
                                  true)
                              .ifPresent(candidates::add)));
    }
    candidates.sort(
        Comparator.comparing(DataCandidate::isTimely)
            .thenComparingInt(DataCandidate::newAttesterCount)
            .reversed());
    return select(stateAtBlockSlot, config, candidates);
  }

  /**
   * The committees to include, in order, with what's needed to prove and merge them unlocked: at
   * most {@code MAX_ATTESTATIONS_PQ} attestations, their proofs within {@code
   * MAX_STARK_PROOF_SIZE}.
   */
  private List<SelectedData> select(
      final BeaconState state, final SpecConfigPq config, final List<DataCandidate> candidates) {
    final List<SelectedData> selected = new ArrayList<>();
    // A recursive proof's size barely grows with its children; the stand-in's is their sum
    final boolean budgetProofSize = starkProver.isMergedProofSizeTheSumOfChildren();
    long remainingProofSize = config.getMaxStarkProofSize();
    for (final DataCandidate candidate : candidates) {
      if (selected.size() >= config.getMaxAttestationsPq()) {
        break;
      }
      final List<SelectedCommittee> included = new ArrayList<>();
      for (final CommitteeCandidate committee : candidate.committees()) {
        final long proofSize = budgetProofSize ? committee.proofSize() : 0;
        if (proofSize <= remainingProofSize) {
          included.add(committee.select(state));
          remainingProofSize -= proofSize;
        }
      }
      if (!included.isEmpty()) {
        selected.add(new SelectedData(candidate.data(), included));
      }
    }
    return selected;
  }

  /** The block's proof of the selected committees, and those it proves. */
  private MergeResult merge(final UInt64 blockSlot, final List<SelectedData> selected) {
    if (starkProver.isIncrementallyMergeable()) {
      return mergeIncrementally(blockSlot, selected);
    }
    final List<ProvenStatements> proofs =
        selected.stream()
            .flatMap(data -> data.committees().stream())
            .map(
                committee ->
                    new ProvenStatements(
                        List.of(committee.statement()), proveForBlock(blockSlot, committee)))
            .toList();
    return new MergeResult(selected, mergeProofs(blockSlot, 1, proofs, selected.size()), 1);
  }

  /**
   * Merges the selected committees' proofs into the largest proof already merged for this block
   * whose claims are all among theirs, so only new proofs are merged. Fresh proofs that share
   * claims with that earlier proof must go in, or it would prove attesters the block leaves out;
   * other fresh proofs go in, in order, while the merge has fewer than {@link #maxProofsPerMerge}
   * proofs, and the rest are left out of the block.
   */
  private MergeResult mergeIncrementally(
      final UInt64 blockSlot, final List<SelectedData> selected) {
    final Set<Claim> available = new HashSet<>();
    selected.forEach(data -> data.committees().forEach(c -> available.addAll(c.claims())));
    final Optional<MergedProof> earlier = mergedProofs.findLargestWithin(blockSlot, available);
    final Set<Claim> earlierClaims = earlier.map(MergedProof::claims).orElse(Set.of());
    final long overlapping =
        selected.stream()
            .flatMap(data -> data.committees().stream())
            .filter(committee -> isPartlyIn(committee.claims(), earlierClaims))
            .count();
    long optionalLeft =
        Math.max(0, maxProofsPerMerge - (earlier.isPresent() ? 1 : 0) - overlapping);

    final List<SelectedData> included = new ArrayList<>();
    final List<SelectedCommittee> fresh = new ArrayList<>();
    int leftOut = 0;
    for (final SelectedData data : selected) {
      final List<SelectedCommittee> committees = new ArrayList<>();
      for (final SelectedCommittee committee : data.committees()) {
        if (earlierClaims.containsAll(committee.claims())) {
          committees.add(committee);
        } else if (isPartlyIn(committee.claims(), earlierClaims) || optionalLeft-- > 0) {
          committees.add(committee);
          fresh.add(committee);
        } else {
          leftOut++;
        }
      }
      if (!committees.isEmpty()) {
        included.add(new SelectedData(data.data(), committees));
      }
    }
    if (leftOut > 0) {
      LOG.debug(
          "Leaving {} committee proofs out of the block at slot {} to merge at most {}",
          leftOut,
          blockSlot,
          maxProofsPerMerge);
    }
    if (fresh.isEmpty()) {
      return new MergeResult(
          included,
          earlier.map(MergedProof::proof).orElse(Bytes.EMPTY),
          earlier.map(MergedProof::round).orElse(0));
    }

    final List<ProvenStatements> children = new ArrayList<>();
    final Set<Claim> claims = new HashSet<>(earlierClaims);
    final List<XmssAggregateStatement> statements = new ArrayList<>();
    earlier.ifPresent(
        proof -> {
          children.add(new ProvenStatements(proof.statements(), proof.proof()));
          statements.addAll(proof.statements());
        });
    for (final SelectedCommittee committee : fresh) {
      children.add(
          new ProvenStatements(
              List.of(committee.statement()), proveForBlock(blockSlot, committee)));
      claims.addAll(committee.claims());
      statements.add(committee.statement());
    }
    final int round = earlier.map(MergedProof::round).orElse(0) + 1;
    final Bytes proof = mergeProofs(blockSlot, round, children, included.size());
    mergedProofs.add(blockSlot, new MergedProof(claims, statements, proof, round));
    return new MergeResult(included, proof, round);
  }

  /** Merges the proofs, timed as merge round {@code round} for the block when there are several. */
  private Bytes mergeProofs(
      final UInt64 blockSlot,
      final int round,
      final List<ProvenStatements> children,
      final int records) {
    if (children.size() < 2) {
      // a single proof is used as it is, without a backend call
      return starkProver.mergeProofs(children);
    }
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("input_proof_count", children.size());
    fields.put("records", records);
    fields.put(
        "claims",
        children.stream()
            .mapToInt(c -> c.statements().stream().mapToInt(st -> st.pubkeys().size()).sum())
            .sum());
    return PqTiming.job("initial", blockSlot, proposalReferenceSlot(blockSlot), round)
        .run(fields, () -> starkProver.mergeProofs(children));
  }

  private void recordProposalSnapshot(final UInt64 blockSlot, final MergeResult merged) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    if (!merged.proof().isEmpty()) {
      fields.put("proof_id", PqTiming.proofId(merged.proof()));
      fields.put("records", merged.included().size());
      fields.put(
          "claims",
          merged.included().stream()
              .flatMap(data -> data.committees().stream())
              .mapToInt(committee -> committee.claims().size())
              .sum());
    }
    PqTiming.job(
            "initial", blockSlot, proposalReferenceSlot(blockSlot), Math.max(1, merged.round()))
        .emit("proposal_snapshot", merged.proof().isEmpty() ? "no_candidate" : "candidate", fields);
  }

  /** The vote slot whose committees are timely in a block at {@code blockSlot}. */
  private UInt64 proposalReferenceSlot(final UInt64 blockSlot) {
    return blockSlot.minusMinZero(
        SpecConfigPq.required(spec.atSlot(blockSlot).getConfig())
            .getMinAttestationInclusionDelayPq());
  }

  private static boolean isPartlyIn(final Set<Claim> claims, final Set<Claim> others) {
    return !others.isEmpty()
        && !others.containsAll(claims)
        && claims.stream().anyMatch(others::contains);
  }

  /** A committee's proof for a block: its network proof, or proved now from its signatures. */
  private Bytes proveForBlock(final UInt64 blockSlot, final SelectedCommittee committee) {
    return prove(
        committee,
        PqTiming.job("fallback", blockSlot, proposalReferenceSlot(blockSlot)),
        Map.of("signatures", committee.signatures().size()));
  }

  private Bytes prove(
      final SelectedCommittee committee, final PqTiming.Job job, final Map<String, Object> fields) {
    return committee
        .networkProof()
        .orElseGet(
            () ->
                job.run(
                    fields,
                    () ->
                        starkProver.proveAggregates(
                            List.of(committee.statement()), List.of(committee.signatures()))));
  }

  /**
   * Merges the proofs of the timely committees for a block at {@code blockSlot}, if a local
   * validator proposes it, so that block production only merges what arrives after. Called at the
   * start of the slot before: the timely committees' aggregates are in by then, and the block in
   * between can't include them, so the merged proof stays usable.
   */
  SafeFuture<Void> prepareBlockProof(final UInt64 blockSlot) {
    if (!spec.atSlot(blockSlot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.PQ)) {
      return SafeFuture.COMPLETE;
    }
    return recentChainData
        .isBlockProposerConnected(blockSlot)
        .thenCompose(
            isProposing -> {
              if (!isProposing) {
                return SafeFuture.COMPLETE;
              }
              return recentChainData
                  .getBestState()
                  .map(state -> state.thenAccept(head -> prepareBlockProof(blockSlot, head)))
                  .orElse(SafeFuture.COMPLETE);
            });
  }

  private MergeResult prepareBlockProof(final UInt64 blockSlot, final BeaconState headState) {
    final List<SelectedData> timely;
    synchronized (this) {
      timely = selectTimely(blockSlot, headState);
    }
    if (timely.isEmpty()) {
      return new MergeResult(List.of(), Bytes.EMPTY, 0);
    }
    final long start = System.currentTimeMillis();
    final MergeResult merged = mergeIncrementally(blockSlot, timely);
    LOG.info(
        "Merged {} committee proofs ahead of the block at slot {} in {} ms",
        merged.included().stream().mapToInt(data -> data.committees().size()).sum(),
        blockSlot,
        System.currentTimeMillis() - start);
    return merged;
  }

  /**
   * For PQ timing experiments: proves the single attestations of a committee at {@code slot}, for
   * the data most of them voted for, as an aggregator would. Network aggregates are left out, as
   * they need no proving. Empty when the committee has no single attestations.
   */
  public Optional<MeasuredProof> proveCommitteeForMeasurement(
      final UInt64 slot, final int committeeIndex) {
    final Optional<CommitteeCandidate> maybeCandidate;
    synchronized (this) {
      maybeCandidate =
          dataRootsBySlot.getOrDefault(slot, Set.of()).stream()
              .map(committeesByDataRoot::get)
              .filter(Objects::nonNull)
              .map(committees -> committees.get(committeeIndex))
              .filter(Objects::nonNull)
              .flatMap(committee -> committee.getAllCandidates().stream())
              .filter(candidate -> candidate.networkProof.isEmpty())
              .max(Comparator.comparingInt(CommitteeCandidate::attesterCount));
    }
    if (maybeCandidate.isEmpty()) {
      return Optional.empty();
    }
    final CommitteeCandidate candidate = maybeCandidate.get();
    return retrieveStateForAttestation(candidate.committee().data)
        .map(
            state -> {
              final SelectedCommittee selected;
              synchronized (this) {
                selected = candidate.select(state);
              }
              final Map<String, Object> fields = new LinkedHashMap<>();
              fields.put("committee", selected.committeeIndex());
              fields.put("signatures", selected.signatures().size());
              fields.put("committee_size", selected.committeeSize());
              final Bytes proof = prove(selected, PqTiming.job("committee", slot, slot), fields);
              return new MeasuredProof(
                  List.of(selected.statement()), proof, selected.claims().size(), 1, 0);
            });
  }

  /**
   * For PQ timing experiments: the merge ahead of a block at {@code blockSlot}, as if a local
   * validator proposed it. Empty when there is nothing to merge.
   */
  public Optional<MeasuredProof> mergeAheadForMeasurement(
      final UInt64 blockSlot, final BeaconState headState) {
    return MeasuredProof.of(prepareBlockProof(blockSlot, headState));
  }

  /**
   * For PQ timing experiments: the attestations' proof for a block at the state's slot, as block
   * production merges it. Empty when there is nothing to include.
   */
  public Optional<MeasuredProof> mergeForBlockMeasurement(final BeaconState stateAtBlockSlot) {
    return MeasuredProof.of(
        mergeForBlock(stateAtBlockSlot, new AttestationForkChecker(spec, stateAtBlockSlot)));
  }

  /**
   * A proof made for a PQ timing experiment, which is never published.
   *
   * @param statements what the proof proves, to verify it
   * @param claims the attesters it proves
   * @param records the attestations it covers
   * @param round how many merges built it, 0 for a committee proof
   */
  public record MeasuredProof(
      List<XmssAggregateStatement> statements, Bytes proof, int claims, int records, int round) {

    private static Optional<MeasuredProof> of(final MergeResult merged) {
      if (merged.proof().isEmpty()) {
        return Optional.empty();
      }
      final List<SelectedCommittee> committees =
          merged.included().stream().flatMap(data -> data.committees().stream()).toList();
      return Optional.of(
          new MeasuredProof(
              committees.stream().map(SelectedCommittee::statement).toList(),
              merged.proof(),
              committees.stream().mapToInt(committee -> committee.claims().size()).sum(),
              merged.included().size(),
              merged.round()));
    }
  }

  /**
   * The committees with attesters at the block's timely slot, by attesters. The head state gives
   * their statements; their validity for the block is checked when it is produced.
   */
  private List<SelectedData> selectTimely(final UInt64 blockSlot, final BeaconState headState) {
    final SpecConfigPq config = SpecConfigPq.required(spec.atSlot(blockSlot).getConfig());
    final UInt64 timelySlot = blockSlot.minusMinZero(config.getMinAttestationInclusionDelayPq());
    final AttestationForkChecker forkChecker = new AttestationForkChecker(spec, headState);
    final List<DataCandidate> candidates = new ArrayList<>();
    dataRootsBySlot
        .getOrDefault(timelySlot, Set.of())
        .forEach(
            dataRoot ->
                createDataCandidate(
                        headState,
                        forkChecker,
                        timelySlot,
                        committeesByDataRoot.get(dataRoot),
                        false)
                    .ifPresent(candidates::add));
    candidates.sort(Comparator.comparingInt(DataCandidate::newAttesterCount).reversed());
    return select(headState, config, candidates);
  }

  private Optional<DataCandidate> createDataCandidate(
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final UInt64 timelySlot,
      final Map<Integer, CommitteeAttestations> committees,
      final boolean validate) {
    if (committees == null || committees.isEmpty()) {
      return Optional.empty();
    }
    final AttestationData data = committees.values().iterator().next().data;
    if (validate && spec.validateAttestation(stateAtBlockSlot, data).isPresent()) {
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
      final List<SelectedCommittee> committees) {
    final IntList setBits = new IntArrayList();
    final int[] committeeIndices = new int[committees.size()];
    int offset = 0;
    for (int i = 0; i < committees.size(); i++) {
      final SelectedCommittee committee = committees.get(i);
      committeeIndices[i] = committee.committeeIndex();
      final int committeeOffset = offset;
      committee.bits().stream().forEach(position -> setBits.add(committeeOffset + position));
      offset += committee.committeeSize();
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
            state -> {
              final SelectedCommittee selected;
              synchronized (this) {
                selected = candidate.select(state);
              }
              final UInt64 slot = candidate.committee().data.getSlot();
              final Map<String, Object> fields = new LinkedHashMap<>();
              fields.put("committee", selected.committeeIndex());
              fields.put("signatures", selected.signatures().size());
              fields.put("committee_size", selected.committeeSize());
              return new PqAggregate(
                  candidate.toAttestation(getAttestationSchema(candidate)),
                  prove(selected, PqTiming.job("committee", slot, slot), fields));
            });
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
  public void onSlot(final UInt64 slot) {
    pruneExpired(slot);
    mergedProofs.prune(slot);
    if (asyncRunner.isPresent() && starkProver.isIncrementallyMergeable()) {
      asyncRunner.get().runAsync(() -> prepareBlockProof(slot.increment())).finishError(LOG);
    }
  }

  private synchronized void pruneExpired(final UInt64 slot) {
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

  /** A single attestation's XMSS signature and the validator who made it. */
  private record SingleSignature(int attesterIndex, Bytes signature) {}

  private record SelectedData(AttestationData data, List<SelectedCommittee> committees) {}

  /**
   * @param round how many merges built the proof, 0 when there was nothing to merge
   */
  private record MergeResult(List<SelectedData> included, Bytes proof, int round) {}

  /**
   * A committee's attesters chosen for a block or an aggregate, with what proves them: a network
   * aggregate's proof, or the single attestations' signatures to prove.
   */
  private record SelectedCommittee(
      int committeeIndex,
      int committeeSize,
      BitSet bits,
      XmssAggregateStatement statement,
      Set<Claim> claims,
      Optional<Bytes> networkProof,
      List<Bytes> signatures) {}

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

    /** What proving and merging this candidate needs, taken while holding the pool's lock. */
    SelectedCommittee select(final BeaconState state) {
      final List<Bytes> signatures = new ArrayList<>();
      if (networkProof.isEmpty()) {
        bits.stream()
            .forEach(
                position -> signatures.add(committee.singleSignatures.get(position).signature()));
      }
      final XmssAggregateStatement statement = statement(state);
      return new SelectedCommittee(
          committee.committeeIndex,
          committee.committeeSize,
          bits,
          statement,
          Claim.of(statement),
          networkProof,
          signatures);
    }

    /**
     * The statement this candidate's proof proves. Built from the single attestations' attesters,
     * it needs no committee lookup, so the state may be well behind the attestation, e.g. after
     * empty slots. A network aggregate's needs its committee from {@code state}, which must be
     * recent enough, as a block's state is.
     */
    XmssAggregateStatement statement(final BeaconState state) {
      final BeaconStateAccessorsPq accessors =
          (BeaconStateAccessorsPq) spec.atSlot(state.getSlot()).beaconStateAccessors();
      if (networkProof.isPresent()) {
        return accessors
            .getAttestationStatements(state, toAttestation(getAttestationSchema(this)))
            .getFirst();
      }
      final IntList attesters = new IntArrayList(bits.cardinality());
      bits.stream()
          .forEach(
              position -> attesters.add(committee.singleSignatures.get(position).attesterIndex()));
      return accessors.getAttestationStatement(state, committee.data, attesters);
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
    // By position in the committee
    private final Int2ObjectSortedMap<SingleSignature> singleSignatures =
        new Int2ObjectAVLTreeMap<>();
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
