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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;

public class Eth2RpcResponseHandler<TResponse, TExpectedResult>
    implements RpcResponseHandler<TResponse> {
  private final RpcResponseListener<TResponse> listener;
  private final SafeFuture<Void> completed;
  private final SafeFuture<TExpectedResult> expectedResult;

  protected Eth2RpcResponseHandler(
      final RpcResponseListener<TResponse> listener,
      final SafeFuture<Void> completed,
      final SafeFuture<TExpectedResult> expectedResult) {
    this.listener = listener;
    this.completed = completed;
    this.expectedResult = expectedResult;
  }

  public static <T> Eth2RpcResponseHandler<T, Void> expectMultipleResponses(
      final RpcResponseListener<T> listener) {
    final SafeFuture<Void> completed = new SafeFuture<>();
    return new Eth2RpcResponseHandler<>(listener, completed, completed);
  }

  public static <T> Eth2RpcResponseHandler<T, Optional<T>> expectOptionalResponse() {
    return expectOptionalResponse(UnaryOperator.identity());
  }

  public static <T> Eth2RpcResponseHandler<T, Optional<T>> expectOptionalResponse(
      final UnaryOperator<RpcResponseListener<T>> listenerWrapper) {
    final AtomicReference<T> firstResponse = new AtomicReference<>();
    final AtomicReference<InvalidRpcResponseException> errorCapture = new AtomicReference<>();
    final RpcResponseListener<T> listener =
        listenerWrapper.apply(createSingleResponseListener(firstResponse, errorCapture));

    final SafeFuture<Void> completed = new SafeFuture<>();
    final SafeFuture<Optional<T>> resultFuture =
        applyError(completed, errorCapture)
            .thenApply(__ -> Optional.ofNullable(firstResponse.get()));

    return new Eth2RpcResponseHandler<>(listener, completed, resultFuture);
  }

  public static <T> Eth2RpcResponseHandler<T, T> expectSingleResponse() {
    final AtomicReference<T> firstResponse = new AtomicReference<>();
    final AtomicReference<InvalidRpcResponseException> errorCapture = new AtomicReference<>();
    final RpcResponseListener<T> responseHandler =
        createSingleResponseListener(firstResponse, errorCapture);

    final SafeFuture<Void> completed = new SafeFuture<>();
    final SafeFuture<T> resultFuture =
        applyError(completed, errorCapture)
            .thenApply(
                __ -> {
                  final T result = firstResponse.get();
                  if (result == null) {
                    throw new InvalidRpcResponseException(
                        "No response received when single response expected");
                  }
                  return result;
                });

    return new Eth2RpcResponseHandler<>(responseHandler, completed, resultFuture);
  }

  @SuppressWarnings("VoidUsed")
  private static SafeFuture<Void> applyError(
      final SafeFuture<Void> future,
      final AtomicReference<InvalidRpcResponseException> errorCapture) {
    return future.thenApply(
        res -> {
          InvalidRpcResponseException error = errorCapture.get();
          if (error != null) {
            throw error;
          }
          return res;
        });
  }

  private static <T> RpcResponseListener<T> createSingleResponseListener(
      final AtomicReference<T> firstResponse,
      final AtomicReference<InvalidRpcResponseException> errorCapture) {
    return response -> {
      if (!firstResponse.compareAndSet(null, response)) {
        final InvalidRpcResponseException error =
            new InvalidRpcResponseException(
                "Received multiple responses when single response expected");
        errorCapture.set(error);
        throw error;
      }
      return SafeFuture.COMPLETE;
    };
  }

  public SafeFuture<Void> getCompletedFuture() {
    return completed;
  }

  public SafeFuture<TExpectedResult> getResult() {
    return completed.thenCompose(__ -> expectedResult);
  }

  @Override
  public void onCompleted(final Optional<? extends Throwable> error) {
    error.ifPresentOrElse(completed::completeExceptionally, () -> completed.complete(null));
  }

  @Override
  public SafeFuture<?> onResponse(final TResponse response) {
    return SafeFuture.of(() -> listener.onResponse(response));
  }

  public static class InvalidRpcResponseException extends RuntimeException {

    public InvalidRpcResponseException(final String errorMessage) {
      super(errorMessage);
    }
  }
}
