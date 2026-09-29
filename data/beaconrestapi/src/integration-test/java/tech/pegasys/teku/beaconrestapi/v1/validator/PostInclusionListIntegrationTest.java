/*
 * Copyright Consensys Software Inc., 2026
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PostInclusionListIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private static final String PATH = "/eth/v1/validator/inclusion_list";

  private SignedInclusionList signedInclusionList;
  private String signedInclusionListJson;

  @BeforeEach
  void setup() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.HEZE);
    signedInclusionList = new DataStructureUtil(spec).randomSignedInclusionList();
    signedInclusionListJson =
        JsonUtil.serialize(
            signedInclusionList, signedInclusionList.getSchema().getJsonTypeDefinition());
  }

  @Test
  void shouldPublishWrappedSignedInclusionList() throws IOException {
    when(validatorApiChannel.sendSignedInclusionLists(List.of(signedInclusionList)))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    try (final Response response =
        post(PATH, "{\"data\":" + signedInclusionListJson + "}", Optional.of("heze"))) {
      assertThat(response.code()).isEqualTo(SC_OK);
    }
    verify(validatorApiChannel).sendSignedInclusionLists(List.of(signedInclusionList));
  }

  @Test
  void shouldRejectUnwrappedSignedInclusionList() throws IOException {
    try (final Response response = post(PATH, signedInclusionListJson, Optional.of("heze"))) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"data\":null}", "{\"data\":{}}"})
  void shouldRejectMissingOrInvalidData(final String body) throws IOException {
    try (final Response response = post(PATH, body, Optional.of("heze"))) {
      assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    }
    verifyNoInteractions(validatorApiChannel);
  }
}
