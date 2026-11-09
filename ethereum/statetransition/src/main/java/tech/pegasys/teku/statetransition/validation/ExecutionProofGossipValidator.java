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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;

public class ExecutionProofGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  // TODO maybe change this to be a map of block/proof in the future
  private final Set<ExecutionProof> receivedValidExecutionProofSet;

  public static ExecutionProofGossipValidator create() {
    return new ExecutionProofGossipValidator(
        // max subnets * 2 epochs * slots per epoch 32 based on mainnet for now
        LimitedSet.createSynchronizedLRU((int) MAX_EXECUTION_PROOF_SUBNETS * 64));
  }

  public ExecutionProofGossipValidator(final Set<ExecutionProof> receivedValidExecutionProofSet) {

    this.receivedValidExecutionProofSet = receivedValidExecutionProofSet;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ExecutionProof executionProof, final UInt64 subnetId) {

    if (!executionProof.getVersion().get().equals(UInt64.ONE)) {
      LOG.trace(
          "ExecutionProof for block root {} has unsupported version {}",
          executionProof.getBlockRoot(),
          executionProof.getVersion());
      return SafeFuture.completedFuture(InternalValidationResult.reject("Unsupported version"));
    }

    // TODO need to check for other validations done in the prototype and spec
    if (!executionProof.getSubnetId().get().equals(subnetId)) {
      LOG.trace(
          "ExecutionProof for block root {} / block hash {} does not match the gossip subnetId",
          executionProof.getBlockRoot(),
          executionProof.getBlockHash());
      return SafeFuture.completedFuture(InternalValidationResult.reject("SubnetId mismatch"));
    }

    // Already seen and valid
    if (receivedValidExecutionProofSet.contains(executionProof)) {
      LOG.trace("Received duplicate execution proof {}", executionProof);
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    // some of the todos in the LH prototype apply to us atm
    // TODO: Add timing validation based on slot
    // TODO: Add block existence validation

    // Validated the execution proof
    LOG.trace(
        "Received and validated execution proof for block root {}, block hash {}",
        executionProof.getBlockRoot(),
        executionProof.getBlockHash());
    receivedValidExecutionProofSet.add(executionProof);
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }
}
