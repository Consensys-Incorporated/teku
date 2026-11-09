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

package tech.pegasys.teku.cli.subcommand.internal.validator.commands;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;

@Command(
    name = "validator-tools",
    description = "[WARNING: NOT FOR PRODUCTION USE] Tools for registering validators",
    hidden = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    subcommands = {GenerateKeysCommand.class},
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ValidatorToolsCommand implements Runnable {
  @Override
  public void run() {
    SUB_COMMAND_LOG.commandIsNotSafeForProduction();
    CommandLine.usage(this, System.out);
  }
}
