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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.apache.logging.log4j.util.Strings;
import picocli.CommandLine;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.util.SlashingProtectionCommandUtils;
import tech.pegasys.teku.data.SlashingProtectionExporter;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;

@CommandLine.Command(
    name = "export",
    description = "Export slashing protection database in minimal format.",
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ExportCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Option(
      names = {"--to"},
      paramLabel = "<FILENAME>",
      description = "The file to export the slashing protection database to.",
      required = true,
      arity = "1")
  private String toFileName = Strings.EMPTY;

  @Override
  public void run() {

    final Path slashProtectionPath =
        SlashingProtectionCommandUtils.getSlashingProtectionPath(dataOptions);
    SlashingProtectionCommandUtils.verifySlashingProtectionPathExists(
        SUB_COMMAND_LOG, slashProtectionPath);

    SlashingProtectionExporter slashingProtectionExporter =
        new SlashingProtectionExporter(slashProtectionPath);

    SUB_COMMAND_LOG.display("Reading slashing protection data from: " + slashProtectionPath);
    final Map<BLSPublicKey, String> errors =
        slashingProtectionExporter.initialise(SUB_COMMAND_LOG::display);

    if (!errors.isEmpty()) {
      errors.forEach((key, error) -> SUB_COMMAND_LOG.display("ERROR: " + error));
      SUB_COMMAND_LOG.exit(
          1, "There were errors reading from slashing protection files, cannot complete.");
    }
    try {
      SUB_COMMAND_LOG.display("Writing slashing protection data to: " + toFileName);
      slashingProtectionExporter.saveToFile(toFileName, SUB_COMMAND_LOG::display);
    } catch (IOException e) {
      SUB_COMMAND_LOG.exit(1, "Failed to export slashing protection data.", e);
    }
  }
}
