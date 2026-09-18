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

package tech.pegasys.teku.infrastructure.logging;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.pattern.RegexReplacement;
import org.apache.logging.log4j.status.StatusLogger;

public class LoggingConfigurator {

  static final String COLOR_LOG_REGEX = "[\\p{Cntrl}&&[^\r\n\u001b]]";
  static final String NO_COLOR_LOG_REGEX = "[\\p{Cntrl}&&[^\r\n]]";

  static final String EVENT_LOGGER_NAME = "teku-event-log";
  static final String STATUS_LOGGER_NAME = "teku-status-log";
  static final String VALIDATOR_LOGGER_NAME = "teku-validator-log";
  static final String P2P_LOGGER_NAME = "teku-p2p-log";
  static final String DB_LOGGER_NAME = "teku-db-log";

  private static final String LOG4J_CONFIG_FILE_KEY = "LOG4J_CONFIGURATION_FILE";
  private static final String LOG4J_LEGACY_CONFIG_FILE_KEY = "log4j.configurationFile";
  private static final String CONSOLE_APPENDER_NAME = "teku-console-appender";
  private static final String FILE_APPENDER_NAME = "teku-log-appender";
  private static final String FILE_MESSAGE_FORMAT =
      "%d{yyyy-MM-dd HH:mm:ss.SSSZZZ} | %t | %-5level | %c{1} | %msg%n";
  private static final AtomicBoolean COLOR = new AtomicBoolean();
  private static final StatusLogger STATUS_LOG = StatusLogger.getLogger();

  @SuppressWarnings("NonFinalStaticField")
  private static LoggingDestination destination;

  @SuppressWarnings("NonFinalStaticField")
  private static boolean includeEvents;

  @SuppressWarnings("NonFinalStaticField")
  private static boolean includeValidatorDuties;

  @SuppressWarnings("NonFinalStaticField")
  private static boolean includeP2pWarnings;

  @SuppressWarnings("NonFinalStaticField")
  private static String file;

  @SuppressWarnings("NonFinalStaticField")
  private static String filePattern;

  @SuppressWarnings("NonFinalStaticField")
  private static Level rootLogLevel = Level.INFO;

  @SuppressWarnings("NonFinalStaticField")
  private static int dbOpAlertThresholdMillis;

  public static boolean isColorEnabled() {
    return COLOR.get();
  }

  public static boolean isIncludeP2pWarnings() {
    return includeP2pWarnings;
  }

  public static int dbOpAlertThresholdMillis() {
    return dbOpAlertThresholdMillis;
  }

  public static synchronized void setColorEnabled(final boolean isEnabled) {
    COLOR.set(isEnabled);
  }

  public static synchronized void setAllLevels(final Level level) {
    STATUS_LOG.info("Setting logging level to {}", level.name());
    // Teku
    Configurator.setAllLevels("tech.pegasys", level);
    // discovery
    Configurator.setAllLevels("org.ethereum", level);
    // jvm-libp2p
    Configurator.setAllLevels("io.libp2p", level);
    rootLogLevel = level;
  }

  public static synchronized void setAllLevels(final String filter, final Level level) {
    STATUS_LOG.info("Setting logging level on filter {} to {}", filter, level.name());
    Configurator.setAllLevels(filter, level);
  }

  public static synchronized void setAllLevelsSilently(final String filter, final Level level) {
    Configurator.setAllLevels(filter, level);
  }

  public void startLogging(final LoggingConfig configuration) {
    update(configuration);
  }

  public static synchronized void update(final LoggingConfig configuration) {
    configuration.getLogLevel().ifPresent(LoggingConfigurator::setAllLevels);
    COLOR.set(configuration.isColorEnabled());
    destination = configuration.getDestination();
    includeEvents = configuration.isIncludeEventsEnabled();
    includeValidatorDuties = configuration.isIncludeValidatorDutiesEnabled();
    includeP2pWarnings = configuration.isIncludeP2pWarningsEnabled();
    file = configuration.getLogFile();
    filePattern = configuration.getLogFileNamePattern();
    dbOpAlertThresholdMillis = configuration.getDbOpAlertThresholdMillis();

    final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    addLoggers((AbstractConfiguration) ctx.getConfiguration());
    ctx.updateLoggers();
  }

  public static synchronized void addLoggersProgrammatically(
      final AbstractConfiguration configuration) {
    addLoggers(configuration);
  }

