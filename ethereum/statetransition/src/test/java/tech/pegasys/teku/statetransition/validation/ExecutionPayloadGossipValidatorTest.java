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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ExecutionPayloadGossipValidatorTest {
  private final Spec spec = mock(Spec.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final BlockGossipValidator blockGossipValidator = mock(BlockGossipValidator.class);
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
  private ExecutionPayloadGossipValidator validator;
  private DataStructureUtil dataStructureUtil;

  private SignedExecutionPayloadEnvelope signedEnvelope;
  private ExecutionPayloadEnvelope envelope;
  private UInt64 slot;
  private Bytes32 blockRoot;
  private BeaconBlock beaconBlock;
  private BeaconState postState;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    validator =
        new ExecutionPayloadGossipValidator(
            spec, gossipValidationHelper, blockGossipValidator, invalidBlockRoots);

    slot = dataStructureUtil.randomSlot();
    signedEnvelope = dataStructureUtil.randomSignedExecutionPayloadEnvelope(slot.longValue());
    envelope = signedEnvelope.getMessage();
    slot = envelope.getSlot();
    blockRoot = envelope.getBeaconBlockRoot();
    postState = dataStructureUtil.randomBeaconState();

    final SignedExecutionPayloadBid matchingBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                slot,
                envelope.getBuilderIndex(),
                envelope.getPayload().getBlockHash(),
                envelope.getExecutionRequests().hashTreeRoot()));
    beaconBlock =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                builder -> builder.signedExecutionPayloadBid(matchingBid)));

    when(gossipValidationHelper.getSlotForBlockRoot(envelope.getBeaconBlockRoot()))
        .thenReturn(Optional.of(slot));
    when(gossipValidationHelper.isBeforeFinalizedSlot(slot)).thenReturn(false);
    when(gossipValidationHelper.retrieveBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconBlock)));
    when(gossipValidationHelper.getStateAtBlockRoot(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(envelope), any())).thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    // Real config so the execution request and withdrawal limit rules see actual limits
    when(specVersion.getConfig()).thenReturn(specContext.getSpec().atSlot(slot).getConfig());
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
  }

  @TestTemplate
  void shouldAcceptWhenValid() {
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreIfAlreadySeen() {
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore(
                "Already received execution payload envelope with block root %s from builder with index %s",
                blockRoot, envelope.getBuilderIndex()));
  }

  @TestTemplate
  void shouldSaveForFutureIfBlockNotSeen() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldIgnoreIfSlotIsBeforeFinalized() {
    when(gossipValidationHelper.isBeforeFinalizedSlot(slot)).thenReturn(true);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore("Signed execution payload envelope slot %s is before the finalized slot", slot));
  }

  @TestTemplate
  void shouldRejectIfBlockIsMarkedInvalid() {
    invalidBlockRoots.put(blockRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject("Execution payload envelope block with root %s is invalid", blockRoot));
  }

  @TestTemplate
  void shouldRejectIfPayloadSlotIsDifferentFromBlockSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot))
        .thenReturn(Optional.of(slot.plus(1)));
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Execution payload envelope slot %s does not match block slot %s",
                slot, slot.plus(1)));
  }

  @TestTemplate
  void shouldRejectIfBuilderIndexMismatch() {
    final SignedExecutionPayloadBid mismatchedBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                slot,
                envelope.getBuilderIndex().plus(1),
                envelope.getPayload().getBlockHash(),
                envelope.getExecutionRequests().hashTreeRoot()));
    final BeaconBlock blockWithMismatchedBid =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                builder -> builder.signedExecutionPayloadBid(mismatchedBid)));
    when(gossipValidationHelper.retrieveBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithMismatchedBid)));

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid builder index. Execution payload envelope had %s but the block execution payload bid had %s",
                envelope.getBuilderIndex(), envelope.getBuilderIndex().plus(1)));
  }

  @TestTemplate
  void shouldRejectIfPayloadHashMismatch() {
    final SignedExecutionPayloadBid mismatchedBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                slot,
                envelope.getBuilderIndex(),
                dataStructureUtil.randomBytes32(),
                envelope.getExecutionRequests().hashTreeRoot()));
    final BeaconBlock blockWithMismatchedBid =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                builder -> builder.signedExecutionPayloadBid(mismatchedBid)));
    when(gossipValidationHelper.retrieveBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithMismatchedBid)));

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload block hash. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                envelope.getPayload().getBlockHash(), mismatchedBid.getMessage().getBlockHash()));
  }

  @TestTemplate
  void shouldRejectIfExecutionRequestsMismatch() {
    final SignedExecutionPayloadBid mismatchedBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            dataStructureUtil.randomExecutionPayloadBid(
                slot,
                envelope.getBuilderIndex(),
                envelope.getPayload().getBlockHash(),
                dataStructureUtil.randomBytes32()));
    final BeaconBlock blockWithMismatchedBid =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                builder -> builder.signedExecutionPayloadBid(mismatchedBid)));
    when(gossipValidationHelper.retrieveBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithMismatchedBid)));

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid execution requests. Execution Payload Envelope had execution requests root of %s but ExecutionPayload Bid had %s",
                envelope.getExecutionRequests().hashTreeRoot(),
                mismatchedBid.getMessage().getExecutionRequestsRoot()));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldSaveForFutureIfStateIsUnavailable() {
    when(gossipValidationHelper.getStateAtBlockRoot(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));

    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreIfAlreadySeenWithGossipLevel() {
    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.GOSSIP)))
        .isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore(
                "Already received execution payload envelope with block root %s from builder with index %s",
                blockRoot, envelope.getBuilderIndex()));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithGossipLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.GOSSIP)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithConsensusBroadcastValidationLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithConsensusAndEquivocationBroadcastValidationLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfBeaconBlockIsEquivocationWithConsensusAndEquivocationLevel() {
    when(blockGossipValidator.isBlockEquivocating(
            beaconBlock.getSlot(), beaconBlock.getProposerIndex(), beaconBlock.getRoot()))
        .thenReturn(true);

    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(
            reject(
                "Execution payload envelope's beacon block with root %s is an equivocation",
                blockRoot));
  }

  @TestTemplate
  void shouldAcceptIfBeaconBlockIsNotEquivocationWithConsensusAndEquivocationLevel() {
    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(ACCEPT);
  }
}
