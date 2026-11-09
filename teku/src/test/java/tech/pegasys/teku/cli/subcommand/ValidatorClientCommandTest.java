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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_VALIDATOR_EXECUTOR_THREADS;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.NodeMode;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;
import tech.pegasys.teku.infrastructure.logging.LoggingDestination;

public class ValidatorClientCommandTest extends AbstractBeaconNodeCommandTest {

  @BeforeEach
  void setUp() {
    expectedNodeMode = NodeMode.VC_ONLY;
  }

  @Test
  public void networkOption_ShouldFail_IfSpecifiedOnParentCommand() {
    final String[] argsNetworkOptOnParent =
        new String[] {"--network", "auto", "vc", "--validator-keys=keys:pass"};
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(2);
    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains("--network option should not be specified before the validator-client command");
  }

  @Test
  void loggingOptions_shouldUseLoggingOptionsFromBeforeSubcommand() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments(
            "--log-destination=console", "vc", "--network=mainnet", "--validator-keys=keys:pass");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  void loggingOptions_shouldUseLoggingOptionsFromAfterSubcommand() {
    final LoggingConfig config =
        getLoggingConfigurationFromArguments(
            "vc", "--validator-keys=keys:pass", "--network=mainnet", "--log-destination=console");
    assertThat(config.getDestination()).isEqualTo(LoggingDestination.CONSOLE);
  }

  @Test
  public void shouldIgnoreNonMatchingSubcommandParams() {
    final TekuConfiguration configuration =
        getTekuConfigurationFromFile(
            "beaconAndValidatorOptions_config.yaml", Optional.of("validator-client"));
    assertThat(configuration.validatorClient().getValidatorConfig().getValidatorKeys())
        .containsExactly("a.key:a.password", "b.json:b.txt");
  }

