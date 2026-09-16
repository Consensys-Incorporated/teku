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

package tech.pegasys.teku.validator.coordinator.publisher;

import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

/**
 * Used to publish blocks (unblinded and blinded) and (blob sidecars/data column sidecars)
 *
 * <p>Consumers should handle a future that completes exceptionally
 */
public interface BlockPublisher {
  SafeFuture<SendSignedBlockResult> sendSignedBlock(
      SignedBlockContainer blockContainer,
      BroadcastValidationLevel broadcastValidationLevel,
      BlockPublishingPerformance blockPublishingPerformance);
}
