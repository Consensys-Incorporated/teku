/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_EXECUTION_PAYLOAD_BID;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetExecutionPayloadBidRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public GetExecutionPayloadBidRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<ExecutionPayloadBid> submit(final UInt64 slot, final UInt64 builderIndex) {
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }
    final ExecutionPayloadBidSchema bidSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadBidSchema();
    final ResponseHandler<ExecutionPayloadBid> jsonResponseHandler =
        new ResponseHandler<>(withDataWrapper(bidSchema));
    final ResponseHandler<ExecutionPayloadBid> responseHandler =
        new ResponseHandler<>(withDataWrapper(bidSchema))
            .withHandler(
                SC_OK,
                (request, response) ->
                    handleResponse(request, response, bidSchema, jsonResponseHandler));
    final Map<String, String> urlParams =
        Map.of("slot", slot.toString(), "builder_index", builderIndex.toString());
    final Map<String, String> headers =
        Map.of("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    return get(
        GET_EXECUTION_PAYLOAD_BID, urlParams, emptyMap(), emptyMap(), headers, responseHandler);
  }

  private Optional<ExecutionPayloadBid> handleResponse(
      final Request request,
      final Response response,
      final ExecutionPayloadBidSchema bidSchema,
      final ResponseHandler<ExecutionPayloadBid> jsonResponseHandler)
      throws IOException {
    final String responseContentType = response.header("Content-Type");
    if (responseContentType != null
        && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
      if (response.body() == null) {
        return Optional.empty();
      }
      return Optional.of(bidSchema.sszDeserialize(Bytes.of(response.body().bytes())));
    }
    return jsonResponseHandler.handleResponse(request, response);
  }
}
