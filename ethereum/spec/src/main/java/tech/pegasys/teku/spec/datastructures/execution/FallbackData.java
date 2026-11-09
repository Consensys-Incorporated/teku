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

package tech.pegasys.teku.spec.datastructures.execution;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;

public class FallbackData {

  private final GetPayloadResponse getPayloadResponse;
  private final FallbackReason reason;

  public FallbackData(final GetPayloadResponse getPayloadResponse, final FallbackReason reason) {
    this.getPayloadResponse = getPayloadResponse;
    this.reason = reason;
  }

  public ExecutionPayload getExecutionPayload() {
    return getPayloadResponse.getExecutionPayload();
  }

  public Optional<BlobsBundle> getBlobsBundle() {
    return getPayloadResponse.getBlobsBundle();
  }

  public UInt256 getExecutionPayloadValue() {
    return getPayloadResponse.getExecutionPayloadValue();
  }

  public Optional<ExecutionRequests> getExecutionRequests() {
    return getPayloadResponse.getExecutionRequests();
  }

  public FallbackReason getReason() {
    return reason;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FallbackData)) {
      return false;
    }
    final FallbackData that = (FallbackData) o;
    return Objects.equals(getPayloadResponse, that.getPayloadResponse) && reason == that.reason;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPayloadResponse, reason);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("getPayloadResponse", getPayloadResponse)
        .add("reason", reason)
        .toString();
  }
}
