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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class RegisterValidatorsRequestTest extends AbstractTypeDefRequestTestBase {

  private RegisterValidatorsRequest request;
  private SszList<SignedValidatorRegistration> validatorRegistrations;

  @BeforeEach
  public void setup() {
    request = new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, true);
    validatorRegistrations = dataStructureUtil.randomSignedValidatorRegistrations(10);
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequestAsSsz() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    request.submit(validatorRegistrations);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.REGISTER_VALIDATOR.getPath(Collections.emptyMap()));
    final byte[] requestBody =
        ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA
            .sszSerialize(validatorRegistrations)
            .toArray();
    assertThat(request.getBody().readByteArray()).isEqualTo(requestBody);
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequestAsJson() throws Exception {
    request = new RegisterValidatorsRequest(mockWebServer.url("/"), okHttpClient, false);
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_OK));
    request.submit(validatorRegistrations);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(ValidatorApiMethod.REGISTER_VALIDATOR.getPath(Collections.emptyMap()));
    final String requestBody =
        JsonUtil.serialize(
            validatorRegistrations,
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition());
    assertThat(request.getBody().readUtf8()).isEqualTo(requestBody);
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(validatorRegistrations))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(validatorRegistrations))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
