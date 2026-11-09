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

package tech.pegasys.teku.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig.LoggingConfigBuilder;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;

public abstract class AbstractBeaconNodeCommandTest {
  private static final Logger LOG = LogManager.getLogger();
  final StringWriter stringWriter = new StringWriter();
  protected final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  protected final PrintWriter errorWriter = new PrintWriter(stringWriter, true);
  protected final LoggingConfigurator loggingConfigurator = mock(LoggingConfigurator.class);
  protected NodeMode expectedNodeMode = NodeMode.COMBINED;

  final StartAction startAction = mock(StartAction.class);

  protected BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(
          outputWriter, errorWriter, Collections.emptyMap(), startAction, loggingConfigurator);

  @TempDir Path dataPath;

  @BeforeAll
  static void disablePicocliColors() {
    System.setProperty("picocli.ansi", "false");
  }

  @AfterAll
  static void resetPicocliColors() {
    System.clearProperty("picocli.ansi");
  }

  public TekuConfiguration getResultingTekuConfiguration() {
    try {
      final ArgumentCaptor<TekuConfiguration> configCaptor =
          ArgumentCaptor.forClass(TekuConfiguration.class);
      assertThat(stringWriter.toString()).isEmpty();
      verify(startAction).start(configCaptor.capture(), eq(expectedNodeMode));

      return configCaptor.getValue();
    } catch (Throwable t) {
      // Ensure we get the errors reported by Teku printed when a test provides invalid input
      // Otherwise it's a nightmare trying to guess why the test is failing
      LOG.error("Failed to parse Teku configuration: " + stringWriter);
      throw t;
    }
  }

  public LoggingConfig getResultingLoggingConfiguration() {
    return beaconNodeCommand.buildLoggingConfig(
        getResultingTekuConfiguration().dataConfig().getDataBasePath().toString(),
        BeaconNodeCommand.LOG_FILE_PREFIX);
  }

  public TekuConfiguration getTekuConfigurationFromArguments(final String... arguments) {
    beaconNodeCommand.parse(arguments);
    return getResultingTekuConfiguration();
  }

  public LoggingConfig getLoggingConfigurationFromArguments(final String... arguments) {
    beaconNodeCommand.parse(arguments);
    return getResultingLoggingConfiguration();
  }

  public TekuConfiguration getTekuConfigurationFromFile(
      final String resourceFilename, final Optional<String> subcommand) {
    final String configFile = this.getClass().getResource("/" + resourceFilename).getPath();
    final String[] args =
        Stream.concat(subcommand.stream(), Stream.of(CONFIG_FILE_OPTION_NAME, configFile))
            .toArray(String[]::new);

    beaconNodeCommand.parse(args);
    return getResultingTekuConfiguration();
  }

  public TekuConfiguration getTekuConfigurationFromFile(final String resourceFilename) {
    return getTekuConfigurationFromFile(resourceFilename, Optional.empty());
  }

  public LoggingConfig getLoggingConfigFromFile(final String resourceFilename) {
    final String configFile = this.getClass().getResource("/" + resourceFilename).getPath();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile};
    beaconNodeCommand.parse(args);
    return getResultingLoggingConfiguration();
  }

  public String getCommandLineOutput() {
    verifyNoInteractions(startAction);
    return new String(stringWriter.getBuffer());
  }

  public ByteArrayOutputStream getStdOut() {
    ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdOut));
    return stdOut;
  }

  protected TekuConfiguration.Builder createConfigBuilder() {
    return TekuConfiguration.builder();
  }

  protected LoggingConfigBuilder createLoggingConfigBuilder() {
    LoggingConfigBuilder builder = LoggingConfig.builder();
    return builder
        .logFileNamePrefix(BeaconNodeCommand.LOG_FILE_PREFIX)
        .dataDirectory(
            createConfigBuilder().data(__ -> {}).build().dataConfig().getDataBasePath().toString());
  }
}
