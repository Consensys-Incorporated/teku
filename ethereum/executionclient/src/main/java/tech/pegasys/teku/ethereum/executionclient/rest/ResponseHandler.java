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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClient.ResponseSchemaAndDeserializableTypeDefinition;
import tech.pegasys.teku.ethereum.executionclient.schema.BuilderApiResponse;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.SpecMilestone;

public class ResponseHandler<TResp extends SszData> extends AbstractResponseHandler {
  private static final Logger LOG = LogManager.getLogger();

  final SafeFuture<Response<BuilderApiResponse<TResp>>> futureResponse = new SafeFuture<>();
  final Optional<ResponseSchemaAndDeserializableTypeDefinition<TResp>>
      responseSchemaAndTypeDefinitionMaybe;

  public ResponseHandler(
      final Optional<ResponseSchemaAndDeserializableTypeDefinition<TResp>>
          responseSchemaAndTypeDefinitionMaybe) {
    this.responseSchemaAndTypeDefinitionMaybe = responseSchemaAndTypeDefinitionMaybe;
  }

  @Override
  void handleFailure(final IOException exception) {
    futureResponse.completeExceptionally(exception);
  }

  @Override
  void handleResponse(final Request request, final okhttp3.Response response) {
    if (handleResponseError(request, response, futureResponse)) {
      return;
    }
    try (final ResponseBody responseBody = response.body()) {
      if (bodyIsEmpty(responseBody) || responseSchemaAndTypeDefinitionMaybe.isEmpty()) {
        futureResponse.complete(Response.fromNullPayload());
        return;
      }
      final String responseContentType = response.header("Content-Type");
      final MediaType responseMediaType =
          Optional.ofNullable(responseContentType)
              .map(MediaType::parse)
              .orElseGet(
                  () -> {
                    LOG.warn(
                        "Response did not include a Content-Type header, defaulting to {} [{}]",
                        MediaType.JSON_UTF_8,
                        request.url());
                    return MediaType.JSON_UTF_8;
                  });

      if (responseMediaType.is(MediaType.OCTET_STREAM)) {
        final TResp payload =
            responseSchemaAndTypeDefinitionMaybe
                .get()
                .responseSchema()
                .sszDeserialize(Bytes.wrap(responseBody.byteStream().readAllBytes()));
        final BuilderApiResponse<TResp> builderApiResponse =
            new BuilderApiResponse<>(
                SpecMilestone.forName(response.header(HEADER_CONSENSUS_VERSION, "")), payload);
        futureResponse.complete(Response.fromPayloadReceivedAsSsz(builderApiResponse));
        return;
      }

      if (!responseMediaType.type().equals(MediaType.JSON_UTF_8.type())
          || !responseMediaType.subtype().equals(MediaType.JSON_UTF_8.subtype())) {
        LOG.warn(
            "Response contains an incorrect Content-Type header: {}, attempting to parse as {} [{}]",
            responseMediaType,
            MediaType.JSON_UTF_8,
            request.url());
      }
      final BuilderApiResponse<TResp> payload =
          JsonUtil.parse(
              responseBody.byteStream(),
              responseSchemaAndTypeDefinitionMaybe.get().responseTypeDefinition());

      futureResponse.complete(Response.fromPayloadReceivedAsJson(payload));

    } catch (final Throwable ex) {
      futureResponse.completeExceptionally(ex);
    }
  }

  public SafeFuture<Response<BuilderApiResponse<TResp>>> getFutureResponse() {
    return futureResponse;
  }
}
