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

package tech.pegasys.teku.spec.logic.versions.bellatrix.block;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;

public interface OptimisticExecutionPayloadExecutor {

  /**
   * At least begins execution of the specified payload, which may complete asynchronously. Note
   * that a {@code true} value does NOT indicate the payload is valid only that it is not
   * immediately found to be invalid and can be optimistically accepted.
   *
   * @param latestExecutionPayloadHeader the latest execution payload header from the pre-state
   * @param payloadToExecute the {@link NewPayloadRequest} to execute
   * @return true if the payload should be optimistically accepted or false to * immediately
   *     invalidate the payload
   */
  boolean optimisticallyExecute(
      Optional<ExecutionPayloadHeader> latestExecutionPayloadHeader,
      NewPayloadRequest payloadToExecute);
}
