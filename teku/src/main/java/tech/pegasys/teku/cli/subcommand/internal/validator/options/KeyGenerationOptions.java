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

package tech.pegasys.teku.cli.subcommand.internal.validator.options;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.util.function.Function;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.ConsoleAdapter;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.KeyGenerator;

public class KeyGenerationOptions {

  @Spec private CommandSpec spec;

  @Option(
      names = {"--number-of-validators"},
      paramLabel = "<NUMBER>",
      description = "The number of validators to create keys for and register",
      converter = PositiveIntegerTypeConverter.class,
      defaultValue = "1")
  private int validatorCount = 1;

  @Option(
      names = {"--keys-output-path"},
      paramLabel = "<FILE|DIR>",
      description =
          "Path to output directory for encrypted keystore files. If not set keystores will be created in current directory")
  private String outputPath;

  @ArgGroup(heading = "Non-interactive password options for validator keystores:%n")
  private ValidatorPasswordOptions validatorPasswordOptions;

  @ArgGroup(heading = "Non-interactive password options for withdrawal keystores:%n")
  private WithdrawalPasswordOptions withdrawalPasswordOptions;

  private final Function<String, String> envSupplier;
  private final ConsoleAdapter consoleAdapter;

  public KeyGenerationOptions() {
    this.envSupplier = System::getenv;
    this.consoleAdapter = new ConsoleAdapter();
  }

  public KeyGenerationOptions(
      final CommandSpec spec,
      final Function<String, String> envSupplier,
      final ConsoleAdapter consoleAdapter) {
    this.spec = spec;
    this.envSupplier = envSupplier;
    this.consoleAdapter = consoleAdapter;
  }

  public KeyGenerator createKeyGenerator(final boolean verboseOutputEnabled) {
    return new KeyGenerator(
        validatorCount,
        outputPath,
        validatorPasswordOptions,
        withdrawalPasswordOptions,
        consoleAdapter,
        spec,
        envSupplier,
        verboseOutputEnabled ? SUB_COMMAND_LOG::display : s -> {});
  }

  public int getValidatorCount() {
    return validatorCount;
  }

  private static class PositiveIntegerTypeConverter implements ITypeConverter<Integer> {
    @Override
    public Integer convert(final String value) throws TypeConversionException {
      try {
        final int parsedValue = Integer.parseInt(value);
        if (parsedValue <= 0) {
          throw new TypeConversionException("Must be a positive number");
        }
        return parsedValue;
      } catch (final NumberFormatException e) {
        throw new TypeConversionException(
            "Invalid format: must be a numeric value but was '" + value + "'");
      }
    }
  }
}
