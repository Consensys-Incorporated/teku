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
import static tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET;

import java.util.Collections;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionData;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.remote.typedef.ResponseHandler;

public class SubscribeToBeaconCommitteeRequest extends AbstractTypeDefRequest {
  public SubscribeToBeaconCommitteeRequest(
      final HttpUrl baseEndpoint, final OkHttpClient okHttpClient) {
    super(baseEndpoint, okHttpClient);
  }

  public void submit(final List<CommitteeSubscriptionRequest> subscriptions) {
    final List<CommitteeSubscriptionData> request =
        subscriptions.stream().map(CommitteeSubscriptionData::create).toList();
    postJson(
        SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET,
        Collections.emptyMap(),
        request,
        listOf(CommitteeSubscriptionData.SSZ_DATA),
        new ResponseHandler<>());
  }
}
