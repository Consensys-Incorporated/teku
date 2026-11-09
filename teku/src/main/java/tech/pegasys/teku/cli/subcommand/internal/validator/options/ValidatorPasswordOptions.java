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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import picocli.CommandLine;

public class ValidatorPasswordOptions implements KeystorePasswordOptions {
  @CommandLine.Option(
      names = {"--encrypted-keystore-validator-password-file"},
      paramLabel = "<FILE>",
      required = true,
      description = "Read password from the file to encrypt the validator keys")
  File validatorPasswordFile;

  @CommandLine.Option(
      names = {"--encrypted-keystore-validator-password-env"},
      paramLabel = "<ENV_VAR>",
      required = true,
      description = "Read password from environment variable to encrypt the validator keys")
  String validatorPasswordEnv;

  @Override
  public File getPasswordFile() {
    return validatorPasswordFile;
  }

  @Override
  public String getPasswordEnvironmentVariable() {
    return validatorPasswordEnv;
  }

  public void setValidatorPasswordEnv(final String validatorPasswordEnv) {
    checkNotNull(validatorPasswordEnv);
    this.validatorPasswordEnv = validatorPasswordEnv;
  }

  public void setValidatorPasswordFile(final File validatorPasswordFile) {
    checkNotNull(validatorPasswordFile);
    this.validatorPasswordFile = validatorPasswordFile;
  }
}
