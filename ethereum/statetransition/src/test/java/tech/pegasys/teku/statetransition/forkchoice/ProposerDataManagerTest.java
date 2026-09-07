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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerDataManagerTest {
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final Spec specMock = mock(Spec.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final Optional<Eth1Address> defaultFeeRecipient =
      Optional.of(Eth1Address.fromHexString("0x2Df386eFF130f991321bfC4F8372Ba838b9AB14B"));

  private final ProposersDataManager proposersDataManager =
      new ProposersDataManager(
          eventThread,
          specMock,
          metricsSystem,
          executionLayerChannel,
          recentChainData,
          defaultFeeRecipient,
          false);

  private final BeaconState state = dataStructureUtil.randomBeaconState();

  private final UInt64 slot = UInt64.ONE;
  private SszList<SignedValidatorRegistration> registrations;
  private final SafeFuture<Void> response = new SafeFuture<>();

  @Test
  void shouldCallRegisterValidator() {

    prepareRegistrations();

    final SafeFuture<Void> updateCall =
        proposersDataManager.updateValidatorRegistrations(registrations, slot);

    verify(executionLayerChannel).builderRegisterValidators(registrations, slot);
    verifyNoMoreInteractions(executionLayerChannel);

    response.complete(null);

    assertThat(updateCall).isCompleted();

    // final update
    assertRegisteredValidatorsCount(2);
  }

  @Test
  void shouldNotSignalValidatorRegistrationUpdatedOnError() {

    prepareRegistrations();

    final SafeFuture<Void> updateCall =
        proposersDataManager.updateValidatorRegistrations(registrations, slot);

    verify(executionLayerChannel).builderRegisterValidators(registrations, slot);

    response.completeExceptionally(new RuntimeException("generic error"));

    assertThat(updateCall).isCompletedExceptionally();

    verifyNoMoreInteractions(executionLayerChannel);
    assertRegisteredValidatorsCount(0);
  }

  @Test
  void shouldSignalAllDataUpdatedAndShouldExpireData() {

    prepareRegistrations();
    final SafeFuture<Void> updateCall =
        proposersDataManager.updateValidatorRegistrations(registrations, slot);
    response.complete(null);
    assertThat(updateCall).isCompleted();

    proposersDataManager.updatePreparedProposersFromPrepareBeaconProposer(
        List.of(
            new BeaconPreparableProposer(
                dataStructureUtil.randomUInt64(), dataStructureUtil.randomEth1Address())),
        slot);

    assertRegisteredValidatorsCount(2);
    assertPreparedProposersCount(1);

    final int slotsPerEpoch = spec.getSlotsPerEpoch(slot);

    // in 2 epochs in the middle of the epoch registrations expire
    UInt64 futureSlot = UInt64.valueOf(slotsPerEpoch * 2L).plus(slotsPerEpoch / 2).minus(1);
    proposersDataManager.onSlot(futureSlot);

    assertRegisteredValidatorsCount(0);
    assertPreparedProposersCount(1);

    // in 3 epochs in the middle of the epoch prepared proposers expire
    futureSlot = UInt64.valueOf(slotsPerEpoch * 3L).plus(slotsPerEpoch / 2).minus(1);
    proposersDataManager.onSlot(futureSlot);

    assertRegisteredValidatorsCount(0);
    assertPreparedProposersCount(0);
  }

  @Test
  void shouldUseProposerPreferencesGasLimitForPayloadAttributes() {
    final UInt64 blockSlot = UInt64.valueOf(12);
    final UInt64 proposerIndex = UInt64.ONE;
    final UInt64 targetGasLimit = UInt64.valueOf(45_000_000);
    final SignedValidatorRegistration validatorRegistration =
        validatorRegistrationWithGasLimit(UInt64.valueOf(30_000_000));
    final ProposerPreferencesManager proposerPreferencesManager =
        mock(ProposerPreferencesManager.class);
    final ProposersDataManager manager = createProposersDataManager(proposerPreferencesManager);
    when(proposerPreferencesManager.getProposerPreferences(blockSlot))
        .thenReturn(Optional.of(proposerPreferences(proposerIndex, targetGasLimit)));

    assertThat(
            manager.getTargetGasLimit(blockSlot, proposerIndex, Optional.of(validatorRegistration)))
        .isEqualTo(targetGasLimit);
  }

  @Test
  void shouldUseValidatorRegistrationGasLimitWithoutProposerPreferences() {
    final UInt64 blockSlot = UInt64.valueOf(12);
    final UInt64 proposerIndex = UInt64.ONE;
    final UInt64 registrationGasLimit = UInt64.valueOf(30_000_000);
    final SignedValidatorRegistration validatorRegistration =
        validatorRegistrationWithGasLimit(registrationGasLimit);

    assertThat(
            proposersDataManager.getTargetGasLimit(
                blockSlot, proposerIndex, Optional.of(validatorRegistration)))
        .isEqualTo(registrationGasLimit);
  }

  @Test
  void shouldIgnoreProposerPreferencesGasLimitForDifferentValidator() {
    final UInt64 blockSlot = UInt64.valueOf(12);
    final UInt64 proposerIndex = UInt64.ONE;
    final UInt64 registrationGasLimit = UInt64.valueOf(30_000_000);
    final ProposerPreferencesManager proposerPreferencesManager =
        mock(ProposerPreferencesManager.class);
    final ProposersDataManager manager = createProposersDataManager(proposerPreferencesManager);
    final SignedValidatorRegistration validatorRegistration =
        validatorRegistrationWithGasLimit(registrationGasLimit);

    when(proposerPreferencesManager.getProposerPreferences(blockSlot))
        .thenReturn(
            Optional.of(proposerPreferences(UInt64.valueOf(2), UInt64.valueOf(45_000_000))));

    assertThat(
            manager.getTargetGasLimit(blockSlot, proposerIndex, Optional.of(validatorRegistration)))
        .isEqualTo(registrationGasLimit);
  }

  @Test
  void shouldUseZeroTargetGasLimitWithoutProposerPreferencesOrValidatorRegistration() {
    assertThat(
            proposersDataManager.getTargetGasLimit(
                UInt64.valueOf(12), UInt64.ONE, Optional.empty()))
        .isEqualTo(UInt64.ZERO);
  }

  private void prepareRegistrations() {
    registrations = dataStructureUtil.randomSignedValidatorRegistrations(2);

    when(executionLayerChannel.builderRegisterValidators(registrations, slot)).thenReturn(response);
    when(recentChainData.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(specMock.getValidatorIndex(state, registrations.get(0).getMessage().getPublicKey()))
        .thenReturn(Optional.of(0));
    when(specMock.getValidatorIndex(state, registrations.get(1).getMessage().getPublicKey()))
        .thenReturn(Optional.of(1));
    when(specMock.getSlotsPerEpoch(any())).thenReturn(spec.getSlotsPerEpoch(slot));
  }

  private ProposersDataManager createProposersDataManager(
      final ProposerPreferencesManager proposerPreferencesManager) {
    return new ProposersDataManager(
        eventThread,
        specMock,
        new StubMetricsSystem(),
        executionLayerChannel,
        recentChainData,
        defaultFeeRecipient,
        false,
        proposerPreferencesManager);
  }

  private ProposerPreferences proposerPreferences(
      final UInt64 validatorIndex, final UInt64 gasLimit) {
    return new ProposerPreferencesSchema()
        .create(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomSlot(),
            validatorIndex,
            dataStructureUtil.randomEth1Address(),
            gasLimit);
  }

  private SignedValidatorRegistration validatorRegistrationWithGasLimit(final UInt64 gasLimit) {
    return SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
        VALIDATOR_REGISTRATION_SCHEMA.create(
            dataStructureUtil.randomBytes20(),
            gasLimit,
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomPublicKey()),
        dataStructureUtil.randomSignature());
  }

  private void assertPreparedProposersCount(final int expectedCount) {
    final OptionalDouble optionalValue =
        metricsSystem
            .getLabelledGauge(TekuMetricCategory.BEACON, "proposers_data")
            .getValue("prepared_proposers");
    assertThat(optionalValue).hasValue(expectedCount);
  }

  private void assertRegisteredValidatorsCount(final int expectedCount) {
    final OptionalDouble optionalValue =
        metricsSystem
            .getLabelledGauge(TekuMetricCategory.BEACON, "proposers_data")
            .getValue("registered_validators");
    assertThat(optionalValue).hasValue(expectedCount);
  }
}
