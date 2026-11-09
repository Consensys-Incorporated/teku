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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

/**
 * Implementations of this interface MUST:
 *
 * <p>- Provide the blinded {@link SignedBeaconBlock} via {@link #getSignedBlindedBeaconBlock()},
 * which serves as the input for the unblinding process.
 *
 * <p>- Expect {@link #unblind()} to be invoked after either {@link
 * #setExecutionPayloadSupplier(Supplier)} (in Pre-Fulu mode) or {@link
 * #setCompletionSupplier(Supplier)} (in Post-Fulu mode).
 */
public interface SignedBeaconBlockUnblinder {

  void setExecutionPayloadSupplier(Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier);

  default void setCompletionSupplier(final Supplier<SafeFuture<Void>> completionFutureSupplier) {
    // do nothing
  }

  SignedBeaconBlock getSignedBlindedBeaconBlock();

  /**
   * Pre-Fulu: Returns the fully unblinded {@link SignedBeaconBlock} by combining the blinded block
   * with the corresponding {@link ExecutionPayload} provided via {@link
   * #setExecutionPayloadSupplier(Supplier)}.
   *
   * <p>Post-Fulu: If the block was successfully submitted to the Builder via {@link
   * #setCompletionSupplier(Supplier)}, the Builder is responsible for publishing the block. In this
   * case, no unblinding is required and the method returns {@code Optional.empty()}.
   */
  SafeFuture<Optional<SignedBeaconBlock>> unblind();

  default boolean isVersionFulu() {
    return false;
  }
}
