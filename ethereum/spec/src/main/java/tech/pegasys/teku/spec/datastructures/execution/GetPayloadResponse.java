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

public class GetPayloadResponse {

  private final ExecutionPayload executionPayload;
  private final UInt256 executionPayloadValue;
  private final Optional<BlobsBundle> blobsBundle;
  private final boolean shouldOverrideBuilder;
  private final Optional<ExecutionRequests> executionRequests;

  public GetPayloadResponse(final ExecutionPayload executionPayload) {
    this.executionPayload = executionPayload;
    this.executionPayloadValue = UInt256.ZERO;
    this.blobsBundle = Optional.empty();
    this.shouldOverrideBuilder = false;
    this.executionRequests = Optional.empty();
  }

  public GetPayloadResponse(
      final ExecutionPayload executionPayload, final UInt256 executionPayloadValue) {
    this.executionPayload = executionPayload;
    this.executionPayloadValue = executionPayloadValue;
    this.blobsBundle = Optional.empty();
    this.shouldOverrideBuilder = false;
    this.executionRequests = Optional.empty();
  }

  public GetPayloadResponse(
      final ExecutionPayload executionPayload,
      final UInt256 executionPayloadValue,
      final BlobsBundle blobsBundle,
      final boolean shouldOverrideBuilder) {
    this.executionPayload = executionPayload;
    this.executionPayloadValue = executionPayloadValue;
    this.blobsBundle = Optional.of(blobsBundle);
    this.shouldOverrideBuilder = shouldOverrideBuilder;
    this.executionRequests = Optional.empty();
  }

  public GetPayloadResponse(
      final ExecutionPayload executionPayload,
      final UInt256 executionPayloadValue,
      final BlobsBundle blobsBundle,
      final boolean shouldOverrideBuilder,
      final ExecutionRequests executionRequests) {
    this.executionPayload = executionPayload;
    this.executionPayloadValue = executionPayloadValue;
    this.blobsBundle = Optional.of(blobsBundle);
    this.shouldOverrideBuilder = shouldOverrideBuilder;
    this.executionRequests = Optional.of(executionRequests);
  }

  public ExecutionPayload getExecutionPayload() {
    return executionPayload;
  }

  public UInt256 getExecutionPayloadValue() {
    return executionPayloadValue;
  }

  public Optional<BlobsBundle> getBlobsBundle() {
    return blobsBundle;
  }

  public boolean getShouldOverrideBuilder() {
    return shouldOverrideBuilder;
  }

  public Optional<ExecutionRequests> getExecutionRequests() {
    return executionRequests;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GetPayloadResponse)) {
      return false;
    }
    final GetPayloadResponse that = (GetPayloadResponse) o;
    return shouldOverrideBuilder == that.shouldOverrideBuilder
        && Objects.equals(executionPayload, that.executionPayload)
        && Objects.equals(executionPayloadValue, that.executionPayloadValue)
        && Objects.equals(blobsBundle, that.blobsBundle)
        && Objects.equals(executionRequests, that.executionRequests);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayload,
        executionPayloadValue,
        blobsBundle,
        shouldOverrideBuilder,
        executionRequests);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayload", executionPayload)
        .add("executionPayloadValue", executionPayloadValue)
        .add("blobsBundle", blobsBundle)
        .add("shouldOverrideBuilder", shouldOverrideBuilder)
        .add("executionRequests", executionRequests)
        .toString();
  }
}
