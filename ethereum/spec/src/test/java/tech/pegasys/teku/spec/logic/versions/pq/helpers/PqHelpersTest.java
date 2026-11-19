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

package tech.pegasys.teku.spec.logic.versions.pq.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PqHelpersTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigPq config =
      SpecConfigPq.required(spec.forMilestone(SpecMilestone.PQ).getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
  private final BeaconStateMutatorsPq mutators =
      new BeaconStateMutatorsPq(config, miscHelpers, accessors, schemaDefinitions);

  @Test
  void aggregationCommitteesMatchAnIndependentImplementation() throws IOException {
    final BeaconStatePq genesis = fixtureGenesis();
    // The window holds the previous epoch (empty at genesis), then epochs 0 and 1 at 8 to 23
    for (int slot = 0; slot < 16; slot++) {
      final List<IntList> computed =
          accessors.computeAggregationCommittees(genesis, UInt64.valueOf(slot));
      assertThat(computed).hasSize(2);
      for (int index = 0; index < computed.size(); index++) {
        final int[] stored =
            accessors
                .getAggregationCommittee(genesis, UInt64.valueOf(slot), UInt64.valueOf(index))
                .toIntArray();
        assertThat(stored)
            .describedAs("slot %s committee %s", slot, index)
            .hasSize(4)
            .isEqualTo(computed.get(index).toIntArray());
      }
    }
  }

  @Test
  void committeeCountIsCappedAtTheFewerPqCommittees() {
    final Spec mainnet = TestSpecFactory.createMainnetPq();
    final SpecConfigPq mainnetConfig =
        SpecConfigPq.required(mainnet.forMilestone(SpecMilestone.PQ).getConfig());
    final SchemaDefinitionsPq mainnetSchemas =
        SchemaDefinitionsPq.required(mainnet.forMilestone(SpecMilestone.PQ).getSchemaDefinitions());
    final PredicatesPq mainnetPredicates = new PredicatesPq(mainnetConfig);
    final BeaconStateAccessorsPq mainnetAccessors =
        new BeaconStateAccessorsPq(
            mainnetConfig,
            mainnetSchemas,
            mainnetPredicates,
            new MiscHelpersPq(mainnetConfig, mainnetPredicates, mainnetSchemas));

    assertThat(mainnetAccessors.getCommitteeCountPerSlot(1_000_000))
        .isEqualTo(UInt64.valueOf(mainnetConfig.getMaxCommitteesPerSlotPq()))
        .isEqualTo(UInt64.valueOf(16));
    assertThat(mainnetAccessors.getCommitteeCountPerSlot(1)).isEqualTo(UInt64.ONE);
  }

  @Test
  void leafIndicesFollowTheSpecLayout() {
    assertThat(miscHelpers.computeXmssAttestationLeafIndex(UInt64.valueOf(5)))
        .isEqualTo(UInt64.valueOf((1L << 15) + 5));
    assertThat(
            miscHelpers.computeXmssLeafIndex(
                UInt64.valueOf(3), PqConstants.LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE))
        .isEqualTo(UInt64.valueOf((1L << 15) + (1L << 29) + (1L << 30) + 3 * 8 + 4));
    final UInt64 firstBidLeaf = UInt64.valueOf((1L << 15) + (1L << 29));
    assertThat(predicates.isBuilderBidLeafIndex(firstBidLeaf.minus(1))).isFalse();
    assertThat(predicates.isBuilderBidLeafIndex(firstBidLeaf)).isTrue();
    assertThat(predicates.isBuilderBidLeafIndex(firstBidLeaf.plus((1L << 30) - 1))).isTrue();
    assertThat(predicates.isBuilderBidLeafIndex(firstBidLeaf.plus(1L << 30))).isFalse();
  }

  @Test
  void depositKeysAreLegacyOrXmss() {
    final Bytes48 blsKey = dataStructureUtil.randomPublicKey().toBytesCompressed();
    final Bytes32 xmssKey = dataStructureUtil.randomBytes32();
    final Bytes48 xmssDepositKey =
        Bytes48.wrap(Bytes.concatenate(Bytes.wrap(new byte[16]), xmssKey));

    assertThat(predicates.isLegacyPubkey(blsKey)).isTrue();
    assertThat(predicates.isLegacyPubkey(xmssDepositKey)).isFalse();
    assertThat(miscHelpers.computeDepositPubkey(xmssDepositKey)).isEqualTo(xmssKey);
    assertThat(miscHelpers.computeDepositPubkey(blsKey))
        .isEqualTo(Hash.sha256(Bytes.concatenate(blsKey, Bytes.of(0))));
    assertThat(miscHelpers.computeLegacyVrfRoot(blsKey))
        .isEqualTo(Hash.sha256(Bytes.concatenate(blsKey, Bytes.of(1))));
  }

  @Test
  void vrfProofOpensTheLeafOfTheSlot() {
    final UInt64 slot = UInt64.valueOf(1234);
    final Bytes32 leaf = dataStructureUtil.randomBytes32();
    final List<Bytes32> branch = new ArrayList<>();
    Bytes32 node = leaf;
    for (int depth = 0; depth < PqConstants.VRF_TREE_DEPTH; depth++) {
      final Bytes32 sibling = dataStructureUtil.randomBytes32();
      branch.add(sibling);
      node =
          ((1234 >> depth) & 1) == 1
              ? Hash.sha256(Bytes.concatenate(sibling, node))
              : Hash.sha256(Bytes.concatenate(node, sibling));
    }
    final Bytes32 vrfRoot = node;
    final BeaconStatePq state = stateWithValidator(validator(vrfRoot), UInt64.ZERO);
    final List<Bytes32> proof = new ArrayList<>();
    proof.add(leaf);
    proof.addAll(branch);

    assertThat(predicates.isValidVrfProof(state, 0, slot, proof)).isTrue();
    assertThat(predicates.isValidVrfProof(state, 0, slot.plus(1), proof)).isFalse();
    proof.set(0, dataStructureUtil.randomBytes32());
    assertThat(predicates.isValidVrfProof(state, 0, slot, proof)).isFalse();
  }

  @Test
  void latestBlockSlotWalksBackOverEmptySlots() {
    final Bytes32 blockAt6 = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        BeaconStatePq.required(schemaDefinitions.getPqBeaconStateSchema().createEmpty())
            .updatedPq(
                mutable -> {
                  mutable.setSlot(UInt64.valueOf(10));
                  for (int slot = 0; slot < 10; slot++) {
                    mutable
                        .getBlockRoots()
                        .set(
                            slot,
                            SszBytes32.of(slot >= 6 ? blockAt6 : Bytes32.leftPad(Bytes.of(slot))));
                  }
                });

    // Slots 7 to 9 are empty, so the latest block at or before 8 is the one at 6
    assertThat(accessors.getLatestBlockSlot(state, UInt64.valueOf(8), UInt64.valueOf(9)))
        .isEqualTo(UInt64.valueOf(6));
    // A parent at or before the slot is the latest block
    assertThat(accessors.getLatestBlockSlot(state, UInt64.valueOf(8), UInt64.valueOf(5)))
        .isEqualTo(UInt64.valueOf(5));
  }

  @Test
  void timelyHeadNeedsAnInclusionDelayOfTwo() {
    assertThat(accessors.getTimelyHeadInclusionDelay()).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void attestationStatementsAndIndexedAttestationFollowTheCommittees() throws IOException {
    final BeaconStatePq genesis = fixtureGenesis();
    final AttestationPqSchema attestationSchema =
        schemaDefinitions.getPqAttestationSchemas().getAttestationSchema();
    final UInt64 slot = UInt64.valueOf(2);
    final IntList committee0 = accessors.getBeaconCommittee(genesis, slot, UInt64.ZERO);
    final IntList committee1 = accessors.getBeaconCommittee(genesis, slot, UInt64.ONE);
    final int bits = committee0.size() + committee1.size();
    // The first attester of committee 0 and the last of committee 1
    final SszBitlist aggregationBits =
        attestationSchema.getAggregationBitsSchema().ofBits(bits, 0, bits - 1);
    final AttestationData data =
        new AttestationData(
            slot,
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
            new Checkpoint(UInt64.ZERO, dataStructureUtil.randomBytes32()));
    final AttestationPq attestation =
        attestationSchema.create(
            aggregationBits,
            data,
            attestationSchema.getCommitteeBitsSchema().orElseThrow().ofBits(0, 1));

    final List<XmssAggregateStatement> statements =
        accessors.getAttestationStatements(genesis, attestation);

    assertThat(statements).hasSize(2);
    assertThat(statements.get(0).pubkeys()).containsExactly(xmssKey(genesis, committee0.getInt(0)));
    assertThat(statements.get(1).pubkeys())
        .containsExactly(xmssKey(genesis, committee1.getInt(committee1.size() - 1)));
    assertThat(statements.get(0).leafIndex()).isEqualTo(PqConstants.XMSS_RESERVED_LEAVES);

    final IndexedAttestationPq indexed =
        accessors.getIndexedAttestation(genesis, attestation, Bytes.of(1, 2));
    assertThat(indexed.getAttestingIndices().asListUnboxed())
        .containsExactlyElementsOf(
            List.of(committee0.getInt(0), committee1.getInt(committee1.size() - 1)).stream()
                .sorted()
                .map(UInt64::valueOf)
                .toList());
    assertThat(indexed.getProof()).isEqualTo(Bytes.of(1, 2));
  }

  @Test
  void preregistrationsAreActiveUntilTheirExpirySlot() {
    final Bytes32 validatorKey = dataStructureUtil.randomBytes32();
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        stateWithValidator(validator(Bytes32.ZERO), UInt64.valueOf(100))
            .updatedPq(
                mutable -> {
                  mutable
                      .getValidatorPreregistrations()
                      .append(
                          new ValidatorPreregistration(
                              dataStructureUtil.randomBytes32(),
                              Bytes32.ZERO,
                              Bytes32.ZERO,
                              UInt64.valueOf(500)));
                  mutable
                      .getValidatorPreregistrations()
                      .append(
                          new ValidatorPreregistration(
                              validatorKey, Bytes32.ZERO, Bytes32.ZERO, UInt64.valueOf(101)));
                  mutable
                      .getBuilderPreregistrations()
                      .append(
                          new BuilderPreregistration(
                              builderKey, Bytes32.ZERO, UInt64.valueOf(100)));
                });

    assertThat(accessors.getValidatorPreregistrationIndex(state, validatorKey)).contains(1);
    assertThat(accessors.getActiveValidatorPreregistration(state, validatorKey)).isPresent();
    // Expiry is exclusive: the parent payload's slot must be before it
    assertThat(accessors.getBuilderPreregistrationIndex(state, builderKey)).contains(0);
    assertThat(accessors.getActiveBuilderPreregistration(state, builderKey)).isEmpty();

    final BeaconStatePq removed =
        state.updatedPq(mutable -> mutators.removeValidatorPreregistration(mutable, validatorKey));
    assertThat(removed.getValidatorPreregistrations()).hasSize(1);
    assertThat(removed.getValidatorPreregistrations().get(0))
        .isEqualTo(state.getValidatorPreregistrations().get(0));
  }

  @Test
  void excessBalanceIsQueuedUnderTheXmssKey() {
    final ValidatorPq validator = validator(Bytes32.ZERO);
    final BeaconStatePq state =
        stateWithValidator(validator, UInt64.ZERO)
            .updatedPq(
                mutable -> {
                  mutable.getBalances().set(0, SszUInt64.of(UInt64.valueOf(40_000_000_000L)));
                  mutators.queueExcessActiveBalance(mutable, 0);
                });

    assertThat(state.getBalances().get(0).get()).isEqualTo(config.getMinActivationBalance());
    final PendingDeposit deposit = state.getPendingDeposits().get(0);
    assertThat(deposit.getPubkeyBytes())
        .isEqualTo(Bytes.concatenate(Bytes.wrap(new byte[16]), validator.getXmssPublicKey()));
    assertThat(deposit.getSignatureBytes()).isEqualTo(Bytes.wrap(new byte[96]));
    assertThat(deposit.getAmount()).isEqualTo(UInt64.valueOf(8_000_000_000L));
    assertThat(miscHelpers.isPendingValidator(List.of(deposit), validator.getXmssPublicKey()))
        .isTrue();
    assertThatThrownBy(
            () ->
                miscHelpers.isPendingValidator(
                    List.of(deposit), dataStructureUtil.randomPublicKey()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private BeaconStatePq fixtureGenesis() throws IOException {
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

  private ValidatorPq validator(final Bytes32 vrfRoot) {
    return new ValidatorPq(
        dataStructureUtil.randomBytes32(),
        vrfRoot,
        Bytes32.ZERO,
        config.getMinActivationBalance(),
        false,
        UInt64.ZERO,
        UInt64.ZERO,
        UInt64.MAX_VALUE,
        UInt64.MAX_VALUE);
  }

  private BeaconStatePq stateWithValidator(
      final ValidatorPq validator, final UInt64 latestBidSlot) {
    final ExecutionPayloadBid latestBid =
        dataStructureUtil.randomExecutionPayloadBid(latestBidSlot, UInt64.ZERO);
    return BeaconStatePq.required(schemaDefinitions.getPqBeaconStateSchema().createEmpty())
        .updatedPq(
            mutable -> {
              mutable.getValidators().append(validator);
              mutable.getBalances().appendElement(config.getMinActivationBalance());
              mutable.setLatestExecutionPayloadBid(latestBid);
            });
  }

  private static Bytes32 xmssKey(final BeaconStatePq state, final int validatorIndex) {
    return ((ValidatorPq) state.getValidators().get(validatorIndex)).getXmssPublicKey();
  }
}
