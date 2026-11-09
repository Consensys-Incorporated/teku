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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import picocli.CommandLine.Model.OptionSpec;

public abstract class AbstractParamsProvider<V> {

  protected Map<String, String> getAdditionalParams(
      final List<OptionSpec> potentialOptions, final Map<String, V> config) {
    final Map<String, String> additionalParams = new HashMap<>();

    potentialOptions.stream()
        .flatMap(this::getPotentialParamAndOption)
        .flatMap(potentialParamAndOption -> getMatchingParam(potentialParamAndOption, config))
        .forEach(
            matchedParam ->
                additionalParams.merge(
                    matchedParam.getKey(),
                    matchedParam.getValue(),
                    (conflict1, conflict2) ->
                        onConflictingParams(matchedParam, conflict1, conflict2)));

    return additionalParams;
  }

  protected Stream<Entry<String, OptionSpec>> getPotentialParamAndOption(
      final OptionSpec optionSpec) {
    return Arrays.stream(optionSpec.names())
        .filter(name -> name.startsWith("--"))
        .map(name -> Map.entry(name.replaceFirst("^-+", ""), optionSpec));
  }

  protected Stream<Entry<String, String>> getMatchingParam(
      final Entry<String, OptionSpec> potentialParamAndOption, final Map<String, V> config) {
    return config.entrySet().stream()
        .filter(
            configEntry ->
                isOptionMatchingConfigEntry(potentialParamAndOption.getKey(), configEntry))
        .map(configEntry -> translateToArg(potentialParamAndOption.getValue(), configEntry));
  }

  protected boolean isOptionMatchingConfigEntry(
      final String potentialParamName, final Entry<String, V> configEntry) {
    return translateEntry(configEntry)
        .map(entry -> entry.getKey().equalsIgnoreCase(potentialParamName))
        .orElse(false);
  }

  protected abstract Optional<Entry<String, V>> translateEntry(final Entry<String, V> configEntry);

  protected abstract Entry<String, String> translateToArg(
      OptionSpec matchedOption, Entry<String, V> configEntry);

  protected abstract String onConflictingParams(
      Entry<String, String> conflictingParam, String conflictingValue1, String conflictingValue2);
}
