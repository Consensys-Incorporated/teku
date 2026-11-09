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

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorDataBuilder.STATE_VALIDATORS_RESPONSE_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_VALIDATORS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetStateValidatorsRequest extends AbstractTypeDefRequest {
  public GetStateValidatorsRequest(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<ObjectAndMetaData<List<StateValidatorData>>> submit(
      final List<String> validatorIds) {
    return get(
        GET_VALIDATORS,
        emptyMap(),
        emptyMap(),
        Map.of(PARAM_ID, String.join(",", validatorIds)),
        emptyMap(),
        new ResponseHandler<>(STATE_VALIDATORS_RESPONSE_TYPE));
  }
}
