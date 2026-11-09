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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA})
public class BlobSidecarGossipValidatorTest {
  private final Map<Bytes32, BlockImportResult> invalidBlocks = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final MiscHelpersDeneb miscHelpersDeneb = mock(MiscHelpersDeneb.class);
  private DataStructureUtil dataStructureUtil;
  private BlobSidecarGossipValidator blobSidecarValidator;

  private UInt64 parentSlot;
  private BeaconState postState;

  private UInt64 slot;
  private UInt64 index;
  private UInt64 proposerIndex;
  private Bytes32 blockRoot;
  private Bytes32 blockParentRoot;

  private BlobSidecar blobSidecar;

  @BeforeEach
  void setup(final SpecContext specContext) {
    this.dataStructureUtil = specContext.getDataStructureUtil();

    blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            specContext.getSpec(), invalidBlocks, gossipValidationHelper, miscHelpersDeneb);

    parentSlot = UInt64.valueOf(1);

    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    proposerIndex = UInt64.valueOf(3);
    blockRoot = dataStructureUtil.randomBytes32();
    blockParentRoot = dataStructureUtil.randomBytes32();

    final BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, blockParentRoot, dataStructureUtil.randomBytes32(), blockRoot);
    blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(
                new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
            .index(index)
            .build();

    postState = dataStructureUtil.randomBeaconState();

    // default validate ACCEPT
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(true);
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(slot, blockParentRoot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(true);
    when(miscHelpersDeneb.verifyBlobKzgProof(any(BlobSidecar.class))).thenReturn(true);
    when(miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(any())).thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    assertThat(blobSidecar.isSignatureValidated()).isTrue();
  }

  @TestTemplate
  void shouldRejectWhenIndexIsTooBig(final SpecContext specContext) {
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();
    final BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, blockParentRoot, dataStructureUtil.randomBytes32(), blockRoot);
    blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(
                new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
            .index(
                UInt64.valueOf(
                    specContext.getSpec().getMaxBlobsPerBlockForHighestMilestone().orElseThrow()))
            .build();

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenSlotIsNotDeneb() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();

    blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            spec, invalidBlocks, gossipValidationHelper, miscHelpersDeneb);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotIsFromFuture() {
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreWhenSlotAlreadyFinalized() {
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_blockRoot() {
    when(gossipValidationHelper.isBlockAvailable(blockParentRoot)).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), eq(proposerIndex), any(), eq(postState)))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    assertThat(blobSidecar.isSignatureValidated()).isFalse();
  }

  @TestTemplate
  void shouldIgnoreWhenParentIsNotAvailable_slot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot)).thenReturn(Optional.empty());

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldRejectWhenParentBlockInvalid() {
    invalidBlocks.put(blockParentRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenParentSlotIsGreater() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockParentRoot))
        .thenReturn(Optional.of(parentSlot.plus(1)));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfFinalizedCheckpointIsNotAnAncestorOfBlobSidecarsBlock() {
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            blobSidecar.getSlot(),
            blobSidecar.getSignedBeaconBlockHeader().getMessage().getParentRoot()))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectWhenInclusionProofFailsValidation() {
    when(miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(any())).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldRejectIfKzgVerificationFailed() {
    when(miscHelpersDeneb.verifyBlobKzgProof(any(BlobSidecar.class))).thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldIgnoreWhenIsNotFirstValidSignature() {
    blobSidecarValidator
        .getReceivedValidBlobSidecarInfoSet()
        .add(
            new BlobSidecarGossipValidator.SlotProposerIndexAndBlobIndex(
                slot, proposerIndex, index));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreIfStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(parentSlot, blockParentRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectIfProposerIndexIsWrong() {
    when(gossipValidationHelper.isProposerTheExpectedProposer(proposerIndex, slot, postState))
        .thenReturn(false);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @TestTemplate
  void shouldTrackValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldMarkForEquivocation() {
    assertThat(blobSidecarValidator.markForEquivocation(blobSidecar)).isTrue();

    assertThat(blobSidecarValidator.markForEquivocation(blobSidecar)).isFalse();

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreImmediatelyWhenBlobFromValidInfoSet() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersDeneb).verifyBlobKzgCommitmentInclusionProof(blobSidecar);
    verify(miscHelpersDeneb).verifyBlobKzgProof(blobSidecar);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);
    clearInvocations(miscHelpersDeneb);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    verify(miscHelpersDeneb, never()).verifyBlobKzgCommitmentInclusionProof(blobSidecar);
    verify(miscHelpersDeneb, never()).verifyBlobKzgProof(blobSidecar);
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldNotVerifyKnownValidSignedHeader() {
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersDeneb).verifyBlobKzgCommitmentInclusionProof(blobSidecar);
    verify(miscHelpersDeneb).verifyBlobKzgProof(blobSidecar);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // Other BlobSidecar from the same block
    final BlobSidecar blobSidecar0 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(blobSidecar.getSignedBeaconBlockHeader())
            .index(ZERO)
            .build();

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(miscHelpersDeneb).verifyBlobKzgCommitmentInclusionProof(blobSidecar0);
    verify(miscHelpersDeneb).verifyBlobKzgProof(blobSidecar0);
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());

    assertThat(blobSidecar.isSignatureValidated()).isTrue();

    clearInvocations(gossipValidationHelper);

    // BlobSidecar from the new block
    final BlobSidecar blobSidecarNew =
        dataStructureUtil.createRandomBlobSidecarBuilder().index(ZERO).build();
    final Bytes32 parentRoot =
        blobSidecarNew.getSignedBeaconBlockHeader().getMessage().getParentRoot();

    when(gossipValidationHelper.isSlotFinalized(blobSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(blobSidecarNew.getSlot())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(parentRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentRoot))
        .thenReturn(Optional.of(parentSlot));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            parentSlot, parentRoot, blobSidecarNew.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
            blobSidecarNew.getSlot(), parentRoot))
        .thenReturn(true);

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecarNew))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);

    verify(miscHelpersDeneb).verifyBlobKzgCommitmentInclusionProof(blobSidecarNew);
    verify(miscHelpersDeneb).verifyBlobKzgProof(blobSidecarNew);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
  }

  @TestTemplate
  void shouldVerifySignedHeaderAgainAfterItDroppedFromCache(final SpecContext specContext) {
    final Spec specMock = mock(Spec.class);
    final SpecVersion specVersion = mock(SpecVersion.class);
    when(specMock.atSlot(any())).thenReturn(specVersion);
    when(specMock.getGenesisSpec()).thenReturn(specVersion);
    when(specVersion.getConfig()).thenReturn(specContext.getSpec().getGenesisSpecConfig());
    // This will make cache of size 3
    when(specVersion.getSlotsPerEpoch()).thenReturn(1);
    when(specMock.getMaxBlobsPerBlockAtSlot(any()))
        .thenReturn(specContext.getSpec().getMaxBlobsPerBlockAtSlot(ZERO));
    this.blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            specMock, invalidBlocks, gossipValidationHelper, miscHelpersDeneb);
    // Accept everything
    when(gossipValidationHelper.isSlotFinalized(any())).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(any())).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(any())).thenReturn(true);
    when(gossipValidationHelper.getParentStateInBlockEpoch(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isProposerTheExpectedProposer(any(), any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(any(), any()))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // First blobSidecar
    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    clearInvocations(gossipValidationHelper);

    // Other BlobSidecar from the same block, known valid block header is detected, so short
    // validation is used
    final BlobSidecar blobSidecar0 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(blobSidecar.getSignedBeaconBlockHeader())
            .index(ZERO)
            .build();

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar0))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 2nd block BlobSidecar
    final BlobSidecar blobSidecar2 = dataStructureUtil.randomBlobSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(blobSidecar2.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar2))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 3rd block BlobSidecar
    final BlobSidecar blobSidecar3 = dataStructureUtil.randomBlobSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(blobSidecar3.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar3))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // 4th block BlobSidecar, erasing block from blobSidecar0 from cache
    final BlobSidecar blobSidecar4 = dataStructureUtil.randomBlobSidecar();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(blobSidecar4.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar4))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    // BlobSidecar from the same block as blobSidecar0 and blobSidecar
    final BlobSidecar blobSidecar5 =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(blobSidecar.getSignedBeaconBlockHeader())
            .index(UInt64.valueOf(2))
            .build();
    when(gossipValidationHelper.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(blobSidecar5.getSlot().decrement()));

    SafeFutureAssert.assertThatSafeFuture(blobSidecarValidator.validate(blobSidecar5))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Signature is validating again though header was known valid until dropped from cache
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }
}
