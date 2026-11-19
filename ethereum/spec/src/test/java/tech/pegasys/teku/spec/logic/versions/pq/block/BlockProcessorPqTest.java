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

package tech.pegasys.teku.spec.logic.versions.pq.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;
import static tech.pegasys.teku.spec.constants.PqConstants.VRF_TREE_DEPTH;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.BeaconBlockBodyPq;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.BeaconBlockBodySchemaPq;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.PqBlockSchemas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.BuilderPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ExecutionRequestsBuilderPq;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ValidatorPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BlockValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.OperationValidatorCapella;
import tech.pegasys.teku.spec.logic.versions.electra.operations.validation.VoluntaryExitValidatorElectra;
import tech.pegasys.teku.spec.logic.versions.gloas.operations.validation.AttestationDataValidatorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.execution.ExecutionRequestsProcessorPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.logic.versions.pq.util.AttestationUtilPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion specVersion = spec.forMilestone(SpecMilestone.PQ);
  private final SpecConfigPq config = SpecConfigPq.required(specVersion.getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(specVersion.getSchemaDefinitions());
  private final PqBlockSchemas blockSchemas = schemaDefinitions.getPqBlockSchemas();
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
  private final BeaconStateMutatorsPq mutators =
      new BeaconStateMutatorsPq(config, miscHelpers, accessors, schemaDefinitions);

  private final List<XmssCall> xmssCalls = new ArrayList<>();
  private boolean xmssResult = true;
  private final XmssSignatureVerifier xmssVerifier =
      (pubkey, leafIndex, message, signature) -> {
        xmssCalls.add(new XmssCall(pubkey, leafIndex, message, signature));
        return xmssResult;
      };

  private final List<StarkCall> starkCalls = new ArrayList<>();
  private boolean starkResult = true;
  private final StarkProofVerifier starkVerifier =
      (statements, proof) -> {
        starkCalls.add(new StarkCall(statements, proof));
        return starkResult;
      };

  private final AttestationUtilPq attestationUtil =
      new AttestationUtilPq(config, schemaDefinitions, accessors, miscHelpers, starkVerifier);

  private final BlockProcessorPq blockProcessor =
      new BlockProcessorPq(
          config,
          predicates,
          miscHelpers,
          accessors,
          mutators,
          specVersion.operationSignatureVerifier(),
          specVersion.getBeaconStateUtil(),
          attestationUtil,
          specVersion.getValidatorsUtil(),
          new OperationValidatorCapella(
              predicates,
              accessors,
              new AttestationDataValidatorGloas(config, miscHelpers, accessors),
              attestationUtil,
              new VoluntaryExitValidatorElectra(config, predicates, accessors)),
          schemaDefinitions,
          (WithdrawalsHelpersGloas) specVersion.getWithdrawalsHelpers().orElseThrow(),
          new ExecutionRequestsDataCodec(schemaDefinitions.getPqExecutionRequestsSchema()),
          new ExecutionRequestsProcessorPq(
              schemaDefinitions,
              miscHelpers,
              config,
              predicates,
              new ValidatorsUtilGloas(config, miscHelpers, accessors),
              mutators,
              accessors),
          xmssVerifier,
          starkVerifier);

  @Test
  void randaoMixesInTheVrfLeafAfterVerifyingTheProof() throws Exception {
    final UInt64 slot = UInt64.ONE;
    final BeaconStatePq genesis = atSlot(fixtureGenesis(), slot);
    final int proposerIndex = accessors.getBeaconProposerIndex(genesis);
    final List<Bytes32> vrfProof = randomVrfProof();
    final BeaconStatePq state =
        withVrfRoot(genesis, proposerIndex, vrfRoot(vrfProof, slot.longValue()));
    final BeaconBlockBodyPq body = body(fields -> setVrfProof(fields, vrfProof));

    final BeaconStatePq processed =
        state.updatedPq(mutable -> blockProcessor.processRandaoNoValidation(mutable, body));

    final Bytes32 expectedMix =
        Hash.sha256(
            Bytes.concatenate(
                accessors.getRandaoMix(state, UInt64.ZERO),
                vrfProof.getFirst(),
                Domain.RANDAO.getWrappedBytes()));
    assertThat(accessors.getRandaoMix(processed, UInt64.ZERO)).isEqualTo(expectedMix);

    // The same proof does not open the leaf of another slot
    final BeaconStatePq laterState = atSlot(state, slot.plus(1));
    final int laterProposer = accessors.getBeaconProposerIndex(laterState);
    final BeaconStatePq laterWithRoot =
        withVrfRoot(laterState, laterProposer, vrfRoot(vrfProof, slot.longValue()));
    assertThatThrownBy(
            () ->
                laterWithRoot.updatedPq(
                    mutable -> blockProcessor.processRandaoNoValidation(mutable, body)))
        .isInstanceOf(BlockProcessingException.class)
        .hasMessageContaining("VRF proof");
  }

  @Test
  void blockSignatureIsVerifiedWithXmssAtTheProposersLeaf() throws Exception {
    final UInt64 slot = UInt64.valueOf(3);
    final BeaconStatePq state = atSlot(fixtureGenesis(), slot);
    final int proposerIndex = accessors.getBeaconProposerIndex(state, slot);
    final BeaconBlock block = block(state, slot, proposerIndex, body(fields -> {}));
    final SignedBeaconBlock signedBlock =
        blockSchemas.getSignedBeaconBlockSchema().create(block, xmssSignature(5));

    assertThat(verifyBlockSignatures(state, signedBlock).isValid()).isTrue();

    final Bytes32 domain =
        accessors.getDomain(state.getForkInfo(), Domain.BEACON_PROPOSER, UInt64.ZERO);
    assertThat(xmssCalls)
        .containsExactly(
            new XmssCall(
                validatorKey(state, proposerIndex),
                miscHelpers.computeXmssLeafIndex(slot, LEAF_OFFSET_BEACON_PROPOSER),
                Bytes32.wrap(miscHelpers.computeSigningRoot(block, domain)),
                xmssSignature(5).getBytes()));
    // No attestations: the proof must prove no statements
    assertThat(starkCalls).containsExactly(new StarkCall(List.of(), Bytes.EMPTY));

    xmssResult = false;
    assertThat(verifyBlockSignatures(state, signedBlock).isValid()).isFalse();
  }

  @Test
  void proposerSlashingHeadersAreVerifiedAtTheirSlotsLeaf() throws Exception {
    final BeaconStatePq state = atSlot(fixtureGenesis(), UInt64.valueOf(12));
    final UInt64 headerSlot = UInt64.valueOf(9);
    final BeaconBlockHeader message1 =
        new BeaconBlockHeader(
            headerSlot,
            UInt64.valueOf(4),
            Bytes32.ZERO,
            dataStructureUtil.randomBytes32(),
            Bytes32.ZERO);
    final BeaconBlockHeader message2 =
        new BeaconBlockHeader(
            headerSlot,
            UInt64.valueOf(4),
            Bytes32.ZERO,
            dataStructureUtil.randomBytes32(),
            Bytes32.ZERO);
    final ProposerSlashing slashing =
        new ProposerSlashing(
            new SignedBeaconBlockHeader(message1, xmssSignature(1)),
            new SignedBeaconBlockHeader(message2, xmssSignature(2)));

    final BlockValidationResult result =
        blockProcessor.verifyProposerSlashings(
            state,
            blockSchemas.getBeaconBlockBodySchema().getProposerSlashingsSchema().of(slashing),
            BLSSignatureVerifier.NOOP);

    assertThat(result.isValid()).isTrue();
    final Bytes32 domain =
        accessors.getDomain(state.getForkInfo(), Domain.BEACON_PROPOSER, UInt64.ONE);
    final UInt64 leafIndex =
        miscHelpers.computeXmssLeafIndex(headerSlot, LEAF_OFFSET_BEACON_PROPOSER);
    assertThat(xmssCalls)
        .containsExactly(
            new XmssCall(
                validatorKey(state, 4),
                leafIndex,
                Bytes32.wrap(miscHelpers.computeSigningRoot(message1, domain)),
                xmssSignature(1).getBytes()),
            new XmssCall(
                validatorKey(state, 4),
                leafIndex,
                Bytes32.wrap(miscHelpers.computeSigningRoot(message2, domain)),
                xmssSignature(2).getBytes()));

    xmssResult = false;
    assertThat(
            blockProcessor
                .verifyProposerSlashings(
                    state,
                    blockSchemas
                        .getBeaconBlockBodySchema()
                        .getProposerSlashingsSchema()
                        .of(slashing),
                    BLSSignatureVerifier.NOOP)
                .isValid())
        .isFalse();
  }

  @Test
  void starkProofProvesTheStatementsOfEveryAttestation() throws Exception {
    final UInt64 slot = UInt64.valueOf(3);
    final BeaconStatePq state = atSlot(fixtureGenesis(), slot);
    final Attestation attestation = fullAttestation(state, UInt64.ONE);
    final Bytes proof = Bytes.fromHexString("0x0102030405");
    final BeaconBlockBodyPq body =
        body(
            fields -> {
              setField(
                  fields,
                  BlockBodyFields.ATTESTATIONS,
                  bodySchema().getAttestationsSchema().of(attestation, attestation));
              setField(
                  fields,
                  BlockBodyFields.PROOF,
                  bodySchema().getAttestationProofSchema().fromBytes(proof));
            });
    final int proposerIndex = accessors.getBeaconProposerIndex(state, slot);
    final SignedBeaconBlock signedBlock =
        blockSchemas
            .getSignedBeaconBlockSchema()
            .create(block(state, slot, proposerIndex, body), xmssSignature(5));

    assertThat(verifyBlockSignatures(state, signedBlock).isValid()).isTrue();

    final List<XmssAggregateStatement> statements =
        accessors.getAttestationStatements(state, attestation);
    assertThat(statements).hasSize(1);
    assertThat(statements.getFirst().leafIndex()).isEqualTo(XMSS_RESERVED_LEAVES.plus(UInt64.ZERO));
    final List<XmssAggregateStatement> expected = new ArrayList<>(statements);
    expected.addAll(statements);
    assertThat(starkCalls).containsExactly(new StarkCall(expected, proof));

    starkResult = false;
    assertThat(verifyBlockSignatures(state, signedBlock).isValid()).isFalse();
  }

  @Test
  void attestationsNeedTheMinimumInclusionDelay() throws Exception {
    final BeaconStatePq state = atSlot(fixtureGenesis(), UInt64.valueOf(3));
    assertThat(config.getMinAttestationInclusionDelayPq()).isEqualTo(2);

    state.updatedPq(
        mutable ->
            blockProcessor.assertAttestationValid(mutable, fullAttestation(state, UInt64.ONE)));
    assertThatThrownBy(
            () ->
                state.updatedPq(
                    mutable ->
                        blockProcessor.assertAttestationValid(
                            mutable, fullAttestation(state, UInt64.valueOf(2)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minimum inclusion delay");
  }

  @Test
  void selfBuildBidCarriesTheZeroSignature() {
    assertThat(blockProcessor.isSelfBuildBidSignature(bid(SszXmssSignature.empty()))).isTrue();
    assertThat(blockProcessor.isSelfBuildBidSignature(bid(xmssSignature(1)))).isFalse();
    assertThat(
            blockProcessor.isSelfBuildBidSignature(
                dataStructureUtil.randomSignedExecutionPayloadBid()))
        .isFalse();
  }

  @Test
  void builderBidIsVerifiedOnlyAtABuilderBidLeaf() throws Exception {
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        atSlot(fixtureGenesis(), UInt64.valueOf(3))
            .updatedPq(
                mutable ->
                    mutable
                        .getBuilders()
                        .append(
                            new BuilderPq(
                                builderKey,
                                SpecConfigGloas.PAYLOAD_BUILDER_VERSION,
                                dataStructureUtil.randomEth1Address(),
                                UInt64.valueOf(1_000_000_000L),
                                UInt64.ZERO,
                                SpecConfigGloas.FAR_FUTURE_EPOCH)));
    final UInt64 firstBidLeaf = XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES);

    // An attestation leaf is not a bid leaf, whatever the signature
    assertThat(
            blockProcessor.verifyExecutionPayloadBidSignature(
                state, bid(signatureAtLeaf(firstBidLeaf.minus(1)))))
        .isFalse();
    assertThat(xmssCalls).isEmpty();

    final UInt64 leaf = firstBidLeaf.plus(7);
    final SignedExecutionPayloadBid signedBid = bid(signatureAtLeaf(leaf));
    assertThat(blockProcessor.verifyExecutionPayloadBidSignature(state, signedBid)).isTrue();
    final Bytes32 domain =
        accessors.getDomain(state.getForkInfo(), Domain.BEACON_BUILDER, UInt64.ZERO);
    assertThat(xmssCalls)
        .containsExactly(
            new XmssCall(
                builderKey,
                leaf,
                Bytes32.wrap(miscHelpers.computeSigningRoot(signedBid.getMessage(), domain)),
                signedBid.getXmssSignature()));
  }

  @Test
  void parentExecutionPayloadAppliesThePreregistrationRequests() throws Exception {
    final Bytes32 validatorKey = dataStructureUtil.randomBytes32();
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final ExecutionRequests requests =
        new ExecutionRequestsBuilderPq(schemaDefinitions.getPqExecutionRequestsSchema())
            .validatorPreregistrations(
                () ->
                    List.of(
                        new ValidatorPreregistrationRequest(
                            validatorKey, Bytes32.ZERO, dataStructureUtil.randomBytes32())))
            .builderPreregistrations(
                () -> List.of(new BuilderPreregistrationRequest(builderKey, Bytes32.ZERO)))
            .build();
    final BeaconStatePq state = atSlot(fixtureGenesis(), UInt64.valueOf(3));

    final BeaconStatePq processed =
        state.updatedPq(
            mutable ->
                blockProcessor.applyParentExecutionPayload(
                    mutable, requests, mutators.createValidatorExitContextSupplier(mutable)));

    assertThat(processed.getValidatorPreregistrations()).hasSize(1);
    assertThat(processed.getValidatorPreregistrations().get(0).getXmssPublicKey())
        .isEqualTo(validatorKey);
    assertThat(processed.getBuilderPreregistrations()).hasSize(1);
    assertThat(processed.getBuilderPreregistrations().get(0).getXmssPublicKey())
        .isEqualTo(builderKey);
    assertThat(processed.getLatestBlockHash())
        .isEqualTo(state.getLatestExecutionPayloadBid().getBlockHash());
  }

  private BlockValidationResult verifyBlockSignatures(
      final BeaconStatePq state, final SignedBeaconBlock signedBlock) {
    xmssCalls.clear();
    starkCalls.clear();
    return blockProcessor.verifyBlockSignatures(
        state, signedBlock, IndexedAttestationCache.NOOP, BLSSignatureVerifier.NOOP);
  }

  private Attestation fullAttestation(final BeaconStatePq state, final UInt64 slot) {
    final AttestationPqSchema schema =
        schemaDefinitions.getPqAttestationSchemas().getAttestationSchema();
    final IntList committee = accessors.getBeaconCommittee(state, slot, UInt64.ZERO);
    final int[] allBits = new int[committee.size()];
    for (int i = 0; i < allBits.length; i++) {
      allBits[i] = i;
    }
    final AttestationData data =
        new AttestationData(
            slot,
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            state.getCurrentJustifiedCheckpoint(),
            new Checkpoint(UInt64.ZERO, dataStructureUtil.randomBytes32()));
    return schema.create(
        schema.getAggregationBitsSchema().ofBits(committee.size(), allBits),
        data,
        schema.getCommitteeBitsSchema().orElseThrow().ofBits(0));
  }

  private SignedExecutionPayloadBid bid(final SszXmssSignature signature) {
    return blockSchemas
        .getSignedExecutionPayloadBidSchema()
        .create(
            dataStructureUtil.randomExecutionPayloadBid(UInt64.valueOf(3), UInt64.ZERO), signature);
  }

  private BeaconBlock block(
      final BeaconStatePq state,
      final UInt64 slot,
      final int proposerIndex,
      final BeaconBlockBodyPq body) {
    return blockSchemas
        .getBeaconBlockSchema()
        .create(
            slot,
            UInt64.valueOf(proposerIndex),
            state.getLatestBlockHeader().hashTreeRoot(),
            Bytes32.ZERO,
            body);
  }

  private BeaconBlockBodySchemaPq bodySchema() {
    return blockSchemas.getBeaconBlockBodySchema();
  }

  private BeaconBlockBodyPq body(final Consumer<List<SszData>> customizer) {
    final BeaconBlockBodyPq empty = BeaconBlockBodyPq.required(bodySchema().createEmpty());
    final List<SszData> fields = new ArrayList<>();
    for (int i = 0; i < empty.size(); i++) {
      fields.add(empty.get(i));
    }
    customizer.accept(fields);
    return BeaconBlockBodyPq.required(bodySchema().createFromFieldValues(fields));
  }

  private void setField(
      final List<SszData> fields, final BlockBodyFields field, final SszData value) {
    fields.set(bodySchema().getFieldIndex(field), value);
  }

  @SuppressWarnings("unchecked")
  private void setVrfProof(final List<SszData> fields, final List<Bytes32> vrfProof) {
    final SszBytes32VectorSchema<?> schema =
        (SszBytes32VectorSchema<?>)
            bodySchema().getChildSchema(bodySchema().getFieldIndex(BlockBodyFields.RANDAO_REVEAL));
    setField(fields, BlockBodyFields.RANDAO_REVEAL, schema.of(vrfProof));
  }

  private List<Bytes32> randomVrfProof() {
    final List<Bytes32> proof = new ArrayList<>();
    for (int i = 0; i <= VRF_TREE_DEPTH; i++) {
      proof.add(dataStructureUtil.randomBytes32());
    }
    return proof;
  }

  /** The root of a VRF tree in which {@code proof} opens the leaf at {@code index}. */
  private static Bytes32 vrfRoot(final List<Bytes32> proof, final long index) {
    Bytes32 node = proof.getFirst();
    for (int depth = 0; depth < VRF_TREE_DEPTH; depth++) {
      final Bytes32 sibling = proof.get(depth + 1);
      node =
          ((index >> depth) & 1) == 1
              ? Hash.sha256(Bytes.concatenate(sibling, node))
              : Hash.sha256(Bytes.concatenate(node, sibling));
    }
    return node;
  }

  private static BeaconStatePq withVrfRoot(
      final BeaconStatePq state, final int validatorIndex, final Bytes32 vrfRoot) {
    final Validator validator = state.getValidators().get(validatorIndex);
    return state.updatedPq(
        mutable ->
            mutable
                .getValidators()
                .set(
                    validatorIndex,
                    new ValidatorPq(
                        ((ValidatorPq) validator).getXmssPublicKey(),
                        vrfRoot,
                        validator.getWithdrawalCredentials(),
                        validator.getEffectiveBalance(),
                        validator.isSlashed(),
                        validator.getActivationEligibilityEpoch(),
                        validator.getActivationEpoch(),
                        validator.getExitEpoch(),
                        validator.getWithdrawableEpoch())));
  }

  private static Bytes32 validatorKey(final BeaconStatePq state, final int validatorIndex) {
    return ((ValidatorPq) state.getValidators().get(validatorIndex)).getXmssPublicKey();
  }

  private static BeaconStatePq atSlot(final BeaconStatePq state, final UInt64 slot) {
    return state.updatedPq(mutable -> mutable.setSlot(slot));
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

  private static SszXmssSignature xmssSignature(final int seed) {
    final byte[] signature = new byte[2404];
    signature[0] = (byte) seed;
    signature[2403] = (byte) seed;
    return new SszXmssSignature(Bytes.wrap(signature));
  }

  private static SszXmssSignature signatureAtLeaf(final UInt64 leafIndex) {
    final byte[] signature = new byte[2404];
    final Bytes leaf = Bytes.ofUnsignedInt(leafIndex.longValue(), ByteOrder.LITTLE_ENDIAN);
    System.arraycopy(leaf.toArrayUnsafe(), 0, signature, 0, 4);
    signature[2403] = 1;
    return new SszXmssSignature(Bytes.wrap(signature));
  }

  private record XmssCall(Bytes32 pubkey, UInt64 leafIndex, Bytes32 message, Bytes signature) {}

  private record StarkCall(List<XmssAggregateStatement> statements, Bytes proof) {}
}
