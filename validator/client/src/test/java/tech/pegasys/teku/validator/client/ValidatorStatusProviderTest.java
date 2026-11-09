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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorStatusProviderTest {

  public static final String VALIDATOR_COUNTS_METRIC = "local_validator_counts";
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final BLSPublicKey validatorKey = BLSTestUtil.randomPublicKey(0);
  private final Collection<BLSPublicKey> validatorKeys = Set.of(validatorKey);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  final ValidatorStatusSubscriber validatorStatusSubscriber = mock(ValidatorStatusSubscriber.class);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private OwnedValidators ownedValidators;

  private ValidatorStatusProvider provider;

  @BeforeEach
  void setup() {
    ownedValidators =
        new OwnedValidators(
            Map.of(validatorKey, new Validator(validatorKey, NO_OP_SIGNER, Optional::empty)));
    provider =
        new OwnedValidatorStatusProvider(
            metricsSystem, ownedValidators, validatorApiChannel, spec, asyncRunner);
    provider.subscribeValidatorStatusesUpdates(validatorStatusSubscriber);
    provider.onSlot(UInt64.ZERO);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldRetryGettingInitialValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Collections.EMPTY_MAP)));

    assertThat(provider.start()).isNotCompleted();
    verify(validatorApiChannel).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(2)).getValidatorStatuses(validatorKeys);

    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel, times(3)).getValidatorStatuses(validatorKeys);
    verify(validatorStatusSubscriber).onValidatorStatuses(anyMap(), eq(false));
    assertThat(provider.start()).isCompleted();
  }

  @Test
  void shouldSetMetricsForInitialValidatorStatuses() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey,
                        validatorDataWithStatus(ValidatorStatus.pending_initialized)))));

    assertThat(provider.start()).isCompleted();

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.VALIDATOR, VALIDATOR_COUNTS_METRIC);
    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(1));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldUpdateValidatorStatusesOnFirstEpochSlot() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        // 1st call - no data at all
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of())))
        // 2nd call - pending status
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey,
                        validatorDataWithStatus(ValidatorStatus.pending_initialized)))))
        // 3rd call - active status
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey, validatorDataWithStatus(ValidatorStatus.active_ongoing)))));

    // Epoch 0
    assertThat(provider.start()).isCompleted();
    final ArgumentCaptor<Map<BLSPublicKey, ValidatorStatus>> statusesCaptor0 =
        ArgumentCaptor.forClass(Map.class);
    verify(validatorStatusSubscriber).onValidatorStatuses(statusesCaptor0.capture(), eq(false));
    assertThat(statusesCaptor0.getValue()).isEmpty();
    clearInvocations(validatorStatusSubscriber);

    final StubLabelledGauge gauge =
        metricsSystem.getLabelledGauge(TekuMetricCategory.VALIDATOR, VALIDATOR_COUNTS_METRIC);
    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(0));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(0));

    // Epoch 1
    provider.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE).plus(1));
    final ArgumentCaptor<Map<BLSPublicKey, ValidatorStatus>> statusesCaptor1 =
        ArgumentCaptor.forClass(Map.class);
    verify(validatorStatusSubscriber).onValidatorStatuses(statusesCaptor1.capture(), eq(false));
    assertThat(statusesCaptor1.getValue())
        .satisfies(
            map -> {
              assertThat(map.size()).isEqualTo(1);
              assertThat(map.get(validatorKey)).isEqualTo(ValidatorStatus.pending_initialized);
            });

    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(1));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(0));
    clearInvocations(validatorStatusSubscriber);

    // Epoch 2
    provider.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)).plus(1));
    final ArgumentCaptor<Map<BLSPublicKey, ValidatorStatus>> statusesCaptor2 =
        ArgumentCaptor.forClass(Map.class);
    verify(validatorStatusSubscriber).onValidatorStatuses(statusesCaptor2.capture(), eq(false));
    assertThat(statusesCaptor2.getValue())
        .satisfies(
            map -> {
              assertThat(map.size()).isEqualTo(1);
              assertThat(map.get(validatorKey)).isEqualTo(ValidatorStatus.active_ongoing);
            });

    assertThat(gauge.getValue(ValidatorStatus.pending_initialized.name()))
        .isEqualTo(OptionalDouble.of(0));
    assertThat(gauge.getValue(ValidatorStatus.active_ongoing.name()))
        .isEqualTo(OptionalDouble.of(1));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldUpdateValidatorStatusesWhenStartedWithNoOwnedValidators() {
    this.ownedValidators = new OwnedValidators();
    this.provider =
        new OwnedValidatorStatusProvider(
            metricsSystem, ownedValidators, validatorApiChannel, spec, asyncRunner);
    provider.subscribeValidatorStatusesUpdates(validatorStatusSubscriber);
    provider.onSlot(UInt64.ZERO);
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey, validatorDataWithStatus(ValidatorStatus.active_ongoing)))));

    // Epoch 0 - no owned validators
    assertThat(provider.start()).isCompleted();
    verify(validatorStatusSubscriber, never()).onValidatorStatuses(anyMap(), anyBoolean());

    // Epoch 1 - one owned validator added
    ownedValidators.addValidator(new Validator(validatorKey, NO_OP_SIGNER, Optional::empty));
    provider.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE).plus(1));
    final ArgumentCaptor<Map<BLSPublicKey, ValidatorStatus>> statusesCaptor =
        ArgumentCaptor.forClass(Map.class);
    verify(validatorStatusSubscriber).onValidatorStatuses(statusesCaptor.capture(), eq(false));
    assertThat(statusesCaptor.getValue())
        .satisfies(
            map -> {
              assertThat(map.size()).isEqualTo(1);
              assertThat(map.get(validatorKey)).isEqualTo(ValidatorStatus.active_ongoing);
            });
  }

  @Test
  void shouldFireNewStatusesOnValidatorAdded() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey, validatorDataWithStatus(ValidatorStatus.active_ongoing)))));

    assertThat(provider.start()).isCompleted();
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<BLSPublicKey, ValidatorStatus>> subscriberCapture =
        ArgumentCaptor.forClass(Map.class);
    verify(validatorStatusSubscriber).onValidatorStatuses(subscriberCapture.capture(), eq(false));
    final Map<BLSPublicKey, ValidatorStatus> result1 = subscriberCapture.getValue();
    assertThat(result1.size()).isEqualTo(1);
    assertThat(result1.keySet().stream().findFirst()).contains(validatorKey);

    // Adding new validator
    final BLSPublicKey validatorKey2 = BLSTestUtil.randomPublicKey(1);
    final Validator validator2 = new Validator(validatorKey2, NO_OP_SIGNER, Optional::empty);
    // should check only new one
    when(validatorApiChannel.getValidatorStatuses(Set.of(validatorKey2)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey2, validatorDataWithStatus(ValidatorStatus.active_ongoing)))));
    ownedValidators.addValidator(validator2);

    provider.onValidatorsAdded();
    verify(validatorStatusSubscriber, times(2))
        .onValidatorStatuses(subscriberCapture.capture(), eq(false));
    final Map<BLSPublicKey, ValidatorStatus> result2 = subscriberCapture.getValue();
    assertThat(result2.size()).isEqualTo(2);
    assertThat(result2.keySet()).contains(validatorKey, validatorKey2);
  }

  @Test
  void shouldPropagatePossibleMissingEvents() {
    when(validatorApiChannel.getValidatorStatuses(validatorKeys))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    Map.of(
                        validatorKey, validatorDataWithStatus(ValidatorStatus.active_ongoing)))));

    assertThat(provider.start()).isCompleted();
    verify(validatorApiChannel).getValidatorStatuses(anyCollection());
    verify(validatorStatusSubscriber).onValidatorStatuses(anyMap(), eq(false));

    // Firing onPossibleMissedEvents()
    provider.onPossibleMissedEvents();
    verifyNoMoreInteractions(validatorApiChannel);
    verify(validatorStatusSubscriber).onValidatorStatuses(anyMap(), eq(true));
  }

  private StateValidatorData validatorDataWithStatus(final ValidatorStatus status) {
    final tech.pegasys.teku.spec.datastructures.state.Validator validator =
        dataStructureUtil.randomValidator();
    return new StateValidatorData(
        dataStructureUtil.randomValidatorIndex(),
        validator.getEffectiveBalance(),
        status,
        validator);
  }
}
