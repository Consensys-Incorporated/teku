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

package tech.pegasys.teku.spec.executionlayer;

import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Designed to handle specification defined ordering and caching of block production operations.
 *
 * <p>Always use this manager instead of using {@link ExecutionLayerChannel} directly for block
 * production-related activities
 */
public interface ExecutionLayerBlockProductionManager {
  ExecutionLayerBlockProductionManager NOOP =
      new ExecutionLayerBlockProductionManager() {
        @Override
        public ExecutionPayloadResult initiateBlockProduction(
            final ExecutionPayloadContext context,
            final BeaconState blockSlotState,
            final boolean attemptBuilderFlow,
            final Optional<UInt64> requestedBuilderBoostFactor,
            final BlockProductionPerformance blockProductionPerformance) {
          return null;
        }

        @Override
        public Optional<ExecutionPayloadResult> getCachedPayloadResult(final UInt64 slot) {
          return Optional.empty();
        }

        @Override
        public SafeFuture<BuilderPayloadOrFallbackData> getUnblindedPayload(
            final SignedBeaconBlock signedBeaconBlock,
            final BlockPublishingPerformance blockPublishingPerformance) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public Optional<BuilderPayloadOrFallbackData> getCachedUnblindedPayload(final UInt64 slot) {
          return Optional.empty();
        }
      };

  /**
   * Initiates block (and sidecar blobs after Deneb) production flow with execution client or
   * builder
   *
   * @param context context required for the production flow
   * @param blockSlotState pre-state
   * @param attemptBuilderFlow set if builder flow should be attempted
   * @param requestedBuilderBoostFactor The proposer boost factor requested by VC
   * @param blockProductionPerformance Block production performance tracker
   * @return {@link ExecutionPayloadResult} coming from local, builder or a local fallback
   */
  ExecutionPayloadResult initiateBlockProduction(
      ExecutionPayloadContext context,
      BeaconState blockSlotState,
      boolean attemptBuilderFlow,
      Optional<UInt64> requestedBuilderBoostFactor,
      BlockProductionPerformance blockProductionPerformance);

  /**
   * Requires {@link #initiateBlockProduction(ExecutionPayloadContext, BeaconState, boolean,
   * Optional, BlockProductionPerformance)} to have been called first in order for a value to be
   * present
   */
  Optional<ExecutionPayloadResult> getCachedPayloadResult(UInt64 slot);

  SafeFuture<BuilderPayloadOrFallbackData> getUnblindedPayload(
      SignedBeaconBlock signedBeaconBlock, BlockPublishingPerformance blockPublishingPerformance);

  /**
   * Requires {@link #getUnblindedPayload(SignedBeaconBlock, BlockPublishingPerformance)} to have
   * been called first in order for a value to be present
   */
  Optional<BuilderPayloadOrFallbackData> getCachedUnblindedPayload(UInt64 slot);
}
