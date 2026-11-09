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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.RecordingBLS12381;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.statetransition.validation.signatures.AggregatingSignatureVerificationService.SignatureTask;

public class AggregatingSignatureVerificationServiceTest {
  private static final List<BLSKeyPair> KEYS = BLSKeyGenerator.generateKeyPairs(50);

  private final int queueCapacity = 50;
  private final int batchSize = 25;
  private final int minBatchSizeToSplit = 5;
  private final int numThreads = 2;
  private final boolean strictThreadLimitEnabled = true;
  private final Duration batchGatherTimeout = Duration.ofMillis(10);
  private final StubAsyncRunner completionRunner = new StubAsyncRunner();

  private final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
  private AggregatingSignatureVerificationService service =
      new AggregatingSignatureVerificationService(
          new StubMetricsSystem(),
          asyncRunnerFactory,
          completionRunner,
          numThreads,
          queueCapacity,
          batchSize,
          minBatchSizeToSplit,
          strictThreadLimitEnabled,
          SignatureVerificationMode.GATHER_AND_GROUP,
          batchGatherTimeout);

  @Test
  public void start_shouldQueueTasks() {
    startService();
    assertThat(getRunner().countDelayedActions()).isEqualTo(numThreads);
  }

  @Test
  public void verify_beforeStarted() {
    assertThatThrownBy(() -> executeValidVerify(0, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Service must be running to execute action 'verify'");
  }

  @Test
  public void verify_afterStopping() {
    startService();
    stopService();
    assertThatThrownBy(() -> executeValidVerify(0, 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Service must be running to execute action 'verify'");
  }

  @Test
  public void verify_withFullQueue() {
    startService();

    fillQueue();
    final SafeFuture<Boolean> future = executeInvalidVerify(0, 0);

    assertThat(future).isDone();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(ServiceCapacityExceededException.class);
  }

  @Test
  public void verify_singleValidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeValidVerify(0, 0);

    assertThat(future).isNotDone();
    runPendingTasks();

    assertThat(future).isCompletedWithValue(true);
  }

  @Test
  public void verify_singleInvalidSignature() {
    startService();
    final SafeFuture<Boolean> future = executeInvalidVerify(0, 0);

    assertThat(future).isNotDone();
    runPendingTasks();

    assertThat(future).isCompletedWithValue(false);
  }

  @Test
  public void verify_validSignatures_fullBatch() {
    verifyValidSignatures(queueCapacity);
  }

  @Test
  public void verify_validSignatures_smallBatch() {
    verifyValidSignatures(minBatchSizeToSplit - 1);
  }

  @Test
  public void verify_validSignatures_listVerify() {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < batchSize; i += 5) {
      final IntList indices = new IntArrayList();
      final BooleanList useValidSignatures = new BooleanArrayList();
      for (int j = 0; j < 5; j++) {
        useValidSignatures.add(true);
        indices.add(i);
      }
      futures.add(executeListVerify(indices, indices, useValidSignatures));
    }
    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isCompletedWithValue(true);
    }
  }

  @Test
  public void verify_InvalidSignature_listVerify() {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < batchSize; i += 5) {
      final IntList indices = new IntArrayList();
      final BooleanList useValidSignatures = new BooleanArrayList();
      // batch 1 contains i: 5-9, and the second signature will be invalid
      for (int j = 0; j < 5; j++) {
        useValidSignatures.add(i != 5 || j != 1);
        indices.add(i);
      }
      futures.add(executeListVerify(indices, indices, useValidSignatures));
    }
    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    // the second future will fail due to an invalid signature in the group, but all other futures
    // pass
    for (int i = 0; i < 5; i++) {
      final SafeFuture<Boolean> future = futures.get(i);
      assertThat(future).isCompletedWithValue(i != 1);
    }
  }

