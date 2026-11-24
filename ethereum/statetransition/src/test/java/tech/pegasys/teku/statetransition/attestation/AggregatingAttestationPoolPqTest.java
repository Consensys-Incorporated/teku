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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE;

import com.fasterxml.jackson.databind.JsonNode;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
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
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolPq.MeasuredProof;
import tech.pegasys.teku.statetransition.pq.timing.PqTiming;
import tech.pegasys.teku.statetransition.pq.timing.PqTimingEvents;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AggregatingAttestationPoolPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(64, spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);

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
        pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO).getImmediately();

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
        recordingPool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO).getImmediately();

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
        pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO).getImmediately();

    assertThat(aggregate.orElseThrow().proof()).isEqualTo(proof);
    assertThat(aggregate.get().attestation().getAggregationBits().getAllSetBits().toIntArray())
        .containsExactly(1, 2);
  }

  @Test
  void shouldProveALocalAggregatorsCommitteeAtTheCollectionCutoff() {
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(recordingProver(provenStatements), asyncRunner);
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    final ValidatableAttestation first = singleAttestation(data, 0, committee.getInt(0));
    final ValidatableAttestation second = singleAttestation(data, 0, committee.getInt(1));
    pool.add(first);
    pool.add(second);
    pool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);

    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, cutoffMillis() - 1);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(provenStatements).isEmpty();

    advanceTimeTo(attestationSlot, cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(provenStatements).hasSize(1);

    // An attestation after the cutoff doesn't hold the aggregate up to prove it again
    pool.add(singleAttestation(data, 0, committee.getInt(2)));
    final SafeFuture<Optional<PqAggregate>> aggregate =
        pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO);

    assertThat(aggregate).isCompleted();
    assertThat(provenStatements).hasSize(1);
    final PqAggregate result = aggregate.getImmediately().orElseThrow();
    assertThat(result.attestation().getAggregationBits().getAllSetBits().toIntArray())
        .containsExactly(0, 1);
    assertThat(result.proof())
        .isEqualTo(Bytes.concatenate(signatureOf(first), signatureOf(second)));
  }

  @Test
  void shouldWaitForAnAggregateBeingProvenAtTheCollectionCutoff() {
    final List<SafeFuture<Optional<PqAggregate>>> askedWhileProving = new ArrayList<>();
    final AttestationData data = attestationData(0);
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final StarkProver recording = recordingProver(provenStatements);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        provingPool(
            new DelegatingProver(recording) {
              @Override
              public Bytes proveAggregates(
                  final List<XmssAggregateStatement> statements,
                  final List<List<Bytes>> signatures) {
                if (askedWhileProving.isEmpty()) {
                  askedWhileProving.add(
                      pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO));
                }
                return super.proveAggregates(statements, signatures);
              }
            },
            asyncRunner);
    pool.add(singleAttestation(data, 0, committee(0).getInt(0)));
    pool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);

    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();

    // Asked while it was being proven, it waited for that proof rather than proving again
    assertThat(askedWhileProving).hasSize(1);
    assertThat(provenStatements).hasSize(1);
    assertThat(askedWhileProving.getFirst()).isCompletedWithValueMatching(Optional::isPresent);
  }

  @Test
  void shouldPreferANetworkAggregateWithAsManyAttestersToTheOneProvenAtTheCutoff() {
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(recordingProver(provenStatements), asyncRunner);
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    pool.add(singleAttestation(data, 0, committee.getInt(0)));
    pool.add(singleAttestation(data, 0, committee.getInt(1)));
    pool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);
    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();

    final Bytes networkProof = randomProof(2);
    pool.add(networkAggregate(data, 0, networkProof, 2, 3));

    assertThat(pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO).getImmediately())
        .hasValueSatisfying(aggregate -> assertThat(aggregate.proof()).isEqualTo(networkProof));
    assertThat(provenStatements).hasSize(1);
  }

  @Test
  void shouldProveOnDemandWhenProvingAtTheCutoffFailed() {
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final StarkProver recording = recordingProver(provenStatements);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        provingPool(
            new DelegatingProver(recording) {
              private boolean failed;

              @Override
              public Bytes proveAggregates(
                  final List<XmssAggregateStatement> statements,
                  final List<List<Bytes>> signatures) {
                if (!failed) {
                  failed = true;
                  throw new IllegalStateException("leanVM unavailable");
                }
                return super.proveAggregates(statements, signatures);
              }
            },
            asyncRunner);
    final AttestationData data = attestationData(0);
    pool.add(singleAttestation(data, 0, committee(0).getInt(0)));
    pool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);
    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(provenStatements).isEmpty();

    assertThat(pool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO))
        .isCompletedWithValueMatching(Optional::isPresent);
    assertThat(provenStatements).hasSize(1);
  }

  @Test
  void shouldProveAtTheCutoffOnlyForLocalAggregatorsAtTheSlot() {
    final List<List<XmssAggregateStatement>> provenStatements = new ArrayList<>();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(recordingProver(provenStatements), asyncRunner);
    pool.add(singleAttestation(attestationData(0), 0, committee(0).getInt(0)));
    pool.add(singleAttestation(attestationData(1), 1, committee(1).getInt(0)));
    // Committee 1 is aggregated locally, but a slot later
    pool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);
    pool.onLocalAggregationDuty(attestationSlot.plus(1), UInt64.ONE);

    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(provenStatements).hasSize(1);
    assertThat(provenStatements.getFirst().getFirst().pubkeys()).hasSize(1);
    // No local aggregator at the next slot has attestations to prove
    advanceTimeTo(attestationSlot.plus(1), 0);
    pool.onSlot(attestationSlot.plus(1));
    advanceTimeTo(attestationSlot.plus(1), cutoffMillis());
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(provenStatements).hasSize(1);
    // Nor any at the slot after
    pool.onSlot(attestationSlot.plus(2));
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  private AggregatingAttestationPoolPq provingPool(
      final StarkProver prover, final StubAsyncRunner asyncRunner) {
    return new AggregatingAttestationPoolPq(
        spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);
  }

  private long cutoffMillis() {
    return spec.getSlotDurationMillis(attestationSlot)
        * AggregatingAttestationPoolPq.AGGREGATION_CUTOFF_BPS
        / 10_000L;
  }

  /** A prover that defers to another. */
  private static class DelegatingProver implements StarkProver {
    private final StarkProver delegate;

    DelegatingProver(final StarkProver delegate) {
      this.delegate = delegate;
    }

    @Override
    public Bytes proveAggregates(
        final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
      return delegate.proveAggregates(statements, signatures);
    }

    @Override
    public long proofSizeOf(final int signatureCount) {
      return delegate.proofSizeOf(signatureCount);
    }

    @Override
    public Bytes mergeProofs(final List<ProvenStatements> proofs) {
      return delegate.mergeProofs(proofs);
    }
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
            spec, storageSystem.recentChainData(), prover, Optional.empty(), timeProvider, 1);
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
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);
    final Bytes proof0 = randomProof(1);
    final Bytes proof1 = randomProof(1);
    pool.add(networkAggregate(attestationData(0), 0, proof0, 0));
    pool.add(networkAggregate(attestationData(1), 1, proof1, 0));
    final UInt64 blockSlot = attestationSlot.plus(2);

    advanceTimeTo(blockSlot.minus(1), 0);
    pool.onSlot(blockSlot.minus(1));
    assertThat(prover.merges).isEmpty();
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(prover.merges).hasSize(1);
    assertThat(prover.merges.getFirst().stream().map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(proof0, proof1);

    // Nothing more to merge once the aggregates are due
    advanceTimeTo(blockSlot.minus(1), spec.getAggregateDueMillis(blockSlot.minus(1)));
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(prover.merges).hasSize(1);

    // Block production finds nothing new to merge
    final PqBlockAttestations block = blockAttestations(blockSlot);

    assertThat(prover.merges).hasSize(1);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(attesterCount(block)).isEqualTo(2);
  }

  @Test
  void shouldTakeTheProofMergedAheadAsItIsAndLeaveLaterAggregatesToLaterBlocks() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);
    final AttestationData data0 = attestationData(0);
    pool.add(networkAggregate(data0, 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));
    final UInt64 blockSlot = attestationSlot.plus(2);
    advanceTimeTo(blockSlot.minus(1), 0);
    pool.onSlot(blockSlot.minus(1));
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(prover.merges).hasSize(1);

    // After the merge ahead: another committee, and a larger aggregate of one merged
    pool.add(networkAggregate(attestationData(2), 2, randomProof(1), 0));
    pool.add(networkAggregate(data0, 0, randomProof(2), 0, 1));
    final PqBlockAttestations block = blockAttestations(blockSlot);

    // The block takes the merged proof and its attestations, without merging again
    assertThat(prover.merges).hasSize(1);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(attesterCount(block)).isEqualTo(2);
  }

  @Test
  void shouldTakeTheFirstCommitteeOnItsOwnProofWhenNothingWasMergedAhead() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);
    final Bytes proof0 = randomProof(2);
    pool.add(networkAggregate(attestationData(0), 0, proof0, 0, 1));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    final PqBlockAttestations block = blockAttestations(attestationSlot.plus(2));

    // No merge: the committee with the most attesters, as its aggregate proved it
    assertThat(prover.merges).isEmpty();
    assertThat(block.proof()).isEqualTo(proof0);
    assertThat(attesterCount(block)).isEqualTo(2);
  }

  @Test
  void shouldMergeEveryCommitteeTheBlockCanIncludeOnceTheAggregatesAreDue() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);
    final UInt64 timelySlot = attestationSlot.plus(1);
    final UInt64 blockSlot = timelySlot.plus(2);
    // Older aggregates, which the block can include but which aren't timely
    final Bytes older0 = randomProof(1);
    final Bytes older1 = randomProof(1);
    pool.add(networkAggregate(attestationData(0), 0, older0, 0));
    pool.add(networkAggregate(attestationData(1), 1, older1, 0));
    final Bytes timely0 = randomProof(1);
    final Bytes timely1 = randomProof(1);
    pool.add(networkAggregate(attestationData(timelySlot, 0), 0, timely0, 0));
    pool.add(networkAggregate(attestationData(timelySlot, 1), 1, timely1, 0));
    final UInt64 slot = blockSlot.minus(1);

    // At the start of the slot before, the timely committees only: the block in between may
    // include the others
    advanceTimeTo(slot, 0);
    pool.onSlot(slot);
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(prover.merges).hasSize(1);
    assertThat(prover.merges.getFirst().stream().map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(timely0, timely1);

    // Not while this slot's aggregates are proven
    advanceTimeTo(slot, spec.getAggregateDueMillis(slot) - 1);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(prover.merges).hasSize(1);

    // Then the rest, into the first merge
    advanceTimeTo(slot, spec.getAggregateDueMillis(slot));
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(prover.merges).hasSize(2);
    final List<ProvenStatements> children = prover.merges.get(1);
    assertThat(children.getFirst().proof()).isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(children.stream().skip(1).map(ProvenStatements::proof))
        .containsExactlyInAnyOrder(older0, older1);

    // Block production finds nothing new to merge
    final PqBlockAttestations block = blockAttestations(blockSlot);

    assertThat(prover.merges).hasSize(2);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(2));
    assertThat(attesterCount(block)).isEqualTo(4);
  }

  @Test
  void shouldMergeAheadPassAfterPassWhileCommitteesAreLeftOut() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    // An earlier merge and one more proof per merge
    pool =
        new AggregatingAttestationPoolPq(
            spec,
            storageSystem.recentChainData(),
            prover,
            Optional.of(asyncRunner),
            timeProvider,
            2);
    final UInt64 timelySlot = attestationSlot.plus(1);
    final UInt64 blockSlot = timelySlot.plus(2);
    final Bytes timely = randomProof(1);
    final Bytes older0 = randomProof(1);
    final Bytes older1 = randomProof(1);
    pool.add(networkAggregate(attestationData(timelySlot, 0), 0, timely, 0));
    pool.add(networkAggregate(attestationData(0), 0, older0, 0));
    pool.add(networkAggregate(attestationData(1), 1, older1, 0));
    final UInt64 slot = blockSlot.minus(1);

    advanceTimeTo(slot, spec.getAggregateDueMillis(slot));
    pool.onSlot(slot);
    asyncRunner.executeDueActionsRepeatedly();

    // The timely proof alone needs no merge; then one proof per pass, each into the last
    assertThat(prover.merges).hasSize(2);
    final List<Bytes> first = prover.merges.get(0).stream().map(ProvenStatements::proof).toList();
    final List<Bytes> second = prover.merges.get(1).stream().map(ProvenStatements::proof).toList();
    assertThat(first).hasSize(2).first().isEqualTo(timely);
    assertThat(second).hasSize(2).first().isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(List.of(first.get(1), second.get(1))).containsExactlyInAnyOrder(older0, older1);
    final PqBlockAttestations block = blockAttestations(blockSlot);
    assertThat(prover.merges).hasSize(2);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(2));
    assertThat(attesterCount(block)).isEqualTo(3);
  }

  @Test
  void shouldNotStartAMergeAheadPassThatWouldRunIntoTheBlockSlot() {
    final UInt64 blockSlot = attestationSlot.plus(3);
    final UInt64 slot = blockSlot.minus(1);
    final long aggregateDue = spec.getAggregateDueMillis(slot);
    // Each merge takes most of what's left of the slot
    final long mergeMillis = (spec.getSlotDurationMillis(slot) - aggregateDue) * 2 / 3;
    final RecursiveProver prover =
        new RecursiveProver() {
          @Override
          public Bytes mergeProofs(final List<ProvenStatements> proofs) {
            timeProvider.advanceTimeByMillis(mergeMillis);
            return super.mergeProofs(proofs);
          }
        };
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec,
            storageSystem.recentChainData(),
            prover,
            Optional.of(asyncRunner),
            timeProvider,
            2);
    // A timely proof and two older ones: two merges after the timely one
    pool.add(networkAggregate(attestationData(attestationSlot.plus(1), 0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    advanceTimeTo(slot, 0);
    pool.onSlot(slot);
    asyncRunner.executeDueActionsRepeatedly();
    // The timely committee alone needs no merge
    assertThat(prover.merges).isEmpty();
    advanceTimeTo(slot, aggregateDue);
    asyncRunner.executeDueActionsRepeatedly();

    // One pass, which leaves a committee out, and no time for another
    assertThat(prover.merges).hasSize(1);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldNotProveSingleAttestationsForABlockWhenMergingAhead() {
    final List<Integer> proved = new ArrayList<>();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(countingProver(proved, 0), asyncRunner);
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    // A network aggregate of one attester, and single attestations of more, which came after
    final Bytes networkProof = randomProof(1);
    pool.add(networkAggregate(data, 0, networkProof, 0));
    pool.add(singleAttestation(data, 0, committee.getInt(0)));
    pool.add(singleAttestation(data, 0, committee.getInt(1)));

    final PqBlockAttestations block = blockAttestations(attestationSlot.plus(2));

    // The network aggregate, rather than proving the single attestations now
    assertThat(proved).isEmpty();
    assertThat(attesterCount(block)).isEqualTo(1);
    assertThat(block.proof()).isEqualTo(networkProof);
  }

  @Test
  void shouldKeepSingleAttestationsProvenAheadForTheBlock() {
    final List<Integer> proved = new ArrayList<>();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(countingProver(proved, 0), asyncRunner);
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    pool.add(singleAttestation(data, 0, committee.getInt(0)));
    pool.add(singleAttestation(data, 0, committee.getInt(1)));
    final UInt64 blockSlot = attestationSlot.plus(2);

    advanceTimeTo(blockSlot.minus(1), 0);
    pool.onSlot(blockSlot.minus(1));
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(proved).containsExactly(2);

    final PqBlockAttestations block = blockAttestations(blockSlot);

    // Block production uses the proof made ahead
    assertThat(proved).containsExactly(2);
    assertThat(attesterCount(block)).isEqualTo(2);
    assertThat(block.proof()).isEqualTo(RecursiveProver.SINGLES_PROOF);
    // which the pool keeps as an aggregate, beside the single attestations
    assertThat(pool.getAttestations(Optional.of(attestationSlot), Optional.of(UInt64.ZERO)))
        .hasSize(2);
  }

  @Test
  void shouldStopProvingAheadAtTheDeadlineAndProveTheRestInTheNextPass() {
    // Each proof takes until the first pass's deadline
    final long proveMillis =
        cutoffMillis() - AggregatingAttestationPoolPq.AHEAD_PROVING_MARGIN_MILLIS;
    final UInt64 blockSlot = attestationSlot.plus(2);
    final UInt64 slot = blockSlot.minus(1);
    final long aggregateDue = spec.getAggregateDueMillis(slot);
    // The second pass has time for one more proof
    assertThat(aggregateDue + proveMillis)
        .isLessThan(
            spec.getSlotDurationMillis(slot)
                - AggregatingAttestationPoolPq.AHEAD_PROVING_MARGIN_MILLIS);
    final List<Integer> proved = new ArrayList<>();
    final RecursiveProver prover = countingProver(proved, proveMillis);
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool = provingPool(prover, asyncRunner);
    pool.add(singleAttestation(attestationData(0), 0, committee(0).getInt(0)));
    pool.add(singleAttestation(attestationData(1), 1, committee(1).getInt(0)));

    // The first pass proves one committee, then is out of time
    advanceTimeTo(slot, 0);
    pool.onSlot(slot);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(proved).containsExactly(1);
    assertThat(prover.merges).isEmpty();

    // The next proves the other, and merges it into the first
    advanceTimeTo(slot, aggregateDue);
    asyncRunner.executeDueActionsRepeatedly();
    assertThat(proved).containsExactly(1, 1);
    assertThat(prover.merges).hasSize(1);

    final PqBlockAttestations block = blockAttestations(blockSlot);
    assertThat(proved).hasSize(2);
    assertThat(prover.merges).hasSize(1);
    assertThat(attesterCount(block)).isEqualTo(2);
    assertThat(block.proof()).isEqualTo(RecursiveProver.mergedProof(1));
  }

  /** A recursive prover recording each committee proof's signature count, taking proveMillis. */
  private RecursiveProver countingProver(final List<Integer> proved, final long proveMillis) {
    return new RecursiveProver() {
      @Override
      public Bytes proveAggregates(
          final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
        proved.add(signatures.getFirst().size());
        timeProvider.advanceTimeByMillis(proveMillis);
        return super.proveAggregates(statements, signatures);
      }
    };
  }

  @Test
  void shouldNotMergeAheadOfPqOrWithoutAggregates() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), prover, asyncRunner, timeProvider);

    // Before the fork
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));
    pool.onSlot(UInt64.ZERO);
    asyncRunner.executeUntilDone();
    assertThat(prover.merges).isEmpty();

    // Nor for a block that can't include them yet
    advanceTimeTo(attestationSlot, 0);
    pool.onSlot(attestationSlot);
    advanceTimeTo(attestationSlot, spec.getSlotDurationMillis(attestationSlot));
    asyncRunner.executeDueActionsRepeatedly();

    assertThat(prover.merges).isEmpty();
  }

  @Test
  void shouldNotMergeAheadWithoutALocalProposer() {
    final RecursiveProver prover = new RecursiveProver();
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final RecentChainData recentChainData = spy(storageSystem.recentChainData());
    doReturn(SafeFuture.completedFuture(false))
        .when(recentChainData)
        .isBlockProposerConnected(any());
    pool =
        new AggregatingAttestationPoolPq(spec, recentChainData, prover, asyncRunner, timeProvider);
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    advanceTimeTo(attestationSlot.plus(1), 0);
    pool.onSlot(attestationSlot.plus(1));
    advanceTimeTo(attestationSlot.plus(2), 0);
    asyncRunner.executeDueActionsRepeatedly();

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
            spec,
            storageSystem.recentChainData(),
            StarkProver.CONCATENATED,
            asyncRunner,
            timeProvider);

    pool.onSlot(attestationSlot.plus(1));

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldRecordProvingAndMergingTimings(@TempDir final Path timingRoot) throws Exception {
    final RecursiveProver prover = new RecursiveProver();
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    PqTimingEvents.start(timingRoot, false);
    try {
      final AttestationData data0 = attestationData(0);
      final IntList committee0 = committee(0);
      pool.add(singleAttestation(data0, 0, committee0.getInt(0)));
      pool.add(singleAttestation(data0, 0, committee0.getInt(1)));
      pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));
      final UInt64 blockSlot = attestationSlot.plus(2);

      // An aggregator's proof of the single attestations
      assertThat(pool.createPqAggregateFor(data0.hashTreeRoot(), UInt64.ZERO)).isCompleted();
      // Proves committee 0 again and merges it with committee 1's aggregate
      blockAttestations(blockSlot);
      // Merges a better aggregate of committee 1 into the first merge
      pool.add(networkAggregate(attestationData(1), 1, randomProof(2), 0, 1));
      blockAttestations(blockSlot);
    } finally {
      PqTiming.stop();
    }

    final List<JsonNode> events = PqTimingEvents.stopAndRead(timingRoot);
    assertThat(events)
        .extracting(
            event ->
                event.get("kind").asText()
                    + ":"
                    + event.get("work").asText()
                    + ":"
                    + event.get("round").asInt())
        .containsExactly(
            "proof_started:committee:1",
            "proof_finished:committee:1",
            "proof_started:fallback:1",
            "proof_finished:fallback:1",
            "proof_started:initial:1",
            "proof_finished:initial:1",
            "proposal_snapshot:initial:1",
            "proof_started:improvement:2",
            "proof_finished:improvement:2",
            "proposal_snapshot:improvement:2");
    final JsonNode committeeProof = events.get(1);
    assertThat(committeeProof.get("slot").asLong()).isEqualTo(attestationSlot.longValue());
    assertThat(committeeProof.get("committee").asInt()).isZero();
    assertThat(committeeProof.get("signatures").asInt()).isEqualTo(2);
    assertThat(committeeProof.get("committee_size").asInt()).isEqualTo(committee(0).size());
    final JsonNode firstMerge = events.get(5);
    assertThat(firstMerge.get("slot").asLong()).isEqualTo(attestationSlot.plus(2).longValue());
    assertThat(firstMerge.get("reference_slot").asLong()).isEqualTo(attestationSlot.longValue());
    assertThat(firstMerge.get("input_proof_count").asInt()).isEqualTo(2);
    assertThat(firstMerge.get("claims").asInt()).isEqualTo(3);
    assertThat(firstMerge.get("proof_id").asText())
        .isEqualTo(PqTiming.proofId(RecursiveProver.mergedProof(1)));
    final JsonNode lastSnapshot = events.get(9);
    assertThat(lastSnapshot.get("outcome").asText()).isEqualTo("candidate");
    assertThat(lastSnapshot.get("records").asInt()).isEqualTo(2);
    assertThat(lastSnapshot.get("claims").asInt()).isEqualTo(4);
    assertThat(lastSnapshot.get("proof_id").asText())
        .isEqualTo(PqTiming.proofId(RecursiveProver.mergedProof(2)));
  }

  @Test
  void shouldRecordAProposalWithNothingToInclude(@TempDir final Path timingRoot) throws Exception {
    PqTimingEvents.start(timingRoot, false);

    blockAttestations(attestationSlot.plus(2));

    assertThat(PqTimingEvents.stopAndRead(timingRoot))
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.get("kind").asText()).isEqualTo("proposal_snapshot");
              assertThat(snapshot.get("outcome").asText()).isEqualTo("no_candidate");
            });
  }

  @Test
  void shouldProveACommitteesSingleAttestationsForMeasurement() {
    final List<List<XmssAggregateStatement>> proven = new ArrayList<>();
    pool =
        new AggregatingAttestationPoolPq(
            spec, storageSystem.recentChainData(), recordingProver(proven));
    final AttestationData data = attestationData(0);
    final IntList committee = committee(0);
    final ValidatableAttestation first = singleAttestation(data, 0, committee.getInt(0));
    final ValidatableAttestation second = singleAttestation(data, 0, committee.getInt(1));
    pool.add(first);
    pool.add(second);
    // A network aggregate with more attesters needs no proving, so isn't measured
    pool.add(networkAggregate(data, 0, randomProof(3), 0, 1, 2));

    final Optional<MeasuredProof> measured = pool.proveCommitteeForMeasurement(attestationSlot, 0);

    assertThat(measured).isPresent();
    assertThat(measured.get().proof())
        .isEqualTo(Bytes.concatenate(signatureOf(first), signatureOf(second)));
    assertThat(measured.get().statements()).isEqualTo(proven.getFirst());
    assertThat(measured.get().claims()).isEqualTo(2);
    assertThat(measured.get().records()).isEqualTo(1);
    assertThat(measured.get().round()).isZero();
    assertThat(pool.proveCommitteeForMeasurement(attestationSlot, 1)).isEmpty();
    assertThat(pool.proveCommitteeForMeasurement(attestationSlot.plus(1), 0)).isEmpty();
  }

  @Test
  void shouldMergeAheadAndForABlockForMeasurement() throws Exception {
    final RecursiveProver prover = new RecursiveProver();
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    final UInt64 blockSlot = attestationSlot.plus(2);
    assertThat(pool.mergeAheadForMeasurement(blockSlot, head.getState())).isEmpty();
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(1), 1, randomProof(1), 0));

    final Optional<MeasuredProof> ahead = pool.mergeAheadForMeasurement(blockSlot, head.getState());

    assertThat(ahead).isPresent();
    assertThat(ahead.get().proof()).isEqualTo(RecursiveProver.mergedProof(1));
    assertThat(ahead.get().statements()).hasSize(2);
    assertThat(ahead.get().round()).isEqualTo(1);
    assertThat(ahead.get().records()).isEqualTo(2);

    pool.add(networkAggregate(attestationData(1), 1, randomProof(2), 0, 1));
    final Optional<MeasuredProof> block =
        pool.mergeForBlockMeasurement(spec.processSlots(head.getState(), blockSlot));

    assertThat(block).isPresent();
    assertThat(block.get().proof()).isEqualTo(RecursiveProver.mergedProof(2));
    assertThat(block.get().round()).isEqualTo(2);
    assertThat(block.get().claims()).isEqualTo(3);
  }

  @Test
  void shouldMergeEveryCommitteeAheadForMeasurement() {
    final RecursiveProver prover = new RecursiveProver();
    pool = new AggregatingAttestationPoolPq(spec, storageSystem.recentChainData(), prover);
    final UInt64 timelySlot = attestationSlot.plus(1);
    final UInt64 blockSlot = timelySlot.plus(2);
    pool.add(networkAggregate(attestationData(timelySlot, 0), 0, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(timelySlot, 1), 1, randomProof(1), 0));
    pool.add(networkAggregate(attestationData(0), 0, randomProof(1), 0));
    assertThat(pool.mergeAheadForMeasurement(blockSlot, head.getState())).isPresent();

    // The older committee goes into the timely committees' merge
    final Optional<MeasuredProof> every =
        pool.mergeEveryCommitteeAheadForMeasurement(blockSlot, head.getState());

    assertThat(every).isPresent();
    assertThat(every.get().proof()).isEqualTo(RecursiveProver.mergedProof(2));
    assertThat(every.get().round()).isEqualTo(2);
    assertThat(every.get().claims()).isEqualTo(3);
    // Then there is nothing new
    assertThat(pool.mergeEveryCommitteeAheadForMeasurement(blockSlot, head.getState())).isEmpty();
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
    static final Bytes SINGLES_PROOF = Bytes.fromHexString("0x5afe");

    final List<List<ProvenStatements>> merges = new ArrayList<>();

    static Bytes mergedProof(final int merge) {
      return Bytes.ofUnsignedInt(merge);
    }

    @Override
    public Bytes proveAggregates(
        final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
      return SINGLES_PROOF;
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
    assertThat(routingPool.createPqAggregateFor(data.hashTreeRoot(), UInt64.ZERO))
        .isCompletedWithValueMatching(Optional::isPresent);

    // Local aggregation duties at PQ slots go to the PQ pool, which proves them at the cutoff
    final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    final AggregatingAttestationPoolPq pqPool =
        new AggregatingAttestationPoolPq(
            spec,
            storageSystem.recentChainData(),
            StarkProver.CONCATENATED,
            asyncRunner,
            timeProvider);
    final ForkRoutingAttestationPool routingToProvingPool =
        new ForkRoutingAttestationPool(spec, storageSystem.recentChainData(), blsPool, pqPool);
    routingToProvingPool.onLocalAggregationDuty(attestationSlot, UInt64.ZERO);
    pqPool.onSlot(attestationSlot);
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
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
    return attestationData(attestationSlot, committeeIndex);
  }

  private AttestationData attestationData(final UInt64 slot, final int committeeIndex) {
    return spec.getGenericAttestationData(
        slot, head.getState(), head, UInt64.valueOf(committeeIndex));
  }

  private void advanceTimeTo(final UInt64 slot, final long millisIntoSlot) {
    final long target =
        spec.computeTimeMillisAtSlot(slot, storageSystem.recentChainData().getGenesisTimeMillis())
                .longValue()
            + millisIntoSlot;
    timeProvider.advanceTimeByMillis(target - timeProvider.getTimeInMillis().longValue());
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
    final int committeeSize =
        spec.getBeaconCommittee(head.getState(), data.getSlot(), UInt64.valueOf(committeeIndex))
            .size();
    return schema.create(
        schema.getAggregationBitsSchema().ofBits(committeeSize, positions),
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
