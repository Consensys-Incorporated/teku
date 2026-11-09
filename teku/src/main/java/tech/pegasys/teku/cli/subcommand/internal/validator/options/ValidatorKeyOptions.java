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
import picocli.CommandLine;

public class ValidatorKeyOptions {
  @CommandLine.Option(
      names = {"--validator-private-key"},
      paramLabel = "<PRIVATE_KEY>",
      required = true,
      description = "Private signing key for the validator")
  String validatorKey;

  @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
  ValidatorKeyStoreOptions validatorKeyStoreOptions;

  @VisibleForTesting
  public String getValidatorKey() {
    return validatorKey;
  }

  @VisibleForTesting
  public ValidatorKeyStoreOptions getValidatorKeyStoreOptions() {
    return validatorKeyStoreOptions;
  }

  @VisibleForTesting
  public void setValidatorKey(final String validatorKey) {
    this.validatorKey = validatorKey;
  }

  @VisibleForTesting
  public void setValidatorKeyStoreOptions(final ValidatorKeyStoreOptions validatorKeyStoreOptions) {
    this.validatorKeyStoreOptions = validatorKeyStoreOptions;
  }
}
