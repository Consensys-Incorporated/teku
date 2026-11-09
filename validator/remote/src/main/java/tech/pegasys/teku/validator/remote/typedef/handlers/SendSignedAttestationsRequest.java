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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SEND_SIGNED_ATTESTATION_V2;
import static tech.pegasys.teku.validator.remote.typedef.FailureListResponse.getFailureListResponseResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.FailureListResponse;

public class SendSignedAttestationsRequest extends AbstractTypeDefRequest {

  private final boolean attestationsV2ApisEnabled;
  private final Spec spec;

  public SendSignedAttestationsRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean attestationsV2ApisEnabled,
      final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
    this.spec = spec;
  }

  public List<SubmitDataError> submit(final List<Attestation> attestations) {
    if (attestations.isEmpty()) {
      return Collections.emptyList();
    }
    // Use attestation v2 api post Electra only. This logic can be removed once we reach the Electra
    // milestone
    final SpecMilestone specMilestone =
        spec.atSlot(attestations.getFirst().getData().getSlot()).getMilestone();
    if (attestationsV2ApisEnabled || specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      return submitPostElectra(attestations, specMilestone);
    }
    return submitPreElectra(attestations);
  }

  private List<SubmitDataError> submitPreElectra(final List<Attestation> attestations) {
    return postJson(
            ValidatorApiMethod.SEND_SIGNED_ATTESTATION,
            Collections.emptyMap(),
            attestations,
            listOf(getJsonTypeDefinition(attestations)),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }

  private List<SubmitDataError> submitPostElectra(
      final List<Attestation> attestations, final SpecMilestone specMilestone) {
    return postJson(
            SEND_SIGNED_ATTESTATION_V2,
            emptyMap(),
            emptyMap(),
            Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name().toLowerCase(Locale.ROOT)),
            attestations,
            listOf(getJsonTypeDefinition(attestations)),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }

  @SuppressWarnings("unchecked")
  private DeserializableTypeDefinition<Attestation> getJsonTypeDefinition(
      final List<Attestation> attestations) {
    return (DeserializableTypeDefinition<Attestation>)
        attestations.getFirst().getSchema().getJsonTypeDefinition();
  }
}
