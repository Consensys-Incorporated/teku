/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beacon.sync.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

class SyncStateTrackerTest {

  public static final int STARTUP_TARGET_PEER_COUNT = 5;
  public static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(10);
  private static final Spec SPEC = TestSpecFactory.createDefault();
  private static final long MAX_SLOTS_BEHIND_HEAD =
      (long) SPEC.getGenesisSpec().getSlotsPerEpoch()
          * SPEC.getGenesisSpec().getConfig().getMaxSeedLookahead();
  private static final UInt64 CURRENT_SLOT = UInt64.valueOf(10_000);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);

  private final ForwardSync syncService = mock(ForwardSync.class);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final EventLogger eventLogger = mock(EventLogger.class);

  private final SyncStateTracker tracker =
      new SyncStateTracker(
          asyncRunner,
          syncService,
          network,
          recentChainData,
          STARTUP_TARGET_PEER_COUNT,
          STARTUP_TIMEOUT,
          eventLogger,
          metricsSystem);
  private SyncSubscriber syncSubscriber;
  private PeerConnectedSubscriber<Eth2Peer> peerSubscriber;

  @BeforeEach
  public void setUp() {
    when(recentChainData.getHeadSlot()).thenReturn(CURRENT_SLOT);
    // stands in for the real RecentChainData tolerance, evaluated against whatever head is stubbed
    // at the time it's asked
    when(recentChainData.isHeadCloseToSlot(any()))
        .thenAnswer(
            invocation ->
                invocation
                    .<UInt64>getArgument(0)
                    .minusMinZero(recentChainData.getHeadSlot())
                    .isLessThanOrEqualTo(MAX_SLOTS_BEHIND_HEAD));
    peersReportingHeadSlots(CURRENT_SLOT, CURRENT_SLOT, CURRENT_SLOT);
    assertThat(tracker.start()).isCompleted();

    final ArgumentCaptor<SyncSubscriber> syncSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    verify(syncService).subscribeToSyncChanges(syncSubscriberArgumentCaptor.capture());
    syncSubscriber = syncSubscriberArgumentCaptor.getValue();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<PeerConnectedSubscriber<Eth2Peer>> peerSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(PeerConnectedSubscriber.class);
    verify(network).subscribeConnect(peerSubscriberArgumentCaptor.capture());
    peerSubscriber = peerSubscriberArgumentCaptor.getValue();
  }

  @Test
  public void shouldStartInStartupState() {
    assertSyncState(SyncState.START_UP);
  }

  @Test
  public void shouldStartInSyncWhenTargetPeerCountIsZero() {
    final SyncStateProvider tracker =
        new SyncStateTracker(
            asyncRunner,
            syncService,
            network,
            recentChainData,
            0,
            STARTUP_TIMEOUT,
            new NoOpMetricsSystem());
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldStartInSyncWhenStartupTimeoutIsZero() {
    final SyncStateProvider tracker =
        new SyncStateTracker(
            asyncRunner,
            syncService,
            network,
            recentChainData,
            STARTUP_TARGET_PEER_COUNT,
            Duration.ofSeconds(0),
            new NoOpMetricsSystem());
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeSyncingWhenSyncStarts() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldReturnToStartupModeWhenSyncCompletesIfPeerRequirementNotMet() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);
    // we're back to waiting for peers rather than in sync, so there's no catch up to announce
    verify(eventLogger, never()).syncCompleted();
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldAnnounceSyncCompletedWhenStartupFinishesAfterASyncRan() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();

    // the sync stops before we have the peers to trust our head, so we wait rather than announce
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);
    verify(eventLogger, never()).syncCompleted();

    // reaching sync closes out the start we announced, even though startup is what got us there
    completeStartup();
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  public void shouldAnnounceANewSyncStartAfterASyncRanDuringStartup() {
    syncSubscriber.onSyncingChange(true);
    verify(eventLogger).syncStart();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);

    // reaching sync closes out the start we announced, and means the next sync really is a new one
    completeStartup();
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();

    syncSubscriber.onSyncingChange(true);
    verify(eventLogger, times(2)).syncStart();

    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(2)).syncCompleted();
  }

  @Test
  public void shouldAnnounceSyncCompletedOnceWhenStartupFinishesWhileStillBehind() {
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);

    // still behind when startup finishes, so we stay out of sync rather than announcing anything
    when(recentChainData.getHeadSlot()).thenReturn(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    completeStartup();
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger, never()).syncCompleted();

    headAt(CURRENT_SLOT);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  public void shouldNotLogOptimisticMessagesOnStartUp() {
    // initialize with optimistic
    tracker.onOptimisticHeadChanged(true);

    // start syncing
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.OPTIMISTIC_SYNCING);
    verify(eventLogger).syncStart();

    // head no more optimistic
    tracker.onOptimisticHeadChanged(false);
    assertSyncState(SyncState.SYNCING);

    // nothing left to sync, but still waiting for peers
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.START_UP);

    // turn head optimistic. No blocks to sync so enter awaiting EL state.
    tracker.onOptimisticHeadChanged(true);
    assertSyncState(SyncState.AWAITING_EL);

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void shouldLogCorrectSequenceOfSyncEvents() {
    // get connected
    completeStartup();

    // start syncing
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStart();

    // turn head optimistic
    tracker.onOptimisticHeadChanged(true);
    verify(eventLogger).headTurnedOptimisticWhileSyncing();

    // beacon synced while head is optimistic
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncCompletedWhileHeadIsOptimistic();

    // head no longer optimistic
    tracker.onOptimisticHeadChanged(false);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger).syncCompleted();

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  void shouldUpdateMetricWhenSyncStatusChanges() {
    assertThat(tracker.getCurrentSyncState().isSyncing()).isFalse();
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "node_syncing_active").getValue())
        .isEqualTo(0.0);

    syncSubscriber.onSyncingChange(true);
    assertThat(tracker.getCurrentSyncState().isSyncing()).isTrue();
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "node_syncing_active").getValue())
        .isEqualTo(1.0);

    syncSubscriber.onSyncingChange(false);
    assertThat(tracker.getCurrentSyncState().isSyncing()).isFalse();
    assertThat(metricsSystem.getGauge(TekuMetricCategory.BEACON, "node_syncing_active").getValue())
        .isEqualTo(0.0);
  }

  @Test
  void shouldReturnToInSyncAfterSyncCompletesIfStartupModeIsDisabled() {
    reset(syncService);
    final SyncStateTracker tracker =
        new SyncStateTracker(
            asyncRunner,
            syncService,
            network,
            recentChainData,
            STARTUP_TARGET_PEER_COUNT,
            Duration.ofSeconds(0),
            new NoOpMetricsSystem());
    tracker.start().join();

    final ArgumentCaptor<SyncSubscriber> syncSubscriberArgumentCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    verify(syncService).subscribeToSyncChanges(syncSubscriberArgumentCaptor.capture());
    syncSubscriber = syncSubscriberArgumentCaptor.getValue();

    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
    syncSubscriber.onSyncingChange(true);
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.SYNCING);
    syncSubscriber.onSyncingChange(false);
    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenSyncCompletesIfPeerRequirementMet() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    completeStartup();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenSyncCompletesIfTimeoutReached() {
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);
    asyncRunner.executeQueuedActions();
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldBeInSyncWhenTargetPeerCountReachedAndSyncNotStarted() {
    completeStartup();
    assertSyncState(SyncState.IN_SYNC);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void shouldRemainInStartupUntilPeerCountIsReached() {
    when(network.getPeerCount()).thenReturn(1);
    peerSubscriber.onConnected(mock(Eth2Peer.class));
    assertSyncState(SyncState.START_UP);
  }

  @Test
  public void shouldCompleteStartupWhenTimeoutIsReached() {
    asyncRunner.executeQueuedActions();
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldStayInSyncWhenTheChainItselfHasALongRunOfEmptySlots() {
    completeStartup();
    // Nobody is proposing, so every head block is ancient - ours and our peers' alike. We are in
    // fact at the tip and must keep proposing to end the outage.
    final UInt64 staleHead = CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD * 4);
    peersReportingHeadSlots(staleHead, staleHead, staleHead);
    headAt(staleHead);

    assertSyncState(SyncState.IN_SYNC);
    verifyNoInteractions(eventLogger);
  }

  @Test
  public void shouldRemainSyncingWhenSyncStopsBeforeReachingTheChainOurPeersAreOn() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    assertSyncState(SyncState.SYNCING);

    // sync stops without reaching the target (e.g. all peers dropped, or it stalled and will retry)
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStoppedWhileBehindHead(MAX_SLOTS_BEHIND_HEAD + 1);
    verify(eventLogger, never()).syncCompleted();
  }

  @Test
  public void shouldBeInSyncWhenSyncStopsExactlyAtTheEdgeOfTheAllowedDistance() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD));

    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger).syncCompleted();
  }

  @Test
  public void shouldReturnToInSyncOnceHeadCatchesUpWithoutSyncRestarting() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.SYNCING);

    // head caught up via gossip, so no sync event will be fired - the slot event must notice
    headAt(CURRENT_SLOT);

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger).syncCompleted();
  }

  @Test
  public void shouldBeAbleToReenterActiveSyncWhileHeldBehindHead() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.SYNCING);

    // the retry mechanism starts a new sync - we must still see it as an active sync
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.SYNCING);

    headAt(CURRENT_SLOT);
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldAnnounceSyncCompletedOnceWhenARetryStopsHavingCaughtUp() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncStoppedWhileBehindHead(anyLong());

    // the retry reaches the network's head, so the sync stopping and us catching up are the same
    // event - only one of the two paths that notice it may announce it
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT);
    syncSubscriber.onSyncingChange(false);

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  public void shouldAnnounceSyncCompletedOnceWhenOptimisticHeadClearsAfterBeingBehind() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncStoppedWhileBehindHead(anyLong());

    // the EL falls behind while we're held back, then we catch up and the EL validates our head -
    // the optimistic-head path and the state update both notice, only one may announce it
    tracker.onOptimisticHeadChanged(true);
    headAt(CURRENT_SLOT);
    tracker.onOptimisticHeadChanged(false);

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  public void shouldNotAnnounceSyncCompletedAgainUntilWeDropBackToSyncing() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger, times(1)).syncCompleted();

    // staying in sync slot after slot is not a new completion to announce
    tracker.onSlot(CURRENT_SLOT.plus(1));
    tracker.onSlot(CURRENT_SLOT.plus(2));

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();

    // dropping back out of sync re-arms it
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger, times(2)).syncCompleted();
  }

  @Test
  public void shouldNotAnnounceSyncStartAgainUntilWeReachedSync() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    verify(eventLogger).syncStart();
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));

    // forward sync gives up and retries without ever reaching the head, so it stays one sync
    syncSubscriber.onSyncingChange(false);
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    syncSubscriber.onSyncingChange(true);
    verify(eventLogger, times(1)).syncStart();

    // once we get there the next sync is a new one
    headAt(CURRENT_SLOT);
    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
    syncSubscriber.onSyncingChange(true);
    verify(eventLogger, times(2)).syncStart();
  }

  @Test
  public void shouldNotAnnounceSyncStartAgainWhileTheHeadStaysOptimistic() {
    // an EL that never validates our head keeps us out of the behind-head branch entirely, so the
    // sync start must be latched on its own rather than by the behind-head report
    completeStartup();
    tracker.onOptimisticHeadChanged(true);
    syncSubscriber.onSyncingChange(true);
    assertSyncState(SyncState.OPTIMISTIC_SYNCING);
    verify(eventLogger).syncStart();

    syncSubscriber.onSyncingChange(false);
    assertSyncState(SyncState.AWAITING_EL);
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    syncSubscriber.onSyncingChange(true);

    verify(eventLogger, times(1)).syncStart();
    verify(eventLogger, never()).syncCompleted();
  }

  @Test
  public void shouldNotReportBehindHeadRepeatedlyWhileHeldBehindHead() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);

    tracker.onSlot(CURRENT_SLOT.plus(1));
    tracker.onSlot(CURRENT_SLOT.plus(2));

    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStoppedWhileBehindHead(anyLong());
  }

  @Test
  public void shouldNotAnnounceSyncStartForRetriesWhileAlreadyReportingBehindHead() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    verify(eventLogger).syncStart();
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    verify(eventLogger).syncStoppedWhileBehindHead(anyLong());

    // peers keep connecting and dropping, so forward sync repeatedly starts and fails. We never
    // said syncing had finished, so we must not keep announcing that it started.
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);

    assertSyncState(SyncState.SYNCING);
    verify(eventLogger, times(1)).syncStart();
    verify(eventLogger, times(1)).syncStoppedWhileBehindHead(anyLong());
    verify(eventLogger, never()).syncCompleted();
  }

  @Test
  public void shouldAnnounceSyncCompletedOnceAfterRepeatedFailedRetries() {
    completeStartup();
    syncSubscriber.onSyncingChange(true);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));
    syncSubscriber.onSyncingChange(false);
    syncSubscriber.onSyncingChange(true);
    syncSubscriber.onSyncingChange(false);

    // a retry finally gets us there
    headAt(CURRENT_SLOT);

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger, times(1)).syncCompleted();
  }

  @Test
  public void shouldIgnoreASinglePeerOverstatingItsHeadOnceEnoughPeersAreConnected() {
    completeStartup();
    // one peer claims a head far ahead, the rest agree with us - don't let the liar hold us back
    peersReportingHeadSlots(
        CURRENT_SLOT.plus(MAX_SLOTS_BEHIND_HEAD * 10),
        CURRENT_SLOT,
        CURRENT_SLOT,
        CURRENT_SLOT,
        CURRENT_SLOT);
    headAt(CURRENT_SLOT);

    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldTrustASinglePeerWhenOnlyOneIsConnected() {
    completeStartup();
    peersReportingHeadSlots(CURRENT_SLOT);
    headAt(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1));

    assertSyncState(SyncState.SYNCING);
  }

  @Test
  public void shouldBeBehindWhenMoreThanOnePeerAgreesOnAHigherHead() {
    final UInt64 ourHead = CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1);
    completeStartup();
    peersReportingHeadSlots(CURRENT_SLOT, CURRENT_SLOT, ourHead, ourHead, ourHead);
    headAt(ourHead);

    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).syncStoppedWhileBehindHead(MAX_SLOTS_BEHIND_HEAD + 1);
  }

  @Test
  public void shouldNotBeBehindWhenOnlyOnePeerClaimsAHigherHead() {
    final UInt64 ourHead = CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1);
    completeStartup();
    peersReportingHeadSlots(CURRENT_SLOT, ourHead, ourHead, ourHead, ourHead);
    headAt(ourHead);

    assertSyncState(SyncState.IN_SYNC);
  }

  @Test
  public void shouldTrustTheHighestHeadWhenTooFewPeersAreConnectedToHaveAChoice() {
    final UInt64 ourHead = CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD + 1);
    completeStartup();
    // below the target peer count a single claim is all we have to go on, so we must act on it
    peersReportingHeadSlots(CURRENT_SLOT, ourHead, ourHead);
    headAt(ourHead);

    assertSyncState(SyncState.SYNCING);
  }

  @Test
  public void shouldStayInSyncWithoutPeersWhenMinimumExpectedPeersIsZero() {
    // a single node network holding every validator has to keep proposing on its own
    final SyncStateTracker tracker = startedTrackerExpectingPeers(0);
    peersReportingHeadSlots();
    when(recentChainData.getHeadSlot()).thenReturn(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD * 4));

    tracker.onSlot(CURRENT_SLOT);

    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
    verify(eventLogger, never()).notInSyncWithoutPeers();
  }

  @Test
  public void shouldNotTrustOwnHeadWithoutPeersWhenMinimumExpectedPeersIsFour() {
    final SyncStateTracker tracker = startedTrackerExpectingPeers(4);
    peersReportingHeadSlots();
    when(recentChainData.getHeadSlot()).thenReturn(CURRENT_SLOT.minus(MAX_SLOTS_BEHIND_HEAD * 4));

    tracker.onSlot(CURRENT_SLOT);

    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.SYNCING);
    verify(eventLogger).notInSyncWithoutPeers();
  }

  @Test
  public void shouldStayInSyncWithoutPeersWhenMinimumExpectedPeersIsZeroEvenAtGenesisHead() {
    // nothing has been imported yet, so there is no head to compare against either
    final SyncStateTracker tracker = startedTrackerExpectingPeers(0);
    peersReportingHeadSlots();
    when(recentChainData.getHeadSlot()).thenReturn(UInt64.ZERO);

    tracker.onSlot(CURRENT_SLOT);

    assertThat(tracker.getCurrentSyncState()).isEqualTo(SyncState.IN_SYNC);
  }

  @Test
  public void shouldRecoverOnceAPeerConnectsAndAgreesWithOurHead() {
    completeStartup();
    peersReportingHeadSlots();
    headAt(CURRENT_SLOT);
    assertSyncState(SyncState.SYNCING);
    verify(eventLogger).notInSyncWithoutPeers();

    peersReportingHeadSlots(CURRENT_SLOT);
    tracker.onSlot(CURRENT_SLOT);

    assertSyncState(SyncState.IN_SYNC);
    verify(eventLogger).syncCompleted();
  }

  private void completeStartup() {
    when(network.getPeerCount()).thenReturn(STARTUP_TARGET_PEER_COUNT);
    peerSubscriber.onConnected(mock(Eth2Peer.class));
  }

  /**
   * A started tracker configured with the given minimum expected peer count, already past its
   * startup window so that the behind-head rule applies.
   */
  private SyncStateTracker startedTrackerExpectingPeers(final int minimumExpectedPeers) {
    final SyncStateTracker tracker =
        new SyncStateTracker(
            asyncRunner,
            syncService,
            network,
            recentChainData,
            minimumExpectedPeers,
            STARTUP_TIMEOUT,
            eventLogger,
            new NoOpMetricsSystem());
    tracker.start().join();
    // fires the startup timeout, leaving START_UP for trackers that were waiting for peers
    asyncRunner.executeQueuedActions();
    return tracker;
  }

  /** Sets our chain head and fires the slot event that makes the tracker re-evaluate. */
  private void headAt(final UInt64 headSlot) {
    when(recentChainData.getHeadSlot()).thenReturn(headSlot);
    tracker.onSlot(CURRENT_SLOT);
  }

  private void peersReportingHeadSlots(final UInt64... headSlots) {
    final List<Eth2Peer> peers =
        Stream.of(headSlots)
            .map(
                headSlot -> {
                  final Eth2Peer peer = mock(Eth2Peer.class);
                  final PeerStatus status = mock(PeerStatus.class);
                  when(peer.hasStatus()).thenReturn(true);
                  when(peer.getStatus()).thenReturn(status);
                  when(status.getHeadSlot()).thenReturn(headSlot);
                  return peer;
                })
            .toList();
    when(network.streamPeers()).thenAnswer(__ -> peers.stream());
  }

  private void assertSyncState(final SyncState syncing) {
    assertThat(tracker.getCurrentSyncState()).isEqualTo(syncing);
  }
}
