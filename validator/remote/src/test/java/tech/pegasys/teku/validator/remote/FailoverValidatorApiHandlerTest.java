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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconNodeRequestLabels;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.RequestOutcome;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.ValidatorApiChannelRequest;

class FailoverValidatorApiHandlerTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalBellatrix();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private BeaconNodeReadinessManager beaconNodeReadinessManager;

  private RemoteValidatorApiChannel primaryApiChannel;
  private RemoteValidatorApiChannel failoverApiChannel1;
  private RemoteValidatorApiChannel failoverApiChannel2;
  private FailoverValidatorApiHandler failoverApiHandler;

  @BeforeEach
  void setUp() {
    beaconNodeReadinessManager = mock(BeaconNodeReadinessManager.class);

    primaryApiChannel = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel1 = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel2 = mock(RemoteValidatorApiChannel.class);

    final Supplier<HttpUrl> randomHttpUrlGenerator =
        () -> HttpUrl.get("http://" + DATA_STRUCTURE_UTIL.randomBytes4().toHexString() + ".com");

    final List<RemoteValidatorApiChannel> failoverDelegates =
        List.of(failoverApiChannel1, failoverApiChannel2);

    when(beaconNodeReadinessManager.isReady(any())).thenReturn(true);
    doReturn(failoverDelegates.iterator())
        .when(beaconNodeReadinessManager)
        .getFailoversInOrderOfReadiness();

    when(primaryApiChannel.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());
    when(failoverApiChannel1.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());
    when(failoverApiChannel2.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryApiChannel,
            failoverDelegates,
            true,
            true,
            stubMetricsSystem);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestSucceedsWithoutFailover(
      final ValidatorApiChannelRequest<T> request, final String methodLabel, final T response) {

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailoversOnFailure(
      final ValidatorApiChannelRequest<T> request, final String methodLabel, final T response) {

    setupSuccesses(request, response, failoverApiChannel2);
    setupFailures(request, primaryApiChannel, failoverApiChannel1);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailoversImmediatelyIfPrimaryBeaconNodeMarkedAsNotReady(
      final ValidatorApiChannelRequest<T> request, final String methodLabel, final T response) {

    setupSuccesses(request, response, failoverApiChannel1);

    when(beaconNodeReadinessManager.isReady(primaryApiChannel)).thenReturn(false);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verify(primaryApiChannel, atLeastOnce()).getEndpoint();
    verifyNoMoreInteractions(primaryApiChannel);

    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailsOnAllFailovers(
      final ValidatorApiChannelRequest<T> request, final String methodLabel) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    verifyFailoverRequestExceptionIsThrown(result, methodLabel);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailsAndNoFailoversConfigured(final ValidatorApiChannelRequest<T> request) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryApiChannel,
            List.of(),
            true,
            true,
            stubMetricsSystem);

    // readiness is ignored
    when(beaconNodeReadinessManager.isReady(primaryApiChannel)).thenReturn(false);

    setupFailures(request, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWithMessage(
            "Request failed for " + primaryApiChannel.getEndpoint());

    assertThat(result).isCompletedExceptionally();

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getSubscriptionRequests")
  <T> void requestIsNotRelayedToFailoversIfFailoversSendSubnetSubscriptionsIsDisabled(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryApiChannel,
            List.of(failoverApiChannel1, failoverApiChannel2),
            false,
            true,
            stubMetricsSystem);

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getPublishSignedDutiesRequests")
  <T> void requestIsNotRelayedToFailoversIfFailoversPublishSignedDutiesIsDisabled(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryApiChannel,
            List.of(failoverApiChannel1, failoverApiChannel2),
            true,
            false,
            stubMetricsSystem);

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllReadyNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    when(beaconNodeReadinessManager.isReady(failoverApiChannel2)).thenReturn(false);

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyNoInteractions(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsNotRelayedIfNoFailoversAreConfigured(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            beaconNodeReadinessManager,
            primaryApiChannel,
            List.of(),
            true,
            true,
            stubMetricsSystem);

    // readiness is ignored
    when(beaconNodeReadinessManager.isReady(primaryApiChannel)).thenReturn(false);

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllNodesButFailsOnOneFailover(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1);
    setupFailures(request, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeButRelayedToFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupFailures(request, primaryApiChannel);
    setupSuccesses(request, response, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void relayedResponseReceivedFromFailoverIfPrimaryBeaconNodeMarkedAsNotReady(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupSuccesses(request, response, failoverApiChannel1, failoverApiChannel2);

    when(beaconNodeReadinessManager.isReady(primaryApiChannel)).thenReturn(false);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verify(primaryApiChannel, atLeastOnce()).getEndpoint();
    verifyNoMoreInteractions(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeAndOneFailoverDoesNotRespond(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupFailures(request, primaryApiChannel);
    SafeFuture<T> neverCompletedFuture = new SafeFuture<>();
    when(request.run(failoverApiChannel1)).thenReturn(neverCompletedFuture);
    setupSuccesses(request, response, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeAndAllFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      @NotNull final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    verifyFailoverRequestExceptionIsThrown(result, methodLabel);

    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
  }

  @Test
  public void publishesBlindedBlockOnlyToTheBeaconNodeWhichCreatedIt() {
    final UInt64 slot = UInt64.ONE;
    final BLSSignature randaoReveal = DATA_STRUCTURE_UTIL.randomSignature();

    final BlockContainerAndMetaData blindedBlock =
        DATA_STRUCTURE_UTIL.randomBlindedBlockContainerAndMetaData(UInt64.ONE);

    final ValidatorApiChannelRequest<Optional<BlockContainerAndMetaData>> creationRequest =
        apiChannel ->
            apiChannel.createUnsignedBlock(
                slot, randaoReveal, Optional.empty(), false, Optional.empty());

    setupFailures(creationRequest, primaryApiChannel);
    setupSuccesses(creationRequest, Optional.of(blindedBlock), failoverApiChannel1);

    SafeFutureAssert.assertThatSafeFuture(creationRequest.run(failoverApiHandler)).isCompleted();

    final SignedBeaconBlock blindedSignedBlock =
        DATA_STRUCTURE_UTIL.signedBlock(blindedBlock.blockContainer().getBlock());

    final ValidatorApiChannelRequest<SendSignedBlockResult> publishingRequest =
        apiChannel ->
            apiChannel.sendSignedBlock(blindedSignedBlock, BroadcastValidationLevel.NOT_REQUIRED);

    setupSuccesses(
        publishingRequest,
        mock(SendSignedBlockResult.class),
        primaryApiChannel,
        failoverApiChannel1,
        failoverApiChannel2);

    SafeFutureAssert.assertThatSafeFuture(publishingRequest.run(failoverApiHandler)).isCompleted();

    verify(failoverApiChannel1)
        .sendSignedBlock(blindedSignedBlock, BroadcastValidationLevel.NOT_REQUIRED);

    verify(primaryApiChannel, never())
        .sendSignedBlock(blindedSignedBlock, BroadcastValidationLevel.NOT_REQUIRED);
    verify(failoverApiChannel2, never())
        .sendSignedBlock(blindedSignedBlock, BroadcastValidationLevel.NOT_REQUIRED);
  }

  @Test
  public void executionPayloadIsCreatedByTheBeaconNodeWhichCreatedTheBid() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final UInt64 slot = UInt64.ONE;
    final UInt64 builderIndex = dataStructureUtil.randomBuilderIndex();

    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid(slot, builderIndex);

    final ValidatorApiChannelRequest<Optional<ExecutionPayloadBid>> bidCreationRequest =
        apiChannel -> apiChannel.createUnsignedExecutionPayloadBid(slot, builderIndex);

    setupFailures(bidCreationRequest, primaryApiChannel);
    setupSuccesses(bidCreationRequest, Optional.of(bid), failoverApiChannel1);

    SafeFutureAssert.assertThatSafeFuture(bidCreationRequest.run(failoverApiHandler)).isCompleted();
    final ExecutionPayloadEnvelope executionPayloadEnvelope =
        dataStructureUtil.randomExecutionPayloadEnvelope(slot);

    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();

    final ValidatorApiChannelRequest<Optional<ExecutionPayloadEnvelope>>
        executionPayloadCreationRequest =
            apiChannel -> apiChannel.createUnsignedExecutionPayload(slot, beaconBlockRoot);

    setupSuccesses(
        executionPayloadCreationRequest,
        Optional.of(executionPayloadEnvelope),
        primaryApiChannel,
        failoverApiChannel1,
        failoverApiChannel2);

    SafeFutureAssert.assertThatSafeFuture(executionPayloadCreationRequest.run(failoverApiHandler))
        .isCompleted();

    verify(failoverApiChannel1).createUnsignedExecutionPayload(slot, beaconBlockRoot);

    verify(primaryApiChannel, never()).createUnsignedExecutionPayload(slot, beaconBlockRoot);
    verify(failoverApiChannel2, never()).createUnsignedExecutionPayload(slot, beaconBlockRoot);
  }

  @Test
  public void executionPayloadIsCreatedByTheBeaconNodeWhichCreatedTheBlockWithSelfBuiltPayload() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();

    final UInt64 slot = UInt64.ONE;

    final BeaconBlock blockWithSelfBuiltPayload =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                slot,
                builder ->
                    builder.signedExecutionPayloadBid(
                        dataStructureUtil.randomSignedExecutionPayloadBid(
                            dataStructureUtil.randomExecutionPayloadBid(
                                slot, SpecConfigGloas.BUILDER_INDEX_SELF_BUILD)))));
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(blockWithSelfBuiltPayload, slot);

    final ValidatorApiChannelRequest<Optional<BlockContainerAndMetaData>> blockCreationRequest =
        apiChannel ->
            apiChannel.createUnsignedBlock(
                slot, randaoReveal, Optional.empty(), false, Optional.empty());

    setupFailures(blockCreationRequest, primaryApiChannel);
    setupSuccesses(
        blockCreationRequest, Optional.of(blockContainerAndMetaData), failoverApiChannel1);

    SafeFutureAssert.assertThatSafeFuture(blockCreationRequest.run(failoverApiHandler))
        .isCompleted();
    final ExecutionPayloadEnvelope executionPayloadEnvelope =
        dataStructureUtil.randomExecutionPayloadEnvelopeForBlock(blockWithSelfBuiltPayload);

    final Bytes32 beaconBlockRoot = blockWithSelfBuiltPayload.getRoot();

    final ValidatorApiChannelRequest<Optional<ExecutionPayloadEnvelope>>
        executionPayloadCreationRequest =
            apiChannel -> apiChannel.createUnsignedExecutionPayload(slot, beaconBlockRoot);

    setupSuccesses(
        executionPayloadCreationRequest,
        Optional.of(executionPayloadEnvelope),
        primaryApiChannel,
        failoverApiChannel1,
        failoverApiChannel2);

    SafeFutureAssert.assertThatSafeFuture(executionPayloadCreationRequest.run(failoverApiHandler))
        .isCompleted();

    verify(failoverApiChannel1).createUnsignedExecutionPayload(slot, beaconBlockRoot);

    verify(primaryApiChannel, never()).createUnsignedExecutionPayload(slot, beaconBlockRoot);
    verify(failoverApiChannel2, never()).createUnsignedExecutionPayload(slot, beaconBlockRoot);
  }

  private <T> void setupSuccesses(
      final ValidatorApiChannelRequest<T> request,
      final T response,
      final RemoteValidatorApiChannel... apiChannels) {
    Stream.of(apiChannels)
        .forEach(
            apiChannel ->
                when(request.run(apiChannel)).thenReturn(SafeFuture.completedFuture(response)));
  }

  private <T> void setupFailures(
      final ValidatorApiChannelRequest<T> request, final RemoteValidatorApiChannel... apiChannels) {
    Stream.of(apiChannels)
        .forEach(
            apiChannel -> {
              final IllegalStateException exception =
                  new IllegalStateException(
                      String.format("Request failed for %s", apiChannel.getEndpoint()));
              when(request.run(apiChannel)).thenReturn(SafeFuture.failedFuture(exception));
            });
  }

  private static Stream<Arguments> getRequestsUsingFailover() {
    final GenesisData genesisData =
        new GenesisData(DATA_STRUCTURE_UTIL.randomUInt64(), DATA_STRUCTURE_UTIL.randomBytes32());
    final BLSPublicKey publicKey = DATA_STRUCTURE_UTIL.randomPublicKey();
    final IntCollection validatorIndices = IntLists.singleton(0);
    final UInt64 slot = DATA_STRUCTURE_UTIL.randomUInt64();
    final BLSSignature randaoReveal = DATA_STRUCTURE_UTIL.randomSignature();
    final UInt64 epoch = DATA_STRUCTURE_UTIL.randomUInt64();
    final Bytes32 randomBytes32 = DATA_STRUCTURE_UTIL.randomBytes32();
    final Attestation attestation = DATA_STRUCTURE_UTIL.randomAttestation();
    final ValidatorLivenessAtEpoch validatorLivenessAtEpoch =
        new ValidatorLivenessAtEpoch(UInt64.ZERO, false);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        DATA_STRUCTURE_UTIL.randomBlockContainerAndMetaData(slot);

    return Stream.of(
        getArguments(
            "getGenesisData",
            ValidatorApiChannel::getGenesisData,
            BeaconNodeRequestLabels.GET_GENESIS_METHOD,
            Optional.of(genesisData)),
        getArguments(
            "getValidatorIndices",
            apiChannel -> apiChannel.getValidatorIndices(List.of(publicKey)),
            BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD,
            Map.of(publicKey, 0)),
        getArguments(
            "getValidatorStatuses",
            apiChannel -> apiChannel.getValidatorStatuses(List.of(publicKey)),
            BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD,
            Optional.of(
                Map.of(
                    publicKey,
                    new StateValidatorData(
                        UInt64.ONE,
                        UInt64.THIRTY_TWO_ETH,
                        ValidatorStatus.active_ongoing,
                        DATA_STRUCTURE_UTIL.randomValidator())))),
        getArguments(
            "getAttestationDuties",
            apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices),
            BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD,
            Optional.of(mock(AttesterDuties.class))),
        getArguments(
            "getSyncCommitteeDuties",
            apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices),
            BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD,
            Optional.of(mock(SyncCommitteeDuties.class))),
        getArguments(
            "getProposerDuties",
            apiChannel -> apiChannel.getProposerDuties(epoch, true),
            BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD,
            Optional.of(mock(ProposerDuties.class))),
        getArguments(
            "createUnsignedBlock",
            apiChannel ->
                apiChannel.createUnsignedBlock(
                    slot, randaoReveal, Optional.empty(), false, Optional.empty()),
            BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD,
            Optional.of(blockContainerAndMetaData)),
        getArguments(
            "createAttestationData",
            apiChannel -> apiChannel.createAttestationData(slot, 0),
            BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD,
            Optional.of(mock(AttestationData.class))),
        getArguments(
            "createAggregate",
            apiChannel -> apiChannel.createAggregate(slot, randomBytes32, Optional.empty()),
            BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD,
            Optional.of(attestation)),
        getArguments(
            "createSyncCommitteeContribution",
            apiChannel -> apiChannel.createSyncCommitteeContribution(slot, 0, randomBytes32),
            BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD,
            Optional.of(mock(SyncCommitteeContribution.class))),
        getArguments(
            "getValidatorsLiveness",
            apiChannel -> apiChannel.getValidatorsLiveness(List.of(UInt64.ZERO), epoch),
            BeaconNodeRequestLabels.GET_VALIDATORS_LIVENESS,
            Optional.of(List.of(validatorLivenessAtEpoch))));
  }

  private static Stream<Arguments> getRelayRequests() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        DATA_STRUCTURE_UTIL.randomSignedValidatorRegistrations(3);
    final BeaconPreparableProposer beaconPreparableProposer =
        DATA_STRUCTURE_UTIL.randomBeaconPreparableProposer();

    return Streams.concat(
        getSubscriptionRequests(),
        getPublishSignedDutiesRequests(),
        Stream.of(
            getArguments(
                "prepareBeaconProposer",
                apiChannel -> apiChannel.prepareBeaconProposer(List.of(beaconPreparableProposer)),
                apiChannel ->
                    verify(apiChannel).prepareBeaconProposer(List.of(beaconPreparableProposer)),
                BeaconNodeRequestLabels.PREPARE_BEACON_PROPOSERS_METHOD,
                null),
            getArguments(
                "registerValidators",
                apiChannel -> apiChannel.registerValidators(validatorRegistrations),
                apiChannel -> verify(apiChannel).registerValidators(validatorRegistrations),
                BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD,
                null),
            getArguments(
                "sendSignedProposerPreferences",
                apiChannel -> apiChannel.sendSignedProposerPreferences(List.of()),
                apiChannel -> verify(apiChannel).sendSignedProposerPreferences(List.of()),
                BeaconNodeRequestLabels.SEND_PROPOSER_PREFERENCES_METHOD,
                List.of())));
  }

  private static Stream<Arguments> getPublishSignedDutiesRequests() {
    final Attestation attestation = DATA_STRUCTURE_UTIL.randomAttestation();
    final SubmitDataError submitDataError =
        new SubmitDataError(DATA_STRUCTURE_UTIL.randomUInt64(), "foo");
    final SignedAggregateAndProof signedAggregateAndProof =
        DATA_STRUCTURE_UTIL.randomSignedAggregateAndProof();
    final SignedBeaconBlock signedBeaconBlock =
        DATA_STRUCTURE_UTIL.randomSignedBlindedBeaconBlock();
    final SyncCommitteeMessage syncCommitteeMessage =
        DATA_STRUCTURE_UTIL.randomSyncCommitteeMessage();
    final SignedContributionAndProof signedContributionAndProof =
        DATA_STRUCTURE_UTIL.randomSignedContributionAndProof(2);

    return Stream.of(
        getArguments(
            "sendSignedAttestations",
            apiChannel -> apiChannel.sendSignedAttestations(List.of(attestation)),
            apiChannel -> verify(apiChannel).sendSignedAttestations(List.of(attestation)),
            BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD,
            List.of(submitDataError)),
        getArguments(
            "sendAggregateAndProofs",
            apiChannel -> apiChannel.sendAggregateAndProofs(List.of(signedAggregateAndProof)),
            apiChannel ->
                verify(apiChannel).sendAggregateAndProofs(List.of(signedAggregateAndProof)),
            BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD,
            List.of(submitDataError)),
        getArguments(
            "sendSignedBlock",
            apiChannel ->
                apiChannel.sendSignedBlock(
                    signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED),
            apiChannel ->
                verify(apiChannel)
                    .sendSignedBlock(signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED),
            BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD,
            mock(SendSignedBlockResult.class)),
        getArguments(
            "sendSyncCommitteeMessages",
            apiChannel -> apiChannel.sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
            apiChannel ->
                verify(apiChannel).sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
            BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD,
            List.of(submitDataError)),
        getArguments(
            "sendSignedContributionAndProofs",
            apiChannel ->
                apiChannel.sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
            apiChannel ->
                verify(apiChannel)
                    .sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
            BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD,
            null));
  }

  private static Stream<Arguments> getSubscriptionRequests() {
    final CommitteeSubscriptionRequest committeeSubscriptionRequest =
        new CommitteeSubscriptionRequest(0, 0, UInt64.ZERO, UInt64.ONE, true);
    final SyncCommitteeSubnetSubscription syncCommitteeSubnetSubscription =
        new SyncCommitteeSubnetSubscription(0, IntSet.of(1), UInt64.ZERO);
    final SubnetSubscription subnetSubscription = new SubnetSubscription(0, UInt64.ONE);

    return Stream.of(
        getArguments(
            "subscribeToBeaconCommittee",
            apiChannel ->
                apiChannel.subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD,
            null),
        getArguments(
            "subscribeToSyncCommitteeSubnets",
            apiChannel ->
                apiChannel.subscribeToSyncCommitteeSubnets(
                    List.of(syncCommitteeSubnetSubscription)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToSyncCommitteeSubnets(List.of(syncCommitteeSubnetSubscription)),
            BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD,
            null),
        getArguments(
            "subscribeToPersistentSubnets",
            apiChannel -> apiChannel.subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            apiChannel ->
                verify(apiChannel).subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD,
            null));
  }

  private static <T> Arguments getArguments(
      final String name,
      final ValidatorApiChannelRequest<T> request,
      final String methodLabel,
      final T response) {
    return Arguments.of(Named.of(name, request), methodLabel, response);
  }

  private static <T> Arguments getArguments(
      final String name,
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {
    return Arguments.of(Named.of(name, request), verifyCallIsMade, methodLabel, response);
  }

  private void verifyRequestCounters(
      final RemoteValidatorApiChannel failoverApiChannel,
      final String methodLabel,
      final Map<RequestOutcome, Long> expectedCountByRequestOutcome) {
    expectedCountByRequestOutcome.forEach(
        (key, value) ->
            assertThat(getFailoverCounterValue(failoverApiChannel, methodLabel, key))
                .isEqualTo(value));
  }

  private <T> void verifyFailoverRequestExceptionIsThrown(
      final SafeFuture<T> result, final String methodLabel) {
    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(FailoverRequestException.class)
        .message()
        .satisfies(
            message -> {
              assertThat(message)
                  .contains(
                      String.format(
                          "Remote request (%s) failed on all configured Beacon Node endpoints",
                          methodLabel));
              assertThat(message)
                  .contains(
                      getExceptionMessageForEndpoint(primaryApiChannel.getEndpoint()),
                      getExceptionMessageForEndpoint(failoverApiChannel1.getEndpoint()),
                      getExceptionMessageForEndpoint(failoverApiChannel2.getEndpoint()));
            });
  }

  private String getExceptionMessageForEndpoint(final HttpUrl endpoint) {
    return String.format("java.lang.IllegalStateException: Request failed for %s", endpoint);
  }

  private long getFailoverCounterValue(
      final RemoteValidatorApiChannel apiChannel,
      final String methodLabel,
      final RequestOutcome outcome) {
    return stubMetricsSystem.getLabelledCounterValue(
        TekuMetricCategory.VALIDATOR,
        FailoverValidatorApiHandler.REMOTE_BEACON_NODES_REQUESTS_COUNTER_NAME,
        apiChannel.getEndpoint().toString(),
        methodLabel,
        outcome.toString());
  }
}
