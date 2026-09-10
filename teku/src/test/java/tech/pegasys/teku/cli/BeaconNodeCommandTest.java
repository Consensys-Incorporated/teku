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

package tech.pegasys.teku.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.metrics.StandardMetricCategory.JVM;
import static org.hyperledger.besu.metrics.StandardMetricCategory.PROCESS;
import static tech.pegasys.teku.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;
import static tech.pegasys.teku.cli.BeaconNodeCommand.LOG_FILE_PREFIX;
import static tech.pegasys.teku.cli.OSUtils.SLASH;
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.BOTH;
import static tech.pegasys.teku.infrastructure.logging.LoggingDestination.DEFAULT_BOTH;
import static tech.pegasys.teku.infrastructure.metrics.MetricsConfig.DEFAULT_METRICS_CATEGORIES;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.EVENTBUS;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;
import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_LOWER_BOUND;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_P2P_PEERS_UPPER_BOUND;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig.DEFAULT_RANDOMLY_SELECTED_PEER_COUNT_PERCENTAGE;
import static tech.pegasys.teku.storage.server.StateStorageMode.MINIMAL;
import static tech.pegasys.teku.storage.server.StateStorageMode.PRUNE;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Model.OptionSpec;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig.LoggingConfigBuilder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.nat.NatMethod;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;

public class BeaconNodeCommandTest extends AbstractBeaconNodeCommandTest {
  private static final Logger LOG = LogManager.getLogger();

  private static final String LOG_FILE =
      LOG_FILE_PREFIX + LoggingConfig.DEFAULT_LOG_FILE_NAME_SUFFIX;
  private static final String LOG_PATTERN =
      LOG_FILE_PREFIX + LoggingConfig.DEFAULT_LOG_FILE_NAME_PATTERN_SUFFIX;
  private static final String XEPOCHS_STORE_BLOBS_OPTION = "--Xepochs-store-blobs";
  private static final String DOPPELGANGER_DETECTION_ENABLED_OPTION =
      "--doppelganger-detection-enabled";
  private static final String XDENEB_FORK_EPOCH_OPTION = "--Xnetwork-deneb-fork-epoch";

  final Eth1Address address =
      Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");

