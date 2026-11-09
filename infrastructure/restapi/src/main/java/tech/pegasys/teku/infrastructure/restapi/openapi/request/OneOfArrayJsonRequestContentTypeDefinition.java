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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;

public class OneOfArrayJsonRequestContentTypeDefinition<T> extends DelegatingOpenApiTypeDefinition
    implements RequestContentTypeDefinition<List<? extends T>> {

  private final BodyTypeSelector<T> bodyTypeSelector;

  public OneOfArrayJsonRequestContentTypeDefinition(
      final SerializableTypeDefinition<List<T>> typeDefinition,
      final BodyTypeSelector<T> bodyTypeSelector) {
    super(typeDefinition);
    this.bodyTypeSelector = bodyTypeSelector;
  }

  @Override
  public List<? extends T> deserialize(final InputStream in) throws IOException {
    return deserialize(in, Map.of());
  }

  @Override
  public List<? extends T> deserialize(final InputStream in, final Map<String, String> headers)
      throws IOException {
    final String json = IOUtils.toString(in, StandardCharsets.UTF_8);
    final DeserializableTypeDefinition<? extends T> type =
        bodyTypeSelector.selectType(new BodyTypeSelectorContext(json, headers));

    if (type == null) {
      throw new MissingRequestBodyException();
    }

    if (!openApiTypeDefinition.getReferencedTypeDefinitions().contains(type)) {
      throw new IllegalStateException(
          "Schema determined for parsing request body is not listed in requestBodyTypes");
    }
    return JsonUtil.parse(json, DeserializableTypeDefinition.listOf(type));
  }

  public interface BodyTypeSelector<T> {
    DeserializableTypeDefinition<? extends T> selectType(final BodyTypeSelectorContext context);
  }

  public static class BodyTypeSelectorContext {
    final String body;
    final Map<String, String> headers;

    public BodyTypeSelectorContext(final String body, final Map<String, String> headers) {
      this.body = body;
      this.headers = headers;
    }

    public String getBody() {
      return body;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }
  }
}
