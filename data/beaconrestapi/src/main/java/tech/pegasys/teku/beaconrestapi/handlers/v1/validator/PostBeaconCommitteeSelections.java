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

import static tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof.BEACON_COMMITTEE_SELECTION_PROOF;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostBeaconCommitteeSelections extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/beacon_committee_selections";

  private static final SerializableTypeDefinition<List<BeaconCommitteeSelectionProof>>
      RESPONSE_TYPE =
          SerializableTypeDefinition.<List<BeaconCommitteeSelectionProof>>object()
              .name("PostBeaconCommitteeSelectionsResponse")
              .withField("data", listOf(BEACON_COMMITTEE_SELECTION_PROOF), Function.identity())
              .build();

  public PostBeaconCommitteeSelections() {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitBeaconCommitteeSelections")
            .summary(
                "Determine if a distributed validator has been selected to aggregate attestations")
            .description(
                "This endpoint should be used by a validator client running as part of a distributed validator cluster, "
                    + "and is  implemented by a distributed validator middleware client. This endpoint is used to "
                    + "exchange partial  selection proofs for combined/aggregated selection proofs to allow a validator "
                    + "client  to correctly determine if any of its validators has been selected to perform an "
                    + "attestation aggregation duty in a slot.  Validator clients running in a distributed validator "
                    + "cluster must query this endpoint at the start of an epoch for the current and lookahead (next) "
                    + "epochs for all validators that have attester duties in the current and lookahead epochs. Consensus"
                    + " clients need not support this endpoint and may return a 501.")
            .tags(TAG_VALIDATOR)
            .requestBodyType(listOf(BEACON_COMMITTEE_SELECTION_PROOF))
            .response(
                SC_OK,
                "Returns the threshold aggregated beacon committee selection proofs.",
                RESPONSE_TYPE)
            .withBadRequestResponse(Optional.of("Invalid request syntax."))
            .withInternalErrorResponse()
            .withNotImplementedResponse()
            .withServiceUnavailableResponse()
            .build());
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.respondError(SC_NOT_IMPLEMENTED, "Method not implemented by the Beacon Node");
  }
}
