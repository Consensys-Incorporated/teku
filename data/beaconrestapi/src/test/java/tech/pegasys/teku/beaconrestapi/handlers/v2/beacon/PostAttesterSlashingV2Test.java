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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
class PostAttesterSlashingV2Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    setSpec(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
    setHandler(new PostAttesterSlashingV2(nodeDataProvider, new SchemaDefinitionCache(spec)));
  }

  @TestTemplate
  void shouldBeAbleToSubmitSlashing() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    request.setRequestBody(slashing);

    when(nodeDataProvider.postAttesterSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @TestTemplate
  void shouldReturnBadRequestIfAttesterSlashingIsInvalid() throws Exception {
    final AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    request.setRequestBody(slashing);

    when(nodeDataProvider.postAttesterSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Nope")));

    handler.handleRequest(request);

    final HttpErrorResponse expectedBody = new HttpErrorResponse(SC_BAD_REQUEST, "Nope");
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expectedBody);
  }

  @TestTemplate
  void shouldReadRequestBody() throws IOException {
    final String data =
        Resources.toString(
            Resources.getResource(
                PostAttesterSlashingV2Test.class, "postAttesterSlashingRequestBody.json"),
            UTF_8);
    assertThat(
            getRequestBodyFromMetadata(
                handler, Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name()), data))
        .isInstanceOf(AttesterSlashing.class);
  }

  @TestTemplate
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @TestTemplate
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @TestTemplate
  void metadata_shouldHandle200() throws IOException {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }
}
