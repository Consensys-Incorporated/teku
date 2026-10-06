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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorOptionsTest extends AbstractBeaconNodeCommandTest {

  // from config file ("T E K U") UTF8 -> bytes32 -> as hex string
  private final Bytes32 graffiti =
      Bytes32.fromHexString("0x542045204b205500000000000000000000000000000000000000000000000000");

  @Test
  public void shouldReadFromConfigurationFile() throws MalformedURLException {
    final ValidatorConfig config =
        getTekuConfigurationFromFile("validatorOptions_config.yaml")
            .validatorClient()
            .getValidatorConfig();

    assertThat(config.getValidatorKeys())
        .containsExactlyInAnyOrder("a.key:a.password", "b.json:b.txt");
    assertThat(config.getValidatorExternalSignerPublicKeySources())
        .containsExactly(
            "0xad113a7d152dc74ae2b26db65bfb89ed07501c818bf47671c6d34e5a2f7224e4c5525dd4fddaa93aa328da86b7205009");
    assertThat(config.getValidatorExternalSignerUrl())
        .isEqualTo(URI.create("https://signer.url/").toURL());
    assertThat(config.getValidatorExternalSignerTimeout()).isEqualTo(Duration.ofMillis(1234));
    assertThat(config.getGraffitiProvider().get()).isEqualTo(Optional.of(graffiti));
  }

  @Test
  public void shouldReadValidatorExternalSignerConcurrentRequestLimit() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments("--validators-external-signer-concurrent-limit=123")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.getValidatorExternalSignerConcurrentRequestLimit()).isEqualTo(123);
  }

  @Test
  public void graffiti_shouldBeEmptyByDefault() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.getGraffitiProvider().get()).isEmpty();
  }

  @Test
  void shouldEnableSlashingProtectionForExternalSignersByDefault() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.isValidatorExternalSignerSlashingProtectionEnabled()).isTrue();
  }

  @Test
  void shouldDisableSlashingProtectionForExternalSigners() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments(
                "--validators-external-signer-slashing-protection-enabled=false")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.isValidatorExternalSignerSlashingProtectionEnabled()).isFalse();
  }

  @Test
  void shouldLoadGraffitiFromFile() {
    final ValidatorConfig config =
        getTekuConfigurationFromFile("validatorOptionsWithGraffitiFile_config.yaml")
            .validatorClient()
            .getValidatorConfig();

    assertThat(config.getGraffitiProvider().get()).isEqualTo(Optional.of(graffiti));
  }

  @Test
  void beaconNodeApiEndpoints_shouldBeEmptyByDefault() {
    TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    assertThat(tekuConfiguration.validatorClient().getValidatorConfig().getBeaconNodeApiEndpoints())
        .isEmpty();
  }

  @Test
  public void shouldReportEmptyIfFeeRecipientNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.validatorClient().getValidatorConfig().getProposerDefaultFeeRecipient())
        .isEmpty();
  }

  @Test
  public void shouldReportAddressIfFeeRecipientSpecified() {
    final String[] args = {
      "--validators-proposer-default-fee-recipient", "0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.validatorClient().getValidatorConfig().getProposerDefaultFeeRecipient())
        .isEqualTo(
            Optional.of(Eth1Address.fromHexString("0xFE3B557E8Fb62b89F4916B721be55cEb828dBd73")));
  }

  @Test
  public void shouldSetValidatorRegistrationTimestampOverride() {
    final String[] args = {"--Xvalidators-builder-registration-timestamp-override", "120000"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config.validatorClient().getValidatorConfig().getBuilderRegistrationTimestampOverride())
        .isEqualTo(Optional.of(UInt64.valueOf(120000)));
  }

  @Test
  public void shouldReportEmptyIfValidatorRegistrationPublicKeyOverrideNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(
            config.validatorClient().getValidatorConfig().getBuilderRegistrationPublicKeyOverride())
        .isEmpty();
  }

  @Test
  public void shouldSetValidatorRegistrationPublicKeyOverride() {
    final String[] args = {
      "--Xvalidators-builder-registration-public-key-override",
      "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config.validatorClient().getValidatorConfig().getBuilderRegistrationPublicKeyOverride())
        .isEqualTo(
            Optional.of(
                BLSPublicKey.fromHexString(
                    "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a")));
  }

  @Test
  public void shouldNotUseValidatorsRegistrationByDefault() {
    final String[] args = {};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.validatorClient().getValidatorConfig().isBuilderRegistrationDefaultEnabled())
        .isFalse();
  }

  @Test
  public void shouldNotSetDefaultGasLimitIfRegistrationDefaultGasLimitNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(
            config.validatorClient().getValidatorConfig().getBuilderRegistrationDefaultGasLimit())
        .isEmpty();
  }

  @Test
  public void shouldSetDefaultGasLimitIfRegistrationDefaultGasLimitIsSpecified() {
    final String[] args = {"--validators-builder-registration-default-gas-limit", "1000"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config.validatorClient().getValidatorConfig().getBuilderRegistrationDefaultGasLimit())
        .contains(UInt64.valueOf(1000));
  }

  @Test
  public void shouldDefaultFalseExitWhenNoValidatorKeysEnabled() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.isExitWhenNoValidatorKeysEnabled()).isFalse();
  }

  @Test
  public void shouldSetExitWhenNoValidatorKeysEnabled() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments("--exit-when-no-validator-keys-enabled=true")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.isExitWhenNoValidatorKeysEnabled()).isTrue();
  }

  @Test
  public void shouldDefaultFalseShutdownWhenValidatorSlashedEnabled() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.isShutdownWhenValidatorSlashedEnabled()).isFalse();
  }

  @Test
  public void shouldSetShutdownWhenValidatorSlashedEnabled() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments("--shut-down-when-validator-slashed-enabled=true")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.isShutdownWhenValidatorSlashedEnabled()).isTrue();
  }

  @Test
  public void shouldSetDefaultGraffitiClientAppend() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments().validatorClient().getValidatorConfig();
    assertThat(config.getClientGraffitiAppendFormat()).isEqualTo(ClientGraffitiAppendFormat.AUTO);
  }

  @Test
  public void shouldOverrideGraffitiClientAppend() {
    final ValidatorConfig config =
        getTekuConfigurationFromArguments("--validators-graffiti-client-append-format=CLIENT_CODES")
            .validatorClient()
            .getValidatorConfig();
    assertThat(config.getClientGraffitiAppendFormat())
        .isEqualTo(ClientGraffitiAppendFormat.CLIENT_CODES);
  }

  @Test
  public void shouldNotUseDvtSelectionsEndpointByDefault() {
    final String[] args = {};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.validatorClient().getValidatorConfig().isDvtSelectionsEndpointEnabled())
        .isFalse();
  }

  @Test
  public void validatorClientBeaconApiExecutorsEmptyByDefault() {
    final TekuConfiguration tekuConfiguration = getTekuConfigurationFromArguments();
    assertThat(
            tekuConfiguration.validatorClient().getValidatorConfig().getBeaconApiExecutorThreads())
        .isEmpty();
    assertThat(
            tekuConfiguration
                .validatorClient()
                .getValidatorConfig()
                .getBeaconApiReadinessExecutorThreads())
        .isEmpty();
  }

  @Test
  public void validatorClientBeaconApiExecutorsCanBeSet() {
    final TekuConfiguration tekuConfiguration =
        getTekuConfigurationFromArguments(
            "--Xvalidator-client-beacon-api-executor-threads=2",
            "--Xvalidator-client-beacon-api-readiness-executor-threads=4");
    assertThat(
            tekuConfiguration.validatorClient().getValidatorConfig().getBeaconApiExecutorThreads())
        .hasValue(2);
    assertThat(
            tekuConfiguration
                .validatorClient()
                .getValidatorConfig()
                .getBeaconApiReadinessExecutorThreads())
        .hasValue(4);
  }
}
