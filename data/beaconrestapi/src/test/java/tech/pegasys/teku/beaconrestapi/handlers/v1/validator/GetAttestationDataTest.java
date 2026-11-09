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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

class GetAttestationDataTest extends AbstractMigratedBeaconHandlerTest {
  private final AttestationData attestationData = dataStructureUtil.randomAttestationData();

  @BeforeEach
  void setUp() {
    setHandler(new GetAttestationData(validatorDataProvider, spec));
  }

  @Test
  void shouldReturnAttestationDataInformation() throws Exception {
    request.setQueryParameter(SLOT, "1");
    request.setOptionalQueryParameter(COMMITTEE_INDEX, "1");
    when(validatorDataProvider.createAttestationDataAtSlot(ONE, 1))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestationData)));

    handler.handleRequest(request);
    final Object responseBody = request.getResponseBody();

    assertThat(responseBody).isEqualTo(attestationData);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
  }

  @Test
  void shouldFail_whenCommitteeIndexIsMissingPreElectra() throws JsonProcessingException {
    request.setQueryParameter(SLOT, "1");
    when(validatorDataProvider.createAttestationDataAtSlot(ONE, 1))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestationData)));
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  void shouldIgnoreNonZeroCommitteeIndexPostElectra() throws Exception {
    final var electraSpec = TestSpecFactory.createMinimalElectra();
    setHandler(new GetAttestationData(validatorDataProvider, electraSpec));
    request.setQueryParameter(SLOT, "1");
    request.setOptionalQueryParameter(COMMITTEE_INDEX, "4");
    when(validatorDataProvider.createAttestationDataAtSlot(ONE, 0))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestationData)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(attestationData);
  }

  @Test
  void shouldAcceptMissingCommitteeIndexPostElectra() throws Exception {
    final var electraSpec = TestSpecFactory.createMinimalElectra();
    setHandler(new GetAttestationData(validatorDataProvider, electraSpec));
    request.setQueryParameter(SLOT, "1");
    when(validatorDataProvider.createAttestationDataAtSlot(ONE, 0))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestationData)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(attestationData);
  }

  public static Stream<Arguments> validateParameters() {
    return Stream.of(
        // valid to have a committee index inside bounds
        Arguments.of(SpecMilestone.DENEB, Optional.of(ONE), Optional.empty()),
        Arguments.of(SpecMilestone.ELECTRA, Optional.of(ZERO), Optional.empty()),
        Arguments.of(SpecMilestone.FULU, Optional.of(ZERO), Optional.empty()),
        Arguments.of(SpecMilestone.GLOAS, Optional.of(ZERO), Optional.empty()),
        Arguments.of(SpecMilestone.GLOAS, Optional.of(ONE), Optional.empty()),
        Arguments.of(SpecMilestone.GLOAS, Optional.empty(), Optional.empty()),
        // post-electra: committee_index is optional and any value is accepted (ignored)
        Arguments.of(SpecMilestone.ELECTRA, Optional.empty(), Optional.empty()),
        Arguments.of(SpecMilestone.FULU, Optional.empty(), Optional.empty()),
        Arguments.of(SpecMilestone.ELECTRA, Optional.of(ONE), Optional.empty()),
        Arguments.of(SpecMilestone.FULU, Optional.of(ONE), Optional.empty()),
        Arguments.of(SpecMilestone.ELECTRA, Optional.of(UInt64.valueOf(4)), Optional.empty()),
        Arguments.of(SpecMilestone.FULU, Optional.of(UInt64.valueOf(4)), Optional.empty()),
        // must be set before electra
        Arguments.of(SpecMilestone.DENEB, Optional.empty(), Optional.of("must be set")),
        // in gloas must be 0 or 1 if set
        Arguments.of(
            SpecMilestone.GLOAS, Optional.of(UInt64.valueOf(2)), Optional.of("less than 2")));
  }

  @ParameterizedTest(name = "spec={0}, committee={1} - failure={2}")
  @MethodSource("validateParameters")
  void validateInputs(
      final SpecMilestone milestone,
      final Optional<UInt64> committeeIndex,
      final Optional<String> messageFragment)
      throws JsonProcessingException {
    final boolean validation = GetAttestationData.validate(request, milestone, committeeIndex);
    if (messageFragment.isEmpty()) {
      assertThat(validation).isTrue();
    } else {
      assertThat(request.getResponseBody().toString()).contains(messageFragment.get());
    }
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, attestationData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetAttestationDataTest.class, "getAttestationData.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
