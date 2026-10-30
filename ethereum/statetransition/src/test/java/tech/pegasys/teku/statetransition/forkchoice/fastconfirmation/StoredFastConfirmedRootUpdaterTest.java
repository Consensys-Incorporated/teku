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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class StoredFastConfirmedRootUpdaterTest {
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StoreTransaction storeTransaction = mock(StoreTransaction.class);
  private final Checkpoint finalizedCheckpoint =
      new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x01"));
  private final StoredFastConfirmedRootUpdater updater =
      new StoredFastConfirmedRootUpdater(recentChainData);

  @BeforeEach
  void setUp() {
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(finalizedCheckpoint));
    when(recentChainData.startStoreTransaction()).thenReturn(storeTransaction);
    when(storeTransaction.commit()).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void shouldStoreConfirmedRootOnlyWhenItChanges() {
    final Bytes32 firstRoot = Bytes32.fromHexString("0x02");
    final Bytes32 secondRoot = Bytes32.fromHexString("0x03");

    updater.onFastConfirmation(firstRoot, UInt64.valueOf(9), UInt64.valueOf(10));
    updater.onFastConfirmation(firstRoot, UInt64.valueOf(9), UInt64.valueOf(11));
    updater.onFastConfirmation(secondRoot, UInt64.valueOf(11), UInt64.valueOf(12));

    verify(storeTransaction).setFastConfirmedRoot(firstRoot);
    verify(storeTransaction).setFastConfirmedRoot(secondRoot);
    verify(storeTransaction, times(2)).commit();
  }

  @Test
  void shouldNotStoreFinalizedRoot() {
    updater.onFastConfirmation(
        finalizedCheckpoint.getRoot(), UInt64.valueOf(8), UInt64.valueOf(10));

    verify(recentChainData, never()).startStoreTransaction();
  }
}
