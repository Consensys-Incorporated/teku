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

package tech.pegasys.teku.builder.rest.handlers;

import static tech.pegasys.teku.builder.rest.BuilderApiMethod.SUBMIT_BUILDER_PREFERENCES;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.builder.rest.ResponseHandler;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesRequest;
import tech.pegasys.teku.spec.schemas.ApiSchemas;

public class SubmitBuilderPreferencesRequest extends AbstractBuilderRequest {

  private final Spec spec;

  public SubmitBuilderPreferencesRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient httpClient) {
    super(baseEndpoint, httpClient);
    this.spec = spec;
  }

  public void submit(
      final BLSPublicKey validatorPubkey,
      final BuilderPreferencesRequest builderPreferencesRequest) {
    postJson(
        SUBMIT_BUILDER_PREFERENCES,
        Map.of("validator_pubkey", validatorPubkey.toString()),
        Map.of(
            HEADER_CONSENSUS_VERSION,
            spec.atSlot(builderPreferencesRequest.getAuth().getMessage().getSlot())
                .getMilestone()
                .lowerCaseName()),
        builderPreferencesRequest,
        ApiSchemas.BUILDER_PREFERENCES_REQUEST_SCHEMA.getJsonTypeDefinition(),
        ResponseHandler.voidHandler(),
        Optional.empty());
  }
}
