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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class StoredLatestCanonicalBlockUpdaterTest {
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  final StoreTransaction storeTransaction = mock(StoreTransaction.class);
  private StoredLatestCanonicalBlockUpdater updater;

  @Test
  void onSlot_shouldUpdateLatestCanonicalBlockRoot() {
    testSuccessWithSpec(TestSpecFactory.createMainnetDeneb());
  }

  @Test
  void onSlot_shouldNotUpdateLatestCanonicalBlockRoot2() {
    testSuccessWithSpec(TestSpecFactory.createMinimalDeneb());
  }

  void testSuccessWithSpec(final Spec spec) {
    final Bytes32 blockRoot = Bytes32.fromHexString("0x01");
    updater = new StoredLatestCanonicalBlockUpdater(recentChainData, spec);
    assertThat(updater.getSlotsPerEpoch()).isEqualTo(spec.getGenesisSpec().getSlotsPerEpoch());
    when(storeTransaction.commit()).thenReturn(SafeFuture.COMPLETE);
    when(recentChainData.startStoreTransaction()).thenReturn(storeTransaction);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(blockRoot));

    updater.onSlot(UInt64.valueOf(spec.getGenesisSpec().getSlotsPerEpoch()).increment());

    verify(storeTransaction).setLatestCanonicalBlockRoot(blockRoot);
  }

  @Test
  void onSlot_shouldNotUpdateLatestCanonicalBlockRoot() {
    final Spec spec = TestSpecFactory.createDefault();
    updater = new StoredLatestCanonicalBlockUpdater(recentChainData, spec);
    updater.onSlot(UInt64.valueOf(spec.getGenesisSpec().getSlotsPerEpoch()));

    verifyNoInteractions(recentChainData);
  }
}
