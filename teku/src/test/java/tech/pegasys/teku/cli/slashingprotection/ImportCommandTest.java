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

package tech.pegasys.teku.cli.slashingprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.cli.slashingprotection.ImportCommand.updateLocalRecords;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;

class ImportCommandTest {
  private static final String PUBKEY =
      "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";

  private final SubCommandLogger log = mock(SubCommandLogger.class);

  @Test
  void shouldSucceedWhenEveryValidatorIsImported(@TempDir final Path tempDir) throws IOException {
    final SlashingProtectionImporter importer = initialiseImporter(tempDir, "3", "4");

    assertThat(updateLocalRecords(importer, log)).isTrue();
    verify(log, never()).error(anyString());
  }

  @Test
  void shouldFailAndReportValidatorsThatWereNotImported(@TempDir final Path tempDir)
      throws IOException {
    final SlashingProtectionImporter importer = initialiseImporter(tempDir, "8", "7");

    assertThat(updateLocalRecords(importer, log)).isFalse();
    verify(log)
        .error(
            PUBKEY
                + ": Slashing protection data could not be imported: "
                + "signed attestation source epoch 8 is after its target epoch 7");
    verify(log).error("Slashing protection data was not imported for 1 validator(s).");
  }

  private static SlashingProtectionImporter initialiseImporter(
      final Path tempDir, final String sourceEpoch, final String targetEpoch) throws IOException {
    final Path importFile = tempDir.resolve("import.json");
    Files.writeString(
        importFile,
        "{\"metadata\":{\"interchange_format_version\":\"5\",\"genesis_validators_root\":"
            + "\"0x0000000000000000000000000000000000000000000000000000000000123456\"},"
            + "\"data\":[{\"pubkey\":\""
            + PUBKEY
            + "\",\"signed_blocks\":[],\"signed_attestations\":[{\"source_epoch\":\""
            + sourceEpoch
            + "\",\"target_epoch\":\""
            + targetEpoch
            + "\"}]}]}");
    final Path slashProtectionPath = Files.createDirectories(tempDir.resolve("slashprotection"));
    final SlashingProtectionImporter importer = new SlashingProtectionImporter(slashProtectionPath);
    assertThat(importer.initialise(importFile.toFile())).isEmpty();
    return importer;
  }
}
