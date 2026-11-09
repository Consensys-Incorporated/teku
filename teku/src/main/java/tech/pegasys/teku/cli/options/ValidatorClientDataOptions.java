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

import java.nio.file.Path;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.service.serviceutils.layout.DataConfig;

public class ValidatorClientDataOptions extends DataOptions {

  @Option(
      names = {"--data-validator-path"},
      paramLabel = "<FILENAME>",
      description = "Path to validator client data\n  Default: <data-base-path>/validator",
      showDefaultValue = Visibility.NEVER,
      arity = "1")
  private Path dataValidatorPath;

  @Override
  protected DataConfig.Builder configureDataConfig(final DataConfig.Builder config) {
    return super.configureDataConfig(config).validatorDataPath(dataValidatorPath);
  }
}
