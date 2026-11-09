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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.COMMITTEE_INDEX_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.SLOT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT_QUERY_DESCRIPTION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_BEACON;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;

public class GetAttestations extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/beacon/pool/attestations";
  private final NodeDataProvider nodeDataProvider;

  public GetAttestations(final DataProvider dataProvider, final Spec spec) {
    this(dataProvider.getNodeDataProvider(), spec);
  }

  public GetAttestations(final NodeDataProvider nodeDataProvider, final Spec spec) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getPoolAttestations")
            .summary("Get Attestations from operations pool")
            .description(
                "Retrieves attestations known by the node but not necessarily incorporated into any block.")
            .tags(TAG_BEACON)
            .deprecated(true)
            .queryParam(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION))
            .queryParam(COMMITTEE_INDEX_PARAMETER)
            .response(SC_OK, "Request successful", getResponseType(spec))
            .build());
    this.nodeDataProvider = nodeDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<UInt64> slot =
        request.getOptionalQueryParameter(SLOT_PARAMETER.withDescription(SLOT_QUERY_DESCRIPTION));
    final Optional<UInt64> committeeIndex =
        request.getOptionalQueryParameter(COMMITTEE_INDEX_PARAMETER);

    request.respondOk(nodeDataProvider.getAttestations(slot, committeeIndex));
  }

  private static SerializableTypeDefinition<List<Attestation>> getResponseType(final Spec spec) {
    return SerializableTypeDefinition.<List<Attestation>>object()
        .name("GetPoolAttestationsResponse")
        .withField(
            "data",
            listOf(
                spec.getGenesisSchemaDefinitions().getAttestationSchema().getJsonTypeDefinition()),
            Function.identity())
        .build();
  }
}
