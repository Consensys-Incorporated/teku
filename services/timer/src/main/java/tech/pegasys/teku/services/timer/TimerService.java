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

package tech.pegasys.teku.services.timer;

import com.google.common.base.Throwables;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;

public class TimerService extends Service {
  private final TimeProvider timeProvider;
  private static final Logger LOG = LogManager.getLogger();

  private final TimeTickHandler timeTickHandler;
  private final AtomicReference<Future<?>> executorFuture =
      new AtomicReference<>(SafeFuture.COMPLETE);
  private final int intervalsPerSecond;
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            final Thread t = new Thread(r, "TimeTick");
            t.setDaemon(true);
            return t;
          });
  private final ExecutorService taskExecutor =
      Executors.newFixedThreadPool(
          1,
          runnable -> {
            final Thread thread = new Thread(runnable, "TimeTickTask");
            thread.setDaemon(true);
            return thread;
          });

  public TimerService(final TimeTickHandler timeTickHandler) {
    this(timeTickHandler, 2, new SystemTimeProvider());
  }

  TimerService(
      final TimeTickHandler timeTickHandler,
      final int intervalsPerSecond,
      final TimeProvider timeProvider) {
    LOG.debug("Initialize TimerService with {} ticks per second", intervalsPerSecond);
    this.timeTickHandler = timeTickHandler;
    this.intervalsPerSecond = intervalsPerSecond;
    this.timeProvider = timeProvider;
  }

  @Override
  public SafeFuture<?> doStart() {
    scheduleNextTick();
    return SafeFuture.COMPLETE;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void scheduleNextTick() {
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    final long millisFromSecondStart = currentTime.mod(1000).longValue();
    final long nextMillisStart = nextTickDue(millisFromSecondStart, intervalsPerSecond);
    scheduler.schedule(
        () -> {
          scheduleNextTick();
          if (executorFuture.get().isDone()) {
            executorFuture.set(taskExecutor.submit(this::safeTick));
          } else {
            LOG.debug("Dropped tick at {}", timeProvider.getTimeInMillis());
          }
        },
        nextMillisStart,
        TimeUnit.MILLISECONDS);
    LOG.trace("Current tick time {}; Scheduled next in {} ms", currentTime, nextMillisStart);
  }

  static long nextTickDue(final long currentMillisOffset, final int ticksPerSecond) {
    final long intervalMs = 1000 / ticksPerSecond;
    final long current = Math.min(currentMillisOffset / intervalMs, ticksPerSecond - 1);
    final long next = (current + 1) % ticksPerSecond;
    if (next == 0) {
      return 1000 - currentMillisOffset;
    }
    final long millisTarget = intervalMs * next;
    return millisTarget - currentMillisOffset;
  }

  private void safeTick() {
    try {
      timeTickHandler.onTick();
      LOG.trace("Tick completed at {}", timeProvider::getTimeInMillis);
    } catch (final Throwable t) {
      final Throwable rootCause = Throwables.getRootCause(t);
      if (rootCause instanceof InterruptedException) {
        LOG.trace("Shutting down", rootCause);
      } else {
        LOG.error("Unhandled exception in timer tick handler", t);
      }
    }
  }

  @Override
  public SafeFuture<?> doStop() {
    scheduler.shutdown();
    taskExecutor.shutdown();
    return SafeFuture.COMPLETE;
  }
}
