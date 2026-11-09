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

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_VALIDATOR_REQUIRED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.MoreObjects;
import io.javalin.http.Header;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetSyncing extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v1/node/syncing";

  private static final SerializableTypeDefinition<SyncStatusData> SYNC_RESPONSE_DATA_TYPE =
      SerializableTypeDefinition.object(SyncStatusData.class)
          .withField("head_slot", UINT64_TYPE, SyncStatusData::getCurrentSlot)
          .withField("sync_distance", UINT64_TYPE, SyncStatusData::getSlotsBehind)
          .withField("is_syncing", BOOLEAN_TYPE, SyncStatusData::isSyncing)
          .withOptionalField("is_optimistic", BOOLEAN_TYPE, SyncStatusData::isOptimistic)
          .withOptionalField("el_offline", BOOLEAN_TYPE, SyncStatusData::elOffline)
          .build();

  private static final SerializableTypeDefinition<SyncStatusData> SYNCING_RESPONSE_TYPE =
      SerializableTypeDefinition.object(SyncStatusData.class)
          .name("GetSyncingStatusResponse")
          .withField("data", SYNC_RESPONSE_DATA_TYPE, Function.identity())
          .build();

  private final SyncDataProvider syncProvider;
  private final ExecutionClientDataProvider executionClientDataProvider;

  public GetSyncing(final DataProvider provider) {
    this(provider.getSyncDataProvider(), provider.getExecutionClientDataProvider());
  }

  GetSyncing(
      final SyncDataProvider syncProvider,
      final ExecutionClientDataProvider executionClientDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getSyncingStatus")
            .summary("Get node syncing status")
            .description(
                "Requests the beacon node to describe if it's currently syncing or not, "
                    + "and if it is, what block it is up to.")
            .tags(TAG_NODE, TAG_VALIDATOR_REQUIRED)
            .response(SC_OK, "Request successful", SYNCING_RESPONSE_TYPE)
            .build());
    this.syncProvider = syncProvider;
    this.executionClientDataProvider = executionClientDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    request.respondOk(new SyncStatusData(syncProvider, executionClientDataProvider));
  }

  static class SyncStatusData {
    private final boolean isSyncing;
    private final Optional<Boolean> isOptimistic;
    private final Optional<Boolean> elOffline;
    private final UInt64 currentSlot;
    private final UInt64 slotsBehind;

    public SyncStatusData(
        final SyncDataProvider syncProvider,
        final ExecutionClientDataProvider executionClientDataProvider) {
      final SyncingStatus status = syncProvider.getSyncingStatus();
      final SyncState syncState = syncProvider.getCurrentSyncState();
      this.slotsBehind = calculateSlotsBehind(status, syncState);
      this.isSyncing = !slotsBehind.isZero();
      this.elOffline = Optional.of(!executionClientDataProvider.isExecutionClientAvailable());
      this.isOptimistic = Optional.of(syncState.isOptimistic());
      this.currentSlot = status.getCurrentSlot();
    }

    SyncStatusData(
        final boolean isSyncing,
        final Boolean isOptimistic,
        final Boolean elOffline,
        final int currentSlot,
        final int slotsBehind) {
      this.isSyncing = isSyncing;
      this.isOptimistic = Optional.ofNullable(isOptimistic);
      this.elOffline = Optional.ofNullable(elOffline);
      this.currentSlot = UInt64.valueOf(currentSlot);
      this.slotsBehind = UInt64.valueOf(slotsBehind);
    }

    public boolean isSyncing() {
      return isSyncing;
    }

    public Optional<Boolean> isOptimistic() {
      return isOptimistic;
    }

    public Optional<Boolean> elOffline() {
      return elOffline;
    }

    public UInt64 getCurrentSlot() {
      return currentSlot;
    }

    public UInt64 getSlotsBehind() {
      return slotsBehind;
    }

    private UInt64 calculateSlotsBehind(
        final SyncingStatus syncingStatus, final SyncState syncState) {
      if (!syncState.isInSync() && syncingStatus.getHighestSlot().isPresent()) {
        final UInt64 highestSlot = syncingStatus.getHighestSlot().get();
        return highestSlot.minusMinZero(syncingStatus.getCurrentSlot());
      }
      return UInt64.ZERO;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SyncStatusData that = (SyncStatusData) o;
      return isSyncing == that.isSyncing
          && Objects.equals(isOptimistic, that.isOptimistic)
          && Objects.equals(elOffline, that.elOffline)
          && Objects.equals(currentSlot, that.currentSlot)
          && Objects.equals(slotsBehind, that.slotsBehind);
    }

    @Override
    public int hashCode() {
      return Objects.hash(isSyncing, isOptimistic, elOffline, currentSlot, slotsBehind);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("isSyncing", isSyncing)
          .add("isOptimistic", isOptimistic)
          .add("elOffline", elOffline)
          .add("currentSlot", currentSlot)
          .add("slotsBehind", slotsBehind)
          .toString();
    }
  }
}
