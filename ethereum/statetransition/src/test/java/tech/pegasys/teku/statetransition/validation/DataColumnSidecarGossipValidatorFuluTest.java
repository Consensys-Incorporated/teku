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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil.InclusionProofInfo;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.spec.logic.common.util.FuluTrackingKey;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnSidecarGossipValidatorFuluTest
    extends AbstractDataColumnSidecarGossipValidatorTest {

  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);

  private UInt64 parentSlot;
  private Bytes32 blockParentRoot;
  private SignedBeaconBlock signedBeaconBlock;

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalFulu(configAdapter);
  }

  @BeforeEach
  protected void setupForkSpecific() {
    final Spec spec =
        createSpec(builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
    this.dataStructureUtil = new DataStructureUtil(spec);

    this.dataColumnSidecarGossipValidator =
        DataColumnSidecarGossipValidator.create(
            spec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);

    parentSlot = UInt64.valueOf(1);
    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    blockParentRoot = dataStructureUtil.randomBytes32();

    signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(slot.longValue(), blockParentRoot);

    dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock, index);

    // Default mocks for ACCEPT
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(true);
    when(gossipValidationHelper.isBlockAvailable(any(Bytes32.class))).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(any(Bytes32.class)))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));
    when(gossipValidationHelper.isProposerTheExpectedProposer(any())).thenReturn(true);
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(any())).thenReturn(true);
    // Not required for FULU
    when(gossipValidationHelper.retrieveBlockByRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1));
  }

  @Test
  void shouldSaveForFutureWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @Test
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(
            ignore(
                "DataColumnSidecar slot %s is too old (slot already finalized). Ignoring", slot));
  }

  @Test
  void shouldRejectWhenDataColumnSidecarStructureIsInvalid() {
    final Spec mockSpec = mock(Spec.class);
    final SpecVersion mockSpecVersion = mock(SpecVersion.class);
    final SpecVersion mockGenesisSpec = mock(SpecVersion.class);
    final DataColumnSidecarUtil mockDataColumnSidecarUtil = mock(DataColumnSidecarUtil.class);

    when(mockSpec.atSlot(any(UInt64.class))).thenReturn(mockSpecVersion);
    when(mockSpec.getGenesisSpec()).thenReturn(mockGenesisSpec);
    when(mockGenesisSpec.getSlotsPerEpoch()).thenReturn(32);
    when(mockSpec.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(mockSpec.getDataColumnSidecarUtil(any(UInt64.class)))
        .thenReturn(mockDataColumnSidecarUtil);

    when(mockDataColumnSidecarUtil.verifyDataColumnSidecarStructure(any(DataColumnSidecar.class)))
        .thenReturn(false);

    final DataColumnSidecarGossipValidator validatorWithMockedSpec =
        DataColumnSidecarGossipValidator.create(
            mockSpec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(any()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(reject("DataColumnSidecar block header signature is invalid"));

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldIgnoreWhenParentIsNotAvailableSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(SAVE_FOR_FUTURE);

    assertValidationMetrics(Map.of(ValidationResultCode.SAVE_FOR_FUTURE, 1));
  }

  @Test
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldRejectIfFinalizedCheckpointIsNotAnAncestorOfDataColumnSidecarsBlock() {
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecar.getSlot(),
            DataColumnSidecarFulu.required(dataColumnSidecar)
                .getSignedBlockHeader()
                .getMessage()
                .getParentRoot()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    // First validation - should accept and add tracking key to the set
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Second validation with same sidecar - should ignore
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(
            ignore("DataColumnSidecar block header state at slot %s wasn't available.", slot));

    assertValidationMetrics(Map.of(ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(any())).thenReturn(false);

    final UInt64 proposerIndex =
        DataColumnSidecarFulu.required(dataColumnSidecar)
            .getSignedBlockHeader()
            .getMessage()
            .getProposerIndex();

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(
            reject(
                "DataColumnSidecar block header proposed by incorrect proposer with index %s",
                proposerIndex));

    assertValidationMetrics(Map.of(ValidationResultCode.REJECT, 1));
  }

  @Test
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldIgnoreImmediatelyWhenDataColumnFromValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify state-related validations were called
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any());
    verify(gossipValidationHelper).isSignatureValidWithRespectToProposerIndex(any());
    clearInvocations(gossipValidationHelper);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    // Second attempt should be ignored without state validations
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any());
    verify(gossipValidationHelper, never()).isSignatureValidWithRespectToProposerIndex(any());

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 1, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldNotValidateBlockForFulu() {
    // In Fulu, sidecars contain signed headers, so no block retrieval needed
    // validateWithBlock returns empty immediately
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify that block retrieval was never called (Fulu has headers in sidecar)
    verify(gossipValidationHelper, never()).retrieveBlockByRoot(any());
  }

  @Test
  void shouldNotVerifyKnownValidSignedHeader() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify full validation was performed
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any());
    verify(gossipValidationHelper).isSignatureValidWithRespectToProposerIndex(any());
    clearInvocations(gossipValidationHelper);

    // Other DataColumnSidecar from the same block (with valid inclusion proof)
    final DataColumnSidecar dataColumnSidecar0 =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock, UInt64.ZERO);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Second sidecar from same block should skip state validations (cached header)
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any());
    verify(gossipValidationHelper, never()).isSignatureValidWithRespectToProposerIndex(any());
    clearInvocations(gossipValidationHelper);

    // DataColumnSidecar from the new block (with valid inclusion proof)
    final SignedBeaconBlock newSignedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final DataColumnSidecar dataColumnSidecarNew =
        dataStructureUtil.randomDataColumnSidecar(newSignedBlock, UInt64.ZERO);
    final Bytes32 parentRoot =
        DataColumnSidecarFulu.required(dataColumnSidecarNew)
            .getSignedBlockHeader()
            .getMessage()
            .getParentRoot();

    when(gossipValidationHelper.isSlotFinalized(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(dataColumnSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(dataColumnSidecarNew.getBeaconBlockRoot()))
        .thenReturn(true);
    when(gossipValidationHelper.isBlockAvailable(parentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            dataColumnSidecarNew.getSlot(), parentRoot))
        .thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecarNew))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    // Verify state validation was attempted
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any());

    assertValidationMetrics(Map.of(ValidationResultCode.ACCEPT, 2, ValidationResultCode.IGNORE, 1));
  }

  @Test
  void shouldRejectWhenInclusionProofIsInvalid() {
    final Spec mockSpec = mock(Spec.class);
    final SpecVersion mockSpecVersion = mock(SpecVersion.class);
    final SpecVersion mockGenesisSpec = mock(SpecVersion.class);
    final DataColumnSidecarUtil mockDataColumnSidecarUtil = mock(DataColumnSidecarUtil.class);

    when(mockSpec.atSlot(any(UInt64.class))).thenReturn(mockSpecVersion);
    when(mockSpec.getGenesisSpec()).thenReturn(mockGenesisSpec);
    when(mockGenesisSpec.getSlotsPerEpoch()).thenReturn(32);
    when(mockSpec.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(mockSpec.getDataColumnSidecarUtil(any(UInt64.class)))
        .thenReturn(mockDataColumnSidecarUtil);

    when(mockDataColumnSidecarUtil.verifyDataColumnSidecarStructure(any(DataColumnSidecar.class)))
        .thenReturn(true);
    when(mockDataColumnSidecarUtil.getInclusionProofCacheKey(any(DataColumnSidecar.class)))
        .thenReturn(Optional.empty());
    // Inclusion proof verification fails
    when(mockDataColumnSidecarUtil.verifyInclusionProof(any(DataColumnSidecar.class)))
        .thenReturn(false);
    when(mockDataColumnSidecarUtil.performSlotTimingValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.performSlotFinalizationValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.isBlockParentSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.isBlockSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.validateBlockSlot(any(), any())).thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateParentBlock(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateWithState(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(mockDataColumnSidecarUtil.validateAndVerifyKzgProofs(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(mockDataColumnSidecarUtil.extractTrackingKey(any()))
        .thenReturn(new FuluTrackingKey(slot, UInt64.ZERO, index));

    final DataColumnSidecarGossipValidator validatorWithMockedSpec =
        DataColumnSidecarGossipValidator.create(
            mockSpec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValue(reject("DataColumnSidecar inclusion proof validation failed"));
  }

  @Test
  void shouldRejectWhenKzgProofsAreInvalid() {
    final Spec mockSpec = mock(Spec.class);
    final SpecVersion mockSpecVersion = mock(SpecVersion.class);
    final SpecVersion mockGenesisSpec = mock(SpecVersion.class);
    final DataColumnSidecarUtil mockDataColumnSidecarUtil = mock(DataColumnSidecarUtil.class);

    when(mockSpec.atSlot(any(UInt64.class))).thenReturn(mockSpecVersion);
    when(mockSpec.getGenesisSpec()).thenReturn(mockGenesisSpec);
    when(mockGenesisSpec.getSlotsPerEpoch()).thenReturn(32);
    when(mockSpec.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(mockSpec.getDataColumnSidecarUtil(any(UInt64.class)))
        .thenReturn(mockDataColumnSidecarUtil);
    when(mockDataColumnSidecarUtil.verifyDataColumnSidecarStructure(any(DataColumnSidecar.class)))
        .thenReturn(true);
    when(mockDataColumnSidecarUtil.getInclusionProofCacheKey(any(DataColumnSidecar.class)))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.verifyInclusionProof(any(DataColumnSidecar.class)))
        .thenReturn(true);
    when(mockDataColumnSidecarUtil.performSlotTimingValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.performSlotFinalizationValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.isBlockParentSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.isBlockSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.validateBlockSlot(any(), any())).thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateParentBlock(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateWithState(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    // KZG proof validation fails
    when(mockDataColumnSidecarUtil.validateAndVerifyKzgProofs(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    DataColumnSidecarValidationError.Critical.format(
                        "Invalid DataColumnSidecar KZG Proofs"))));
    when(mockDataColumnSidecarUtil.extractTrackingKey(any()))
        .thenReturn(new FuluTrackingKey(slot, UInt64.ZERO, index));

    final DataColumnSidecarGossipValidator validatorWithMockedSpec =
        DataColumnSidecarGossipValidator.create(
            mockSpec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValue(reject("Invalid DataColumnSidecar KZG Proofs"));
  }

  @Test
  void shouldSkipInclusionProofVerificationForKnownValidInclusionProof() {
    final Spec mockSpec = mock(Spec.class);
    final SpecVersion mockGenesisSpec = mock(SpecVersion.class);
    final DataColumnSidecarUtil mockDataColumnSidecarUtil = mock(DataColumnSidecarUtil.class);

    when(mockSpec.getGenesisSpec()).thenReturn(mockGenesisSpec);
    when(mockGenesisSpec.getSlotsPerEpoch()).thenReturn(32);
    when(mockSpec.getNumberOfDataColumns()).thenReturn(Optional.of(128));
    when(mockSpec.getDataColumnSidecarUtil(any(UInt64.class)))
        .thenReturn(mockDataColumnSidecarUtil);

    final InclusionProofInfo inclusionProofInfo =
        new InclusionProofInfo(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    when(mockDataColumnSidecarUtil.verifyDataColumnSidecarStructure(any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.getInclusionProofCacheKey(any()))
        .thenReturn(Optional.of(inclusionProofInfo));
    when(mockDataColumnSidecarUtil.verifyInclusionProof(any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.performSlotTimingValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.performSlotFinalizationValidation(any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.isBlockParentSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.isBlockSeen(any(), any())).thenReturn(true);
    when(mockDataColumnSidecarUtil.validateBlockSlot(any(), any())).thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateParentBlock(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(mockDataColumnSidecarUtil.validateWithState(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              final Set<InclusionProofInfo> proofInfoSet = invocation.getArgument(2);
              proofInfoSet.add(inclusionProofInfo);
              return SafeFuture.completedFuture(Optional.empty());
            });
    when(mockDataColumnSidecarUtil.validateAndVerifyKzgProofs(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(mockDataColumnSidecarUtil.extractTrackingKey(any()))
        .thenReturn(new FuluTrackingKey(slot, UInt64.ZERO, index));

    final DataColumnSidecarGossipValidator validatorWithMockedSpec =
        DataColumnSidecarGossipValidator.create(
            mockSpec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);

    // First validation, inclusion proof is verified and cached
    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(mockDataColumnSidecarUtil).verifyInclusionProof(any());
    clearInvocations(mockDataColumnSidecarUtil);

    // Second sidecar from same block but different index. Inclusion proof is already cached
    when(mockDataColumnSidecarUtil.extractTrackingKey(any()))
        .thenReturn(new FuluTrackingKey(slot, UInt64.ZERO, UInt64.valueOf(2)));

    SafeFutureAssert.assertThatSafeFuture(validatorWithMockedSpec.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // verifyInclusionProof should not be called
    verify(mockDataColumnSidecarUtil, never()).verifyInclusionProof(any());
  }
}
