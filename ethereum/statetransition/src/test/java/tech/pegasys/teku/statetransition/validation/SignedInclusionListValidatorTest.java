/*
 * Copyright Consensys Software Inc., 2026
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.heze.util.InclusionListUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.storage.client.RecentChainData;

class SignedInclusionListValidatorTest {

  private static final UInt64 HEZE_FORK_EPOCH = UInt64.valueOf(2);
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);

  private final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(HEZE_FORK_EPOCH);
  private final SchemaDefinitionsHeze schemaDefinitions =
      SchemaDefinitionsHeze.required(spec.forMilestone(SpecMilestone.HEZE).getSchemaDefinitions());
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final SignedInclusionListValidator validator =
      new SignedInclusionListValidator(
          spec,
          recentChainData,
          gossipValidationHelper,
          AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));

  // The first Heze slot: its committee lookahead starts at the previous epoch
  private final UInt64 slot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH);
  private final UInt64 lookaheadEpoch = HEZE_FORK_EPOCH.decrement();
  private final UInt64 lookaheadStartSlot = spec.computeStartSlotAtEpoch(lookaheadEpoch);
  private BeaconState genesisState;
  private BeaconState lookaheadState;
  private Bytes32 dependentRoot;
  private int committeeMember;

  @BeforeEach
  void setUp() throws Exception {
    genesisState = ChainBuilder.create(spec, VALIDATOR_KEYS).generateGenesis().getState();
    // No blocks after genesis, so genesis is the dependent block
    dependentRoot = BeaconBlockHeader.fromState(genesisState).getRoot();
    lookaheadState = spec.processSlots(genesisState, lookaheadStartSlot);
    committeeMember = inclusionListUtil().getInclusionListCommittee(lookaheadState, slot).getInt(0);
    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(true);
    mockDependentRoot(dependentRoot, genesisState);
  }

  @Test
  void shouldAcceptValidInclusionListForFirstHezeSlotWhenLookaheadStateIsPreFork() {
    assertThat(lookaheadState.getFork()).isNotEqualTo(spec.fork(HEZE_FORK_EPOCH));

    assertThat(validate(signedInclusionList(transaction(1)))).isEqualTo(accept());
  }

  @Test
  void shouldAcceptDependentRootOnAnotherBranch() {
    // Any possible dependent block is accepted, not just the one on the local head's branch
    final Bytes32 otherBranchRoot = Bytes32.fromHexStringLenient("0x01");
    mockDependentRoot(otherBranchRoot, genesisState);

    assertThat(validate(signedInclusionList(otherBranchRoot, transaction(1)))).isEqualTo(accept());
    verify(recentChainData)
        .retrieveCheckpointState(new Checkpoint(lookaheadEpoch, otherBranchRoot));
  }

  @Test
  void shouldIgnoreWhenDependentBlockHasNotBeenSeen() {
    when(gossipValidationHelper.isBlockAvailable(dependentRoot)).thenReturn(false);

    assertIgnoredWith(validate(signedInclusionList(transaction(1))), "has not been seen");
  }

  @Test
  void shouldIgnoreWhenDependentBlockHasNotPassedValidation() {
    when(gossipValidationHelper.getStateAtBlockRoot(dependentRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertIgnoredWith(validate(signedInclusionList(transaction(1))), "has not passed validation");
  }

  @Test
  void shouldRejectWhenDependentBlockIsAtLookaheadEpochStart() {
    // The shuffling dependent slot is the slot before the lookahead epoch starts
    mockDependentRoot(dependentRoot, lookaheadState);

    assertRejectedWith(
        validate(signedInclusionList(transaction(1))), "after the shuffling dependent slot");
    verify(gossipValidationHelper, never()).isPossibleDependentRoot(any(), any());
  }

  @Test
  void shouldIgnoreWhenDependentBlockIsNotPossible() {
    when(gossipValidationHelper.isPossibleDependentRoot(dependentRoot, lookaheadStartSlot))
        .thenReturn(false);

    assertIgnoredWith(
        validate(signedInclusionList(transaction(1))), "not a possible dependent block");
  }

  @Test
  void shouldIgnoreWhenLookaheadStateIsUnavailable() {
    when(recentChainData.retrieveCheckpointState(new Checkpoint(lookaheadEpoch, dependentRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertIgnoredWith(validate(signedInclusionList(transaction(1))), "state is unavailable");
  }

  @Test
  void shouldRejectIncluderNotInCommittee() {
    final IntList committee = inclusionListUtil().getInclusionListCommittee(lookaheadState, slot);
    final int nonMember =
        IntStream.range(0, VALIDATOR_KEYS.size())
            .filter(index -> !committee.contains(index))
            .findFirst()
            .orElseThrow();
    committeeMember = nonMember;

    assertRejectedWith(
        validate(signedInclusionList(transaction(1))), "not within the inclusion list committee");
  }

  @Test
  void shouldRejectNonGenesisDependentRootInGenesisEpoch() throws Exception {
    // In the genesis epoch, the lookahead starts at slot 0 so only genesis can be the dependent
    final Spec hezeAtGenesisSpec = TestSpecFactory.createMinimalHeze();
    final SignedInclusionListValidator genesisEpochValidator =
        new SignedInclusionListValidator(
            hezeAtGenesisSpec,
            recentChainData,
            gossipValidationHelper,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    final UInt64 genesisEpochSlot = UInt64.valueOf(2);
    final Bytes32 slotOneRoot = Bytes32.fromHexStringLenient("0x02");
    final BeaconState slotOneState =
        hezeAtGenesisSpec.processSlots(
            ChainBuilder.create(hezeAtGenesisSpec, VALIDATOR_KEYS).generateGenesis().getState(),
            UInt64.ONE);
    when(gossipValidationHelper.isSlotCurrent(genesisEpochSlot)).thenReturn(true);
    mockDependentRoot(slotOneRoot, slotOneState);
    final SchemaDefinitionsHeze genesisSchemaDefinitions =
        SchemaDefinitionsHeze.required(hezeAtGenesisSpec.getGenesisSchemaDefinitions());
    final SignedInclusionList inclusionList =
        genesisSchemaDefinitions
            .getSignedInclusionListSchema()
            .create(
                genesisSchemaDefinitions
                    .getInclusionListSchema()
                    .create(genesisEpochSlot, UInt64.ZERO, slotOneRoot, List.of(transaction(1))),
                BLSSignature.empty());

    assertRejectedWith(
        safeJoin(genesisEpochValidator.validate(inclusionList)),
        "at slot 1, after the shuffling dependent slot 0");
  }

  @Test
  void shouldIgnoreThirdValidMessageFromValidator() {
    assertThat(validate(signedInclusionList(transaction(1)))).isEqualTo(accept());
    assertThat(validate(signedInclusionList(transaction(2)))).isEqualTo(accept());

    assertThat(validate(signedInclusionList(transaction(3))).isIgnore()).isTrue();
  }

  @Test
  void shouldCountValidMessagesPerDependentRoot() {
    assertThat(validate(signedInclusionList(transaction(1)))).isEqualTo(accept());
    assertThat(validate(signedInclusionList(transaction(2)))).isEqualTo(accept());
    final Bytes32 otherRoot = Bytes32.fromHexStringLenient("0x01");
    mockDependentRoot(otherRoot, genesisState);
    final SignedInclusionList otherRootInclusionList =
        signedInclusionList(otherRoot, transaction(3));

    // Two lists already counted for one dependent root don't block another root
    assertThat(validate(otherRootInclusionList)).isEqualTo(accept());
  }

  @Test
  void shouldIgnoreThirdMessageWhenAllThreeWereValidatingConcurrently() {
    // Hold every signature check open so all three lists pass the up-front seen count
    final List<SafeFuture<Boolean>> pendingSignatureChecks = new ArrayList<>();
    final AsyncBLSSignatureVerifier pendingVerifier = mock(AsyncBLSSignatureVerifier.class);
    when(pendingVerifier.verify(any(BLSPublicKey.class), any(), any()))
        .thenAnswer(
            __ -> {
              final SafeFuture<Boolean> result = new SafeFuture<>();
              pendingSignatureChecks.add(result);
              return result;
            });
    final SignedInclusionListValidator concurrentValidator =
        new SignedInclusionListValidator(
            spec, recentChainData, gossipValidationHelper, pendingVerifier);

    final List<SafeFuture<InternalValidationResult>> results =
        List.of(
            concurrentValidator.validate(signedInclusionList(transaction(1))),
            concurrentValidator.validate(signedInclusionList(transaction(2))),
            concurrentValidator.validate(signedInclusionList(transaction(3))));
    assertThat(pendingSignatureChecks).hasSize(3);
    pendingSignatureChecks.forEach(check -> check.complete(true));

    assertThat(safeJoin(results.get(0))).isEqualTo(accept());
    assertThat(safeJoin(results.get(1))).isEqualTo(accept());
    assertThat(safeJoin(results.get(2)).isIgnore()).isTrue();
  }

  @Test
  void shouldNotCountInvalidMessages() {
    final SignedInclusionList validInclusionList = signedInclusionList(transaction(1));
    final SignedInclusionList invalidSignature =
        schemaDefinitions
            .getSignedInclusionListSchema()
            .create(signedInclusionList(transaction(2)).getMessage(), BLSSignature.empty());

    assertThat(validate(invalidSignature).isReject()).isTrue();
    assertThat(validate(invalidSignature).isReject()).isTrue();

    assertThat(validate(validInclusionList)).isEqualTo(accept());
  }

  @Test
  void shouldIgnoreInclusionListNotForCurrentSlot() {
    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(false);

    assertThat(validate(signedInclusionList(transaction(1))).isIgnore()).isTrue();
    verify(gossipValidationHelper, never()).getStateAtBlockRoot(any());
  }

  @Test
  void shouldIgnoreInclusionListWithNoTransactions() {
    final InternalValidationResult result = validate(signedInclusionList());

    assertThat(result.isIgnore()).isTrue();
    assertThat(result.getDescription())
        .hasValueSatisfying(description -> assertThat(description).contains("no transactions"));
  }

  @Test
  void shouldRejectEmptyTransaction() {
    assertThat(validate(signedInclusionList(transaction(1), emptyTransaction())).isReject())
        .isTrue();
  }

  @Test
  void shouldRejectTransactionsExceedingMaximumSize() {
    final int maxSize =
        SpecConfigHeze.required(spec.atSlot(slot).getConfig())
            .getMaxTransactionsBytesPerInclusionList();
    final Transaction oversizedTransaction =
        schemaDefinitions
            .getExecutionPayloadSchema()
            .getTransactionSchema()
            .fromBytes(Bytes.wrap(new byte[maxSize + 1]));

    assertThat(validate(signedInclusionList(oversizedTransaction)).isReject()).isTrue();
  }

  @Test
  void shouldIgnoreNotCurrentSlotBeforeRejectingTransactions() {
    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(false);

    // The spec checks the slot before the transactions, so this is ignored, not rejected
    assertThat(validate(signedInclusionList(transaction(1), emptyTransaction())).isIgnore())
        .isTrue();
  }

  @Test
  void shouldIgnoreSeenCountBeforeRejectingTransactions() {
    assertThat(validate(signedInclusionList(transaction(1)))).isEqualTo(accept());
    assertThat(validate(signedInclusionList(transaction(2)))).isEqualTo(accept());

    // The spec checks the seen count before the transactions, so this is ignored, not rejected
    assertThat(validate(signedInclusionList(transaction(3), emptyTransaction())).isIgnore())
        .isTrue();
  }

  private InternalValidationResult validate(final SignedInclusionList signedInclusionList) {
    return safeJoin(validator.validate(signedInclusionList));
  }

  private static InternalValidationResult accept() {
    return InternalValidationResult.ACCEPT;
  }

  private Transaction transaction(final int value) {
    return schemaDefinitions
        .getExecutionPayloadSchema()
        .getTransactionSchema()
        .fromBytes(Bytes.of(value));
  }

  private Transaction emptyTransaction() {
    return schemaDefinitions
        .getExecutionPayloadSchema()
        .getTransactionSchema()
        .fromBytes(Bytes.EMPTY);
  }

  private InclusionListUtil inclusionListUtil() {
    return spec.atSlot(slot).getInclusionListUtil().orElseThrow();
  }

  private SignedInclusionList signedInclusionList(final Transaction... transactions) {
    return signedInclusionList(dependentRoot, transactions);
  }

  /** Makes {@code root} a known, validated and possible dependent block. */
  private void mockDependentRoot(final Bytes32 root, final BeaconState dependentBlockState) {
    when(gossipValidationHelper.isBlockAvailable(root)).thenReturn(true);
    when(gossipValidationHelper.getStateAtBlockRoot(root))
        .thenReturn(SafeFuture.completedFuture(Optional.of(dependentBlockState)));
    when(gossipValidationHelper.isPossibleDependentRoot(eq(root), any())).thenReturn(true);
    when(recentChainData.retrieveCheckpointState(new Checkpoint(lookaheadEpoch, root)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(lookaheadState)));
  }

  private static void assertRejectedWith(
      final InternalValidationResult result, final String expectedDescription) {
    assertThat(result.isReject()).isTrue();
    assertThat(result.getDescription())
        .hasValueSatisfying(description -> assertThat(description).contains(expectedDescription));
  }

  private static void assertIgnoredWith(
      final InternalValidationResult result, final String expectedDescription) {
    assertThat(result.isIgnore()).isTrue();
    assertThat(result.getDescription())
        .hasValueSatisfying(description -> assertThat(description).contains(expectedDescription));
  }

  private SignedInclusionList signedInclusionList(
      final Bytes32 dependentRoot, final Transaction... transactions) {
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(slot, UInt64.valueOf(committeeMember), dependentRoot, List.of(transactions));
    final ForkInfo forkInfo =
        new ForkInfo(
            spec.fork(spec.computeEpochAtSlot(slot)), genesisState.getGenesisValidatorsRoot());
    final BLSSignature signature =
        safeJoin(
            new LocalSigner(spec, VALIDATOR_KEYS.get(committeeMember), SyncAsyncRunner.SYNC_RUNNER)
                .signInclusionList(inclusionList, forkInfo));
    return schemaDefinitions.getSignedInclusionListSchema().create(inclusionList, signature);
  }
}
