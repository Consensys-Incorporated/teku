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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

@TestSpecContext(milestone = {GLOAS, HEZE})
public class ProposerPreferencesPublisherTest {

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider =
      mock(ProposerConfigPropertiesProvider.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposerPreferencesPublisher publisher;
  private OwnedValidators ownedValidators;
  private Signer signer;
  private Validator validator;
  private BLSPublicKey publicKey;
  private Eth1Address feeRecipient;
  private UInt64 gasLimit;

  private void setUp(final SpecContext specContext) {
    setUp(specContext.getSpec(), specContext.getDataStructureUtil());
  }

  private void setUp(final Spec spec, final DataStructureUtil dataStructureUtil) {
    this.spec = spec;
    this.dataStructureUtil = dataStructureUtil;
    signer = mock(Signer.class);
    publicKey = dataStructureUtil.randomPublicKey();
    feeRecipient = dataStructureUtil.randomEth1Address();
    gasLimit = dataStructureUtil.randomUInt64();
    validator = new Validator(publicKey, signer, Optional::empty);

    ownedValidators = new OwnedValidators(Map.of(publicKey, validator));

    publisher =
        new ProposerPreferencesPublisher(
            ownedValidators,
            spec,
            validatorApiChannel,
            proposerConfigPropertiesProvider,
            forkProvider);

    when(proposerConfigPropertiesProvider.getFeeRecipient(publicKey))
        .thenReturn(Optional.of(feeRecipient));
    when(proposerConfigPropertiesProvider.getGasLimit(eq(publicKey), any())).thenReturn(gasLimit);
    when(forkProvider.getForkInfo(any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomForkInfo()));
    when(signer.signProposerPreferences(any(), any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(validatorApiChannel.sendSignedProposerPreferences(anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
  }

  @TestTemplate
  void shouldPublishWhenDutiesIncludeOurValidator(final SpecContext specContext) {
    setUp(specContext);
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);

    publisher.onProposerDutiesLoaded(
        epoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(new ProposerDuty(publicKey, 42, slot)),
            false));

    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());
    // duties may be for a future epoch, so the gas limit must be resolved for that epoch
    verify(proposerConfigPropertiesProvider).getGasLimit(publicKey, epoch);
  }

  @TestTemplate
  void shouldReceiveValidationRejectionDescription(final SpecContext specContext) {
    setUp(specContext);
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);
    final String rejectionDescription = "Invalid proposer preferences signature";
    when(validatorApiChannel.sendSignedProposerPreferences(anyList()))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(new SubmitDataError(UInt64.ZERO, rejectionDescription))));

    try (LogCaptor logCaptor = LogCaptor.forClass(ValidatorLogger.class)) {
      publisher.onProposerDutiesLoaded(
          epoch,
          new ProposerDuties(
              dataStructureUtil.randomBytes32(),
              List.of(new ProposerDuty(publicKey, 42, slot)),
              false));

      assertThat(logCaptor.getErrorLogs())
          .singleElement()
          .asString()
          .contains("Failed to publish proposer preferences for epoch " + epoch);
      assertThat(logCaptor.getErrorThrowables())
          .extracting(error -> error.getCause().getMessage())
          .containsExactly(rejectionDescription);
    }
  }

  @TestTemplate
  void shouldNotPublishWhenNoDutiesForOurValidators(final SpecContext specContext) {
    setUp(specContext);
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);
    final BLSPublicKey otherKey = dataStructureUtil.randomPublicKey();

    publisher.onProposerDutiesLoaded(
        epoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(new ProposerDuty(otherKey, 99, slot)),
            false));

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldNotPublishWhenFeeRecipientNotConfigured(final SpecContext specContext) {
    setUp(specContext);
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);

    when(proposerConfigPropertiesProvider.getFeeRecipient(publicKey)).thenReturn(Optional.empty());

    publisher.onProposerDutiesLoaded(
        epoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(new ProposerDuty(publicKey, 42, slot)),
            false));

    verify(validatorApiChannel, never()).sendSignedProposerPreferences(anyList());
  }

  @TestTemplate
  void shouldPublishRemainingPreferencesWhenSigningFails(final SpecContext specContext) {
    setUp(specContext);
    final UInt64 epoch = UInt64.valueOf(6);
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);

    final int failingValidatorIndex = 99;
    final int successfulValidatorIndex = 42;

    final BLSPublicKey failingKey = dataStructureUtil.randomPublicKey();
    final Signer failingSigner = mock(Signer.class);
    final Validator failingValidator = new Validator(failingKey, failingSigner, Optional::empty);
    ownedValidators.addValidator(failingValidator);

    when(proposerConfigPropertiesProvider.getFeeRecipient(failingKey))
        .thenReturn(Optional.of(dataStructureUtil.randomEth1Address()));
    when(proposerConfigPropertiesProvider.getGasLimit(eq(failingKey), any())).thenReturn(gasLimit);
    when(failingSigner.signProposerPreferences(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new UnsupportedOperationException("not supported")));

    publisher.onProposerDutiesLoaded(
        epoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(
                new ProposerDuty(failingKey, failingValidatorIndex, slot),
                new ProposerDuty(publicKey, successfulValidatorIndex, slot.plus(1))),
            false));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<SignedProposerPreferences>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(validatorApiChannel).sendSignedProposerPreferences(captor.capture());
    final List<SignedProposerPreferences> published = captor.getValue();
    assertThat(published).hasSize(1);
    assertThat(published.getFirst().getMessage().getValidatorIndex())
        .isEqualTo(UInt64.valueOf(successfulValidatorIndex));
  }

  @Test
  void shouldPublishFirstGloasEpochDutiesLoadedDuringLastFuluEpoch() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE);
    setUp(transitionSpec, new DataStructureUtil(transitionSpec));

    final UInt64 gloasEpoch = UInt64.ONE;
    final UInt64 firstGloasSlot = spec.computeStartSlotAtEpoch(gloasEpoch);
    assertThat(spec.areProposerAndBuilderPreferencesRequiredAtEpoch(UInt64.ZERO)).isFalse();

    publisher.onProposerDutiesLoaded(
        gloasEpoch,
        new ProposerDuties(
            dataStructureUtil.randomBytes32(),
            List.of(new ProposerDuty(publicKey, 42, firstGloasSlot)),
            false));

    verify(signer).signProposerPreferences(any(), any());
    verify(validatorApiChannel).sendSignedProposerPreferences(anyList());
  }
}
