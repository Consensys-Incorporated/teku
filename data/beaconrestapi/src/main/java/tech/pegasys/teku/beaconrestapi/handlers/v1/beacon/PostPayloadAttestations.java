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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
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
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfArrayJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.validator.api.SubmitDataError;

/**
 * Submits PTC members' payload attestation messages. From PQ they are signed with XMSS, a {@link
 * PayloadAttestationMessagePq}; the {@code Eth-Consensus-Version} header names the fork, and
 * without it the messages are read as Gloas ones.
 */
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
    final List<SszData> messages = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future;
    if (!messages.isEmpty() && messages.getFirst() instanceof PayloadAttestationMessagePq) {
      future =
          provider.submitPqPayloadAttestationMessages(
              messages.stream().map(PayloadAttestationMessagePq.class::cast).toList());
    } else {
      future =
          provider.submitPayloadAttestationMessages(
              messages.stream().map(PayloadAttestationMessage.class::cast).toList());
    }

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
    final boolean pqSupported =
        schemaDefinitionCache.getSupportedMilestones().contains(SpecMilestone.PQ);

    final SerializableOneOfTypeDefinitionBuilder<SszData> builder =
        new SerializableOneOfTypeDefinitionBuilder<SszData>().title("PayloadAttestationMessage");
    builder.withType(
        value -> value instanceof PayloadAttestationMessage,
        getMessageSchema(schemaDefinitionCache, SpecMilestone.GLOAS).getJsonTypeDefinition());
    if (pqSupported) {
      builder.withType(
          value -> value instanceof PayloadAttestationMessagePq,
          getMessageSchema(schemaDefinitionCache, SpecMilestone.PQ).getJsonTypeDefinition());
    }
    final SerializableOneOfTypeDefinition<SszData> messageType = builder.build();

    final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<SszData> bodyTypeSelector =
        context ->
            getMessageSchema(
                    schemaDefinitionCache,
                    getMilestone(
                        schemaDefinitionCache,
                        Optional.ofNullable(context.getHeaders().get(HEADER_CONSENSUS_VERSION))))
                .getJsonTypeDefinition();

    final BiFunction<Bytes, Optional<String>, List<SszData>> octetStreamParser =
        (bytes, headerConsensusVersion) -> {
          final SpecMilestone milestone =
              getMilestone(schemaDefinitionCache, headerConsensusVersion);
          final int ptcSize =
              SpecConfigGloas.required(spec.forMilestone(milestone).getConfig())
                  .getPayloadTimelinessCommitteeSize();
          return SszListSchema.create(getMessageSchema(schemaDefinitionCache, milestone), ptcSize)
              .sszDeserialize(bytes)
              .asList()
              .stream()
              .map(SszData.class::cast)
              .toList();
        };

    return EndpointMetadata.post(ROUTE)
        .operationId("submitPayloadAttestationMessages")
        .summary("Submit PayloadAttestation objects to node")
        .description(
            "Submit signed payload attestation messages to the beacon node to be validated and submitted if valid. "
                + "From PQ the messages are signed with XMSS; set Eth-Consensus-Version to the fork of the messages.")
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            SerializableTypeDefinition.listOf(messageType), bodyTypeSelector, octetStreamParser)
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

  /** The fork of the messages: Gloas without the header, otherwise the header's fork. */
  private static SpecMilestone getMilestone(
      final SchemaDefinitionCache schemaDefinitionCache, final Optional<String> headerVersion) {
    if (headerVersion.isEmpty()) {
      return SpecMilestone.GLOAS;
    }
    final SpecMilestone milestone =
        MilestoneDependentTypesUtil.headerBasedSelector(
            schemaDefinitionCache, headerVersion, (headerMilestone, __) -> headerMilestone);
    return milestone.isGreaterThanOrEqualTo(SpecMilestone.PQ)
        ? SpecMilestone.PQ
        : SpecMilestone.GLOAS;
  }

  @SuppressWarnings("unchecked")
  private static SszSchema<SszData> getMessageSchema(
      final SchemaDefinitionCache schemaDefinitionCache, final SpecMilestone milestone) {
    final SchemaDefinitions schemaDefinitions =
        schemaDefinitionCache.getSchemaDefinition(milestone);
    if (milestone == SpecMilestone.PQ) {
      return (SszSchema<SszData>)
          (SszSchema<?>)
              SchemaDefinitionsPq.required(schemaDefinitions)
                  .getPqAttestationSchemas()
                  .getPayloadAttestationMessageSchema();
    }
    return (SszSchema<SszData>)
        (SszSchema<?>)
            SchemaDefinitionsGloas.required(schemaDefinitions).getPayloadAttestationMessageSchema();
  }
}
