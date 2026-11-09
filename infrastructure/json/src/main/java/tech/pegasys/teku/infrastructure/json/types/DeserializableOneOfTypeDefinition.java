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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class DeserializableOneOfTypeDefinition<TObject>
    extends SerializableOneOfTypeDefinition<TObject> {

  private final Map<Predicate<String>, DeserializableTypeDefinition<? extends TObject>> parserTypes;

  DeserializableOneOfTypeDefinition(
      final Optional<String> name,
      final Optional<String> title,
      final Optional<String> description,
      final Map<Predicate<TObject>, DeserializableTypeDefinition<? extends TObject>> types,
      final Map<Predicate<String>, DeserializableTypeDefinition<? extends TObject>> parserTypes) {
    super(name, title, description, types);
    this.parserTypes = parserTypes;
  }

  public static <TObject> DeserializableOneOfTypeDefinitionBuilder<TObject> object(
      @SuppressWarnings("unused") final Class<TObject> type) {
    return object();
  }

  static <TObject> DeserializableOneOfTypeDefinitionBuilder<TObject> object() {
    return new DeserializableOneOfTypeDefinitionBuilder<>();
  }

  public DeserializableTypeDefinition<? extends TObject> getMatchingType(final String content) {
    DeserializableTypeDefinition<? extends TObject> typeDefinition = null;
    for (Predicate<String> predicate : parserTypes.keySet()) {
      if (predicate.test(content)) {
        typeDefinition = parserTypes.get(predicate);
        break;
      }
    }

    checkArgument(typeDefinition != null, "No class deserialization method found: %s", content);
    return typeDefinition;
  }
}
