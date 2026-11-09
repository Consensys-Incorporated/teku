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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostPrepareBeaconProposer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.validator.BeaconPreparableProposer;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class PostPrepareBeaconProposerTest extends AbstractDataBackedRestAPIIntegrationTest {
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setup() {
    startRestAPIAtGenesis(SpecMilestone.BELLATRIX);
    dataStructureUtil = new DataStructureUtil(spec);
  }

  @Test
  void shouldReturnOk() throws IOException {
    final List<BeaconPreparableProposer> request =
        dataStructureUtil.randomBeaconPreparableProposers(2);
    when(validatorApiChannel.prepareBeaconProposer(request)).thenReturn(SafeFuture.COMPLETE);

    try (Response response =
        post(
            PostPrepareBeaconProposer.ROUTE,
            JsonUtil.serialize(
                request, DeserializableTypeDefinition.listOf(BeaconPreparableProposer.SSZ_DATA)))) {

      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"[{}]", "{}", "invalid"})
  void shouldReturnBadRequest(final String body) throws IOException {
    when(validatorApiChannel.prepareBeaconProposer(any())).thenReturn(SafeFuture.COMPLETE);
    try (Response response = post(PostPrepareBeaconProposer.ROUTE, body)) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldHandleEmptyRequest() throws IOException {
    when(validatorApiChannel.prepareBeaconProposer(any())).thenReturn(SafeFuture.COMPLETE);
    try (Response response = post(PostPrepareBeaconProposer.ROUTE, "[]")) {
      assertThat(response.code()).isEqualTo(SC_OK);
    }
  }
}
