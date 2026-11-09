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

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collection;

public interface SerializableFieldDefinition<TObject> {
  void writeField(final TObject source, JsonGenerator gen) throws IOException;

  void writeOpenApiField(JsonGenerator gen) throws IOException;

  Collection<OpenApiTypeDefinition> getReferencedTypeDefinitions();

  String getName();

  default boolean isRequired() {
    return false;
  }
}
