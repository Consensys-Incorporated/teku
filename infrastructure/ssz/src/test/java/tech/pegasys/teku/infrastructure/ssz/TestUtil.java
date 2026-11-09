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

package tech.pegasys.teku.infrastructure.ssz;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestUtil {

  public static <C> List<C> waitAll(final List<Future<C>> futures) {
    return futures.stream()
        .map(
            f -> {
              try {
                return f.get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  public static <C> List<Future<C>> executeParallel(final Callable<C> task, final int threadNum) {
    ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
    CountDownLatch latch = new CountDownLatch(threadNum);
    try {
      return IntStream.range(0, threadNum)
          .mapToObj(
              i ->
                  (Callable<C>)
                      () -> {
                        latch.countDown();
                        // start simultaneously for more aggressive concurrency
                        latch.await();
                        return task.call();
                      })
          .map(threadPool::submit)
          .collect(Collectors.toList());
    } finally {
      threadPool.shutdown();
    }
  }
}
