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

package tech.pegasys.teku.spec.datastructures.util;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AttestationProcessingResult {

  public static final AttestationProcessingResult SUCCESSFUL =
      new AttestationProcessingResult(Status.SUCCESSFUL, Optional.empty());
  public static final AttestationProcessingResult SAVED_FOR_FUTURE =
      new AttestationProcessingResult(Status.SAVED_FOR_FUTURE, Optional.empty());
  public static final AttestationProcessingResult DEFER_FOR_FORK_CHOICE =
      new AttestationProcessingResult(Status.DEFER_FORK_CHOICE_PROCESSING, Optional.empty());
  public static final AttestationProcessingResult UNKNOWN_BLOCK =
      new AttestationProcessingResult(Status.UNKNOWN_BLOCK, Optional.empty());
  public static final AttestationProcessingResult UNKNOWN_EXECUTION_PAYLOAD =
      new AttestationProcessingResult(Status.UNKNOWN_EXECUTION_PAYLOAD, Optional.empty());

  private final Status status;
  private final Optional<String> invalidReason;

  private AttestationProcessingResult(final Status status, final Optional<String> invalidReason) {
    this.status = status;
    this.invalidReason = invalidReason;
  }

  public static AttestationProcessingResult invalid(final String reason) {
    return new AttestationProcessingResult(Status.INVALID, Optional.of(reason));
  }

  public AttestationProcessingResult ifSuccessful(
      final Supplier<AttestationProcessingResult> nextStep) {
    return isSuccessful() ? nextStep.get() : this;
  }

  public SafeFuture<AttestationProcessingResult> ifSuccessfulAsync(
      final Supplier<SafeFuture<AttestationProcessingResult>> nextStep) {
    return isSuccessful() ? nextStep.get() : SafeFuture.completedFuture(this);
  }

  public void ifUnsuccessful(final Consumer<String> handler) {
    if (!isSuccessful()) {
      handler.accept(getInvalidReason());
    }
  }

  public void ifInvalid(final Consumer<String> handler) {
    if (status == Status.INVALID) {
      handler.accept(getInvalidReason());
    }
  }

  public boolean isSuccessful() {
    return status == Status.SUCCESSFUL;
  }

  public boolean isInvalid() {
    return status == Status.INVALID;
  }

  public Status getStatus() {
    return status;
  }

  public String getInvalidReason() {
    return invalidReason.orElseGet(status::toString);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AttestationProcessingResult that = (AttestationProcessingResult) o;
    return getStatus() == that.getStatus()
        && Objects.equals(getInvalidReason(), that.getInvalidReason());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getStatus(), getInvalidReason());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("invalidReason", invalidReason)
        .toString();
  }

  public enum Status {
    SUCCESSFUL,
    UNKNOWN_BLOCK,
    UNKNOWN_EXECUTION_PAYLOAD,
    SAVED_FOR_FUTURE,
    DEFER_FORK_CHOICE_PROCESSING,
    INVALID
  }
}
