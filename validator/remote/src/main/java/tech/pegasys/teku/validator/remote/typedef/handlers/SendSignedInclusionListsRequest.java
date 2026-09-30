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

import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.withDataWrapper;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_INCLUSION_LIST;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.BeaconNodeApiErrorUtils;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SendSignedInclusionListsRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public SendSignedInclusionListsRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public List<SubmitDataError> submit(final List<SignedInclusionList> signedInclusionLists) {
    final List<SubmitDataError> failures = new ArrayList<>();
    for (int index = 0; index < signedInclusionLists.size(); index++) {
      final SignedInclusionList signedInclusionList = signedInclusionLists.get(index);
      final Optional<String> failure =
          postJson(
              SEND_SIGNED_INCLUSION_LIST,
              Map.of(),
              Map.of(),
              Map.of(
                  HEADER_CONSENSUS_VERSION,
                  spec.atSlot(signedInclusionList.getMessage().getSlot())
                      .getMilestone()
                      .name()
                      .toLowerCase(Locale.ROOT)),
              signedInclusionList,
              withDataWrapper(signedInclusionList.getSchema()),
              new ResponseHandler<String>()
                  .withHandler(
                      SC_BAD_REQUEST,
                      (request, response) ->
                          Optional.of(BeaconNodeApiErrorUtils.getErrorMessage(response)))
                  .withHandler(
                      SC_NOT_FOUND,
                      (request, response) -> {
                        throw new IllegalArgumentException(
                            "Inclusion list endpoint not found: " + request.url());
                      }));
      if (failure.isPresent()) {
        failures.add(new SubmitDataError(UInt64.valueOf(index), failure.get()));
      }
    }
    return failures;
  }
}
