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

package tech.pegasys.teku.cli.util;

import java.nio.file.Path;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.ValidatorClientService;

public class SlashingProtectionCommandUtils {
  public static void verifySlashingProtectionPathExists(
      final SubCommandLogger subCommandLogger, final Path slashProtectionPath) {
    if (!slashProtectionPath.toFile().exists() || !slashProtectionPath.toFile().isDirectory()) {
      subCommandLogger.exit(
          1,
          "Unable to locate the path containing slashing protection data. Expected "
              + slashProtectionPath
              + " to be a directory containing slashing protection yml files.");
    }
  }

  public static Path getSlashingProtectionPath(final ValidatorClientDataOptions dataOptions) {
    final DataDirLayout dataDirLayout = DataDirLayout.createFrom(dataOptions.getDataConfig());
    return ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
  }
}
