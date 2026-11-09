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

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Unmatched;
import tech.pegasys.teku.cli.converter.MetricCategoryConverter;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.BeaconNodeOptions;
import tech.pegasys.teku.cli.options.BeaconRestApiOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.ExecutionLayerOptions;
import tech.pegasys.teku.cli.options.InteropOptions;
import tech.pegasys.teku.cli.options.LoggingOptions;
import tech.pegasys.teku.cli.options.MetricsOptions;
import tech.pegasys.teku.cli.options.P2POptions;
import tech.pegasys.teku.cli.options.StoreOptions;
import tech.pegasys.teku.cli.options.ValidatorOptions;
import tech.pegasys.teku.cli.options.ValidatorRestApiOptions;
import tech.pegasys.teku.cli.options.WeakSubjectivityOptions;
import tech.pegasys.teku.cli.options.ZkChainOptions;
import tech.pegasys.teku.cli.subcommand.GenesisCommand;
import tech.pegasys.teku.cli.subcommand.MigrateDatabaseCommand;
import tech.pegasys.teku.cli.subcommand.PeerCommand;
import tech.pegasys.teku.cli.subcommand.SlashingProtectionCommand;
import tech.pegasys.teku.cli.subcommand.TransitionCommand;
import tech.pegasys.teku.cli.subcommand.UnstableOptionsCommand;
import tech.pegasys.teku.cli.subcommand.ValidatorClientCommand;
import tech.pegasys.teku.cli.subcommand.VoluntaryExitCommand;
import tech.pegasys.teku.cli.subcommand.admin.AdminCommand;
import tech.pegasys.teku.cli.subcommand.debug.DebugToolsCommand;
import tech.pegasys.teku.cli.subcommand.internal.InternalToolsCommand;
import tech.pegasys.teku.cli.util.AdditionalParamsProvider;
import tech.pegasys.teku.cli.util.CascadingParamsProvider;
import tech.pegasys.teku.cli.util.EnvironmentVariableParamsProvider;
import tech.pegasys.teku.cli.util.MixinSectionOptionRenderer;
import tech.pegasys.teku.cli.util.YamlConfigFileParamsProvider;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

