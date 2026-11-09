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

package tech.pegasys.teku.spec.logic.common.statetransition.executionpayloadvalidator;

/**
 * Represents execution payload validation result which may contain reason exception in case of a
 * failure
 */
public class ExecutionPayloadValidationResult {

  public static final ExecutionPayloadValidationResult SUCCESSFUL =
      new ExecutionPayloadValidationResult(true);

  private final boolean isValid;
  private final String failureReason;

  private ExecutionPayloadValidationResult(final String failureReason) {
    this.failureReason = failureReason;
    this.isValid = false;
  }

  private ExecutionPayloadValidationResult(final boolean isValid) {
    this.isValid = isValid;
    failureReason = null;
  }

  public static ExecutionPayloadValidationResult failed(final String reason) {
    return new ExecutionPayloadValidationResult(reason);
  }

  public boolean isValid() {
    return isValid;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
