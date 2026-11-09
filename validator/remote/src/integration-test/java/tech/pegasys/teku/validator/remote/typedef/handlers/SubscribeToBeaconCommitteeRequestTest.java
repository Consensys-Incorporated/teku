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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;

import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class SubscribeToBeaconCommitteeRequestTest extends AbstractTypeDefRequestTestBase {
  private SubscribeToBeaconCommitteeRequest request;
  private List<CommitteeSubscriptionRequest> subscriptions;
  final int committeeIndex1 = 1;
  final int validatorIndex1 = 6;
  final UInt64 committeesAtSlot1 = UInt64.valueOf(10);
  final UInt64 slot1 = UInt64.valueOf(15);
  final boolean aggregator1 = true;

  final int committeeIndex2 = 2;
  final int validatorIndex2 = 7;
  final UInt64 committeesAtSlot2 = UInt64.valueOf(11);
  final UInt64 slot2 = UInt64.valueOf(16);
  final boolean aggregator2 = false;

  @BeforeEach
  public void setup() {
    request = new SubscribeToBeaconCommitteeRequest(mockWebServer.url("/"), okHttpClient);
    subscriptions =
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex1, committeeIndex1, committeesAtSlot1, slot1, aggregator1),
            new CommitteeSubscriptionRequest(
                validatorIndex2, committeeIndex2, committeesAtSlot2, slot2, aggregator2));
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit(subscriptions);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(
            ValidatorApiMethod.SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET.getPath(
                Collections.emptyMap()));
    final String expectedBody =
        "[{\"validator_index\":\"6\",\"committee_index\":\"1\",\"committees_at_slot\":\"10\",\"slot\":\"15\",\"is_aggregator\":true},"
            + "{\"validator_index\":\"7\",\"committee_index\":\"2\",\"committees_at_slot\":\"11\",\"slot\":\"16\",\"is_aggregator\":false}]";
    assertThat(request.getBody().readUtf8()).isEqualTo(expectedBody);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(subscriptions))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(subscriptions))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
