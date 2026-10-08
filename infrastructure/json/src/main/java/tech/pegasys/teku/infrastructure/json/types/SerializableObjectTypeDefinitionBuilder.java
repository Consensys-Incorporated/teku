/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class SerializableObjectTypeDefinitionBuilder<TObject> {

  private final Map<String, SerializableFieldDefinition<TObject>> fields = new LinkedHashMap<>();
  private Optional<String> name = Optional.empty();
  private Optional<String> title = Optional.empty();
  private Optional<String> description = Optional.empty();

  SerializableObjectTypeDefinitionBuilder() {}

  public SerializableObjectTypeDefinitionBuilder<TObject> name(final String name) {
    this.name = Optional.of(name);
    return this;
  }

  public SerializableObjectTypeDefinitionBuilder<TObject> title(final String title) {
    this.title = Optional.of(title);
    return this;
  }

  public SerializableObjectTypeDefinitionBuilder<TObject> description(final String description) {
    this.description = Optional.of(description);
    return this;
  }

  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, TField> getter) {
    checkArgument(
        !this.fields.containsKey(name),
        "Field %s was already defined, attempting to add twice.",
        name);
    this.fields.put(name, new RequiredSerializableFieldDefinition<>(name, getter, type));
    return this;
  }

  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withOptionalField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, Optional<TField>> getter) {
    checkArgument(
        !this.fields.containsKey(name),
        "Field %s was already defined, attempting to add twice.",
        name);
    this.fields.put(name, new OptionalSerializableFieldDefinition<>(name, getter, type));
    return this;
  }

  /** Adds a required field that is written as {@code null} when the getter returns empty. */
  public <TField> SerializableObjectTypeDefinitionBuilder<TObject> withNullableField(
      final String name,
      final SerializableTypeDefinition<TField> type,
      final Function<TObject, Optional<TField>> getter) {
    checkArgument(
        !this.fields.containsKey(name),
        "Field %s was already defined, attempting to add twice.",
        name);
    this.fields.put(name, new NullableSerializableFieldDefinition<>(name, getter, type));
    return this;
  }

  public SerializableTypeDefinition<TObject> build() {
    return new SerializableObjectTypeDefinition<>(name, title.or(() -> name), description, fields);
  }
}
