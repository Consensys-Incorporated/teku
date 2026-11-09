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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_VOLUNTARY_EXIT;

import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.exceptions.BadRequestException;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.validator.remote.apiclient.BeaconNodeApiErrorUtils;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PostVoluntaryExitRequest extends AbstractTypeDefRequest {
  public PostVoluntaryExitRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public void submit(final SignedVoluntaryExit voluntaryExit) throws BadRequestException {
    try {
      postJson(
          SEND_SIGNED_VOLUNTARY_EXIT,
          Map.of(),
          voluntaryExit,
          SignedVoluntaryExit.SSZ_SCHEMA.getJsonTypeDefinition(),
          new ResponseHandler<>()
              .withHandler(
                  SC_BAD_REQUEST,
                  (request, response) -> {
                    throw new IllegalArgumentException(
                        BeaconNodeApiErrorUtils.getErrorMessage(response));
                  }));
    } catch (final IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
