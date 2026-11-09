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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostAttesterSlashingV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAttesterSlashingV2IntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    checkEmptyBodyToRoute(PostAttesterSlashingV2.ROUTE, SC_BAD_REQUEST);
  }

  @TestTemplate
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            "{\"foo\": \"bar\"}",
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));
    assertThat(response.code()).isEqualTo(400);
  }

  @TestTemplate
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();

    doThrow(new RuntimeException()).when(attesterSlashingPool).addLocal(slashing);

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            JsonUtil.serialize(slashing, slashing.getSchema().getJsonTypeDefinition()),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));
    assertThat(response.code()).isEqualTo(500);
  }

  @TestTemplate
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();

    when(attesterSlashingPool.addLocal(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            JsonUtil.serialize(slashing, slashing.getSchema().getJsonTypeDefinition()),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    verify(attesterSlashingPool).addLocal(slashing);

    assertThat(response.code()).isEqualTo(200);
  }

  @TestTemplate
  void shouldFailWhenMissingConsensusHeader() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            JsonUtil.serialize(slashing, slashing.getSchema().getJsonTypeDefinition()));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("Missing required header value for (%s)", HEADER_CONSENSUS_VERSION);
  }

  @TestTemplate
  void shouldFailWhenBadConsensusHeaderValue() throws Exception {

    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();

    when(attesterSlashingPool.addLocal(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final String badConsensusHeaderValue = "NonExistingMileStone";
    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            JsonUtil.serialize(slashing, slashing.getSchema().getJsonTypeDefinition()),
            Collections.emptyMap(),
            Optional.of(badConsensusHeaderValue));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo(
            String.format(
                "Invalid value for (%s) header: %s",
                HEADER_CONSENSUS_VERSION, badConsensusHeaderValue));
  }
}
