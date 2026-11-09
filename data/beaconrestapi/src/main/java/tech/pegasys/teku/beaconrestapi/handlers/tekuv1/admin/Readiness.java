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

import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.REQUIRE_PREPARED_PROPOSERS_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.REQUIRE_VALIDATOR_REGISTRATIONS_PARAMETER;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.TARGET_PEER_COUNT_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_TEKU;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;

public class Readiness extends RestApiEndpoint {
  public static final String ROUTE = "/teku/v1/admin/readiness";
  private final SyncDataProvider syncProvider;
  private final ChainDataProvider chainDataProvider;
  private final NetworkDataProvider networkDataProvider;
  private final NodeDataProvider nodeDataProvider;

  private final ExecutionClientDataProvider executionClientDataProvider;

  public Readiness(final DataProvider provider) {
    this(
        provider.getSyncDataProvider(),
        provider.getChainDataProvider(),
        provider.getNetworkDataProvider(),
        provider.getNodeDataProvider(),
        provider.getExecutionClientDataProvider());
  }

  Readiness(
      final SyncDataProvider syncProvider,
      final ChainDataProvider chainDataProvider,
      final NetworkDataProvider networkDataProvider,
      final NodeDataProvider nodeDataProvider,
      final ExecutionClientDataProvider executionClientDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("readiness")
            .summary("Get node readiness")
            .description("Returns 200 if the node is ready to accept traffic")
            .tags(TAG_TEKU)
            .queryParam(TARGET_PEER_COUNT_PARAMETER)
            .queryParam(REQUIRE_PREPARED_PROPOSERS_PARAMETER)
            .queryParam(REQUIRE_VALIDATOR_REGISTRATIONS_PARAMETER)
            .response(SC_OK, "Node is ready")
            .response(SC_SERVICE_UNAVAILABLE, "Node not initialized or having issues")
            .response(
                SC_NO_CONTENT, "Data is unavailable because the chain has not yet reached genesis")
            .build());
    this.syncProvider = syncProvider;
    this.chainDataProvider = chainDataProvider;
    this.networkDataProvider = networkDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.executionClientDataProvider = executionClientDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);

    if (!chainDataProvider.isStoreAvailable()
        || syncProvider.isSyncing()
        || belowTargetPeerCount(request)
        || !requiredNodeDataIsAvailable(request)
        || !executionClientDataProvider.isExecutionClientAvailable()) {
      request.respondWithCode(SC_SERVICE_UNAVAILABLE);
    } else {
      request.respondWithCode(SC_OK);
    }
  }

  private boolean belowTargetPeerCount(final RestApiRequest request) {
    return request
        .getOptionalQueryParameter(TARGET_PEER_COUNT_PARAMETER)
        .map(targetPeerCount -> networkDataProvider.countPeers() < targetPeerCount)
        .orElse(false);
  }

  private boolean requiredNodeDataIsAvailable(final RestApiRequest request) {
    return preparedProposersAreAvailable(request) && validatorRegistrationsAreAvailable(request);
  }

  private boolean preparedProposersAreAvailable(final RestApiRequest request) {
    final boolean requirePreparedProposers =
        request.getOptionalQueryParameter(REQUIRE_PREPARED_PROPOSERS_PARAMETER).orElse(false);
    if (!requirePreparedProposers) {
      return true;
    }
    return !nodeDataProvider.getPreparedProposerInfo().isEmpty();
  }

  private boolean validatorRegistrationsAreAvailable(final RestApiRequest request) {
    final boolean requireValidatorRegistrations =
        request.getOptionalQueryParameter(REQUIRE_VALIDATOR_REGISTRATIONS_PARAMETER).orElse(false);
    if (!requireValidatorRegistrations) {
      return true;
    }
    return !nodeDataProvider.getValidatorRegistrationInfo().isEmpty();
  }
}
