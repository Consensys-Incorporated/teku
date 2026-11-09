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

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeyGenerationOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.KeyGenerator;

@Command(
    name = "generate-keys",
    description = "[WARNING: NOT FOR PRODUCTION USE] Generate validator keys",
    hidden = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GenerateKeysCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;

  @Mixin private KeyGenerationOptions keyGenerationOptions;
  @Mixin private VerbosityOptions verbosityOptions;

  public GenerateKeysCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j uses non-daemon threads which halts the program
  }

  @VisibleForTesting
  GenerateKeysCommand(
      final Consumer<Integer> shutdownFunction,
      final KeyGenerationOptions keyGenerationOptions,
      final VerbosityOptions verbosityOptions) {
    this.shutdownFunction = shutdownFunction;
    this.keyGenerationOptions = keyGenerationOptions;
    this.verbosityOptions = verbosityOptions;
  }

  @Override
  public void run() {
    SUB_COMMAND_LOG.commandIsNotSafeForProduction();
    final KeyGenerator keyGenerator =
        keyGenerationOptions.createKeyGenerator(verbosityOptions.isVerboseOutputEnabled());
    keyGenerator.generateKeys();
    shutdownFunction.accept(0);
  }
}
