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

package tech.pegasys.teku.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.slashinginterchange.Metadata;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionImporterTest {
  private static final Logger LOG = LogManager.getLogger();
  private final String pubkey =
      "b845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed";
  private final BLSPublicKey publicKey =
      BLSPublicKey.fromBytesCompressed(Bytes48.wrap(Bytes.fromHexString(pubkey)));

  @Test
  public void shouldFailWithParseError(@TempDir final Path tempDir)
      throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("minimal_invalidKey.json", tempDir);
    assertThat(errorString).startsWith("Json does not appear valid.");
  }

  @Test
  public void shouldFailWithInvalidJson(@TempDir final Path tempDir)
      throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("invalid_json.json", tempDir);
    assertThat(errorString).startsWith("Json does not appear valid");
  }

  @Test
  public void shouldFailWithVersionCheckFailure(@TempDir final Path tempDir)
      throws URISyntaxException, IOException {
    final String errorString = loadAndGetErrorText("oldMetadata.json", tempDir);
    assertThat(errorString)
        .contains("Required version is " + Metadata.INTERCHANGE_VERSION.toString());
  }

  @Test
  public void shouldFailIfMetadataNotPresent(@TempDir final Path tempDir)
      throws IOException, URISyntaxException {
    final String errorString = loadAndGetErrorText("signedBlock.json", tempDir);
    assertThat(errorString).contains("required fields: (metadata");
  }

  @Test
  public void shouldImportSingleRecord(@TempDir final Path tempDir)
      throws URISyntaxException, IOException {
    final File ruleFile = usingResourceFile("slashProtection.yml", tempDir);
    final SlashingProtectionImporter importer = new SlashingProtectionImporter(tempDir);
    importer.initialise(ruleFile);
    final Optional<String> maybeError = importer.updateSigningRecord(publicKey, (__) -> {});
    assertThat(maybeError).isEmpty();
    assertThat(tempDir.resolve(pubkey + ".yml").toFile()).exists();
  }

  @Test
  public void shouldExportAndImportFile(@TempDir final Path tempDir)
      throws IOException, URISyntaxException {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();

    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    final File ruleFile = usingResourceFile("slashProtection.yml", tempDir);
    final Optional<String> exportError = exporter.readSlashProtectionFile(ruleFile, LOG::debug);
    final String originalFileContent = Files.readString(ruleFile.toPath());
    assertThat(exportError).isEmpty();

    assertThat(Files.exists(ruleFile.toPath())).isTrue();
    assertThat(Files.exists(exportedFile)).isFalse();
    exporter.saveToFile(exportedFile.toString(), LOG::debug);
    ruleFile.delete();
    assertThat(Files.exists(exportedFile)).isTrue();
    assertThat(Files.exists(ruleFile.toPath())).isFalse();

    SlashingProtectionImporter importer = new SlashingProtectionImporter(tempDir);
    importer.initialise(new File(exportedFile.toString()));
    final Map<BLSPublicKey, String> errors = importer.updateLocalRecords((__) -> {});
    assertThat(errors).isEmpty();
    assertThat(Files.exists(ruleFile.toPath())).isTrue();

    assertThat(originalFileContent).isEqualTo(Files.readString(ruleFile.toPath()));
  }

  @Test
  void shouldImportFileOverRepairedRecords(@TempDir final Path tempDir) throws Exception {
    final SubCommandLogger logger = mock(SubCommandLogger.class);
    final Path initialRecords = tempDir.resolve("initial");
    final Path repairedRecords = tempDir.resolve("repaired");
    assertThat(initialRecords.toFile().mkdirs()).isTrue();
    assertThat(repairedRecords.toFile().mkdirs()).isTrue();

    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final File initialRuleFile =
        usingResourceFile("slashProtectionWithGenesisRoot.yml", initialRecords);
    final File repairedRuleFile =
        usingResourceFile("slashProtectionWithGenesisRoot.yml", repairedRecords);

    final SlashingProtectionExporter exporter = new SlashingProtectionExporter(tempDir);
    exporter.readSlashProtectionFile(repairedRuleFile, LOG::debug);
    final String originalFileContent = Files.readString(initialRuleFile.toPath());

    assertThat(exportedFile).doesNotExist();
    exporter.saveToFile(exportedFile.toString(), LOG::debug);
    assertThat(exportedFile).exists();

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(logger, repairedRecords, true);
    final UInt64 repairedSlot = UInt64.valueOf(1566);
    final UInt64 repairedEpoch = UInt64.valueOf(7668);
    repairer.updateRecords(repairedSlot, repairedEpoch);
    verify(logger, never()).error(any());
    verify(logger, never()).error(any(), any());
    assertThat(Files.readString(repairedRuleFile.toPath())).isNotEqualTo(originalFileContent);

    SlashingProtectionImporter importer = new SlashingProtectionImporter(repairedRecords);
    importer.initialise(exportedFile.toFile());
    final Map<BLSPublicKey, String> errors = importer.updateLocalRecords((__) -> {});

    assertThat(errors).isEmpty();
    // Should wind up with a file that contains the slot and epochs from the repair, combined with
    // the genesis root from the initial file
    final ValidatorSigningRecord initialRecord = loadSigningRecord(initialRuleFile);
    final ValidatorSigningRecord importedRecord = loadSigningRecord(repairedRuleFile);
    assertThat(importedRecord)
        .isEqualTo(
            new ValidatorSigningRecord(
                initialRecord.genesisValidatorsRoot(), repairedSlot, repairedEpoch, repairedEpoch));
  }

  @Test
  void shouldFailImportingIfValidatorExistingRecordHasDifferentGenesisValidatorsRoot(
      @TempDir final Path tempDir) throws URISyntaxException, IOException {
    final SlashingProtectionImporter importer = new SlashingProtectionImporter(tempDir);

    final File slashProtection = getResourceFile("format2_minimal.json");

    importer.initialise(slashProtection);

    Map<BLSPublicKey, String> errors = importer.updateLocalRecords(__ -> {});

    assertThat(errors).isEmpty();

    final File slashProtectionWithDifferentGvr =
        getResourceFile("format2_minimal_different_genesis_validators_root.json");

    importer.initialise(slashProtectionWithDifferentGvr);

    errors = importer.updateLocalRecords(__ -> {});

    assertThat(errors)
        .hasSize(1)
        .containsEntry(publicKey, "Genesis validators root did not match what was expected.");
  }

  private ValidatorSigningRecord loadSigningRecord(final File repairedRuleFile) throws IOException {
    return ValidatorSigningRecord.fromBytes(
        Bytes.wrap(Files.readAllBytes(repairedRuleFile.toPath())));
  }

  private String loadAndGetErrorText(final String resourceFile, final Path tempDir)
      throws URISyntaxException, IOException {
    SlashingProtectionImporter importer = new SlashingProtectionImporter(tempDir);

    final Optional<String> errorString =
        importer.initialise(new File(Resources.getResource(resourceFile).toURI()));

    return errorString.orElse("");
  }

  private File usingResourceFile(final String resourceFileName, final Path tempDir)
      throws URISyntaxException, IOException {
    final Path tempFile = tempDir.resolve(pubkey + ".yml").toAbsolutePath();
    Files.copy(
        getResourceFile(resourceFileName).toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
    return tempFile.toFile();
  }

  private File getResourceFile(final String resourceFileName) throws URISyntaxException {
    return new File(Resources.getResource(resourceFileName).toURI());
  }
}
