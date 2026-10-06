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

import static tech.pegasys.teku.ethereum.json.types.validator.ProposerDutiesBuilder.PROPOSER_DUTIES_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.GET_PROPOSER_DUTIES_V2;

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class GetProposerDutiesV2Request extends AbstractTypeDefRequest {
  public GetProposerDutiesV2Request(final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<ProposerDuties> submit(final UInt64 epoch) {
    return get(
        GET_PROPOSER_DUTIES_V2,
        Map.of(EPOCH, epoch.toString()),
        Map.of(),
        new ResponseHandler<>(PROPOSER_DUTIES_TYPE));
  }
}