  public void verifyValidSignatures(final int batchSize) {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int j = 0; j < batchSize; j++) {
      futures.add(executeValidVerify(j, j));
    }

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isCompletedWithValue(true);
    }
  }

  @Test
  public void verify_mixedSignatures_fullBatch() {
    verifyMixedSignatures(queueCapacity);
  }

  @Test
  public void verify_mixedSignatures_smallBatch() {
    verifyMixedSignatures(minBatchSizeToSplit - 1);
  }

  private void verifyMixedSignatures(final int batchSize) {
    startService();

    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int j = 0; j < batchSize; j++) {
      if (j % 3 == 0) {
        futures.add(executeInvalidVerify(j, j));
      } else {
        futures.add(executeValidVerify(j, j));
      }
    }

    for (SafeFuture<Boolean> future : futures) {
      assertThat(future).isNotDone();
    }
    runPendingTasks();

    for (int j = 0; j < batchSize; j++) {
      final SafeFuture<Boolean> future = futures.get(j);
      if (j % 3 == 0) {
        assertThat(future).isCompletedWithValue(false);
      } else {
        assertThat(future).isCompletedWithValue(true);
      }
    }
  }

  @Test
  public void testRealServiceWithThreads() throws Exception {
    final MetricsSystem metrics = new StubMetricsSystem();
    final AsyncRunnerFactory realRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metrics));
    service =
        new AggregatingSignatureVerificationService(
            metrics,
            realRunnerFactory,
            realRunnerFactory.create("completion", 1),
            1,
            queueCapacity,
            batchSize,
            minBatchSizeToSplit,
            strictThreadLimitEnabled,
            SignatureVerificationMode.GATHER_AND_GROUP,
            batchGatherTimeout);
    startService();

    final Random random = new Random(1);
    for (int i = 0; i < 3; i++) {
      final List<SafeFuture<Boolean>> validFutures = new ArrayList<>();
      final List<SafeFuture<Boolean>> invalidFutures = new ArrayList<>();
      for (int j = 0; j < queueCapacity - i; j++) {
        if (random.nextFloat() < .5) {
          validFutures.add(executeValidVerify(j, j));
        } else {
          invalidFutures.add(executeInvalidVerify(j, j));
        }
      }

      final List<SafeFuture<Boolean>> allFutures = new ArrayList<>();
      allFutures.addAll(validFutures);
      allFutures.addAll(invalidFutures);
      Waiter.waitFor(
          SafeFuture.allOf(allFutures.toArray(SafeFuture<?>[]::new)), Duration.ofSeconds(5));

      validFutures.forEach(f -> assertThat(f).isCompletedWithValue(true));
      invalidFutures.forEach(f -> assertThat(f).isCompletedWithValue(false));
    }
  }

  @Test
  public void verifyTasksIndividually_completesPendingTasksAndSkipsCompletedOnes() {
    startService();

    final SafeFuture<Boolean> alreadyCompleted = executeValidVerify(0, 0);
    final SafeFuture<Boolean> pendingValid = executeValidVerify(1, 1);
    // Mismatched key / message / signature list sizes make verification throw for this task.
    final SafeFuture<Boolean> pendingMalformed =
        service.verify(
            List.of(List.of(KEYS.get(2).getPublicKey())), List.of(Bytes.of(2)), List.of());
    final List<SignatureTask> tasks = getPendingTasks();

    // Complete the first task up front, as if it had already been verified earlier in the batch.
    tasks.getFirst().completeAsync(true);
    completionRunner.executeQueuedActions();
    assertThat(alreadyCompleted).isCompletedWithValue(true);

    service.verifyTasksIndividually(tasks);
    completionRunner.executeQueuedActions();

    // The already-completed task is left untouched, the pending valid one is verified, and the
    // malformed one fails in isolation without affecting the others.
    assertThat(alreadyCompleted).isCompletedWithValue(true);
    assertThat(pendingValid).isCompletedWithValue(true);
    assertThat(pendingMalformed).isCompletedExceptionally();
  }

  @Test
  public void run_isolatesFailureAndKeepsWorkerAliveWhenBatchVerificationThrows() throws Exception {
    final MetricsSystem metrics = new StubMetricsSystem();
    final AsyncRunnerFactory realRunnerFactory =
        AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(metrics));
    service =
        new AggregatingSignatureVerificationService(
            metrics,
            realRunnerFactory,
            realRunnerFactory.create("completion", 1),
            // Single worker thread: if a thrown batch permanently killed the worker, the following
            // valid task would hang and the test would time out.
            1,
            queueCapacity,
            batchSize,
            minBatchSizeToSplit,
            strictThreadLimitEnabled,
            SignatureVerificationMode.GATHER_AND_GROUP,
            batchGatherTimeout);
    startService();

    // Mismatched public key / message / signature list sizes make the underlying BLS batch
    // verification throw. This used to kill the worker thread permanently.
    final SafeFuture<Boolean> malformed =
        service.verify(
            List.of(List.of(KEYS.get(0).getPublicKey())), List.of(Bytes.of(1)), List.of());
    Waiter.waitFor(malformed.exceptionally(err -> false), Duration.ofSeconds(5));
    assertThat(malformed).isCompletedExceptionally();

    // The worker survived the failure, so a subsequent valid signature is still verified.
    final SafeFuture<Boolean> valid = executeValidVerify(0, 0);
    Waiter.waitFor(valid, Duration.ofSeconds(5));
    assertThat(valid).isCompletedWithValue(true);
  }

  @Test
  public void batchVerifySignatures_smallFailedBatchIsolatesAThrowingTask() {
    startService();
    // Each of these tasks throws when verified on its own (mismatched list sizes), but their sizes
    // cancel out so the combined batch is well formed: it fails rather than throws, and the small
    // batch falls back to verifying each task individually.
    final SafeFuture<Boolean> malformedMissingSignature =
        service.verify(
            List.of(List.of(KEYS.get(0).getPublicKey())), List.of(Bytes.of(0)), List.of());
    final SafeFuture<Boolean> malformedOnlySignature =
        service.verify(
            List.of(), List.of(), List.of(BLS.sign(KEYS.get(3).getSecretKey(), Bytes.of(3))));
    final SafeFuture<Boolean> valid = executeValidVerify(1, 1);
    final SafeFuture<Boolean> invalid = executeInvalidVerify(2, 2);
    final List<SignatureTask> tasks = getPendingTasks();

    service.batchVerifySignatures(tasks); // must not throw
    completionRunner.executeQueuedActions();

    assertThat(valid).isCompletedWithValue(true);
    assertThat(invalid).isCompletedWithValue(false);
    assertThat(malformedMissingSignature).isCompletedExceptionally();
    assertThat(malformedOnlySignature).isCompletedExceptionally();
  }

  @ParameterizedTest
  @EnumSource(SignatureVerificationMode.class)
  public void strictThreadLimit_fallbackVerifiesMultiSignatureTasksOnTheCallingThread(
      final SignatureVerificationMode mode) {
    final RecordingBLS12381 recording = new RecordingBLS12381(BlstLoader.INSTANCE.orElseThrow());
    BLS.setBlsImplementation(recording);
    try {
      service = createService(mode, batchGatherTimeout);
      startService();
      final SafeFuture<Boolean> multi = executeAllValidListVerify(8);
      // Two tasks whose signatures all share one message, so they go through same-message prepare
      final SafeFuture<Boolean> sameMessage1 = executeSameMessageListVerify(10, 3, 42);
      final SafeFuture<Boolean> sameMessage2 = executeSameMessageListVerify(13, 2, 42);
      final SafeFuture<Boolean> invalid = executeInvalidVerify(9, 9);
      final List<SignatureTask> tasks = getPendingTasks();

      service.batchVerifySignatures(tasks); // batch fails, falls back to individual tasks
      completionRunner.executeQueuedActions();

      assertThat(multi).isCompletedWithValue(true);
      assertThat(sameMessage1).isCompletedWithValue(true);
      assertThat(sameMessage2).isCompletedWithValue(true);
      assertThat(invalid).isCompletedWithValue(false);
      assertThat(recording.getPrepareThreadNames()).isNotEmpty();
      assertThat(recording.getPrepareThreadNames()).containsOnly(Thread.currentThread().getName());
      assertSameMessageParallelArguments(recording, mode);
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  @ParameterizedTest
  @EnumSource(SignatureVerificationMode.class)
  public void strictThreadLimit_exceptionFallbackVerifiesMultiSignatureTasksOnTheCallingThread(
      final SignatureVerificationMode mode) {
    final RecordingBLS12381 recording = new RecordingBLS12381(BlstLoader.INSTANCE.orElseThrow());
    BLS.setBlsImplementation(recording);
    try {
      service = createService(mode, batchGatherTimeout);
      startService();
      final SafeFuture<Boolean> multi = executeAllValidListVerify(8);
      // Two tasks whose signatures all share one message, so they go through same-message prepare
      final SafeFuture<Boolean> sameMessage1 = executeSameMessageListVerify(10, 3, 42);
      final SafeFuture<Boolean> sameMessage2 = executeSameMessageListVerify(13, 2, 42);
      // Mismatched key / message / signature list sizes make verification throw for this task.
      final SafeFuture<Boolean> malformed =
          service.verify(
              List.of(List.of(KEYS.get(9).getPublicKey())), List.of(Bytes.of(9)), List.of());
      final List<SignatureTask> tasks = getPendingTasks();

      service.verifyTasksIndividually(tasks);
      completionRunner.executeQueuedActions();

      assertThat(multi).isCompletedWithValue(true);
      assertThat(sameMessage1).isCompletedWithValue(true);
      assertThat(sameMessage2).isCompletedWithValue(true);
      assertThat(malformed).isCompletedExceptionally();
      assertThat(recording.getPrepareThreadNames()).isNotEmpty();
      assertThat(recording.getPrepareThreadNames()).containsOnly(Thread.currentThread().getName());
      assertSameMessageParallelArguments(recording, mode);
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_evenNumber() {
    startService();

    final int taskCount = 8;
    for (int i = 0; i < taskCount; i++) {
      executeValidVerify(0, i);
    }

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(taskCount);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(2);
    assertThat(split.get(0).size()).isEqualTo(4);
    assertThat(split.get(1).size()).isEqualTo(4);
    assertThat(split.get(0)).doesNotContainAnyElementsOf(split.get(1));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_oddNumber() {
    startService();

    final int taskCount = 7;
    for (int i = 0; i < taskCount; i++) {
      executeValidVerify(0, i);
    }

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(taskCount);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(2);
    assertThat(split.get(0).size()).isEqualTo(4);
    assertThat(split.get(1).size()).isEqualTo(3);
    assertThat(split.get(0)).doesNotContainAnyElementsOf(split.get(1));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  public void splitTasks_singleTask() {
    startService();

    executeValidVerify(0, 0);

    final List<SignatureTask> tasks = getPendingTasks();
    assertThat(tasks.size()).isEqualTo(1);
    final List<List<SignatureTask>> split = service.splitTasks(tasks);

    assertThat(split.size()).isEqualTo(1);
    assertThat(split.get(0).size()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void waitForBatch_gathersTasksArrivingWithinTheGatherWindow() throws Exception {
    // A 10s gather window with a target of 3 means the gather can only end by reaching the target,
    // so the outcome does not depend on how quickly the test submits tasks
    service = createService(SignatureVerificationMode.GATHER_AND_GROUP, Duration.ofSeconds(10), 3);
    startService();
    executeValidVerify(0, 0);

    final CompletableFuture<List<SignatureTask>> batch =
        CompletableFuture.supplyAsync(service::waitForBatch);
    waitUntilFirstTaskTaken();
    executeValidVerify(1, 1);
    // The late task is taken into the batch, which keeps gathering because the target is not met
    Waiter.waitFor(() -> assertThat(service.batchSignatureTasks).isEmpty());
    assertThat(batch).isNotDone();
    executeValidVerify(2, 2);

    assertThat(batch.get(5, TimeUnit.SECONDS)).hasSize(3);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void waitForBatch_stopsGatheringOnceTargetSizeIsReached() throws Exception {
    service = createService(SignatureVerificationMode.GATHER_AND_GROUP, Duration.ofSeconds(10), 3);
    startService();
    executeValidVerify(0, 0);

    final long start = System.nanoTime();
    final CompletableFuture<List<SignatureTask>> batch =
        CompletableFuture.supplyAsync(service::waitForBatch);
    waitUntilFirstTaskTaken();
    executeValidVerify(1, 1);
    executeValidVerify(2, 2);

    assertThat(batch.get(5, TimeUnit.SECONDS)).hasSize(3);
    // Returned as soon as the target was reached, well before the 10s gather timeout
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void waitForBatch_returnsAfterGatherTimeoutWhenTargetSizeNotReached() throws Exception {
    service = createService(SignatureVerificationMode.GATHER_AND_GROUP, Duration.ofMillis(200));
    startService();
    executeValidVerify(0, 0);

    final CompletableFuture<List<SignatureTask>> batch =
        CompletableFuture.supplyAsync(service::waitForBatch);

    assertThat(batch.get(5, TimeUnit.SECONDS)).hasSize(1);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void waitForBatch_onlyOneWorkerGathersAtATime() throws Exception {
    // A max batch size of 5 ends the gather as soon as the fifth task arrives, so the outcome does
    // not depend on how quickly the tasks are submitted relative to the gather window
    service = createService(SignatureVerificationMode.GATHER_AND_GROUP, Duration.ofSeconds(10), 5);
    startService();
    executeValidVerify(0, 0);

    final ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      final Future<List<SignatureTask>> worker1 = workers.submit(service::waitForBatch);
      final Future<List<SignatureTask>> worker2 = workers.submit(service::waitForBatch);
      waitUntilFirstTaskTaken();
      for (int i = 1; i < 5; i++) {
        executeValidVerify(i, i);
      }

      Waiter.waitFor(
          () ->
              assertThat(hasCompletedWithTasks(worker1) || hasCompletedWithTasks(worker2))
                  .isTrue());
      final boolean worker1Gathered = hasCompletedWithTasks(worker1);
      final Future<List<SignatureTask>> gatherer = worker1Gathered ? worker1 : worker2;
      final Future<List<SignatureTask>> other = worker1Gathered ? worker2 : worker1;
      // The gathering worker collected everything that arrived in its window. The other worker
      // never took a task: it is either still waiting for the gather lock or, on a slow machine,
      // gave up waiting for it empty-handed
      assertThat(gatherer.get()).hasSize(5);
      if (other.isDone()) {
        assertThat(other.get()).isEmpty();
      }
    } finally {
      workers.shutdownNow();
    }
  }

  private static boolean hasCompletedWithTasks(final Future<List<SignatureTask>> worker) {
    try {
      return worker.isDone() && !worker.get().isEmpty();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest
  @EnumSource(SignatureVerificationMode.class)
  public void run_exitsInsteadOfSpinningWhenWorkerIsInterrupted(
      final SignatureVerificationMode mode) throws Exception {
    service = createService(mode, batchGatherTimeout);
    startService();

    final Thread worker = new Thread(service::run, "interrupted-worker");
    worker.start();
    // Wait until the worker is blocked waiting for a batch, then interrupt it
    Waiter.waitFor(() -> assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING));
    worker.interrupt();
    worker.join(TimeUnit.SECONDS.toMillis(5));

    // The service is still running but the worker must not keep re-polling with the interrupt
    // flag set, which would throw immediately on every iteration
    assertThat(service.isRunning()).isTrue();
    assertThat(worker.isAlive()).isFalse();
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void legacyMode_waitForBatchReturnsWithoutGathering() {
    service = createService(SignatureVerificationMode.LEGACY, Duration.ofSeconds(10));
    startService();
    executeValidVerify(0, 0);

    final long start = System.nanoTime();
    final List<SignatureTask> batch = service.waitForBatch();

    assertThat(batch).hasSize(1);
    // Legacy mode drains whatever is queued and never waits for the 10s gather window
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  public void legacyMode_verifiesWithBlsBatchVerify() {
    final RecordingBLS12381 recording = new RecordingBLS12381(BlstLoader.INSTANCE.orElseThrow());
    BLS.setBlsImplementation(recording);
    try {
      service = createService(SignatureVerificationMode.LEGACY, batchGatherTimeout);
      final List<SafeFuture<Boolean>> futures = executeThreeValidSameMessageVerifies();

      service.batchVerifySignatures(getPendingTasks());
      completionRunner.executeQueuedActions();

      assertThat(recording.getSameMessagePrepareCount()).isZero();
      futures.forEach(future -> assertThat(future).isCompletedWithValue(true));
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  @Test
  public void gatherAndGroupMode_verifiesWithSameMessageBatchVerifier() {
    final RecordingBLS12381 recording = new RecordingBLS12381(BlstLoader.INSTANCE.orElseThrow());
    BLS.setBlsImplementation(recording);
    try {
      service = createService(SignatureVerificationMode.GATHER_AND_GROUP, batchGatherTimeout);
      final List<SafeFuture<Boolean>> futures = executeThreeValidSameMessageVerifies();

      service.batchVerifySignatures(getPendingTasks());
      completionRunner.executeQueuedActions();

      assertThat(recording.getSameMessagePrepareCount()).isEqualTo(1);
      futures.forEach(future -> assertThat(future).isCompletedWithValue(true));
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  @Test
  public void strictThreadLimit_passesParallelFalseToSameMessagePreparation() {
    assertThat(strictThreadLimitEnabled).isTrue();
    final RecordingBLS12381 recording = new RecordingBLS12381(BlstLoader.INSTANCE.orElseThrow());
    BLS.setBlsImplementation(recording);
    try {
      service = createService(SignatureVerificationMode.GATHER_AND_GROUP, batchGatherTimeout);
      final List<SafeFuture<Boolean>> futures = executeThreeValidSameMessageVerifies();

      service.batchVerifySignatures(getPendingTasks());
      completionRunner.executeQueuedActions();

      futures.forEach(future -> assertThat(future).isCompletedWithValue(true));
      assertThat(recording.getSameMessageParallelArguments()).isNotEmpty();
      assertThat(recording.getSameMessageParallelArguments()).containsOnly(false);
    } finally {
      BLS.resetBlsImplementation();
    }
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void gatherTargetIsClampedToMaxBatchSize() {
    final int maxBatchSize = 10;
    service =
        createService(
            SignatureVerificationMode.GATHER_AND_GROUP, Duration.ofSeconds(10), maxBatchSize);
    startService();
    for (int i = 0; i < maxBatchSize; i++) {
      executeValidVerify(i, i);
    }

    final long start = System.nanoTime();
    final List<SignatureTask> batch = service.waitForBatch();

    assertThat(batch).hasSize(maxBatchSize);
    // The internal gather target (64) is clamped to maxBatchSize, so a full batch returns at once
    // instead of waiting out the 10s gather timeout
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  private static void assertSameMessageParallelArguments(
      final RecordingBLS12381 recording, final SignatureVerificationMode mode) {
    if (mode == SignatureVerificationMode.LEGACY) {
      // Legacy mode never groups by message
      assertThat(recording.getSameMessagePrepareCount()).isZero();
    } else {
      assertThat(recording.getSameMessageParallelArguments()).isNotEmpty();
      assertThat(recording.getSameMessageParallelArguments()).containsOnly(false);
    }
  }

  /** Starts the service and queues three single-signature tasks that all sign one message. */
  private List<SafeFuture<Boolean>> executeThreeValidSameMessageVerifies() {
    startService();
    final Bytes message = Bytes.of(42);
    final List<SafeFuture<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final BLSKeyPair keypair = KEYS.get(i);
      futures.add(
          service.verify(
              keypair.getPublicKey(), message, BLS.sign(keypair.getSecretKey(), message)));
    }
    return futures;
  }

  /**
   * The gathering worker takes the first task out of the queue before it starts waiting for more,
   * so an empty queue means a gather is in progress and later submissions land inside it.
   */
  private void waitUntilFirstTaskTaken() {
    Waiter.waitFor(() -> assertThat(service.batchSignatureTasks).isEmpty());
  }

  private AggregatingSignatureVerificationService createService(
      final SignatureVerificationMode mode, final Duration gatherTimeout) {
    return createService(mode, gatherTimeout, batchSize);
  }

  private AggregatingSignatureVerificationService createService(
      final SignatureVerificationMode mode, final Duration gatherTimeout, final int maxBatchSize) {
    // Stub runner factory: worker threads are never actually started so only the explicit
    // waitForBatch calls in the test consume the queue
    return new AggregatingSignatureVerificationService(
        new StubMetricsSystem(),
        asyncRunnerFactory,
        completionRunner,
        numThreads,
        queueCapacity,
        maxBatchSize,
        minBatchSizeToSplit,
        strictThreadLimitEnabled,
        mode,
        gatherTimeout);
  }

  private void startService() {
    try {
      service.start().get(500, TimeUnit.MILLISECONDS);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private void stopService() {
    try {
      service.stop().get(500, TimeUnit.MILLISECONDS);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void fillQueue() {
    for (int i = 0; i < queueCapacity; i++) {
      executeValidVerify(0, i);
    }
  }

  private SafeFuture<Boolean> executeValidVerify(final int keypairIndex, final int data) {
    return executeVerify(keypairIndex, data, true);
  }

  private SafeFuture<Boolean> executeInvalidVerify(final int keypairIndex, final int data) {
    return executeVerify(keypairIndex, data, false);
  }

  private SafeFuture<Boolean> executeVerify(
      final int keypairIndex, final int data, final boolean useValidSignature) {
    final BLSKeyPair keypair = KEYS.get(keypairIndex);
    final Bytes message = Bytes.of(data);
    final BLSSignature signature =
        useValidSignature ? BLS.sign(keypair.getSecretKey(), message) : BLSSignature.empty();
    return service.verify(keypair.getPublicKey(), message, signature);
  }

  private SafeFuture<Boolean> executeListVerify(
      final IntList keyIndices, final IntList data, final BooleanList useValidSignatures) {
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = 0; i < keyIndices.size(); i++) {
      publicKeys.add(List.of(KEYS.get(i).getPublicKey()));
      messages.add(Bytes.of(data.getInt(i)));
      signatures.add(
          useValidSignatures.getBoolean(i)
              ? BLS.sign(KEYS.get(i).getSecretKey(), messages.get(i))
              : BLSSignature.empty());
    }
    return service.verify(publicKeys, messages, signatures);
  }

  /** One task with {@code count} valid signatures, each over a distinct key and message. */
  private SafeFuture<Boolean> executeAllValidListVerify(final int count) {
    final IntList indices = new IntArrayList();
    final BooleanList useValidSignatures = new BooleanArrayList();
    for (int i = 0; i < count; i++) {
      indices.add(i);
      useValidSignatures.add(true);
    }
    return executeListVerify(indices, indices, useValidSignatures);
  }

  /** One task with {@code count} valid signatures from consecutive keys, all over one message. */
  private SafeFuture<Boolean> executeSameMessageListVerify(
      final int firstKeyIndex, final int count, final int data) {
    final Bytes message = Bytes.of(data);
    final List<List<BLSPublicKey>> publicKeys = new ArrayList<>();
    final List<Bytes> messages = new ArrayList<>();
    final List<BLSSignature> signatures = new ArrayList<>();
    for (int i = firstKeyIndex; i < firstKeyIndex + count; i++) {
      publicKeys.add(List.of(KEYS.get(i).getPublicKey()));
      messages.add(message);
      signatures.add(BLS.sign(KEYS.get(i).getSecretKey(), message));
    }
    return service.verify(publicKeys, messages, signatures);
  }

  private void runPendingTasks() {
    // Get pending tasks
    final List<SignatureTask> tasks = getPendingTasks();
    service.batchVerifySignatures(tasks);
    completionRunner.executeQueuedActions();
  }

  private List<SignatureTask> getPendingTasks() {
    final List<SignatureTask> pendingTasks = new ArrayList<>();
    service.batchSignatureTasks.drainTo(pendingTasks);
    return pendingTasks;
  }

  private StubAsyncRunner getRunner() {
    final List<StubAsyncRunner> runners = asyncRunnerFactory.getStubAsyncRunners();
    assertThat(runners.size()).isEqualTo(1);
    return runners.get(0);
  }
}
