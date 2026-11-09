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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static tech.pegasys.teku.api.ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.ETH_CONSENSUS_VERSION_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.commons.io.function.IOFunction;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostProposerPreferences extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/proposer_preferences";

  private final ValidatorDataProvider provider;
  private final int maxItems;

  public PostProposerPreferences(
      final DataProvider dataProvider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(dataProvider.getValidatorDataProvider(), spec, schemaDefinitionCache);
  }

  public PostProposerPreferences(
      final ValidatorDataProvider provider,
      final Spec spec,
      final SchemaDefinitionCache schemaDefinitionCache) {
    this(provider, schemaDefinitionCache, getMaxItems(spec));
  }

  private PostProposerPreferences(
      final ValidatorDataProvider provider,
      final SchemaDefinitionCache schemaDefinitionCache,
      final int maxItems) {
    super(createMetadata(schemaDefinitionCache, maxItems));
    this.provider = provider;
    this.maxItems = maxItems;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final List<SignedProposerPreferences> proposerPreferences = request.getRequestBody();
    if (proposerPreferences.size() > maxItems) {
      request.respondError(
          SC_BAD_REQUEST,
          String.format(
              "A maximum of %s SignedProposerPreferences objects can be submitted to the node at one time",
              maxItems));
      return;
    }
    request.respondAsync(
        provider
            .submitProposerPreferences(proposerPreferences)
            .thenApply(PostProposerPreferences::respond));
  }

  private static AsyncApiResponse respond(final List<SubmitDataError> errors) {
    if (errors.isEmpty()) {
      return AsyncApiResponse.respondWithCode(SC_OK);
    }
    return AsyncApiResponse.respondWithObject(
        SC_BAD_REQUEST, ErrorListBadRequest.convert(PARTIAL_PUBLISH_FAILURE_MESSAGE, errors));
  }

  private static int getMaxItems(final Spec spec) {
    final SpecConfigGloas specConfig =
        SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
    return (specConfig.getMinSeedLookahead() + 1) * specConfig.getSlotsPerEpoch();
  }

  private static EndpointMetadata createMetadata(
      final SchemaDefinitionCache schemaDefinitionCache, final int maxItems) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(
            schemaDefinitionCache.getSchemaDefinition(SpecMilestone.GLOAS));
    final SignedProposerPreferencesSchema signedProposerPreferencesSchema =
        schemaDefinitions.getSignedProposerPreferencesSchema();
    final IOFunction<Bytes, List<SignedProposerPreferences>> octetStreamParser =
        bytes ->
            SszListSchema.create(signedProposerPreferencesSchema, maxItems)
                .sszDeserialize(bytes)
                .asList();

    return EndpointMetadata.post(ROUTE)
        .operationId("submitProposerPreferences")
        .summary("Submit signed proposer preferences")
        .description(
            "Verifies given signed proposer preferences and publishes them on the proposer_preferences gossipsub topic.")
        .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
        .requestBodyType(
            DeserializableTypeDefinition.listOf(
                signedProposerPreferencesSchema.getJsonTypeDefinition()),
            octetStreamParser)
        .headerRequired(
            ETH_CONSENSUS_VERSION_TYPE.withDescription(
                "The active consensus version to which the signed proposer preferences being submitted belongs."))
        .response(
            SC_OK, "Signed proposer preferences are stored in pool and broadcast to the network")
        .response(
            SC_BAD_REQUEST,
            "Errors with one or more signed proposer preferences",
            ErrorListBadRequest.getJsonTypeDefinition())
        .withChainDataResponses()
        .build();
  }
}
