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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.getSchemaDefinitionForAllSupportedMilestones;
import static tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.MilestoneDependentTypesUtil.headerBasedSelector;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.openapi.request.OneOfArrayJsonRequestContentTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SignedAggregateAndProofPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostAggregateAndProofsV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/validator/aggregate_and_proofs";
  private final ValidatorDataProvider provider;

  public PostAggregateAndProofsV2(
      final DataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public PostAggregateAndProofsV2(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    super(createMetadata(spec, schemaDefinitionCache));
    this.provider = provider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<SszData> signedAggregateAndProofs = request.getRequestBody();
    // From PQ, aggregates carry the proof of their signatures, and the header selects the type
    final SafeFuture<List<SubmitDataError>> future =
        !signedAggregateAndProofs.isEmpty()
                && signedAggregateAndProofs.getFirst() instanceof SignedAggregateAndProofPq
            ? provider.sendPqAggregateAndProofs(
                signedAggregateAndProofs.stream()
                    .map(SignedAggregateAndProofPq.class::cast)
                    .toList())
            : provider.sendAggregateAndProofs(
                signedAggregateAndProofs.stream()
                    .map(SignedAggregateAndProof.class::cast)
                    .toList());

    request.respondAsync(
        future.thenApply(
            errors -> {
              if (errors.isEmpty()) {
                return AsyncApiResponse.respondWithCode(SC_OK);
              }
              return AsyncApiResponse.respondWithError(
                  SC_BAD_REQUEST, PARTIAL_PUBLISH_FAILURE_MESSAGE);
            }));
  }

  /** The milestone's signed aggregate schema: from PQ, the XMSS-signed one with a proof. */
  @SuppressWarnings("unchecked")
  private static SszSchema<SszData> getSignedAggregateAndProofSchema(
      final SchemaDefinitions schemaDefinitions) {
    return (SszSchema<SszData>)
        (SszSchema<?>)
            (schemaDefinitions instanceof SchemaDefinitionsPq schemaDefinitionsPq
                ? schemaDefinitionsPq.getPqAttestationSchemas().getSignedAggregateAndProofSchema()
                : schemaDefinitions.getSignedAggregateAndProofSchema());
  }

  private static UInt64 getAggregateSlot(final SszData signedAggregateAndProof) {
    return signedAggregateAndProof instanceof SignedAggregateAndProofPq pqAggregate
        ? pqAggregate.getMessage().getAggregate().getData().getSlot()
        : ((SignedAggregateAndProof) signedAggregateAndProof)
            .getMessage()
            .getAggregate()
            .getData()
            .getSlot();
  }

  private static EndpointMetadata createMetadata(
      final Spec spec, final SchemaDefinitionCache schemaDefinitionCache) {

    final BiPredicate<SszData, SpecMilestone> signedAggregateAndProofSchemaPredicate =
        (signedAggregateAndProof, milestone) ->
            schemaDefinitionCache
                .milestoneAtSlot(getAggregateSlot(signedAggregateAndProof))
                .equals(milestone);

    final SerializableOneOfTypeDefinition<SszData> signedAggregateAndProofSchemaDefinition =
        getSchemaDefinitionForAllSupportedMilestones(
            schemaDefinitionCache,
            "SignedAggregateAndProof",
            PostAggregateAndProofsV2::getSignedAggregateAndProofSchema,
            signedAggregateAndProofSchemaPredicate);

    final OneOfArrayJsonRequestContentTypeDefinition.BodyTypeSelector<SszData>
        aggregateAndProofBodySelector =
            context ->
                headerBasedSelector(
                    context.getHeaders(),
                    schemaDefinitionCache,
                    PostAggregateAndProofsV2::getSignedAggregateAndProofSchema);

    final BiFunction<Bytes, Optional<String>, List<SszData>> milestoneSpecificOctetStreamParser =
        (bytes, headerConsensusVersion) ->
            headerBasedSelector(
                schemaDefinitionCache,
                headerConsensusVersion,
                (milestone, schemaDefinitions) -> {
                  final SpecConfig specConfig = spec.forMilestone(milestone).getConfig();
                  return SszListSchema.create(
                          getSignedAggregateAndProofSchema(
                              schemaDefinitionCache.getSchemaDefinition(milestone)),
                          (long) specConfig.getMaxCommitteesPerSlot()
                              * ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE)
                      .sszDeserialize(bytes)
                      .asList();
                });

    return EndpointMetadata.post(ROUTE)
        .operationId("publishAggregateAndProofsV2")
        .summary("Publish multiple aggregate and proofs")
        .description(
            "Verifies given aggregate and proofs and publishes it on appropriate gossipsub topic.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            SerializableTypeDefinition.listOf(signedAggregateAndProofSchemaDefinition),
            aggregateAndProofBodySelector,
            milestoneSpecificOctetStreamParser)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "Version of the aggregate and proofs being submitted."))
        .response(SC_OK, "Attestations are stored in pool and broadcast on appropriate subnet")
        .withBadRequestResponse(Optional.of("Invalid request syntax."))
        .withChainDataResponses()
        .build();
  }
}