  @SuppressWarnings("MissingCasesInEnumSwitch")
  private static void addLoggers(final AbstractConfiguration configuration) {

    if (isUninitialized()) {
      return;
    }

    if (isProgrammaticLoggingDisabled()) {
      displayLoggingConfigurationDisabled();
      return;
    }

    if (isProgrammaticLoggingRedundant()) {
      displayCustomLog4jConfigUsed();
      return;
    }

    displayProgrammaticLoggingConfiguration();

    Appender consoleAppender;
    Appender fileAppender;
    LoggerConfig eventsLogger = null;
    LoggerConfig statusLogger = null;
    LoggerConfig validatorLogger = null;
    LoggerConfig dbLogger = null;

    if (destination != LoggingDestination.CONSOLE
        && destination != LoggingDestination.FILE
        && destination != LoggingDestination.DEFAULT_BOTH
        && destination != LoggingDestination.BOTH) {
      displayUnknownDestinationConfigured();
      destination = LoggingDestination.DEFAULT_BOTH;
    }

    switch (destination) {
      case CONSOLE -> {
        consoleAppender = consoleAppender(configuration, false);

        // These loggers are additive and the root logger already has the appender, so adding it
        // to them as well would log every message twice.
        statusLogger = setUpStatusLogger(Optional.empty());
        eventsLogger = setUpEventsLogger(Optional.empty());
        validatorLogger = setUpValidatorLogger(Optional.empty());
        dbLogger = setUpDbLogger(Optional.empty());

        addAppenderToRootLogger(configuration, consoleAppender);
      }
      case FILE -> {
        fileAppender = fileAppender(configuration);

        // These loggers are additive and the root logger already has the appender, so adding it
        // to them as well would log every message twice.
        statusLogger = setUpStatusLogger(Optional.empty());
        eventsLogger = setUpEventsLogger(Optional.empty());
        validatorLogger = setUpValidatorLogger(Optional.empty());
        dbLogger = setUpDbLogger(Optional.empty());

        addAppenderToRootLogger(configuration, fileAppender);
      }
      case DEFAULT_BOTH, BOTH -> {
        consoleAppender = consoleAppender(configuration, true);
        eventsLogger = setUpEventsLogger(Optional.of(consoleAppender));
        statusLogger = setUpStatusLogger(Optional.of(consoleAppender));
        validatorLogger = setUpValidatorLogger(Optional.of(consoleAppender));
        dbLogger = setUpDbLogger(Optional.of(consoleAppender));

        fileAppender = fileAppender(configuration);

        addAppenderToRootLogger(configuration, fileAppender);
      }
    }

    configuration.addLogger(eventsLogger.getName(), eventsLogger);
    configuration.addLogger(statusLogger.getName(), statusLogger);
    configuration.addLogger(validatorLogger.getName(), validatorLogger);
    configuration.addLogger(dbLogger.getName(), dbLogger);

    STATUS_LOG.info("Include P2P warnings set to: {}", includeP2pWarnings);
    configuration.getLoggerContext().updateLoggers();
  }

  private static void displayProgrammaticLoggingConfiguration() {
    switch (destination) {
      case CONSOLE:
        STATUS_LOG.info("Configuring logging for destination: console");
        break;
      case FILE:
        STATUS_LOG.info("Configuring logging for destination: file");
        STATUS_LOG.info("Logging file location: {}", file);
        break;
      default:
      // fall through
      case DEFAULT_BOTH:
      // fall through
      case BOTH:
        STATUS_LOG.info("Configuring logging for destination: console and file");
        STATUS_LOG.info("Logging file location: {}", file);
        break;
    }

    STATUS_LOG.info("Logging includes events: {}", includeEvents);
    STATUS_LOG.info("Logging includes validator duties: {}", includeValidatorDuties);
    STATUS_LOG.info("Logging includes color: {}", COLOR);
  }

  private static void displayCustomLog4jConfigUsed() {
    STATUS_LOG.info(
        "Custom logging configuration applied from: {}", getCustomLog4jConfigFile().orElse(""));
  }

  private static void displayLoggingConfigurationDisabled() {
    STATUS_LOG.info("Teku logging configuration disabled.");
  }

  private static void displayUnknownDestinationConfigured() {
    STATUS_LOG.warn(
        "Unknown logging destination: {}, applying default: {}",
        destination,
        LoggingDestination.BOTH);
  }

  private static boolean isUninitialized() {
    return destination == null;
  }

  @VisibleForTesting
  // returns the original destination
  static LoggingDestination setDestination(final LoggingDestination destination) {
    final LoggingDestination original = LoggingConfigurator.destination;
    LoggingConfigurator.destination = destination;
    return original;
  }

  private static boolean isProgrammaticLoggingDisabled() {
    return destination == LoggingDestination.CUSTOM;
  }

  private static boolean isProgrammaticLoggingRedundant() {
    return (destination == LoggingDestination.DEFAULT_BOTH
            || destination == LoggingDestination.CUSTOM)
        && isCustomLog4jConfigFileProvided();
  }

