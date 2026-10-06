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

package tech.pegasys.teku.infrastructure.async.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncStreamTest {
  private static final Logger LOG = LogManager.getLogger();

  @Test
  void sanityTest() {
    List<SafeFuture<Integer>> futures =
        Stream.generate(() -> new SafeFuture<Integer>()).limit(5).toList();

    ArrayList<Integer> collector = new ArrayList<>();

    SafeFuture<List<Integer>> listPromise =
        AsyncStream.createUnsafe(futures.iterator())
            .flatMap(AsyncStream::create)
            .flatMap(
                i ->
                    AsyncStream.createUnsafe(
                        IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .limit(10)
            .collect(collector);

    assertThat(collector).isEmpty();

    futures.get(1).complete(1);

    assertThat(collector).isEmpty();

    futures.get(0).complete(0);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140);
    assertThat(listPromise).isNotDone();

    // even if future is completed exceptionally it never reaches downstream thus shouldn't affect
    // the final result
    futures.get(4).completeExceptionally(new RuntimeException("test"));

    assertThat(listPromise).isNotDone();

    futures.get(2).complete(2);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240);
    assertThat(listPromise).isNotDone();

    futures.get(3).complete(3);

    // limit(10) kicks in
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);
    assertThat(listPromise)
        .isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300));
  }

  @Test
  void mapAsyncTest() throws Exception {
    int listSize = 100;
    SafeFuture<Void> launchFuture = new SafeFuture<>();
    List<SafeFuture<Integer>> futures =
        Stream.generate(() -> new SafeFuture<Integer>()).limit(listSize).toList();

    SafeFuture<List<Integer>> listFuture =
        AsyncStream.create(launchFuture)
            .flatMap(
                __ -> {
                  Stream<Integer> idxStream = IntStream.range(0, futures.size()).boxed();
                  return AsyncStream.createUnsafe(idxStream.iterator()).mapAsync(futures::get);
                })
            .toList();

    launchFuture.complete(null);
    for (int i = 0; i < futures.size(); i++) {
      futures.get(i).complete(i);
    }

    assertThat(listFuture.get(1, TimeUnit.SECONDS))
        .containsExactlyElementsOf(IntStream.range(0, listSize).boxed().toList());
  }

  @Test
  void longStreamOfCompletedFuturesShouldNotCauseStackOverflow() {
    List<Integer> ints =
        AsyncStream.createUnsafe(IntStream.range(0, 10000).boxed().iterator())
            .mapAsync(SafeFuture::completedFuture)
            .toList()
            .join();

    assertThat(ints).hasSize(10000);
  }

  @Test
  void longStreamOfFlatMapShouldNotCauseStackOverflow() {
    List<Integer> ints =
        AsyncStream.createUnsafe(IntStream.range(0, 10000).boxed().iterator())
            .flatMap(AsyncStream::of)
            .toList()
            .join();

    assertThat(ints).hasSize(10000);
  }

  @Test
  void checkTheOrderIsPreserved() {
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    SafeFuture<Integer> fut1 = new SafeFuture<>();

    SafeFuture<List<Integer>> listFut = AsyncStream.of(fut0, fut1).mapAsync(f -> f).toList();

    fut1.complete(1);
    fut0.complete(0);

    assertThat(listFut).isCompletedWithValue(List.of(0, 1));
  }

  @Test
  void checkUntilAndCollectLast() {
    List<SafeFuture<Integer>> futures =
        Stream.generate(() -> new SafeFuture<Integer>()).limit(10).toList();

    SafeFuture<List<Integer>> resFuture =
        AsyncStream.createUnsafe(futures.iterator())
            .mapAsync(fut -> fut)
            .takeUntil(i -> i == 4, true)
            .collectLast(2);

    for (int i = 0; i < 4; i++) {
      futures.get(i).complete(i);
    }

    assertThat(resFuture).isNotDone();

    futures.get(4).complete(4);

    assertThat(resFuture).isCompletedWithValue(List.of(3, 4));
  }

  @Test
  void checkUntilEmpty() {
    assertThat(AsyncStream.of(0, 1, 2).takeUntil(i -> i == 0, false).findFirst().join()).isEmpty();
    assertThat(AsyncStream.of(0, 1, 2).takeUntil(i -> i == 100, false).collectLast(1).join())
        .containsExactly(2);
    assertThat(AsyncStream.of(0, 1, 2).takeUntil(i -> i == 100, true).collectLast(1).join())
        .containsExactly(2);
  }

  @Test
  void checkUntilFirst() {
    assertThat(AsyncStream.of(0, 1, 2).takeUntil(i -> i == 0, true).toList().join())
        .containsExactly(0);
  }

  @Test
  void checkCollectLastWithLessElements() {
    assertThat(AsyncStream.of(0, 1).collectLast(3).join()).containsExactly(0, 1);
  }

  @Test
  @Disabled // https://github.com/Consensys-Incorporated/teku/issues/9718
  void testConcurrentExceptionHasUsefulWrap() throws Exception {
    final int baseNumber = 10000;
    final int threadCount = 10;
    final int perThreadIncrement = 1000;
    final int expectedTotal = baseNumber + threadCount * perThreadIncrement;
    final Set<Integer> ints =
        new HashSet<>(IntStream.range(0, baseNumber).boxed().collect(Collectors.toSet()));
    final Set<Integer> collector = new HashSet<>();
    final CountDownLatch startLatch = new CountDownLatch(threadCount);
    final CountDownLatch finishLatch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      final int start = baseNumber + i * perThreadIncrement;
      new Thread(
              () -> {
                startLatch.countDown();
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                for (int j = start; j < start + perThreadIncrement; j++) {
                  ints.add(j);
                  try {
                    Thread.sleep(2);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                }
                finishLatch.countDown();
              })
          .start();
    }
    final LogCaptor logCaptorCopy;
    try (LogCaptor logCaptor = LogCaptor.forClass(AsyncStreamTest.class)) {
      logCaptorCopy = logCaptor;
      AsyncStream.createUnsafe(ints.iterator())
          .map(i -> i)
          .forEach(collector::add)
          .finishDebug(LOG);
    }

    final boolean rc = finishLatch.await(10, TimeUnit.SECONDS);
    assertThat(rc).isTrue();

    assertThat(collector).hasSizeLessThan(expectedTotal);
    final Throwable ex = logCaptorCopy.getThrowable(0).get();
    assertThat(ExceptionUtils.getStackTrace(ex))
        .contains("SyncToAsyncIteratorImpl stack trace holder", "AsyncStreamTest.java:");
    final String logString = logCaptorCopy.getErrorLogs().get(0);
    assertThat(logString).contains("ConcurrentModificationException");
  }

  @Test
  void checkMerge() {
    assertThat(
            AsyncStream.of(0, 1, 2)
                .takeUntil(i -> i == 0, false)
                .merge(AsyncStream.of(3))
                .toList()
                .join())
        .containsExactlyInAnyOrder(3);
    assertThat(AsyncStream.of(0, 1, 2).merge(AsyncStream.of(3)).toList().join())
        .containsExactlyInAnyOrder(0, 1, 2, 3);
    assertThat(AsyncStream.of(0, 1, 2).merge(AsyncStream.of()).toList().join())
        .containsExactlyInAnyOrder(0, 1, 2);
    assertThat(AsyncStream.of(0, 1, 2).merge(AsyncStream.of(2)).toList().join())
        .containsExactlyInAnyOrder(0, 1, 2, 2);
  }
}
