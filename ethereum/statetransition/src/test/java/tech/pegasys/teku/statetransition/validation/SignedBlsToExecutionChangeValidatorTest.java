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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.OperationSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.storage.client.RecentChainData;

class SignedBlsToExecutionChangeValidatorTest {

  private final Spec spec = spy(TestSpecFactory.createMinimalCapella());
  private final TimeProvider timeProvider = new SystemTimeProvider();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final SignatureVerificationService signatureVerificationService =
      mock(SignatureVerificationService.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private SignedBlsToExecutionChangeValidator validator;

  @BeforeEach
  public void beforeEach() {
    Mockito.reset(spec, recentChainData);
    when(recentChainData.getGenesisTime()).thenReturn(ZERO);
    when(recentChainData.getHeadSlot()).thenReturn(UInt64.ONE);
    final BeaconState beaconState = mock(BeaconState.class);
    when(beaconState.getSlot()).thenReturn(ZERO);
    when(recentChainData.getBestState())
        .thenReturn(Optional.of(SafeFuture.completedFuture(beaconState)));
    validator =
        new SignedBlsToExecutionChangeValidator(
            spec, timeProvider, recentChainData, signatureVerificationService);
  }

  @Test
  public void validateFullyShouldAcceptValidMessage() {
    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();
    mockSpecValidationSucceeded(spec);
    mockSignatureVerificationSucceeded(spec);

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validateForGossip(message);

    assertValidationResult(validationResult, ValidationResultCode.ACCEPT);
  }

  @Test
  public void validateFullyShouldRejectMessageIfSpecValidationFails() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final String expectedFailureDescription = "Spec validation failed";
    mockSpecValidationFailed(expectedFailureDescription);
    mockSignatureVerificationSucceeded(spec);

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validateForGossip(signedBlsToExecutionChange);

    assertValidationResult(
        validationResult, ValidationResultCode.REJECT, expectedFailureDescription);
  }

  @Test
  public void validateForBlockInclusionShouldReturnSpecValidationInvalidReasonWhenInvalid() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final String expectedFailureDescription = "Spec validation failed";
    mockSpecValidationFailed(expectedFailureDescription);

    final Optional<OperationInvalidReason> maybeOperationInvalidReason =
        validator.validateForBlockInclusion(mock(BeaconState.class), signedBlsToExecutionChange);

    assertThat(maybeOperationInvalidReason).isNotEmpty();
    assertThat(maybeOperationInvalidReason.get().describe()).contains(expectedFailureDescription);
  }

  @Test
  public void validateForBlockInclusionShouldReturnEmptyIfSpecValidationSucceeds() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    mockSpecValidationSucceeded(spec);
    mockSignatureVerificationSucceeded(spec);

    final Optional<OperationInvalidReason> maybeOperationInvalidReason =
        validator.validateForBlockInclusion(mock(BeaconState.class), signedBlsToExecutionChange);

    assertThat(maybeOperationInvalidReason).isEmpty();
  }

  @Test
  void validateForGossipShouldIgnoreGossipBeforeCapella() {
    final Spec localSpec = TestSpecFactory.createMinimalBellatrix();
    validator =
        new SignedBlsToExecutionChangeValidator(
            localSpec, timeProvider, recentChainData, signatureVerificationService);
    SignedBlsToExecutionChange change = dataStructureUtil.randomSignedBlsToExecutionChange();

    final SafeFuture<InternalValidationResult> future = validator.validateForGossip(change);
    assertThat(future)
        .isCompletedWithValue(
            InternalValidationResult.create(
                IGNORE,
                "BlsToExecutionChange arrived before Capella and was ignored for validator 657086."));
  }

  @Test
  void validateForGossipShouldStartAcceptingGossipAfterCapellaUpgrade() {
    final UInt64 capellaActivationEpoch = UInt64.ONE;
    final Spec localSpec =
        spy(TestSpecFactory.createMinimalWithCapellaForkEpoch(capellaActivationEpoch));
    final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);

    validator =
        new SignedBlsToExecutionChangeValidator(
            localSpec, stubTimeProvider, recentChainData, signatureVerificationService);
    final SignedBlsToExecutionChange change = dataStructureUtil.randomSignedBlsToExecutionChange();

    // Ignore message because Capella has not activated yet
    assertValidationResult(validator.validateForGossip(change), IGNORE);

    // Advance clock to Capella activation epoch
    final long slotsPerEpoch = localSpec.getSlotsPerEpoch(ZERO);
    stubTimeProvider.advanceTimeByMillis(
        capellaActivationEpoch
            .times(slotsPerEpoch * localSpec.getSlotDurationMillis(ZERO))
            .longValue());

    mockSpecValidationSucceeded(localSpec);
    mockSignatureVerificationSucceeded(localSpec);

    // Accept message now that Capella has activated
    assertValidationResult(validator.validateForGossip(change), ValidationResultCode.ACCEPT);
  }

  private void assertValidationResult(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidationResultCode expectedResultCode) {
    assertThat(validationResult)
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }

  private void assertValidationResult(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidationResultCode expectedResultCode,
      final String expectedDescription) {
    assertThat(validationResult)
        .isCompletedWithValueMatching(
            result ->
                result.code() == expectedResultCode
                    && result.getDescription().orElseThrow().contains(expectedDescription));
  }

  private void mockSpecValidationSucceeded(final Spec spec) {
    doReturn(Optional.empty())
        .when(spec)
        .validateBlsToExecutionChange(
            any(BeaconState.class), any(), any(BlsToExecutionChange.class));
  }

  private void mockSpecValidationFailed(final String expectedDescription) {
    final OperationInvalidReason expectedInvalidReason = () -> expectedDescription;
    doReturn(Optional.of(expectedInvalidReason))
        .when(spec)
        .validateBlsToExecutionChange(
            any(BeaconState.class), any(), any(BlsToExecutionChange.class));
  }

  private void mockSignatureVerificationSucceeded(final Spec spec) {
    doReturn(true)
        .when(spec)
        .verifyBlsToExecutionChangeSignature(
            any(BeaconState.class),
            any(SignedBlsToExecutionChange.class),
            eq(BLSSignatureVerifier.SIMPLE));

    final SpecVersion specVersion = spy(spec.atSlot(ZERO));
    final OperationSignatureVerifier signatureVerifier = mock(OperationSignatureVerifier.class);

    doReturn(specVersion).when(spec).atSlot(eq(ZERO));
    when(specVersion.operationSignatureVerifier()).thenReturn(signatureVerifier);
    when(signatureVerifier.verifyBlsToExecutionChangeSignatureAsync(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(true));

    doReturn(SafeFuture.completedFuture(true))
        .when(signatureVerificationService)
        .verify(any(BLSPublicKey.class), any(Bytes.class), any(BLSSignature.class));
  }
}
