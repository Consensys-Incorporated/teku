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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@FunctionalInterface
public interface OperationProcessor<T> {
  /**
   * Process operation
   *
   * @param operation Operation
   * @param arrivalTimestamp arrival timestamp if operation is received from remote (not necessarily
   *     provided). We may consider adding meta container when we need more than only arrival time
   *     alongside, but for memory saving and module imports simplicity let's delay it until at
   *     least 2 fields are needed.
   * @return result of operation validation
   */
  SafeFuture<InternalValidationResult> process(T operation, Optional<UInt64> arrivalTimestamp);

  OperationProcessor<?> NOOP =
      (__, ___) -> SafeFuture.completedFuture(InternalValidationResult.ACCEPT);

  @SuppressWarnings("unchecked")
  static <T> OperationProcessor<T> noop() {
    return (OperationProcessor<T>) NOOP;
  }
}
