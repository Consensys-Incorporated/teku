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

import static tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof.SYNC_COMMITTEE_SELECTION_PROOF;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class PostSyncCommitteeSelections extends RestApiEndpoint {

  public static final String ROUTE = "/eth/v1/validator/sync_committee_selections";

  private static final SerializableTypeDefinition<List<SyncCommitteeSelectionProof>> RESPONSE_TYPE =
      SerializableTypeDefinition.<List<SyncCommitteeSelectionProof>>object()
          .name("PostSyncCommitteeSelectionsResponse")
          .withField("data", listOf(SYNC_COMMITTEE_SELECTION_PROOF), Function.identity())
          .build();

  public PostSyncCommitteeSelections() {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("submitSyncCommitteeSelections")
            .summary(
                "Determine if a distributed validator has been selected to make a sync committee contribution")
            .description(
                "Submit sync committee selections to a DVT middleware client. It returns the threshold aggregated "
                    + "sync committee selection. This endpoint should be used by a validator client running as part "
                    + "of a distributed validator cluster, and is implemented by a distributed validator middleware "
                    + "client. This endpoint is used to exchange partial selection proof slot signatures for "
                    + "combined/aggregated selection proofs to allow a validator client to correctly determine if one"
                    + " of its validators has been selected to perform a sync committee contribution (sync "
                    + "aggregation) duty in this slot. Consensus clients need not support this endpoint and may "
                    + "return a 501.")
            .tags(TAG_VALIDATOR)
            .requestBodyType(listOf(SYNC_COMMITTEE_SELECTION_PROOF))
            .response(
                SC_OK,
                "Returns the threshold aggregated sync committee selection proofs.",
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
