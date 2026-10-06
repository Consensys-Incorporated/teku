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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllBellatrixFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllFieldsSet;
import static tech.pegasys.teku.spec.config.SpecConfigLoader.EPHEMERY_CONFIG_URL;
import static tech.pegasys.teku.spec.networks.Eth2Network.EPHEMERY;

import com.google.common.io.Resources;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecConfigLoaderTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(Eth2Network.class)
  public void shouldLoadAllKnownNetworks(final Eth2Network network) throws Exception {
    assumeThat(network).isNotEqualTo(EPHEMERY);
    final SpecConfig config = SpecConfigLoader.loadConfigStrict(network.configName()).specConfig();
    // testing latest SpecConfig ensures all fields will be asserted on
    assertAllFieldsSet(config, SpecConfigElectra.class);
  }

  @Test
  void shouldLoadEphemeryNetwork() throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig(EPHEMERY_CONFIG_URL).specConfig();
    assertAllFieldsSet(config, SpecConfigElectra.class);
  }

  /**
   * For the three networks supported by Infura, go the extra mile and ensure the CONFIG_NAME key is
   * still included in the raw config which is exposed by the config/spec REST API.
   *
   * <p>Prior to Altair, Lighthouse required this field to be a known testnet name, mainnet or
   * minimal. Post-Altair we will be able to remove this as the new PRESET_BASE key will be
   * sufficient.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"hoodi", "mainnet"})
  public void shouldMaintainConfigNameBackwardsCompatibility(final String name) {
    final SpecConfig config = SpecConfigLoader.loadConfig(name).specConfig();
    assertThat(config.getRawConfig().get("CONFIG_NAME")).isEqualTo(name);
  }

  @Test
  public void shouldLoadConfigWithoutGasLimitSchedule(@TempDir final Path tempDir)
      throws Exception {
    // a config predating EIP-8261 must still load: the field is optional
    final Path configFile = writeMainnetConfigWithout(tempDir, "GAS_LIMIT_SCHEDULE:");

    final SpecConfig config = SpecConfigLoader.loadConfigStrict(configFile.toString()).specConfig();

    AssertionsForInterfaceTypes.assertThat(SpecConfigGloas.required(config).getGasLimitSchedule())
        .isEmpty();
  }

  @Test
  public void shouldLoadConfigWithGasLimitSchedule(@TempDir final Path tempDir) throws Exception {
    final Path configFile = writeMainnetConfigWithout(tempDir, "GLOAS_FORK_EPOCH:");
    try (final FileWriter writer =
        new FileWriter(configFile.toFile(), StandardCharsets.UTF_8, true)) {
      writer.write(
          """

          GLOAS_FORK_EPOCH: 500000
          GAS_LIMIT_SCHEDULE:
            - EPOCH: 510000
              GAS_LIMIT: 45000000
            - EPOCH: 500000
              GAS_LIMIT: 60000000
          """);
    }

    final SpecConfig config = SpecConfigLoader.loadConfigStrict(configFile.toString()).specConfig();

    // entries are sorted by epoch
    AssertionsForInterfaceTypes.assertThat(SpecConfigGloas.required(config).getGasLimitSchedule())
        .containsExactly(
            new GasLimitScheduleEntry(UInt64.valueOf(500000), UInt64.valueOf(60_000_000)),
            new GasLimitScheduleEntry(UInt64.valueOf(510000), UInt64.valueOf(45_000_000)));
  }

  @Test
  public void shouldNotVerifyGasLimitScheduleWhenGloasIsNotScheduled(@TempDir final Path tempDir)
      throws Exception {
    final Path configFile = writeMainnetConfigWithout(tempDir, "GAS_LIMIT_SCHEDULE:");
    try (final FileWriter writer =
        new FileWriter(configFile.toFile(), StandardCharsets.UTF_8, true)) {
      writer.write(
          """

          GAS_LIMIT_SCHEDULE:
            - EPOCH: 500000
              GAS_LIMIT: 60000000
          """);
    }

    final SpecConfig config = SpecConfigLoader.loadConfigStrict(configFile.toString()).specConfig();

    assertThat(config.getGloasForkEpoch()).isEqualTo(SpecConfig.FAR_FUTURE_EPOCH);
    AssertionsForInterfaceTypes.assertThat(SpecConfigGloas.required(config).getGasLimitSchedule())
        .hasSize(1);
  }

  /** Copies the bundled mainnet config, dropping the (single line) entry starting with the key. */
  private Path writeMainnetConfigWithout(final Path tempDir, final String key) throws IOException {
    final Path configFile = tempDir.resolve("config.yaml");
    try (final InputStream inputStream = getMainnetConfigAsStream()) {
      final String config = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      Files.writeString(
          configFile,
          config.lines().filter(line -> !line.startsWith(key)).collect(Collectors.joining("\n")));
    }
    return configFile;
  }

  @Test
  public void shouldLoadMainnet() throws Exception {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet").specConfig();
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldReadConfigByDefaults(@TempDir final Path tempDir) throws Exception {
    final Path configFile = tempDir.resolve("config.yaml");
    Files.writeString(
        configFile,
        """
            CONFIG_NAME: 'mainnet'
            PRESET_BASE: 'mainnet'
            """);
    final SpecConfig config =
        SpecConfigLoader.loadConfig(configFile.toString(), false, true, __ -> {}).specConfig();
    assertAllBellatrixFieldsSet(config);
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldFailToReadConfigWithMissingItemsWhenStrict(@TempDir final Path tempDir)
      throws IOException {
    final Path configFile = tempDir.resolve("config.yaml");
    Files.writeString(configFile, "CONFIG_NAME: 'mainnet'");
    assertThatThrownBy(
            () -> SpecConfigLoader.loadConfig(configFile.toString(), true, true, __ -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "The specified network configuration had missing or invalid values for constants");
  }

  @Test
  public void shouldFailToReadConfigIfNotForgivingAndHasExtraItems(@TempDir final Path tempDir)
      throws Exception {
    final Path file = tempDir.resolve("mainnet.yml");
    try (final InputStream inputStream = getMainnetConfigAsStream()) {
      writeStreamToFile(inputStream, file);
    }
    try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8, true)) {
      writer.write("\nUNKNOWN_OPTION: FOO");
    }
    assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toString(), true, false, __ -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Detected unknown spec config entries: UNKNOWN_OPTION");
  }

  @Test
  public void shouldReadConfigByDefaultsWithoutPreset(@TempDir final Path tempDir)
      throws Exception {
    final Path configFile = tempDir.resolve("config.yaml");
    Files.writeString(configFile, "CONFIG_NAME: 'mainnet'\n");
    final SpecConfig config =
        SpecConfigLoader.loadConfig(configFile.toString(), false, true, __ -> {}).specConfig();
    assertAllBellatrixFieldsSet(config);
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldFailToReadConfigWithoutDefaulting(@TempDir final Path tempDir)
      throws Exception {
    final Path configFile = tempDir.resolve("config.yaml");
    Files.writeString(configFile, "PRESET_BASE: 'mainnet'\n");
    assertThatThrownBy(
            () -> SpecConfigLoader.loadConfig(configFile.toString(), true, true, __ -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "The specified network configuration had missing or invalid values for constants");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ALTAIR_FORK_VERSION",
        "BELLATRIX_FORK_VERSION",
        "CAPELLA_FORK_VERSION",
        "DENEB_FORK_VERSION"
      })
  public void shouldFailToReadConfigMissingKeyVariablesIfStrict(
      final String parameter, @TempDir final Path tempDir) throws Exception {
    final Path file = tempDir.resolve("mainnet.yml");
    try (final InputStream inputStream = getMainnetConfigAsStream();
        final FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
      final InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
      final BufferedReader reader = new BufferedReader(isr);
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.contains(parameter)) {
          writer.write(line + "\n");
        }
      }
    }
    assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toString(), true, true, __ -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(parameter);
  }

  @Test
  public void shouldFailToReadConfigWithMissingConfigName(@TempDir final Path tempDir)
      throws Exception {
    final Path configFile = tempDir.resolve("config.yaml");
    Files.writeString(
        configFile,
        """
            CONFIG_NAME: 'missing'
            PRESET_BASE: 'mainnet'
            """);
    assertThatThrownBy(
            () -> SpecConfigLoader.loadConfig(configFile.toString(), true, true, __ -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "The specified network configuration had missing or invalid values for constants");
  }

  @Test
  public void shouldLoadMainnetFromFileUrl() throws Exception {
    final URL url = getMainnetConfigResourceAsUrl();
    final SpecConfig config = SpecConfigLoader.loadConfig(url.toString()).specConfig();
    assertAllBellatrixFieldsSet(config);
  }

  @Test
  public void shouldLoadMainnetFromFile(@TempDir final Path tempDir) throws Exception {
    try (final InputStream inputStream = getMainnetConfigAsStream()) {
      final Path file = tempDir.resolve("mainnet.yml");
      writeStreamToFile(inputStream, file);
      final SpecConfig config =
          SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()).specConfig();
      assertAllBellatrixFieldsSet(config);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"altairForkSpec.json", "mergeForkSpec.json", "bellatrixForkSpec.json"})
  void shouldLoadMainnetPreservingBackwardsCompatibilityWithRestApi(final String specFilename)
      throws Exception {
    try (final InputStream in =
        Resources.getResource(SpecConfigLoaderTest.class, specFilename).openStream()) {
      final SpecConfig config = SpecConfigLoader.loadRemoteConfig(readJsonConfig(in)).specConfig();
      assertAllAltairFieldsSet(config);
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_wrongType(@TempDir final Path tempDir)
      throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_wrongType.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Unable to load configuration for network \""
                  + file.toAbsolutePath()
                  + "\": Could not load spec config preset '300' specified in config '"
                  + file.toAbsolutePath()
                  + "'");
    }
  }

  @Test
  public void shouldHandleInvalidPresetValue_unknownPreset(@TempDir final Path tempDir)
      throws Exception {
    try (final InputStream inputStream = loadInvalidFile("invalidPreset_unknown.yaml")) {
      final Path file = tempDir.resolve("invalid.yml");
      writeStreamToFile(inputStream, file);
      assertThatThrownBy(() -> SpecConfigLoader.loadConfig(file.toAbsolutePath().toString()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "Unable to load configuration for network \""
                  + file.toAbsolutePath()
                  + "\": Could not load spec config preset 'foo' specified in config '"
                  + file.toAbsolutePath()
                  + "'")
          .hasRootCauseMessage(
              "Could not load spec config preset 'foo' specified in config '%s'",
              file.toAbsolutePath().toString());
    }
  }

  @Test
  public void shouldBeAbleToOverridePresetValues() {
    final URL configUrl = SpecConfigLoaderTest.class.getResource("standard/with-overrides.yaml");
    final SpecConfig config =
        SpecConfigLoader.loadConfig(configUrl.toString(), false, __ -> {}).specConfig();
    assertThat(config).isNotNull();
    assertThat(config.getMaxCommitteesPerSlot()).isEqualTo(12); // Mainnet preset is 64.
  }

  private void writeStreamToFile(final InputStream inputStream, final Path filePath)
      throws Exception {
    try (final OutputStream outputStream = Files.newOutputStream(filePath)) {
      IOUtils.copy(inputStream, outputStream);
    }
  }

  private InputStream getMainnetConfigAsStream() {
    return SpecConfig.class.getResourceAsStream("configs/mainnet.yaml");
  }

  private URL getMainnetConfigResourceAsUrl() {
    return SpecConfig.class.getResource("configs/mainnet.yaml");
  }

  private InputStream loadInvalidFile(final String file) {
    return getClass().getResourceAsStream("invalid/" + file);
  }

  private Map<String, Object> readJsonConfig(final InputStream source) {
    YamlConfigReader reader = new YamlConfigReader();
    return reader.readValues(source);
  }
}
