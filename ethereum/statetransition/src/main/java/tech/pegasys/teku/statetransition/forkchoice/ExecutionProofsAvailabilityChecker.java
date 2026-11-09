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

import java.time.Duration;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

public class ExecutionProofsAvailabilityChecker implements AvailabilityChecker<ExecutionProof> {
  private final ExecutionProofManager executionProofManager;
  private final SafeFuture<DataAndValidationResult<ExecutionProof>> validationResult =
      new SafeFuture<>();
  private final SignedBeaconBlock block;

  public ExecutionProofsAvailabilityChecker(
      final ExecutionProofManager executionProofManager, final SignedBeaconBlock block) {
    this.executionProofManager = executionProofManager;
    this.block = block;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    executionProofManager
        .validateBlockWithExecutionProofs(block)
        .orTimeout(Duration.ofSeconds(10))
        .propagateTo(validationResult);
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<ExecutionProof>> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public void logAvailabilityCheckResult(
      final Logger log, final DataAndValidationResult<ExecutionProof> result) {
    log.debug(
        "Data availability check for slot: {}, block_root: {} result: {}",
        block.getSlot(),
        block.getRoot(),
        result.toLogString());
  }
}
