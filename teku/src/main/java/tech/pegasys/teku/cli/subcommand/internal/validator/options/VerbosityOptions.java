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
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;

public class VerbosityOptions {

  @Option(
      names = {"--verbose-output-enabled"},
      paramLabel = "<true|false>",
      description = "Controls verbose status output. (default: ${DEFAULT-VALUE})",
      arity = "1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      defaultValue = "true")
  private boolean verboseOutputEnabled = true;

  VerbosityOptions() {}

  @VisibleForTesting
  public VerbosityOptions(final boolean verboseOutputEnabled) {
    this.verboseOutputEnabled = verboseOutputEnabled;
  }

  public boolean isVerboseOutputEnabled() {
    return verboseOutputEnabled;
  }
}