@SuppressWarnings("unused")
@Command(
    name = "teku",
    subcommands = {
      CommandLine.HelpCommand.class,
      AdminCommand.class,
      TransitionCommand.class,
      PeerCommand.class,
      GenesisCommand.class,
      SlashingProtectionCommand.class,
      MigrateDatabaseCommand.class,
      DebugToolsCommand.class,
      UnstableOptionsCommand.class,
      VoluntaryExitCommand.class,
      InternalToolsCommand.class,
    },
    showDefaultValues = true,
    abbreviateSynopsis = true,
    description = "Run the Teku beacon chain client and validator",
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%n@|bold Description:|@%n%n",
    optionListHeading = "%n@|bold Options:|@%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class BeaconNodeCommand implements Callable<Integer> {

  public static final String LOG_FILE_PREFIX = "teku";

  public static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  static final String TEKU_CONFIG_FILE_ENV = "TEKU_CONFIG_FILE";
  private final PrintWriter outputWriter;
  private final PrintWriter errorWriter;
  private final Map<String, String> environment;
  private final StartAction startAction;
  private final LoggingConfigurator loggingConfigurator;
  private final MetricCategoryConverter metricCategoryConverter = new MetricCategoryConverter();

  // allows two pass approach to obtain optional config file
  private static class ConfigFileCommand {

    @Option(
        names = {"-c", CONFIG_FILE_OPTION_NAME},
        arity = "1",
        // Available to all subcommands
        scope = ScopeType.INHERIT)
    File configFile;

    @SuppressWarnings("UnunsedVariable")
    @Unmatched
    List<String> otherOptions;
  }

  @Option(
      names = {"-c", CONFIG_FILE_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Path/filename of the yaml config file (default: none)",
      arity = "1",
      // Available to all subcommands
      scope = ScopeType.INHERIT)
  private File configFile;

  @Mixin(name = "Network")
  private final Eth2NetworkOptions eth2NetworkOptions = new Eth2NetworkOptions();

  @Mixin(name = "P2P")
  private final P2POptions p2POptions = new P2POptions();

  @Mixin(name = "Validator")
  private final ValidatorOptions validatorOptions = new ValidatorOptions();

  @Mixin(name = "Execution Layer")
  private final ExecutionLayerOptions executionLayerOptions = new ExecutionLayerOptions();

  @Mixin(name = "Data Storage")
  private final BeaconNodeDataOptions beaconNodeDataOptions = new BeaconNodeDataOptions();

  @Mixin(name = "Beacon REST API")
  private final BeaconRestApiOptions beaconRestApiOptions = new BeaconRestApiOptions();

  @Mixin(name = "Validator REST API")
  private final ValidatorRestApiOptions validatorRestApiOptions = new ValidatorRestApiOptions();

  @Mixin(name = "Weak Subjectivity")
  private final WeakSubjectivityOptions weakSubjectivityOptions = new WeakSubjectivityOptions();

  @Mixin(name = "Interop")
  private final InteropOptions interopOptions = new InteropOptions();

  @Mixin(name = "Store")
  private final StoreOptions storeOptions = new StoreOptions();

  @Mixin(name = "Logging")
  private final LoggingOptions loggingOptions = new LoggingOptions();

  @Mixin(name = "Metrics")
  private final MetricsOptions metricsOptions = new MetricsOptions();

  @Mixin(name = "ZK-Chain")
  private final ZkChainOptions zkChainOptions = new ZkChainOptions();

  @Mixin(name = "Beacon Node")
  private final BeaconNodeOptions beaconNodeOptions = new BeaconNodeOptions();

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  private final ValidatorClientCommand validatorClientSubcommand;
  private final BootnodeCommand bootnodeSubcommand;

  public BeaconNodeCommand(
      final PrintWriter outputWriter,
      final PrintWriter errorWriter,
      final Map<String, String> environment,
      final StartAction startAction,
      final LoggingConfigurator loggingConfigurator) {
    this.outputWriter = outputWriter;
    this.errorWriter = errorWriter;
    this.environment = environment;
    this.startAction = startAction;
    this.loggingConfigurator = loggingConfigurator;

    metricCategoryConverter.addCategories(TekuMetricCategory.class);
    metricCategoryConverter.addCategories(StandardMetricCategory.class);
    this.validatorClientSubcommand = new ValidatorClientCommand(loggingOptions);
    this.bootnodeSubcommand = new BootnodeCommand(loggingOptions);
  }

  private CommandLine configureCommandLine(final CommandLine commandLine) {
    commandLine
        .registerConverter(MetricCategory.class, metricCategoryConverter)
        .registerConverter(UInt64.class, UInt64::valueOf)
        .setUnmatchedOptionsAllowedAsOptionParameters(false);

    commandLine
        .getHelpSectionMap()
        .put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, new MixinSectionOptionRenderer());

    return commandLine;
  }

  private CommandLine getConfigFileCommandLine(final ConfigFileCommand configFileCommand) {
    return configureCommandLine(new CommandLine(configFileCommand));
  }

  private CommandLine getCommandLine() {
    return configureCommandLine(
        new CommandLine(this)
            .addSubcommand(validatorClientSubcommand)
            .addSubcommand(bootnodeSubcommand));
  }

  public int parse(final String[] args) {
    // first pass to obtain config file if specified and print usage/version help
    final ConfigFileCommand configFileCommand = new ConfigFileCommand();
    final CommandLine configFileCommandLine = getConfigFileCommandLine(configFileCommand);
    configFileCommandLine.parseArgs(args);
    if (configFileCommandLine.isUsageHelpRequested()) {
      return executeCommandUsageHelp();
    } else if (configFileCommandLine.isVersionHelpRequested()) {
      return executeCommandVersion();
    }

    final Optional<File> configFile = getConfigFileFromCliOrEnv(configFileCommand);

    // second pass to collect matched params and enrich with additional (environment and\or config
    // file)
    final CommandLine commandLine = getCommandLine();
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);

    ParseResult parseResult;
    try {
      parseResult = getMostNestedParseResult(commandLine, args);
    } catch (ParameterException e) {
      return handleParseException(e, args);
    }

    // calculate potential additional params from config provider
    final List<OptionSpec> potentialAdditionalParams =
        parseResult.commandSpec().options().stream()
            .filter(optionSpec -> !parseResult.matchedOptionsSet().contains(optionSpec))
            .toList();

    final AdditionalParamsProvider additionalParamsProvider =
        additionalParamsProvider(commandLine, configFile);

    final Map<String, String> additionalParams;
    try {
      additionalParams = additionalParamsProvider.getAdditionalParams(potentialAdditionalParams);
    } catch (ParameterException e) {
      return handleParseException(e, args);
    }

    // build new argument list by concatenating original args and the additional params
    final String[] enrichedArgs =
        Stream.concat(
                Stream.of(args),
                additionalParams.entrySet().stream()
                    .flatMap(paramEntry -> Stream.of(paramEntry.getKey(), paramEntry.getValue())))
            .toArray(String[]::new);

    // last pass - execute command with enriched arguments
    commandLine.setOut(outputWriter);
    commandLine.setErr(errorWriter);
    commandLine.setParameterExceptionHandler(this::handleParseException);
    return commandLine.execute(enrichedArgs);
  }

  private ParseResult getMostNestedParseResult(final CommandLine commandLine, final String[] args) {
    ParseResult parseResult;
    parseResult = commandLine.parseArgs(args);

    // switch to last subcommand if needed
    while (parseResult.hasSubcommand()) {
      parseResult = parseResult.subcommand();
    }
    return parseResult;
  }

  private Optional<File> getConfigFileFromCliOrEnv(final ConfigFileCommand configFileCommand) {
    return Optional.ofNullable(configFileCommand.configFile)
        .or(() -> Optional.ofNullable(environment.get(TEKU_CONFIG_FILE_ENV)).map(File::new));
  }

  private int executeCommandVersion() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.printVersionHelp(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnVersionHelp();
  }

  private int executeCommandUsageHelp() {
    final CommandLine baseCommandLine = getCommandLine();
    baseCommandLine.usage(outputWriter);
    return baseCommandLine.getCommandSpec().exitCodeOnUsageHelp();
  }

  private AdditionalParamsProvider additionalParamsProvider(
      final CommandLine commandLine, final Optional<File> configFile) {
    if (configFile.isEmpty()) {
      return new EnvironmentVariableParamsProvider(environment);
    }

    return new CascadingParamsProvider(
        new EnvironmentVariableParamsProvider(environment),
        new YamlConfigFileParamsProvider(commandLine, configFile.get()));
  }

  private int handleParseException(final CommandLine.ParameterException ex, final String[] args) {
    errorWriter.println(ex.getMessage());

    CommandLine.UnmatchedArgumentException.printSuggestions(ex, errorWriter);
    printUsage(errorWriter);

    return ex.getCommandLine().getCommandSpec().exitCodeOnInvalidInput();
  }

  private void printUsage(final PrintWriter outputWriter) {
    outputWriter.println();
    outputWriter.println("To display full help:");
    outputWriter.println("teku [COMMAND] --help");
  }

  public boolean isOptionSpecified(final String optionLongName) {
    var parseResult = spec.commandLine().getParseResult();
    var option = spec.findOption(optionLongName);
    return option != null && parseResult.hasMatchedOption(option);
  }

  @Override
  public Integer call() {
    try {
      startLogging();
      final TekuConfiguration tekuConfig = tekuConfiguration();
      startAction.start(tekuConfig, NodeMode.COMBINED);
      return 0;
    } catch (final Throwable t) {
      return handleExceptionAndReturnExitCode(t);
    }
  }

  public int handleExceptionAndReturnExitCode(final Throwable e) {
    final Optional<Throwable> maybeUserErrorException =
        ExceptionUtil.<Throwable>getCause(e, InvalidConfigurationException.class)
            .or(() -> ExceptionUtil.getCause(e, DatabaseStorageException.class));
    if (maybeUserErrorException.isPresent()) {
      reportUserError(maybeUserErrorException.get());
      return 2;
    } else {
      reportUnexpectedError(e);
      return 1;
    }
  }

  public void reportUnexpectedError(final Throwable t) {
    getLogger().fatal("Teku failed to start", t);
    errorWriter.println("Teku failed to start: " + t.getMessage());
    printUsage(errorWriter);
  }

  public void reportUserError(final Throwable ex) {
    getLogger().fatal(ex.getMessage(), ex);
    errorWriter.println(ex.getMessage());
    printUsage(errorWriter);
  }

  /**
   * Not using a static field for this log instance because some code in this class executes prior
   * to the logging configuration being applied so it's not always safe to use the logger.
   *
   * <p>Where this is used we also ensure the messages are printed to the error writer so they will
   * be printed even if logging is not yet configured.
   *
   * @return the logger for this class
   */
  private Logger getLogger() {
    return LogManager.getLogger();
  }

  private void startLogging() {
    LoggingConfig loggingConfig =
        buildLoggingConfig(beaconNodeDataOptions.getDataPath(), LOG_FILE_PREFIX);
    loggingConfigurator.startLogging(loggingConfig);
    // jupnp logs a lot of context to level WARN, and it is quite verbose.
    LoggingConfigurator.setAllLevelsSilently("org.jupnp", Level.ERROR);
  }

  public LoggingConfig buildLoggingConfig(
      final String logDirectoryPath, final String logFilePrefix) {
    return loggingOptions.applyLoggingConfiguration(logDirectoryPath, logFilePrefix);
  }

  public StartAction getStartAction() {
    return startAction;
  }

  protected TekuConfiguration tekuConfiguration() {
    try {
      TekuConfiguration.Builder builder = TekuConfiguration.builder();
      // Eth2NetworkOptions configures network defaults across builders, so configure this first
      eth2NetworkOptions.configure(builder);
      executionLayerOptions.configure(builder);
      weakSubjectivityOptions.configure(builder);
      validatorOptions.configure(builder);
      p2POptions.configure(builder);
      beaconRestApiOptions.configure(builder);
      validatorRestApiOptions.configure(builder);
      loggingOptions.configureWireLogs(builder);
      interopOptions.configure(builder);
      beaconNodeDataOptions.configure(builder);
      metricsOptions.configure(builder);
      storeOptions.configure(builder);
      zkChainOptions.configure(builder);
      beaconNodeOptions.configure(builder);

      return builder.build();
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidConfigurationException(e);
    }
  }

  public LoggingConfigurator getLoggingConfigurator() {
    return loggingConfigurator;
  }
}
