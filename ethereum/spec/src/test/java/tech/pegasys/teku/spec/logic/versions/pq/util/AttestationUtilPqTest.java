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

package tech.pegasys.teku.spec.logic.versions.pq.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.xmss.XmssConstants;

class AttestationUtilPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion specVersion = spec.forMilestone(SpecMilestone.PQ);
  private final SpecConfigPq config = SpecConfigPq.required(specVersion.getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(specVersion.getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);

  private final List<List<XmssAggregateStatement>> statements = new ArrayList<>();
  private final List<Bytes> proofs = new ArrayList<>();
  private boolean starkResult = true;
  private final StarkProofVerifier starkVerifier =
      (proven, proof) -> {
        statements.add(proven);
        proofs.add(proof);
        return starkResult;
      };
  // Accepts a stub signature carrying the expected leaf, under the expected attester's key
  private Bytes32 expectedSignerKey;
  private final XmssSignatureVerifier xmssVerifier =
      (publicKey, leafIndex, message, signature) ->
          XmssSignatureVerifier.getLeafIndex(signature).equals(leafIndex)
              && publicKey.equals(expectedSignerKey);
  private final AttestationUtilPq attestationUtil =
      new AttestationUtilPq(
          config, schemaDefinitions, accessors, miscHelpers, starkVerifier, xmssVerifier);

  private final IndexedAttestationPqSchema indexedSchema =
      schemaDefinitions.getPqAttestationSchemas().getIndexedAttestationSchema();
  private final AttestationData data =
      new AttestationData(
          UInt64.valueOf(9),
          UInt64.ZERO,
          dataStructureUtil.randomBytes32(),
          new Checkpoint(UInt64.ZERO, dataStructureUtil.randomBytes32()),
          new Checkpoint(UInt64.ONE, dataStructureUtil.randomBytes32()));

  @Test
  void verifiesTheProofOfOneStatementOverTheAttesters() throws Exception {
    final BeaconStatePq state = fixtureGenesis();
    final Bytes proof = Bytes.fromHexString("0xabcdef");

    assertThat(
            attestationUtil
                .isValidIndexedAttestation(
                    state.getFork(), state, indexed(proof, 3, 7, 12), BLSSignatureVerifier.NOOP)
                .isSuccessful())
        .isTrue();

    final Bytes32 domain =
        accessors.getDomain(
            Domain.BEACON_ATTESTER, UInt64.ONE, state.getFork(), state.getGenesisValidatorsRoot());
    assertThat(statements)
        .containsExactly(
            List.of(
                new XmssAggregateStatement(
                    List.of(key(state, 3), key(state, 7), key(state, 12)),
                    miscHelpers.computeXmssAttestationLeafIndex(UInt64.ONE),
                    Bytes32.wrap(miscHelpers.computeSigningRoot(data, domain)))));
    assertThat(proofs).containsExactly(proof);

    starkResult = false;
    assertThat(
            attestationUtil
                .isValidIndexedAttestation(state.getFork(), state, indexed(proof, 3))
                .isSuccessful())
        .isFalse();
  }

  @Test
  void rejectsEmptyUnsortedOrUnknownAttesters() throws Exception {
    final BeaconStatePq state = fixtureGenesis();

    assertThat(isValid(state, indexed(Bytes.EMPTY))).isFalse();
    assertThat(isValid(state, indexed(Bytes.EMPTY, 7, 3))).isFalse();
    assertThat(isValid(state, indexed(Bytes.EMPTY, 3, 3))).isFalse();
    assertThat(isValid(state, indexed(Bytes.EMPTY, 3, state.getValidators().size()))).isFalse();
    assertThat(statements).isEmpty();
  }

  @Test
  void blsIndexedAttestationsAreInvalid() throws Exception {
    final BeaconStatePq state = fixtureGenesis();
    assertThat(isValid(state, dataStructureUtil.randomIndexedAttestation())).isFalse();
  }

  private boolean isValid(final BeaconStatePq state, final IndexedAttestation attestation) {
    return attestationUtil
        .isValidIndexedAttestation(state.getFork(), state, attestation)
        .isSuccessful();
  }

  private IndexedAttestation indexed(final Bytes proof, final long... indices) {
    final List<UInt64> attesters = new ArrayList<>();
    for (final long index : indices) {
      attesters.add(UInt64.valueOf(index));
    }
    return indexedSchema.create(
        indexedSchema.getAttestingIndicesSchema().of(attesters), data, proof);
  }

  private static Bytes32 key(final BeaconStatePq state, final int index) {
    return ((ValidatorPq) state.getValidators().get(index)).getXmssPublicKey();
  }

  @Test
  void singleAttestation_verifiesXmssAtTheTargetEpochsLeaf() throws Exception {
    final BeaconStatePq state = fixtureGenesis();
    final int attester = committee(state).getInt(1);
    expectedSignerKey = key(state, attester);
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(
            spec, single(attester, miscHelpers.computeXmssAttestationLeafIndex(UInt64.ONE)));

    assertThat(
            safeJoin(
                    attestationUtil.isValidIndexedAttestationAsync(
                        state.getFork(),
                        state,
                        attestation,
                        AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP)))
                .isSuccessful())
        .isTrue();
    // Fork choice uses the result without verifying again
    assertThat(attestation.isValidIndexedAttestation()).isTrue();
    assertThat(attestation.getIndexedAttestation().orElseThrow().attestingIndices())
        .containsExactly(UInt64.valueOf(attester));
    // Downstream it is an aggregate of one attester; the single, with its signature, is kept
    final AttestationPq converted = (AttestationPq) attestation.getAttestation();
    assertThat(converted.getAggregationBits().getAllSetBits().toIntArray()).containsExactly(1);
    assertThat(converted.getCommitteeBitsRequired().getAllSetBits().toIntArray())
        .containsExactly(data.getIndex().intValue());
    assertThat(attestation.getUnconvertedSingleAttestation())
        .hasValueSatisfying(single -> assertThat(single).isInstanceOf(SingleAttestationPq.class));
  }

  @Test
  void attestationSubnets_areModuloThePqSubnetCount() {
    // compute_subnet_for_attestation: (16 committees * slot 3 + committee 5) mod 16, not mod 64
    assertThat(config.getAttestationSubnetCountPq()).isEqualTo(16);
    assertThat(
            specVersion
                .getBeaconStateUtil()
                .computeSubnetForCommittee(
                    UInt64.valueOf(3), UInt64.valueOf(5), UInt64.valueOf(16)))
        .isEqualTo(5);
  }

  @Test
  void singleAttestation_rejectsAnotherLeafOrANonMember() throws Exception {
    final BeaconStatePq state = fixtureGenesis();
    final int attester = committee(state).getInt(1);
    expectedSignerKey = key(state, attester);

    // Signed at the next epoch's leaf
    assertThat(
            isValid(
                state,
                single(attester, miscHelpers.computeXmssAttestationLeafIndex(UInt64.valueOf(2)))))
        .isFalse();
    // Not in the committee
    final int outsider =
        IntStream.range(0, state.getValidators().size())
            .filter(index -> !committee(state).contains(index))
            .findFirst()
            .orElseThrow();
    expectedSignerKey = key(state, outsider);
    assertThat(
            isValid(
                state, single(outsider, miscHelpers.computeXmssAttestationLeafIndex(UInt64.ONE))))
        .isFalse();
  }

  @Test
  void aggregateWithoutItsProof_isNotValidatedHere() throws Exception {
    final BeaconStatePq state = fixtureGenesis();
    final AttestationPqSchema schema =
        schemaDefinitions.getPqAttestationSchemas().getAttestationSchema();
    final AttestationPq aggregate =
        schema.create(
            schema.getAggregationBitsSchema().ofBits(committee(state).size(), 0, 1),
            data,
            schema.getCommitteeBitsSchema().orElseThrow().ofBits(0));

    assertThat(isValid(state, aggregate)).isFalse();
  }

  private boolean isValid(final BeaconStatePq state, final Attestation attestation) {
    return safeJoin(
            attestationUtil.isValidIndexedAttestationAsync(
                state.getFork(),
                state,
                ValidatableAttestation.from(spec, attestation),
                AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP)))
        .isSuccessful();
  }

  private IntList committee(final BeaconStatePq state) {
    return accessors.getBeaconCommittee(state, data.getSlot(), data.getIndex());
  }

  private SingleAttestationPq single(final int attester, final UInt64 leaf) {
    final Bytes signature =
        Bytes.concatenate(
            Bytes.ofUnsignedInt(leaf.longValue(), ByteOrder.LITTLE_ENDIAN),
            Bytes.wrap(new byte[XmssConstants.SIGNATURE_SIZE - XmssConstants.LEAF_INDEX_SIZE]));
    return new SingleAttestationPq(data.getIndex(), UInt64.valueOf(attester), data, signature);
  }

  private BeaconStatePq fixtureGenesis() throws Exception {
    return BeaconStatePq.required(
        schemaDefinitions
            .getPqBeaconStateSchema()
            .sszDeserialize(
                Bytes.wrap(
                    Resources.toByteArray(
                        Resources.getResource(
                            "tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/pq/"
                                + "pq_genesis_minimal.ssz")))));
  }
}
