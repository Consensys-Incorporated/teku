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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.validator.api.SubmitDataError;

class PostBlsToExecutionChangesTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  public void setup() {
    setSpec(TestSpecFactory.createMinimalCapella());
    setHandler(new PostBlsToExecutionChanges(nodeDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnSuccessWhenValidOperationIsSubmittedToThePool() throws Exception {
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    request.setRequestBody(List.of(blsToExecutionChange));
    when(chainDataProvider.getMilestoneAtHead()).thenReturn(SpecMilestone.CAPELLA);
    when(nodeDataProvider.postBlsToExecutionChanges(anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();

    verify(nodeDataProvider).postBlsToExecutionChanges(eq(List.of(blsToExecutionChange)));
  }

  @Test
  void shouldReturnBadRequestWhenInvalidOperationIsSubmittedToThePool() throws Exception {
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    request.setRequestBody(List.of(blsToExecutionChange));
    when(chainDataProvider.getMilestoneAtHead()).thenReturn(SpecMilestone.CAPELLA);
    when(nodeDataProvider.postBlsToExecutionChanges(List.of(blsToExecutionChange)))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(new SubmitDataError(UInt64.ZERO, "Operation invalid"))));

    handler.handleRequest(request);

    final ErrorListBadRequest expectedBody =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details",
            List.of(new SubmitDataError(UInt64.ZERO, "Operation invalid")));
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(expectedBody);
  }

  @Test
  void shouldReturnBadRequestWhenInvalidNumberOfOperationsSubmitted() throws Exception {
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    List<SignedBlsToExecutionChange> requestBody = Collections.nCopies(5001, blsToExecutionChange);

    request.setRequestBody(requestBody);
    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new HttpErrorResponse(
                SC_BAD_REQUEST,
                "A maximum of 5000 SignedBLSToExecutionChange objects can be submitted to the node's pool at one time"));
  }

  @Test
  void metadata_shouldHandle400() throws IOException {
    final List<SubmitDataError> errors =
        List.of(
            new SubmitDataError(UInt64.ZERO, "Darn"), new SubmitDataError(UInt64.ONE, "Incorrect"));
    final ErrorListBadRequest responseData =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    final String data = getResponseStringFromMetadata(handler, SC_BAD_REQUEST, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(PostAttestationTest.class, "errorListBadRequest.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }
}