  @Test
  public void sentryConfigOption_shouldBuildExpectedConfigWhenOptionHasFileNameParam() {
    final String sentryConfigPath = pathFor("sentry_node_config.json");
    final String[] argsWithSentryConfig =
        new String[] {
          "vc",
          "--network",
          "minimal",
          "--validator-keys=keys:pass",
          "--sentry-config-file",
          sentryConfigPath,
        };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(argsWithSentryConfig);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getSentryNodeConfigurationFile())
        .contains(sentryConfigPath);
  }

  @Test
  public void sentryConfigOption_emptyConfigWhenMissingSentryConfigFileParam() {
    final String[] argsWithoutSentryConfig =
        new String[] {"vc", "--network", "minimal", "--validator-keys=keys:pass"};

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(argsWithoutSentryConfig);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getSentryNodeConfigurationFile())
        .isEmpty();
  }

  @Test
  public void sentryConfigOption_shouldFailWhenOptionIsMissingRequiredFileNameParam() {
    final String[] argsWithSentryConfigMissingParam =
        new String[] {
          "vc", "--network", "minimal", "--validator-keys=keys:pass", "--sentry-config-file",
        };

    int parseResult = beaconNodeCommand.parse(argsWithSentryConfigMissingParam);
    assertThat(parseResult).isNotZero();

    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput).contains("Missing required parameter for option '--sentry-config-file'");
  }

  @Test
  public void shouldThrowErrorWhenUsingBeaconNodeEndpointAndSentryNodesConfig() {
    final String[] args = {
      "vc",
      "--network",
      "minimal",
      "--validator-keys=keys:pass",
      "--beacon-node-api-endpoint",
      "http://127.0.0.1:1234",
      "--sentry-config-file",
      "/tmp/foo.json"
    };

    int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isNotZero();

    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains(
            "Error: --beacon-node-api-endpoints=<ENDPOINT>, --sentry-config-file=<FILE> are "
                + "mutually exclusive (specify only one)");
  }

  @Test
  public void dutiesProviderSentryNodeEndpointIsUsedAsMainBeaconNodeApiEndpoint() {
    // From sentry_node_config.json
    final URI expectedBeaconNodeApiEndpoint = URI.create("http://duties:5051");

    final String[] args = {
      "vc",
      "--network",
      "minimal",
      "--validator-keys=keys:pass",
      "--sentry-config-file",
      pathFor("sentry_node_config.json")
    };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getBeaconNodeApiEndpoints())
        .contains(List.of(expectedBeaconNodeApiEndpoint));
  }

  @Test
  public void doppelgangerDetectionShouldBeDisabledByDefault() {

    final String[] args = {"vc", "--network", "minimal", "--validator-keys=keys:pass"};

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().isDoppelgangerDetectionEnabled())
        .isFalse();
  }

  @Test
  public void shouldEnableDoppelgangerDetection() {

    final String[] args = {
      "vc",
      "--network",
      "minimal",
      "--validator-keys=keys:pass",
      "--doppelganger-detection-enabled",
      "true"
    };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().isDoppelgangerDetectionEnabled())
        .isTrue();
  }

  @Test
  public void clientExecutorThreadsShouldBeDefaultValue() {

    final String[] args = {"vc", "--network", "minimal", "--validator-keys=keys:pass"};

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getExecutorThreads())
        .isEqualTo(DEFAULT_VALIDATOR_EXECUTOR_THREADS);
    assertThat(DEFAULT_VALIDATOR_EXECUTOR_THREADS).isGreaterThanOrEqualTo(5);
  }

  @Test
  public void clientExecutorThreadsShouldBeSetValue() {

    final String[] args = {
      "vc",
      "--validator-keys=keys:pass",
      "--network",
      "minimal",
      "--Xvalidator-client-executor-threads",
      "1000"
    };

    final TekuConfiguration tekuConfig = getTekuConfigurationFromArguments(args);

    assertThat(tekuConfig.validatorClient().getValidatorConfig().getExecutorThreads())
        .isEqualTo(1000);
  }

  @Test
  public void clientExecutorThreadsShouldThrowOverLimit() {
    final String[] args = {
      "vc",
      "--network",
      "minimal",
      "--validator-keys=keys:pass",
      "--Xvalidator-client-executor-threads",
      "6000"
    };

    int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isEqualTo(2);
    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains(
            "--Xvalidator-client-executor-threads must be greater than 0 and less than 5000.");
  }

  @Test
  public void shouldSetUseObolDvtSelectionsEndpoint() {
    final String[] args = {
      "vc", "--network", "minimal", "--validator-keys=keys:pass", "--Xobol-dvt-integration-enabled"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);

    assertThat(config.validatorClient().getValidatorConfig().isDvtSelectionsEndpointEnabled())
        .isTrue();
  }

  @Test
  public void shouldNotUseObolDvtSelectionsEndpointByDefault() {
    final String[] args = {"vc", "--network", "minimal", "--validator-keys=keys:pass"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.validatorClient().getValidatorConfig().isDvtSelectionsEndpointEnabled())
        .isFalse();
  }

  @Test
  public void shouldFail_IfNoValidatorKeysSourceProvided() {
    final String[] argsNetworkOptOnParent = new String[] {"vc", "--network", "minimal"};
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(2);
    String cmdOutput = getCommandLineOutput();
    assertThat(cmdOutput)
        .contains(
            "No validator keys source provided, should provide local or remote keys otherwise enable the key-manager"
                + " api to start the validator client");
  }

  @Test
  public void shouldNotFail_IfNoLocalValidatorKeys_ValidatorRestApiEnabled(
      @TempDir final Path tempPath) throws IOException {
    AssertionsForClassTypes.assertThat(tempPath.resolve("keystore").toFile().createNewFile())
        .isTrue();
    AssertionsForClassTypes.assertThat(tempPath.resolve("pass").toFile().createNewFile()).isTrue();
    final String[] argsNetworkOptOnParent =
        new String[] {
          "vc",
          "--network",
          "minimal",
          "--validator-api-enabled=true",
          "--validator-api-keystore-file",
          tempPath.resolve("keystore").toString(),
          "--validator-api-keystore-password-file",
          tempPath.resolve("pass").toString()
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
  }

  @Test
  public void shouldNotFail_IfNoLocalValidatorKeys_ExternalSignerUrlProvided() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "vc", "--network", "minimal", "--validators-external-signer-url=http://localhost"
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
  }

  private String pathFor(final String filename) {
    return Resources.getResource(ValidatorClientCommandTest.class, filename).toString();
  }
}
