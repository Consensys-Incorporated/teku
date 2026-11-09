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
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorDataBuilder.STATE_VALIDATORS_RESPONSE_TYPE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.PARAM_ID;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class GetStateValidatorsRequestTest extends AbstractTypeDefRequestTestBase {

  private GetStateValidatorsRequest request;
  private List<String> validatorIds;

  @BeforeEach
  public void setup() {
    request = new GetStateValidatorsRequest(mockWebServer.url("/"), okHttpClient);
    validatorIds =
        List.of(
            dataStructureUtil.randomPublicKey().toHexString(),
            dataStructureUtil.randomPublicKey().toHexString());
  }

  @TestTemplate
  public void getStateValidatorsRequest_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit(validatorIds);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.GET_VALIDATORS.getPath(Collections.emptyMap()));
    assertThat(request.getRequestUrl().queryParameter(PARAM_ID))
        .isEqualTo(String.join(",", validatorIds));
  }

  @TestTemplate
  public void shouldGetStateValidatorsData() throws JsonProcessingException {
    final List<StateValidatorData> expected =
        List.of(generateStateValidatorData(), generateStateValidatorData());
    final ObjectAndMetaData<List<StateValidatorData>> response =
        new ObjectAndMetaData<>(expected, specMilestone, false, true, false);

    final String body = serialize(response, STATE_VALIDATORS_RESPONSE_TYPE);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK).setBody(body));

    final Optional<ObjectAndMetaData<List<StateValidatorData>>> maybeStateValidatorsData =
        request.submit(validatorIds);
    assertThat(maybeStateValidatorsData).isPresent();
    assertThat(maybeStateValidatorsData.get().getData()).isEqualTo(expected);
  }

  @TestTemplate
  void handle400() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(validatorIds))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle404() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NOT_FOUND));
    assertThat(request.submit(validatorIds)).isEmpty();
  }

  @TestTemplate
  void handle500() {
    assumeThat(specMilestone).isGreaterThan(PHASE0);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(validatorIds))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }

  private StateValidatorData generateStateValidatorData() {
    final long index = dataStructureUtil.randomLong();
    final Validator validator =
        new Validator(
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);
    return new StateValidatorData(
        UInt64.valueOf(index),
        dataStructureUtil.randomUInt64(),
        ValidatorStatus.active_ongoing,
        validator);
  }
}
