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
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.FailureListResponse;

public class SendSyncCommitteeMessagesRequest extends AbstractTypeDefRequest {
  public SendSyncCommitteeMessagesRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public List<SubmitDataError> submit(final List<SyncCommitteeMessage> syncCommitteeMessages) {
    if (syncCommitteeMessages.isEmpty()) {
      return Collections.emptyList();
    }
    final DeserializableTypeDefinition<SyncCommitteeMessage> jsonTypeDefinition =
        syncCommitteeMessages.getFirst().getSchema().getJsonTypeDefinition();
    return postJson(
            ValidatorApiMethod.SEND_SYNC_COMMITTEE_MESSAGES,
            Collections.emptyMap(),
            syncCommitteeMessages,
            listOf(jsonTypeDefinition),
            getFailureListResponseResponseHandler())
        .map(FailureListResponse::failures)
        .orElse(Collections.emptyList());
  }
}
