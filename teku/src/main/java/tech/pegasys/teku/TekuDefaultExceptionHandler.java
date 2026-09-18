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

package tech.pegasys.teku;

import static tech.pegasys.teku.infrastructure.exceptions.ExitConstants.ERROR_EXIT_CODE;
import static tech.pegasys.teku.infrastructure.exceptions.ExitConstants.FATAL_EXIT_CODE;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.IntConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.events.ChannelExceptionHandler;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.services.beaconchain.EphemeryLifecycleException;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public final class TekuDefaultExceptionHandler
    implements ChannelExceptionHandler, UncaughtExceptionHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final StatusLogger statusLog;
  private final IntConsumer haltAction;

  public TekuDefaultExceptionHandler() {
    this(StatusLogger.STATUS_LOG, exitCode -> Runtime.getRuntime().halt(exitCode));
  }

  @VisibleForTesting
  TekuDefaultExceptionHandler(final StatusLogger statusLog, final IntConsumer haltAction) {
    this.statusLog = statusLog;
    this.haltAction = haltAction;
  }

  @Override
  public void handleException(
      final Throwable error,
      final Object subscriber,
      final Method invokedMethod,
      final Object[] args) {
    handleException(
        error,
        "event '"
            + invokedMethod.getDeclaringClass()
            + "."
            + invokedMethod.getName()
            + "' in handler '"
            + subscriber.getClass().getName()
            + "'");
  }

  @Override
  public void uncaughtException(final Thread t, final Throwable e) {
    handleException(e, t.getName());
  }

  private void handleException(final Throwable exception, final String subscriberDescription) {
    final Optional<FatalServiceFailureException> fatalServiceError =
        ExceptionUtil.getCause(exception, FatalServiceFailureException.class);

    if (fatalServiceError.isPresent()) {
      final String failedService = fatalServiceError.get().getService();
      statusLog.fatalError(failedService, exception);
      System.exit(FATAL_EXIT_CODE);
    } else if (ExceptionUtil.getCause(exception, DatabaseStorageException.class)
        .filter(DatabaseStorageException::isUnrecoverable)
        .isPresent()) {
      statusLog.fatalError(subscriberDescription, exception);
      System.exit(FATAL_EXIT_CODE);
    } else if (exception instanceof OutOfMemoryError
        || ExceptionUtil.hasCause(exception, OutOfMemoryError.class)) {
      // Heap exhaustion is handled by -XX:+ExitOnOutOfMemoryError, which terminates the JVM before
      // this handler runs. What reaches here are the out of memory errors thrown by Java code,
      // which that flag does not cover: Netty's OutOfDirectMemoryError and the NIO "Cannot reserve
      // ... direct buffer memory" error. Causes are checked because they arrive wrapped, for
      // example in a CompletionException.
      haltImmediately(subscriberDescription, exception);
    } else if (exception instanceof EphemeryLifecycleException) {
      statusLog.fatalError(subscriberDescription, exception);
      System.exit(ERROR_EXIT_CODE);
    } else if (exception instanceof ShuttingDownException) {
      LOG.debug("Shutting down", exception);
    } else if (isExpectedNettyError(exception)) {
      LOG.debug("Channel unexpectedly closed", exception);
    } else if (Throwables.getRootCause(exception) instanceof RejectedExecutionException) {
      LOG.error(
          "Unexpected rejected execution due to full task queue in {}", subscriberDescription);
    } else if (isSpecFailure(exception)) {
      statusLog.specificationFailure(subscriberDescription, exception);
    } else {
      statusLog.unexpectedFailure(subscriberDescription, exception);
    }
  }

  /**
   * Terminates the process without running shutdown hooks. {@link System#exit(int)} is not used
   * because it runs the hooks, which stop services and have blocked forever doing so, leaving a
   * process that is alive but useless. The database recovers from its write ahead log on the next
   * start. Logging is in a try/finally so that failing to log cannot prevent the shutdown.
   */
  private void haltImmediately(final String subscriberDescription, final Throwable exception) {
    try {
      statusLog.fatalError(subscriberDescription, exception);
    } finally {
      haltAction.accept(ERROR_EXIT_CODE);
    }
  }

  private boolean isExpectedNettyError(final Throwable exception) {
    return exception instanceof ClosedChannelException;
  }

  private static boolean isSpecFailure(final Throwable exception) {
    return exception instanceof IllegalArgumentException;
  }
}
