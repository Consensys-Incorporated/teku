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

package tech.pegasys.teku.beacon.sync.forward.multipeer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.SyncController.SYNC_AWAKE_INTERVAL;
import static tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChainTestUtil.chainWith;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncControllerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Sync sync = mock(Sync.class);
  private final SyncTargetSelector syncTargetSelector = mock(SyncTargetSelector.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Executor subscriberExecutor = mock(Executor.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final TargetChain targetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());

  private final SyncController syncController =
      new SyncController(
          eventThread, asyncRunner, subscriberExecutor, recentChainData, syncTargetSelector, sync);
  private static final UInt64 HEAD_SLOT = UInt64.valueOf(2338);

  @BeforeEach
  void setUp() {
    when(recentChainData.getHeadSlot()).thenReturn(HEAD_SLOT);
  }

  @Test
  void shouldNotBeSyncingInitially() {
    assertNotSyncing();
  }

  @Test
  void shouldStartFinalizedSyncWhenTargetChainsUpdatedWithSuitableFinalizedChain() {
    ignoreFuture(startFinalizedSync());

    verify(sync).syncToChain(targetChain);

    assertThat(syncController.isSyncActive()).isTrue();
    assertThat(syncController.getSyncStatus())
        .isEqualTo(
            new SyncingStatus(true, HEAD_SLOT, HEAD_SLOT, targetChain.getChainHead().getSlot()));
  }

  @Test
  void shouldNotBeSyncingWhenSyncCompletes() {
    final SafeFuture<SyncResult> syncResult = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    syncResult.complete(SyncResult.COMPLETE);

    assertNotSyncing();
  }

  @Test
  void shouldNotOverwriteCurrentSyncWhenChainsUpdatedButNoBetterChainAvailable() {
    ignoreFuture(startFinalizedSync());
    verify(sync).syncToChain(targetChain);

    // Updated but still the same best chain
    onTargetChainsUpdated();

    // Should not restart the sync
    verifyNoMoreInteractions(sync);
  }

  @Test
  void shouldSwitchSyncTargetWhenBetterChainAvailable() {
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    ignoreFuture(startFinalizedSync());
    verify(sync).syncToChain(targetChain);

    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(newTargetChain)));
    when(sync.syncToChain(newTargetChain)).thenReturn(new SafeFuture<>());
    onTargetChainsUpdated();

    verify(sync).syncToChain(newTargetChain);

    assertThat(syncController.isSyncActive()).isTrue();
    assertThat(syncController.getSyncStatus())
        .isEqualTo(
            new SyncingStatus(true, HEAD_SLOT, HEAD_SLOT, newTargetChain.getChainHead().getSlot()));
  }

  @Test
  void shouldPassFailedSyncsToSyncTargetSelector() {
    // When a sync fails, we want to stay in sync mode and switch to another target chain even
    // if it's not much ahead of our current head.

    final SafeFuture<SyncResult> syncFuture = startFinalizedSync();

    // First selection has no current sync
    verify(syncTargetSelector).selectSyncTarget(Optional.empty());

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());
    syncFuture.complete(SyncResult.FAILED);

    verify(syncTargetSelector)
        .selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    // Should restart sync to the chain even though it has the same target
    verify(sync, times(2)).syncToChain(targetChain);
  }

  @Test
  void shouldPassExceptionallyCompletedSyncsToSyncTargetSelector() {
    // When a sync fails, we want to stay in sync mode and switch to another target chain even
    // if it's not much ahead of our current head.

    final SafeFuture<SyncResult> syncFuture = startFinalizedSync();

    // First selection has no current sync
    verify(syncTargetSelector).selectSyncTarget(Optional.empty());

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());
    syncFuture.completeExceptionally(new RuntimeException());

    verify(syncTargetSelector)
        .selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    // Should restart sync to the chain even though it has the same target
    verify(sync, times(2)).syncToChain(targetChain);
  }

  @Test
  void shouldRemainSyncingWhenNoTargetChainSelectedButPreviousSyncStillActive() {
    final SafeFuture<SyncResult> previousSync = startFinalizedSync();

    assertThat(syncController.isSyncActive()).isTrue();

    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.empty());
    onTargetChainsUpdated();

    assertThat(syncController.isSyncActive()).isTrue();

    previousSync.complete(SyncResult.COMPLETE);

    assertNotSyncing();
  }

  @Test
  void shouldNotifySubscribersWhenSyncStatusChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<SyncResult> syncResult = startFinalizedSync();

    assertSyncSubscriberNotified(subscriber, true);

    syncResult.complete(SyncResult.COMPLETE);

    assertSyncSubscriberNotified(subscriber, false);
  }

  @Test
  void shouldNotNotifySubscribersAgainWhenSyncTargetChanges() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);
    final SafeFuture<SyncResult> previousSync = startFinalizedSync();

    assertSyncSubscriberNotified(subscriber, true);

    // Sync switches to a better chain
    final TargetChain newTargetChain = chainWith(dataStructureUtil.randomSlotAndBlockRoot());
    when(syncTargetSelector.selectSyncTarget(Optional.of(SyncTarget.finalizedTarget(targetChain))))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    when(sync.syncToChain(newTargetChain))
        .thenAnswer(
            invocation -> {
              previousSync.complete(SyncResult.TARGET_CHANGED);
              return new SafeFuture<>();
            });
    onTargetChainsUpdated();

    // But subscribers are not notified because we're already syncing.
    verifyNoMoreInteractions(subscriberExecutor);
    verifyNoMoreInteractions(subscriber);
  }

  @Test
  void shouldStartNonFinalizedSyncWhenNoSuitableFinalizedTargetChainAvailable() {
    when(syncTargetSelector.selectSyncTarget(Optional.empty()))
        .thenReturn(Optional.of(SyncTarget.nonfinalizedTarget(targetChain)));

    when(sync.syncToChain(targetChain)).thenReturn(new SafeFuture<>());

    onTargetChainsUpdated();

    ignoreFuture(verify(sync).syncToChain(targetChain));
  }

  @Test
  void shouldNotNotifySubscribersWhenRunningSpeculativeTarget() {
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<SyncResult> syncResult = new SafeFuture<>();
    when(syncTargetSelector.selectSyncTarget(any()))
        .thenReturn(Optional.of(SyncTarget.speculativeTarget(targetChain)));
    when(sync.syncToChain(targetChain)).thenReturn(syncResult);

    onTargetChainsUpdated();
    syncResult.complete(SyncResult.COMPLETE);

    verify(subscriberExecutor, never()).execute(any());
  }

  @Test
  void shouldRestartSyncEverySyncAwakePeriod() {
    final SafeFuture<SyncResult> syncResult = startFinalizedSync();
    assertThat(syncController.isSyncActive()).isTrue();
    syncResult.complete(SyncResult.COMPLETE);
    assertNotSyncing();

    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);

    final SafeFuture<SyncResult> syncResultNew = new SafeFuture<>();
    when(syncTargetSelector.selectSyncTarget(any()))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    when(sync.syncToChain(targetChain)).thenReturn(syncResultNew);
    timeProvider.advanceTimeBySeconds(SYNC_AWAKE_INTERVAL.toSeconds());
    asyncRunner.executeDueActions();
    assertThat(syncController.isSyncActive()).isTrue();
    assertSyncSubscriberNotified(subscriber, true);
  }

  @Test
  void shouldNotRestartSyncWithoutNewTargets() {
    final SafeFuture<SyncResult> syncResult = startFinalizedSync();
    assertThat(syncController.isSyncActive()).isTrue();
    syncResult.complete(SyncResult.COMPLETE);
    assertNotSyncing();

    when(syncTargetSelector.selectSyncTarget(any())).thenReturn(Optional.empty());
    final SyncSubscriber subscriber = mock(SyncSubscriber.class);
    syncController.subscribeToSyncChanges(subscriber);
    timeProvider.advanceTimeBySeconds(SYNC_AWAKE_INTERVAL.toSeconds());
    asyncRunner.executeDueActions();
    assertNotSyncing();
    assertSyncSubscriberNotified(subscriber, false);
  }

  private void assertSyncSubscriberNotified(
      final SyncSubscriber subscriber, final boolean syncing) {
    // Shouldn't notify on the event thread
    verifyNoMoreInteractions(subscriber);

    // Notification happens via the subscriberExecutor
    final ArgumentCaptor<Runnable> notificationCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(subscriberExecutor, atLeastOnce()).execute(notificationCaptor.capture());
    notificationCaptor.getValue().run();

    verify(subscriber).onSyncingChange(syncing);
  }

  private SafeFuture<SyncResult> startFinalizedSync() {
    final SafeFuture<SyncResult> syncResult = new SafeFuture<>();
    when(syncTargetSelector.selectSyncTarget(any()))
        .thenReturn(Optional.of(SyncTarget.finalizedTarget(targetChain)));
    when(sync.syncToChain(targetChain)).thenReturn(syncResult);
    onTargetChainsUpdated();
    return syncResult;
  }

  private void onTargetChainsUpdated() {
    eventThread.execute(syncController::onTargetChainsUpdated);
  }

  private void assertNotSyncing() {
    assertThat(syncController.isSyncActive()).isFalse();
    assertThat(syncController.getSyncStatus()).isEqualTo(new SyncingStatus(false, HEAD_SLOT));
  }
}
