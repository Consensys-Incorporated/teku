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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static tech.pegasys.teku.spec.config.SpecConfigAssertions.assertAllAltairFieldsSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigReaderTest {
  private final SpecConfigReader reader = new SpecConfigReader();

  @Test
  public void read_missingConfig() {
    processFileAsInputStream(getInvalidConfigPath("missingChurnLimit"), this::readConfig);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MIN_PER_EPOCH_CHURN_LIMIT");
  }

  @Test
  public void read_missingAltairConstant() {
    processFileAsInputStream(getInvalidConfigPath("missingAltairField"), this::readConfig);

    assertThatThrownBy(
            () ->
                reader.build(
                    builder ->
                        builder.altairForkEpoch(UInt64.ZERO).bellatrixForkEpoch(UInt64.ZERO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("EPOCHS_PER_SYNC_COMMITTEE_PERIOD");
  }

  @Test
  void read_unknownConstant() {
    assertThatThrownBy(
            () -> processFileAsInputStream(getInvalidConfigPath("unknownField"), this::readConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Detected unknown spec config entries: UNKNOWN_CONSTANT");
  }

  @Test
  void read_ignoringUnknownConstant() {
    Assertions.assertThatCode(
            () -> {
              processFileAsInputStream(
                  getInvalidConfigPath("unknownField"),
                  source -> reader.readAndApply(source, true));
              assertAllAltairFieldsSet(reader.build().specConfig());
            })
        .doesNotThrowAnyException();
  }

  @Test
  public void read_emptyFile() {
    processFileAsInputStream(
        getInvalidConfigPath("empty"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Supplied spec config is empty"));
  }

  @Test
  public void read_almostEmptyFile() {
    processFileAsInputStream(getInvalidConfigPath("almostEmpty"), this::readConfig);

    try {
      reader.build();
      Assertions.fail("Should have received an exception");
    } catch (IllegalArgumentException e) {
      final String message = e.getMessage();
      assertThat(message).contains("missing or invalid values for constants");
      // this message contains a number of items separated by ", ".
      // If there's only one field, then this test will return 1 element, but it should return all
      // missing fields.
      assertThat(message.split(", ")).hasSizeGreaterThan(1);
    }
  }

  @Test
  public void read_invalidInteger_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_wrongType"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: 'string value'"));
  }

  @Test
  public void read_invalidInteger_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '2147483648'"));
  }

  @Test
  public void read_invalidInteger_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidInteger_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MAX_COMMITTEES_PER_SLOT: '-1'"));
  }

  @Test
  public void read_invalidLong_wrongType() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_wrongType"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT"));
  }

  @Test
  public void read_invalidLong_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '9223372036854775808'"));
  }

  @Test
  public void read_invalidLong_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidLong_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant VALIDATOR_REGISTRY_LIMIT: '-1099511627776'"));
  }

  @Test
  public void read_invalidUInt64_negative() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_negative"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse value for constant MIN_GENESIS_TIME: '-1'"));
  }

  @Test
  public void read_invalidUInt64_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidUInt64_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant MIN_GENESIS_TIME: '18446744073709552001'"));
  }

  @Test
  public void read_listOfBlobSchedules() {
    final Map<String, Object> data = new HashMap<>();
    data.put(
        "BLOB_SCHEDULE",
        List.of(
            Map.of("EPOCH", "1", "MAX_BLOBS_PER_BLOCK", "2"),
            Map.of("EPOCH", "3", "MAX_BLOBS_PER_BLOCK", "4")));

    assertDoesNotThrow(() -> reader.loadFromMap(data, true));
  }

  @Test
  public void read_invalidListThrowsException() {
    final Map<String, Object> data = new HashMap<>();
    data.put("BLOB_SCHEDULE", List.of("A", "b"));
    assertThatThrownBy(() -> reader.loadFromMap(data, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("BLOB_SCHEDULE");
  }

  @Test
  public void read_listOfGasLimitSchedules() {
    final Map<String, Object> data = new HashMap<>();
    data.put(
        "GAS_LIMIT_SCHEDULE",
        List.of(
            Map.of("EPOCH", "1", "GAS_LIMIT", "60000000"),
            Map.of("EPOCH", "3", "GAS_LIMIT", "45000000")));

    assertDoesNotThrow(() -> reader.loadFromMap(data, true));
  }

  @Test
  public void read_invalidGasLimitScheduleThrowsException() {
    final Map<String, Object> data = new HashMap<>();
    data.put("GAS_LIMIT_SCHEDULE", List.of(Map.of("EPOCH", "1", "MAX_BLOBS_PER_BLOCK", "2")));
    assertThatThrownBy(() -> reader.loadFromMap(data, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GAS_LIMIT_SCHEDULE");
  }

  @Test
  public void read_localConfigFile_notLoadingDefaults(@TempDir final Path tempDir)
      throws IOException {
    Files.writeString(
        tempDir.resolve("test.yaml"), "PRESET_BASE: 'mainnet'\nCONFIG_NAME: 'mainnet'", UTF_8);
    final Map<String, Object> data =
        reader.readValues(Files.newInputStream(tempDir.resolve("test.yaml")));
    reader.loadFromMap(data, true);
    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SECONDS_PER_ETH1_BLOCK");
  }

  @Test
  public void read_invalidBytes4_tooLarge() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooLarge"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102030405'"));
  }

  @Test
  public void read_invalidBytes4_tooSmall() {
    processFileAsInputStream(
        getInvalidConfigPath("invalidBytes4_tooSmall"),
        stream ->
            assertThatThrownBy(() -> readConfig(stream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                    "Failed to parse value for constant GENESIS_FORK_VERSION: '0x0102'"));
  }

  private void readConfig(final InputStream preset) throws IOException {
    reader.readAndApply(preset, false);
  }

  private static String getInvalidConfigPath(final String name) {
    return getConfigPath("invalid/" + name);
  }

  private static String getConfigPath(final String name) {
    final String path = "tech/pegasys/teku/spec/config/";
    return path + name + ".yaml";
  }

  private void processFileAsInputStream(final String fileName, final InputStreamHandler handler) {
    try (final InputStream inputStream = getFileFromResourceAsStream(fileName)) {
      handler.accept(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getFileFromResourceAsStream(final String fileName) {
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    if (inputStream == null) {
      throw new IllegalArgumentException("File not found: " + fileName);
    }

    return inputStream;
  }

  private interface InputStreamHandler {
    void accept(InputStream inputStream) throws IOException;
  }
}
