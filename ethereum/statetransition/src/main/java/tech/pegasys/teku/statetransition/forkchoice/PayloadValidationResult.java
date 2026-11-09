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

package tech.pegasys.teku.statetransition.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

public class PayloadValidationResult {

  public static final PayloadValidationResult VALID =
      new PayloadValidationResult(PayloadStatus.VALID);

  private final Optional<Bytes32> transitionBlockRoot;
  private final PayloadStatus status;

  public PayloadValidationResult(final Bytes32 transitionBlockRoot, final PayloadStatus status) {
    this(Optional.of(transitionBlockRoot), status);
  }

  public PayloadValidationResult(final PayloadStatus status) {
    this(Optional.empty(), status);
  }

  private PayloadValidationResult(
      final Optional<Bytes32> transitionBlockRoot, final PayloadStatus status) {
    this.transitionBlockRoot = transitionBlockRoot;
    this.status = status;
  }

  public Optional<Bytes32> getInvalidTransitionBlockRoot() {
    return status.hasInvalidStatus() ? transitionBlockRoot : Optional.empty();
  }

  public PayloadStatus getStatus() {
    return status;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PayloadValidationResult that = (PayloadValidationResult) o;
    return Objects.equals(transitionBlockRoot, that.transitionBlockRoot)
        && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transitionBlockRoot, status);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("transitionBlockRoot", transitionBlockRoot)
        .add("status", status)
        .toString();
  }
}
