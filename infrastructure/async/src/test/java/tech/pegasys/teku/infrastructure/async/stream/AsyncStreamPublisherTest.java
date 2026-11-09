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

package tech.pegasys.teku.infrastructure.async.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

@SuppressWarnings("FutureReturnValueIgnored")
public class AsyncStreamPublisherTest {

  AsyncStreamPublisher<Integer> publisher = AsyncStream.createPublisher(Integer.MAX_VALUE);
  AsyncStream<Integer> stream =
      publisher
          .flatMap(
              i -> AsyncStream.createUnsafe(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
          .filter(i -> i % 2 == 0)
          .map(i -> i * 10)
          .limit(10);

  //  List<Integer> expectedValues = List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);

  @Test
  void sanityTest() {
    List<Integer> collector = new ArrayList<>();
    SafeFuture<List<Integer>> listPromise = stream.collect(collector);

    assertThat(collector).isEmpty();

    {
      SafeFuture<Boolean> f = publisher.onNext(0);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40);

    {
      SafeFuture<Boolean> f = publisher.onNext(1);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140);

    {
      SafeFuture<Boolean> f = publisher.onNext(2);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240);
    assertThat(listPromise).isNotDone();

    {
      SafeFuture<Boolean> f = publisher.onNext(3);
      assertThat(f).isCompletedWithValue(false);
    }
    // limit(10) kicks in
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);
    assertThat(listPromise)
        .isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300));
  }

  @Test
  void completeShouldCompleteStream() {
    SafeFuture<List<Integer>> listPromise = stream.toList();
    publisher.onNext(0);
    publisher.onComplete();
    assertThat(listPromise).isCompletedWithValue(List.of(0, 20, 40));
  }

  @Test
  void errorShouldCompleteStream() {
    SafeFuture<List<Integer>> listPromise = stream.toList();
    publisher.onNext(0);
    publisher.onError(new RuntimeException("test"));
    assertThat(listPromise).isCompletedExceptionally();
  }

  @Test
  void publishingAllPriorToConsumeShouldWork() {
    publisher.onNext(0);
    publisher.onNext(1);
    publisher.onComplete();

    assertThat(stream.toList()).isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140));
  }

  @Test
  void publishingPartiallyPriorToConsumeShouldWork() {
    publisher.onNext(0);
    SafeFuture<List<Integer>> list = stream.toList();
    publisher.onNext(1);
    publisher.onComplete();

    assertThat(list).isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140));
  }

  @Test
  void issuingErrorPriorToConsumeShouldWork() {
    publisher.onNext(0);
    publisher.onError(new RuntimeException("test"));

    assertThat(stream.toList()).isCompletedExceptionally();
  }

  @Test
  void shouldIgnoreAnyItemsAfterOnComplete() {
    publisher.onNext(0);
    publisher.onComplete();
    publisher.onNext(1);

    assertThat(stream.toList()).isCompletedWithValue(List.of(0, 20, 40));
  }

  @Test
  void sanityThreadSafetyTest() throws Exception {
    AsyncStreamPublisher<Integer> myPublisher = AsyncStream.createPublisher(Integer.MAX_VALUE);
    AsyncStream<Integer> stream =
        myPublisher
            .map(i -> i * 10)
            .flatMap(i -> AsyncStream.of(i / 10, 777777))
            .filter(i -> i != 777777);

    int threadCount = 16;
    CountDownLatch startLatch = new CountDownLatch(threadCount);
    CountDownLatch finishLatch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      int finalI = i;
      new Thread(
              () -> {
                startLatch.countDown();
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
                for (int j = 0; j < 1000; j++) {
                  myPublisher.onNext(finalI * 1000 + j);
                }
                finishLatch.countDown();
              })
          .start();
    }
    SafeFuture<List<Integer>> listPromise = stream.toList();

    boolean rc = finishLatch.await(5, TimeUnit.SECONDS);
    assertThat(rc).isTrue();

    myPublisher.onComplete();

    List<Integer> list = listPromise.get(5, TimeUnit.SECONDS);

    assertThat(list)
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, threadCount * 1000).boxed().toList());
  }
}
