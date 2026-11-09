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

import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SlashingProtectedIncrementalExporterTest {
  private static final Logger LOG = LogManager.getLogger();
  private final String pubkey =
      "b845089a1457f811bfc000588fbb4e713669be8ce060ea6be3c6ece09afc3794106c91ca73acda5e5457122d58723bed";

  @Test
  public void shouldExportSlashProtection(@TempDir final Path tempDir)
      throws IOException, URISyntaxException {
    final Path exportedFile = tempDir.resolve("exportedFile.json").toAbsolutePath();
    final SlashingProtectionIncrementalExporter exporter =
        new SlashingProtectionIncrementalExporter(tempDir);

    final Optional<String> error =
        exporter.readSlashProtectionFile(
            usingResourceFile("slashProtection.yml", tempDir), LOG::debug);
    assertThat(error).isEmpty();
    assertThat(Files.exists(exportedFile)).isFalse();
    exporter.saveToFile(exportedFile.toString(), LOG::debug);

    final String exportedData = exporter.finalise();
    final String expectedResult = resourceFileAsString("shouldExportSlashProtection.json");
    assertThat(exportedData).isEqualTo(expectedResult);
  }

  @Test
  void shouldCreateEmptySlashingProtectionDocument(@TempDir final Path tempDir) throws IOException {
    final SlashingProtectionIncrementalExporter exporter =
        new SlashingProtectionIncrementalExporter(tempDir);
    assertThat(exporter.finalise()).isEqualTo(resourceFileAsString("emptySlashingData.json"));
  }

  private File usingResourceFile(final String resourceFileName, final Path tempDir)
      throws URISyntaxException, IOException {
    final Path tempFile = tempDir.resolve(pubkey + ".yml").toAbsolutePath();
    Files.copy(
        new File(Resources.getResource(resourceFileName).toURI()).toPath(),
        tempFile,
        StandardCopyOption.REPLACE_EXISTING);
    return tempFile.toFile();
  }

  private String resourceFileAsString(final String resourceFileName) throws IOException {
    return Resources.toString(Resources.getResource(resourceFileName), StandardCharsets.UTF_8);
  }
}
