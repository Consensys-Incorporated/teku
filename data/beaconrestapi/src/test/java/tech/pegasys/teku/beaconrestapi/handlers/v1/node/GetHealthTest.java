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

package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import static jakarta.servlet.http.HttpServletResponse.SC_CONTINUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SYNCING_STATUS;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;

public class GetHealthTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setHandler(new GetHealth(syncDataProvider, chainDataProvider));
  }

  @Test
  public void shouldReturnSyncingStatusWhenSyncing() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_PARTIAL_CONTENT);
  }

  @Test
  void shouldReturnUnhealthyWhenRejectedExecutionsExist() throws Exception {
    rejectedExecutionCount = 1;
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
  }

  @Test
  public void shouldReturnSyncingStatusWhenStartingUp() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.START_UP);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnCustomSyncingStatusWhenSyncing() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    request.setOptionalQueryParameter(SYNCING_STATUS, "100");

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_CONTINUE);
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingWrongParam() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    request.setOptionalQueryParameter(SYNCING_STATUS, "a");
    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnDefaultSyncingStatusWhenSyncingMultipleParams() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    request.setOptionalQueryParameter(SYNCING_STATUS, "1,2");

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_PARTIAL_CONTENT);
  }

  @Test
  public void shouldReturnOkWhenInSyncAndReady() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldReturnUnavailableWhenStoreNotAvailable() throws Exception {
    when(chainDataProvider.isStoreAvailable()).thenReturn(false);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_SERVICE_UNAVAILABLE);
  }

  @ParameterizedTest(name = "syncingStatus: {0}")
  @ValueSource(ints = {98, 600})
  public void shouldThrowBadRequestWhenInvalidSyncingStatusOutsideBounds(final int syncingStatus) {
    when(chainDataProvider.isStoreAvailable()).thenReturn(true);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    request.setOptionalQueryParameter(SYNCING_STATUS, String.valueOf(syncingStatus));

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid syncing status code");
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
  void metadata_shouldHandle200() {
    verifyMetadataEmptyResponse(handler, SC_OK);
  }

  @Test
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @Test
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataEmptyResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
