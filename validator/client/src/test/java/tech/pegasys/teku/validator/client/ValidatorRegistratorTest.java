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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = SpecMilestone.BELLATRIX)
class ValidatorRegistratorTest {

  private static final int BATCH_SIZE = 100;
  private static final UInt64 TIMESTAMP = ONE;

  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final SignedValidatorRegistrationFactory signedValidatorRegistrationFactory =
      mock(SignedValidatorRegistrationFactory.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner();

  private SpecContext specContext;
  private DataStructureUtil dataStructureUtil;
  private int slotsPerEpoch;

  private Validator validator1;
  private Validator validator2;
  private Validator validator3;

  private Map<BLSPublicKey, ValidatorStatus> validatorStatuses;

  private Eth1Address eth1Address;
  private UInt64 gasLimit;

  private ValidatorRegistrator validatorRegistrator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    this.specContext = specContext;
    slotsPerEpoch = specContext.getSpec().getGenesisSpecConfig().getSlotsPerEpoch();
    dataStructureUtil = specContext.getDataStructureUtil();
    validator1 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator2 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    validator3 = new Validator(dataStructureUtil.randomPublicKey(), signer, Optional::empty);
    // all validators are active by default
    validatorStatuses =
        Map.of(
            validator1.getPublicKey(),
            ValidatorStatus.active_ongoing,
            validator2.getPublicKey(),
            ValidatorStatus.active_ongoing,
            validator3.getPublicKey(),
            ValidatorStatus.active_ongoing);

    eth1Address = dataStructureUtil.randomEth1Address();
    gasLimit = dataStructureUtil.randomUInt64();

    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            ownedValidators,
            proposerConfigPropertiesProvider,
            signedValidatorRegistrationFactory,
            validatorApiChannel,
            BATCH_SIZE,
            stubAsyncRunner);

