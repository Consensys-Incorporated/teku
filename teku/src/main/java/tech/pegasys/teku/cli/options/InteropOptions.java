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

package tech.pegasys.teku.cli.options;

import com.google.common.base.Strings;
import java.nio.file.Path;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.validator.api.InteropConfig;

public class InteropOptions {

  @Option(
      hidden = true,
      names = {"--Xinterop-genesis-time"},
      paramLabel = "<INTEGER>",
      description = "Time of mocked genesis",
      arity = "1")
  private int interopGenesisTime = InteropConfig.DEFAULT_INTEROP_GENESIS_TIME;

  @Option(
      hidden = true,
      names = {"--Xinterop-genesis-payload-header"},
      paramLabel = "<FILE>",
      description = "Payload header to be included in the mocked genesis",
      arity = "1")
  private String interopGenesisPayloadHeader = null;

  @Option(
      hidden = true,
      names = {"--Xinterop-owned-validator-start-index"},
      paramLabel = "<INTEGER>",
      description = "Index of first validator owned by this node",
      arity = "1")
  private int interopOwnerValidatorStartIndex = 0;

  @Option(
      hidden = true,
      names = {"--Xinterop-owned-validator-count"},
      paramLabel = "<INTEGER>",
      description = "Number of validators owned by this node",
      arity = "1")
  private int interopOwnerValidatorCount = 0;

  @Option(
      hidden = true,
      names = {"--Xinterop-number-of-validators"},
      paramLabel = "<INTEGER>",
      description = "Represents the total number of validators in the network")
  private int interopNumberOfValidators = InteropConfig.DEFAULT_INTEROP_NUMBER_OF_VALIDATORS;

  @Option(
      hidden = true,
      names = {"--Xinterop-enabled"},
      paramLabel = "<BOOLEAN>",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enables developer options for testing",
      arity = "0..1")
  private boolean interopEnabled = false;

  public TekuConfiguration.Builder configure(final TekuConfiguration.Builder builder) {
    return builder.interop(
        interopBuilder ->
            interopBuilder
                .interopGenesisTime(interopGenesisTime)
                .interopGenesisPayloadHeader(convertToPath(interopGenesisPayloadHeader))
                .interopOwnedValidatorStartIndex(interopOwnerValidatorStartIndex)
                .interopOwnedValidatorCount(interopOwnerValidatorCount)
                .interopNumberOfValidators(interopNumberOfValidators)
                .interopEnabled(interopEnabled));
  }

  private Path convertToPath(final String option) {
    if (Strings.isNullOrEmpty(option)) {
      return null;
    }
    return Path.of(option);
  }
}
