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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class SignedValidatorRegistrationFactoryTest {

  private final TimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(12);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final Signer signer = mock(Signer.class);

  private Validator validator;
  private Eth1Address feeRecipient;
  private UInt64 gasLimit;
  private SignedValidatorRegistrationFactory signedValidatorRegistrationFactory;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    feeRecipient = dataStructureUtil.randomEth1Address();
    gasLimit = dataStructureUtil.randomUInt64();
    validator = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);

    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(true);
    when(proposerConfigPropertiesProvider.getFeeRecipient(any()))
        .thenReturn(Optional.of(feeRecipient));
    when(proposerConfigPropertiesProvider.getGasLimit(any())).thenReturn(gasLimit);

    when(proposerConfigPropertiesProvider.refresh()).thenReturn(SafeFuture.COMPLETE);

    // random signature for all signings
    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class));

    signedValidatorRegistrationFactory =
        new SignedValidatorRegistrationFactory(proposerConfigPropertiesProvider, stubTimeProvider);
  }

  @TestTemplate
  public void whenCreateCalled_thenSigningRegistrationIsReturned() {
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});

    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result -> result.getMessage().getPublicKey().equals(validator.getPublicKey()));
  }

  @TestTemplate
  public void whenOldRegistrationDoesNotNeedToBeUpdated_thenSignerIsNotCalled() {
    final SignedValidatorRegistration oldSignedValidatorRegistration =
        createSignedValidatorRegistration(validator.getPublicKey(), feeRecipient);
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.of(oldSignedValidatorRegistration), throwable -> {});

    verify(signer, never()).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result -> result.getMessage().getPublicKey().equals(validator.getPublicKey()));
  }

  @TestTemplate
  public void whenOldRegistrationNeedsToBeUpdated_thenSignerIsCalled() {
    // Old registration with another fee recipient
    final SignedValidatorRegistration oldSignedValidatorRegistration =
        createSignedValidatorRegistration(
            validator.getPublicKey(), dataStructureUtil.randomEth1Address());
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.of(oldSignedValidatorRegistration), throwable -> {});

    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator.getPublicKey())
                    && result.getMessage().getFeeRecipient().equals(feeRecipient));
  }

  @TestTemplate
  public void whenSigningIsCompletedExceptionally_thenExceptionIsPropagated() {
    when(signer.signValidatorRegistration(any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("oops")));
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});

    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration).isCompletedExceptionally();
  }

  @TestTemplate
  void shouldRegisterWithTimestampOverride() {
    final UInt64 timestampOverride = dataStructureUtil.randomUInt64();

    when(proposerConfigPropertiesProvider.getBuilderRegistrationTimestampOverride(
            validator.getPublicKey()))
        .thenReturn(Optional.of(timestampOverride));

    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator.getPublicKey())
                    && result.getMessage().getTimestamp().equals(timestampOverride));
  }

  @TestTemplate
  void shouldRegisterWithPublicKeyOverride() {
    final BLSPublicKey publicKeyOverride = dataStructureUtil.randomPublicKey();
    when(proposerConfigPropertiesProvider.getBuilderRegistrationPublicKeyOverride(
            validator.getPublicKey()))
        .thenReturn(Optional.of(publicKeyOverride));

    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result -> result.getMessage().getPublicKey().equals(publicKeyOverride));
  }

  @TestTemplate
  void setsCorrectGasLimitForValidators() {
    final Validator validator2 =
        new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    final UInt64 validator2GasLimit = UInt64.valueOf(27_000_000);

    // validator2 will have custom gas limit
    when(proposerConfigPropertiesProvider.getGasLimit(validator2.getPublicKey()))
        .thenReturn(validator2GasLimit);

    // Verify validator
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator.getPublicKey())
                    && result.getMessage().getGasLimit().equals(gasLimit));

    // Verify validator2
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration2 =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator2, Optional.empty(), throwable -> {});
    verify(signer, times(2)).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration2)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator2.getPublicKey())
                    && result.getMessage().getGasLimit().equals(validator2GasLimit));
  }

  @TestTemplate
  void setsCorrectTimeStampOverrideForValidators() {
    final Validator validator2 =
        new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    final UInt64 validator2Timestamp = UInt64.valueOf(1_000_000);

    // validator2 will have custom gas limit
    when(proposerConfigPropertiesProvider.getBuilderRegistrationTimestampOverride(
            validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2Timestamp));

    // Verify validator
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator.getPublicKey())
                    && result
                        .getMessage()
                        .getTimestamp()
                        .equals(stubTimeProvider.getTimeInSeconds()));

    // Verify validator2
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration2 =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator2, Optional.empty(), throwable -> {});
    verify(signer, times(2)).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration2)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator2.getPublicKey())
                    && result.getMessage().getTimestamp().equals(validator2Timestamp));
  }

  @TestTemplate
  void setsCorrectFeeRecipientForValidators() {
    final Validator validator2 =
        new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    final Eth1Address validator2FeeRecipient = dataStructureUtil.randomEth1Address();

    // validator2 will have custom gas limit
    when(proposerConfigPropertiesProvider.getFeeRecipient(validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2FeeRecipient));

    // Verify validator
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator.getPublicKey())
                    && result.getMessage().getFeeRecipient().equals(feeRecipient));

    // Verify validator2
    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration2 =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator2, Optional.empty(), throwable -> {});
    verify(signer, times(2)).signValidatorRegistration(any());
    assertThat(signedValidatorRegistration2)
        .isCompletedWithValueMatching(
            result ->
                result.getMessage().getPublicKey().equals(validator2.getPublicKey())
                    && result.getMessage().getFeeRecipient().equals(validator2FeeRecipient));
  }

  @TestTemplate
  void throwsWhenFeeRecipientIsNotConfigured() {
    // no fee recipient provided
    when(proposerConfigPropertiesProvider.getFeeRecipient(validator.getPublicKey()))
        .thenReturn(Optional.empty());

    final SafeFuture<SignedValidatorRegistration> signedValidatorRegistration =
        signedValidatorRegistrationFactory.createSignedValidatorRegistration(
            validator, Optional.empty(), throwable -> {});
    verify(signer, never()).signValidatorRegistration(any());
    SafeFutureAssert.assertThatSafeFuture(signedValidatorRegistration)
        .isCompletedExceptionallyWithMessage(
            String.format(
                "Fee recipient not configured for %s. Will skip registering it.",
                validator.getPublicKey()));
  }

  private SignedValidatorRegistration createSignedValidatorRegistration(
      final BLSPublicKey publicKey, final Eth1Address customFeeRecipient) {
    return SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
        VALIDATOR_REGISTRATION_SCHEMA.create(
            customFeeRecipient, gasLimit, stubTimeProvider.getTimeInSeconds(), publicKey),
        dataStructureUtil.randomSignature());
  }
}
