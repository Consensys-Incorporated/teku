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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getRequestBodyFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostAttesterDutiesTest extends AbstractMigratedBeaconHandlerTest {
  final AttesterDuty duty =
      new AttesterDuty(dataStructureUtil.randomPublicKey(), 1, 2, 0, 2, 1, UInt64.ONE);
  final AttesterDuties attesterDuties =
      new AttesterDuties(false, dataStructureUtil.randomBytes32(), List.of(duty));

  @BeforeEach
  public void setup() {
    setHandler(new PostAttesterDuties(syncDataProvider, validatorDataProvider));
    request.setPathParameter("epoch", "1");
    request.setRequestBody(List.of(1));
  }

  @Test
  public void shouldReturnServiceUnavailableWhenResultIsEmpty() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(validatorDataProvider.getAttesterDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
    assertThat(request.getResponseBody())
        .isEqualTo(
            new HttpErrorResponse(
                SC_SERVICE_UNAVAILABLE,
                "Beacon node is currently syncing and not serving requests."));
  }

  @Test
  public void shouldGetAttesterDuties() throws JsonProcessingException {
    when(validatorDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(validatorDataProvider.getAttesterDuties(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attesterDuties)));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(attesterDuties);
  }

  @Test
  void shouldReadRequestBody() throws IOException {
    final String data = "[\"1\"]";
    assertThat(getRequestBodyFromMetadata(handler, data)).isEqualTo(List.of(1));
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
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, attesterDuties);
    final String expected =
        Resources.toString(
            Resources.getResource(PostAttesterDutiesTest.class, "postAttesterDuties.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
