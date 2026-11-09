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
import tech.pegasys.teku.infrastructure.bytes.Bytes8;

public class ForkChoiceUpdatedResult {
  private final PayloadStatus payloadStatus;
  private final Optional<Bytes8> payloadId;

  public ForkChoiceUpdatedResult(
      final PayloadStatus payloadStatus, final Optional<Bytes8> payloadId) {
    this.payloadStatus = payloadStatus;
    this.payloadId = payloadId;
  }

  public PayloadStatus getPayloadStatus() {
    return payloadStatus;
  }

  public Optional<Bytes8> getPayloadId() {
    return payloadId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkChoiceUpdatedResult that = (ForkChoiceUpdatedResult) o;
    return Objects.equals(payloadStatus, that.payloadStatus)
        && Objects.equals(payloadId, that.payloadId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payloadStatus, payloadId);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("payloadStatus", payloadStatus)
        .add("payloadId", payloadId)
        .toString();
  }
}
