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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl.Source;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.ExecutionPayloadAndBlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerBlockProductionManagerImplTest {

  private final ExecutionClientHandler executionClientHandler = mock(ExecutionClientHandler.class);

  private final BuilderClient builderClient = Mockito.mock(BuilderClient.class);

  private Spec spec = TestSpecFactory.createMinimalCapella();

  private DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BuilderCircuitBreaker builderCircuitBreaker = mock(BuilderCircuitBreaker.class);
  private ExecutionLayerManagerImpl executionLayerManager;
  private ExecutionLayerBlockProductionManagerImpl blockProductionManager;

  private final UInt256 executionPayloadValue = UInt256.valueOf(12345);

  @BeforeEach
  public void setup() {
    this.executionLayerManager = createExecutionLayerChannelImpl(true, false);
    this.blockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
  }

  @Test
  public void preDeneb_builderOffline() throws Exception {
    setBuilderOffline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponse(executionPayloadContext, executionPayloadValue, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getExecutionPayloadValueFuture().get())
        .isEqualTo(executionPayloadValue);
    verify(executionClientHandler).engineGetPayload(any(), any());

    // we expect local builder bid as result
    final BuilderBidOrFallbackData expectedResult =
        BuilderBidOrFallbackData.create(
            new FallbackData(getPayloadResponse, FallbackReason.BUILDER_NOT_AVAILABLE));
    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        executionPayloadResult.getBuilderBidOrFallbackDataFuture().orElseThrow();
    assertThat(builderBidOrFallbackDataFuture.get()).isEqualTo(expectedResult);
    final FallbackData localFallback =
        verifyFallbackToLocalEL(slot, executionPayloadContext, expectedResult);

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);
    // wrong slot
    assertThat(blockProductionManager.getCachedPayloadResult(slot.plus(1))).isEmpty();

    final SafeFuture<BuilderPayloadOrFallbackData> unblindedPayload =
        blockProductionManager.getUnblindedPayload(
            dataStructureUtil.randomSignedBlindedBeaconBlock(slot),
            BlockPublishingPerformance.NOOP);
    assertThat(unblindedPayload.get().getFallbackData()).hasValue(localFallback);

    // wrong slot, we will hit builder client by this call
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot.plus(1));
    assertThatThrownBy(
        () ->
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP));
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
  }

  @Test
  public void preDeneb_builderOnline() throws Exception {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    // we expect result from the builder
    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(executionPayloadContext, false);
    prepareEngineGetPayloadResponse(executionPayloadContext, executionPayloadValue, slot);
    final BuilderBidOrFallbackData expectedResult = BuilderBidOrFallbackData.create(builderBid);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFutureFromLocalFlow()).isEmpty();
    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        executionPayloadResult.getBuilderBidOrFallbackDataFuture().orElseThrow();
    assertThat(builderBidOrFallbackDataFuture.get()).isEqualTo(expectedResult);
    assertThat(executionPayloadResult.getExecutionPayloadValueFuture().get())
        .isEqualTo(builderBid.getValue());

    // we expect both builder and local engine have been called
    verifyBuilderCalled(slot, executionPayloadContext);
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final ExecutionPayload payload = prepareBuilderGetPayloadResponse(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(BuilderPayloadOrFallbackData.create(payload));

    // we expect both builder and local engine have been called
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionClientHandler);
    verifySourceCounter(Source.BUILDER, FallbackReason.NONE);
  }

  @Test
  public void preDeneb_noBuilder() throws Exception {
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponse(executionPayloadContext, executionPayloadValue, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            false,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getBuilderBidOrFallbackDataFuture()).isEmpty();
    assertThat(executionPayloadResult.getExecutionPayloadValueFuture().get())
        .isEqualTo(executionPayloadValue);

    // no blobs before Deneb
    final Optional<BlobsBundle> blobsBundle =
        executionPayloadResult.getBlobsBundleFutureFromLocalFlow().orElseThrow().get();
    assertThat(blobsBundle).isEmpty();

    final ExecutionPayload executionPayload =
        executionPayloadResult.getExecutionPayloadFutureFromLocalFlow().orElseThrow().get();
    assertThat(executionPayload).isEqualTo(getPayloadResponse.getExecutionPayload());

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    // we will hit builder client by this call
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);
    assertThatThrownBy(
        () ->
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP));
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
  }

  @Test
  public void postDeneb_builderOffline() throws Exception {
    setupDeneb();
    setBuilderOffline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponseWithBlobs(
            executionPayloadContext, executionPayloadValue, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getExecutionPayloadValueFuture().get())
        .isEqualTo(executionPayloadValue);

    // we expect local builder bid as result
    final BuilderBidOrFallbackData expectedResult =
        BuilderBidOrFallbackData.create(
            new FallbackData(getPayloadResponse, FallbackReason.BUILDER_NOT_AVAILABLE));
    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        executionPayloadResult.getBuilderBidOrFallbackDataFuture().orElseThrow();
    assertThat(builderBidOrFallbackDataFuture.get()).isEqualTo(expectedResult);
    final FallbackData localFallback =
        verifyFallbackToLocalEL(slot, executionPayloadContext, expectedResult);

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    final SafeFuture<BuilderPayloadOrFallbackData> unblindedPayload =
        blockProductionManager.getUnblindedPayload(
            dataStructureUtil.randomSignedBlindedBeaconBlock(slot),
            BlockPublishingPerformance.NOOP);
    assertThat(unblindedPayload.get().getFallbackData()).hasValue(localFallback);

    verifyNoMoreInteractions(builderClient);
    verifyNoMoreInteractions(executionClientHandler);
  }

  @Test
  public void postDeneb_builderOnline() throws Exception {
    setupDeneb();
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    // we expect result from the builder
    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(executionPayloadContext, false);
    prepareEngineGetPayloadResponseWithBlobs(executionPayloadContext, executionPayloadValue, slot);

    final BuilderBidOrFallbackData expectedResult = BuilderBidOrFallbackData.create(builderBid);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFutureFromLocalFlow()).isEmpty();

    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        executionPayloadResult.getBuilderBidOrFallbackDataFuture().orElseThrow();
    assertThat(builderBidOrFallbackDataFuture.get()).isEqualTo(expectedResult);

    // we expect both builder and local engine have been called
    verifyBuilderCalled(slot, executionPayloadContext);
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    final ExecutionPayloadAndBlobsBundle payloadAndBlobsBundle =
        prepareBuilderGetPayloadResponseWithBlobs(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(BuilderPayloadOrFallbackData.create(payloadAndBlobsBundle));

    // we expect both builder and local engine have been called
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionClientHandler);
    verifySourceCounter(Source.BUILDER, FallbackReason.NONE);
  }

  @Test
  public void postDeneb_noBuilder() throws Exception {
    setupDeneb();
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        prepareEngineGetPayloadResponseWithBlobs(
            executionPayloadContext, executionPayloadValue, slot);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            false,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getBuilderBidOrFallbackDataFuture()).isEmpty();
    assertThat(executionPayloadResult.getExecutionPayloadValueFuture().get())
        .isEqualTo(executionPayloadValue);

    final ExecutionPayload executionPayload =
        executionPayloadResult.getExecutionPayloadFutureFromLocalFlow().orElseThrow().get();
    assertThat(executionPayload).isEqualTo(getPayloadResponse.getExecutionPayload());
    final Optional<BlobsBundle> blobsBundle =
        executionPayloadResult.getBlobsBundleFutureFromLocalFlow().orElseThrow().get();
    assertThat(blobsBundle).isEqualTo(getPayloadResponse.getBlobsBundle());

    assertThat(blockProductionManager.getCachedPayloadResult(slot))
        .contains(executionPayloadResult);

    // we will hit builder client by this call
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);
    assertThatThrownBy(
        () ->
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP));
    verify(builderClient).getPayload(signedBlindedBeaconBlock);
  }

  @Test
  public void postFulu_builderOnline() throws Exception {
    setupFulu();
    setBuilderOnline();

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    // we expect result from the builder
    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(executionPayloadContext, false);
    prepareEngineGetPayloadResponseWithBlobs(executionPayloadContext, executionPayloadValue, slot);

    final BuilderBidOrFallbackData expectedResult = BuilderBidOrFallbackData.create(builderBid);

    final ExecutionPayloadResult executionPayloadResult =
        blockProductionManager.initiateBlockProduction(
            executionPayloadContext,
            state,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP);
    assertThat(executionPayloadResult.getExecutionPayloadContext())
        .isEqualTo(executionPayloadContext);
    assertThat(executionPayloadResult.getExecutionPayloadFutureFromLocalFlow()).isEmpty();
    assertThat(executionPayloadResult.getBlobsBundleFutureFromLocalFlow()).isEmpty();

    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        executionPayloadResult.getBuilderBidOrFallbackDataFuture().orElseThrow();
    assertThat(builderBidOrFallbackDataFuture.get()).isEqualTo(expectedResult);

    // we expect both builder and local engine have been called
    verifyBuilderCalled(slot, executionPayloadContext);
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    prepareBuilderGetPayloadV2InFulu(signedBlindedBeaconBlock);

    // we expect result from the builder
    assertThat(
            blockProductionManager.getUnblindedPayload(
                signedBlindedBeaconBlock, BlockPublishingPerformance.NOOP))
        .isCompletedWithValue(BuilderPayloadOrFallbackData.createSuccessful());

    // we expect both builder and local engine have been called
    verify(builderClient).getPayloadV2(signedBlindedBeaconBlock);
    verifyNoMoreInteractions(executionClientHandler);
    verifySourceCounter(Source.BUILDER, FallbackReason.NONE);
  }

  private void setupDeneb() {
    this.spec = TestSpecFactory.createMinimalDeneb();
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.executionLayerManager = createExecutionLayerChannelImpl(true, false);
    this.blockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
  }

  private void setupFulu() {
    this.spec = TestSpecFactory.createMinimalFulu();
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.executionLayerManager = createExecutionLayerChannelImpl(true, false);
    this.blockProductionManager =
        new ExecutionLayerBlockProductionManagerImpl(executionLayerManager);
  }

  private BuilderBid prepareBuilderGetHeaderResponse(
      final ExecutionPayloadContext executionPayloadContext, final boolean prepareEmptyResponse) {
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();

    final SignedBuilderBid signedBuilderBid = dataStructureUtil.randomSignedBuilderBid();

    doAnswer(
            __ -> {
              if (prepareEmptyResponse) {
                return SafeFuture.completedFuture(
                    Response.fromPayloadReceivedAsJson(Optional.empty()));
              }
              return SafeFuture.completedFuture(
                  Response.fromPayloadReceivedAsJson(Optional.of(signedBuilderBid)));
            })
        .when(builderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());

    return signedBuilderBid.getMessage();
  }

  private FallbackData verifyFallbackToLocalEL(
      final UInt64 slot,
      final ExecutionPayloadContext executionPayloadContext,
      final BuilderBidOrFallbackData builderBidOrFallbackData) {
    final FallbackData fallbackData = builderBidOrFallbackData.getFallbackData().orElseThrow();
    final FallbackReason fallbackReason = fallbackData.getReason();
    if (fallbackReason == FallbackReason.BUILDER_HEADER_NOT_AVAILABLE
        || fallbackReason == FallbackReason.BUILDER_ERROR
        || fallbackReason == FallbackReason.LOCAL_BLOCK_VALUE_WON) {
      // we expect both builder and local engine have been called
      verifyBuilderCalled(slot, executionPayloadContext);
    } else {
      // we expect only local engine have been called
      verifyNoInteractions(builderClient);
    }
    verifyEngineCalled(executionPayloadContext, slot);

    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(slot);

    // we expect result from the cached payload
    assertThat(
            executionLayerManager.builderGetPayload(
                signedBlindedBeaconBlock,
                (aSlot) ->
                    Optional.of(
                        ExecutionPayloadResult.createForBuilderFlow(
                            executionPayloadContext,
                            SafeFuture.completedFuture(builderBidOrFallbackData)))))
        .isCompletedWithValue(BuilderPayloadOrFallbackData.create(fallbackData));

    // we expect no additional calls
    verifyNoMoreInteractions(builderClient);
    verifyNoMoreInteractions(executionClientHandler);

    verifySourceCounter(Source.BUILDER_LOCAL_EL_FALLBACK, fallbackReason);

    return fallbackData;
  }

  private ExecutionPayload prepareBuilderGetPayloadResponse(
      final SignedBlockContainer signedBlindedBlockContainer) {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    when(builderClient.getPayload(signedBlindedBlockContainer.getSignedBlock()))
        .thenReturn(SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(payload)));
    return payload;
  }

  private ExecutionPayloadAndBlobsBundle prepareBuilderGetPayloadResponseWithBlobs(
      final SignedBlockContainer signedBlindedBlockContainer) {
    final ExecutionPayloadAndBlobsBundle payloadAndBlobsBundle =
        dataStructureUtil.randomExecutionPayloadAndBlobsBundle();
    when(builderClient.getPayload(signedBlindedBlockContainer.getSignedBlock()))
        .thenReturn(
            SafeFuture.completedFuture(Response.fromPayloadReceivedAsJson(payloadAndBlobsBundle)));
    return payloadAndBlobsBundle;
  }

  private void prepareBuilderGetPayloadV2InFulu(
      final SignedBlockContainer signedBlindedBlockContainer) {
    when(builderClient.getPayloadV2(signedBlindedBlockContainer.getSignedBlock()))
        .thenReturn(SafeFuture.completedFuture(null));
  }

  private GetPayloadResponse prepareEngineGetPayloadResponse(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt256 executionPayloadValue,
      final UInt64 slot) {
    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(dataStructureUtil.randomExecutionPayload(), executionPayloadValue);
    when(executionClientHandler.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(getPayloadResponse));
    return getPayloadResponse;
  }

  private GetPayloadResponse prepareEngineGetPayloadResponseWithBlobs(
      final ExecutionPayloadContext executionPayloadContext,
      final UInt256 executionPayloadValue,
      final UInt64 slot) {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(payload, executionPayloadValue, blobsBundle, false);
    when(executionClientHandler.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(getPayloadResponse));
    return getPayloadResponse;
  }

  private ExecutionLayerManagerImpl createExecutionLayerChannelImpl(
      final boolean builderEnabled, final boolean builderValidatorEnabled) {
    when(builderCircuitBreaker.isEngaged(any())).thenReturn(false);
    return ExecutionLayerManagerImpl.create(
        eventLogger,
        executionClientHandler,
        spec,
        builderEnabled ? Optional.of(builderClient) : Optional.empty(),
        stubMetricsSystem,
        builderValidatorEnabled
            ? new BuilderBidValidatorImpl(spec, eventLogger)
            : BuilderBidValidator.NOOP,
        builderCircuitBreaker,
        BUILDER_BOOST_FACTOR_MAX_PROFIT,
        true);
  }

  private void updateBuilderStatus(
      final SafeFuture<Response<Void>> builderClientResponse, final UInt64 slot) {
    when(builderClient.status()).thenReturn(builderClientResponse);
    // trigger update of the builder status
    executionLayerManager.onSlot(slot);
  }

  private void setBuilderOffline() {
    setBuilderOffline(UInt64.ONE);
  }

  private void setBuilderOffline(final UInt64 slot) {
    updateBuilderStatus(SafeFuture.completedFuture(Response.fromErrorMessage("oops")), slot);
    reset(builderClient);
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isFalse();
  }

  private void setBuilderOnline() {
    updateBuilderStatus(SafeFuture.completedFuture(Response.fromNullPayload()), UInt64.ONE);
    reset(builderClient);
    assertThat(executionLayerManager.getExecutionBuilderModule().isBuilderAvailable()).isTrue();
  }

  private void verifyBuilderCalled(
      final UInt64 slot, final ExecutionPayloadContext executionPayloadContext) {
    verify(builderClient)
        .getHeader(
            slot,
            executionPayloadContext
                .getPayloadBuildingAttributes()
                .getValidatorRegistrationPublicKey()
                .orElseThrow(),
            executionPayloadContext.getParentHash());
  }

  private void verifyEngineCalled(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    verify(executionClientHandler).engineGetPayload(executionPayloadContext, slot);
  }

  private void verifySourceCounter(final Source source, final FallbackReason reason) {
    final long actualCount =
        stubMetricsSystem.getLabelledCounterValue(
            TekuMetricCategory.BEACON,
            "execution_payload_source_total",
            source.toString(),
            reason.toString());
    assertThat(actualCount).isOne();
  }
}
