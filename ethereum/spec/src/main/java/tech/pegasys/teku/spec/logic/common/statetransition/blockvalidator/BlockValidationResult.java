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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import java.util.function.Supplier;

/** Represents block validation result which may contain reason exception in case of a failure */
public class BlockValidationResult {
  public static final BlockValidationResult SUCCESSFUL = new BlockValidationResult(true);

  private final boolean isValid;
  private final String failureReason;

  private BlockValidationResult(final String failureReason) {
    this.failureReason = failureReason;
    this.isValid = false;
  }

  private BlockValidationResult(final boolean isValid) {
    this.isValid = isValid;
    failureReason = null;
  }

  public static BlockValidationResult failed(final String reason) {
    return new BlockValidationResult(reason);
  }

  public boolean isValid() {
    return isValid;
  }

  public String getFailureReason() {
    return failureReason;
  }

  @SafeVarargs
  public static BlockValidationResult allOf(final Supplier<BlockValidationResult>... checks) {
    for (Supplier<BlockValidationResult> check : checks) {
      final BlockValidationResult result = check.get();
      if (!result.isValid()) {
        return result;
      }
    }
    return SUCCESSFUL;
  }
}
