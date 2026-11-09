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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;

public class GetSyncingTest extends AbstractMigratedBeaconHandlerTest {

  @BeforeEach
  void setUp() {
    setHandler(new GetSyncing(syncDataProvider, executionClientDataProvider));
    when(executionClientDataProvider.isExecutionClientAvailable()).thenReturn(true);
  }

  @Test
  public void shouldGetSyncingStatusSyncing() throws Exception {
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 7, 10));

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(new GetSyncing.SyncStatusData(true, false, false, 7, 3));
  }

  @Test
  public void shouldGetSyncStatusInSync() throws Exception {
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(new GetSyncing.SyncStatusData(false, false, false, 10, 0));
  }

  @Test
  public void shouldGetSyncStatusInSyncWhenHeadIsOptimistic() throws Exception {
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 10));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.OPTIMISTIC_SYNCING);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(new GetSyncing.SyncStatusData(false, true, false, 10, 0));
  }

  @Test
  public void shouldGetElOffline() throws Exception {
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 11));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(executionClientDataProvider.isExecutionClientAvailable()).thenReturn(false);

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody())
        .isEqualTo(new GetSyncing.SyncStatusData(false, false, true, 10, 0));
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
  void metadata_shouldHandle200() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(
            handler, SC_OK, new GetSyncing.SyncStatusData(false, false, false, 10, 0));
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"head_slot\":\"10\",\"sync_distance\":\"0\",\"is_syncing\":false,\"is_optimistic\":false,\"el_offline\":false}}");
  }

  @Test
  void metadata_shouldHandle200WithoutOptionalFields() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(
            handler, SC_OK, new GetSyncing.SyncStatusData(false, null, null, 10, 0));
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"head_slot\":\"10\",\"sync_distance\":\"0\",\"is_syncing\":false}}");
  }

  @Test
  void metadata_shouldHandle200WithoutIsOptimistic() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(
            handler, SC_OK, new GetSyncing.SyncStatusData(false, null, false, 10, 0));
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"head_slot\":\"10\",\"sync_distance\":\"0\",\"is_syncing\":false,\"el_offline\":false}}");
  }

  @Test
  void metadata_shouldHandle200WithoutElOffline() throws JsonProcessingException {
    final String data =
        getResponseStringFromMetadata(
            handler, SC_OK, new GetSyncing.SyncStatusData(false, false, null, 10, 0));
    assertThat(data)
        .isEqualTo(
            "{\"data\":{\"head_slot\":\"10\",\"sync_distance\":\"0\",\"is_syncing\":false,\"is_optimistic\":false}}");
  }
}
