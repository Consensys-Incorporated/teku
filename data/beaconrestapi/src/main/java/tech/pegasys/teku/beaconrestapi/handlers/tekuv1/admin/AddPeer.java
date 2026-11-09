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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;

public class AddPeer extends RestApiEndpoint {

  public static final String ROUTE = "/teku/v1/admin/add_peer";
  final NetworkDataProvider networkDataProvider;

  public AddPeer(final DataProvider dataProvider) {
    this(dataProvider.getNetworkDataProvider());
  }

  AddPeer(final NetworkDataProvider networkDataProvider) {
    super(
        EndpointMetadata.post(ROUTE)
            .operationId("AddPeer")
            .summary("Add a static peer to the node")
            .description("Add a static peer to the node passing a multiaddress.")
            .tags(TAG_TEKU)
            .requestBodyType(STRING_TYPE.withDescription("Multiaddress of the peer to add"))
            .response(SC_OK, "Peer added successfully")
            .withBadRequestResponse(Optional.of("Invalid peer address"))
            .withInternalErrorResponse()
            .build());
    this.networkDataProvider = networkDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    try {

      final String peerAddress = request.getRequestBody();
      if (peerAddress.isEmpty()) {
        request.respondWithCode(SC_OK);
        return;
      }

      final DiscoveryNetwork<?> discoveryNetwork =
          networkDataProvider
              .getDiscoveryNetwork()
              .orElseThrow(() -> new IllegalStateException("Discovery network not available"));

      discoveryNetwork.addStaticPeer(peerAddress);
      request.respondWithCode(SC_OK);
    } catch (final IllegalArgumentException e) {
      request.respondError(SC_BAD_REQUEST, e.getMessage());
    } catch (final IllegalStateException e) {
      request.respondError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }
}
