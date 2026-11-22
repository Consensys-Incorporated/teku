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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
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
import tech.pegasys.teku.spec.logic.versions.pq.util.AttestationUtilPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AggregatingAttestationPoolPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(64, spec);

  private AggregatingAttestationPoolPq pool;
  private SignedBlockAndState head;
  private UInt64 attestationSlot;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    head =
        storageSystem
            .chainUpdater()
            .advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH).plus(1));
    storageSystem.chainUpdater().updateBestBlock(head);
    assertThat(spec.atSlot(head.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    attestationSlot = head.getSlot();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), StarkProver.CONCATENATED);
  }

  @Test
  void shouldAggregateSingleAttestationsWithProofOfTheirSignatures() {
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    final ValidatableAttestation first = singleAttestation(data, 0, committee.getInt(1));
    final ValidatableAttestation second = singleAttestation(data, 0, committee.getInt(0));
    pool.add(first);
    pool.add(second);

    final Optional<PqAggregate> aggregate =
        pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO);

    assertThat(aggregate).isPresent();
    assertThat(aggregate.get().attestation().getAggregationBits().getAllSetBits().toIntArray())
        .containsExactly(0, 1);
    // The proof is the signatures in committee order
    assertThat(aggregate.get().proof())
        .isEqualTo(Bytes.concatenate(signatureOf(second), signatureOf(first)));
    assertThat(pool.getSize()).isEqualTo(2);
  }

  @Test
  void shouldAggregateWhenTheHeadIsEpochsBehindTheAttestations() throws Exception {
    // With empty slots the head state is too old for committee lookups at the attestation's slot
    final UInt64 laterSlot = attestationSlot.plus(spec.slotsPerEpoch(attestationSlot) * 3L);
    storageSystem.chainUpdater().setCurrentSlot(laterSlot);
    final BeaconState laterState = spec.processSlots(head.getState(), laterSlot);
    final AttestationData data =
        spec.getGenericAttestationData(laterSlot, laterState, head, UInt64.ZERO);
    final IntList committee = spec.getBeaconCommittee(laterState, laterSlot, UInt64.ZERO);
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final AggregatingAttestationPoolPq recordingPool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), recordingProver(provenStatements));
    recordingPool.add(singleAttestation(laterState, data, 0, committee.getInt(1)));
    recordingPool.add(singleAttestation(laterState, data, 0, committee.getInt(0)));

    final Optional<PqAggregate> aggregate =
        recordingPool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO);

    assertThat(aggregate).isPresent();
    // The statement get_attestation_statements gives with an up-to-date state
    assertThat(provenStatements)
        .containsExactly(
            ((BeaconStateAccessorsPq) spec.atSlot(laterSlot).beaconStateAccessors())
                .getAttestationStatements(laterState, aggregate.get().attestation()));
  }

  @Test
  void shouldPreferANetworkAggregateWithMoreAttesters() {
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    pool.add(singleAttestation(data, 0, committee.getInt(0)));
    final Bytes proof = randomProof(2);
    pool.add(networkAggregate(data, 0, proof, 1, 2));

    final Optional<PqAggregate> aggregate =
        pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO);

    assertThat(aggregate.orElseThrow().proof()).isEqualTo(proof);
    assertThat(aggregate.get().attestation().getAggregationBits().getAllSetBits().toIntArray())
        .containsExactly(1, 2);
  }

  @Test
  void shouldKeepOnlyAggregatesThatAreNotSubsetsOfAnother() {
    final AttestationData data = attestationData(0);
    pool.add(networkAggregate(data, 0, randomProof(1), 0));
    pool.add(networkAggregate(data, 0, randomProof(2), 0, 1));
    pool.add(networkAggregate(data, 0, randomProof(1), 1));

    assertThat(pool.getSize()).isEqualTo(1);
    assertThat(pool.getAttestations(Optional.empty(), Optional.empty()))
        .singleElement()
        .satisfies(
            attestation ->
                assertThat(attestation.getAggregationBits().getAllSetBits().toIntArray())
                    .containsExactly(0, 1));
  }

  @Test
  void shouldNotKeepAttestationsWithoutSignaturesOrProof() {
    final AttestationData data = attestationData(0);
    final ValidatableAttestation fromBlock =
        ValidatableAttestation.from(spec, aggregate(data, 0, 0));
    fromBlock.saveCommitteeShufflingSeedAndCommitteesSize(head.getState());
    pool.add(fromBlock);

    assertThat(pool.getSize()).isZero();
  }

  @Test
  void shouldIncludeAttestationsOnlyAfterTheInclusionDelay() {
    final AttestationData data = attestationData(0);
    pool.add(networkAggregate(data, 0, randomProof(1), 0));

    assertThat(blockAttestations(attestationSlot.plus(1)).attestations()).isEmpty();
    assertThat(blockAttestations(attestationSlot.plus(2)).attestations()).hasSize(1);
  }

  @Test
  void shouldShareOneAttestationForCommitteesWithEqualDataAndMergeTheirProofs() {
    final AttestationData data = attestationData(0);
    assertThat(
            spec.getCommitteeCountPerSlot(head.getState(), spec.getCurrentEpoch(head.getState())))
        .isGreaterThanOrEqualTo(UInt64.valueOf(2));
    final Bytes proof0 = randomProof(1);
    final Bytes proof1 = randomProof(2);
    pool.add(networkAggregate(data, 1, proof1, 0, 1));
    pool.add(networkAggregate(data, 0, proof0, 1));

    final PqBlockAttestations blockAttestations = blockAttestations(attestationSlot.plus(2));

    assertThat(blockAttestations.attestations()).hasSize(1);
    final Attestation attestation = blockAttestations.attestations().get(0);
    assertThat(attestation.getCommitteeIndicesRequired()).containsExactly(UInt64.ZERO, UInt64.ONE);
    final int committee0Size = committee(0).size();
    assertThat(attestation.getAggregationBits().getAllSetBits().toIntArray())
        .containsExactly(1, committee0Size, committee0Size + 1);
    // Proofs merged in body order, committee by committee
    assertThat(blockAttestations.proof()).isEqualTo(Bytes.concatenate(proof0, proof1));
  }

  @Test
  void shouldPreferAttestersNotYetIncluded() {
    final AttestationData data = attestationData(0);
    pool.add(networkAggregate(data, 0, randomProof(1), 0));
    final UInt64 blockSlot = attestationSlot.plus(2);
    final PqBlockAttestations first = blockAttestations(blockSlot);
    pool.onAttestationsIncludedInBlock(blockSlot, first.attestations());

    assertThat(blockAttestations(blockSlot.plus(1)).attestations()).isEmpty();
  }

  @Test
  void shouldStayWithinTheProofSizeBudget() {
    final StarkProver hugeProofs =
        new StarkProver() {
          @Override
          public Bytes proveAggregates(
              final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
            return StarkProver.CONCATENATED.proveAggregates(statements, signatures);
          }

          @Override
          public long proofSizeOf(final int signatureCount) {
            return Long.MAX_VALUE;
          }

          @Override
          public Bytes mergeProofs(final List<ProvenStatements> proofs) {
            return StarkProver.CONCATENATED.mergeProofs(proofs);
          }
        };
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), hugeProofs);
    final AttestationData data = attestationData(0);
    pool.add(singleAttestation(data, 0, committee(0).getInt(0)));

    assertThat(blockAttestations(attestationSlot.plus(2)).attestations()).isEmpty();
  }

  @Test
  void shouldMergeRecursiveProofsWithTheirStatementsAndNoSizeBudget() {
    // A recursive prover: proof sizes don't add up, and merging needs each proof's statements
    final List<ProvenStatements> merged = new ArrayList<>();
    final StarkProver recursive =
        new StarkProver() {
          @Override
          public Bytes proveAggregates(
              final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
            return Bytes.fromHexString("0x5afe");
          }

          @Override
          public long proofSizeOf(final int signatureCount) {
            return Long.MAX_VALUE;
          }

          @Override
          public boolean isMergedProofSizeTheSumOfChildren() {
            return false;
          }

          @Override
          public Bytes mergeProofs(final List<ProvenStatements> proofs) {
            merged.addAll(proofs);
            return Bytes.fromHexString("0x3e26ed");
          }
        };
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), recursive);
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    pool.add(singleAttestation(data, 0, committee.getInt(1)));
    final Bytes networkProof = randomProof(2);
    pool.add(networkAggregate(attestationData(1), 1, networkProof, 0, 1));

    final PqBlockAttestations blockAttestations = blockAttestations(attestationSlot.plus(2));

    assertThat(blockAttestations.attestations()).hasSize(2);
    assertThat(blockAttestations.proof()).isEqualTo(Bytes.fromHexString("0x3e26ed"));
    // Each proof comes with the statement get_attestation_statements gives for its committee
    final BeaconStateAccessorsPq accessors =
        (BeaconStateAccessorsPq) spec.atSlot(attestationSlot).beaconStateAccessors();
    assertThat(merged).hasSize(2);
    for (final ProvenStatements proven : merged) {
      final XmssAggregateStatement statement = proven.statements().getFirst();
      final Attestation onChain =
          blockAttestations.attestations().stream()
              .filter(attestation -> attestation.getData().equals(statementData(proven)))
              .findFirst()
              .orElseThrow();
      assertThat(accessors.getAttestationStatements(head.getState(), onChain))
          .containsExactly(statement);
    }
    assertThat(merged.stream().map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(Bytes.fromHexString("0x5afe"), networkProof);
  }

  /** The attestation data of a merged proof, from which committee its statement covers. */
  private AttestationData statementData(final ProvenStatements proven) {
    return proven.proof().equals(Bytes.fromHexString("0x5afe"))
        ? attestationData(0)
        : attestationData(1);
  }

  @Test
  void shouldMergeOnlyNewProofsIntoAnEarlierMergedProof() {
    final RecursiveProver prover = new RecursiveProver();
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    final Bytes proof0 = randomProof(1);
    final Bytes proof1 = randomProof(1);
    pool.add(networkAggregate(attestationData(0), 0, proof0, 0));
    pool.add(networkAggregate(attestationData(1), 1, proof1, 0));
    final UInt64 blockSlot = attestationSlot.plus(2);

    final PqBlockAttestations first = blockAttestations(blockSlot);

    assertThat(prover.merges).hasSize(1);
    assertThat(prover.merges.getFirst().stream().map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(proof0, proof1);
    assertThat(first.proof()).isEqualTo(RecursiveProver.mergedProof(1));

    // An aggregate of committee 1 with one more attester replaces the one merged
    final Bytes betterProof1 = randomProof(2);
    pool.add(networkAggregate(attestationData(1), 1, betterProof1, 0, 1));

    final PqBlockAttestations second = blockAttestations(blockSlot);

    // Only the new aggregate is merged into the earlier proof, which proves both committees
    assertThat(prover.merges).hasSize(2);
    final List<ProvenStatements> children = prover.merges.get(1);
    assertThat(children.stream().map(ProvenStatements::proof))
        .containsExactly(first.proof(), betterProof1);
    assertThat(children.getFirst().statements()).hasSize(2);
    assertThat(second.proof()).isEqualTo(RecursiveProver.mergedProof(2));
    assertThat(attesterCount(second)).isEqualTo(3);

    // Nothing new: the last merged proof is the block's
    final PqBlockAttestations third = blockAttestations(blockSlot);
    assertThat(prover.merges).hasSize(2);
    assertThat(third.proof()).isEqualTo(second.proof());
    assertThat(attesterCount(third)).isEqualTo(3);
  }

  @Test
  void shouldLeaveOutProofsBeyondTheMergeLimit() {
    final RecursiveProver prover = new RecursiveProver();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, Optional.empty(), 1);
    // Committee 0's attestation adds more attesters, so it comes first
    final Bytes proof0 = randomProof(2);
    pool.add(networkAggregate(attestationData(0), 0, proof0, 0, 1));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));
    final UInt64 blockSlot = attestationSlot.plus(2);

    final PqBlockAttestations first = blockAttestations(blockSlot);

    assertThat(first.attestations()).hasSize(1);
    assertThat(first.attestations().get(0).getData()).isEqualTo(attestationData(0));
    assertThat(first.proof()).isEqualTo(proof0);

    // The earlier proof fills the merge, so committee 1 still waits
    final PqBlockAttestations second = blockAttestations(blockSlot);

    assertThat(second.attestations()).hasSize(1);
    assertThat(second.proof()).isEqualTo(proof0);
    assertThat(prover.merges).isEmpty();
  }

  @Test
  void shouldNotHoldThePoolLockWhileMerging() {
    final List<Integer> sizesSeenWhileMerging = new ArrayList<>();
    final RecursiveProver prover =
        new RecursiveProver() {
          @Override
          public Bytes mergeProofs(final List<ProvenStatements> proofs) {
            try {
              // Waits forever if this thread holds the pool's lock
              sizesSeenWhileMerging.add(
                  CompletableFuture.supplyAsync(pool::getSize).get(10, TimeUnit.SECONDS));
            } catch (final Exception e) {
              throw new IllegalStateException(e);
            }
            return super.mergeProofs(proofs);
          }
        };
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    blockAttestations(attestationSlot.plus(2));

    assertThat(sizesSeenWhileMerging).containsExactly(2);
  }

  @Test
  void shouldMergeTimelyProofsAheadOfALocalProposal() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner);
    final Bytes proof0 = randomProof(1);
    final Bytes proof1 = randomProof(1);
    pool.add(networkAggregate(attestationData(0), 0, proof0, 0));
    pool.add(networkAggregate(attestationData(1), 1, proof1, 0));
    final UInt64 blockSlot = attestationSlot.plus(2);

    pool.onSlot(blockSlot.minus(1));
    assertThat(prover.merges).isEmpty();
    asyncRunner.executeQueuedActions();

    assertThat(prover.merges).hasSize(1);
    assertThat(prover.merges.getFirst().stream().map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(proof0, proof1);

    // Block production finds nothing new to merge
    final PqBlockAttestations block = blockAttestations(blockSlot);

    assertThat(prover.merges).hasSize(1);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(attesterCount(block)).isEqualTo(2);
  }

  @Test
  void shouldNotMergeAheadOfPqOrWithoutTimelyAggregates() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner);
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    // Before the fork, and for a block whose timely slot has no aggregates
    pool.onSlot(UInt64.ZERO);
    pool.onSlot(attestationSlot.plus(2));
    asyncRunner.executeQueuedActions();

    assertThat(prover.merges).isEmpty();
  }

  @Test
  void shouldForgetMergedProofsOnceTheirSlotHasPassed() {
    final RecursiveProver prover = new RecursiveProver();
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));
    final UInt64 blockSlot = attestationSlot.plus(2);
    blockAttestations(blockSlot);

    pool.onSlot(blockSlot.plus(1));
    blockAttestations(blockSlot);

    assertThat(prover.merges).hasSize(2);
    assertThat(prover.merges.get(1)).hasSize(2);
  }

  @Test
  void shouldNotMergeAheadWithTheConcatenationStandIn() {
    final StubAsyncRunner asyncRunner = new StubAsyncRunner();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), StarkProver.CONCATENATED, asyncRunner);

    pool.onSlot(attestationSlot.plus(1));

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  private static int attesterCount(final PqBlockAttestations blockAttestations) {
    return blockAttestations.attestations().stream()
        .mapToInt(attestation -> attestation.getAggregationBits().getBitCount())
        .sum();
  }

  /**
   * A recursive prover, which merges proofs incrementally: records each merge's children and names
   * its proof by how many merges came before. A single proof merges to itself, as leanVM's does.
   */
  private static class RecursiveProver implements StarkProver {
    final List<List<ProvenStatements>> merges = new ArrayList<>();

    static Bytes mergedProof(final int merge) {
      return Bytes.ofUnsignedInt(merge);
    }

    @Override
    public Bytes proveAggregates(
        final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
      return Bytes.fromHexString("0x5afe");
    }

    @Override
    public long proofSizeOf(final int signatureCount) {
      return 0;
    }

    @Override
    public boolean isMergedProofSizeTheSumOfChildren() {
      return false;
    }

    @Override
    public boolean isIncrementallyMergeable() {
      return true;
    }

    @Override
    public Bytes mergeProofs(final List<ProvenStatements> proofs) {
      if (proofs.size() == 1) {
        return proofs.getFirst().proof();
      }
      merges.add(List.copyOf(proofs));
      return mergedProof(merges.size());
    }
  }

  @Test
  void shouldFilterAttestationsBySlotAndCommittee() {
    final AttestationData data = attestationData(0);
    pool.add(networkAggregate(data, 0, randomProof(1), 0));
    pool.add(networkAggregate(data, 1, randomProof(1), 0));

    assertThat(pool.getAttestations(Optional.of(attestationSlot), Optional.of(UInt64.ONE)))
        .hasSize(1);
    assertThat(pool.getAttestations(Optional.of(attestationSlot.plus(1)), Optional.empty()))
        .isEmpty();
  }

  @Test
  void shouldPruneOldAttestations() {
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));

    pool.onSlot(attestationSlot.plus(AggregatingAttestationPool.ATTESTATION_RETENTION_SLOTS));
    assertThat(pool.getSize()).isEqualTo(1);
    pool.onSlot(attestationSlot.plus(AggregatingAttestationPool.ATTESTATION_RETENTION_SLOTS + 1));
    assertThat(pool.getSize()).isZero();
  }

  @Test
  void forkRoutingPoolShouldKeepPqAttestationsInThePqPool() {
    final AggregatingAttestationPool blsPool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), StarkProver.CONCATENATED);
    final ForkRoutingAttestationPool routingPool =
        new ForkRoutingAttestationPool(spec, storageSystem.recentChainData(), blsPool, pool);
    final AttestationData data = attestationData(0);

    routingPool.add(networkAggregate(data, 0, randomProof(1), 0));

    assertThat(pool.getSize()).isEqualTo(1);
    assertThat(blsPool.getSize()).isZero();
    assertThat(routingPool.getSize()).isEqualTo(1);
    assertThat(routingPool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO)).isPresent();
  }

  private PqBlockAttestations blockAttestations(final UInt64 blockSlot) {
    final BeaconState state;
    try {
      state = spec.processSlots(head.getState(), blockSlot);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
    return pool.getPqAttestationsForBlock(state, new AttestationForkChecker(spec, state));
  }

  private AttestationData attestationData(final int committeeIndex) {
    return spec.getGenericAttestationData(
        attestationSlot, head.getState(), head, UInt64.valueOf(committeeIndex));
  }

  private IntList committee(final int committeeIndex) {
    return spec.getBeaconCommittee(
        head.getState(), attestationSlot, UInt64.valueOf(committeeIndex));
  }

  private ValidatableAttestation singleAttestation(
      final AttestationData data, final int committeeIndex, final int validatorIndex) {
    return singleAttestation(head.getState(), data, committeeIndex, validatorIndex);
  }

  private ValidatableAttestation singleAttestation(
      final BeaconState state,
      final AttestationData data,
      final int committeeIndex,
      final int validatorIndex) {
    final SingleAttestationPq single =
        new SingleAttestationPq(
            UInt64.valueOf(committeeIndex),
            UInt64.valueOf(validatorIndex),
            data,
            dataStructureUtil.randomXmssSignature().getBytes());
    final ValidatableAttestation attestation = ValidatableAttestation.from(spec, single);
    attestation.convertToAggregatedFormatFromSingleAttestation(
        ((AttestationUtilPq) spec.atSlot(data.getSlot()).getAttestationUtil())
            .convertSingleAttestationToAggregated(state, single));
    attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
    return attestation;
  }

  /** The concatenation prover, recording the statements it proves. */
  private static StarkProver recordingProver(
      final List<List<XmssAggregateStatement>> provenStatements) {
    return new StarkProver() {
      @Override
      public Bytes proveAggregates(
          final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
        provenStatements.add(statements);
        return StarkProver.CONCATENATED.proveAggregates(statements, signatures);
      }

      @Override
      public long proofSizeOf(final int signatureCount) {
        return StarkProver.CONCATENATED.proofSizeOf(signatureCount);
      }

      @Override
      public Bytes mergeProofs(final List<ProvenStatements> proofs) {
        return StarkProver.CONCATENATED.mergeProofs(proofs);
      }
    };
  }

  private static Bytes signatureOf(final ValidatableAttestation attestation) {
    return ((SingleAttestationPq) attestation.getUnconvertedAttestation()).getSignature();
  }

  private ValidatableAttestation networkAggregate(
      final AttestationData data,
      final int committeeIndex,
      final Bytes proof,
      final int... positions) {
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, aggregate(data, committeeIndex, positions));
    attestation.setAggregateProof(proof);
    attestation.saveCommitteeShufflingSeedAndCommitteesSize(head.getState());
    return attestation;
  }

  private AttestationPq aggregate(
      final AttestationData data, final int committeeIndex, final int... positions) {
    final AttestationPqSchema schema =
        SchemaDefinitionsPq.required(spec.atSlot(attestationSlot).getSchemaDefinitions())
            .getPqAttestationSchemas()
            .getAttestationSchema();
    return schema.create(
        schema.getAggregationBitsSchema().ofBits(committee(committeeIndex).size(), positions),
        data,
        schema.getCommitteeBitsSchema().orElseThrow().ofBits(committeeIndex));
  }

  private Bytes randomProof(final int signatures) {
    return Bytes.concatenate(
        IntStream.range(0, signatures)
            .mapToObj(__ -> dataStructureUtil.randomBytes(XMSS_SIGNATURE_SIZE))
            .toArray(Bytes[]::new));
  }
}
