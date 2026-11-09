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

import static tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof.BEACON_COMMITTEE_SELECTION_PROOF;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class BeaconCommitteeSelectionsRequest extends AbstractTypeDefRequest {

  public BeaconCommitteeSelectionsRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public Optional<List<BeaconCommitteeSelectionProof>> submit(
      final List<BeaconCommitteeSelectionProof> validatorsPartialProof) {
    return postJson(
        ValidatorApiMethod.BEACON_COMMITTEE_SELECTIONS,
        Collections.emptyMap(),
        validatorsPartialProof,
        listOf(BEACON_COMMITTEE_SELECTION_PROOF),
        new ResponseHandler<>(
                SharedApiTypes.withDataWrapper(
                    "BeaconCommitteeSelectionsResponse", listOf(BEACON_COMMITTEE_SELECTION_PROOF)))
            .withHandler(SC_NOT_IMPLEMENTED, (request, response) -> Optional.empty())
            .withHandler(SC_SERVICE_UNAVAILABLE, (request, response) -> Optional.empty()));
  }
}
