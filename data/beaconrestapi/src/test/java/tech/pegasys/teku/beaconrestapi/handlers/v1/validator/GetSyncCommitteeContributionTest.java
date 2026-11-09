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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class GetSyncCommitteeContributionTest extends AbstractMigratedBeaconHandlerTest {
  @SuppressWarnings("HidingField")
  private final Spec spec = TestSpecFactory.createMinimalAltair();

  @SuppressWarnings("HidingField")
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  void setUp() {
    setHandler(new GetSyncCommitteeContribution(validatorDataProvider, schemaDefinitionCache));
  }

  @Test
  void shouldReturnSyncCommitteeContributionInformation() throws Exception {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    request.setQueryParameter("slot", "1");
    request.setQueryParameter("subcommittee_index", "1");
    request.setQueryParameter("beacon_block_root", beaconBlockRoot.toHexString());

    final SyncCommitteeContribution syncCommitteeContribution =
        dataStructureUtil.randomSyncCommitteeContribution(UInt64.ONE);
    when(validatorDataProvider.createSyncCommitteeContribution(
            eq(UInt64.valueOf(1)), eq(1), eq(beaconBlockRoot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(syncCommitteeContribution)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(syncCommitteeContribution);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
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
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final SyncCommitteeContribution syncCommitteeContribution =
        dataStructureUtil.randomSyncCommitteeContribution(UInt64.ONE);

    final String data = getResponseStringFromMetadata(handler, SC_OK, syncCommitteeContribution);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetSyncCommitteeContributionTest.class, "getSyncCommitteeContribution.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}
