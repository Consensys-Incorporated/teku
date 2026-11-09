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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

public interface KeystorePasswordOptions {
  File getPasswordFile();

  String getPasswordEnvironmentVariable();

  static String readFromFile(final CommandLine commandLine, final File passwordFile) {
    try {
      final String password = Files.readString(passwordFile.toPath(), StandardCharsets.UTF_8);
      if (isBlank(password)) {
        throw new ParameterException(
            commandLine, "Error: Empty password from file: " + passwordFile);
      }
      return password;
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throw new ParameterException(commandLine, "Error: File not found: " + passwordFile);
    } catch (final IOException e) {
      throw new ParameterException(
          commandLine,
          "Error: Unexpected IO error reading file [" + passwordFile + "] : " + e.getMessage());
    }
  }

  static String readFromEnvironmentVariable(
      final CommandLine commandLine,
      final Function<String, String> envSupplier,
      final String environmentVariable) {
    final String password = envSupplier.apply(environmentVariable);
    if (isBlank(password)) {
      throw new ParameterException(
          commandLine,
          "Error: Password cannot be read from environment variable: " + environmentVariable);
    }
    return password;
  }
}
