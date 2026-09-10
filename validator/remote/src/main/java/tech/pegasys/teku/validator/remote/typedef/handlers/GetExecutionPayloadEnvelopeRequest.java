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
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_EXECUTION_PAYLOAD_ENVELOPE;

import com.google.common.net.MediaType;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetExecutionPayloadEnvelopeRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public GetExecutionPayloadEnvelopeRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<ExecutionPayloadEnvelope> submit(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }
    final ExecutionPayloadEnvelopeSchema envelopeSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadEnvelopeSchema();
    final ResponseHandler<ExecutionPayloadEnvelope> jsonResponseHandler =
        new ResponseHandler<>(withDataWrapper(envelopeSchema));
    final ResponseHandler<ExecutionPayloadEnvelope> responseHandler =
        new ResponseHandler<>(withDataWrapper(envelopeSchema))
            .withHandler(
                SC_OK,
                (request, response) ->
                    handleResponse(request, response, envelopeSchema, jsonResponseHandler));
    final Map<String, String> urlParams =
        Map.of("slot", slot.toString(), "beacon_block_root", beaconBlockRoot.toHexString());
    final Map<String, String> headers =
        Map.of("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    return get(
        GET_EXECUTION_PAYLOAD_ENVELOPE,
        urlParams,
        emptyMap(),
        emptyMap(),
        headers,
        responseHandler);
  }

  private Optional<ExecutionPayloadEnvelope> handleResponse(
      final Request request,
      final Response response,
      final ExecutionPayloadEnvelopeSchema envelopeSchema,
      final ResponseHandler<ExecutionPayloadEnvelope> jsonResponseHandler)
      throws IOException {
    final String responseContentType = response.header("Content-Type");
    if (responseContentType != null
        && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
      if (response.body() == null) {
        return Optional.empty();
      }
      return Optional.of(envelopeSchema.sszDeserialize(Bytes.of(response.body().bytes())));
    }
    return jsonResponseHandler.handleResponse(request, response);
  }
}
