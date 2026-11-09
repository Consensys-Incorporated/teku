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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SlashingProtectionRepairerTest {
  private static final UInt64 TWO = UInt64.valueOf(2);
  final ValidatorSigningRecord validatorSigningRecord =
      new ValidatorSigningRecord(Optional.empty(), ONE, ONE, ONE);
  private SyncDataAccessor syncDataAccessor;
  private final SubCommandLogger subCommandLogger = mock(SubCommandLogger.class);
  final Spec spec = TestSpecFactory.createMinimalPhase0();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  final List<String> keys =
      List.of(
          randomValidatorFile(),
          randomValidatorFile(),
          randomValidatorFile(),
          randomValidatorFile());
  final Map<String, Optional<ValidatorSigningRecord>> testData =
      Map.of(
          keys.get(0), Optional.empty(),
          keys.get(1), optionalSigningRecord(ONE, ONE, ONE),
          keys.get(2),
              optionalSigningRecord(
                  UInt64.valueOf(1024000), UInt64.valueOf(1234), UInt64.valueOf(2345)),
          keys.get(3),
              optionalSigningRecord(
                  UInt64.valueOf(1024000), UInt64.valueOf(12345), UInt64.valueOf(2345)));

  @Test
  void updateSigningRecord_shouldNotUpdateBetterRecords() {
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                ZERO, ZERO, Optional.of(validatorSigningRecord)))
        .isEqualTo(validatorSigningRecord);
  }

  @Test
  void updateSigningRecord_shouldNotRequireInitialRecord() {
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ONE, ONE, Optional.empty()))
        .isEqualTo(validatorSigningRecord);
  }

  @Test
  void updateSigningRecord_shouldUpdateBlockSlot() {
    final ValidatorSigningRecord expectedValue =
        new ValidatorSigningRecord(Optional.empty(), TWO, ONE, ONE);
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                TWO, ZERO, Optional.of(validatorSigningRecord)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateAttestationEpoch() {
    final ValidatorSigningRecord expectedValue =
        new ValidatorSigningRecord(Optional.empty(), ONE, TWO, TWO);
    assertThat(
            SlashingProtectionRepairer.updateSigningRecord(
                ONE, TWO, Optional.of(validatorSigningRecord)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateSourceAttestationEpoch() {
    final ValidatorSigningRecord initialValue =
        new ValidatorSigningRecord(Optional.empty(), ONE, ZERO, TWO);
    final ValidatorSigningRecord expectedValue =
        new ValidatorSigningRecord(Optional.empty(), ONE, ONE, TWO);
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ONE, ONE, Optional.of(initialValue)))
        .isEqualTo(expectedValue);
  }

  @Test
  void updateSigningRecord_shouldUpdateTargetAttestationEpoch() {
    final ValidatorSigningRecord initialValue =
        new ValidatorSigningRecord(Optional.empty(), ONE, TWO, ONE);
    final ValidatorSigningRecord expectedValue =
        new ValidatorSigningRecord(Optional.empty(), ONE, TWO, TWO);
    assertThat(SlashingProtectionRepairer.updateSigningRecord(ZERO, TWO, Optional.of(initialValue)))
        .isEqualTo(expectedValue);
  }

  @Test
  public void shouldNotUpdateFilesWithInvalidPubkeys(@TempDir final Path tempDir)
      throws IOException {
    setupPathForTest(tempDir, Map.of("a.yml", Optional.of(validatorSigningRecord)));
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, true);
    assertThat(repairer.hasUpdates()).isFalse();
    verify(subCommandLogger).display(" --- a.yml - invalid public key - ignoring file");

    repairer.updateRecords(UInt64.MAX_VALUE, UInt64.MAX_VALUE);
    verifyNoMoreInteractions(subCommandLogger);

    assertThat(fileContents(tempDir.resolve("a.yml")))
        .isEqualTo(Optional.of(validatorSigningRecord));
  }

  @Test
  public void shouldUpdateValidAndInvalidFiles(@TempDir final Path tempDir) throws IOException {
    setupPathForTest(tempDir, testData);
    SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, true);
    assertThat(repairer.hasUpdates()).isTrue();

    final UInt64 blockSlot = UInt64.valueOf(1023999);
    final UInt64 attestationEpoch = UInt64.valueOf(2344);
    repairer.updateRecords(blockSlot, attestationEpoch);

    final Optional<ValidatorSigningRecord> defaultRecord =
        Optional.of(
            new ValidatorSigningRecord(
                Optional.empty(), blockSlot, attestationEpoch, attestationEpoch));

    assertThat(fileContents(tempDir.resolve(keys.get(0)))).isEqualTo(defaultRecord);
    // all original values were lower, so the entire file should get updated
    assertThat(fileContents(tempDir.resolve(keys.get(1)))).isEqualTo(defaultRecord);
    // sourceAttestation changed, but other values were higher
    assertThat(fileContents(tempDir.resolve(keys.get(2))))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1024000), attestationEpoch, UInt64.valueOf(2345)));
    // all original values were better
    assertThat(fileContents(tempDir.resolve(keys.get(3)))).isEqualTo(testData.get(keys.get(3)));
  }

  @Test
  public void shouldUpdateInvalidFiles(@TempDir final Path tempDir) throws IOException {
    setupPathForTest(tempDir, testData);
    SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);
    assertThat(repairer.hasUpdates()).isTrue();

    final UInt64 blockSlot = UInt64.valueOf(1023999);
    final UInt64 attestationEpoch = UInt64.valueOf(2344);
    repairer.updateRecords(blockSlot, attestationEpoch);

    final Optional<ValidatorSigningRecord> defaultRecord =
        Optional.of(
            new ValidatorSigningRecord(
                Optional.empty(), blockSlot, attestationEpoch, attestationEpoch));

    assertThat(fileContents(tempDir.resolve(keys.get(0)))).isEqualTo(defaultRecord);
    assertThat(fileContents(tempDir.resolve(keys.get(1)))).isEqualTo(testData.get(keys.get(1)));
    assertThat(fileContents(tempDir.resolve(keys.get(2)))).isEqualTo(testData.get(keys.get(2)));
    assertThat(fileContents(tempDir.resolve(keys.get(3)))).isEqualTo(testData.get(keys.get(3)));
  }

  @Test
  public void shouldRewriteNonEmptyCorruptFile(@TempDir final Path tempDir) throws IOException {
    final String key = randomValidatorFile();
    writeRawFile(tempDir, key, "garbage: [");
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);
    assertThat(repairer.hasUpdates()).isTrue();
    verify(subCommandLogger)
        .display(pubkeyOf(key) + ": Incomplete or invalid slashing protection data");

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    assertThat(fileContents(tempDir.resolve(key)))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));
  }

  @Test
  public void shouldTreatRecordWithMissingFieldAsInvalid(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    writeRawFile(
        tempDir, key, "---\nlastSignedBlockSlot: 5000\nlastSignedAttestationSourceEpoch: 600\n");
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);
    assertThat(repairer.hasUpdates()).isTrue();

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    // the partial record's higher slot is not kept, the file is replaced with the supplied values
    assertThat(fileContents(tempDir.resolve(key)))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));
  }

  @Test
  public void shouldReportEmptyFileAsInvalidRatherThanEmpty(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    writeRawFile(tempDir, key, "");
    SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);

    verify(subCommandLogger)
        .display(pubkeyOf(key) + ": Incomplete or invalid slashing protection data");
    verify(subCommandLogger, never()).display(endsWith(": Empty slashing protection record"));
  }

  @Test
  public void shouldDropGenesisValidatorsRootWhenRewritingInvalidFile(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    writeRawFile(
        tempDir,
        key,
        "---\ngenesisValidatorsRoot: \""
            + Bytes32.fromHexStringLenient("0x1234").toHexString()
            + "\"\nlastSignedBlockSlot: 5000\n");
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    assertThat(fileContents(tempDir.resolve(key)).orElseThrow().genesisValidatorsRoot()).isEmpty();
  }

  @Test
  public void shouldPreserveGenesisValidatorsRootWhenUpdatingValidRecords(
      @TempDir final Path tempDir) throws IOException {
    final Optional<Bytes32> genesisValidatorsRoot = Optional.of(dataStructureUtil.randomBytes32());
    final String lowKey = randomValidatorFile();
    final String highKey = randomValidatorFile();
    final ValidatorSigningRecord highRecord =
        new ValidatorSigningRecord(
            genesisValidatorsRoot, UInt64.valueOf(5000), UInt64.valueOf(600), UInt64.valueOf(600));
    setupPathForTest(
        tempDir,
        Map.of(
            lowKey,
            Optional.of(new ValidatorSigningRecord(genesisValidatorsRoot, ONE, ONE, ONE)),
            highKey,
            Optional.of(highRecord)));

    SlashingProtectionRepairer.create(subCommandLogger, tempDir, true)
        .updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    assertThat(fileContents(tempDir.resolve(lowKey)))
        .contains(
            new ValidatorSigningRecord(
                genesisValidatorsRoot,
                UInt64.valueOf(1234),
                UInt64.valueOf(154),
                UInt64.valueOf(154)));
    assertThat(fileContents(tempDir.resolve(highKey))).contains(highRecord);
    verify(subCommandLogger, never()).display(pubkeyOf(highKey) + ": updating");
  }

  @Test
  public void shouldFindRecordsFromOtherNetworks(@TempDir final Path tempDir) throws IOException {
    final Bytes32 networkRoot = dataStructureUtil.randomBytes32();
    final Bytes32 otherRoot = dataStructureUtil.randomBytes32();
    final String matchingKey = randomValidatorFile();
    final String otherNetworkKey = randomValidatorFile();
    final String noRootKey = randomValidatorFile();
    final String corruptKey = randomValidatorFile();
    setupPathForTest(
        tempDir,
        Map.of(
            matchingKey,
            Optional.of(new ValidatorSigningRecord(Optional.of(networkRoot), ONE, ONE, ONE)),
            otherNetworkKey,
            Optional.of(new ValidatorSigningRecord(Optional.of(otherRoot), ONE, ONE, ONE)),
            noRootKey,
            optionalSigningRecord(ONE, ONE, ONE)));
    writeRawFile(tempDir, corruptKey, "garbage: [");

    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);

    assertThat(repairer.getPubkeysWithDifferentGenesisValidatorsRoot(networkRoot))
        .containsExactly(pubkeyOf(otherNetworkKey));
    assertThat(repairer.getPubkeysWithDifferentGenesisValidatorsRoot(otherRoot))
        .containsExactly(pubkeyOf(matchingKey));
    assertThat(repairer.hasRecordsWithGenesisValidatorsRoot()).isTrue();
  }

  @Test
  public void shouldNotFindOtherNetworksWhenRecordsHaveNoGenesisValidatorsRoot(
      @TempDir final Path tempDir) throws IOException {
    setupPathForTest(tempDir, Map.of(randomValidatorFile(), optionalSigningRecord(ONE, ONE, ONE)));
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, true);

    assertThat(
            repairer.getPubkeysWithDifferentGenesisValidatorsRoot(
                dataStructureUtil.randomBytes32()))
        .isEmpty();
    assertThat(repairer.hasRecordsWithGenesisValidatorsRoot()).isFalse();
  }

  @Test
  public void shouldNotCreateFilesForKeysWithoutRecords(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    setupPathForTest(tempDir, Map.of(key, optionalSigningRecord(ONE, ONE, ONE)));
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, true);

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    // the repairer only knows about files that already exist; it never loads validator keys
    assertThat(listFileNames(tempDir)).containsExactly(key);
  }

  @Test
  public void shouldHaveNoUpdatesForEmptyDirectory(@TempDir final Path tempDir) {
    assertThat(SlashingProtectionRepairer.create(subCommandLogger, tempDir, false).hasUpdates())
        .isFalse();
    assertThat(SlashingProtectionRepairer.create(subCommandLogger, tempDir, true).hasUpdates())
        .isFalse();
  }

  @Test
  public void shouldHaveNoUpdatesWhenAllRecordsValidAndUpdateAllDisabled(
      @TempDir final Path tempDir) throws IOException {
    setupPathForTest(tempDir, Map.of(randomValidatorFile(), optionalSigningRecord(ONE, ONE, ONE)));
    assertThat(SlashingProtectionRepairer.create(subCommandLogger, tempDir, false).hasUpdates())
        .isFalse();
  }

  @Test
  public void shouldNotLowerRecordsWhenRepairedAgainWithLowerSlot(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    setupPathForTest(tempDir, Map.of(key, optionalSigningRecord(ONE, ONE, ONE)));
    SlashingProtectionRepairer.create(subCommandLogger, tempDir, true)
        .updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));
    final Optional<ValidatorSigningRecord> afterFirstRun = fileContents(tempDir.resolve(key));
    assertThat(afterFirstRun)
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));

    reset(subCommandLogger);
    SlashingProtectionRepairer.create(subCommandLogger, tempDir, true)
        .updateRecords(UInt64.valueOf(123), UInt64.valueOf(15));

    assertThat(fileContents(tempDir.resolve(key))).isEqualTo(afterFirstRun);
    verify(subCommandLogger, never()).display(endsWith(": updating"));
  }

  @Test
  public void shouldRaiseNeverSignedAttestationEpochs(@TempDir final Path tempDir)
      throws IOException {
    final String key = randomValidatorFile();
    setupPathForTest(
        tempDir, Map.of(key, Optional.of(ValidatorSigningRecord.emptySigningRecord(null))));

    // a never-signed record is valid, so it is left alone unless update-all is enabled
    assertThat(SlashingProtectionRepairer.create(subCommandLogger, tempDir, false).hasUpdates())
        .isFalse();

    SlashingProtectionRepairer.create(subCommandLogger, tempDir, true)
        .updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    assertThat(fileContents(tempDir.resolve(key)))
        .isEqualTo(
            optionalSigningRecord(UInt64.valueOf(1234), UInt64.valueOf(154), UInt64.valueOf(154)));
  }

  @Test
  public void shouldOnlyLogUpdatingForRecordsThatChange(@TempDir final Path tempDir)
      throws IOException {
    final String lowKey = randomValidatorFile();
    final String highKey = randomValidatorFile();
    setupPathForTest(
        tempDir,
        Map.of(
            lowKey, optionalSigningRecord(ONE, ONE, ONE),
            highKey,
                optionalSigningRecord(
                    UInt64.valueOf(5000), UInt64.valueOf(600), UInt64.valueOf(600))));
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, true);
    verify(subCommandLogger).display(pubkeyOf(lowKey) + ": looks valid");
    verify(subCommandLogger).display(pubkeyOf(highKey) + ": looks valid");

    repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    verify(subCommandLogger).display(pubkeyOf(lowKey) + ": updating");
    verify(subCommandLogger, never()).display(pubkeyOf(highKey) + ": updating");
  }

  @Test
  public void shouldReportWriteFailures(@TempDir final Path tempDir) throws IOException {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    final String key = randomValidatorFile();
    writeRawFile(tempDir, key, "garbage: [");
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(subCommandLogger, tempDir, false);

    final Set<PosixFilePermission> originalDirPermissions = Files.getPosixFilePermissions(tempDir);
    final Set<PosixFilePermission> readOnly = PosixFilePermissions.fromString("r-xr-xr-x");
    Files.setPosixFilePermissions(tempDir.resolve(key), readOnly);
    Files.setPosixFilePermissions(tempDir, readOnly);
    try {
      repairer.updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));
    } finally {
      Files.setPosixFilePermissions(tempDir, originalDirPermissions);
    }

    verify(subCommandLogger).display("The following errors were encountered:");
    verify(subCommandLogger).display(startsWith(pubkeyOf(key) + ": update failed, "));
    assertThat(Files.readString(tempDir.resolve(key), StandardCharsets.UTF_8))
        .isEqualTo("garbage: [");
  }

  @Test
  public void shouldNotReportErrorsWhenWritesSucceed(@TempDir final Path tempDir)
      throws IOException {
    writeRawFile(tempDir, randomValidatorFile(), "garbage: [");
    SlashingProtectionRepairer.create(subCommandLogger, tempDir, false)
        .updateRecords(UInt64.valueOf(1234), UInt64.valueOf(154));

    verify(subCommandLogger, never()).display("The following errors were encountered:");
    verify(subCommandLogger, never()).display(contains(": update failed"));
  }

  private void writeRawFile(final Path dir, final String fileName, final String content)
      throws IOException {
    Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
  }

  private String pubkeyOf(final String fileName) {
    return fileName.substring(0, fileName.length() - ".yml".length());
  }

  private List<String> listFileNames(final Path dir) {
    return Arrays.stream(dir.toFile().listFiles()).map(File::getName).toList();
  }

  private Optional<ValidatorSigningRecord> fileContents(final Path file) throws IOException {
    syncDataAccessor = SyncDataAccessor.create(file.getParent());
    return syncDataAccessor.read(file).map(ValidatorSigningRecord::fromBytes);
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
    syncDataAccessor = SyncDataAccessor.create(slashingProtectionPath);
    for (Map.Entry<String, Optional<ValidatorSigningRecord>> entry : records.entrySet()) {
      Path outputFile = slashingProtectionPath.resolve(entry.getKey());
      final Optional<ValidatorSigningRecord> maybeSigningRecord = entry.getValue();
      final Bytes data =
          maybeSigningRecord.isPresent()
              ? maybeSigningRecord.get().toBytes()
              : Bytes.fromHexString("");
      syncDataAccessor.syncedWrite(outputFile, data);
    }
  }
}
