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

package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class GetAllBlobSidecarsAtSlotTest extends AbstractMigratedBeaconHandlerTest {

  final List<UInt64> indices =
      List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3));

  @BeforeEach
  void setup() {
    setSpec(TestSpecFactory.createMinimalDeneb());
    setHandler(new GetAllBlobSidecarsAtSlot(chainDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, "1");
    request.setListQueryParameters(
        "indices", indices.stream().map(UInt64::toString).collect(Collectors.toList()));
  }

  @Test
  void shouldReturnAllBlobSidecarsAtSlot() throws JsonProcessingException {
    final List<BlobSidecar> nonCanonicalBlobSidecars = dataStructureUtil.randomBlobSidecars(4);
    when(chainDataProvider.getAllBlobSidecarsAtSlot(eq(UInt64.ONE), eq(indices)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(nonCanonicalBlobSidecars)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(nonCanonicalBlobSidecars);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws Exception {
    final List<BlobSidecar> nonCanonicalBlobSidecars = dataStructureUtil.randomBlobSidecars(4);

    final JsonNode data =
        JsonTestUtil.parseAsJsonNode(
            getResponseStringFromMetadata(handler, SC_OK, nonCanonicalBlobSidecars));
    final JsonNode expected =
        JsonTestUtil.parseAsJsonNode(
            Resources.toString(
                Resources.getResource(
                    GetAllBlobSidecarsAtSlotTest.class, "getAllBlobSidecarsAtSlot.json"),
                UTF_8));
    assertThat(data).isEqualTo(expected);
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
