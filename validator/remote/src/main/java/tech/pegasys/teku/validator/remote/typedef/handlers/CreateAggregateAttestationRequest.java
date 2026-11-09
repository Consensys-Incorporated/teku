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

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.ATTESTATION_DATA_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_AGGREGATE_V2;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class CreateAggregateAttestationRequest extends AbstractTypeDefRequest {
  private final SchemaDefinitionCache schemaDefinitionCache;
  private final SpecMilestone specMilestone;
  private final UInt64 slot;
  final Bytes32 attestationHashTreeRoot;
  final Optional<UInt64> committeeIndex;
  final boolean attestationsV2ApisEnabled;

  public CreateAggregateAttestationRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final SchemaDefinitionCache schemaDefinitionCache,
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final Optional<UInt64> committeeIndex,
      final boolean attestationsV2ApisEnabled,
      final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.schemaDefinitionCache = schemaDefinitionCache;
    this.specMilestone = spec.atSlot(slot).getMilestone();
    this.slot = slot;
    this.attestationHashTreeRoot = attestationHashTreeRoot;
    this.committeeIndex = committeeIndex;
    this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
  }

  public Optional<ObjectAndMetaData<Attestation>> submit() {
    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitionCache.atSlot(slot).getAttestationSchema().castTypeToAttestationSchema();

    // Use attestation v2 api post Electra only. This logic can be removed once we reach the Electra
    // milestone
    if (attestationsV2ApisEnabled || specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      if (committeeIndex.isEmpty()) {
        throw new IllegalArgumentException("Missing required parameter: committee index");
      }
      return submitPostElectra(
          slot, attestationHashTreeRoot, committeeIndex.get(), attestationSchema);
    }

    return submitPreElectra(slot, attestationHashTreeRoot, attestationSchema, specMilestone);
  }

  private Optional<ObjectAndMetaData<Attestation>> submitPostElectra(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final UInt64 committeeIndex,
      final AttestationSchema<Attestation> attestationSchema) {
    final DeserializableTypeDefinition<GetAggregateAttestationResponseV2>
        getAggregateAttestationTypeDef =
            DeserializableTypeDefinition.object(GetAggregateAttestationResponseV2.class)
                .initializer(GetAggregateAttestationResponseV2::new)
                .withField(
                    "version",
                    DeserializableTypeDefinition.enumOf(SpecMilestone.class),
                    GetAggregateAttestationResponseV2::getSpecMilestone,
                    GetAggregateAttestationResponseV2::setSpecMilestone)
                .withField(
                    "data",
                    attestationSchema.getJsonTypeDefinition(),
                    GetAggregateAttestationResponse::getData,
                    GetAggregateAttestationResponse::setData)
                .build();
    final ResponseHandler<GetAggregateAttestationResponseV2> responseHandler =
        new ResponseHandler<>(getAggregateAttestationTypeDef);
    final Map<String, String> queryParams = new HashMap<>();
    queryParams.put(SLOT, slot.toString());
    queryParams.put(ATTESTATION_DATA_ROOT, attestationHashTreeRoot.toHexString());
    queryParams.put(COMMITTEE_INDEX, committeeIndex.toString());

    return get(GET_AGGREGATE_V2, queryParams, responseHandler)
        .map(
            getAggregateAttestationResponse ->
                new ObjectAndMetaData<>(
                    getAggregateAttestationResponse.getData(),
                    getAggregateAttestationResponse.getSpecMilestone(),
                    false,
                    false,
                    false));
  }

  private Optional<ObjectAndMetaData<Attestation>> submitPreElectra(
      final UInt64 slot,
      final Bytes32 attestationHashTreeRoot,
      final AttestationSchema<Attestation> attestationSchema,
      final SpecMilestone specMilestone) {
    final DeserializableTypeDefinition<GetAggregateAttestationResponse>
        getAggregateAttestationTypeDef =
            DeserializableTypeDefinition.object(GetAggregateAttestationResponse.class)
                .initializer(GetAggregateAttestationResponse::new)
                .withField(
                    "data",
                    attestationSchema.getJsonTypeDefinition(),
                    GetAggregateAttestationResponse::getData,
                    GetAggregateAttestationResponse::setData)
                .build();
    final ResponseHandler<GetAggregateAttestationResponse> responseHandler =
        new ResponseHandler<>(getAggregateAttestationTypeDef);
    final Map<String, String> queryParams =
        Map.of(SLOT, slot.toString(), ATTESTATION_DATA_ROOT, attestationHashTreeRoot.toString());

    return get(GET_AGGREGATE, queryParams, responseHandler)
        .map(
            getAggregateAttestationResponse ->
                new ObjectAndMetaData<>(
                    getAggregateAttestationResponse.getData(), specMilestone, false, false, false));
  }

  public static class GetAggregateAttestationResponse {

    private Attestation data;

    public GetAggregateAttestationResponse() {}

    public GetAggregateAttestationResponse(final Attestation data) {
      this.data = data;
    }

    public Attestation getData() {
      return data;
    }

    public void setData(final Attestation data) {
      this.data = data;
    }
  }

  public static class GetAggregateAttestationResponseV2 extends GetAggregateAttestationResponse {

    private SpecMilestone specMilestone;

    public GetAggregateAttestationResponseV2() {}

    public GetAggregateAttestationResponseV2(final Attestation data) {
      super(data);
    }

    public SpecMilestone getSpecMilestone() {
      return specMilestone;
    }

    public void setSpecMilestone(final SpecMilestone specMilestone) {
      this.specMilestone = specMilestone;
    }
  }
}
