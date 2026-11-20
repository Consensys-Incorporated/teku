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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_AGGREGATE_AND_PROOF;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.generator.PqTestKeys;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.xmss.XmssConstants;

/** Gossip validation of PQ aggregates (validate_beacon_aggregate_and_proof_gossip). */
class AggregateAttestationValidatorPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;
  private static final UInt64 COMMITTEE_INDEX = UInt64.ZERO;

  /** Accepts a stub signature that carries the expected leaf. */
  private static final XmssSignatureVerifier LEAF_CHECKING_VERIFIER =
      (publicKey, leafIndex, message, signature) ->
          XmssSignatureVerifier.getLeafIndex(signature).equals(leafIndex);

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private StorageSystem storageSystem;
  private GossipValidationHelper gossipValidationHelper;
  private AggregateAttestationValidatorPq validator;
  private StateAndBlockSummary head;

  @BeforeEach
  void setUp() {
    // The aggregate proof is the spec's concatenation stand-in, which accepts every proof while
    // XMSS verification is disabled, as in the spec's own tests
    XmssConstants.disableXmssVerification();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem
        .chainUpdater()
        .updateBestBlock(
            storageSystem
                .chainUpdater()
                .advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH).plus(1)));
    head = storageSystem.getChainHead();
    storageSystem.chainUpdater().setCurrentSlot(head.getSlot().plus(1));
    gossipValidationHelper =
        new GossipValidationHelper(
            spec,
            storageSystem.recentChainData(),
            storageSystem.getMetricsSystem(),
            LEAF_CHECKING_VERIFIER);
    final AttestationValidator attestationValidator =
        new AttestationValidator(
            spec,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP),
            gossipValidationHelper,
            Map.of());
    validator =
        new AggregateAttestationValidatorPq(spec, attestationValidator, gossipValidationHelper);
  }

  @AfterEach
  void tearDown() {
    XmssConstants.enableXmssVerification();
  }

  @Test
  void acceptsAnAggregateFromAnAggregator() {
    final SignedAggregateAndProofPq signed = signedAggregate(aggregator(), aggregatorLeaf());
    final ValidatableAttestation attestation = validatable(signed);

    assertThat(safeJoin(validator.validate(signed, attestation)).isAccept()).isTrue();
    // Fork choice and the pool take it as verified, with its proof
    assertThat(attestation.isValidIndexedAttestation()).isTrue();
    assertThat(attestation.getAggregateProof()).contains(signed.getMessage().getProof());
  }

  @Test
  void rejectsAValidatorOutsideTheAggregationCommittee() {
    final IntList aggregators = aggregationCommittee();
    final int outsider =
        IntStream.range(0, head.getState().getValidators().size())
            .filter(index -> !aggregators.contains(index))
            .findFirst()
            .orElseThrow();
    final SignedAggregateAndProofPq signed = signedAggregate(outsider, aggregatorLeaf());

    assertThat(safeJoin(validator.validate(signed, validatable(signed))).isReject()).isTrue();
  }

  @Test
  void rejectsAnAggregatorSignatureAtAnotherLeaf() {
    final SignedAggregateAndProofPq signed =
        signedAggregate(aggregator(), aggregatorLeaf().plus(1));

    assertThat(safeJoin(validator.validate(signed, validatable(signed))).isReject()).isTrue();
  }

  @Test
  void ignoresASecondAggregateFromTheSameAggregatorAndEpoch() {
    final SignedAggregateAndProofPq signed = signedAggregate(aggregator(), aggregatorLeaf());
    assertThat(safeJoin(validator.validate(signed, validatable(signed))).isAccept()).isTrue();

    assertThat(safeJoin(validator.validate(signed, validatable(signed))).isIgnore()).isTrue();
  }

  private SignedAggregateAndProofPq signedAggregate(final int aggregator, final UInt64 leaf) {
    final PqAttestationSchemas schemas =
        SchemaDefinitionsPq.required(spec.atSlot(head.getSlot()).getSchemaDefinitions())
            .getPqAttestationSchemas();
    final AttestationPqSchema attestationSchema = schemas.getAttestationSchema();
    final int committeeSize =
        spec.getBeaconCommittee(head.getState(), head.getSlot(), COMMITTEE_INDEX).size();
    final AttestationData data =
        spec.getGenericAttestationData(head.getSlot(), head.getState(), head, COMMITTEE_INDEX);
    final AttestationPq aggregate =
        attestationSchema.create(
            attestationSchema
                .getAggregationBitsSchema()
                .ofBits(committeeSize, IntStream.range(0, committeeSize).toArray()),
            data,
            attestationSchema
                .getCommitteeBitsSchema()
                .orElseThrow()
                .ofBits(COMMITTEE_INDEX.intValue()));
    return schemas
        .getSignedAggregateAndProofSchema()
        .create(
            schemas
                .getAggregateAndProofSchema()
                .create(UInt64.valueOf(aggregator), aggregate, Bytes.EMPTY),
            PqTestKeys.stubSignature(leaf).getBytes());
  }

  private ValidatableAttestation validatable(final SignedAggregateAndProofPq signed) {
    return ValidatableAttestation.from(spec, signed.getMessage().getAggregate());
  }

  private IntList aggregationCommittee() {
    return ((BeaconStateAccessorsPq) spec.atSlot(head.getSlot()).beaconStateAccessors())
        .getAggregationCommittee(head.getState(), head.getSlot(), COMMITTEE_INDEX);
  }

  private int aggregator() {
    return aggregationCommittee().getInt(0);
  }

  private UInt64 aggregatorLeaf() {
    return gossipValidationHelper.computeXmssLeafIndex(
        head.getSlot(), LEAF_OFFSET_AGGREGATE_AND_PROOF);
  }
}
