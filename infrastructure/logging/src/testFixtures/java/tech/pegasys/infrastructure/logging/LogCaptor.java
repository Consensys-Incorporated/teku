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

package tech.pegasys.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.spi.ExtendedLogger;

public class LogCaptor implements AutoCloseable {
  private static final AtomicInteger UNIQUEIFIER = new AtomicInteger();
  private final LoggerConfig loggerConfig;
  private final CapturingAppender appender;

  public LogCaptor(final LoggerConfig loggerConfig, final CapturingAppender appender) {

    this.loggerConfig = loggerConfig;
    this.appender = appender;
  }

  public void assertMessagesInOrder(final Level level, final String... messages) {
    assertThat(getMessages(level)).containsSubsequence(messages);
  }

  public void assertDebugLog(final String message) {
    assertLogged(Level.DEBUG, message);
  }

  public void assertInfoLog(final String message) {
    assertLogged(Level.INFO, message);
  }

  public void assertWarnLog(final String message) {
    assertLogged(Level.WARN, message);
  }

  public void assertErrorLog(final String message) {
    assertLogged(Level.ERROR, message);
  }

  public void assertFatalLog(final String message) {
    assertLogged(Level.FATAL, message);
  }

  public void assertLogged(final Level level, final String message) {
    assertThat(getMessages(level))
        .anySatisfy(
            logString -> {
              assertThat(logString.contains(message)).isTrue();
            });
  }

  public List<String> getInfoLogs() {
    return getMessages(Level.INFO).collect(Collectors.toList());
  }

  public List<String> getWarnLogs() {
    return getMessages(Level.WARN).collect(Collectors.toList());
  }

  public List<String> getErrorLogs() {
    return getMessages(Level.ERROR).collect(Collectors.toList());
  }

  public List<Throwable> getErrorThrowables() {
    return appender.logs.stream()
        .filter(log -> log.getLevel().equals(Level.ERROR))
        .map(LogEvent::getThrown)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public List<String> getDebugLogs() {
    return getMessages(Level.DEBUG).collect(Collectors.toList());
  }

  public List<String> getTraceLogs() {
    return getMessages(Level.TRACE).collect(Collectors.toList());
  }

  private Stream<String> getMessages(final Level level) {
    return appender.logs.stream()
        .filter(log -> log.getLevel().equals(level))
        .map(log -> log.getMessage().getFormattedMessage());
  }

  public Optional<Throwable> getThrowable(final int index) {
    return Optional.ofNullable(appender.logs.get(index).getThrown());
  }

  public static LogCaptor forClass(final Class<?> clazz) {
    final LoggerContext context = LoggerContext.getContext(false);
    final ExtendedLogger logger = context.getLogger(clazz);
    final CapturingAppender appender =
        new CapturingAppender("LogCaptorAppender" + UNIQUEIFIER.incrementAndGet());
    final LoggerConfig loggerConfig = context.getConfiguration().getLoggerConfig(logger.getName());
    loggerConfig.addAppender(appender, null, null);
    return new LogCaptor(loggerConfig, appender);
  }

  public static LogCaptor forClass(final Class<?> clazz, final Level level) {
    return forLogger(LoggerContext.getContext(false).getLogger(clazz), level);
  }

  /** Captures a logger addressed by name rather than by class, e.g. the event or status log. */
  public static LogCaptor forLoggerName(final String loggerName, final Level level) {
    return forLogger(LoggerContext.getContext(false).getLogger(loggerName), level);
  }

  private static LogCaptor forLogger(final ExtendedLogger logger, final Level level) {
    final LoggerContext context = LoggerContext.getContext(false);
    final CapturingAppender appender =
        new CapturingAppender("LogCaptorAppender" + UNIQUEIFIER.incrementAndGet());
    final LoggerConfig loggerConfig = context.getConfiguration().getLoggerConfig(logger.getName());
    ((Logger) logger).setLevel(level);
    loggerConfig.addAppender(appender, null, null);
    return new LogCaptor(loggerConfig, appender);
  }

  public void clearLogs() {
    appender.logs.clear();
  }

  @Override
  public void close() {
    loggerConfig.removeAppender(appender.getName());
  }

  private static class CapturingAppender implements Appender {
    private static final PatternLayout LAYOUT =
        PatternLayout.newBuilder().setPattern("%msg").build();
    private final List<LogEvent> logs = new ArrayList<>();
    private final String name;

    private CapturingAppender(final String name) {
      this.name = name;
    }

    @Override
    public void append(final LogEvent event) {
      logs.add(event.toImmutable());
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public Layout<? extends Serializable> getLayout() {
      return LAYOUT;
    }

    @Override
    public boolean ignoreExceptions() {
      return false;
    }

    @Override
    public ErrorHandler getHandler() {
      return null;
    }

    @Override
    public void setHandler(final ErrorHandler handler) {}

    @Override
    public State getState() {
      return State.STARTED;
    }

    @Override
    public void initialize() {}

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public boolean isStarted() {
      return true;
    }

    @Override
    public boolean isStopped() {
      return false;
    }
  }
}
