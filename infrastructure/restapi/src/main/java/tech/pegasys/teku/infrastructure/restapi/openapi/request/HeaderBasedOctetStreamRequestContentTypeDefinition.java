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
import java.util.function.BiFunction;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DelegatingOpenApiTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.openapi.response.OctetStreamResponseContentTypeDefinition;

public class HeaderBasedOctetStreamRequestContentTypeDefinition<T>
    extends DelegatingOpenApiTypeDefinition implements RequestContentTypeDefinition<T> {

  private final BiFunction<Bytes, Map<String, String>, T> parser;

  private HeaderBasedOctetStreamRequestContentTypeDefinition(
      final BiFunction<Bytes, Map<String, String>, T> parser) {
    super(OctetStreamResponseContentTypeDefinition.OCTET_STREAM_BYTES_TYPE);
    this.parser = parser;
  }

  public static <T> RequestContentTypeDefinition<T> parseBytes(
      final BiFunction<Bytes, Map<String, String>, T> parser) {
    return new HeaderBasedOctetStreamRequestContentTypeDefinition<>(parser);
  }

  @Override
  public T deserialize(final InputStream in, final Map<String, String> headers) throws IOException {
    return parser.apply(Bytes.wrap(in.readAllBytes()), headers);
  }

  @Override
  public T deserialize(final InputStream in) throws IOException {
    return deserialize(in, Map.of());
  }
}
