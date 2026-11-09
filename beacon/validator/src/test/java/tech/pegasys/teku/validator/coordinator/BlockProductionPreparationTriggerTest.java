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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockProductionPreparationTriggerTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @SuppressWarnings("unchecked")
  private final Consumer<UInt64> blockProductionPreparator = mock(Consumer.class);

  private final FutureBlockProductionPreparationTrigger trigger =
      new FutureBlockProductionPreparationTrigger(
          recentChainData, asyncRunner, blockProductionPreparator);

  private final UInt64 currentSlot = UInt64.valueOf(10);
  private final UInt64 blockPreparationSlot = UInt64.valueOf(11);

  @Test
  void shouldNotCallPreparatorWhenNotInSync() {
    trigger.onSyncingStatusChanged(false);
    trigger.onFutureBlockProductionPreparationDue(currentSlot);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(blockProductionPreparator);
  }

  @Test
  void shouldCallPreparatorWhenInSyncAndProposerIsConnected() {
    when(recentChainData.isBlockProposerConnected(blockPreparationSlot))
        .thenReturn(SafeFuture.completedFuture(true));
    trigger.onSyncingStatusChanged(true);
    trigger.onFutureBlockProductionPreparationDue(currentSlot);
    asyncRunner.executeQueuedActions();
    verify(recentChainData).isBlockProposerConnected(blockPreparationSlot);
    verify(blockProductionPreparator).accept(blockPreparationSlot);
  }

  @Test
  void shouldNotCallPreparatorWhenInSyncAndProposerIsNotConnected() {
    when(recentChainData.isBlockProposerConnected(blockPreparationSlot))
        .thenReturn(SafeFuture.completedFuture(false));
    trigger.onSyncingStatusChanged(true);
    trigger.onFutureBlockProductionPreparationDue(currentSlot);
    asyncRunner.executeQueuedActions();
    verify(recentChainData).isBlockProposerConnected(blockPreparationSlot);
    verifyNoInteractions(blockProductionPreparator);
  }
}
