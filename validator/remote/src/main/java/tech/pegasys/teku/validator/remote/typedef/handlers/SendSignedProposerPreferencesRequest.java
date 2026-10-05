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

import static java.util.Collections.emptyMap;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_PROPOSER_PREFERENCES;
import static tech.pegasys.teku.validator.remote.typedef.FailureListResponse.getFailureListResponseResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.typedef.FailureListResponse;

public class SendSignedProposerPreferencesRequest extends AbstractTypeDefRequest {
  private final Spec spec;

  public SendSignedProposerPreferencesRequest(
      final Spec spec, final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
    this.spec = spec;
  }

  public List<SubmitDataError> submit(
      final List<SignedProposerPreferences> signedProposerPreferences) {
    if (signedProposerPreferences.isEmpty()) {
      return Collections.emptyList();
    }
    final UInt64 slot = signedProposerPreferences.getFirst().getMessage().getProposalSlot();
    final SpecMilestone milestone = spec.atSlot(slot).getMilestone();

    final DeserializableTypeDefinition<SignedProposerPreferences> typeDefinition =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getSignedProposerPreferencesSchema()
            .getJsonTypeDefinition();

    return postJson(
            SEND_SIGNED_PROPOSER_PREFERENCES,
            emptyMap(),
            emptyMap(),
            Map.of(HEADER_CONSENSUS_VERSION, milestone.lowerCaseName()),
            signedProposerPreferences,
            listOf(typeDefinition),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }
}
