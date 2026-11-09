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

import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_SYNCING_STATUS;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.api.required.ValidatorRequiredApiTypes;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetSyncingStatusRequest extends AbstractTypeDefRequest {

  public GetSyncingStatusRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public SyncingStatus submit() {
    return get(
            GET_SYNCING_STATUS,
            new ResponseHandler<>(ValidatorRequiredApiTypes.SYNCING_STATUS_RESPONSE))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Empty response when querying the Beacon Node syncing status"));
  }
}
