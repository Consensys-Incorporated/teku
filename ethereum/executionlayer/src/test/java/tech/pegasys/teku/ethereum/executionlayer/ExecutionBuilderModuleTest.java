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

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionBuilderModuleTest {

  private LogCaptor logCaptor;

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final UInt64 slot = UInt64.ONE;

  private final ExecutionLayerManagerImpl executionLayerManager =
      mock(ExecutionLayerManagerImpl.class);
  private final BuilderBidValidator builderBidValidator = mock(BuilderBidValidator.class);
  private final BuilderCircuitBreaker builderCircuitBreaker = mock(BuilderCircuitBreaker.class);
  private final BuilderClient builderClient = Mockito.mock(BuilderClient.class);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BeaconState state = dataStructureUtil.randomBeaconState(slot);

  private ExecutionPayloadContext executionPayloadContext;
  private ExecutionBuilderModule executionBuilderModule;

  @BeforeEach
  public void setUp() {
    logCaptor = LogCaptor.forClass(ExecutionBuilderModule.class);
    preparePayloadExecutionContext();
    when(builderCircuitBreaker.isEngaged(any())).thenReturn(false);
  }

  @AfterEach
  public void tearDown() {
    logCaptor.close();
  }

  @ParameterizedTest(name = "Scenario: {0}")
  @MethodSource("generateComparisonFactorScenarios")
  void builderGetHeader_shouldRespectComparisonFactors(
      final String scenario,
      final UInt256 localValue,
      final UInt256 builderValue,
      final UInt64 beaconNodeComparisonFactor,
      final Optional<UInt64> requestedComparisonFactor,
      final boolean localShouldWin,
      final String comparisonLogMessage) {

    prepareExecutionBuilderModule(beaconNodeComparisonFactor, true);

    final GetPayloadResponse localFallback = prepareLocalFallback(localValue, false);

    final BuilderBid builderBid = prepareBuilderGetHeaderResponse(false, builderValue);

    final SafeFuture<BuilderBidOrFallbackData> result =
        callBuilderGetHeader(requestedComparisonFactor);

    if (localShouldWin) {
      assertGetHeaderResultFallbacksToLocal(
          result, localFallback, FallbackReason.LOCAL_BLOCK_VALUE_WON);
    } else {
      assertGetHeaderResultIsFromBuilder(result, builderBid);
    }

    logCaptor.assertInfoLog(comparisonLogMessage);
  }

  private static Stream<Arguments> generateComparisonFactorScenarios() {

    // list of arguments:
    // 1. scenario name
    // 2. local execution payload value
    // 3. builder bid value
    // 4. beacon node comparison factor
    // 5. requested comparison factor
    // 6. should local payload win
    // 7. expected log message

    return Stream.of(
        arguments(
            "Local wins - MAX_PROFIT factor from BN",
            UInt256.valueOf(333_000_001_000_000L),
            UInt256.valueOf(222_000_001_000_000L),
            BUILDER_BOOST_FACTOR_MAX_PROFIT,
            Optional.empty(),
            true,
            "Local execution payload (0.000333 ETH) is chosen over builder bid (0.000222 ETH) - builder compare factor: MAX_PROFIT, source: BN."),
        arguments(
            "Builder wins - MAX_PROFIT factor from BN",
            UInt256.valueOf(222_000_001_000_000L),
            UInt256.valueOf(333_000_001_000_000L),
            BUILDER_BOOST_FACTOR_MAX_PROFIT,
            Optional.empty(),
            false,
            "Builder bid (0.000333 ETH) is chosen over local execution payload (0.000222 ETH) - builder compare factor: MAX_PROFIT, source: BN."),
        arguments(
            "Local wins - 50% factor from BN",
            UInt256.valueOf(333_000_001_000_000L).multiply(51).divide(100),
            UInt256.valueOf(333_000_001_000_000L),
            UInt64.valueOf(50),
            Optional.empty(),
            true,
            "Local execution payload (0.000170 ETH) is chosen over builder bid (0.000333 ETH) - builder compare factor: 50%, source: BN."),
        arguments(
            "Builder wins - 50% factor from BN",
            UInt256.valueOf(333_000_001_000_000L).multiply(49).divide(100),
            UInt256.valueOf(333_000_001_000_000L),
            UInt64.valueOf(50),
            Optional.empty(),
            false,
            "Builder bid (0.000333 ETH) is chosen over local execution payload (0.000163 ETH) - builder compare factor: 50%, source: BN."),
        arguments(
            "Local wins - 50% factor from BN overridden by 48% factor from VC",
            UInt256.valueOf(333_000_001_000_000L).multiply(49).divide(100),
            UInt256.valueOf(333_000_001_000_000L),
            UInt64.valueOf(50),
            Optional.of(UInt64.valueOf(48)),
            true,
            "Local execution payload (0.000163 ETH) is chosen over builder bid (0.000333 ETH) - builder compare factor: 48%, source: VC."),
        arguments(
            "Builder wins - MAX_PROFIT factor from BN overridden by PREFER_BUILDER factor from VC",
            UInt256.valueOf(333_000_001_000_000L).multiply(1_000_000),
            UInt256.valueOf(0L),
            BUILDER_BOOST_FACTOR_MAX_PROFIT,
            Optional.of(BUILDER_BOOST_FACTOR_PREFER_BUILDER),
            false,
            "Builder bid (0.000000 ETH) is chosen over local execution payload (333.000001 ETH) - builder compare factor: PREFER_BUILDER, source: VC."),
        arguments(
            "Local wins - PREFER_EXECUTION factor from BN",
            UInt256.valueOf(0L),
            UInt256.valueOf(333_000_001_000_000L).multiply(1_000_000),
            BUILDER_BOOST_FACTOR_PREFER_EXECUTION,
            Optional.empty(),
            true,
            "Local execution payload (0.000000 ETH) is chosen over builder bid (333.000001 ETH) - builder compare factor: PREFER_EXECUTION, source: BN."));
  }

  private SafeFuture<BuilderBidOrFallbackData> callBuilderGetHeader(
      final Optional<UInt64> requestedBuilderBoostFactor) {
    return executionBuilderModule.builderGetHeader(
        executionPayloadContext,
        state,
        requestedBuilderBoostFactor,
        BlockProductionPerformance.NOOP);
  }

  private void preparePayloadExecutionContext() {
    executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(
            slot, dataStructureUtil.randomBytes32(), false, true);
  }

  private GetPayloadResponse prepareLocalFallback(
      final UInt256 executionPayloadValue, final boolean shouldOverrideBuilder) {
    final GetPayloadResponse getPayloadResponse =
        new GetPayloadResponse(
            dataStructureUtil.randomExecutionPayload(),
            executionPayloadValue,
            dataStructureUtil.randomBlobsBundle(),
            shouldOverrideBuilder);
    when(executionLayerManager.engineGetPayloadForFallback(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(getPayloadResponse));

    return getPayloadResponse;
  }

  private void prepareExecutionBuilderModule(
      final UInt64 builderBidCompareFactor, final boolean useShouldOverrideBuilderFlag) {

    executionBuilderModule =
        new ExecutionBuilderModule(
            executionLayerManager,
            spec,
            builderBidValidator,
            builderCircuitBreaker,
            Optional.of(builderClient),
            eventLogger,
            builderBidCompareFactor,
            useShouldOverrideBuilderFlag);
  }

  private BuilderBid prepareBuilderGetHeaderResponse(
      final boolean prepareEmptyResponse, final UInt256 builderBlockValue) {
    final UInt64 slot = executionPayloadContext.getForkChoiceState().headBlockSlot();

    final BuilderBid builderBid =
        dataStructureUtil.randomBuilderBid(builder -> builder.value(builderBlockValue));
    final SignedBuilderBid signedBuilderBid = dataStructureUtil.randomSignedBuilderBid(builderBid);

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

  void assertGetHeaderResultIsFromBuilder(
      final SafeFuture<BuilderBidOrFallbackData> result, final BuilderBid builderBid) {
    assertThatSafeFuture(result)
        .isCompletedWithValueMatching(
            builderBidOrFallbackData -> builderBidOrFallbackData.getFallbackData().isEmpty(),
            "no fallback")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getBuilderBid()
                    .orElseThrow()
                    .getHeader()
                    .equals(builderBid.getHeader()),
            "header from builder")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getBuilderBid()
                    .orElseThrow()
                    .getOptionalBlobKzgCommitments()
                    .equals(builderBid.getOptionalBlobKzgCommitments()),
            "kzg commitments from builder")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getBuilderBid()
                    .orElseThrow()
                    .getValue()
                    .equals(builderBid.getValue()),
            "value from builder");
  }

  void assertGetHeaderResultFallbacksToLocal(
      final SafeFuture<BuilderBidOrFallbackData> result,
      final GetPayloadResponse localFallback,
      final FallbackReason reason) {
    assertThatSafeFuture(result)
        .isCompletedWithValueMatching(
            builderBidOrFallbackData -> builderBidOrFallbackData.getBuilderBid().isEmpty(),
            "no builder bid")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getFallbackData()
                    .orElseThrow()
                    .getExecutionPayload()
                    .equals(localFallback.getExecutionPayload()),
            "fallback payload equals local payload")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getFallbackData()
                    .orElseThrow()
                    .getBlobsBundle()
                    .equals(localFallback.getBlobsBundle()),
            "fallback bundle equals local bundle")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getFallbackData()
                    .orElseThrow()
                    .getExecutionPayloadValue()
                    .equals(localFallback.getExecutionPayloadValue()),
            "fallback payload value equals local payload value")
        .isCompletedWithValueMatching(
            builderBidOrFallbackData ->
                builderBidOrFallbackData.getFallbackData().orElseThrow().getReason().equals(reason),
            "fallback reason matches");
  }
}
