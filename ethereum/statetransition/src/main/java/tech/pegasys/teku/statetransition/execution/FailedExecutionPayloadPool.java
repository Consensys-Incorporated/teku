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

package tech.pegasys.teku.statetransition.execution;

import com.google.common.base.Throwables;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.storage.server.ShuttingDownException;

/**
 * Maintains a pool of execution payloads that failed execution (against the EL) and retries them
 */
public class FailedExecutionPayloadPool {

  private static final Logger LOG = LogManager.getLogger();

  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
  static final Duration SHORT_DELAY = Duration.ofSeconds(2);

  private static final List<Class<? extends Throwable>> TIMEOUT_EXCEPTIONS =
      List.of(InterruptedException.class, SocketTimeoutException.class, TimeoutException.class);

  private final Queue<SignedExecutionPayloadEnvelope> awaitingExecutionQueue =
      new ArrayBlockingQueue<>(10);
  private Optional<SignedExecutionPayloadEnvelope> retryingExecutionPayload = Optional.empty();

  private Duration currentDelay = SHORT_DELAY;

  private final ExecutionPayloadManager executionPayloadManager;
  private final AsyncRunner asyncRunner;

  public FailedExecutionPayloadPool(
      final ExecutionPayloadManager executionPayloadManager, final AsyncRunner asyncRunner) {
    this.executionPayloadManager = executionPayloadManager;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addFailedExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload) {
    if (retryingExecutionPayload.isEmpty()) {
      retryingExecutionPayload = Optional.of(executionPayload);
      scheduleNextRetry();
    } else {
      if (retryingExecutionPayload.get().equals(executionPayload)
          || awaitingExecutionQueue.contains(executionPayload)) {
        // Already retrying this execution payload.
        return;
      }
      if (!awaitingExecutionQueue.offer(executionPayload)) {
        LOG.debug(
            "Discarding execution payload {} as execution retry pool capacity exceeded",
            executionPayload::toLogString);
      }
    }
  }

  private synchronized void handleExecutionResult(
      final SignedExecutionPayloadEnvelope executionPayload,
      final ExecutionPayloadImportResult importResult) {
    if (importResult.hasFailedExecution()) {
      currentDelay = currentDelay.multipliedBy(2);
      if (currentDelay.compareTo(MAX_RETRY_DELAY) > 0) {
        currentDelay = MAX_RETRY_DELAY;
      }
      if (awaitingExecutionQueue.isEmpty() || isTimeout(importResult)) {
        scheduleNextRetry();
      } else {
        // Try a different execution payload
        final SignedExecutionPayloadEnvelope nextExecutionPayload = awaitingExecutionQueue.remove();
        awaitingExecutionQueue.add(executionPayload);
        retryingExecutionPayload = Optional.of(nextExecutionPayload);
        scheduleNextRetry();
      }
    } else {
      currentDelay = SHORT_DELAY;
      retryingExecutionPayload = Optional.ofNullable(awaitingExecutionQueue.poll());
      retryingExecutionPayload.ifPresent(this::retryExecution);
    }
  }

  private boolean isTimeout(final ExecutionPayloadImportResult importResult) {
    return importResult
        .getFailureCause()
        .map(
            error ->
                TIMEOUT_EXCEPTIONS.stream().anyMatch(type -> ExceptionUtil.hasCause(error, type)))
        .orElse(false);
  }

  private synchronized void scheduleNextRetry() {
    retryingExecutionPayload.ifPresent(
        executionPayload ->
            asyncRunner
                .runAfterDelay(() -> retryExecution(executionPayload), currentDelay)
                .finishStackTrace());
  }

  private synchronized void retryExecution(final SignedExecutionPayloadEnvelope executionPayload) {
    LOG.debug("Retrying execution of execution payload {}", executionPayload.toLogString());
    // Retried payloads may have originated from either gossip-validated or sync/RPC imports and the
    // pool does not track their provenance, so we deliberately import them as commitment-unverified
    // to avoid poisoning the invalid-payload cache with envelopes that were never proven to be the
    // block's committed payload.
    executionPayloadManager
        .importExecutionPayload(executionPayload, false)
        .thenAccept(result -> handleExecutionResult(executionPayload, result))
        .finish(
            error -> {
              if (!(Throwables.getRootCause(error) instanceof ShuttingDownException)) {
                LOG.error("Failed re-execution of block", error);
                scheduleNextRetry();
              }
            });
  }
}
