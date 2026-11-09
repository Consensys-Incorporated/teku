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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class MergeTransitionBlockValidatorTest {

  private final Bytes32 terminalBlockHash = Bytes32.random();
  private final UInt64 terminalEpoch = UInt64.ZERO;
  private final Spec spec =
      TestSpecFactory.createMinimalBellatrix(
          b -> {
            b.blsSignatureVerifier(BLSSignatureVerifier.NOOP);
            b.bellatrixBuilder(
                bb ->
                    bb.terminalBlockHash(terminalBlockHash)
                        .terminalBlockHashActivationEpoch(terminalEpoch));
          });
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannelStub executionLayer =
      new ExecutionLayerChannelStub(spec, false);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  /**
   * The details of how we validate the merge block are all handled by {@link
   * BellatrixTransitionHelpers} so we don't want to retest all that here. Instead, we check that
   * those extra checks started (the call to {@link
   * ExecutionLayerChannel#eth1GetPowBlock(Bytes32)}).
   *
   * <p>Since the future we return from getPowBlock is never completed we never complete the
   * validations which saves us a bunch of mocking.
   */
  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis(false);
  }

  @Test
  void shouldReturnValidImmediatelyWhenThereIsTransitionBlockFullyVerified() {
    final SignedBlockAndState chainHead = generateNonFinalizedTransition(true);
    storageSystem.chainUpdater().saveBlock(chainHead);
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier = createTransitionValidator();

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isEmpty();

    final SafeFuture<PayloadValidationResult> result =
        transitionVerifier.verifyTransitionBlock(
            BeaconStateBellatrix.required(chainHead.getState())
                .getLatestExecutionPayloadHeaderRequired(),
            blockToVerify.getBlock());

    // No need to request blocks and check TTD
    assertThat(executionLayer.getRequestedPowBlocks()).isEmpty();
    assertThat(result).isCompletedWithValue(new PayloadValidationResult(PayloadStatus.VALID));
  }

  @Test
  void shouldVerifyNonFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateNonFinalizedTransition(true);
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().getLatestBlockAndState();
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier = createTransitionValidator();

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isPresent();

    final SafeFuture<PayloadValidationResult> result =
        transitionVerifier.verifyTransitionBlock(
            BeaconStateBellatrix.required(chainHead.getState())
                .getLatestExecutionPayloadHeaderRequired(),
            blockToVerify.getBlock());

    assertThat(result)
        .isCompletedWithValue(
            new PayloadValidationResult(transitionBlock.getRoot(), PayloadStatus.VALID));
  }

  @Test
  void shouldReportRootForInvalidNonFinalizedAncestorTransitionBlock() {
    final SignedBlockAndState transitionBlock = generateNonFinalizedTransition(false);
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().getLatestBlockAndState();
    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier = createTransitionValidator();

    assertThat(
            storageSystem
                .recentChainData()
                .getForkChoiceStrategy()
                .orElseThrow()
                .getOptimisticallySyncedTransitionBlockRoot(chainHead.getRoot()))
        .isPresent();

    final SafeFuture<PayloadValidationResult> result =
        transitionVerifier.verifyTransitionBlock(
            BeaconStateBellatrix.required(chainHead.getState())
                .getLatestExecutionPayloadHeaderRequired(),
            blockToVerify.getBlock());

    assertThat(result).isCompleted();
    final PayloadValidationResult validationResult = safeJoin(result);
    assertThat(validationResult.getInvalidTransitionBlockRoot())
        .contains(transitionBlock.getRoot());
    assertThat(validationResult.getStatus()).matches(PayloadStatus::hasInvalidStatus);
  }

  @Test
  void shouldVerifyFinalizedAncestorTransitionBlock() {
    generateFinalizedTransition(true);
    final BeaconStateBellatrix chainHeadState =
        storageSystem
            .chainBuilder()
            .getLatestBlockAndState()
            .getState()
            .toVersionBellatrix()
            .orElseThrow();

    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier = createTransitionValidator();

    assertThat(storageSystem.recentChainData().getStore().getFinalizedOptimisticTransitionPayload())
        .isPresent();

    final SafeFuture<PayloadValidationResult> result =
        transitionVerifier.verifyTransitionBlock(
            chainHeadState.getLatestExecutionPayloadHeaderRequired(), blockToVerify.getBlock());

    assertThat(result).isCompletedWithValue(new PayloadValidationResult(PayloadStatus.VALID));
  }

  /**
   * We can't mark a finalized block as invalid or reorg to a valid fork, so if we discover we
   * finalized an invalid transition we have to bail out with {@link
   * tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException}
   */
  @Test
  void shouldFailWithFatalServiceFailuresExceptionWhenFinalizedAncestorTransitionBlockIsInvalid() {
    generateFinalizedTransition(false);
    final BeaconStateBellatrix chainHeadState =
        storageSystem
            .chainBuilder()
            .getLatestBlockAndState()
            .getState()
            .toVersionBellatrix()
            .orElseThrow();

    final SignedBlockAndState blockToVerify = storageSystem.chainBuilder().generateNextBlock();

    final MergeTransitionBlockValidator transitionVerifier = createTransitionValidator();

    assertThat(storageSystem.recentChainData().getStore().getFinalizedOptimisticTransitionPayload())
        .isPresent();

    final SafeFuture<PayloadValidationResult> result =
        transitionVerifier.verifyTransitionBlock(
            chainHeadState.getLatestExecutionPayloadHeaderRequired(), blockToVerify.getBlock());

    assertThatSafeFuture(result).isCompletedExceptionallyWith(FatalServiceFailureException.class);
  }

  private SignedBlockAndState generateNonFinalizedTransition(final boolean isValid) {
    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(5)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final SignedBlockAndState transitionBlock =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                6,
                BlockOptions.create()
                    .setTerminalBlockHash(
                        isValid ? terminalBlockHash : dataStructureUtil.randomBytes32()));
    storageSystem.chainUpdater().saveOptimisticBlock(transitionBlock);

    storageSystem
        .chainBuilder()
        .generateBlocksUpToSlot(39)
        .forEach(storageSystem.chainUpdater()::saveOptimisticBlock);
    final SignedBlockAndState chainHead = storageSystem.chainBuilder().generateBlockAtSlot(40);
    storageSystem.chainUpdater().saveOptimisticBlock(chainHead);
    return transitionBlock;
  }

  private void generateFinalizedTransition(final boolean isValid) {
    generateNonFinalizedTransition(isValid);

    // Finalize between transition block and chain head
    storageSystem.chainUpdater().finalizeEpoch(1);
  }

  private MergeTransitionBlockValidator createTransitionValidator() {
    return new MergeTransitionBlockValidator(spec, storageSystem.recentChainData());
  }
}
