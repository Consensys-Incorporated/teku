/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;

/**
 * In non-blinded flow, {@link #getPayloadResponseFuture} will be present.
 *
 * <p>In blinded flow, {@link #builderBidOrFallbackDataFuture} would be present.
 */
public class ExecutionPayloadResult {

  private final ExecutionPayloadContext executionPayloadContext;
  private final Optional<SafeFuture<GetPayloadResponse>> getPayloadResponseFuture;
  private final Optional<SafeFuture<BuilderBidOrFallbackData>> builderBidOrFallbackDataFuture;

  private ExecutionPayloadResult(
      final ExecutionPayloadContext executionPayloadContext,
      final Optional<SafeFuture<GetPayloadResponse>> getPayloadResponseFuture,
      final Optional<SafeFuture<BuilderBidOrFallbackData>> builderBidOrFallbackDataFuture) {
    this.executionPayloadContext = executionPayloadContext;
    this.getPayloadResponseFuture = getPayloadResponseFuture;
    this.builderBidOrFallbackDataFuture = builderBidOrFallbackDataFuture;
  }

  public static ExecutionPayloadResult createForLocalFlow(
      final ExecutionPayloadContext executionPayloadContext,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    return new ExecutionPayloadResult(
        executionPayloadContext, Optional.of(getPayloadResponseFuture), Optional.empty());
  }

  public static ExecutionPayloadResult createForBuilderFlow(
      final ExecutionPayloadContext executionPayloadContext,
      final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture) {
    return new ExecutionPayloadResult(
        executionPayloadContext, Optional.empty(), Optional.of(builderBidOrFallbackDataFuture));
  }

  public ExecutionPayloadContext getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public SafeFuture<GetPayloadResponse> getPayloadResponseFutureFromLocalFlowRequired() {
    checkState(
        getPayloadResponseFuture.isPresent(),
        "GetPayloadResponse from local flow has been requested but it's not present");
    return getPayloadResponseFuture.get();
  }

  public Optional<SafeFuture<ExecutionPayload>> getExecutionPayloadFutureFromLocalFlow() {
    return getPayloadResponseFuture.map(
        getPayloadResponse ->
            getPayloadResponse.thenApply(GetPayloadResponse::getExecutionPayload));
  }

  public Optional<SafeFuture<Optional<BlobsBundle>>> getBlobsBundleFutureFromLocalFlow() {
    return getPayloadResponseFuture.map(
        getPayloadResponse -> getPayloadResponse.thenApply(GetPayloadResponse::getBlobsBundle));
  }

  public Optional<SafeFuture<Optional<ExecutionRequests>>>
      getExecutionRequestsFutureFromLocalFlow() {
    return getPayloadResponseFuture.map(
        getPayloadResponse ->
            getPayloadResponse.thenApply(GetPayloadResponse::getExecutionRequests));
  }

  public Optional<SafeFuture<BuilderBidOrFallbackData>> getBuilderBidOrFallbackDataFuture() {
    return builderBidOrFallbackDataFuture;
  }

  /**
   * @return the value from the local payload, the builder bid or the local fallback payload
   */
  public SafeFuture<UInt256> getExecutionPayloadValueFuture() {
    return getPayloadResponseFuture
        .map(
            getPayloadResponse ->
                getPayloadResponse.thenApply(GetPayloadResponse::getExecutionPayloadValue))
        .orElseGet(this::getExecutionPayloadValueFutureFromBuilderFlow);
  }

  public SafeFuture<UInt256> getExecutionPayloadValueFutureFromLocalFlowRequired() {
    return getPayloadResponseFutureFromLocalFlowRequired()
        .thenApply(GetPayloadResponse::getExecutionPayloadValue);
  }

  public boolean isFromLocalFlow() {
    return getPayloadResponseFuture.isPresent();
  }

  private SafeFuture<UInt256> getExecutionPayloadValueFutureFromBuilderFlow() {
    return builderBidOrFallbackDataFuture
        .orElseThrow()
        .thenApply(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getBuilderBid()
                    // from the builder bid
                    .map(BuilderBid::getValue)
                    // from the local fallback
                    .orElseGet(
                        () ->
                            builderBidOrFallbackData
                                .getFallbackDataRequired()
                                .getExecutionPayloadValue()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadResult that = (ExecutionPayloadResult) o;
    return Objects.equals(executionPayloadContext, that.executionPayloadContext)
        && Objects.equals(getPayloadResponseFuture, that.getPayloadResponseFuture)
        && Objects.equals(builderBidOrFallbackDataFuture, that.builderBidOrFallbackDataFuture);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        executionPayloadContext, getPayloadResponseFuture, builderBidOrFallbackDataFuture);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("executionPayloadContext", executionPayloadContext)
        .add("getPayloadResponseFuture", getPayloadResponseFuture)
        .add("builderBidOrFallbackDataFuture", builderBidOrFallbackDataFuture)
        .toString();
  }
}
