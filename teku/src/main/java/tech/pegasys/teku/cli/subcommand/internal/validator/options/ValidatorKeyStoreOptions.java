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

import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import picocli.CommandLine;

public class ValidatorKeyStoreOptions {
  @CommandLine.Option(
      names = {"--encrypted-keystore-validator-file"},
      paramLabel = "<FILE>",
      required = true,
      description = "Path to the keystore file containing encrypted signing key for the validator")
  File validatorKeystoreFile;

  @CommandLine.ArgGroup(multiplicity = "1")
  ValidatorPasswordOptions validatorPasswordOptions;

  @VisibleForTesting
  public File getValidatorKeystoreFile() {
    return validatorKeystoreFile;
  }

  @VisibleForTesting
  public ValidatorPasswordOptions getValidatorPasswordOptions() {
    return validatorPasswordOptions;
  }

  @VisibleForTesting
  public void setValidatorKeystoreFile(final File validatorKeystoreFile) {
    this.validatorKeystoreFile = validatorKeystoreFile;
  }

  @VisibleForTesting
  public void setValidatorPasswordOptions(final ValidatorPasswordOptions validatorPasswordOptions) {
    this.validatorPasswordOptions = validatorPasswordOptions;
  }
}
