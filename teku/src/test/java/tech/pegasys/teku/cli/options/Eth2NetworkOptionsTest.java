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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.FINALIZED_STATE_URL_PATH;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.GENESIS_STATE_URL_PATH;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

class Eth2NetworkOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  void shouldEnableDenebByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getHighestSupportedMilestone()).isEqualTo(SpecMilestone.FULU);
  }

  @Test
  void shouldUseAltairForkEpochIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xnetwork-altair-fork-epoch", "64");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(63)))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(64)))
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  void shouldUseTrustedSetupIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xtrusted-setup", "/test.txt");
    final Optional<String> trustedSetup = config.eth2NetworkConfiguration().getTrustedSetup();
    assertThat(trustedSetup).isEqualTo(Optional.of("/test.txt"));
  }

  @Test
  void shouldUseDefaultTrustedSetupIfUnspecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final Optional<String> trustedSetup = config.eth2NetworkConfiguration().getTrustedSetup();
    assertThat(trustedSetup)
        .matches(ts -> ts.map(path -> path.endsWith("mainnet-trusted-setup.txt")).orElse(false));
  }

  @Test
  void shouldPrepareBlockProductionIsEnabledByDefaultOnMainnet() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isPrepareBlockProductionEnabled()).isTrue();
  }

  @Test
  void shouldPrepareBlockProductionIsEnabledByDefaultOnHoodi() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--network", "hoodi");
    assertThat(config.eth2NetworkConfiguration().isPrepareBlockProductionEnabled()).isTrue();
  }

  @Test
  void shouldDisablePrepareBlockProduction() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xprepare-block-production-enabled", "false");
    assertThat(config.eth2NetworkConfiguration().isPrepareBlockProductionEnabled()).isFalse();
  }

  @Test
  void shouldDisablePrepareBlockProductionForGnosis() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--network", "gnosis", "--Xprepare-block-production-enabled", "true");
    assertThat(config.eth2NetworkConfiguration().isPrepareBlockProductionEnabled()).isFalse();
  }

  @Test
  void shouldUseBellatrixForkEpochIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch", "120000", "--ee-endpoint", "someEndpoint");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(119999)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(120000)))
        .isEqualTo(SpecMilestone.BELLATRIX);
  }

  @Test
  void shouldUseCustomSafeSlotsToImportOptimistically() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch",
            "120000",
            "--ee-endpoint",
            "someEndpoint",
            "--Xnetwork-safe-slots-to-import-optimistically",
            "256");
    final Spec spec = config.eth2NetworkConfiguration().getSpec();
    assertThat(
            spec.forMilestone(SpecMilestone.BELLATRIX)
                .getConfig()
                .toVersionBellatrix()
                .orElseThrow()
                .getSafeSlotsToImportOptimistically())
        .isEqualTo(256);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void shouldSetLateBlockImportEnabled(final String value) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xfork-choice-late-block-reorg-enabled", value);
    assertThat(config.eth2NetworkConfiguration().isForkChoiceLateBlockReorgEnabled())
        .isEqualTo(Boolean.valueOf(value));
  }

  @Test
  void shouldDisableFastConfirmationByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isFastConfirmationEnabled()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void shouldSetFastConfirmationEnabled(final String value) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xfast-confirmation-enabled", value);
    assertThat(config.eth2NetworkConfiguration().isFastConfirmationEnabled())
        .isEqualTo(Boolean.valueOf(value));
  }

  @Test
  void shouldAggregatingAttestationPoolProfilerDisabledByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isAggregatingAttestationPoolProfilingEnabled())
        .isEqualTo(false);
  }

  @Test
  void shouldUseDefaultAlwaysSendPayloadAttributesIfUnspecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isForkChoiceUpdatedAlwaysSendPayloadAttributes())
        .isEqualTo(false);
  }

  @Test
  void shouldUseAlwaysSendPayloadAttributesIfSpecified() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xfork-choice-updated-always-send-payload-attributes", "true");
    assertThat(config.eth2NetworkConfiguration().isForkChoiceUpdatedAlwaysSendPayloadAttributes())
        .isEqualTo(true);
  }

  @Test
  void shouldMergeTransitionsOverrideContainsMainnetTransitionByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().getTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.empty());
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashOverride())
        .contains(
            Bytes32.fromHexString(
                "0x55b11b918355b1ef9c5db810302ebad0bf2544255b530cdce90674d5887bb286"));
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride())
        .contains(UInt64.valueOf(146875));
  }

  @Test
  void minimalNetwork_shouldMergeTransitionsOverrideBeEmptyByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--network", "minimal");
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getGenesisState())
        .isEqualTo(Optional.empty());
  }

  @Test
  void shouldLoadMergeTerminalTotalDifficultyOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-total-terminal-difficulty-override", "123456789012345678901");
    assertThat(config.eth2NetworkConfiguration().getTotalTerminalDifficultyOverride())
        .isEqualTo(Optional.of(UInt256.valueOf(new BigInteger("123456789012345678901"))));
  }

  @Test
  void shouldLoadMergeTerminalBlockHashOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-terminal-block-hash-override",
            "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e");
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashOverride())
        .isEqualTo(
            Optional.of(
                Bytes32.fromHexStringStrict(
                    "0x7562f205a2d14e80a3a67da9df0b769b0ba0111a8e81034606f8f27f51f4dd8e")));
  }

  @Test
  void shouldLoadTerminalBlockHashEpochOverride() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-terminal-block-hash-epoch-override", "120000");
    assertThat(config.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride())
        .isEqualTo(Optional.of(UInt64.valueOf(120000)));
  }

  @Test
  void shouldFailLoadingInvalidTransitionOverrides() {
    assertThrows(
        AssertionError.class,
        () ->
            getTekuConfigurationFromArguments(
                "--Xnetwork-total-terminal-difficulty-override", "asd"));

    assertThrows(
        AssertionError.class,
        () -> getTekuConfigurationFromArguments("--Xnetwork-terminal-block-hash-override", "756"));

    assertThrows(
        AssertionError.class,
        () ->
            getTekuConfigurationFromArguments(
                "--Xnetwork-terminal-block-hash-epoch-override", "asd"));
  }

  @Test
  void shouldLoadGenesisState() {
    final String genesisState =
        "https://221EMZ2YSdriVVdXx:5058f100c7@eth2-beacon-mainnet.infura.io/eth/v1/debug/beacon/states/finalized";
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--genesis-state", genesisState);
    assertThat(config.eth2NetworkConfiguration().getNetworkBoostrapConfig().getGenesisState())
        .isEqualTo(Optional.of(genesisState));
  }

  @Test
  void shouldFailIfBothCheckpointSyncUrlAndInitialStateSet() {
    assertThatThrownBy(
            () ->
                getTekuConfigurationFromArguments(
                    "--checkpoint-sync-url", "http://foo:9000", "--initial-state", "genesis.ssz"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Both --initial-state and --checkpoint-sync-url are provided");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://foo:9000", "http://foo:9000/"})
  public void checkpointSyncUrlOptionShouldSetInitialAndGenesisStateOptions(
      final String beaconApiEndpoint) {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--checkpoint-sync-url", beaconApiEndpoint);

    final Eth2NetworkConfiguration networkConfiguration = config.eth2NetworkConfiguration();

    assertThat(networkConfiguration.getNetworkBoostrapConfig().getGenesisState())
        .hasValue("http://foo:9000/" + GENESIS_STATE_URL_PATH);
    assertThat(networkConfiguration.getNetworkBoostrapConfig().getInitialState())
        .hasValue("http://foo:9000/" + FINALIZED_STATE_URL_PATH);
  }

  @Test
  public void shouldShowGoerliDeprecationWarning() {
    assertThatThrownBy(() -> getTekuConfigurationFromArguments("--network", "goerli"))
        .isInstanceOf(AssertionError.class) // thrown because we had an error
        .hasMessageContaining("Goerli support has been removed");
  }

  @Test
  public void rustKzgFlagShouldBeDisabledByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.eth2NetworkConfiguration().isRustKzgEnabled()).isFalse();
  }

  @Test
  public void rustKzgFlagCanBeUsedToToggleRustKzgOn() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--Xrust-kzg-enabled");
    assertThat(config.eth2NetworkConfiguration().isRustKzgEnabled()).isTrue();
  }

  @Test
  void shouldUseSetDataColumnSidecarRecoveryDelay() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xdata-column-sidecar-recovery-max-delay", "2300");
    final OptionalLong maxDelayMillis =
        config.eth2NetworkConfiguration().getDataColumnSidecarRecoveryMaxDelayMillis();
    assertThat(maxDelayMillis).hasValue(2300L);
  }

  @Test
  void dataColumnSidecarRecoveryDelayEmptyByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    final OptionalLong maxDelayMillis =
        config.eth2NetworkConfiguration().getDataColumnSidecarRecoveryMaxDelayMillis();
    assertThat(maxDelayMillis).isEmpty();
  }

  @Test
  void invalidDataColumnSidecarRecoveryDelayShouldThrow() {
    assertThatThrownBy(
            () ->
                getTekuConfigurationFromArguments(
                    "--Xdata-column-sidecar-recovery-max-delay", "invalid"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "Invalid value for option '--Xdata-column-sidecar-recovery-max-delay'");
  }

  // Tests for fork epoch implicit defaulting

  @Test
  void shouldDefaultAltairToZeroWhenBellatrixSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.BELLATRIX);
  }

  @Test
  void shouldDefaultPreviousForksWhenCapellaSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-capella-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.CAPELLA);
  }

  @Test
  void shouldDefaultPreviousForksWhenDenebSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-deneb-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.DENEB)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.DENEB);
  }

  @Test
  void shouldDefaultPreviousForksWhenElectraSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-electra-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.DENEB)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.ELECTRA)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.ELECTRA);
  }

  @Test
  void shouldDefaultPreviousForksWhenFuluSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-fulu-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.DENEB)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.ELECTRA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.FULU)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.FULU);
  }

  @Test
  void shouldDefaultPreviousForksWhenGloasSetToZero() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-gloas-fork-epoch", "0", "--ee-endpoint", "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.DENEB)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.ELECTRA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.FULU)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.GLOAS)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.GLOAS);
  }

  @Test
  void shouldHandleExplicitZeroForkEpochs() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xnetwork-bellatrix-fork-epoch",
            "0",
            "--Xnetwork-deneb-fork-epoch",
            "0",
            "--ee-endpoint",
            "someEndpoint");
    final Eth2NetworkConfiguration networkConfig = config.eth2NetworkConfiguration();
    final Spec spec = networkConfig.getSpec();

    assertThat(networkConfig.getForkEpoch(SpecMilestone.ALTAIR)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.BELLATRIX)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.CAPELLA)).hasValue(UInt64.ZERO);
    assertThat(networkConfig.getForkEpoch(SpecMilestone.DENEB)).hasValue(UInt64.ZERO);
    assertThat(spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.ZERO))
        .isEqualTo(SpecMilestone.DENEB);
  }
}
