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

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.validator.remote.typedef.FailureListResponse.getFailureListResponseResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.FailureListResponse;

public class SendAggregateAndProofsRequest extends AbstractTypeDefRequest {

  private final boolean attestationsV2ApisEnabled;
  private final Spec spec;

  public SendAggregateAndProofsRequest(
      final HttpUrl baseEndpoint,
      final OkHttpClient okHttpClient,
      final boolean attestationsV2ApisEnabled,
      final Spec spec) {
    super(baseEndpoint, okHttpClient);
    this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
    this.spec = spec;
  }

  public List<SubmitDataError> submit(final List<SignedAggregateAndProof> aggregateAndProofs) {
    if (aggregateAndProofs.isEmpty()) {
      return Collections.emptyList();
    }

    final SpecMilestone specMilestone =
        spec.atSlot(aggregateAndProofs.getFirst().getMessage().getAggregate().getData().getSlot())
            .getMilestone();

    if (attestationsV2ApisEnabled || specMilestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      return submitPostElectra(aggregateAndProofs, specMilestone);
    }
    return submitPreElectra(aggregateAndProofs);
  }

  private List<SubmitDataError> submitPreElectra(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return postJson(
            ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS,
            Collections.emptyMap(),
            aggregateAndProofs,
            listOf(getTypeDefinition(aggregateAndProofs)),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }

  private List<SubmitDataError> submitPostElectra(
      final List<SignedAggregateAndProof> aggregateAndProofs, final SpecMilestone specMilestone) {
    return postJson(
            ValidatorApiMethod.SEND_SIGNED_AGGREGATE_AND_PROOFS_V2,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Map.of(
                RestApiConstants.HEADER_CONSENSUS_VERSION,
                specMilestone.name().toLowerCase(Locale.ROOT)),
            aggregateAndProofs,
            listOf(getTypeDefinition(aggregateAndProofs)),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }

  private static DeserializableTypeDefinition<SignedAggregateAndProof> getTypeDefinition(
      final List<SignedAggregateAndProof> aggregateAndProofs) {
    return aggregateAndProofs.getFirst().getSchema().getJsonTypeDefinition();
  }
}
