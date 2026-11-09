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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.beaconrestapi.schema.ErrorListBadRequest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class PostSyncCommitteesTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalAltair());
    setHandler(new PostSyncCommittees(validatorDataProvider));
  }

  @Test
  void shouldBeAbleToSubmitSyncCommittees() throws Exception {
    request.setRequestBody(List.of(dataStructureUtil.randomSyncCommitteeMessage()));
    when(validatorDataProvider.submitCommitteeSignatures(any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();
  }

  @Test
  void shouldReportInvalidSyncCommittees() throws Exception {
    final List<SubmitDataError> errors = List.of(new SubmitDataError(UInt64.ZERO, "Darn"));
    final ErrorListBadRequest response =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    request.setRequestBody(List.of(dataStructureUtil.randomSyncCommitteeMessage()));
    when(validatorDataProvider.submitCommitteeSignatures(any()))
        .thenReturn(SafeFuture.completedFuture(errors));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
    assertThat(request.getResponseBody()).isEqualTo(response);
  }

  @Test
  void metadata_shouldHandle400_errorResponse() throws IOException {
    final List<SubmitDataError> errors =
        List.of(
            new SubmitDataError(UInt64.ZERO, "Darn"), new SubmitDataError(UInt64.ONE, "Incorrect"));
    final ErrorListBadRequest responseData =
        new ErrorListBadRequest(
            "Some items failed to publish, refer to errors for details", errors);

    final String data = getResponseStringFromMetadata(handler, SC_BAD_REQUEST, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(PostSyncCommitteesTest.class, "postSyncCommittees.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
