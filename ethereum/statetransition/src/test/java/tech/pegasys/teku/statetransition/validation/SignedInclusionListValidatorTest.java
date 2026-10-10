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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
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

  private final UInt64 slot = spec.computeStartSlotAtEpoch(HEZE_FORK_EPOCH);
  private BeaconState state;
  private int committeeMember;

  @BeforeEach
  void setUp() throws Exception {
    final BeaconState genesisState =
        ChainBuilder.create(spec, VALIDATOR_KEYS).generateGenesis().getState();
    // The block in effect at the first Heze slot is still pre-fork
    state = spec.processSlots(genesisState, slot.decrement());
    committeeMember = inclusionListUtil().getInclusionListCommittee(state, slot).getInt(0);
    when(recentChainData.retrieveStateInEffectAtSlot(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(true);
  }

  @Test
  void shouldAcceptValidInclusionListForFirstHezeSlotWhenBlockInEffectIsPreFork() {
    assertThat(state.getFork()).isNotEqualTo(spec.fork(HEZE_FORK_EPOCH));

    assertThat(validate(signedInclusionList(transaction(1)))).isEqualTo(accept());
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
    final SignedInclusionList otherRootInclusionList =
        signedInclusionList(Bytes32.fromHexStringLenient("0x01"), transaction(3));

    // Two lists already counted for the head's dependent root don't block another root. That list
    // is then ignored by the head-branch dependent root check, not the seen count.
    assertThat(validate(otherRootInclusionList).getDescription())
        .hasValueSatisfying(
            description -> assertThat(description).contains("dependent root mismatch"));
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
    verify(recentChainData, never()).retrieveStateInEffectAtSlot(any());
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
    return signedInclusionList(spec.getInclusionListDependentRoot(state, slot), transactions);
  }

  private SignedInclusionList signedInclusionList(
      final Bytes32 dependentRoot, final Transaction... transactions) {
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(slot, UInt64.valueOf(committeeMember), dependentRoot, List.of(transactions));
    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(spec.computeEpochAtSlot(slot)), state.getGenesisValidatorsRoot());
    final BLSSignature signature =
        safeJoin(
            new LocalSigner(spec, VALIDATOR_KEYS.get(committeeMember), SyncAsyncRunner.SYNC_RUNNER)
                .signInclusionList(inclusionList, forkInfo));
    return schemaDefinitions.getSignedInclusionListSchema().create(inclusionList, signature);
  }
}
