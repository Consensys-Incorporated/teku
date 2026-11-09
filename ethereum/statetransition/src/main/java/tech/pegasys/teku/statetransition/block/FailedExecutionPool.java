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

package tech.pegasys.teku.statetransition.block;

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
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class FailedExecutionPool {
  private static final Logger LOG = LogManager.getLogger();
  static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
  static final Duration SHORT_DELAY = Duration.ofSeconds(2);
  private static final List<Class<? extends Throwable>> TIMEOUT_EXCEPTIONS =
      List.of(InterruptedException.class, SocketTimeoutException.class, TimeoutException.class);
  private final Queue<SignedBeaconBlock> awaitingExecutionQueue = new ArrayBlockingQueue<>(10);
  private final BlockManager blockManager;
  private final AsyncRunner asyncRunner;

  private Optional<SignedBeaconBlock> retryingBlock = Optional.empty();

  private Duration currentDelay = SHORT_DELAY;

  public FailedExecutionPool(final BlockManager blockManager, final AsyncRunner asyncRunner) {
    this.blockManager = blockManager;
    this.asyncRunner = asyncRunner;
  }

  public synchronized void addFailedBlock(final SignedBeaconBlock block) {
    if (retryingBlock.isEmpty()) {
      retryingBlock = Optional.of(block);
      scheduleNextRetry();
    } else {
      if (retryingBlock.get().equals(block) || awaitingExecutionQueue.contains(block)) {
        // Already retrying this block.
        return;
      }
      if (!awaitingExecutionQueue.offer(block)) {
        LOG.info(
            "Discarding block {} as execution retry pool capacity exceeded", block::toLogString);
      }
    }
  }

  private synchronized void handleExecutionResult(
      final SignedBeaconBlock block, final BlockImportResult importResult) {
    if (importResult.hasFailedExecutingExecutionPayload()) {
      currentDelay = currentDelay.multipliedBy(2);
      if (currentDelay.compareTo(MAX_RETRY_DELAY) > 0) {
        currentDelay = MAX_RETRY_DELAY;
      }
      if (awaitingExecutionQueue.isEmpty() || isTimeout(importResult)) {
        scheduleNextRetry();
      } else {
        // Try a different block
        final SignedBeaconBlock nextBlock = awaitingExecutionQueue.remove();
        awaitingExecutionQueue.add(block);
        retryingBlock = Optional.of(nextBlock);
        scheduleNextRetry();
      }
    } else {
      currentDelay = SHORT_DELAY;
      retryingBlock = Optional.ofNullable(awaitingExecutionQueue.poll());
      retryingBlock.ifPresent(this::retryExecution);
    }
  }

  private boolean isTimeout(final BlockImportResult importResult) {
    return importResult
        .getFailureCause()
        .map(
            error ->
                TIMEOUT_EXCEPTIONS.stream().anyMatch(type -> ExceptionUtil.hasCause(error, type)))
        .orElse(false);
  }

  private synchronized void scheduleNextRetry() {
    retryingBlock.ifPresent(
        block ->
            asyncRunner
                .runAfterDelay(() -> retryExecution(block), currentDelay)
                .finishStackTrace());
  }

  private synchronized void retryExecution(final SignedBeaconBlock block) {
    LOG.info("Retrying execution of block {}", block.toLogString());
    SafeFuture.of(
            () ->
                blockManager
                    .importBlock(block)
                    .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult))
        .exceptionally(BlockImportResult::internalError)
        .thenAccept(result -> handleExecutionResult(block, result))
        .finish(
            error -> {
              if (!(Throwables.getRootCause(error) instanceof ShuttingDownException)) {
                LOG.error("Failed re-execution of block", error);
                scheduleNextRetry();
              }
            });
  }
}
