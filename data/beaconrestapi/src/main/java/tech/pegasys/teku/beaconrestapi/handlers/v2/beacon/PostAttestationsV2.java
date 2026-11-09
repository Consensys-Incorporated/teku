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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_EXPERIMENTAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
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
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestationSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAttestationsV2 extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v2/beacon/pool/attestations";
  private final ValidatorDataProvider validatorDataProvider;

  public PostAttestationsV2(
      final DataProvider validatorDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(validatorDataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public PostAttestationsV2(
      final ValidatorDataProvider validatorDataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(spec, schemaDefinitionCache));
    this.validatorDataProvider = validatorDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<Attestation> attestations = request.getRequestBody();
    final SafeFuture<List<SubmitDataError>> future =
        validatorDataProvider.submitAttestations(attestations);

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

    final BiPredicate<Attestation, SpecMilestone> attestationSchemaPredicate =
        (attestation, milestone) ->
            schemaDefinitionCache
                .milestoneAtSlot(attestation.getData().getSlot())
                .equals(milestone);

    final SerializableOneOfTypeDefinitionBuilder<Attestation> builder =
        new SerializableOneOfTypeDefinitionBuilder<Attestation>().title("SignedAttestation");

    builder.withType(
        value -> attestationSchemaPredicate.test(value, SpecMilestone.PHASE0),
        getPhaseOAttestationSchema(schemaDefinitionCache).getJsonTypeDefinition());
    builder.withType(
        value -> attestationSchemaPredicate.test(value, SpecMilestone.ELECTRA),
        getElectraAttestationSchema(schemaDefinitionCache).getJsonTypeDefinition());
    final SerializableOneOfTypeDefinition<Attestation> attestationSchemaDefinition =
        builder.build();

    final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<Attestation>
        attestationBodySelector =
            context ->
                headerBasedSelector(
                    context.getHeaders(),
                    schemaDefinitionCache,
                    PostAttestationsV2::getAttestationSchema);

    final BiFunction<Bytes, Optional<String>, List<Attestation>>
        milestoneSpecificOctetStreamParser =
            (bytes, headerConsensusVersion) -> {
              return headerBasedSelector(
                  schemaDefinitionCache,
                  headerConsensusVersion,
                  (milestone, schemaDefinitions) -> {
                    final SpecConfig specConfig = spec.forMilestone(milestone).getConfig();
                    final SszSchema<Attestation> attestationSchema =
                        getAttestationSchema(schemaDefinitions);
                    return SszListSchema.create(
                            attestationSchema,
                            (long) specConfig.getMaxValidatorsPerCommittee()
                                * specConfig.getMaxCommitteesPerSlot())
                        .sszDeserialize(bytes)
                        .asList();
                  });
            };

    return EndpointMetadata.post(ROUTE)
        .operationId("submitPoolAttestationsV2")
        .summary("Submit Attestation objects to node")
        .description(
            """
                  Submits Attestation objects to the node. Each attestation in the request body is processed individually.
                  If an attestation is validated successfully, the node MUST publish that attestation on the appropriate subnet.
                  If one or more attestations fail validation, the node MUST return a 400 error with details of which attestations have failed, and why.
                  """)
        .tags(TAG_BEACON, TAG_VALIDATOR_REQUIRED, TAG_EXPERIMENTAL)
        .requestBodyType(
            SerializableTypeDefinition.listOf(attestationSchemaDefinition),
            attestationBodySelector,
            milestoneSpecificOctetStreamParser)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the attestations being submitted."))
        .response(SC_OK, "Attestations are stored in pool and broadcast on appropriate subnet")
        .response(
            SC_BAD_REQUEST,
            "Errors with one or more attestations",
            ErrorListBadRequest.getJsonTypeDefinition())
        .withChainDataResponses()
        .build();
  }

  private static SszSchema<Attestation> getAttestationSchema(
      final SchemaDefinitions schemaDefinitions) {
    return schemaDefinitions
        .toVersionElectra()
        .map(
            schemaDefinitionsElectra ->
                schemaDefinitionsElectra.getSingleAttestationSchema().castTypeToAttestationSchema())
        .orElse(schemaDefinitions.getAttestationSchema());
  }

  private static AttestationSchema<Attestation> getPhaseOAttestationSchema(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return schemaDefinitionCache.getSchemaDefinition(SpecMilestone.PHASE0).getAttestationSchema();
  }

  private static SingleAttestationSchema getElectraAttestationSchema(
      final SchemaDefinitionCache schemaDefinitionCache) {
    return SchemaDefinitionsElectra.required(
            schemaDefinitionCache.getSchemaDefinition(SpecMilestone.ELECTRA))
        .getSingleAttestationSchema();
  }
}
