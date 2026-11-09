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

package tech.pegasys.teku.spec.executionlayer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class PayloadStatus {
  public static final PayloadStatus VALID =
      new PayloadStatus(
          Optional.of(ExecutionPayloadStatus.VALID),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
  public static final PayloadStatus SYNCING =
      new PayloadStatus(
          Optional.of(ExecutionPayloadStatus.SYNCING),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
  public static final PayloadStatus ACCEPTED =
      new PayloadStatus(
          Optional.of(ExecutionPayloadStatus.ACCEPTED),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());

  public static PayloadStatus failedExecution(final Throwable cause) {
    return new PayloadStatus(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(cause));
  }

  public static PayloadStatus invalid(
      final Optional<Bytes32> latestValidHash, final Optional<String> validationError) {
    return new PayloadStatus(
        Optional.of(ExecutionPayloadStatus.INVALID),
        latestValidHash,
        validationError,
        Optional.empty());
  }

  public static PayloadStatus valid(
      final Optional<Bytes32> latestValidHash, final Optional<String> validationError) {
    return new PayloadStatus(
        Optional.of(ExecutionPayloadStatus.VALID),
        latestValidHash,
        validationError,
        Optional.empty());
  }

  public static PayloadStatus create(
      final ExecutionPayloadStatus status,
      final Optional<Bytes32> latestValidHash,
      final Optional<String> validationError) {
    return new PayloadStatus(
        Optional.of(status), latestValidHash, validationError, Optional.empty());
  }

  private final Optional<ExecutionPayloadStatus> status;
  private final Optional<Bytes32> latestValidHash;
  private final Optional<String> validationError;
  private final Optional<Throwable> failureCause;

  private PayloadStatus(
      final Optional<ExecutionPayloadStatus> status,
      final Optional<Bytes32> latestValidHash,
      final Optional<String> validationError,
      final Optional<Throwable> failureCause) {
    this.status = status;
    this.latestValidHash = latestValidHash;
    this.validationError = validationError;
    this.failureCause = failureCause;
  }

  public boolean hasFailedExecution() {
    return failureCause.isPresent();
  }

  public Optional<Throwable> getFailureCause() {
    return failureCause;
  }

  public Optional<ExecutionPayloadStatus> getStatus() {
    return status;
  }

  public boolean hasStatus(final ExecutionPayloadStatus status) {
    return this.status.map(s -> s == status).orElse(false);
  }

  public boolean hasValidStatus() {
    return this.status.map(ExecutionPayloadStatus::isValid).orElse(false);
  }

  public boolean hasNotValidatedStatus() {
    return this.status.map(ExecutionPayloadStatus::isNotValidated).orElse(false);
  }

  public boolean hasInvalidStatus() {
    return this.status.map(ExecutionPayloadStatus::isInvalid).orElse(false);
  }

  public Optional<Bytes32> getLatestValidHash() {
    return latestValidHash;
  }

  public Optional<String> getValidationError() {
    return validationError;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PayloadStatus that = (PayloadStatus) o;
    return Objects.equals(status, that.status)
        && Objects.equals(latestValidHash, that.latestValidHash)
        && Objects.equals(validationError, that.validationError)
        && Objects.equals(failureCause, that.failureCause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, latestValidHash, validationError, failureCause);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("latestValidHash", latestValidHash)
        .add("validationError", validationError)
        .add("failureCause", failureCause)
        .toString();
  }
}
