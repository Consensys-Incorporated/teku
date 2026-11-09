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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class SerializableOneOfTypeDefinitionBuilder<TObject> {
  private final Map<Predicate<TObject>, SerializableTypeDefinition<? extends TObject>> types =
      new LinkedHashMap<>();
  private Optional<String> name = Optional.empty();
  private Optional<String> title = Optional.empty();
  private Optional<String> description = Optional.empty();

  public SerializableOneOfTypeDefinition<TObject> build() {
    return new SerializableOneOfTypeDefinition<>(name, title.or(() -> name), description, types);
  }

  public <T extends TObject> SerializableOneOfTypeDefinitionBuilder<TObject> name(
      final String name) {
    this.name = Optional.ofNullable(name);
    return this;
  }

  public <T extends TObject> SerializableOneOfTypeDefinitionBuilder<TObject> title(
      final String title) {
    this.title = Optional.ofNullable(title);
    return this;
  }

  public <T extends TObject> SerializableOneOfTypeDefinitionBuilder<TObject> description(
      final String description) {
    this.description = Optional.ofNullable(description);
    return this;
  }

  public <T extends TObject> SerializableOneOfTypeDefinitionBuilder<TObject> withType(
      final Predicate<TObject> predicate, final SerializableTypeDefinition<T> typeDefinition) {
    types.put(predicate, typeDefinition);
    return this;
  }
}
