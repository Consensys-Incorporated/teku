/*
 * Copyright Consensys Software Inc., 2025
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.events.SyncState.SYNCING;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EPOCH;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuties;
import tech.pegasys.teku.ethereum.json.types.validator.InclusionListDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;

@TestSpecContext(milestone = {SpecMilestone.HEZE})
public class GetInclusionListCommitteeDutiesTest extends AbstractMigratedBeaconHandlerTest {

  private final InclusionListDuties duties =
      new InclusionListDuties(
          false,
          Bytes32.fromHexString("0x1234"),
          List.of(
              getInclusionListCommitteeDuty(2, spec.computeStartSlotAtEpoch(UInt64.valueOf(100)))));

  @BeforeEach
  void setup() {
    setHandler(new GetInclusionListCommitteeDuties(syncDataProvider, validatorDataProvider));
  }

  @Test
  public void shouldGetInclusionListCommitteeDuties() throws Exception {
    request.setPathParameter(EPOCH, "100");
    request.setRequestBody(List.of(UInt64.ONE));

    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(validatorDataProvider.getInclusionListDuties(eq(UInt64.valueOf(100)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(duties)));

    handler.handleRequest(request);

    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    Assertions.assertThat(request.getResponseBody()).isEqualTo(duties);
  }

  @Test
  void shouldRespondSyncing() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SYNCING);
    handler.handleRequest(request);

    Assertions.assertThat(request.getResponseCode())
        .isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    Assertions.assertThat(request.getResponseBody()).isInstanceOf(HttpErrorResponse.class);
  }

  private InclusionListDuty getInclusionListCommitteeDuty(
      final int validatorIndex, final UInt64 slot) {
    return new InclusionListDuty(
        BLSTestUtil.randomPublicKey(1), UInt64.valueOf(validatorIndex), slot);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, duties);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetInclusionListCommitteeDutiesTest.class, "getInclusionListCommitteeDuties.json"),
            UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualToIgnoringWhitespace(expected);
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
