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

package tech.pegasys.teku.reference.phase0.slashing_protection_interchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtector;

public class SlashingProtectionInterchangeTestExecutor implements TestExecutor {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final TestData testData =
        TestDataUtils.loadJson(
            testDefinition, testDefinition.getTestName(), TestData.getJsonTypeDefinition());

    // our implementation fails when importing one of the keys in an interchange, which is already
    // in our slashprotection directory with a different genesis validators root. However, the test
    // does not import any keys. This case is covered by
    // SlashingProtectionImporterTest#shouldFailImportingIfValidatorExistingRecordHasDifferentGenesisValidatorsRoot()
    if (testData.name().startsWith("wrong_genesis_validators_root")) {
      LOG.info("Skipping {}", testData.name());
      return;
    }

    LOG.info("Running {}", testData.name());

    final Path slashingProtectionPath = Files.createTempDirectory("slashprotection");
    try {
      runTest(testData, slashingProtectionPath);
    } finally {
      deleteDirectory(slashingProtectionPath);
    }
  }

  private void runTest(final TestData testData, final Path slashingProtectionPath) {
    final SlashingProtectionImporter importer =
        new SlashingProtectionImporter(slashingProtectionPath);
    final LocalSlashingProtector slashingProtector =
        new LocalSlashingProtector(
            SyncDataAccessor.create(slashingProtectionPath), slashingProtectionPath);
    testData.steps().forEach(step -> runStep(step, importer, slashingProtector));
  }

  private void runStep(
      final Step step,
      final SlashingProtectionImporter importer,
      final LocalSlashingProtector slashingProtector) {
    final Map<BLSPublicKey, String> importErrors = importInterchange(importer, step.interchange());
    if (step.shouldSucceed()) {
      assertThat(importErrors).isEmpty();
    } else {
      assertThat(importErrors).isNotEmpty();
    }
    final Bytes32 genesisValidatorsRoot =
        step.interchange().metadata().genesisValidatorsRoot().orElse(null);
    step.blocks()
        .forEach(
            block ->
                assertThat(
                        slashingProtector.maySignBlock(
                            block.pubkey(), genesisValidatorsRoot, block.slot()))
                    .isCompletedWithValue(block.shouldSucceed()));
    step.attestations()
        .forEach(
            attestation ->
                assertThat(
                        slashingProtector.maySignAttestation(
                            attestation.pubkey(),
                            genesisValidatorsRoot,
                            attestation.sourceEpoch(),
                            attestation.targetEpoch()))
                    .isCompletedWithValue(attestation.shouldSucceed()));
  }

  private Map<BLSPublicKey, String> importInterchange(
      final SlashingProtectionImporter importer,
      final SlashingProtectionInterchangeFormat interchange) {
    try {
      final Path importFile = Files.createTempFile("import", ".json");
      final String data =
          JsonUtil.serialize(
              interchange, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());
      Files.writeString(importFile, data);
      final Optional<String> initialiseError = importer.initialise(importFile.toFile());
      assertThat(initialiseError).isEmpty();
      // cleanup
      Files.delete(importFile);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return importer.updateLocalRecords(status -> LOG.info("Import status: " + status));
  }

  private void deleteDirectory(final Path dir) {
    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
      for (Path file : files) {
        if (Files.isRegularFile(file)) {
          Files.delete(file);
        }
      }
      Files.delete(dir);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
