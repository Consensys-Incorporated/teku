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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

@SuppressWarnings("ClassInitializationDeadlock")
public interface BlockImportResult {
  BlockImportResult FAILED_BLOCK_IS_FROM_FUTURE =
      new FailedBlockImportResult(FailureReason.BLOCK_IS_FROM_FUTURE, Optional.empty());
  BlockImportResult BUILDER_WITHHOLD =
      new FailedBlockImportResult(FailureReason.BUILDER_WITHHOLD, Optional.empty());
  BlockImportResult FAILED_UNKNOWN_PARENT =
      new FailedBlockImportResult(FailureReason.UNKNOWN_PARENT, Optional.empty());
  BlockImportResult FAILED_UNKNOWN_PARENT_EXECUTION_PAYLOAD =
      new FailedBlockImportResult(FailureReason.UNKNOWN_PARENT_EXECUTION_PAYLOAD, Optional.empty());
  BlockImportResult FAILED_INVALID_ANCESTRY =
      new FailedBlockImportResult(
          FailureReason.DOES_NOT_DESCEND_FROM_LATEST_FINALIZED, Optional.empty());
  BlockImportResult FAILED_WEAK_SUBJECTIVITY_CHECKS =
      new FailedBlockImportResult(FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS, Optional.empty());

  BlockImportResult FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING =
      new FailedBlockImportResult(
          FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING, Optional.empty());

  BlockImportResult FAILED_DESCENDANT_OF_INVALID_BLOCK =
      new FailedBlockImportResult(FailureReason.DESCENDANT_OF_INVALID_BLOCK, Optional.empty());

  BlockImportResult FAILED_BROADCAST_VALIDATION =
      new FailedBlockImportResult(FailureReason.FAILED_BROADCAST_VALIDATION, Optional.empty());

  static BlockImportResult failedDataAvailabilityCheckInvalid(final Optional<Throwable> cause) {
    return new FailedBlockImportResult(FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID, cause);
  }

  static BlockImportResult failedDataAvailabilityCheckNotAvailable(
      final Optional<Throwable> cause) {
    return new FailedBlockImportResult(
        FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE, cause);
  }

  static BlockImportResult failedExecutionPayloadExecution(final Throwable cause) {
    return new FailedBlockImportResult(
        FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION, Optional.of(cause));
  }

  static BlockImportResult failedStateTransition(final Exception cause) {
    return new FailedBlockImportResult(FailureReason.FAILED_STATE_TRANSITION, Optional.of(cause));
  }

  static BlockImportResult internalError(final Throwable cause) {
    return new FailedBlockImportResult(FailureReason.INTERNAL_ERROR, Optional.of(cause));
  }

  static BlockImportResult successful(final SignedBeaconBlock block) {
    return new SuccessfulBlockImportResult(block);
  }

  static BlockImportResult optimisticallySuccessful(final SignedBeaconBlock block) {
    return new OptimisticSuccessfulBlockImportResult(block);
  }

  static BlockImportResult knownBlock(final SignedBeaconBlock block, final boolean isOptimistic) {
    return isOptimistic
        ? new OptimisticSuccessfulBlockImportResult(block)
        : new SuccessfulBlockImportResult(block);
  }

  enum FailureReason {
    UNKNOWN_PARENT,
    UNKNOWN_PARENT_EXECUTION_PAYLOAD,
    BLOCK_IS_FROM_FUTURE,
    BUILDER_WITHHOLD,
    DOES_NOT_DESCEND_FROM_LATEST_FINALIZED,
    FAILED_STATE_TRANSITION,
    FAILED_WEAK_SUBJECTIVITY_CHECKS,
    FAILED_EXECUTION_PAYLOAD_EXECUTION,
    FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING,
    DESCENDANT_OF_INVALID_BLOCK,
    FAILED_DATA_AVAILABILITY_CHECK_INVALID,
    FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE,
    FAILED_BROADCAST_VALIDATION,
    INTERNAL_ERROR // A catch-all category for unexpected errors (bugs)
  }

  boolean isSuccessful();

  /**
   * @return If successful, returns a {@code SignedBeaconBlock}, otherwise returns null.
   */
  SignedBeaconBlock getBlock();

  /**
   * @return If failed, returns a non-null failure reason, otherwise returns null.
   */
  FailureReason getFailureReason();

  /**
   * @return If failed, may return a {@code Throwable} cause. If successful, this value is always
   *     empty.
   */
  Optional<Throwable> getFailureCause();

  default boolean isBlockOnCanonicalChain() {
    return false;
  }

  default boolean isImportedOptimistically() {
    return false;
  }

  default boolean hasFailedExecutingExecutionPayload() {
    return false;
  }

  default boolean isDataNotAvailable() {
    return false;
  }

  default void markAsCanonical() {
    throw new UnsupportedOperationException(
        "Only successful block imports can be marked as canonical");
  }
}
