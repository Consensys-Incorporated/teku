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

package tech.pegasys.teku.infrastructure.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class AsyncRunnerTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @Test
  public void runWithRetry_successOnFirstRun() {
    final AtomicInteger runCounter = new AtomicInteger(0);
    final ExceptionThrowingFutureSupplier<Integer> futureSupplier =
        () -> SafeFuture.completedFuture(runCounter.incrementAndGet());

    final SafeFuture<Integer> res =
        asyncRunner.runWithRetry(futureSupplier, Duration.ofSeconds(5), 10);

    // We should complete immediately without any upfront retry delay
    assertThat(res).isCompletedWithValue(1);
    assertThat(runCounter.get()).isEqualTo(1);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
  }

  @Test
  public void runWithRetry_successAfterRetry() {
    final AtomicInteger runCounter = new AtomicInteger(0);
    final ExceptionThrowingFutureSupplier<Integer> futureSupplier =
        () -> {
          final int runs = runCounter.incrementAndGet();
          if (runs == 1) {
            // Fail on the first run
            return SafeFuture.failedFuture(new RuntimeException("Failed"));
          } else {
            return SafeFuture.completedFuture(runs);
          }
        };

    final SafeFuture<Integer> res =
        asyncRunner.runWithRetry(futureSupplier, Duration.ofSeconds(5), 10);

    // We should not complete immediately - instead a retry should be queued
    assertThat(res).isNotDone();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);

    // Run retry
    asyncRunner.executeQueuedActions();

    // We should complete on the first retry
    assertThat(res).isCompletedWithValue(2);
    assertThat(runCounter.get()).isEqualTo(2);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
  }

  @Test
  public void runWithRetry_successOnLastRetry() {
    final AtomicInteger runCounter = new AtomicInteger(0);
    final ExceptionThrowingFutureSupplier<Integer> futureSupplier =
        () -> {
          final int runs = runCounter.incrementAndGet();
          if (runs < 3) {
            // Fail on the first run
            return SafeFuture.failedFuture(new RuntimeException("Failed"));
          } else {
            return SafeFuture.completedFuture(runs);
          }
        };

    final SafeFuture<Integer> res =
        asyncRunner.runWithRetry(futureSupplier, Duration.ofSeconds(5), 2);

    // We should retry twice
    for (int i = 0; i < 2; i++) {
      // A retry should be queued
      assertThat(res).isNotDone();
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);

      // Run retry
      asyncRunner.executeQueuedActions();
    }

    // We should complete successfully after retrying
    assertThat(res).isCompletedWithValue(3);
    assertThat(runCounter.get()).isEqualTo(3);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
  }

  @Test
  public void runWithRetry_failOnEveryRetry() {
    final AtomicInteger runCounter = new AtomicInteger(0);
    final ExceptionThrowingFutureSupplier<Integer> futureSupplier =
        () -> {
          final int runs = runCounter.incrementAndGet();
          return SafeFuture.failedFuture(new RuntimeException("Failed run #" + runs));
        };

    final SafeFuture<Integer> res =
        asyncRunner.runWithRetry(futureSupplier, Duration.ofSeconds(5), 2);

    // We should retry twice
    for (int i = 0; i < 2; i++) {
      // A retry should be queued
      assertThat(res).isNotDone();
      assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);

      // Run retry
      asyncRunner.executeQueuedActions();
    }

    // We should complete successfully after retrying
    assertThat(res).isCompletedExceptionally();
    assertThat(runCounter.get()).isEqualTo(3);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(0);
    assertThatThrownBy(res::get)
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed run #3");
  }
}
