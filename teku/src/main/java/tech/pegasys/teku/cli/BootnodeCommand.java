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

import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.P2POptions;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;

@Command(
    name = "bootnode",
    description = "Run Teku in Bootnode mode",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BootnodeCommand implements Callable<Integer> {

  public static final String LOG_FILE_PREFIX = "teku-bootnode";

  @Mixin(name = "Network")
  private Eth2NetworkOptions eth2NetworkOptions;

  @Mixin(name = "Data Storage")
  private BeaconNodeDataOptions beaconNodeDataOptions;

  @Mixin(name = "P2P")
  private P2POptions p2POptions;

  @Mixin(name = "Logging")
  private final LoggingOptions loggingOptions;

  @Mixin(name = "Metrics")
  private MetricsOptions metricsOptions;

  @ParentCommand private BeaconNodeCommand parentCommand;

  public BootnodeCommand(final LoggingOptions sharedLoggingOptions) {
    this.loggingOptions = sharedLoggingOptions;
  }

  @Override
  public Integer call() {
    try {
      startLogging();
      final TekuConfiguration globalConfiguration = tekuConfiguration();
      parentCommand.getStartAction().start(globalConfiguration, NodeMode.BOOTNODE_ONLY);
      return 0;
    } catch (final Throwable t) {
      return parentCommand.handleExceptionAndReturnExitCode(t);
    }
  }

  private void startLogging() {
    LoggingConfig loggingConfig =
        parentCommand.buildLoggingConfig(beaconNodeDataOptions.getDataPath(), LOG_FILE_PREFIX);
    parentCommand.getLoggingConfigurator().startLogging(loggingConfig);
    // jupnp logs a lot of context to level WARN, and it is quite verbose.
    LoggingConfigurator.setAllLevelsSilently("org.jupnp", Level.ERROR);
  }

  protected TekuConfiguration tekuConfiguration() {
    try {
      final TekuConfiguration.Builder builder = TekuConfiguration.builder();
      eth2NetworkOptions.configure(builder);
      beaconNodeDataOptions.configure(builder);
      p2POptions.configure(builder);
      loggingOptions.configureWireLogs(builder);
      metricsOptions.configure(builder);

      return builder.build();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidConfigurationException(e);
    }
  }
}
