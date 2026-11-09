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

package tech.pegasys.teku.beaconrestapi.v1.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.node.GetSyncing;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetSyncingIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetSyncStatusWhenSyncing() throws IOException {
    startRestAPIAtGenesis();
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(true, 1, 10, 15));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    verifyData(10, 5, true, false);
  }

  @Test
  public void shouldGetSyncStatusWhenNotSyncing() throws IOException {
    startRestAPIAtGenesis();
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 6, 11, 16));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    verifyData(11, 0, false, false);
  }

  @Test
  public void shouldGetSyncStatusWhenElOffline() throws IOException {
    startRestAPIAtGenesis();
    when(syncService.getSyncStatus()).thenReturn(getSyncStatus(false, 1, 10, 15));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.SYNCING);
    // update EL availability
    dataProvider.getExecutionClientDataProvider().onAvailabilityUpdated(false);
    verifyData(10, 5, true, true);
  }

  private void verifyData(
      final int headSlot, final int syncDistance, final boolean isSyncing, final boolean elOffline)
      throws IOException {
    final Response response = getResponse(GetSyncing.ROUTE);
    assertThat(response.code()).isEqualTo(SC_OK);
    final JsonNode data = getResponseData(response);

    assertThat(data.get("head_slot").asInt()).isEqualTo(headSlot);
    assertThat(data.get("sync_distance").asInt()).isEqualTo(syncDistance);
    assertThat(data.get("is_syncing").asBoolean()).isEqualTo(isSyncing);
    assertThat(data.get("el_offline").asBoolean()).isEqualTo(elOffline);
  }

  private SyncingStatus getSyncStatus(
      final boolean isSyncing,
      final long startSlot,
      final long currentSlot,
      final long highestSlot) {
    return new SyncingStatus(
        isSyncing,
        UInt64.valueOf(currentSlot),
        UInt64.valueOf(startSlot),
        UInt64.valueOf(highestSlot));
  }
}
