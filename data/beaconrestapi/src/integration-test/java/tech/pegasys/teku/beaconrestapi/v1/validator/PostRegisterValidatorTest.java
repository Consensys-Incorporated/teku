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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostRegisterValidator;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostRegisterValidatorTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    dataStructureUtil = new DataStructureUtil(spec);
    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenAnswer(
            args -> {
              final Collection<BLSPublicKey> publicKeys = args.getArgument(0);
              final Map<BLSPublicKey, StateValidatorData> validatorStatuses =
                  publicKeys.stream()
                      .collect(
                          Collectors.toMap(
                              Function.identity(),
                              __ ->
                                  new StateValidatorData(
                                      dataStructureUtil.randomValidatorIndex(),
                                      dataStructureUtil.randomUInt64(),
                                      ValidatorStatus.active_ongoing,
                                      dataStructureUtil.randomValidator())));
              return SafeFuture.completedFuture(Optional.of(validatorStatuses));
            });
  }

  @Test
  void shouldReturnOk() throws IOException {
    final SszList<SignedValidatorRegistration> request =
        dataStructureUtil.randomSignedValidatorRegistrations(10);
    when(validatorApiChannel.registerValidators(request)).thenReturn(SafeFuture.COMPLETE);

    try (final Response response =
        post(
            PostRegisterValidator.ROUTE,
            JsonUtil.serialize(
                request, SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition()))) {

      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }

  @Test
  void shouldReturnServerErrorWhenThereIsAnExceptionWhileRegistering() throws IOException {
    final SszList<SignedValidatorRegistration> request =
        dataStructureUtil.randomSignedValidatorRegistrations(10);
    when(validatorApiChannel.registerValidators(request))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    try (final Response response =
        post(
            PostRegisterValidator.ROUTE,
            JsonUtil.serialize(
                request, SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition()))) {

      assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.body().string()).isEqualTo("{\"code\":500,\"message\":\"oopsy\"}");
    }
  }

  @Test
  void shouldReturnServerErrorWhenThereIsAnExceptionWhileGettingStatuses() throws IOException {
    final SszList<SignedValidatorRegistration> request =
        dataStructureUtil.randomSignedValidatorRegistrations(10);
    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    try (final Response response =
        post(
            PostRegisterValidator.ROUTE,
            JsonUtil.serialize(
                request, SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA.getJsonTypeDefinition()))) {

      assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.body().string()).isEqualTo("{\"code\":500,\"message\":\"oopsy\"}");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"[{}]", "{}", "invalid"})
  void shouldReturnBadRequest(final String body) throws IOException {
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);
    try (final Response response = post(PostRegisterValidator.ROUTE, body)) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldHandleEmptyRequest() throws IOException {
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);
    try (final Response response = post(PostRegisterValidator.ROUTE, "[]")) {
      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }
}
