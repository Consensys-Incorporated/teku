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

package tech.pegasys.teku.cli.slashingprotection;

import java.util.List;
import java.util.Set;
import picocli.CommandLine.IModelTransformer;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Hides options that the repair command gets from shared option classes but never reads, so its
 * help only lists options that change what it does.
 *
 * <p>The options are still accepted, so a config file or command line written for the validator
 * client can be reused.
 */
public class HideUnusedRepairOptions implements IModelTransformer {
  static final Set<String> UNUSED_OPTIONS =
      Set.of(
          "--ignore-weak-subjectivity-period-enabled",
          "--validators-external-signer-url",
          "--validators-external-signer-timeout",
          "--validators-external-signer-keystore",
          "--validators-external-signer-keystore-password-file",
          "--validators-external-signer-truststore",
          "--validators-external-signer-truststore-password-file",
          "--validators-external-signer-concurrent-limit");

  @Override
  public CommandSpec transform(final CommandSpec commandSpec) {
    for (final OptionSpec option : List.copyOf(commandSpec.options())) {
      if (!option.hidden() && UNUSED_OPTIONS.contains(option.longestName())) {
        commandSpec.remove(option);
        commandSpec.addOption(option.toBuilder().hidden(true).build());
      }
    }
    return commandSpec;
  }
}
