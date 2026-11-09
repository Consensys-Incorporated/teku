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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.cli.BeaconNodeCommand.LOG_FILE_PREFIX;
import static tech.pegasys.teku.cli.OSUtils.SLASH;
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.OSUtils;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.p2p.network.config.WireLogsConfig;

public class LoggingOptionsTest extends AbstractBeaconNodeCommandTest {
  private static final String LOG_FILE =
      LOG_FILE_PREFIX + LoggingConfig.DEFAULT_LOG_FILE_NAME_SUFFIX;
  private static final String LOG_PATTERN =
      LOG_FILE_PREFIX + LoggingConfig.DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX;

  @Test
  public void loggingOptions_shouldReadFromConfigurationFile() {
    final LoggingConfig loggingConfig = getLoggingConfigFromFile("loggingOptions_config.yaml");

    assertThat(loggingConfig.getDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(loggingConfig.isColorEnabled()).isFalse();
    assertThat(loggingConfig.isIncludeEventsEnabled()).isFalse();
    assertThat(loggingConfig.getLogFile())
        .isEqualTo(VersionProvider.defaultStoragePath() + SLASH + "logs" + SLASH + "a.log");
    assertThat(loggingConfig.getLogFileNamePattern())
        .isEqualTo(VersionProvider.defaultStoragePath() + SLASH + "logs" + SLASH + "a%d.log");
  }

  @Test
  public void wireLoggingOptions_shouldReadFromConfigurationFile() {
    final TekuConfiguration config = getTekuConfigurationFromFile("wireLoggingOptions_config.yaml");
    final WireLogsConfig wireConfig = config.network().getWireLogsConfig();
    assertThat(wireConfig.isLogWireCipher()).isTrue();
    assertThat(wireConfig.isLogWirePlain()).isTrue();
    assertThat(wireConfig.isLogWireMuxFrames()).isTrue();
    assertThat(wireConfig.isLogWireGossip()).isTrue();
  }

  @Test
  public void logDestination_shouldHaveSensibleDefaultValue() {
    // This is important!
    // If it defaults to "both" or some other value custom log4j configs get overwritten
    beaconNodeCommand.parse(new String[0]);

    final LoggingConfig config = getResultingLoggingConfiguration();
    assertThat(config.getDestination()).isEqualTo(DEFAULT_BOTH);
  }

  @Test
  public void logDestination_shouldAcceptFileAsDestination() {
    final LoggingConfig config = getLoggingConfigurationFromArguments("--log-destination", "file");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.FILE);
    assertThat(createLoggingConfigBuilder().destination(LoggingDestination.FILE).build())
        .isEqualTo(config);
  }

  @Test
  public void includeEvents_shouldNotRequireAValue() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments("--log-include-events-enabled");
    assertThat(config.isIncludeEventsEnabled()).isTrue();
  }

  @Test
  public void logDestination_shouldAcceptConsoleAsDestination() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments("--log-destination", "console");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void logDestination_shouldAcceptBothAsDestination() {
    final LoggingConfig config = getLoggingConfigurationFromArguments("--log-destination", "both");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.BOTH);
  }

  @Test
  public void shouldSetLogFileToDefaultDataDirectory() {
    final String[] args = {};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFile())
        .isEqualTo(
            StringUtils.joinWith(SLASH, VersionProvider.defaultStoragePath(), "logs", LOG_FILE));
  }

  @Test
  public void shouldSetLogPatternToDefaultDataDirectory() {
    final String[] args = {"--data-path", OSUtils.toOSPath("/my/path")};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFileNamePattern())
        .isEqualTo(OSUtils.toOSPath("/my/path/logs/" + LOG_PATTERN));
  }

  @Test
  public void shouldSetLogPatternOnCommandLine() {
    final String[] args = {
      "--data-path",
      OSUtils.toOSPath("/my/path"),
      "--log-file-name-pattern",
      OSUtils.toOSPath("/z/%d.log")
    };
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFileNamePattern()).isEqualTo(OSUtils.toOSPath("/z/%d.log"));
  }

  @Test
  public void shouldSetDbOpAlertThresholdFromCommandLine() {
    final int dbOpAlertThresholdMillis = 250;
    final String[] args = {
      "--Xlog-db-op-alert-threshold", String.valueOf(dbOpAlertThresholdMillis)
    };
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getDbOpAlertThresholdMillis()).isEqualTo(dbOpAlertThresholdMillis);
  }

  @Test
  public void shouldSetLogPatternOnWithoutPath() {
    final String[] args = {"--log-file-name-pattern", "%d.log"};
    final String expectedLogPatternPath =
        StringUtils.joinWith(
            System.getProperty("file.separator"),
            VersionProvider.defaultStoragePath(),
            "logs",
            "%d.log");
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFileNamePattern()).isEqualTo(expectedLogPatternPath);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL", "off", "fatal", "error",
        "warn", "info", "debug", "trace", "all"
      })
  public void loglevel_shouldAcceptValues(final String level) {
    final String[] args = {"--logging", level};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogLevel().orElseThrow().toString()).isEqualToIgnoringCase(level);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"Off", "Fatal", "eRRoR", "WaRN", "InfO", "DebUG", "trACE", "All"})
  public void loglevel_shouldAcceptValuesMixedCase(final String level) {
    final String[] args = {"--logging", level};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogLevel().orElseThrow().toString())
        .isEqualTo(level.toUpperCase(Locale.ROOT));
  }

  @Test
  public void logLevel_shouldRejectInvalidValues() {
    final String[] args = {"--logging", "invalid"};
    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("'invalid' is not a valid log level. Supported values are");
  }

  @Test
  public void shouldSetLogFileToTheOptionProvided() {
    final String[] args = {"--log-file", OSUtils.toOSPath("/hello/world.log")};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFile()).isEqualTo(OSUtils.toOSPath("/hello/world.log"));
  }

  @Test
  public void shouldSetLogFileToTheOptionProvidedRegardlessOfDataPath() {
    final String[] args = {
      "--log-file", OSUtils.toOSPath("/hello/world.log"),
      "--data-path", OSUtils.toOSPath("/yo")
    };
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFile()).isEqualTo(OSUtils.toOSPath("/hello/world.log"));
  }

  @Test
  public void shouldSetLogFileRelativeToSetDataDirectory() {
    final String[] args = {"--data-path", OSUtils.toOSPath("/yo")};
    final LoggingConfig config = getLoggingConfigurationFromArguments(args);
    assertThat(config.getLogFile()).isEqualTo(OSUtils.toOSPath("/yo/logs/teku.log"));
  }
}
