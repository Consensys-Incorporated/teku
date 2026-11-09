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

package tech.pegasys.teku.cli.util;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Model.OptionSpec;

public class EnvironmentVariableParamsProvider extends AbstractParamsProvider<String>
    implements AdditionalParamsProvider {
  private static final Logger LOG = LogManager.getLogger();
  private static final String ENV_VAR_PREFIX = "TEKU_";

  private final Map<String, String> environment;

  public EnvironmentVariableParamsProvider(final Map<String, String> environment) {
    this.environment = environment;
  }

  @Override
  public Map<String, String> getAdditionalParams(final List<OptionSpec> potentialParams) {
    return getAdditionalParams(potentialParams, environment);
  }

  @Override
  protected String onConflictingParams(
      final Entry<String, String> conflictingParam,
      final String conflictingValue1,
      final String conflictingValue2) {
    LOG.warn(
        "Multiple environment variables referring to the same configuration '{}'. Keeping {} and ignoring {}",
        conflictingParam.getKey().replaceFirst("^-+", ""),
        conflictingValue1,
        conflictingValue2);
    return conflictingValue1;
  }

  @Override
  protected Optional<Entry<String, String>> translateEntry(
      final Entry<String, String> configEntry) {
    if (!configEntry.getKey().startsWith(ENV_VAR_PREFIX)) {
      return Optional.empty();
    }
    return Optional.of(
        Map.entry(
            configEntry.getKey().substring(ENV_VAR_PREFIX.length()).replace('_', '-'),
            configEntry.getValue()));
  }

  @Override
  protected Map.Entry<String, String> translateToArg(
      final OptionSpec matchedOption, final Map.Entry<String, String> envEntry) {
    return Map.entry(matchedOption.longestName(), envEntry.getValue());
  }
}