  private static boolean isCustomLog4jConfigFileProvided() {
    return getCustomLog4jConfigFile().isPresent();
  }

  private static Optional<String> getCustomLog4jConfigFile() {
    return Optional.ofNullable(System.getenv(LOG4J_CONFIG_FILE_KEY))
        .or(() -> Optional.ofNullable(System.getProperty(LOG4J_CONFIG_FILE_KEY)))
        .or(() -> Optional.ofNullable(System.getenv(LOG4J_LEGACY_CONFIG_FILE_KEY)))
        .or(() -> Optional.ofNullable(System.getProperty(LOG4J_LEGACY_CONFIG_FILE_KEY)));
  }

  private static void addAppenderToRootLogger(
      final AbstractConfiguration configuration, final Appender appender) {
    configuration.getRootLogger().addAppender(appender, null, null);
  }

  private static LoggerConfig setUpEventsLogger(final Optional<Appender> appender) {
    final Level eventsLogLevel = includeEvents ? rootLogLevel : Level.OFF;
    final LoggerConfig logger = new LoggerConfig(EVENT_LOGGER_NAME, eventsLogLevel, true);
    appender.ifPresent(a -> logger.addAppender(a, eventsLogLevel, null));
    return logger;
  }

  private static LoggerConfig setUpStatusLogger(final Optional<Appender> appender) {
    final LoggerConfig logger = new LoggerConfig(STATUS_LOGGER_NAME, rootLogLevel, true);
    appender.ifPresent(a -> logger.addAppender(a, rootLogLevel, null));
    return logger;
  }

  private static LoggerConfig setUpValidatorLogger(final Optional<Appender> appender) {
    // Don't disable validator error logs unless the root log level disables error.
    final Level validatorLogLevel =
        includeValidatorDuties || rootLogLevel.isMoreSpecificThan(Level.ERROR)
            ? rootLogLevel
            : Level.ERROR;
    final LoggerConfig logger = new LoggerConfig(VALIDATOR_LOGGER_NAME, validatorLogLevel, true);
    appender.ifPresent(a -> logger.addAppender(a, rootLogLevel, null));
    return logger;
  }

  private static LoggerConfig setUpDbLogger(final Optional<Appender> appender) {
    final LoggerConfig logger = new LoggerConfig(DB_LOGGER_NAME, rootLogLevel, true);
    appender.ifPresent(a -> logger.addAppender(a, rootLogLevel, null));
    return logger;
  }

  @VisibleForTesting
  static PatternLayout consoleAppenderLayout(
      final AbstractConfiguration configuration, final boolean omitStackTraces) {
    final Pattern logReplacement =
        Pattern.compile(isColorEnabled() ? COLOR_LOG_REGEX : NO_COLOR_LOG_REGEX);
    return PatternLayout.newBuilder()
        .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
        .withAlwaysWriteExceptions(!omitStackTraces)
        .withNoConsoleNoAnsi(true)
        .withConfiguration(configuration)
        .withPatternSelector(
            new ConsolePatternSelector(
                configuration, omitStackTraces, LoggingDestination.CONSOLE.equals(destination)))
        .build();
  }

  private static Appender consoleAppender(
      final AbstractConfiguration configuration, final boolean omitStackTraces) {
    configuration.removeAppender(CONSOLE_APPENDER_NAME);

    final Layout<?> layout = consoleAppenderLayout(configuration, omitStackTraces);

    final Appender consoleAppender =
        ConsoleAppender.newBuilder().setName(CONSOLE_APPENDER_NAME).setLayout(layout).build();
    consoleAppender.start();

    return consoleAppender;
  }

  @VisibleForTesting
  static PatternLayout fileAppenderLayout(final AbstractConfiguration configuration) {
    final Pattern logReplacement =
        Pattern.compile(isColorEnabled() ? COLOR_LOG_REGEX : NO_COLOR_LOG_REGEX);
    return PatternLayout.newBuilder()
        .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
        .withPattern(FILE_MESSAGE_FORMAT)
        .withConfiguration(configuration)
        .build();
  }

  private static Appender fileAppender(final AbstractConfiguration configuration) {
    configuration.removeAppender(FILE_APPENDER_NAME);
    final Layout<?> layout = fileAppenderLayout(configuration);

    final Appender fileAppender =
        RollingFileAppender.newBuilder()
            .setName(FILE_APPENDER_NAME)
            .withAppend(true)
            .setLayout(layout)
            .withFileName(file)
            .withFilePattern(filePattern)
            .withPolicy(
                CompositeTriggeringPolicy.createPolicy(
                    TimeBasedTriggeringPolicy.newBuilder()
                        .withInterval(1)
                        .withModulate(true)
                        .build()))
            .build();
    fileAppender.start();

    return fileAppender;
  }
}
