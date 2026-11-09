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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.ethereum.json.types.EthereumTypes;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ParameterMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public class GetAttestationData extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/validator/attestation_data";
  private final ValidatorDataProvider provider;
  private final Spec spec;

  private static final ParameterMetadata<UInt64> SLOT_PARAM =
      SLOT_PARAMETER.withDescription(
          "`uint64` The slot for which an attestation data should be created.");

  private static final SerializableTypeDefinition<AttestationData> RESPONSE_TYPE =
      SerializableTypeDefinition.object(AttestationData.class)
          .name("ProduceAttestationDataResponse")
          .withField(
              "data", AttestationData.SSZ_SCHEMA.getJsonTypeDefinition(), Function.identity())
          .build();

  public GetAttestationData(final DataProvider provider, final Spec spec) {
    this(provider.getValidatorDataProvider(), spec);
  }

  public GetAttestationData(final ValidatorDataProvider provider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("produceAttestationData")
            .summary("Produce an AttestationData")
            .description(
                """
                  Requests that the beacon node produce an AttestationData. For `slot`s in \
                  Electra and later, this AttestationData must have a `committee_index` of 0.

                  For `slot`s in Gloas and later, the `index` in `AttestationData` is used to signal \
                  payload availability which is determined and set by the beacon node.

                  A 503 error must be returned if the block identified by the response \
                  `beacon_block_root` is optimistic (i.e. the attestation attests to a block \
                  that has not been fully verified by an execution engine).
                  """)
            .tags(TAG_VALIDATOR, TAG_VALIDATOR_REQUIRED)
            .queryParam(SLOT_PARAM)
            .queryParam(
                COMMITTEE_INDEX_PARAMETER.withDescription(
                    "`UInt64` The committee index for which an attestation data should be created. For `slot`s in "
                        + "Electra and later (before Gloas), this parameter is ignored and the response will always have `committee_index` of 0."))
            .response(
                SC_OK,
                "Request successful",
                RESPONSE_TYPE,
                EthereumTypes.sszResponseType(
                    attestationData -> spec.atSlot(attestationData.getSlot()).getMilestone()))
            .withNotFoundResponse()
            .withNotAcceptableResponse()
            .withChainDataResponses()
            .build());
    this.provider = provider;
    this.spec = spec;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final UInt64 slot = request.getQueryParameter(SLOT_PARAM);
    final Optional<UInt64> committeeIndex =
        request.getOptionalQueryParameter(COMMITTEE_INDEX_PARAMETER);
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();
    if (!validate(request, milestone, committeeIndex)) {
      return;
    }

    // Post-Electra (before Gloas), committee_index is deprecated and must be ignored.
    // Always use 0 regardless of what the caller provided.
    final int effectiveCommitteeIndex =
        (milestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)
                && milestone.isLessThan(SpecMilestone.GLOAS))
            ? 0
            : committeeIndex.orElse(UInt64.ZERO).intValue();

    final SafeFuture<Optional<AttestationData>> future =
        provider.createAttestationDataAtSlot(slot, effectiveCommitteeIndex);

    request.respondAsync(
        future.thenApply(
            maybeAttestationData ->
                maybeAttestationData
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }

  static boolean validate(
      final RestApiRequest request,
      final SpecMilestone milestone,
      final Optional<UInt64> committeeIndex)
      throws JsonProcessingException {
    if (committeeIndex.isEmpty() && milestone.isLessThan(SpecMilestone.ELECTRA)) {
      // prior to electra, committeeIndex is a required field
      request.respondError(
          SC_BAD_REQUEST,
          String.format("'%s' parameter must be set before electra.", COMMITTEE_INDEX));
      return false;
    } else if (milestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS)
        && committeeIndex.isPresent()
        && committeeIndex.get().isGreaterThan(1)) {
      // post gloas, the committee index is optional. If set, it must be 0 or 1.
      request.respondError(
          SC_BAD_REQUEST,
          String.format("'%s' parameter must be less than 2 in gloas.", COMMITTEE_INDEX));
      return false;
    }
    return true;
  }
}
