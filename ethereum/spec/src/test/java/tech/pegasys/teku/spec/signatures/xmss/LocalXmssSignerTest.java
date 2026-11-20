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

package tech.pegasys.teku.spec.signatures.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_AGGREGATE_AND_PROOF;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_PROPOSER_PREFERENCES;

import java.nio.file.Path;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AggregateAndProofPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.PqAttestationSchemas;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.xmss.Xmss;
import tech.pegasys.teku.xmss.XmssSecretKey;
import tech.pegasys.teku.xmss.XmssSignature;
import tech.pegasys.teku.xmss.XmssSigningKey;

class LocalXmssSignerTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalPq();
  // Loading a key builds its top tree, so one key serves every test; each test uses fresh leaves
  // through its own leaf record
  private static XmssSigningKey signingKey;

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(SPEC);
  private final MiscHelpersPq miscHelpers = (MiscHelpersPq) SPEC.getGenesisSpec().miscHelpers();
  private final Bytes32 vrfSeed = Bytes32.fromHexStringLenient("0x5eed");
  private final VrfTree vrfTree = VrfTree.derive(vrfSeed, 10, 4);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();

  @TempDir Path dir;
  private LocalXmssSigner signer;

  @BeforeAll
  static void loadKey() {
    signingKey =
        Xmss.loadSigningKey(
            XmssSecretKey.fromBytes(
                Bytes.fromHexString(
                    "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")));
  }

  @AfterAll
  static void closeKey() {
    signingKey.close();
  }

  @BeforeEach
  void setUp() {
    signer = createSigner(SPEC);
  }

  @Test
  void signBlock_signsAtTheProposerLeafOfItsSlot() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(UInt64.valueOf(12));

    final XmssSignature signature = safeJoin(signer.signBlock(block, forkInfo));

    assertVerifies(
        miscHelpers.computeXmssLeafIndex(block.getSlot(), LEAF_OFFSET_BEACON_PROPOSER),
        signingRootUtil.signingRootForSignBlock(block, forkInfo),
        signature);
  }

  @Test
  void signBlock_refusesASecondBlockForTheSameSlot() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(UInt64.valueOf(12));
    final BeaconBlock otherBlock = dataStructureUtil.randomBeaconBlock(UInt64.valueOf(12));
    safeJoin(signer.signBlock(block, forkInfo));

    assertThatSafeFuture(signer.signBlock(otherBlock, forkInfo))
        .isCompletedExceptionallyWith(XmssLeafReuseException.class);
    // The same block again may be re-signed, for example to re-broadcast it
    assertThatSafeFuture(signer.signBlock(block, forkInfo)).isCompleted();
  }

  @Test
  void signAttestationData_signsAtTheTargetEpochsLeaf() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(UInt64.ONE);

    final XmssSignature signature = safeJoin(signer.signAttestationData(attestationData, forkInfo));

    assertVerifies(
        miscHelpers.computeXmssAttestationLeafIndex(attestationData.getTarget().getEpoch()),
        signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo),
        signature);
  }

  @Test
  void signAggregateAndProof_signsAtTheAggregateLeafOfItsSlot() {
    final PqAttestationSchemas schemas =
        SchemaDefinitionsPq.required(SPEC.getGenesisSchemaDefinitions()).getPqAttestationSchemas();
    final AttestationPqSchema attestationSchema = schemas.getAttestationSchema();
    final AttestationPq aggregate =
        attestationSchema.create(
            attestationSchema.getAggregationBitsSchema().ofBits(4, 1, 3),
            dataStructureUtil.randomAttestationData(UInt64.valueOf(9)),
            attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(0));
    final AggregateAndProofPq aggregateAndProof =
        schemas
            .getAggregateAndProofSchema()
            .create(UInt64.valueOf(3), aggregate, Bytes.fromHexString("0x1234"));

    final XmssSignature signature =
        safeJoin(signer.signAggregateAndProof(aggregateAndProof, forkInfo));

    assertVerifies(
        miscHelpers.computeXmssLeafIndex(
            aggregate.getData().getSlot(), LEAF_OFFSET_AGGREGATE_AND_PROOF),
        signingRootUtil.signingRootForSignAggregateAndProof(aggregateAndProof, forkInfo),
        signature);
  }

  @Test
  void signProposerPreferences_refusesDifferentPreferencesForTheSameSlot() {
    final ProposerPreferences random = dataStructureUtil.randomProposerPreferences();
    final ProposerPreferences preferences =
        random
            .getSchema()
            .create(
                random.getDependentRoot(),
                UInt64.valueOf(20),
                random.getValidatorIndex(),
                random.getFeeRecipient(),
                random.getTargetGasLimit());
    final XmssSignature signature = safeJoin(signer.signProposerPreferences(preferences, forkInfo));
    assertVerifies(
        miscHelpers.computeXmssLeafIndex(
            preferences.getProposalSlot(), LEAF_OFFSET_PROPOSER_PREFERENCES),
        signingRootUtil.signingRootForSignProposerPreferences(preferences, forkInfo),
        signature);

    // As when the dependent root changes: the spec forbids signing the slot's preferences again
    final ProposerPreferences changed =
        preferences
            .getSchema()
            .create(
                dataStructureUtil.randomBytes32(),
                preferences.getProposalSlot(),
                preferences.getValidatorIndex(),
                preferences.getFeeRecipient(),
                preferences.getTargetGasLimit());
    assertThatSafeFuture(signer.signProposerPreferences(changed, forkInfo))
        .isCompletedExceptionallyWith(XmssLeafReuseException.class);
  }

  @Test
  void signAtReservedLeaf_usesEachReservedLeafOnceInOrder() {
    final Bytes32 first = Bytes32.fromHexStringLenient("0x01");
    final Bytes32 second = Bytes32.fromHexStringLenient("0x02");

    final XmssSignature firstSignature = safeJoin(signer.signAtReservedLeaf(first));
    final XmssSignature secondSignature = safeJoin(signer.signAtReservedLeaf(second));

    assertVerifies(UInt64.ZERO, first, firstSignature);
    assertVerifies(UInt64.ONE, second, secondSignature);
  }

  @Test
  void computeVrfProof_isTheTreesProof() {
    final List<Bytes32> proof = safeJoin(signer.computeVrfProof(UInt64.valueOf(77)));

    assertThat(proof).isEqualTo(vrfTree.computeProof(UInt64.valueOf(77)));
    assertThat(VrfTree.computeRootFromProof(proof, UInt64.valueOf(77)))
        .isEqualTo(vrfTree.getRoot());
  }

  @Test
  void signing_rejectsSlotsBeforeThePqFork() {
    final Spec specWithLaterPq = TestSpecFactory.createMinimalWithPqForkEpoch(UInt64.ONE);
    final LocalXmssSigner preForkSigner = createSigner(specWithLaterPq);
    final AttestationData attestationData = dataStructureUtil.randomAttestationData(UInt64.ZERO);

    assertThatThrownBy(() -> preForkSigner.signAttestationData(attestationData, forkInfo))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private LocalXmssSigner createSigner(final Spec spec) {
    return new LocalXmssSigner(
        spec,
        signingKey,
        vrfTree,
        new LocalXmssLeafProtector(SyncDataAccessor.create(dir), dir),
        SYNC_RUNNER);
  }

  private void assertVerifies(
      final UInt64 leafIndex, final Bytes signingRoot, final XmssSignature signature) {
    assertThat(signature.getLeafIndex()).isEqualTo(leafIndex);
    assertThat(
            Xmss.verify(signingKey.getPublicKey(), leafIndex, Bytes32.wrap(signingRoot), signature))
        .isTrue();
  }
}