  @BeforeEach
  public void resetBeaconNodeCommand() {
    this.beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter, errorWriter, Collections.emptyMap(), startAction, loggingConfigurator);
  }

  @Test
  public void unknownOptionShouldDisplayShortHelpMessage() {
    final String[] args = {"--hlp"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Unknown option");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void unknownOptionInConfigFileShouldDisplayShortHelpMessage() throws IOException {
    final Path configFile = createInvalidConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Unknown option");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void unmatchedOptionsNotAllowedAsOptionParameters() {
    final String[] args = {"--eth1-endpoints http://localhost:8545 --foo"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Unknown option");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void invalidValueShouldDisplayShortHelpMessage() {
    final String[] args = {"--metrics-enabled=bob"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Invalid value");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(str).doesNotContain("Default");
  }

  @Test
  public void helpOptionShouldDisplayFullHelp() {
    final String[] args = {"--help"};

    beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str).contains("Description:");
    assertThat(str).contains("Default");
    assertThat(str).doesNotContain("To display full help:");
  }

  @Test
  void helpShouldNotShowUnsupportedOptions() {
    // Any option starting with --X is marked as a developer option and should not be shown in the
    // supported help
    final String[] args = {"--help"};

    beaconNodeCommand.parse(args);
    final String commandLineOutput = getCommandLineOutput();

    assertThat(
            getCommandLineOptionsFromCommandLineOutput(
                commandLineOutput, Pattern.compile("--X[^ ]+")))
        .describedAs("Found --X command arguments not marked as hidden")
        .isEmpty();
  }

  @Test
  void developerHelpShouldNotShowSupportedOptions() {
    // Any option in developer help should be a --X option, and if they're not prefixed in this way
    // there's likely a problem.
    final String[] args = {"-X"};

    beaconNodeCommand.parse(args);
    final String commandLineOutput = getCommandLineOutput();

    assertThat(
            getCommandLineOptionsFromCommandLineOutput(
                commandLineOutput, Pattern.compile("--[^X][^ ]+=")))
        .describedAs("Found hidden command arguments not prefixed with --X")
        .isEmpty();
  }

  @Test
  void booleanOptionsShouldSetFallbackValueToTrue() {
    final List<String> invalidOptions =
        allBooleanOptions()
            .filter(option -> !"true".equals(option.fallbackValue()))
            .map(OptionSpec::longestName)
            .collect(Collectors.toList());

    assertThat(invalidOptions).describedAs("Values with incorrect fallback value").isEmpty();
  }

  @Test
  void booleanOptionsShouldSetShowDefaultValueToAlways() {
    final List<String> invalidOptions =
        allBooleanOptions()
            .filter(option -> option.showDefaultValue() != Visibility.ALWAYS)
            .map(OptionSpec::longestName)
            .collect(Collectors.toList());

    assertThat(invalidOptions).describedAs("Values with incorrect show default value").isEmpty();
  }

  private Stream<OptionSpec> allBooleanOptions() {
    return allOptions()
        .filter(option -> option.type().equals(Boolean.TYPE) || option.type().equals(Boolean.class))
        .filter(option -> !option.usageHelp() && !option.versionHelp());
  }

  private Stream<OptionSpec> allOptions() {
    final CommandLine commandLine = new CommandLine(beaconNodeCommand);
    Stream<OptionSpec> stream = commandLine.getCommandSpec().options().stream();
    stream = addSubCommandOptions(commandLine, stream);
    return stream;
  }

  private Stream<OptionSpec> addSubCommandOptions(
      final CommandLine commandLine, final Stream<OptionSpec> stream) {
    Stream<OptionSpec> mutableStream = stream;
    for (CommandLine subCommand : commandLine.getSubcommands().values()) {
      mutableStream = Stream.concat(mutableStream, subCommand.getCommandSpec().options().stream());
      mutableStream = addSubCommandOptions(subCommand, mutableStream);
    }
    return mutableStream;
  }

  @Test
  public void loadDefaultsWhenNoArgsArePassed() {
    // p2p-enabled default is "true" which require p2p-private-key-file to be non-null
    final String[] args = {"--data-path", dataPath.toString(), "--p2p-enabled", "false"};

    beaconNodeCommand.parse(args);

    assertTekuAndLoggingConfiguration(
        expectedDefaultConfigurationBuilder().build(), expectedDefaultLoggingBuilder().build());
  }

  @Test
  void ignoreVersionAndHelpEnvVars() {
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Map.of("TEKU_VERSION", "1.2.3", "TEKU_HELP", "what?"),
            startAction,
            loggingConfigurator);

    // No error from invalid --version or --help arg.
    assertThat(beaconNodeCommand.parse(new String[0])).isZero();
  }

  @Test
  public void overrideEnvironmentValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = createCliArgs();
    args[5] = "1.2.3.5";
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Collections.singletonMap("TEKU_P2P_INTERFACE", "1.2.3.4"),
            startAction,
            loggingConfigurator);

    beaconNodeCommand.parse(args);

    TekuConfiguration expected =
        expectedConfigurationBuilder().network(n -> n.networkInterface("1.2.3.5")).build();
    assertTekuAndLoggingConfiguration(expected, expectedLoggingBuilder().build());
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInEnvironmentVariables() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Collections.singletonMap("TEKU_P2P_INTERFACE", "1.2.3.5"),
            startAction,
            loggingConfigurator);

    beaconNodeCommand.parse(args);

    final TekuConfiguration expected =
        expectedCompleteConfigInFileBuilder().network(n -> n.networkInterface("1.2.3.5")).build();
    assertTekuAndLoggingConfiguration(
        expected, expectedCompleteConfigInFileLoggingBuilder().build());
  }

  @Test
  public void overrideConfigFileValuesIfKeyIsPresentInCLIOptions() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {
      CONFIG_FILE_OPTION_NAME, configFile.toString(), "--p2p-interface", "1.2.3.5"
    };

    beaconNodeCommand.parse(args);

    final TekuConfiguration expected =
        expectedCompleteConfigInFileBuilder().network(n -> n.networkInterface("1.2.3.5")).build();
    assertTekuAndLoggingConfiguration(
        expected, expectedCompleteConfigInFileLoggingBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInEnvironmentVariables() {
    beaconNodeCommand =
        new BeaconNodeCommand(
            outputWriter,
            errorWriter,
            Map.of("TEKU_DATA_PATH", dataPath.toString(), "TEKU_P2P_ENABLED", "false"),
            startAction,
            loggingConfigurator);

    beaconNodeCommand.parse(new String[] {});

    assertTekuAndLoggingConfiguration(
        expectedDefaultConfigurationBuilder().build(), expectedDefaultLoggingBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInCLIOptions() {
    final String[] args = createCliArgs();

    beaconNodeCommand.parse(args);

    TekuConfiguration configuration = expectedConfigurationBuilder().build();
    assertTekuAndLoggingConfiguration(configuration, expectedLoggingBuilder().build());
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final Path configFile = createConfigFile();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile.toString()};

    beaconNodeCommand.parse(args);

    assertTekuAndLoggingConfiguration(
        expectedCompleteConfigInFileBuilder().build(),
        expectedCompleteConfigInFileLoggingBuilder().build());
  }

  @Test
  public void interopEnabled_shouldNotRequireAValue() {
    final InteropConfig config =
        getTekuConfigurationFromArguments("--Xinterop-enabled").beaconChain().interopConfig();
    assertThat(config.isInteropEnabled()).isTrue();
  }

  @Test
  public void checkThatNoCLIArgumentsYieldsDefaultConfig() {
    beaconNodeCommand.parse(new String[0]);
    assertTekuConfiguration(createConfigBuilder().build());
  }

  @Test
  public void shouldSetNatMethod() {
    final String[] args = {"--p2p-nat-method", "upnp"};
    beaconNodeCommand.parse(args);
    assertThat(beaconNodeCommand.tekuConfiguration().natConfiguration().getNatMethod())
        .isEqualTo(NatMethod.UPNP);
  }

  @Test
  public void doppelgangerDetectionShouldBeDisabledByDefault() {
    final String[] args = {};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand
                .tekuConfiguration()
                .validatorClient()
                .getValidatorConfig()
                .isDoppelgangerDetectionEnabled())
        .isFalse();
  }

  @Test
  public void shouldEnableDoppelgangerDetection() {
    final String[] args = {DOPPELGANGER_DETECTION_ENABLED_OPTION, "true"};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand
                .tekuConfiguration()
                .validatorClient()
                .getValidatorConfig()
                .isDoppelgangerDetectionEnabled())
        .isTrue();
  }

  @Test
  public void shouldDisableDoppelgangerDetection() {
    final String[] args = {DOPPELGANGER_DETECTION_ENABLED_OPTION, "false"};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand
                .tekuConfiguration()
                .validatorClient()
                .getValidatorConfig()
                .isDoppelgangerDetectionEnabled())
        .isFalse();
  }

  @Test
  public void shouldIgnoreSmallDenebEpochsStoreBlobs() {
    final String[] args = {XEPOCHS_STORE_BLOBS_OPTION, "2000", XDENEB_FORK_EPOCH_OPTION, "200000"};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand.tekuConfiguration().eth2NetworkConfiguration().getEpochsStoreBlobs())
        .contains(2000);

    final SpecConfigDeneb specConfigDeneb =
        SpecConfigDeneb.required(
            beaconNodeCommand
                .tekuConfiguration()
                .eth2NetworkConfiguration()
                .getSpec()
                .forMilestone(SpecMilestone.DENEB)
                .getConfig());
    // not overridden in spec however
    assertThat(specConfigDeneb.getEpochsStoreBlobs()).isEqualTo(4096);
  }

  @Test
  public void shouldMaxDenebEpochsStoreBlobs() {
    final String[] args = {XEPOCHS_STORE_BLOBS_OPTION, "MAX"};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand.tekuConfiguration().eth2NetworkConfiguration().getEpochsStoreBlobs())
        .contains(Integer.MAX_VALUE);
  }

  @Test
  public void shouldParseDenebEpochsStoreBlobs() {
    final String[] args = {XEPOCHS_STORE_BLOBS_OPTION, "12345", XDENEB_FORK_EPOCH_OPTION, "200000"};
    beaconNodeCommand.parse(args);
    assertThat(
            beaconNodeCommand.tekuConfiguration().eth2NetworkConfiguration().getEpochsStoreBlobs())
        .contains(12345);
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigDeneb.required(
            beaconNodeCommand
                .tekuConfiguration()
                .eth2NetworkConfiguration()
                .getSpec()
                .forMilestone(SpecMilestone.DENEB)
                .getConfig());
    assertThat(specConfigDeneb.getEpochsStoreBlobs()).isEqualTo(12345);
  }

  @Test
  public void shouldHaveTrustedSetupConfiguredForDeneb() {
    final String[] args = {XDENEB_FORK_EPOCH_OPTION, "200000"};
    beaconNodeCommand.parse(args);
    final Optional<String> trustedSetup =
        beaconNodeCommand.tekuConfiguration().eth2NetworkConfiguration().getTrustedSetup();
    assertThat(trustedSetup).isPresent();
  }

  @Test
  public void shouldParseBuilderOptions() {
    final String[] args = {
      "--Xbuilder-min-bid",
      "42",
      "--Xbuilder-boost-factor",
      "80",
      "--Xbuilder-urls",
      "https://foobar.com,https://plataberget.com"
    };
    beaconNodeCommand.parse(args);
    final ValidatorConfig validatorConfig =
        beaconNodeCommand.tekuConfiguration().validatorClient().getValidatorConfig();
    assertThat(validatorConfig.getBuilderMinBid()).isEqualTo(UInt64.valueOf(42));
    assertThat(validatorConfig.getBuilderBoostFactor()).isEqualTo(UInt64.valueOf(80));
    assertThat(validatorConfig.getBuilderUrls()).hasSize(2);
  }

  @Test
  @Disabled("used for ad-hoc reconciliation between CLI and docs")
  public void verifyCliOptionsAndDocsConsistency() throws IOException {
    beaconNodeCommand.parse(new String[] {"--help"});
    beaconNodeCommand.parse(new String[] {"vc", "--help"});
    final String commandLineOutput = getCommandLineOutput();

    final List<String> allCliOptionsWithoutHyphens =
        getCommandLineOptionsFromCommandLineOutput(commandLineOutput, Pattern.compile("--[\\w-]+"))
            .stream()
            .map(option -> option.replaceFirst("--", ""))
            .distinct()
            .toList();

    final List<String> docsCliOptions =
        Stream.of(
                Jsoup.connect("https://docs.teku.consensys.io/reference/cli").get(),
                Jsoup.connect(
                        "https://docs.teku.consensys.io/reference/cli/subcommands/validator-client")
                    .get())
            .flatMap(doc -> doc.selectStream("h2.anchor, h3.anchor"))
            .flatMap(anchor -> anchor.selectStream("code"))
            .map(Element::text)
            .map(String::trim)
            .toList();

    final Set<String> cliOnly = new TreeSet<>(allCliOptionsWithoutHyphens);
    docsCliOptions.forEach(cliOnly::remove);

    final Set<String> docsOnly = new TreeSet<>(docsCliOptions);
    allCliOptionsWithoutHyphens.forEach(docsOnly::remove);
    // this option doesn't show in `teku vc --help` but it's available (see ValidatorClientOptions)
    docsOnly.remove("sentry-config-file");

    if (!cliOnly.isEmpty() || !docsOnly.isEmpty()) {
      throw new AssertionError(
          "Mismatch between CLI and docs.\n"
              + "\uD83D\uDD0D CLI options missing from docs: "
              + cliOnly
              + "\n"
              + "\uD83D\uDCDA Docs options missing from CLI: "
              + docsOnly);
    }
  }

  private Path createConfigFile() throws IOException {
    final URL configFile = BeaconNodeCommandTest.class.getResource("/complete_config.yaml");
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace("data-path: \".\"", "data-path: \"" + dataPath.toString() + "\"");
    return createTempFile(updatedConfig.getBytes(UTF_8));
  }

  private Path createInvalidConfigFile() throws IOException {
    final URL configFile = BeaconNodeCommandTest.class.getResource("/complete_config.yaml");
    final String updatedConfig =
        Resources.toString(configFile, UTF_8).replace("network:", "xnetwork:");
    return createTempFile(updatedConfig.getBytes(UTF_8));
  }

  private String[] createCliArgs() {
    return new String[] {
      "--network",
      "minimal",
      "--p2p-enabled",
      "false",
      "--p2p-interface",
      "1.2.3.4",
      "--p2p-port",
      "1234",
      "--p2p-discovery-enabled",
      "false",
      "--p2p-advertised-port",
      "9000",
      "--p2p-private-key-file",
      "path/to/file",
      "--Xinterop-genesis-time",
      "1",
      "--Xinterop-owned-validator-start-index",
      "0",
      "--Xinterop-owned-validator-count",
      "64",
      "--initial-state",
      "",
      "--Xinterop-number-of-validators",
      "64",
      "--Xinterop-enabled",
      "true",
      "--eth1-deposit-contract-address",
      "0x77f7bED277449F51505a4C54550B074030d989bC",
      "--ee-endpoint",
      "http://localhost:8550",
      "--metrics-enabled",
      "false",
      "--metrics-port",
      "8008",
      "--metrics-interface",
      "127.0.0.1",
      "--metrics-categories",
      "BEACON,LIBP2P,NETWORK,EVENTBUS,JVM,PROCESS",
      "--data-path",
      dataPath.toString(),
      "--data-storage-mode",
      "prune",
      "--rest-api-port",
      "5051",
      "--rest-api-docs-enabled",
      "false",
      "--rest-api-enabled",
      "false",
      "--Xrest-api-light-client-enabled",
      "false",
      "--rest-api-interface",
      "127.0.0.1",
      "--Xrest-api-max-url-length",
      "65535",
      "--Xpeer-blocks-rate-limit",
      "500",
      "--Xpeer-blob-sidecars-rate-limit",
      "2000",
      "--Xpeer-request-limit",
      "100"
    };
  }

  private TekuConfiguration.Builder expectedDefaultConfigurationBuilder() {
    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder("mainnet").build();

    return expectedConfigurationBuilder()
        .eth2NetworkConfig(b -> b.applyNetworkDefaults("mainnet"))
        .executionLayer(b -> b.engineEndpoint(null))
        .powchain(
            b -> {
              b.depositContract(networkConfig.getEth1DepositContractAddress());
              b.depositContractDeployBlock(networkConfig.getEth1DepositContractDeployBlock());
            })
        .storageConfiguration(
            b ->
                b.eth1DepositContract(networkConfig.getEth1DepositContractAddress())
                    .dataStorageMode(MINIMAL))
        .metrics(b -> b.metricsCategories(DEFAULT_METRICS_CATEGORIES))
        .restApi(b -> b.eth1DepositContractAddress(networkConfig.getEth1DepositContractAddress()))
        .p2p(p -> p.peerBlocksRateLimit(500).peerBlobSidecarsRateLimit(2000).peerRequestLimit(100))
        .discovery(
            d ->
                d.isDiscoveryEnabled(true)
                    .listenUdpPort(9000)
                    .bootnodes(networkConfig.getDiscoveryBootnodes()))
        .network(
            n ->
                n.advertisedPort(OptionalInt.empty())
                    .networkInterface("0.0.0.0")
                    .listenPort(9000)
                    .privateKeyFile(""))
        .validator(
            b ->
                b.validatorKeystoreLockingEnabled(true)
                    .validatorPerformanceTrackingMode(ValidatorPerformanceTrackingMode.ALL))
        .interop(b -> b.interopEnabled(false).interopGenesisTime(0).interopOwnedValidatorCount(0));
  }

  private TekuConfiguration.Builder expectedCompleteConfigInFileBuilder() {
    return expectedConfigurationBuilder();
  }

  private TekuConfiguration.Builder expectedConfigurationBuilder() {
    return TekuConfiguration.builder()
        .eth2NetworkConfig(b -> b.applyMinimalNetworkDefaults().eth1DepositContractAddress(address))
        .executionLayer(b -> b.engineEndpoint("http://localhost:8550"))
        .powchain(b -> b.depositContract(address))
        .store(b -> b.hotStatePersistenceFrequencyInEpochs(2))
        .storageConfiguration(
            b ->
                b.eth1DepositContract(address)
                    .dataStorageMode(PRUNE)
                    .dataStorageFrequency(StorageConfiguration.DEFAULT_STORAGE_FREQUENCY)
                    .dataStorageCreateDbVersion(DatabaseVersion.DEFAULT_VERSION)
                    .maxKnownNodeCacheSize(StorageConfiguration.DEFAULT_MAX_KNOWN_NODE_CACHE_SIZE))
        .data(b -> b.dataBasePath(dataPath))
        .p2p(
            b ->
                b.targetSubnetSubscriberCount(DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT)
                    .peerBlocksRateLimit(500)
                    .peerBlobSidecarsRateLimit(2000)
                    .peerRequestLimit(100))
        .discovery(
            d ->
                d.isDiscoveryEnabled(false)
                    .listenUdpPort(1234)
                    .advertisedUdpPort(OptionalInt.of(9000))
                    .minPeers(DEFAULT_P2P_PEERS_LOWER_BOUND)
                    .maxPeers(DEFAULT_P2P_PEERS_UPPER_BOUND)
                    .minRandomlySelectedPeers(
                        DEFAULT_P2P_PEERS_LOWER_BOUND
                            * DEFAULT_RANDOMLY_SELECTED_PEER_COUNT_PERCENTAGE
                            / 100))
        .network(
            n ->
                n.isEnabled(false)
                    .networkInterface("1.2.3.4")
                    .listenPort(1234)
                    .advertisedPort(OptionalInt.of(9000))
                    .advertisedIp(Optional.empty())
                    .privateKeyFile("path/to/file"))
        .sync(s -> s.isSyncEnabled(false).isMultiPeerSyncEnabled(true))
        .restApi(
            b ->
                b.restApiPort(5051)
                    .restApiDocsEnabled(false)
                    .restApiEnabled(false)
                    .restApiLightClientEnabled(false)
                    .restApiInterface("127.0.0.1")
                    .restApiHostAllowlist(List.of("127.0.0.1", "localhost"))
                    .restApiCorsAllowedOrigins(new ArrayList<>())
                    .eth1DepositContractAddress(address)
                    .maxUrlLength(65535)
                    .maxPendingEvents(BeaconRestApiConfig.DEFAULT_MAX_EVENT_QUEUE_SIZE)
                    .validatorThreads(Optional.empty()))
        .validatorApi(
            b ->
                b.restApiPort(5052)
                    .maxUrlLength(65535)
                    .restApiInterface("127.0.0.1")
                    .restApiHostAllowlist(List.of("127.0.0.1", "localhost"))
                    .restApiCorsAllowedOrigins(new ArrayList<>()))
        .validator(
            b ->
                b.validatorExternalSignerTimeout(Duration.ofSeconds(5))
                    .validatorExternalSignerConcurrentRequestLimit(32)
                    .validatorKeystoreLockingEnabled(true)
                    .validatorPerformanceTrackingMode(ValidatorPerformanceTrackingMode.ALL)
                    .graffitiProvider(new FileBackedGraffitiProvider())
                    .generateEarlyAttestations(true)
                    .doppelgangerDetectionEnabled(
                        ValidatorConfig.DEFAULT_DOPPELGANGER_DETECTION_ENABLED))
        .metrics(
            b ->
                b.metricsEnabled(false)
                    .metricsPort(8008)
                    .metricsInterface("127.0.0.1")
                    .metricsCategories(Set.of(BEACON, LIBP2P, NETWORK, EVENTBUS, JVM, PROCESS))
                    .metricsHostAllowlist(List.of("127.0.0.1", "localhost"))
                    .idleTimeoutSeconds(60))
        .interop(
            b ->
                b.interopGenesisTime(1)
                    .interopOwnedValidatorStartIndex(0)
                    .interopOwnedValidatorCount(64)
                    .interopNumberOfValidators(64)
                    .interopEnabled(true))
        .natConfig(b -> b.natMethod(NatMethod.NONE));
  }

  public LoggingConfigBuilder expectedDefaultLoggingBuilder() {
    return expectedLoggingBuilder()
        .destination(DEFAULT_BOTH)
        .logPath(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_FILE))
        .logPathPattern(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_PATTERN));
  }

  private LoggingConfigBuilder expectedCompleteConfigInFileLoggingBuilder() {
    return expectedLoggingBuilder()
        .destination(BOTH)
        .logPath(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_FILE))
        .logPathPattern(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_PATTERN));
  }

  private LoggingConfigBuilder expectedLoggingBuilder() {
    LoggingConfigBuilder builder = LoggingConfig.builder();
    return builder
        .colorEnabled(true)
        .destination(DEFAULT_BOTH)
        .logPath(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_FILE))
        .logPathPattern(StringUtils.joinWith(SLASH, dataPath.toString(), "logs", LOG_PATTERN))
        .includeEventsEnabled(true)
        .includeValidatorDutiesEnabled(true);
  }

  private void assertTekuAndLoggingConfiguration(
      final TekuConfiguration expected, final LoggingConfig expectedLogging) {
    assertTekuConfiguration(expected);
    final LoggingConfig actualLogging = getResultingLoggingConfiguration();
    assertThat(actualLogging).isEqualTo(expectedLogging);
  }

  private void assertTekuConfiguration(final TekuConfiguration expected) {
    final TekuConfiguration actual = getResultingTekuConfiguration();

    // Assert Spec object separately to avoid recursion issues
    assertThat(actual.eth2NetworkConfiguration().getSpec())
        .isEqualTo(expected.eth2NetworkConfiguration().getSpec());

    // Ignore Spec recursion and synchronization locks from lazy suppliers.
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFieldsOfTypes(Spec.class)
        .ignoringFieldsMatchingRegexes(".*\\.lock")
        .isEqualTo(expected);
  }

  private Path createTempFile(final byte[] contents) throws IOException {
    final Path file = Files.createTempFile("config", "yaml");
    Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }

  private List<String> getCommandLineOptionsFromCommandLineOutput(
      final String commandLineOutput, final Pattern pattern) {
    final Matcher matcher = pattern.matcher(commandLineOutput);
    final List<String> commandLineOptions = new ArrayList<>();
    while (matcher.find()) {
      final MatchResult current = matcher.toMatchResult();
      LOG.debug("found {} at position {}", current.group().trim(), current.start());
      commandLineOptions.add(current.group().trim());
    }
    return commandLineOptions;
  }

  @ParameterizedTest
  @MethodSource("exceptionsAndExpectedStatusCodeParam")
  public void handlingExceptionShouldReturnExpectedStatusCode(
      final Throwable throwable, final int expectedStatusCode) {
    assertThat(beaconNodeCommand.handleExceptionAndReturnExitCode(throwable))
        .isEqualTo(expectedStatusCode);
  }

  private static Stream<Arguments> exceptionsAndExpectedStatusCodeParam() {
    return Stream.of(
        Arguments.of(new IllegalStateException("foo"), 1),
        Arguments.of(new RuntimeException("foo"), 1),
        Arguments.of(new InvalidConfigurationException("foo"), 2),
        Arguments.of(DatabaseStorageException.unrecoverable("foo"), 2),
        // Even when wrapped on something like completion exception, we still want it to return 2
        Arguments.of(new CompletionException(new InvalidConfigurationException("foo")), 2),
        Arguments.of(new CompletionException(DatabaseStorageException.unrecoverable("foo")), 2));
  }
}
