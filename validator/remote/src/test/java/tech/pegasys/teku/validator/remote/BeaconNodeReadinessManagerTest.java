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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.node.PeerCountBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.required.SyncingStatus;

public class BeaconNodeReadinessManagerTest {

  private static final SyncingStatus SYNCED_OPTIMISTIC_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, false, Optional.of(true), Optional.empty());

  private static final SyncingStatus SYNCED_NON_OPTIMISTIC_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, false, Optional.of(false), Optional.empty());

  private static final SyncingStatus SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.empty(), Optional.empty());

  private static final SyncingStatus OPTIMISTICALLY_SYNCING_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.of(true), Optional.empty());

  private static final SyncingStatus EL_OFFLINE_STATUS =
      new SyncingStatus(UInt64.ONE, UInt64.ZERO, true, Optional.of(true), Optional.of(true));

  private final PeerCount notEnoughPeers =
      new PeerCountBuilder().connected(UInt64.valueOf(2)).disconnected(UInt64.valueOf(60)).build();
  private final PeerCount enoughPeers =
      new PeerCountBuilder().connected(UInt64.valueOf(51)).disconnected(UInt64.valueOf(60)).build();

  private final RemoteValidatorApiChannel beaconNodeApi = mock(RemoteValidatorApiChannel.class);
  private final RemoteValidatorApiChannel failoverBeaconNodeApi =
      mock(RemoteValidatorApiChannel.class);

  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);

  private final BeaconNodeReadinessChannel beaconNodeReadinessChannel =
      mock(BeaconNodeReadinessChannel.class);

  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);

  private final BeaconNodeReadinessManager beaconNodeReadinessManager =
      new BeaconNodeReadinessManager(
          timeProvider,
          beaconNodeApi,
          List.of(failoverBeaconNodeApi),
          validatorLogger,
          beaconNodeReadinessChannel);

  @BeforeEach
  public void setup() {
    when(beaconNodeApi.getPeerCount()).thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(failoverBeaconNodeApi.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  public void performsReadinessCheckOnStartup() {
    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    beaconNodeReadinessManager.start().join();

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isFalse();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    verify(validatorLogger).primaryBeaconNodeNotReady();
    verify(beaconNodeReadinessChannel).onPrimaryNodeNotReady();
    verify(beaconNodeReadinessChannel).onFailoverNodeNotReady(failoverBeaconNodeApi);

    beaconNodeReadinessManager.stop().join();

    assertThat(beaconNodeReadinessManager.isRunning()).isFalse();
  }

  @Test
  public void performsReadinessOnceWhenOverlaps() {
    final SafeFuture<SyncingStatus> primarySyncingStatusFuture = new SafeFuture<>();
    final SafeFuture<SyncingStatus> failoverSyncingStatusFuture = new SafeFuture<>();

    when(beaconNodeApi.getSyncingStatus()).thenReturn(primarySyncingStatusFuture);
    when(failoverBeaconNodeApi.getSyncingStatus()).thenReturn(failoverSyncingStatusFuture);

    final SafeFuture<?> startFuture = beaconNodeReadinessManager.start();

    assertThatSafeFuture(startFuture).isNotDone();

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThatSafeFuture(startFuture).isNotDone();

    primarySyncingStatusFuture.complete(SYNCING_STATUS);
    failoverSyncingStatusFuture.complete(SYNCING_STATUS);

    assertThatSafeFuture(startFuture).isCompleted();

    verify(beaconNodeApi, times(1)).getSyncingStatus();
    verify(failoverBeaconNodeApi, times(1)).getSyncingStatus();

    verify(validatorLogger, times(1)).primaryBeaconNodeNotReady();
    verify(beaconNodeReadinessChannel, times(1)).onPrimaryNodeNotReady();
    verify(beaconNodeReadinessChannel, times(1)).onFailoverNodeNotReady(failoverBeaconNodeApi);
  }

  @Test
  public void retrievesReadinessAndPublishesToAChannel() {
    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_OPTIMISTIC_STATUS));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();

    verifyNoInteractions(validatorLogger);
    verify(beaconNodeReadinessChannel).onFailoverNodeNotReady(failoverBeaconNodeApi);

    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(OPTIMISTICALLY_SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isFalse();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isTrue();

    verify(validatorLogger).primaryBeaconNodeNotReady();
    verify(beaconNodeReadinessChannel).onPrimaryNodeNotReady();

    // primary node recovers
    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_OPTIMISTIC_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();

    verify(validatorLogger).primaryBeaconNodeIsBackAndReady();
    // call it every time we check, channel will filter it
    verify(beaconNodeReadinessChannel, times(2)).onPrimaryNodeReady();
  }

  @Test
  public void shouldFallbackToFailoverNodeWhenElGoesOffline() {
    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(EL_OFFLINE_STATUS));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_OPTIMISTIC_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isFalse();
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isTrue();

    verify(validatorLogger).primaryBeaconNodeNotReady();
    verify(beaconNodeReadinessChannel).onPrimaryNodeNotReady();
  }

  @Test
  public void ordersFailoversByReadinessStatus() {

    // Primary BN, synced optimistic
    final RemoteValidatorApiChannel primaryBeaconNodeApi = mock(RemoteValidatorApiChannel.class);
    when(primaryBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_OPTIMISTIC_STATUS));
    // Since the BN is optimistic, the peer count won't be checked
    when(primaryBeaconNodeApi.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.of(enoughPeers)));

    // Failover BN, syncing
    final RemoteValidatorApiChannel syncingFailover = mock(RemoteValidatorApiChannel.class);
    when(syncingFailover.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));
    // Since the BN is syncing/not ready, the peer count won't be checked
    when(syncingFailover.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.of(enoughPeers)));

    // Failover BN, synced optimistic
    final RemoteValidatorApiChannel syncedOptimisticFailover =
        mock(RemoteValidatorApiChannel.class);
    when(syncedOptimisticFailover.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_OPTIMISTIC_STATUS));
    // Since the BN is optimistic, the peer count won't be checked
    when(syncedOptimisticFailover.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.of(enoughPeers)));

    // Failover BN synced and non-optimistic/ready
    final RemoteValidatorApiChannel syncedNonOptimisticNotEnoughPeersFailover =
        mock(RemoteValidatorApiChannel.class);
    when(syncedNonOptimisticNotEnoughPeersFailover.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_NON_OPTIMISTIC_STATUS));
    // Failover BN doesn't have enough connected peers
    when(syncedNonOptimisticNotEnoughPeersFailover.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.of(notEnoughPeers)));

    // Failover BN synced and non-optimistic/ready
    final RemoteValidatorApiChannel syncedNonOptimisticEnoughPeersFailover =
        mock(RemoteValidatorApiChannel.class);
    when(syncedNonOptimisticEnoughPeersFailover.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCED_NON_OPTIMISTIC_STATUS));
    // Failover BN has enough connected peers
    when(syncedNonOptimisticEnoughPeersFailover.getPeerCount())
        .thenReturn(SafeFuture.completedFuture(Optional.of(enoughPeers)));

    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            timeProvider,
            primaryBeaconNodeApi,
            List.of(
                syncingFailover,
                syncedOptimisticFailover,
                syncedNonOptimisticNotEnoughPeersFailover,
                syncedNonOptimisticEnoughPeersFailover),
            validatorLogger,
            beaconNodeReadinessChannel);

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    // The expected order should be: ready/non-optimistic with enough peers -> ready/non-optimistic
    // without enough peers -> optimistic -> syncing/not ready
    assertThat(beaconNodeReadinessManager.getFailoversInOrderOfReadiness())
        .toIterable()
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(
            syncedNonOptimisticEnoughPeersFailover,
            syncedNonOptimisticNotEnoughPeersFailover,
            syncedOptimisticFailover,
            syncingFailover);
  }

  @Test
  public void shouldReturnCorrectDefaultReadinessAndFailoverNodesWhenNoReadinessCheckPerformed() {
    // default to true if never ran on primary
    assertThat(beaconNodeReadinessManager.isReady(beaconNodeApi)).isTrue();
    // default to false if never ran on failover
    assertThat(beaconNodeReadinessManager.isReady(failoverBeaconNodeApi)).isFalse();
    // default to true for unmonitored nodes
    final RemoteValidatorApiChannel unknownNodeApi = mock(RemoteValidatorApiChannel.class);
    assertThat(beaconNodeReadinessManager.isReady(unknownNodeApi)).isTrue();

    verifyNoInteractions(validatorLogger, beaconNodeReadinessChannel);
  }

  @Test
  public void shouldDelayReadinessCheckOnErroredFailoverNode() {
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    when(beaconNodeApi.getSyncingStatus()).thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    verify(failoverBeaconNodeApi, times(1)).getSyncingStatus();

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    // still only one call to failover
    verify(failoverBeaconNodeApi, times(1)).getSyncingStatus();

    timeProvider.advanceTimeBySeconds(2 * 60); // advance by 2 minutes

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    // enough time has passed, so we should retry
    verify(failoverBeaconNodeApi, times(2)).getSyncingStatus();
  }

  @Test
  public void shouldAlwaysPerformReadinessCheckOnErroredPrimaryNode() {
    when(beaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));
    when(failoverBeaconNodeApi.getSyncingStatus())
        .thenReturn(SafeFuture.completedFuture(SYNCING_STATUS));

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    verify(beaconNodeApi, times(1)).getSyncingStatus();

    advanceToNextQueryPeriod(beaconNodeReadinessManager);

    // on errored primary node, we always retry
    verify(beaconNodeApi, times(2)).getSyncingStatus();
  }

  private void advanceToNextQueryPeriod(
      final BeaconNodeReadinessManager beaconNodeReadinessManager) {
    beaconNodeReadinessManager.onAttestationAggregationDue(UInt64.ZERO);
  }
}
