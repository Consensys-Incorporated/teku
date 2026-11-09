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

package tech.pegasys.teku.infrastructure.restapi.openapi.response;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;

public class EventStreamResponseContentTypeDefinition extends DelegatingOpenApiTypeDefinition
    implements ResponseContentTypeDefinition<Void> {

  public EventStreamResponseContentTypeDefinition() {
    super(STRING_TYPE);
  }

  @Override
  public String getContentType() {
    return ContentTypes.EVENT_STREAM;
  }

  @Override
  public void serialize(final Void value, final OutputStream out) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, String> getAdditionalHeaders(final Void value) {
    return Map.of();
  }
}
