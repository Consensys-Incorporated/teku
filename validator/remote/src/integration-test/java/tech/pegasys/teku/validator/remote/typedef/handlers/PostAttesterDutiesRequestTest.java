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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;

import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.http.RestApiConstants;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class PostAttesterDutiesRequestTest extends AbstractTypeDefRequestTestBase {

  private PostAttesterDutiesRequest request;
  private UInt64 epoch;
  private List<Integer> validatorIndices;

  @BeforeEach
  public void setup() {
    request = new PostAttesterDutiesRequest(mockWebServer.url("/"), okHttpClient);
    epoch = dataStructureUtil.randomEpoch();
    validatorIndices =
        List.of(
            dataStructureUtil.randomValidatorIndex().intValue(),
            dataStructureUtil.randomValidatorIndex().intValue());
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit(epoch, validatorIndices);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(
            ValidatorApiMethod.GET_ATTESTATION_DUTIES.getPath(
                Map.of(RestApiConstants.EPOCH, epoch.toString())));
    final String requestBody =
        JsonUtil.serialize(validatorIndices, DeserializableTypeDefinition.listOf(INTEGER_TYPE));
    assertThat(request.getBody().readUtf8()).isEqualTo(requestBody);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(epoch, validatorIndices))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle404() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));
    assertThat(request.submit(epoch, validatorIndices)).isEmpty();
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(epoch, validatorIndices))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
