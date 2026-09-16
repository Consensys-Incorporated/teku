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

package tech.pegasys.teku.infrastructure.subscribers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.exceptions.ExitConstants;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;

public class ObservableValueTest {

  @AfterEach
  void restoreFatalErrorHandler() {
    FatalErrorHandler.restoreDefaultProcessTerminator();
  }

  // Suppressing exceptions must not hide the node running out of memory
  @Test
  public void shouldShutdownWhenASuppressedCallbackFailsFatally() {
    final List<Integer> exitCodes = new ArrayList<>();
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> exitCodes.add(exitCode));
    final ObservableValue<String> observableValue = new ObservableValue<>(true);
    observableValue.subscribe(
        value -> {
          throw new IllegalStateException("whoops", new OutOfMemoryError());
        });

    observableValue.set("value");

    Assertions.assertThat(exitCodes).containsExactly(ExitConstants.ERROR_EXIT_CODE);
  }

  @Test
  public void shouldNotShutdownWhenASuppressedCallbackFailsWithAnOrdinaryException() {
    final List<Integer> exitCodes = new ArrayList<>();
    FatalErrorHandler.overrideProcessTerminator(
        (exitCode, gracefulTimeout) -> exitCodes.add(exitCode));
    final ObservableValue<String> observableValue = new ObservableValue<>(true);
    observableValue.subscribe(
        value -> {
          throw new IllegalStateException("whoops");
        });

    observableValue.set("value");

    Assertions.assertThat(exitCodes).isEmpty();
  }

  @Test
  public void testConcurrentSubscribersNotifications() throws InterruptedException {
    ObservableValue<Integer> observableValue = new ObservableValue<>(false);
    class Listener implements ValueObserver<Integer> {
      int val;

      @Override
      public void onValueChanged(final Integer newValue) {
        if (newValue <= val) {
          Assertions.fail("newValue <= val: " + newValue + " <= " + val);
        }
        val = newValue;
      }
    }

    List<Listener> listeners = Collections.synchronizedList(new ArrayList<>());
    int threadCnt = 64;
    CountDownLatch startLatch = new CountDownLatch(threadCnt);
    CountDownLatch stopLatch = new CountDownLatch(threadCnt);

    Runnable runnable =
        () -> {
          while (!Thread.interrupted()) {
            Listener listener = new Listener();
            observableValue.subscribe(listener);
            listeners.add(listener);
            startLatch.countDown();
          }
          stopLatch.countDown();
        };

    List<Thread> threads =
        Stream.generate(() -> runnable)
            .map(Thread::new)
            .peek(Thread::start)
            .limit(threadCnt)
            .toList();

    startLatch.await(5, TimeUnit.SECONDS);
    observableValue.set(777);
    threads.forEach(Thread::interrupt);
    stopLatch.await(5, TimeUnit.SECONDS);

    for (Listener listener : listeners) {
      if (listener.val != 777) {
        throw new RuntimeException();
      }
    }
  }
}
