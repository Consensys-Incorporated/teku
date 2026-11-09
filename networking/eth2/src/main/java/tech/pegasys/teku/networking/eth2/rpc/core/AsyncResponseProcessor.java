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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.AdditionalDataReceivedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseHandler;

class AsyncResponseProcessor<TResponse> {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicInteger responseCount = new AtomicInteger(0);
  private final Queue<TResponse> queuedResponses = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean isProcessing = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  private final AtomicBoolean allResponsesDelivered = new AtomicBoolean(false);
  private final SafeFuture<Void> finishedProcessing = new SafeFuture<>();

  private final AsyncRunner asyncRunner;
  private final RpcResponseHandler<TResponse> responseHandler;
  private final AsyncProcessingErrorHandler onError;

  public AsyncResponseProcessor(
      final AsyncRunner asyncRunner,
      final RpcResponseHandler<TResponse> responseHandler,
      final AsyncProcessingErrorHandler onError) {
    this.asyncRunner = asyncRunner;
    this.responseHandler = responseHandler;
    this.onError = onError;
  }

  public void processResponse(final TResponse response) throws RpcException {
    if (allResponsesDelivered.get()) {
      throw new AdditionalDataReceivedException();
    }
    if (cancelled.get()) {
      LOG.trace("Request cancelled, dropping response: {}", response);
      return;
    }
    LOG.trace("Queue response for processing: {}", response);
    responseCount.incrementAndGet();
    queuedResponses.add(response);
    checkQueue();
  }

  public int getResponseCount() {
    return responseCount.get();
  }

  /** Stop processing and clear any pending requests */
  private void cancel(final Throwable error) {
    cancelled.set(true);
    queuedResponses.clear();
    finishedProcessing.completeExceptionally(error);
  }

  /**
   * Updates this processor to no longer accept new responses, and returns a future that completes
   * when existing responses are all processed.
   *
   * @return A future that completes when all currently delivered responses are finished processing.
   */
  public SafeFuture<Void> finishProcessing() {
    allResponsesDelivered.set(true);
    checkQueue();

    return finishedProcessing;
  }

  private synchronized void checkQueue() {
    if (!cancelled.get() && !isProcessing.get() && !queuedResponses.isEmpty()) {
      processNextResponse();
    } else if (allResponsesDelivered.get() && !isProcessing.get() && queuedResponses.isEmpty()) {
      LOG.trace("Finished processing responses.");
      finishedProcessing.complete(null);
    }
  }

  private void processNextResponse() {
    isProcessing.set(true);
    final TResponse response = queuedResponses.poll();
    LOG.trace("Process response: {}", response);
    asyncRunner
        .runAsync(
            () -> {
              LOG.trace("Send response to response handler: {}", response);
              return responseHandler.onResponse(response);
            })
        .exceptionally(
            (err) -> {
              LOG.trace("Failed to process response: " + response, err);
              cancel(err);
              onError.handleError(err);
              return null;
            })
        .always(
            () -> {
              LOG.trace("Finish processing: {}", response);
              isProcessing.set(false);
              checkQueue();
            });
  }

  public interface AsyncProcessingErrorHandler {
    void handleError(final Throwable error);
  }
}
