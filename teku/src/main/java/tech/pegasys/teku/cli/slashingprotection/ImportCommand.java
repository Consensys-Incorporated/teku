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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.logging.log4j.util.Strings;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.util.SlashingProtectionCommandUtils;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;

@CommandLine.Command(
    name = "import",
    description =
        "Import slashing protection database. Supports minimal or complete interchange format.",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ImportCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Option(
      names = {"--from"},
      paramLabel = "<FILENAME>",
      description = "The file to import the slashing protection database from.",
      required = true,
      arity = "1")
  private String fromFileName = Strings.EMPTY;

  @Override
  public void run() {
    final Path slashProtectionPath =
        SlashingProtectionCommandUtils.getSlashingProtectionPath(dataOptions);
    File importFile = new File(fromFileName);
    verifyImportFileExists(importFile);
    prepareOutputPath(slashProtectionPath.toFile());

    SlashingProtectionImporter importer = new SlashingProtectionImporter(slashProtectionPath);

    try {
      SUB_COMMAND_LOG.display("Reading slashing protection data from: " + importFile);
      final Optional<String> errorCondition = importer.initialise(importFile);
      errorCondition.ifPresent(s -> SUB_COMMAND_LOG.exit(1, s));
    } catch (IOException e) {
      String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
      SUB_COMMAND_LOG.exit(
          1, String.format("Failed to read from import file: %s. %s", importFile, cause));
    }

    SUB_COMMAND_LOG.display("Writing slashing protection data to: " + slashProtectionPath);
    importer.updateLocalRecords(SUB_COMMAND_LOG::display);
  }

  private void verifyImportFileExists(final File importFile) {
    if (!importFile.exists() || !importFile.isFile() || !importFile.canRead()) {
      SUB_COMMAND_LOG.exit(1, "Cannot open " + importFile.toString() + " for reading.");
    }
  }

  private void prepareOutputPath(final File outputPath) {
    if (!outputPath.exists() && !outputPath.mkdirs()) {
      SUB_COMMAND_LOG.exit(
          1, "Failed to create path to store slashing protection data " + outputPath);
    }
    if (!outputPath.isDirectory() || !outputPath.canWrite()) {
      SUB_COMMAND_LOG.exit(1, "Path " + outputPath + " is not a directory or can't be written to.");
    }
  }
}
