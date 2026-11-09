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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.OpenApiTypeDefinition;

public class OctetStreamResponseContentTypeDefinition<T> extends DelegatingOpenApiTypeDefinition
    implements ResponseContentTypeDefinition<T> {
  public static final OpenApiTypeDefinition OCTET_STREAM_BYTES_TYPE =
      DeserializableTypeDefinition.string(Bytes.class)
          .formatter(Bytes::toHexString)
          .parser(Bytes::fromHexString)
          .format("binary")
          .build();
  private final OctetStreamSerializer<T> serializer;
  private final Function<T, Map<String, String>> toAdditionalHeaders;

  public OctetStreamResponseContentTypeDefinition(
      final OctetStreamSerializer<T> serializer,
      final Function<T, Map<String, String>> toAdditionalHeaders) {
    super(OCTET_STREAM_BYTES_TYPE);
    this.serializer = serializer;
    this.toAdditionalHeaders = toAdditionalHeaders;
  }

  @Override
  public String getContentType() {
    return ContentTypes.OCTET_STREAM;
  }

  @Override
  public void serialize(final T value, final OutputStream out) throws IOException {
    serializer.serialize(value, out);
  }

  @Override
  public Map<String, String> getAdditionalHeaders(final T value) {
    return toAdditionalHeaders.apply(value);
  }

  public interface OctetStreamSerializer<T> {
    void serialize(T data, OutputStream out) throws IOException;
  }
}
