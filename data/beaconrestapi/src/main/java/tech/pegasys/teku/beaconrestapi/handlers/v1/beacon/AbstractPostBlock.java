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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.infrastructure.restapi.endpoints.AsyncApiResponse;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public abstract class AbstractPostBlock extends RestApiEndpoint {

  protected final ValidatorDataProvider validatorDataProvider;
  protected final SyncDataProvider syncDataProvider;

  public AbstractPostBlock(
      final ValidatorDataProvider validatorDataProvider,
      final SyncDataProvider syncDataProvider,
      final EndpointMetadata metadata) {
    super(metadata);
    this.validatorDataProvider = validatorDataProvider;
    this.syncDataProvider = syncDataProvider;
  }

  protected AsyncApiResponse processSendSignedBlockResult(final SendSignedBlockResult result) {
    return result
        .getRejectionReason()
        .map(
            rejectionReason -> {
              if (result.isPublished()) {
                return AsyncApiResponse.respondWithCode(SC_ACCEPTED);
              }
              return AsyncApiResponse.respondWithError(SC_INTERNAL_SERVER_ERROR, rejectionReason);
            })
        .orElse(AsyncApiResponse.respondWithCode(SC_OK));
  }
}
