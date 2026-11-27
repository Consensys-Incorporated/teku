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

package tech.pegasys.teku.infrastructure.async.eventthread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;

class AsyncRunnerEventThreadTest {
  private final AsyncRunnerFactory asyncRunnerFactory =
      AsyncRunnerFactory.createDefault(new MetricTrackingExecutorFactory(new StubMetricsSystem()));

  private final EventThread eventThread =
      new AsyncRunnerEventThread("AsyncRunnerEventThreadTest", asyncRunnerFactory);

  @AfterEach
  void tearDown() {
    eventThread.stop();
    asyncRunnerFactory.shutdown();
  }

  @Test
  void executeLater_shouldThrowWhenNotStarted() {
    assertThatThrownBy(() -> eventThread.executeLater(eventThread::checkOnEventThread))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkOnEventThread_shouldNotBeOnEventThreadWhenNotStarted() {
    assertThatThrownBy(eventThread::checkOnEventThread).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkOnEventThread_shouldNotBeOnEventThreadWhenStartedButNotUsingExecute() {
    eventThread.start();
    assertThatThrownBy(eventThread::checkOnEventThread).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void checkOnEventThread_shouldBeOnEventThreadWhenUsingExecute() throws Exception {
    eventThread.start();
    waitForOnEventThread(eventThread::checkOnEventThread);
  }

  @Test
  void runnable_shouldExecuteImmediatelyIfAlreadyOnEventThread() throws Exception {
    eventThread.start();
    waitForOnEventThread(
        () -> {
          final AtomicBoolean actionRun = new AtomicBoolean(false);
          eventThread.execute(() -> actionRun.set(true));
          // Should have happened synchronously because we're already on the event thread
          assertThat(actionRun).isTrue();
        });
  }

  @Test
  void supplier_shouldExecuteImmediatelyIfAlreadyOnEventThread() throws Exception {
    eventThread.start();
    waitForOnEventThread(
        () -> {
          final SafeFuture<String> result = eventThread.execute(() -> "Yay");
          // Should have happened synchronously because we're already on the event thread
          assertThat(result).isCompletedWithValue("Yay");
        });
  }

  @Test
  void supplier_shouldExecuteSupplierOnEventThread() throws Exception {
    eventThread.start();
    final SafeFuture<String> result =
        eventThread.execute(
            () -> {
              eventThread.checkOnEventThread();
              return "Yay";
            });
    assertThat(Waiter.waitFor(result)).isEqualTo("Yay");
  }

  private void waitForOnEventThread(final Runnable action) throws Exception {
    final SafeFuture<Void> complete = new SafeFuture<>();
    eventThread.execute(
        () -> {
          try {
            action.run();
            complete.complete(null);
          } catch (final Throwable t) {
            complete.completeExceptionally(t);
          }
        });
    Waiter.waitFor(complete);
  }
}
