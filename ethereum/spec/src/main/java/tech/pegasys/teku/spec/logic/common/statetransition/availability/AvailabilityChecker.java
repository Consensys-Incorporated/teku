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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import static tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult.notRequiredResultFuture;

import java.util.Optional;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface AvailabilityChecker<Data> {
  class NOOP<Data> implements AvailabilityChecker<Data> {
    private NOOP() {}

    @Override
    public boolean initiateDataAvailabilityCheck() {
      return true;
    }

    @Override
    public SafeFuture<DataAndValidationResult<Data>> getAvailabilityCheckResult() {
      return notRequiredResultFuture();
    }

    @Override
    public SafeFuture<DataAndValidationResult<Data>> getAndLogAvailabilityCheckResult(
        final Logger log) {
      return getAvailabilityCheckResult();
    }
  }

  AvailabilityChecker<?> NOOP = new NOOP<>();
  AvailabilityChecker<BlobSidecar> NOOP_BLOB_SIDECAR = new NOOP<>();
  AvailabilityChecker<UInt64> NOOP_DATACOLUMN_SIDECAR = new NOOP<>();
  AvailabilityChecker<ExecutionProof> NOOP_EXECUTION_PROOF = new NOOP<>();

  /**
   * Similar to {@link OptimisticExecutionPayloadExecutor#optimisticallyExecute(Optional,
   * NewPayloadRequest)}
   *
   * @return true if data availability check is initiated or false to immediately fail the
   *     validation
   */
  boolean initiateDataAvailabilityCheck();

  SafeFuture<DataAndValidationResult<Data>> getAvailabilityCheckResult();

  default SafeFuture<DataAndValidationResult<Data>> getAndLogAvailabilityCheckResult(
      final Logger log) {
    return getAvailabilityCheckResult().thenPeek(result -> logAvailabilityCheckResult(log, result));
  }

  default void logAvailabilityCheckResult(
      final Logger log, final DataAndValidationResult<Data> result) {
    log.debug("Data availability check result: {}", result.toLogString());
  }
}
