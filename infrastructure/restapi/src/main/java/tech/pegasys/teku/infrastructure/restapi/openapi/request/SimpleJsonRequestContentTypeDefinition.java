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

package tech.pegasys.teku.infrastructure.restapi.openapi.request;

import java.io.IOException;
import java.io.InputStream;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SimpleJsonRequestContentTypeDefinition<T> extends DelegatingOpenApiTypeDefinition
    implements RequestContentTypeDefinition<T> {

  private final DeserializableTypeDefinition<T> typeDefinition;

  public SimpleJsonRequestContentTypeDefinition(
      final DeserializableTypeDefinition<T> typeDefinition) {
    super(typeDefinition);
    this.typeDefinition = typeDefinition;
  }

  @Override
  public T deserialize(final InputStream in) throws IOException {
    return JsonUtil.parse(in, typeDefinition);
  }
}
