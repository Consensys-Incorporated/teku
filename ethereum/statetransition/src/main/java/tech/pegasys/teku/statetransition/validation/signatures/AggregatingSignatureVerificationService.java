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

package tech.pegasys.teku.statetransition.validation.signatures;

import static java.util.Collections.singletonList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.SameMessageBatchVerifier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsQuantileHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;

public class AggregatingSignatureVerificationService extends SignatureVerificationService {
  private static final Logger LOG = LogManager.getLogger();

  static final int DEFAULT_MIN_BATCH_SIZE_TO_SPLIT = 25;

  private static final Duration IDLE_POLL_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration GATHER_LOCK_TIMEOUT = Duration.ofSeconds(1);
  private static final int BATCH_GATHER_TARGET_SIZE = 64;

  private final AsyncRunner completionRunner;
  private final int numThreads;
  private final int maxBatchSize;
  private final int minBatchSizeToSplit;
  private final boolean strictThreadLimitEnabled;
  private final SignatureVerificationMode mode;
  private final long batchGatherTimeoutNanos;
  private final int batchGatherTargetSize;

  @VisibleForTesting final BlockingQueue<SignatureTask> batchSignatureTasks;
  // GATHER_AND_GROUP only: one worker gathers a batch at a time, otherwise tasks arriving during
  // the gather window would be spread one-by-one across all idle workers instead of forming a
  // single batch
  private final ReentrantLock gatherLock = new ReentrantLock();
  private final AsyncRunner asyncRunner;
  private final Counter batchCounter;
  private final Counter taskCounter;
  private final MetricsQuantileHistogram batchSizeHistogram;
  private final MetricsQuantileHistogram batchGatherTimeHistogram;

