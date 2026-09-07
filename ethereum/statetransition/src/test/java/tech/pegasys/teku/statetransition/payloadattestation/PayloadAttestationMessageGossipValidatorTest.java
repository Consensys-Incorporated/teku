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

package tech.pegasys.teku.statetransition.payloadattestation;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.time.Duration;
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
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class PayloadAttestationMessageGossipValidatorTest {
  private final Spec spec = mock(Spec.class);
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);

  private PayloadAttestationMessageGossipValidator payloadAttestationMessageGossipValidator;

  private PayloadAttestationMessage payloadAttestationMessage;
  private UInt64 slot;
  private UInt64 validatorIndex;
  private Bytes32 blockRoot;
  private BeaconState postState;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    final DataStructureUtil dataStructureUtil = specContext.getDataStructureUtil();
    this.payloadAttestationMessageGossipValidator =
        new PayloadAttestationMessageGossipValidator(
            spec, gossipValidationHelper, invalidBlockRoots);

    payloadAttestationMessage = dataStructureUtil.randomPayloadAttestationMessage();
    slot = payloadAttestationMessage.getData().getSlot();
    validatorIndex = payloadAttestationMessage.getValidatorIndex();
    blockRoot = payloadAttestationMessage.getData().getBeaconBlockRoot();
    postState = dataStructureUtil.randomBeaconState();

    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(true);
    when(gossipValidationHelper.isBlockAvailable(blockRoot)).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(slot));
    when(gossipValidationHelper.getStateAtBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(payloadAttestationMessage.getData()), any()))
        .thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(spec.getPtc(postState, slot)).thenReturn(IntList.of(validatorIndex.intValue()));
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
  }

  @TestTemplate
  void shouldAccept() {
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldAcceptAndStoreAllPtcPositionsForValidator() {
    when(spec.getPtc(postState, slot))
        .thenReturn(
            IntList.of(
                validatorIndex.intValue(),
                1,
                validatorIndex.intValue(),
                2,
                validatorIndex.intValue()));
    final ValidatablePayloadAttestationMessage validatablePayloadAttestationMessage =
        validatablePayloadAttestationMessage();

    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(validatablePayloadAttestationMessage))
        .isCompletedWithValue(ACCEPT);

    assertThat(validatablePayloadAttestationMessage.getPtcPositions())
        .hasValueSatisfying(ptcPositions -> assertThat(ptcPositions).isEqualTo(IntSet.of(0, 2, 4)));
  }

  @TestTemplate
  void shouldIgnore_whenSlotIsNotCurrent() {
    when(gossipValidationHelper.isSlotCurrent(slot)).thenReturn(false);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            ignore(
                "Ignoring payload attestation with slot %s from validator with index %s because it's not from the current slot",
                slot, validatorIndex));
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            ignore(
                "Payload attestation for slot %s and validator index %s already seen",
                slot, validatorIndex));
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen_AfterInitialCheck() {
    final SafeFuture<Optional<BeaconState>> getStateFuture1 = new SafeFuture<>();
    final SafeFuture<Optional<BeaconState>> getStateFuture2 = new SafeFuture<>();
    when(gossipValidationHelper.getStateAtBlockRoot(any()))
        .thenReturn(getStateFuture1)
        .thenReturn(getStateFuture2);

    final SafeFuture<InternalValidationResult> validationFuture1 =
        payloadAttestationMessageGossipValidator.validate(validatablePayloadAttestationMessage());
    final SafeFuture<InternalValidationResult> validationFuture2 =
        payloadAttestationMessageGossipValidator.validate(validatablePayloadAttestationMessage());

    getStateFuture1.complete(Optional.of(postState));
    assertThat(validationFuture1).succeedsWithin(Duration.ofSeconds(10)).isEqualTo(ACCEPT);

    getStateFuture2.complete(Optional.of(postState));
    assertThat(validationFuture2)
        .succeedsWithin(Duration.ofSeconds(10))
        .isEqualTo(
            ignore(
                "Payload attestation for slot %s and validator index %s already seen",
                slot, validatorIndex));
  }

  @TestTemplate
  void shouldSaveForFuture_whenBlockNotAvailable() {
    when(gossipValidationHelper.isBlockAvailable(blockRoot)).thenReturn(false);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldIgnore_whenBlockSlotDoesNotMatchAttestationSlot() {
    final UInt64 blockSlot = slot.plus(1);
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(blockSlot));

    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            ignore(
                "Payload attestations's block with root %s is at slot %s but attestation is for slot %s",
                blockRoot, blockSlot, slot));
  }

  @TestTemplate
  void shouldReject_whenBlockIsInvalid() {
    invalidBlockRoots.put(blockRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            reject("Payload attestations's block with root %s is invalid", blockRoot));
  }

  @TestTemplate
  void shouldSaveForFuture_whenStateIsUnavailable() {
    when(gossipValidationHelper.getStateAtBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldReject_whenValidatorNotInPtcCommittee() {
    when(spec.getPtc(postState, slot)).thenReturn(IntList.of(validatorIndex.intValue() + 1));
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            reject(
                "Payload attestation's validator index %s is not in the payload committee",
                validatorIndex));
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(reject("Invalid payload attestation signature"));
  }

  @TestTemplate
  void shouldSkipSeenPayloadAttestation() {
    // First validation is successful
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(ACCEPT);
    verify(gossipValidationHelper).isBlockAvailable(blockRoot);
    verify(gossipValidationHelper).getStateAtBlockRoot(any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());

    clearInvocations(gossipValidationHelper);

    // Second validation is ignored
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(
            ignore(
                "Payload attestation for slot %s and validator index %s already seen",
                slot, validatorIndex));
    verify(gossipValidationHelper, never()).isBlockAvailable(blockRoot);
    verify(gossipValidationHelper, never()).getStateAtBlockRoot(any());
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    // Fail validation due to bad signature
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    // Fix the signature and try again
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // It should be accepted now
    assertThatSafeFuture(
            payloadAttestationMessageGossipValidator.validate(
                validatablePayloadAttestationMessage()))
        .isCompletedWithValue(ACCEPT);
  }

  private ValidatablePayloadAttestationMessage validatablePayloadAttestationMessage() {
    return ValidatablePayloadAttestationMessage.fromNetwork(payloadAttestationMessage);
  }
}
