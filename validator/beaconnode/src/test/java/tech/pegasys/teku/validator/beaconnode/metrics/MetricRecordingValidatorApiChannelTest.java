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

package tech.pegasys.teku.validator.beaconnode.metrics;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesEntry;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel.RequestOutcome;

class MetricRecordingValidatorApiChannelTest {

  private final ValidatorApiChannel delegate = mock(ValidatorApiChannel.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final MetricRecordingValidatorApiChannel apiChannel =
      new MetricRecordingValidatorApiChannel(metricsSystem, delegate);

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordSuccessfulRequestForData(
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String methodLabel,
      final Object value) {
    final Optional<Object> response = Optional.of(value);
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(response));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);

    assertThat(result).isCompletedWithValue(response);

    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordFailedRequestForData(
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String methodLabel) {
    final RuntimeException exception = new RuntimeException("Nope");
    when(method.apply(delegate)).thenReturn(SafeFuture.failedFuture(exception));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCause(exception);

    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getDataRequestArguments")
  public void shouldRecordRequestForDataWhenDataUnavailable(
      final Function<ValidatorApiChannel, SafeFuture<Optional<Object>>> method,
      final String methodLabel) {
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Optional<Object>> result = method.apply(apiChannel);
    assertThat(result).isCompletedWithValue(Optional.empty());

    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getSendDataArguments")
  void shouldRecordSuccessfulSendRequest(
      final Function<ValidatorApiChannel, SafeFuture<List<Object>>> method,
      final String methodLabel,
      final Optional<String> timerLabel) {
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(emptyList()));

    final SafeFuture<List<Object>> result = method.apply(apiChannel);

    assertThat(result).isCompletedWithValue(emptyList());

    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isZero();

    timerLabel.ifPresent(
        label -> assertThat(getDutiesOperationTimerDurations(label, "send")).hasSize(1));
  }

  @Test
  void shouldRecordSuccessfulSendSignedProposerPreferences() {
    when(delegate.sendSignedProposerPreferences(emptyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final SafeFuture<List<SubmitDataError>> result =
        apiChannel.sendSignedProposerPreferences(emptyList());

    assertThat(result).isCompletedWithValue(List.of());

    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD, RequestOutcome.SUCCESS))
        .isEqualTo(1);
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD, RequestOutcome.ERROR))
        .isZero();
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD,
                RequestOutcome.DATA_UNAVAILABLE))
        .isZero();
  }

  @Test
  void shouldRecordFailedSendSignedProposerPreferences() {
    final List<SubmitDataError> failures = List.of(new SubmitDataError(UInt64.ZERO, "Nope"));
    when(delegate.sendSignedProposerPreferences(emptyList()))
        .thenReturn(SafeFuture.completedFuture(failures));

    final SafeFuture<List<SubmitDataError>> result =
        apiChannel.sendSignedProposerPreferences(emptyList());

    assertThat(result).isCompletedWithValue(failures);

    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD, RequestOutcome.SUCCESS))
        .isZero();
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD, RequestOutcome.ERROR))
        .isEqualTo(1);
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD,
                RequestOutcome.DATA_UNAVAILABLE))
        .isZero();
  }

  @Test
  void shouldRecordSuccessfulSendBuilderPreferences() {
    final SszList<BuilderPreferencesEntry> emptyEntries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(emptyList());
    when(delegate.sendBuilderPreferences(emptyEntries))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    final SafeFuture<List<SubmitDataError>> result =
        apiChannel.sendBuilderPreferences(emptyEntries);

    assertThat(result).isCompletedWithValue(List.of());
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD, RequestOutcome.SUCCESS))
        .isEqualTo(1);
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD, RequestOutcome.ERROR))
        .isZero();
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD,
                RequestOutcome.DATA_UNAVAILABLE))
        .isZero();
  }

  @Test
  void shouldRecordFailedSendBuilderPreferences() {
    final List<SubmitDataError> failures = List.of(new SubmitDataError(UInt64.ZERO, "Nope"));
    final SszList<BuilderPreferencesEntry> emptyEntries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(emptyList());
    when(delegate.sendBuilderPreferences(emptyEntries))
        .thenReturn(SafeFuture.completedFuture(failures));

    final SafeFuture<List<SubmitDataError>> result =
        apiChannel.sendBuilderPreferences(emptyEntries);

    assertThat(result).isCompletedWithValue(failures);
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD, RequestOutcome.SUCCESS))
        .isZero();
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD, RequestOutcome.ERROR))
        .isEqualTo(1);
    assertThat(
            getCounterValue(
                BeaconNodeRequestLabels.SEND_BUILDER_PREFERENCES_METHOD,
                RequestOutcome.DATA_UNAVAILABLE))
        .isZero();
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getSendDataArguments")
  void shouldRecordFailingSendRequest(
      final Function<ValidatorApiChannel, SafeFuture<List<Object>>> method,
      final String methodLabel,
      final Optional<String> metricsTimerLabel,
      final List<Object> failures) {
    when(method.apply(delegate)).thenReturn(SafeFuture.completedFuture(failures));

    final SafeFuture<List<Object>> result = method.apply(apiChannel);

    assertThat(result).isCompletedWithValue(failures);

    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isZero();

    metricsTimerLabel.ifPresent(
        label -> assertThat(getDutiesOperationTimerDurations(label, "send")).hasSize(1));
  }

  @ParameterizedTest(name = "{displayName} - {0}")
  @MethodSource("getNoResponseCallArguments")
  public void shouldRecordCallsWithNoResponse(
      final Function<ValidatorApiChannel, SafeFuture<Void>> method, final String methodLabel) {
    when(method.apply(delegate)).thenReturn(SafeFuture.COMPLETE);

    final SafeFuture<Void> result = method.apply(apiChannel);

    assertThat(result).isCompleted();

    assertThat(getCounterValue(methodLabel, RequestOutcome.SUCCESS)).isEqualTo(1);
    assertThat(getCounterValue(methodLabel, RequestOutcome.ERROR)).isZero();
    assertThat(getCounterValue(methodLabel, RequestOutcome.DATA_UNAVAILABLE)).isZero();
  }

  public static Stream<Arguments> getNoResponseCallArguments() {
    return Stream.of(
        noResponseTest(
            "subscribeToBeaconCommitteeForAggregation",
            channel -> channel.subscribeToBeaconCommittee(emptyList()),
            BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD),
        noResponseTest(
            "subscribeToPersistentSubnets",
            channel -> channel.subscribeToPersistentSubnets(emptySet()),
            BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD));
  }

  private static Arguments noResponseTest(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Void>> method,
      final String methodLabel) {
    return Arguments.of(Named.named(name, method), methodLabel);
  }

  public static Stream<Arguments> getDataRequestArguments() {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalAltair());
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final int subcommitteeIndex = dataStructureUtil.randomPositiveInt();
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    final List<UInt64> validatorIndices =
        List.of(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final Function<ValidatorApiChannel, SafeFuture<Optional<Attestation>>> createAggregateMethod =
        channel ->
            channel.createAggregate(
                attestationData.getSlot(), attestationData.hashTreeRoot(), Optional.empty());
    return Stream.of(
        requestDataTest(
            "getGenesisData",
            ValidatorApiChannel::getGenesisData,
            BeaconNodeRequestLabels.GET_GENESIS_METHOD,
            new GenesisData(dataStructureUtil.randomUInt64(), Bytes32.random())),
        requestDataTest(
            "createUnsignedBlock",
            channel ->
                channel.createUnsignedBlock(
                    slot, signature, Optional.empty(), false, Optional.empty()),
            BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD,
            dataStructureUtil.randomBlockContainerAndMetaData(slot)),
        requestDataTest(
            "createAttestationData",
            channel -> channel.createAttestationData(slot, 4),
            BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD,
            dataStructureUtil.randomAttestationData()),
        Arguments.of(
            Named.named("createAggregate", createAggregateMethod),
            BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD,
            dataStructureUtil.randomAttestation()),
        requestDataTest(
            "createSyncCommitteeContribution",
            channel ->
                channel.createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot),
            BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD,
            dataStructureUtil.randomSyncCommitteeContribution(slot)),
        requestDataTest(
            "getValidatorsLiveness",
            channel -> channel.getValidatorsLiveness(validatorIndices, epoch),
            BeaconNodeRequestLabels.GET_VALIDATORS_LIVENESS,
            new ArrayList<>()));
  }

  public static Stream<Arguments> getSendDataArguments() {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalAltair());
    final List<SubmitDataError> submissionErrors =
        List.of(new SubmitDataError(UInt64.ZERO, "Nope"));
    final List<Attestation> attestations = List.of(dataStructureUtil.randomAttestation());
    final List<SyncCommitteeMessage> syncCommitteeMessages =
        List.of(dataStructureUtil.randomSyncCommitteeMessage());
    final List<SignedAggregateAndProof> aggregateAndProofs =
        List.of(dataStructureUtil.randomSignedAggregateAndProof());
    return Stream.of(
        sendDataTest(
            "sendSignedAttestations",
            channel -> channel.sendSignedAttestations(attestations),
            BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD,
            Optional.of("attestation_production"),
            submissionErrors),
        sendDataTest(
            "sendSyncCommitteeMessages",
            channel -> channel.sendSyncCommitteeMessages(syncCommitteeMessages),
            BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD,
            Optional.empty(),
            submissionErrors),
        sendDataTest(
            "sendAggregateAndProofs",
            channel -> channel.sendAggregateAndProofs(aggregateAndProofs),
            BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD,
            Optional.empty(),
            submissionErrors));
  }

  private static <T> Arguments requestDataTest(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<Optional<T>>> method,
      final String methodLabel,
      final T presentValue) {
    return Arguments.of(Named.named(name, method), methodLabel, presentValue);
  }

  private static <T> Arguments sendDataTest(
      final String name,
      final Function<ValidatorApiChannel, SafeFuture<List<T>>> method,
      final String methodLabel,
      final Optional<String> timerLabel,
      final List<T> errors) {
    return Arguments.of(Named.named(name, method), methodLabel, timerLabel, errors);
  }

  private long getCounterValue(final String methodLabel, final RequestOutcome outcome) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.VALIDATOR,
        MetricRecordingValidatorApiChannel.BEACON_NODE_REQUESTS_COUNTER_NAME,
        methodLabel,
        outcome.toString());
  }

  private Set<Long> getDutiesOperationTimerDurations(final String... labels) {
    return metricsSystem
        .getLabelledOperationTimer(TekuMetricCategory.VALIDATOR_DUTY, "timer")
        .getDurations(labels);
  }
}
