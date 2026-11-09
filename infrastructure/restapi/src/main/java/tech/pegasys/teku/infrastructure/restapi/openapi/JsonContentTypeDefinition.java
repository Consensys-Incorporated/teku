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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import java.io.IOException;
import java.io.OutputStream;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class JsonContentTypeDefinition<T> extends DelegatingOpenApiTypeDefinition
    implements ContentTypeDefinition<T> {
  private final SerializableTypeDefinition<T> typeDefinition;

  public JsonContentTypeDefinition(final SerializableTypeDefinition<T> typeDefinition) {
    super(typeDefinition);
    this.typeDefinition = typeDefinition;
  }

  @Override
  public void serialize(final T value, final OutputStream out) throws IOException {
    JsonUtil.serializeToBytes(value, typeDefinition, out);
  }
}
