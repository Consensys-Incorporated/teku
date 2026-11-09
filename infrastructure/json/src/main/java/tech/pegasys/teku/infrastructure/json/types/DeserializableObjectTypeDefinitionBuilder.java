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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> {
  private final Map<String, DeserializableFieldDefinition<TObject, TBuilder>> fields =
      new LinkedHashMap<>();
  private Optional<String> name = Optional.empty();
  private Optional<String> title = Optional.empty();
  private Optional<String> description = Optional.empty();
  private Supplier<TBuilder> initializer;
  private Function<TBuilder, TObject> finisher;

  DeserializableObjectTypeDefinitionBuilder() {}

  public DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> name(final String name) {
    this.name = Optional.of(name);
    return this;
  }

  public DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> title(final String title) {
    this.title = Optional.of(title);
    return this;
  }

  public DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> description(
      final String description) {
    this.description = Optional.of(description);
    return this;
  }

  public DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> initializer(
      final Supplier<TBuilder> initializer) {
    this.initializer = initializer;
    return this;
  }

  public DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> finisher(
      final Function<TBuilder, TObject> finisher) {
    this.finisher = finisher;
    return this;
  }

  public <TField> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> withField(
      final String name,
      final DeserializableTypeDefinition<TField> type,
      final Function<TObject, TField> getter,
      final BiConsumer<TBuilder, TField> setter) {
    this.fields.put(name, new RequiredDeserializableFieldDefinition<>(name, getter, setter, type));
    return this;
  }

  public <TField> DeserializableObjectTypeDefinitionBuilder<TObject, TBuilder> withOptionalField(
      final String name,
      final DeserializableTypeDefinition<TField> type,
      final Function<TObject, Optional<TField>> getter,
      final BiConsumer<TBuilder, Optional<TField>> setter) {
    this.fields.put(name, new OptionalDeserializableFieldDefinition<>(name, getter, setter, type));
    return this;
  }

  public DeserializableTypeDefinition<TObject> build() {
    checkNotNull(initializer, "Must specify an initializer");
    checkNotNull(finisher, "Must specify a finisher");
    return new DeserializableObjectTypeDefinition<>(
        name, title.or(() -> name), description, initializer, finisher, fields);
  }
}
