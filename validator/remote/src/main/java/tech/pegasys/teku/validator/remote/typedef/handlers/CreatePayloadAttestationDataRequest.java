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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PAYLOAD_ATTESTATION_DATA;

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
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationDataSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreatePayloadAttestationDataRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public CreatePayloadAttestationDataRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient, final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public Optional<PayloadAttestationData> submit(final UInt64 slot) {
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }
    final PayloadAttestationDataSchema payloadAttestationDataSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getPayloadAttestationDataSchema();
    final ResponseHandler<PayloadAttestationData> jsonResponseHandler =
        new ResponseHandler<>(withDataWrapper(payloadAttestationDataSchema));
    final ResponseHandler<PayloadAttestationData> responseHandler =
        new ResponseHandler<>(withDataWrapper(payloadAttestationDataSchema))
            .withHandler(
                SC_OK,
                (request, response) ->
                    handlePayloadAttestationDataResult(
                        request, response, payloadAttestationDataSchema, jsonResponseHandler));
    final Map<String, String> queryParams = Map.of("slot", slot.toString());
    final Map<String, String> headers =
        Map.of("Accept", "application/octet-stream;q=0.9, application/json;q=0.4");
    return get(
        GET_PAYLOAD_ATTESTATION_DATA,
        emptyMap(),
        queryParams,
        emptyMap(),
        headers,
        responseHandler);
  }

  private Optional<PayloadAttestationData> handlePayloadAttestationDataResult(
      final Request request,
      final Response response,
      final PayloadAttestationDataSchema payloadAttestationDataSchema,
      final ResponseHandler<PayloadAttestationData> jsonResponseHandler)
      throws IOException {
    final String responseContentType = response.header("Content-Type");
    if (responseContentType != null
        && MediaType.parse(responseContentType).is(MediaType.OCTET_STREAM)) {
      if (response.body() == null) {
        return Optional.empty();
      }
      return Optional.of(
          payloadAttestationDataSchema.sszDeserialize(Bytes.of(response.body().bytes())));
    }
    return jsonResponseHandler.handleResponse(request, response);
  }
}
