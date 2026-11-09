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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import tech.pegasys.teku.bls.keystore.KeyStore;
import tech.pegasys.teku.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.WithdrawalPasswordOptions;

class KeyGeneratorTest {

  private static final int VALIDATORS_COUNT = 2;
  private static final String EXPECTED_PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final Function<String, String> ENV_SUPPLIER =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? EXPECTED_PASSWORD : null;
  private ConsoleAdapter consoleAdapter;
  private CommandSpec commandSpec;

  @BeforeEach
  void setUp() {
    consoleAdapter = mock(ConsoleAdapter.class);
    commandSpec = mock(CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(consoleAdapter.isConsoleAvailable()).thenReturn(true);
    when(consoleAdapter.readPassword(anyString(), any()))
        .thenReturn(EXPECTED_PASSWORD.toCharArray());
  }

  @Test
  void encryptedKeystoresAreCreatedWithInteractivePassword(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertEncryptedKeystoresAreCreated(outputPath, null, null);
  }

  @Test
  void emptyInteractivePasswordRaisesError(@TempDir final Path tempDir) throws IOException {
    when(consoleAdapter.readPassword(anyString(), any())).thenReturn(null);
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> assertEncryptedKeystoresAreCreated(outputPath, null, null))
        .withMessage("Error: Password is blank");
  }

  @Test
  void mismatchedValidatorInteractivePasswordRaisesError(@TempDir final Path tempDir)
      throws IOException {
    when(consoleAdapter.readPassword(anyString(), any()))
        .thenReturn(EXPECTED_PASSWORD.toCharArray())
        .thenReturn("mismatched".toCharArray());
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> assertEncryptedKeystoresAreCreated(outputPath, null, null))
        .withMessage("Error: Password mismatched");
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswordFromEnvironmentVariable(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.setValidatorPasswordEnv(EXPECTED_ENV_VARIABLE);
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.setWithdrawalPasswordEnv(EXPECTED_ENV_VARIABLE);

    assertEncryptedKeystoresAreCreated(
        outputPath, validatorPasswordOptions, withdrawalPasswordOptions);
  }

  @Test
  void invalidEnvironmentVariableRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.setValidatorPasswordEnv("INVALID_ENV");
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.setWithdrawalPasswordEnv("INVALID_ENV");

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: Password cannot be read from environment variable: INVALID_ENV");
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswordFromFile(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final Path passwordFile = Files.writeString(tempDir.resolve("password.txt"), EXPECTED_PASSWORD);

    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.setValidatorPasswordFile(passwordFile.toFile());
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.setWithdrawalPasswordFile(passwordFile.toFile());

    assertEncryptedKeystoresAreCreated(
        outputPath, validatorPasswordOptions, withdrawalPasswordOptions);
  }

  @Test
  void invalidPasswordFileRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    final File passwordFile = tempDir.resolve("nonexistent.txt").toFile();
    validatorPasswordOptions.setValidatorPasswordFile(passwordFile);
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.setWithdrawalPasswordFile(passwordFile);

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: File not found: " + passwordFile);
  }

  @Test
  void emptyPasswordFileRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    final File passwordFile = Files.writeString(outputPath.resolve("password.txt"), "").toFile();
    validatorPasswordOptions.setValidatorPasswordFile(passwordFile);
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.setWithdrawalPasswordFile(passwordFile);

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: Empty password from file: " + passwordFile);
  }

  private void assertEncryptedKeystoresAreCreated(
      final Path outputPath,
      final ValidatorPasswordOptions validatorPasswordOptions,
      final WithdrawalPasswordOptions withdrawalPasswordOptions) {

    final KeyGenerator keyGenerator =
        new KeyGenerator(
            VALIDATORS_COUNT,
            outputPath.toString(),
            validatorPasswordOptions,
            withdrawalPasswordOptions,
            consoleAdapter,
            commandSpec,
            ENV_SUPPLIER,
            SUB_COMMAND_LOG::display);
    keyGenerator.generateKeys();

    // assert that files exist: 2 per validator
    final File[] keystoreFiles = outputPath.toFile().listFiles();
    assertThat(keystoreFiles).hasSize(VALIDATORS_COUNT * 2);
    // all files should have the same password
    assertKeyStoreFilesExistAndAreEncryptedWithPassword(outputPath);

    // select only withdrawal files
    FilenameFilter withdrawalFilter =
        (dir, name) -> name.toLowerCase(Locale.ROOT).contains("withdrawal");

    // assert that files exist: 1 withdrawal file per validator
    final File[] withdrawalFiles = outputPath.toFile().listFiles(withdrawalFilter);
    assertThat(withdrawalFiles).hasSize(VALIDATORS_COUNT);
    Arrays.stream(withdrawalFiles).forEach(file -> assertThat(file).isFile());

    // select only validator files
    FilenameFilter validatorFilter =
        (dir, name) -> name.toLowerCase(Locale.ROOT).contains("validator");

    // assert that files exist: 1 validator file per validator
    final File[] validatorFiles = outputPath.toFile().listFiles(validatorFilter);
    assertThat(validatorFiles).hasSize(VALIDATORS_COUNT);
    Arrays.stream(validatorFiles).forEach(file -> assertThat(file).isFile());
  }

  private void assertKeyStoreFilesExistAndAreEncryptedWithPassword(final Path parentDir) {
    final File[] keyStoreFiles = parentDir.toFile().listFiles();
    for (File file : keyStoreFiles) {
      assertThat(
              KeyStore.validatePassword(
                  EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(file.toURI())))
          .isTrue();
    }
  }
}
