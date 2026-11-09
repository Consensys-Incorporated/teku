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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.PARAMETER_BLOCK_ID;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.fulu.ColumnCustodyAtSlot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class GetColumnCustodyAtSlot extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/beacon/column_custody_at_slot/{block_id}";
  private final ChainDataProvider chainDataProvider;

  private static final SerializableTypeDefinition<ColumnCustodyAtSlot> COLUMN_CUSTODY_TYPE =
      SerializableTypeDefinition.object(ColumnCustodyAtSlot.class)
          .withOptionalField("root", CoreTypes.BYTES32_TYPE, ColumnCustodyAtSlot::root)
          .withField("columns_found", listOf(UINT64_TYPE), ColumnCustodyAtSlot::columnsFound)
          .withOptionalField("blob_count", INTEGER_TYPE, ColumnCustodyAtSlot::blobCount)
          .build();

  private static final SerializableTypeDefinition<ColumnCustodyAtSlot> RESPONSE_TYPE =
      SerializableTypeDefinition.<ColumnCustodyAtSlot>object()
          .name("GetColumnCustodyAtSlot")
          .withField("data", COLUMN_CUSTODY_TYPE, Function.identity())
          .build();

  public GetColumnCustodyAtSlot(final DataProvider provider) {
    this(provider.getChainDataProvider());
  }

  public GetColumnCustodyAtSlot(final ChainDataProvider chainDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("GetColumnCustodyAtSlot")
            .summary("Get column custody")
            .description("Get all columns found on at a slot.")
            .tags(TAG_TEKU)
            .pathParam(PARAMETER_BLOCK_ID.withDescription("slot of the columns to retrieve."))
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .withNotFoundResponse()
            .build());
    this.chainDataProvider = chainDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    final String blockId = request.getPathParameter(PARAMETER_BLOCK_ID);
    final SafeFuture<Optional<ColumnCustodyAtSlot>> future =
        chainDataProvider.getColumnCustodyAtSlot(blockId);
    request.respondAsync(
        future.thenApply(
            maybeCustody ->
                maybeCustody
                    .map(AsyncApiResponse::respondOk)
                    .orElseGet(AsyncApiResponse::respondNotFound)));
  }
}
