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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;

public interface V4FinalizedStateStorageLogic<S> {
  Optional<BeaconState> getLatestAvailableFinalizedState(
      KvStoreAccessor db, S schema, UInt64 maxSlot);

  Optional<UInt64> getEarliestAvailableFinalizedStateSlot(KvStoreAccessor db, S schema);

  FinalizedStateUpdater<S> updater();

  @MustBeClosed
  Stream<UInt64> streamFinalizedStateSlots(
      KvStoreAccessor db, final S schema, UInt64 startSlot, UInt64 endSlot);

  interface FinalizedStateUpdater<S> {
    void addFinalizedState(
        KvStoreAccessor db, KvStoreTransaction transaction, S schema, BeaconState state);

    void addReconstructedFinalizedState(
        KvStoreAccessor db, KvStoreTransaction transaction, S schema, BeaconState state);

    void deleteFinalizedState(KvStoreTransaction transaction, S schema, UInt64 slot);

    void commit();
  }
}
