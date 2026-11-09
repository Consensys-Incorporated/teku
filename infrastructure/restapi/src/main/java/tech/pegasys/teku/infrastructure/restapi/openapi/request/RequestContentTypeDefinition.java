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
import java.util.Map;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;

public interface RequestContentTypeDefinition<T> extends OpenApiTypeDefinition {

  T deserialize(InputStream in) throws IOException;

  default T deserialize(final InputStream in, final Map<String, String> headers)
      throws IOException {
    return deserialize(in);
  }
}
