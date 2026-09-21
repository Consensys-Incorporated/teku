/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.BODY_INTEGER_LIST;
import static tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties.PTC_DUTIES_TYPE_DEFINITION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PTC_DUTIES;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.validator.PayloadTimelinessCommitteeDuties;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class PostPayloadTimelinessCommitteeDutiesRequest extends AbstractTypeDefRequest {
  public PostPayloadTimelinessCommitteeDutiesRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<PayloadTimelinessCommitteeDuties> submit(
      final UInt64 epoch, final Collection<Integer> validatorIndices) {
    return postJson(
        GET_PTC_DUTIES,
        Map.of(RestApiConstants.EPOCH, epoch.toString()),
        validatorIndices.stream().toList(),
        BODY_INTEGER_LIST,
        new ResponseHandler<>(PTC_DUTIES_TYPE_DEFINITION));
  }
}
