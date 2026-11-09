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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
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