  @VisibleForTesting
  AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner completionRunner,
      final int numThreads,
      final int queueCapacity,
      final int maxBatchSize,
      final int minBatchSizeToSplit,
      final boolean strictThreadLimitEnabled,
      final SignatureVerificationMode mode,
      final Duration batchGatherTimeout) {
    this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
    this.asyncRunner = asyncRunnerFactory.create(this.getClass().getSimpleName(), this.numThreads);
    this.completionRunner = completionRunner;
    this.maxBatchSize = maxBatchSize;

    this.batchSignatureTasks = new ArrayBlockingQueue<>(queueCapacity);
    this.minBatchSizeToSplit = minBatchSizeToSplit;
    this.strictThreadLimitEnabled = strictThreadLimitEnabled;
    this.mode = mode;
    this.batchGatherTimeoutNanos = batchGatherTimeout.toNanos();
    this.batchGatherTargetSize = Math.min(BATCH_GATHER_TARGET_SIZE, maxBatchSize);
    metricsSystem.createGauge(
        TekuMetricCategory.EXECUTOR,
        "signature_verifications_queue_size",
        "Tracks number of signatures waiting to be batch verified",
        this::getQueueSize);
    batchCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.EXECUTOR,
            "signature_verifications_batch_count_total",
            "Reports the number of verification batches processed");
    taskCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.EXECUTOR,
            "signature_verifications_task_count_total",
            "Reports the number of individual verification tasks processed");
    batchSizeHistogram =
        MetricsQuantileHistogram.create(
            TekuMetricCategory.EXECUTOR,
            metricsSystem,
            "signature_verifications_batch_size",
            "Histogram of signature verification batch sizes",
            3,
            List.of());
    batchGatherTimeHistogram =
        MetricsQuantileHistogram.create(
            TekuMetricCategory.EXECUTOR,
            metricsSystem,
            "signature_verifications_batch_gather_time_micros",
            "Histogram of the time spent gathering a batch after its first task was taken, in"
                + " microseconds",
            3,
            List.of());
  }

  public AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner completionRunner,
      final int maxThreads,
      final int queueCapacity,
      final int maxBatchSize,
      final boolean strictThreadLimitEnabled,
      final SignatureVerificationMode mode,
      final Duration batchGatherTimeout) {
    this(
        metricsSystem,
        asyncRunnerFactory,
        completionRunner,
        maxThreads,
        queueCapacity,
        maxBatchSize,
        DEFAULT_MIN_BATCH_SIZE_TO_SPLIT,
        strictThreadLimitEnabled,
        mode,
        batchGatherTimeout);
  }

  @Override
  protected SafeFuture<?> doStart() {
    for (int i = 0; i < numThreads; i++) {
      asyncRunner
          .runAsync(this::run)
          .finish(
              err ->
                  AggregatingSignatureVerificationService.LOG.error(
                      "Signature Verification Task failed", err));
    }

    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    return verify(singletonList(publicKeys), singletonList(message), singletonList(signature));
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    assertIsRunning("verify");
    final SignatureTask task =
        new SignatureTask(completionRunner, publicKeys, messages, signatures);
    if (!batchSignatureTasks.offer(task)) {
      // Queue is full
      final Throwable error =
          new ServiceCapacityExceededException("Failed to process signature, queue is full.");
      task.result.completeExceptionally(error);
    }
    return task.result;
  }

  @VisibleForTesting
  void run() {
    while (isRunning()) {
      final List<SignatureTask> tasks = waitForBatch();
      if (!tasks.isEmpty()) {
        try {
          batchVerifySignatures(tasks);
        } catch (final RuntimeException ex) {
          // Batch verification can throw on malformed input (for example a BLS aggregate public
          // key at infinity). Keep the worker alive by falling back to verifying the tasks
          // individually rather than letting the exception unwind the loop and drop the thread.
          LOG.error("Unexpected error during batch signature verification", ex);
          verifyTasksIndividually(tasks);
        }
      }
      if (Thread.currentThread().isInterrupted()) {
        // waitForBatch re-sets the interrupt flag after an InterruptedException. Polling the queue
        // again would throw immediately and spin, so let the worker exit: its executor is
        // shutting down.
        if (isRunning()) {
          LOG.warn("Signature verification worker interrupted while the service is running");
        }
        return;
      }
    }
  }

  @VisibleForTesting
  void verifyTasksIndividually(final List<SignatureTask> tasks) {
    for (final SignatureTask task : tasks) {
      if (task.result.isDone()) {
        // Already verified before the failure; skip it so we don't repeat the expensive
        // verification or overwrite its result.
        continue;
      }
      try {
        task.completeAsync(batchVerify(task.publicKeys, task.messages, task.signatures));
      } catch (final RuntimeException ex) {
        // Isolate the failure to this task so the rest of the batch still completes.
        task.completeExceptionallyAsync(ex);
      }
    }
  }

  /** Batch-verifies signatures with the configured mode and thread policy. */
  private boolean batchVerify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    return switch (mode) {
      case LEGACY ->
          strictThreadLimitEnabled
              ? BLS.batchVerify(publicKeys, messages, signatures, publicKeys.size() > 1, false)
              : BLS.batchVerify(publicKeys, messages, signatures);
      case GATHER_AND_GROUP ->
          SameMessageBatchVerifier.batchVerify(
              publicKeys, messages, signatures, !strictThreadLimitEnabled);
    };
  }

  @VisibleForTesting
  List<SignatureTask> waitForBatch() {
    return mode == SignatureVerificationMode.LEGACY ? legacyWaitForBatch() : gatherBatch();
  }

  /** Takes the first queued task, waiting for one if needed, plus whatever else is queued. */
  private List<SignatureTask> legacyWaitForBatch() {
    final List<SignatureTask> tasks = new ArrayList<>();
    try {
      int batchSize = maxBatchSize;
      final SignatureTask firstTask =
          batchSignatureTasks.poll(IDLE_POLL_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
      if (firstTask != null) {
        tasks.add(firstTask);
        batchSize -= 1;
      }
      batchSignatureTasks.drainTo(tasks, batchSize);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return tasks;
  }

  /**
   * Takes the first queued task, then keeps gathering until the batch reaches the gather target or
   * the gather timeout expires. Only one worker gathers at a time.
   */
  private List<SignatureTask> gatherBatch() {
    final List<SignatureTask> tasks = new ArrayList<>();
    try {
      if (!gatherLock.tryLock(GATHER_LOCK_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
        // Let the caller re-check whether the service is still running
        return tasks;
      }
      try {
        final SignatureTask firstTask =
            batchSignatureTasks.poll(IDLE_POLL_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        if (firstTask == null) {
          return tasks;
        }
        final long gatherStart = System.nanoTime();
        tasks.add(firstTask);
        batchSignatureTasks.drainTo(tasks, maxBatchSize - tasks.size());

        // Keep gathering for a short while so that a steady trickle of tasks forms a real batch
        final long deadline = gatherStart + batchGatherTimeoutNanos;
        while (tasks.size() < batchGatherTargetSize) {
          final long remainingNanos = deadline - System.nanoTime();
          if (remainingNanos <= 0) {
            break;
          }
          final SignatureTask nextTask =
              batchSignatureTasks.poll(remainingNanos, TimeUnit.NANOSECONDS);
          if (nextTask == null) {
            break;
          }
          tasks.add(nextTask);
          batchSignatureTasks.drainTo(tasks, maxBatchSize - tasks.size());
        }
        batchGatherTimeHistogram.recordValue(
            TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - gatherStart));
      } finally {
        gatherLock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return tasks;
  }

  @VisibleForTesting
  void batchVerifySignatures(final List<SignatureTask> tasks) {
    batchCounter.inc();
    taskCounter.inc(tasks.size());
    batchSizeHistogram.recordValue(tasks.size());
    final List<List<BLSPublicKey>> allKeys = new ArrayList<>();
    final List<Bytes> allMessages = new ArrayList<>();
    final List<BLSSignature> allSignatures = new ArrayList<>();

    for (SignatureTask task : tasks) {
      allKeys.addAll(task.publicKeys);
      allMessages.addAll(task.messages);
      allSignatures.addAll(task.signatures);
    }

    final boolean batchIsValid = batchVerify(allKeys, allMessages, allSignatures);
    if (batchIsValid) {
      for (SignatureTask task : tasks) {
        task.completeAsync(true);
      }
    } else if (tasks.size() == 1) {
      // We only had 1 signature, so it must be invalid
      tasks.getFirst().completeAsync(false);
    } else if (tasks.size() >= minBatchSizeToSplit) {
      // Split up tasks and try to verify in smaller batches
      final List<List<SignatureTask>> splitTasks = splitTasks(tasks);
      for (List<SignatureTask> splitTask : splitTasks) {
        batchVerifySignatures(splitTask);
      }
    } else {
      // Validate each signature individually
      verifyTasksIndividually(tasks);
    }
  }

  @VisibleForTesting
  List<List<SignatureTask>> splitTasks(final List<SignatureTask> tasks) {
    final int splitListSize = Math.toIntExact((long) Math.ceil(tasks.size() / 2.0));
    return Lists.partition(tasks, splitListSize);
  }

  private double getQueueSize() {
    return batchSignatureTasks.size();
  }

  @VisibleForTesting
  static class SignatureTask {
    final SafeFuture<Boolean> result = new SafeFuture<>();
    private final AsyncRunner asyncRunner;
    final List<List<BLSPublicKey>> publicKeys;
    final List<Bytes> messages;
    final List<BLSSignature> signatures;

    private SignatureTask(
        final AsyncRunner asyncRunner,
        final List<List<BLSPublicKey>> publicKeys,
        final List<Bytes> messages,
        final List<BLSSignature> signatures) {
      this.asyncRunner = asyncRunner;
      this.publicKeys = publicKeys;
      this.messages = messages;
      this.signatures = signatures;
    }

    public void completeAsync(final boolean isValid) {
      asyncRunner.runAsync(() -> result.complete(isValid)).finish(result::completeExceptionally);
    }

    public void completeExceptionallyAsync(final Throwable error) {
      asyncRunner
          .runAsync(() -> result.completeExceptionally(error))
          .finish(result::completeExceptionally);
    }
  }
}
