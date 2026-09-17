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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfArrayJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessageSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostPayloadAttestations extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/payload_attestations";

  private final ValidatorDataProvider provider;

  public PostPayloadAttestations(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public PostPayloadAttestations(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(spec, schemaDefinitionCache));
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<PayloadAttestationMessage> messages = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future =
        provider.submitPayloadAttestationMessages(messages);

    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              final ErrorListBadRequest data =
                  ErrorListBadRequest.convert(PARTIAL_PUBLISH_FAILURE_MESSAGE, errors);
              return AsyncApiResponse.respondWithObject(SC_BAD_REQUEST, data);
            }));
  }

  private static EndpointMetadata createMetadata(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS));
    final PayloadAttestationMessageSchema payloadAttestationMessageSchema =
        schemaDefinitions.getPayloadAttestationMessageSchema();
    final int ptcSize =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig())
            .getPayloadTimelinessCommitteeSize();

    final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<PayloadAttestationMessage>
        bodyTypeSelector = context -> payloadAttestationMessageSchema.getJsonTypeDefinition();

    final BiFunction<Bytes, Optional<String>, List<PayloadAttestationMessage>> octetStreamParser =
        (bytes, headerConsensusVersion) ->
            SszListSchema.create(payloadAttestationMessageSchema, ptcSize)
                .sszDeserialize(bytes)
                .asList();

    return EndpointMetadata.post(ROUTE)
        .operationId("submitPayloadAttestationMessages")
        .summary("Submit PayloadAttestation objects to node")
        .description(
            "Submit signed payload attestation messages to the beacon node to be validated and submitted if valid.")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            DeserializableTypeDefinition.listOf(
                payloadAttestationMessageSchema.getJsonTypeDefinition()),
            bodyTypeSelector,
            octetStreamParser)
        .header(ETH_CONSENSUS_VERSION_TYPE)
        .response(
            SC_OK, "Payload attestations are stored in pool and broadcast on appropriate subnet")
        .response(
            SC_BAD_REQUEST,
            "Errors with one or more payload attestations",
            ErrorListBadRequest.getJsonTypeDefinition())
        .withChainDataResponses()
        .build();
  }
}
