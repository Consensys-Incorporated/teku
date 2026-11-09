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

package tech.pegasys.teku.spec.logic.common.statetransition.results;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

@SuppressWarnings("ClassInitializationDeadlock")
public interface ExecutionPayloadImportResult {

  ExecutionPayloadImportResult FAILED_UNKNOWN_BEACON_BLOCK_ROOT =
      new FailedExecutionPayloadImportResult(
          FailureReason.UNKNOWN_BEACON_BLOCK_ROOT, Optional.empty());

  static ExecutionPayloadImportResult failedVerification(final Exception cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_VERIFICATION, Optional.of(cause));
  }

  static ExecutionPayloadImportResult failedExecution(final Throwable cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_EXECUTION, Optional.of(cause));
  }

  static ExecutionPayloadImportResult failedDataAvailabilityCheckInvalid(
      final Optional<Throwable> cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID, cause);
  }

  static ExecutionPayloadImportResult failedDataAvailabilityCheckNotAvailable(
      final Optional<Throwable> cause) {
    return new FailedExecutionPayloadImportResult(
        FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE, cause);
  }

  static ExecutionPayloadImportResult internalError(final Throwable cause) {
    return new FailedExecutionPayloadImportResult(FailureReason.INTERNAL_ERROR, Optional.of(cause));
  }

  static ExecutionPayloadImportResult successful(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return new SuccessfulExecutionPayloadImportResult(executionPayload);
  }

  static ExecutionPayloadImportResult optimisticallySuccessful(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return new OptimisticSuccessfulExecutionPayloadImportResult(executionPayload);
  }

  enum FailureReason {
    UNKNOWN_BEACON_BLOCK_ROOT,
    FAILED_VERIFICATION,
    FAILED_EXECUTION,
    FAILED_DATA_AVAILABILITY_CHECK_INVALID,
    FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE,
    INTERNAL_ERROR // A catch-all category for unexpected errors (bugs)
  }

  boolean isSuccessful();

  default boolean hasFailedExecution() {
    return false;
  }

  default boolean isDataNotAvailable() {
    return false;
  }

  default boolean isImportedOptimistically() {
    return false;
  }

  default String toLogString() {
    return getFailureReason()
        + getFailureCause()
            .map(ExceptionUtil::getRootCauseMessage)
            .filter(causeMessage -> !causeMessage.isBlank())
            .map(causeMessage -> " (" + causeMessage + ")")
            .orElse("");
  }

  /**
   * @return If successful, returns a {@code SignedExecutionPayloadEnvelope}, otherwise returns
   *     null.
   */
  SignedExecutionPayloadEnvelope getExecutionPayload();

  /**
   * @return If failed, returns a non-null failure reason, otherwise returns null.
   */
  FailureReason getFailureReason();

  /**
   * @return If failed, may return a {@code Throwable} cause. If successful, this value is always
   *     empty.
   */
  Optional<Throwable> getFailureCause();
}
