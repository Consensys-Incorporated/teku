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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class PrepareBeaconProposersRequestTest extends AbstractTypeDefRequestTestBase {
  private PrepareBeaconProposersRequest request;
  private List<BeaconPreparableProposer> beaconPreparableProposers;

  @BeforeEach
  public void setup() {
    request = new PrepareBeaconProposersRequest(mockWebServer.url("/"), okHttpClient);
    beaconPreparableProposers =
        List.of(
            dataStructureUtil.randomBeaconPreparableProposer(),
            dataStructureUtil.randomBeaconPreparableProposer());
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    request.submit(beaconPreparableProposers);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.PREPARE_BEACON_PROPOSER.getPath(Collections.emptyMap()));
    final String requestBody =
        JsonUtil.serialize(
            beaconPreparableProposers,
            DeserializableTypeDefinition.listOf(BeaconPreparableProposer.SSZ_DATA));
    assertThat(request.getBody().readUtf8()).isEqualTo(requestBody);
  }

  @TestTemplate
  public void prepareBeaconProposer_noRequestWhenEmptyList() {
    request.submit(List.of());
    assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(beaconPreparableProposers))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(beaconPreparableProposers))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
