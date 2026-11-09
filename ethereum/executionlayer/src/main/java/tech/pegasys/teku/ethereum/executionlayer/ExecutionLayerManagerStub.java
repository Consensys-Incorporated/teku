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

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;

public class ExecutionLayerManagerStub extends ExecutionLayerChannelStub
    implements ExecutionLayerManager {
  private static final Logger LOG = LogManager.getLogger();

  private final BuilderCircuitBreaker builderCircuitBreaker;

  public ExecutionLayerManagerStub(
      final Spec spec,
      final TimeProvider timeProvider,
      final boolean enableTransitionEmulation,
      final List<String> additionalConfigs,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    super(spec, timeProvider, additionalConfigs, enableTransitionEmulation);
    this.builderCircuitBreaker = builderCircuitBreaker;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // NOOP
  }

  @Override
  public SafeFuture<BuilderBidOrFallbackData> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    final boolean builderCircuitBreakerEngaged = builderCircuitBreaker.isEngaged(state);
    LOG.info("Builder Circuit Breaker isEngaged: " + builderCircuitBreakerEngaged);

    return super.builderGetHeader(
            executionPayloadContext, state, requestedBuilderBoostFactor, blockProductionPerformance)
        .thenCompose(
            builderBidOrFallbackData -> {
              if (builderCircuitBreakerEngaged) {
                return engineGetPayload(executionPayloadContext, state)
                    .thenApply(
                        getPayloadResponse ->
                            BuilderBidOrFallbackData.create(
                                new FallbackData(
                                    getPayloadResponse, FallbackReason.CIRCUIT_BREAKER_ENGAGED)));
              } else {
                return SafeFuture.completedFuture(builderBidOrFallbackData);
              }
            });
  }
}
