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

import static java.lang.System.lineSeparator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class KeystorePasswordOptionsTest {
  private static final String PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final String SPACE_ENV_VARIABLE = "SPACE_ENV";
  private static final Function<String, String> ENV_SUPPLIER =
      envVariable ->
          switch (envVariable) {
            case EXPECTED_ENV_VARIABLE -> PASSWORD;
            case SPACE_ENV_VARIABLE -> "     ";
            default -> null;
          };
  private static final String EXPECTED_ENV_ERROR =
      "Error: Password cannot be read from environment variable: %s";
  private static final String EXPECTED_EMPTY_PASSWORD_FILE_ERROR =
      "Error: Empty password from file: %s";
  private final CommandLine commandLine = mock(CommandLine.class);

  @Test
  void passwordCanBeReadFromFileWithSingleLine(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    Files.writeString(passwordFile, PASSWORD);

    final String actualPassword =
        KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile());
    assertThat(actualPassword).isEqualTo(PASSWORD);
  }

  @Test
  void passwordCanBeReadFromFileWithMultipleLines(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String fileContent = PASSWORD + lineSeparator() + "secondline";
    Files.writeString(passwordFile, fileContent);

    final String actualPassword =
        KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile());
    // Normalisation of the password including stripping lines is done by the signers library
    // Teku should pass through the file content as-is.
    assertThat(actualPassword).isEqualTo(fileContent);
  }

  @Test
  void emptyPasswordFileThrowsException(@TempDir final Path tempDir) throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String expectedPassword = "";
    Files.writeString(passwordFile, expectedPassword + lineSeparator());

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage(EXPECTED_EMPTY_PASSWORD_FILE_ERROR, passwordFile.toFile());
  }

  @Test
  void whitespaceCharactersOnlyPasswordFileThrowsException(@TempDir final Path tempDir)
      throws IOException {
    final Path passwordFile = tempDir.resolve("password.txt");
    final String expectedPassword = "    ";
    Files.writeString(passwordFile, expectedPassword + lineSeparator());

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage(EXPECTED_EMPTY_PASSWORD_FILE_ERROR, passwordFile.toFile());
  }

  @Test
  void nonExistentPasswordFileThrowsException(@TempDir final Path tempDir) {
    final Path passwordFile = tempDir.resolve("password.txt");
    final CommandLine commandLine = mock(CommandLine.class);

    Assertions.assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> KeystorePasswordOptions.readFromFile(commandLine, passwordFile.toFile()))
        .withMessage("Error: File not found: " + passwordFile);
  }

  @Test
  void passwordCanBeReadFromEnvironmentSupplierFunction() {
    final CommandLine commandLine = mock(CommandLine.class);
    final String actualPassword =
        KeystorePasswordOptions.readFromEnvironmentVariable(
            commandLine, ENV_SUPPLIER, EXPECTED_ENV_VARIABLE);
    assertThat(actualPassword).isEqualTo(PASSWORD);
  }

  @Test
  void nonExistentEnvironmentVariableThrowsException() {
    final CommandLine commandLine = mock(CommandLine.class);
    final String testEnv = "NOT_VALID";
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                KeystorePasswordOptions.readFromEnvironmentVariable(
                    commandLine, ENV_SUPPLIER, testEnv))
        .withMessage(EXPECTED_ENV_ERROR, testEnv);
  }

  @Test
  void emptyPasswordFromEnvironmentVariableThrowsException() {
    final CommandLine commandLine = mock(CommandLine.class);
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                KeystorePasswordOptions.readFromEnvironmentVariable(
                    commandLine, ENV_SUPPLIER, SPACE_ENV_VARIABLE))
        .withMessage(EXPECTED_ENV_ERROR, SPACE_ENV_VARIABLE);
  }
}
