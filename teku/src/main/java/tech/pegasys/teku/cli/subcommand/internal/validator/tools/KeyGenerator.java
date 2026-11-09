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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static tech.pegasys.teku.cli.subcommand.internal.validator.options.KeystorePasswordOptions.readFromEnvironmentVariable;
import static tech.pegasys.teku.cli.subcommand.internal.validator.options.KeystorePasswordOptions.readFromFile;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeystorePasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.WithdrawalPasswordOptions;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class KeyGenerator {
  private static final String VALIDATOR_PASSWORD_PROMPT = "Validator Keystore";
  private static final String WITHDRAWAL_PASSWORD_PROMPT = "Withdrawal Keystore";
  private final int validatorCount;
  private final String outputPath;
  private final ValidatorPasswordOptions validatorPasswordOptions;
  private final WithdrawalPasswordOptions withdrawalPasswordOptions;
  private final SecureRandom srng;
  private final ConsoleAdapter consoleAdapter;
  private final CommandSpec commandSpec;
  private final Function<String, String> envSupplier;
  private final Consumer<String> commandOutput;

  public KeyGenerator(
      final int validatorCount,
      final String outputPath,
      final ValidatorPasswordOptions validatorPasswordOptions,
      final WithdrawalPasswordOptions withdrawalPasswordOptions,
      final ConsoleAdapter consoleAdapter,
      final CommandSpec commandSpec,
      final Function<String, String> envSupplier,
      final Consumer<String> commandOutput) {
    if (validatorCount < 0) {
      throw new InvalidConfigurationException(
          String.format("Invalid validatorCount: %d", validatorCount));
    }
    this.validatorCount = validatorCount;
    this.outputPath = outputPath;
    this.validatorPasswordOptions = validatorPasswordOptions;
    this.withdrawalPasswordOptions = withdrawalPasswordOptions;
    this.consoleAdapter = consoleAdapter;
    this.commandSpec = commandSpec;
    this.envSupplier = envSupplier;
    this.commandOutput = commandOutput;
    this.srng = SecureRandomProvider.createSecureRandom();
  }

  public List<ValidatorKeys> generateKeys() {
    return generateKeysStream().toList();
  }

  public Stream<ValidatorKeys> generateKeysStream() {
    final KeysWriter keysWriter = getKeysWriter(srng);
    return IntStream.range(0, validatorCount).mapToObj(ignore -> generateKey(keysWriter));
  }

  private ValidatorKeys generateKey(final KeysWriter keysWriter) {
    final BLSKeyPair validatorKey = BLSKeyPair.random(srng);
    final BLSKeyPair withdrawalKey = BLSKeyPair.random(srng);
    keysWriter.writeKeys(validatorKey, withdrawalKey);
    return new ValidatorKeys(validatorKey, withdrawalKey);
  }

  private KeysWriter getKeysWriter(final SecureRandom secureRandom) {
    final String validatorKeystorePassword =
        readKeystorePassword(validatorPasswordOptions, VALIDATOR_PASSWORD_PROMPT);
    final String withdrawalKeystorePassword =
        readKeystorePassword(withdrawalPasswordOptions, WITHDRAWAL_PASSWORD_PROMPT);

    final Path keystoreDir = getKeystoreOutputDir();
    final KeysWriter keysWriter =
        new EncryptedKeystoreWriter(
            secureRandom,
            validatorKeystorePassword,
            withdrawalKeystorePassword,
            keystoreDir,
            commandOutput);
    return keysWriter;
  }

  private Path getKeystoreOutputDir() {
    return isBlank(outputPath) ? Path.of(".") : Path.of(outputPath);
  }

  private String readKeystorePassword(
      final KeystorePasswordOptions keystorePasswordOptions, final String passwordPrompt) {
    final String password;
    if (keystorePasswordOptions == null) {
      password = askForPassword(passwordPrompt);
    } else if (keystorePasswordOptions.getPasswordFile() != null) {
      password = readFromFile(commandSpec.commandLine(), keystorePasswordOptions.getPasswordFile());
    } else {
      password =
          readFromEnvironmentVariable(
              commandSpec.commandLine(),
              envSupplier,
              keystorePasswordOptions.getPasswordEnvironmentVariable());
    }
    return password;
  }

  private String askForPassword(final String option) {

    if (!consoleAdapter.isConsoleAvailable()) {
      throw new ParameterException(
          commandSpec.commandLine(), "Cannot read password from console: Console not available");
    }

    final char[] firstInput = consoleAdapter.readPassword("Enter password for %s:", option);
    final char[] reconfirmedInput =
        consoleAdapter.readPassword("Re-Enter password for %s:", option);
    if (firstInput == null || reconfirmedInput == null) {
      throw new ParameterException(commandSpec.commandLine(), "Error: Password is blank");
    }

    if (Arrays.equals(firstInput, reconfirmedInput)) {
      final String password = new String(firstInput);
      if (password.isBlank()) {
        throw new ParameterException(commandSpec.commandLine(), "Error: Password is blank");
      }
      return password;
    }

    throw new ParameterException(commandSpec.commandLine(), "Error: Password mismatched");
  }
}
