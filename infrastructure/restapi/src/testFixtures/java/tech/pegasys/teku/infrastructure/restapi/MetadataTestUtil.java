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

package tech.pegasys.teku.infrastructure.restapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.AssertionsForClassTypes;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.RequestContentTypeDefinition;

public class MetadataTestUtil {

  public static void verifyMetadataErrorResponse(final RestApiEndpoint handler, final int code)
      throws JsonProcessingException {
    final EndpointMetadata metadata = handler.getMetadata();
    final byte[] result =
        toBytes(
            out ->
                metadata.serialize(
                    code, ContentTypes.JSON, new HttpErrorResponse(code, "BAD"), out));
    AssertionsForClassTypes.assertThat(new String(result, StandardCharsets.UTF_8))
        .isEqualTo("{\"code\":" + code + ",\"message\":\"BAD\"}");
  }

  public static void verifyMetadataEmptyResponse(final RestApiEndpoint handler, final int code) {
    final EndpointMetadata metadata = handler.getMetadata();
    assertThatThrownBy(() -> metadata.getResponseType(code, ContentTypes.JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unexpected content type");
  }

  public static String getResponseStringFromMetadata(
      final RestApiEndpoint handler, final int code, final Object data)
      throws JsonProcessingException {
    final EndpointMetadata metadata = handler.getMetadata();
    final byte[] result = toBytes(out -> metadata.serialize(code, ContentTypes.JSON, data, out));
    return new String(result, StandardCharsets.UTF_8);
  }

  public static byte[] getResponseSszFromMetadata(
      final RestApiEndpoint handler, final int code, final Object data)
      throws JsonProcessingException {
    final EndpointMetadata metadata = handler.getMetadata();
    return toBytes(out -> metadata.serialize(code, ContentTypes.OCTET_STREAM, data, out));
  }

  public static Object getRequestBodyFromMetadata(final RestApiEndpoint handler, final String json)
      throws IOException {
    final RequestContentTypeDefinition<?> requestContentTypeDefinition =
        handler.getMetadata().getRequestBodyType();
    return requestContentTypeDefinition.deserialize(
        IOUtils.toInputStream(json, StandardCharsets.UTF_8));
  }

  public static Object getRequestBodyFromMetadata(
      final RestApiEndpoint handler, final Map<String, String> headers, final String json)
      throws IOException {
    final RequestContentTypeDefinition<?> requestContentTypeDefinition =
        handler.getMetadata().getRequestBodyType();
    return requestContentTypeDefinition.deserialize(
        IOUtils.toInputStream(json, StandardCharsets.UTF_8), headers);
  }

  private static byte[] toBytes(final Serializer func) throws JsonProcessingException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    func.serialize(out);
    return out.toByteArray();
  }

  private interface Serializer {
    void serialize(OutputStream out) throws JsonProcessingException;
  }
}
