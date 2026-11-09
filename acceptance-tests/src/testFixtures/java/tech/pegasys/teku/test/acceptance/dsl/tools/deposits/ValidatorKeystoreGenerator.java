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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

public class ValidatorKeystoreGenerator {
  private final EncryptedKeystoreWriter encryptedKeystoreWriter;
  private final String validatorKeyPassword;
  private final Path passwordsOutputPath;

  public ValidatorKeystoreGenerator(
      final String validatorKeyPassword,
      final Path keysOutputPath,
      final Path passwordsOutputPath,
      final Consumer<String> commandOutput) {
    // Withdrawal key password is unnecessary for this mode of running.
    this.encryptedKeystoreWriter =
        new EncryptedKeystoreWriter(
            SecureRandomProvider.createSecureRandom(),
            validatorKeyPassword,
            "",
            keysOutputPath,
            commandOutput);
    this.validatorKeyPassword = validatorKeyPassword;
    this.passwordsOutputPath = passwordsOutputPath;
    createDirectory(passwordsOutputPath);
  }

  public void generateKeystoreAndPasswordFiles(final List<ValidatorKeys> validatorKeys) {
    try {
      for (final ValidatorKeys key : validatorKeys) {
        final BLSKeyPair keyPair = key.getValidatorKey();
        final String keystoreFileName = encryptedKeystoreWriter.writeValidatorKey(keyPair);
        final String validatorPasswordFileName =
            keyPair.getPublicKey().toAbbreviatedString() + "_validator.txt";
        final Path validatorPasswordFile =
            Files.createFile(passwordsOutputPath.resolve(validatorPasswordFileName));
        Files.write(validatorPasswordFile, validatorKeyPassword.getBytes(Charset.defaultCharset()));
        if (key.isLocked()) {
          final Path lockFile = Path.of(keystoreFileName + ".lock");
          lockFile.toFile().createNewFile();
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void createDirectory(final Path directoryPath) {
    try {
      Files.createDirectories(directoryPath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
