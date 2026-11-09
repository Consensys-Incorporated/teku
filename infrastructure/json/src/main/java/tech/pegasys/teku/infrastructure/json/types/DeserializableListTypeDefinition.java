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

package tech.pegasys.teku.infrastructure.json.types;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class DeserializableListTypeDefinition<T>
    extends DeserializableArrayTypeDefinition<T, List<T>> {

  public DeserializableListTypeDefinition(
      final DeserializableTypeDefinition<T> itemType,
      final Optional<Integer> minItems,
      final Optional<Integer> maxItems) {
    super(itemType, Function.identity(), minItems, maxItems);
  }
}
