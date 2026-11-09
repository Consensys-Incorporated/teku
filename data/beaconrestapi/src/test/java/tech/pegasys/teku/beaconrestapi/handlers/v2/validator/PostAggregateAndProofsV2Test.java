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

package tech.pegasys.teku.beaconrestapi.handlers.v2.validator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectra;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.validator.api.SubmitDataError;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAggregateAndProofsV2Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;

  @BeforeEach
  public void beforeEach(final TestSpecInvocationContextProvider.SpecContext specContext) {
    setSpec(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
    setHandler(
        new PostAggregateAndProofsV2(validatorDataProvider, spec, new SchemaDefinitionCache(spec)));
  }

  @TestTemplate
  public void shouldReturnSuccessWhenSendAggregateAndProofSucceeds() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    request.setRequestBody(List.of(signedAggregateAndProof));

    when(validatorDataProvider.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @TestTemplate
  public void shouldReturnBadRequestWhenErrorsReturned() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    request.setRequestBody(List.of(signedAggregateAndProof));

    final List<SubmitDataError> errors = List.of(new SubmitDataError(UInt64.ZERO, "Failed"));
    when(validatorDataProvider.sendAggregateAndProofs(anyList()))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);

    final HttpErrorResponse expected =
        new HttpErrorResponse(
            SC_BAD_REQUEST, "Some items failed to publish, refer to errors for details");
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expected);
  }

  @TestTemplate
  void shouldReadRequestBody() throws IOException {
    final String data = getExpectedResponseAsJson(specMilestone);
    final Object requestBody =
        getRequestBodyFromMetadata(
            handler, Map.of(HEADER_CONSENSUS_VERSION, specMilestone.name()), data);
    assertThat(requestBody).isInstanceOf(List.class);
    assertThat(((List<?>) requestBody).get(0)).isInstanceOf(SignedAggregateAndProof.class);
    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA)) {
      assertThat(
              ((SignedAggregateAndProof) ((List<?>) requestBody).get(0))
                  .getMessage()
                  .getAggregate())
          .isInstanceOf(AttestationElectra.class);
    } else {
      assertThat(
              ((SignedAggregateAndProof) ((List<?>) requestBody).get(0))
                  .getMessage()
                  .getAggregate())
          .isInstanceOf(AttestationPhase0.class);
    }
  }

  @TestTemplate
  void metadata_shouldHandle400() throws IOException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @TestTemplate
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @TestTemplate
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  private String getExpectedResponseAsJson(final SpecMilestone specMilestone) throws IOException {
    final String fileName =
        String.format("postAggregateAndProofsRequestBody%s.json", specMilestone.name());
    return Resources.toString(
        Resources.getResource(PostAggregateAndProofsV2Test.class, fileName), UTF_8);
  }

  @TestTemplate
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @TestTemplate
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
