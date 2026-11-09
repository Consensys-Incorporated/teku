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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

public class PayloadStatusV1 {
  private final ExecutionPayloadStatus status;

  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 latestValidHash;

  private final String validationError;

  public PayloadStatusV1(
      final @JsonProperty("status") ExecutionPayloadStatus status,
      final @JsonProperty("latestValidHash") Bytes32 latestValidHash,
      final @JsonProperty("validationError") String validationError) {
    checkNotNull(status, "status");
    this.status = status;
    this.latestValidHash = latestValidHash;
    this.validationError = validationError;
  }

  public PayloadStatus asInternalExecutionPayload() {
    return PayloadStatus.create(
        status, Optional.ofNullable(latestValidHash), Optional.ofNullable(validationError));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PayloadStatusV1 that = (PayloadStatusV1) o;
    return Objects.equals(status, that.status)
        && Objects.equals(latestValidHash, that.latestValidHash)
        && Objects.equals(validationError, that.validationError);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, latestValidHash, validationError);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("latestValidHash", latestValidHash)
        .add("validationError", validationError)
        .toString();
  }
}
