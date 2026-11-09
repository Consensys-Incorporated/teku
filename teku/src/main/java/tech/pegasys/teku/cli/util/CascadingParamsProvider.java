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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Model.OptionSpec;

public class CascadingParamsProvider implements AdditionalParamsProvider {

  private final List<AdditionalParamsProvider> additionalConfigProviders;

  public CascadingParamsProvider(final AdditionalParamsProvider... additionalConfigProviders) {
    this.additionalConfigProviders = List.of(additionalConfigProviders);
  }

  @Override
  public Map<String, String> getAdditionalParams(final List<OptionSpec> potentialParams) {
    return additionalConfigProviders.stream()
        .map(
            additionalConfigProvider ->
                additionalConfigProvider.getAdditionalParams(potentialParams))
        .reduce(
            (primaryConfig, secondaryConfig) -> {
              final HashMap<String, String> mergedConfig = new HashMap<>(primaryConfig);
              secondaryConfig.forEach(
                  (key, value) ->
                      mergedConfig.merge(
                          key, value, (primaryValue, secondaryValue) -> primaryValue));
              return mergedConfig;
            })
        .orElse(Map.of());
  }
}
