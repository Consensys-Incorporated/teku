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

package tech.pegasys.teku.ethereum.json.types.config;

import java.util.Map;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SpecConfigDataMapBuilder {

  public static final DeserializableTypeDefinition<Map<String, Object>> GET_SPEC_RESPONSE_TYPE =
      DeserializableTypeDefinition.<Map<String, Object>, SpecConfigDataMapBuilder>object()
          .name("GetSpecResponse")
          .initializer(SpecConfigDataMapBuilder::new)
          .finisher(SpecConfigDataMapBuilder::build)
          .withField(
              "data",
              DeserializableTypeDefinition.configMap(),
              Function.identity(),
              SpecConfigDataMapBuilder::configMap)
          .build();

  private Map<String, Object> configMap = Map.of();

  public SpecConfigDataMapBuilder configMap(final Map<String, Object> configMap) {
    this.configMap = configMap;
    return this;
  }

  public Map<String, Object> build() {
    return configMap;
  }
}