    when(signedValidatorRegistrationFactory.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              return SafeFuture.completedFuture(
                  createSignedValidatorRegistration(validator.getPublicKey()));
            });

    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(true);
    when(proposerConfigPropertiesProvider.isBuilderEnabled(any())).thenReturn(true);
    when(proposerConfigPropertiesProvider.refresh()).thenReturn(SafeFuture.COMPLETE);

    // random signature for all signings
    doAnswer(invocation -> SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(signer)
        .signValidatorRegistration(any(ValidatorRegistration.class));

    // registration is successful by default
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @TestTemplate
  void doesNotRegisterValidators_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    runRegistrationFlowWithSubscription(0);

    verifyNoInteractions(
        ownedValidators, validatorApiChannel, signedValidatorRegistrationFactory, signer);
  }

  @TestTemplate
  void doesNotRegisterValidators_ifTheSameStatusesArrivesInTheSameEpoch() {
    setOwnedValidators(validator1, validator2, validator3);

    // initially validators will be registered anyway since it's the first call
    runRegistrationFlowForSlotWithSubscription(ZERO);

    verify(validatorApiChannel).registerValidators(any());

    // after the initial call, registration should not occur
    // as it's the same epoch and validator set was not changed
    runRegistrationFlowForSlotWithSubscription(UInt64.valueOf(3));

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @TestTemplate
  void registersValidatorsAgain_ifPossibleMissingEventsFiredInTheSameEpoch() {
    setOwnedValidators(validator1, validator2, validator3);

    // initially validators will be registered anyway since it's the first call
    runRegistrationFlowForSlotWithSubscription(ZERO);

    verify(validatorApiChannel).registerValidators(any());

    // Even within the same epoch registration should occur again, when possible missing events are
    // detected, because it may mean changing of BN which requires VC to run registration again
    validatorRegistrator.onSlot(UInt64.valueOf(3));
    validatorRegistrator.onUpdatedValidatorStatuses(validatorStatuses, true);

    verify(validatorApiChannel, times(2)).registerValidators(any());
  }

  @TestTemplate
  void cleanupsCache_ifValidatorIsNoLongerOwned() {
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);

    // validator1 not active anymore
    setOwnedValidators(validator2, validator3);

    runRegistrationFlowWithSubscription(1);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);
  }

  @TestTemplate
  void doesNotRegisterValidatorsOnPossibleMissedEvents_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    validatorRegistrator.onPossibleMissedEvents();
    verifyNoInteractions(
        ownedValidators, signedValidatorRegistrationFactory, validatorApiChannel, signer);
  }

  @TestTemplate
  void doesNotRegisterNewlyAddedValidators_ifNotReady() {
    when(proposerConfigPropertiesProvider.isReadyToProvideProperties()).thenReturn(false);

    validatorRegistrator.onValidatorsAdded();

    verifyNoInteractions(
        ownedValidators, signedValidatorRegistrationFactory, validatorApiChannel, signer);
  }

  @TestTemplate
  void doesNotRegisterNewlyAddedValidators_ifFirstCallHasNotBeenDone() {
    validatorRegistrator.onValidatorsAdded();

    verifyNoInteractions(
        ownedValidators, signedValidatorRegistrationFactory, validatorApiChannel, signer);
  }

  @TestTemplate
  void registersNewlyAddedValidators() {
    setOwnedValidators(validator1);

    runRegistrationFlowWithSubscription(0);

    // new validators are added
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    assertThat(registrationCalls).hasSize(2);

    // first call only has validator1
    verifyRegistrations(registrationCalls.get(0), List.of(validator1));

    // second call should have processed validator2 and validator3
    verifyRegistrations(registrationCalls.get(1), List.of(validator2, validator3));
  }

  @TestTemplate
  void registerValidatorsEvenIfOneRegistrationCreationFails() {
    setOwnedValidators(validator1, validator2, validator3);

    reset(signedValidatorRegistrationFactory);
    when(signedValidatorRegistrationFactory.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              if (validator.equals(validator2)) {
                return SafeFuture.failedFuture(new IllegalStateException("oopsy"));
              }
              return SafeFuture.completedFuture(
                  createSignedValidatorRegistration(validator.getPublicKey()));
            });
    runRegistrationFlowWithSubscription(0);

    reset(signedValidatorRegistrationFactory);
    when(signedValidatorRegistrationFactory.createSignedValidatorRegistration(any(), any(), any()))
        .thenAnswer(
            args -> {
              final Validator validator = (Validator) args.getArguments()[0];
              return SafeFuture.completedFuture(
                  createSignedValidatorRegistration(validator.getPublicKey()));
            });
    runRegistrationFlowWithSubscription(1);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator3));
    verifyRegistrations(registrationCalls.get(1), List.of(validator1, validator2, validator3));
  }

  @TestTemplate
  void doesNotRegister_ifValidatorBuilderFlowIsNotEnabled() {
    setOwnedValidators(validator1, validator2, validator3);

    // disable builder flow for validator 1
    when(proposerConfigPropertiesProvider.isBuilderEnabled(validator1.getPublicKey()))
        .thenReturn(false);

    runRegistrationFlowWithSubscription(0);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);

    verifyRegistrations(registrationCalls.get(0), List.of(validator2, validator3));
  }

  @TestTemplate
  void doesNotRegister_ifValidatorIsExited() {
    setOwnedValidators(validator1, validator2, validator3);

    // only validator2 is active
    validatorStatuses =
        Map.of(
            validator1.getPublicKey(),
            ValidatorStatus.pending_initialized,
            validator2.getPublicKey(),
            ValidatorStatus.active_ongoing,
            validator3.getPublicKey(),
            ValidatorStatus.exited_unslashed);

    runRegistrationFlowWithSubscription(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);

    // validator1, validator2 are active
    validatorStatuses =
        Map.of(
            validator1.getPublicKey(),
            ValidatorStatus.active_ongoing,
            validator2.getPublicKey(),
            ValidatorStatus.active_exiting,
            validator3.getPublicKey(),
            ValidatorStatus.exited_unslashed);

    runRegistrationFlowWithSubscription(1);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
    verifyRegistrations(registrationCalls.get(1), List.of(validator1, validator2));
  }

  @TestTemplate
  void doesNotRegister_ifValidatorStatusUnknown() {
    setOwnedValidators(validator1, validator2, validator3);
    // only validator2 information is available
    validatorStatuses = Map.of(validator2.getPublicKey(), ValidatorStatus.active_ongoing);

    runRegistrationFlowWithSubscription(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(1);
    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);
    verifyRegistrations(registrationCalls.get(0), List.of(validator2));
  }

  @TestTemplate
  void registers_whenStatusBecomeKnown() {
    // Epoch 0: No validators, no statuses
    setOwnedValidators();
    this.validatorStatuses = Map.of();

    runRegistrationFlowWithSubscription(0);
    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(0);
    verify(validatorApiChannel, never()).registerValidators(any());

    // Epoch 1: 1 validator, no statuses
    setOwnedValidators(validator2);
    this.validatorStatuses = Map.of();

    runRegistrationFlowWithSubscription(1);
    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(0);
    verify(validatorApiChannel, never()).registerValidators(any());

    // Epoch 2: 1 validator, becomes active
    this.validatorStatuses = Map.of(validator2.getPublicKey(), ValidatorStatus.active_ongoing);

    runRegistrationFlowWithSubscription(2);
    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(1);
    final List<List<SignedValidatorRegistration>> registrationCalls2 = captureRegistrationCalls(1);
    verifyRegistrations(registrationCalls2.get(0), List.of(validator2));
  }

  @TestTemplate
  void noRegistrationsAreSentIfEmpty() {
    setOwnedValidators();

    runRegistrationFlowForSlotWithSubscription(ZERO);

    verifyNoInteractions(validatorApiChannel, signer, signedValidatorRegistrationFactory);
  }

  @TestTemplate
  void noRegistrationsAreSentIfValidatorsAreFilteredOut() {
    setOwnedValidators(validator1, validator2, validator3);

    // disable builder flow for validator1 and validator2
    when(proposerConfigPropertiesProvider.isBuilderEnabled(validator1.getPublicKey()))
        .thenReturn(false);
    when(proposerConfigPropertiesProvider.isBuilderEnabled(validator2.getPublicKey()))
        .thenReturn(false);

    // validator3 is exited
    validatorStatuses = Map.of(validator3.getPublicKey(), ValidatorStatus.withdrawal_done);

    runRegistrationFlowForSlotWithSubscription(ZERO);

    verifyNoInteractions(validatorApiChannel, signer, signedValidatorRegistrationFactory);
  }

  @TestTemplate
  void checksStatusesAndSendsRegistrationsInBatches() {
    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            ownedValidators,
            proposerConfigPropertiesProvider,
            signedValidatorRegistrationFactory,
            validatorApiChannel,
            2,
            stubAsyncRunner);
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
    verifyRegistrations(registrationCalls.get(1), List.of(validator3));
  }

  @TestTemplate
  void stopsToSendBatchesOnFirstFailure() {
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    validatorRegistrator =
        new ValidatorRegistrator(
            specContext.getSpec(),
            ownedValidators,
            proposerConfigPropertiesProvider,
            signedValidatorRegistrationFactory,
            validatorApiChannel,
            2,
            stubAsyncRunner);
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    // caching is after signing so still performed for first batch only
    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(2);
    verify(validatorApiChannel).registerValidators(any());

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2));
  }

  @TestTemplate
  void schedulesRetryOnFailure() {
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")))
        .thenReturn(SafeFuture.COMPLETE);

    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    assertThat(stubAsyncRunner.countDelayedActions()).isOne();

    // execute the delayed future
    stubAsyncRunner.executeQueuedActions();

    // no more retries
    assertThat(stubAsyncRunner.countDelayedActions()).isZero();

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(2);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2, validator3));
    verifyRegistrations(registrationCalls.get(1), List.of(validator1, validator2, validator3));
  }

  @TestTemplate
  void shouldQueueOnlyOneRetry() {
    SafeFuture<Void> firstAttempt = new SafeFuture<>();
    SafeFuture<Void> secondAttempt = new SafeFuture<>();
    when(validatorApiChannel.registerValidators(any()))
        .thenReturn(firstAttempt)
        .thenReturn(secondAttempt);

    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);
    firstAttempt.completeExceptionally(new IllegalStateException("oopsy"));
    runRegistrationFlowWithSubscription(0);
    secondAttempt.completeExceptionally(new IllegalStateException("oopsy"));

    assertThat(stubAsyncRunner.countDelayedActions()).isOne();
  }

  @TestTemplate
  void checksValidatorStatusWithPublicKeyOverride() {
    final BLSPublicKey validator2KeyOverride = dataStructureUtil.randomPublicKey();
    when(proposerConfigPropertiesProvider.getBuilderRegistrationPublicKeyOverride(
            validator2.getPublicKey()))
        .thenReturn(Optional.of(validator2KeyOverride));
    validatorStatuses =
        Map.of(
            validator1.getPublicKey(),
            ValidatorStatus.active_ongoing,
            validator2KeyOverride,
            ValidatorStatus.active_ongoing,
            validator3.getPublicKey(),
            ValidatorStatus.active_ongoing);
    setOwnedValidators(validator1, validator2, validator3);

    runRegistrationFlowWithSubscription(0);

    assertThat(validatorRegistrator.getNumberOfCachedRegistrations()).isEqualTo(3);
    verify(validatorApiChannel).registerValidators(any());

    final List<List<SignedValidatorRegistration>> registrationCalls = captureRegistrationCalls(1);

    verifyRegistrations(registrationCalls.get(0), List.of(validator1, validator2, validator3));
  }

  private void setOwnedValidators(final Validator... validators) {
    final List<Validator> validatorsAsList = Arrays.stream(validators).collect(Collectors.toList());
    when(ownedValidators.getValidators()).thenReturn(validatorsAsList);
  }

  private void runRegistrationFlowForSlotWithSubscription(final UInt64 slot) {
    validatorRegistrator.onSlot(slot);
    validatorRegistrator.onUpdatedValidatorStatuses(validatorStatuses, false);
  }

  private void runRegistrationFlowWithSubscription(final int epoch) {
    validatorRegistrator.onSlot(UInt64.valueOf(epoch).times(slotsPerEpoch));
    validatorRegistrator.onUpdatedValidatorStatuses(validatorStatuses, false);
  }

  private List<List<SignedValidatorRegistration>> captureRegistrationCalls(final int times) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel, times(times)).registerValidators(argumentCaptor.capture());

    return argumentCaptor.getAllValues().stream().map(SszList::asList).toList();
  }

  private void verifyRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations,
      final List<Validator> expectedRegisteredValidators) {

    assertThat(validatorRegistrations)
        .hasSize(expectedRegisteredValidators.size())
        .allSatisfy(registration -> assertThat(registration.getSignature().isValid()).isTrue())
        .map(SignedValidatorRegistration::getMessage)
        .allSatisfy(
            registration -> {
              assertThat(registration.getFeeRecipient()).isEqualTo(eth1Address);
              assertThat(registration.getTimestamp()).isEqualTo(TIMESTAMP);
              assertThat(registration.getGasLimit()).isEqualTo(gasLimit);
            })
        .map(ValidatorRegistration::getPublicKey)
        .containsExactlyInAnyOrderElementsOf(
            expectedRegisteredValidators.stream()
                .map(Validator::getPublicKey)
                .collect(Collectors.toList()));
  }

  private SignedValidatorRegistration createSignedValidatorRegistration(
      final BLSPublicKey publicKey) {
    return SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
        VALIDATOR_REGISTRATION_SCHEMA.create(eth1Address, gasLimit, TIMESTAMP, publicKey),
        dataStructureUtil.randomSignature());
  }
}
