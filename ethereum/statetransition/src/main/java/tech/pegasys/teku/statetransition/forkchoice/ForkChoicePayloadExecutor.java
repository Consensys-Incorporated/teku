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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkChoicePayloadExecutor implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionLayerChannel executionLayer;
  private final SignedBeaconBlock block;
  private final MergeTransitionBlockValidator transitionBlockValidator;

  private Optional<SafeFuture<PayloadValidationResult>> result = Optional.empty();

  ForkChoicePayloadExecutor(
      final SignedBeaconBlock block,
      final ExecutionLayerChannel executionLayer,
      final MergeTransitionBlockValidator transitionBlockValidator) {
    this.block = block;
    this.transitionBlockValidator = transitionBlockValidator;
    this.executionLayer = executionLayer;
  }

  public static ForkChoicePayloadExecutor create(
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final ExecutionLayerChannel executionLayer) {
    return new ForkChoicePayloadExecutor(
        block, executionLayer, new MergeTransitionBlockValidator(spec, recentChainData));
  }

  public SafeFuture<PayloadValidationResult> getExecutionResult() {
    return result.orElse(
        SafeFuture.completedFuture(new PayloadValidationResult(PayloadStatus.VALID)));
  }

  @Override
  public boolean optimisticallyExecute(
      final Optional<ExecutionPayloadHeader> latestExecutionPayloadHeader,
      final NewPayloadRequest payloadToExecute) {
    final ExecutionPayload executionPayload = payloadToExecute.getExecutionPayload();
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it checks the parentRoot matches
      return true;
    }
    result =
        Optional.of(
            executionLayer
                .engineNewPayload(payloadToExecute, block.getSlot())
                .thenCompose(
                    result -> {
                      if (result.hasValidStatus()) {
                        return transitionBlockValidator.verifyTransitionBlock(
                            // safe to use, it will always be present
                            latestExecutionPayloadHeader.orElseThrow(), block);
                      } else {
                        return SafeFuture.completedFuture(new PayloadValidationResult(result));
                      }
                    })
                .exceptionally(
                    error -> {
                      LOG.error("Error while validating payload", error);
                      return new PayloadValidationResult(PayloadStatus.failedExecution(error));
                    }));

    return true;
  }
}
