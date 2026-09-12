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

package tech.pegasys.teku.infrastructure.exceptions;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Detects fatal errors ({@link OutOfMemoryError}) and shuts the process down.
 *
 * <p>Once the heap is exhausted the node cannot be trusted to make progress, so the only safe
 * option is to exit and let the process supervisor restart us. That only works if we actually
 * notice the error, which is harder than it looks:
 *
 * <ul>
 *   <li>The error is almost never seen in its original form. Anything thrown inside an async task
 *       arrives wrapped in a {@link java.util.concurrent.CompletionException} (possibly several
 *       layers deep), or as a suppressed error of a combined future's failure, so {@code instanceof
 *       OutOfMemoryError} checks miss it. {@link #isFatalError} walks the whole cause/suppressed
 *       graph instead.
 *   <li>Most of the code base converts any {@link Throwable} into a failed future or a log line and
 *       carries on. {@link #shutdownIfFatalError(Throwable, String)} is called from those choke
 *       points so a swallowed error still terminates the node.
 *   <li>A graceful {@link System#exit(int)} runs shutdown hooks which stop services and wait on
 *       futures. Under memory pressure that can block forever, leaving a process that is alive but
 *       useless. We therefore always arm a watchdog which {@link Runtime#halt(int)}s if the
 *       graceful shutdown doesn't complete in time.
 * </ul>
 */
public class FatalErrorHandler {
  private static final Logger LOG = LogManager.getLogger();

  /** Bounds the cause/suppressed graph traversal, protecting against cycles. */
  private static final int MAX_INSPECTED_ERRORS = 100;

  @VisibleForTesting static final Duration GRACEFUL_SHUTDOWN_TIMEOUT = Duration.ofSeconds(90);

  private static final AtomicBoolean SHUTDOWN_TRIGGERED = new AtomicBoolean(false);

  private static volatile ProcessTerminator processTerminator = FatalErrorHandler::terminateProcess;

  private FatalErrorHandler() {}

  /**
   * Runs {@code action}, triggering shutdown if it throws a fatal error, then rethrows. Use this
   * for void callbacks (e.g. {@link java.util.function.BiConsumer}) in async pipelines where the
   * JVM's {@link java.util.concurrent.CompletableFuture} internals would otherwise swallow the
   * error before our {@code completeExceptionally} override can see it.
   */
  public static void runGuarded(final Runnable action) {
    try {
      action.run();
    } catch (final RuntimeException | Error t) {
      shutdownIfFatalError(t, "async callback");
      throw t;
    }
  }

  /**
   * Calls {@code action}, triggering shutdown if it throws a fatal error, then rethrows. Use this
   * for value-returning callbacks (e.g. {@link java.util.function.Function}) in async pipelines.
   */
  public static <T> T callGuarded(final Supplier<T> action) {
    try {
      return action.get();
    } catch (final RuntimeException | Error t) {
      shutdownIfFatalError(t, "async callback");
      throw t;
    }
  }

  /**
   * Returns true if the supplied error is, or was caused by, an unrecoverable error. All causes and
   * suppressed errors are inspected, as fatal errors are frequently wrapped by the async framework.
   */
  public static boolean isFatalError(final Throwable error) {
    // Called for every exceptionally completed future so the common case, a plain chain of causes,
    // is checked without allocating anything
    Throwable current = error;
    for (int depth = 0; current != null && depth < MAX_INSPECTED_ERRORS; depth++) {
      if (current instanceof OutOfMemoryError) {
        return true;
      }
      if (current.getSuppressed().length > 0) {
        return isFatalErrorInGraph(error);
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * Handles the general case where suppressed errors mean causes form a graph rather than a list
   */
  private static boolean isFatalErrorInGraph(final Throwable error) {
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final Deque<Throwable> queue = new ArrayDeque<>();
    queue.add(error);
    seen.add(error);
    int inspected = 0;
    while (!queue.isEmpty() && inspected < MAX_INSPECTED_ERRORS) {
      final Throwable current = queue.poll();
      inspected++;
      if (current instanceof OutOfMemoryError) {
        return true;
      }
      addIfNotSeen(queue, seen, current.getCause());
      for (Throwable suppressed : current.getSuppressed()) {
        addIfNotSeen(queue, seen, suppressed);
      }
    }
    return false;
  }

  private static void addIfNotSeen(
      final Deque<Throwable> queue, final Set<Throwable> seen, final Throwable error) {
    if (error != null && seen.add(error)) {
      queue.add(error);
    }
  }

  /**
   * Shuts the node down if the supplied error is or was caused by a fatal error.
   *
   * <p>Safe to call from anywhere an error may otherwise be swallowed. Shutdown is only triggered
   * once, no matter how many threads report the error.
   *
   * @param error the error to inspect
   * @param context description of where the error was detected, included in the log message
   * @return true if the error was fatal, meaning the node is shutting down
   */
  public static boolean shutdownIfFatalError(final Throwable error, final String context) {
    if (!isFatalError(error)) {
      return false;
    }
    shutdown(error, context);
    return true;
  }

  /** Shuts the node down, assuming the caller has already established that the error is fatal. */
  public static void shutdown(final Throwable error, final String context) {
    if (isShutdownAlreadyTriggered()) {
      return;
    }
    try {
      LOG.fatal("Shutting down after unrecoverable error in {}", context, error);
    } catch (final Throwable t) {
      // Logging itself can fail when out of memory, shutting down still needs to happen
      try {
        System.err.println(
            "Shutting down after unrecoverable error in " + context + " (" + error + ")");
      } catch (final Throwable ignored) {
        // String concatenation can also fail under memory pressure; terminate regardless
      }
    } finally {
      processTerminator.terminate(ExitConstants.ERROR_EXIT_CODE, GRACEFUL_SHUTDOWN_TIMEOUT);
    }
  }

  /**
   * Shuts the node down with the specified exit code, for callers that have already reported the
   * reason. Unlike {@link System#exit(int)} this never blocks the calling thread and guarantees the
   * process actually goes away, even if shutdown hooks get stuck.
   */
  public static void terminate(final int exitCode) {
    if (isShutdownAlreadyTriggered()) {
      return;
    }
    processTerminator.terminate(exitCode, GRACEFUL_SHUTDOWN_TIMEOUT);
  }

  private static boolean isShutdownAlreadyTriggered() {
    // Once shutting down, further errors are expected and must not restart the process termination
    return !SHUTDOWN_TRIGGERED.compareAndSet(false, true);
  }

  private static void terminateProcess(final int exitCode, final Duration gracefulTimeout) {
    terminateProcess(
        gracefulTimeout, () -> System.exit(exitCode), () -> Runtime.getRuntime().halt(exitCode));
  }

  /**
   * Requests a graceful exit, falling back to halting the JVM if it doesn't complete within {@code
   * gracefulTimeout}.
   *
   * <p>{@link System#exit(int)} blocks the calling thread while shutdown hooks run, so it is
   * invoked from a dedicated thread to avoid deadlocking whichever thread reported the error.
   *
   * <p>The watchdog is what makes the shutdown reliable. Shutdown hooks stop services, some of
   * which wait on latches with no timeout (Jetty's selector shutdown for example), and a hook that
   * never returns leaves the JVM alive forever holding the {@code Shutdown} class monitor. {@link
   * Runtime#halt(int)} takes a different lock and skips hooks entirely, so it still works in that
   * state.
   */
  @VisibleForTesting
  static void terminateProcess(
      final Duration gracefulTimeout, final Runnable exit, final Runnable halt) {
    try {
      startDaemonThread(
          "fatal-error-halt-watchdog",
          () -> {
            try {
              Thread.sleep(gracefulTimeout.toMillis());
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
            try {
              System.err.println("Graceful shutdown did not complete in time, halting JVM");
            } catch (final Throwable ignored) {
              // String writes can fail under memory pressure; halt regardless
            }
            halt.run();
          });
      startDaemonThread("fatal-error-shutdown", exit);
    } catch (final Throwable t) {
      // We may not even be able to create threads anymore, so halt without a graceful shutdown
      halt.run();
    }
  }

  private static void startDaemonThread(final String name, final Runnable action) {
    final Thread thread = new Thread(action, name);
    thread.setDaemon(true);
    thread.start();
  }

  @FunctionalInterface
  public interface ProcessTerminator {
    void terminate(int exitCode, Duration gracefulTimeout);
  }

  /**
   * Replaces the process termination logic and clears any previously triggered shutdown. For use by
   * tests only, which must restore the default with {@link #restoreDefaultProcessTerminator()}.
   */
  @VisibleForTesting
  public static void overrideProcessTerminator(final ProcessTerminator terminator) {
    processTerminator = terminator;
    SHUTDOWN_TRIGGERED.set(false);
  }

  @VisibleForTesting
  public static void restoreDefaultProcessTerminator() {
    processTerminator = FatalErrorHandler::terminateProcess;
    SHUTDOWN_TRIGGERED.set(false);
  }
}
