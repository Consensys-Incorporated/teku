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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/** Validator keys that have no slashing protection record file. */
class SlashingProtectionRepairerMissingRecordsTest {
  private final SubCommandLogger subCommandLogger = mock(SubCommandLogger.class);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void shouldCreateRecordsForKeysWithoutFilesWhenUpdateAllEnabled(
      @TempDir final Path tempDir) throws IOException {
    final String existingKey = randomValidatorFile();
    final Optional<ValidatorSigningRecord> existingRecord =
        optionalSigningRecord(UInt64.valueOf(5000), UInt64.valueOf(600), UInt64.valueOf(600));
    setupPathForTest(tempDir, Map.of(existingKey, existingRecord));
    final BLSPublicKey missingKey1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey missingKey2 = dataStructureUtil.randomPublicKey();

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(
            subCommandLogger,
            tempDir,
            true,
            List.of(
                BLSPublicKey.fromHexString(pubkeyOf(existingKey)),
                missingKey1,
                missingKey2,
                missingKey1));
    assertThat(repairer.hasUpdates()).isTrue();
    assertThat(repairer.getRecordsToCreateCount()).isEqualTo(2);
    verify(subCommandLogger)
        .display(fileNameOf(missingKey1, "") + ": no slashing protection record");
    verify(subCommandLogger)
        .display(fileNameOf(missingKey2, "") + ": no slashing protection record");
    verify(subCommandLogger, never()).display(contains("--update-all-enabled would create"));
    verify(subCommandLogger)
        .display(
            "2 validator keys have no slashing protection record, so the validator client will"
                + " treat them as never having signed. --update-all-enabled will create the"
                + " missing records.");

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    final Optional<ValidatorSigningRecord> repairedRecord =
        optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154));
    assertThat(fileContents(tempDir.resolve(fileNameOf(missingKey1, ".yml"))))
        .isEqualTo(repairedRecord);
    assertThat(fileContents(tempDir.resolve(fileNameOf(missingKey2, ".yml"))))
        .isEqualTo(repairedRecord);
    assertThat(fileContents(tempDir.resolve(existingKey))).isEqualTo(existingRecord);
    verify(subCommandLogger).display(fileNameOf(missingKey1, "") + ": updating");
    assertThat(
            fileContents(tempDir.resolve(fileNameOf(missingKey1, ".yml")))
                .orElseThrow()
                .genesisValidatorsRoot())
        .isEmpty();
  }

  @Test
  void describeRecordsToCreate_shouldStateCount() {
    assertThat(SlashingProtectionRepairer.describeRecordsToCreate(1))
        .isEqualTo(
            "1 new slashing protection record will be created for validator keys that don't have"
                + " one.");
    assertThat(SlashingProtectionRepairer.describeRecordsToCreate(3))
        .isEqualTo(
            "3 new slashing protection records will be created for validator keys that don't"
                + " have one.");
  }

  @Test
  public void shouldOnlyReportKeysWithoutFilesWhenUpdateAllDisabled(@TempDir final Path tempDir)
      throws IOException {
    final String corruptKey = randomValidatorFile();
    setupPathForTest(tempDir, Map.of(corruptKey, Optional.empty()));
    final BLSPublicKey missingKey = dataStructureUtil.randomPublicKey();

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(
            subCommandLogger,
            tempDir,
            false,
            List.of(BLSPublicKey.fromHexString(pubkeyOf(corruptKey)), missingKey));
    assertThat(repairer.hasUpdates()).isTrue();
    assertThat(repairer.getRecordsToCreateCount()).isZero();
    verify(subCommandLogger)
        .display(fileNameOf(missingKey, "") + ": no slashing protection record");
    verify(subCommandLogger)
        .display(
            "1 validator key has no slashing protection record, so the validator client will"
                + " treat it as never having signed. --update-all-enabled would create the"
                + " missing record.");

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    // the corrupt file is still repaired, but no file is created for the missing key
    assertThat(fileContents(tempDir.resolve(corruptKey)))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));
    assertThat(tempDir.resolve(fileNameOf(missingKey, ".yml"))).doesNotExist();
  }

  @Test
  public void shouldNotHaveUpdatesForKeysWithoutFilesWhenUpdateAllDisabled(
      @TempDir final Path tempDir) {
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(
            subCommandLogger,
            tempDir,
            false,
            List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey()));

    assertThat(repairer.hasUpdates()).isFalse();
    assertThat(repairer.getRecordsToCreateCount()).isZero();
    verify(subCommandLogger).display(startsWith("2 validator keys have no slashing protection"));
  }

  @Test
  public void shouldNotTreatKeysWithInvalidFilesAsMissing(@TempDir final Path tempDir)
      throws IOException {
    final String corruptKey = randomValidatorFile();
    setupPathForTest(tempDir, Map.of(corruptKey, Optional.empty()));

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(
            subCommandLogger,
            tempDir,
            true,
            List.of(BLSPublicKey.fromHexString(pubkeyOf(corruptKey))));

    assertThat(repairer.getRecordsToCreateCount()).isZero();
    verify(subCommandLogger, never()).display(endsWith(": no slashing protection record"));
  }

  @Test
  public void shouldCreateRecordsWhenSlashingProtectionDirectoryIsMissing(
      @TempDir final Path tempDir) throws IOException {
    final Path slashProtectionPath = tempDir.resolve("slashprotection");
    final BLSPublicKey missingKey = dataStructureUtil.randomPublicKey();

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(
            subCommandLogger, slashProtectionPath, true, List.of(missingKey));
    assertThat(repairer.hasUpdates()).isTrue();

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    assertThat(fileContents(slashProtectionPath.resolve(fileNameOf(missingKey, ".yml"))))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));
  }

  private String pubkeyOf(final String fileName) {
    return fileName.substring(0, fileName.length() - ".yml".length());
  }

  private String fileNameOf(final BLSPublicKey publicKey, final String suffix) {
    return publicKey.toBytesCompressed().toUnprefixedHexString() + suffix;
  }

  private Optional<ValidatorSigningRecord> fileContents(final Path file) throws IOException {
    return SyncDataAccessor.create(file.getParent())
        .read(file)
        .map(ValidatorSigningRecord::fromBytes);
  }

  private Optional<ValidatorSigningRecord> optionalSigningRecord(
      final UInt64 blockSlot, final UInt64 sourceEpoch, final UInt64 targetEpoch) {
    return Optional.of(
        new ValidatorSigningRecord(Optional.empty(), blockSlot, sourceEpoch, targetEpoch));
  }

  private String randomValidatorFile() {
    return dataStructureUtil.randomPublicKey().toBytesCompressed().toUnprefixedHexString() + ".yml";
  }

  private void setupPathForTest(
      final Path slashingProtectionPath,
      final Map<String, Optional<ValidatorSigningRecord>> records)
      throws IOException {
    final SyncDataAccessor syncDataAccessor = SyncDataAccessor.create(slashingProtectionPath);
    for (Map.Entry<String, Optional<ValidatorSigningRecord>> entry : records.entrySet()) {
      final Bytes data = entry.getValue().map(ValidatorSigningRecord::toBytes).orElse(Bytes.EMPTY);
      syncDataAccessor.syncedWrite(slashingProtectionPath.resolve(entry.getKey()), data);
    }
  }
}
