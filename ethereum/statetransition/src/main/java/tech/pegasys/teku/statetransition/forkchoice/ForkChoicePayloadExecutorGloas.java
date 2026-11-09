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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

class ForkChoicePayloadExecutorGloas implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final SignedExecutionPayloadEnvelope signedEnvelope;
  private final ExecutionLayerChannel executionLayer;

  private Optional<SafeFuture<PayloadStatus>> result = Optional.empty();

  ForkChoicePayloadExecutorGloas(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer) {
    this.signedEnvelope = signedEnvelope;
    this.executionLayer = executionLayer;
  }

  public static ForkChoicePayloadExecutorGloas create(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final ExecutionLayerChannel executionLayer) {
    return new ForkChoicePayloadExecutorGloas(signedEnvelope, executionLayer);
  }

  public SafeFuture<PayloadStatus> getExecutionResult() {
    return result.orElse(SafeFuture.completedFuture(PayloadStatus.VALID));
  }

  @Override
  public boolean optimisticallyExecute(
      final Optional<ExecutionPayloadHeader> latestExecutionPayloadHeader,
      final NewPayloadRequest payloadToExecute) {
    result =
        Optional.of(
            executionLayer
                .engineNewPayload(payloadToExecute, signedEnvelope.getSlot())
                .exceptionally(
                    error -> {
                      LOG.error("Error while validating payload", error);
                      return PayloadStatus.failedExecution(error);
                    }));
    return true;
  }
}
