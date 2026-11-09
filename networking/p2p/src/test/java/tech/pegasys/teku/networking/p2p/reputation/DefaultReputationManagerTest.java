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

package tech.pegasys.teku.networking.p2p.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.LARGE_PENALTY;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.LARGE_REWARD;
import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.SMALL_PENALTY;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.metrics.StubGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;

class DefaultReputationManagerTest {

  private static final int MORE_THAN_COOLDOWN_PERIOD =
      DefaultReputationManager.COOLDOWN_PERIOD.intValue() + 1;
  private static final int MORE_THAN_BAN_PERIOD =
      DefaultReputationManager.BAN_PERIOD.intValue() + 1;
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final PeerAddress peerAddress = new PeerAddress(new MockNodeId(1));
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final PeerPools peerPools = new PeerPools();
  private final ReputationManager reputationManager =
      new DefaultReputationManager(metricsSystem, timeProvider, 5, peerPools);

  @Test
  public void shouldDisallowConnectionInitiationWhenConnectionHasFailedRecently() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();
  }

  @Test
  public void shouldNotRejectInboundConnectionWhenConnectionHasFailedRecently() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();
  }

  @Test
  public void shouldAllowConnectionInitiationToUnknownPeers() {
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldAllowConnectionAfterSuccessfulConnection() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    reputationManager.reportInitiatedConnectionSuccessful(peerAddress);

    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldAllowConnectionInitiationAfterTimePasses() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  public void shouldReportCacheSize() {
    final StubGauge cacheSizeGauge =
        metricsSystem.getGauge(TekuMetricCategory.NETWORK, "peer_reputation_cache_size");
    assertThat(cacheSizeGauge.getValue()).isZero();

    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    assertThat(cacheSizeGauge.getValue()).isEqualTo(1);

    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    reputationManager.reportInitiatedConnectionFailed(new PeerAddress(new MockNodeId(2)));
    reputationManager.reportInitiatedConnectionSuccessful(new PeerAddress(new MockNodeId(2)));
    assertThat(cacheSizeGauge.getValue()).isEqualTo(2);
  }

  @Test
  void shouldNotAllowConnectionAfterDisconnect() {
    reputationManager.reportDisconnection(peerAddress, Optional.empty(), true);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isFalse();
  }

  @Test
  void shouldNotRejectInboundConnectionAfterDisconnect() {
    reputationManager.reportDisconnection(peerAddress, Optional.empty(), true);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();
  }

  @Test
  void shouldRejectInboundConnectionAfterRateLimitDisconnect() {
    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.RATE_LIMITING), true);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress))
        .contains(DisconnectReason.RATE_LIMITING);
  }

  @Test
  void shouldRejectInboundConnectionAfterRateLimitDisconnectDuringExistingCooldown() {
    reputationManager.reportInitiatedConnectionFailed(peerAddress);
    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();

    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.RATE_LIMITING), true);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress))
        .contains(DisconnectReason.RATE_LIMITING);
  }

  @Test
  void shouldNotRejectInboundConnectionForStaticPeerAfterRateLimitDisconnect() {
    peerPools.addPeerToPool(peerAddress.getId(), PeerConnectionType.STATIC);

    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.RATE_LIMITING), true);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();
  }

  @Test
  void shouldStopRejectingInboundConnectionAfterRateLimitCooldownExpires() {
    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.RATE_LIMITING), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();
  }

  @Test
  void shouldAllowConnectionAfterDisconnectAfterTimePasses() {
    reputationManager.reportDisconnection(peerAddress, Optional.empty(), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isTrue();
  }

  @Test
  void shouldAllowConnectionToPreviouslyUnresponsivePeersAfterTimePasses() {
    reputationManager.reportDisconnection(
        peerAddress, Optional.of(DisconnectReason.UNRESPONSIVE), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);

    assertThat(reputationManager.isConnectionInitiationAllowed(new PeerAddress(new MockNodeId(1))))
        .isTrue();
  }

  @ParameterizedTest(name = "reason={0}")
  @MethodSource("getNoBanDisconnectReasons")
  void shouldNotBanForNonFatalReasons(final DisconnectReason disconnectReason) {
    reputationManager.reportDisconnection(peerAddress, Optional.of(disconnectReason), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  static Stream<DisconnectReason> getNoBanDisconnectReasons() {
    return Stream.of(
        DisconnectReason.TOO_MANY_PEERS,
        DisconnectReason.UNRESPONSIVE,
        DisconnectReason.SHUTTING_DOWN,
        DisconnectReason.RATE_LIMITING);
  }

  @ParameterizedTest(name = "reason={0}")
  @MethodSource("getBanDisconnectReasons")
  void shouldAllowReconnectionForBanDisconnectReasonAfterBanIsOver(
      final DisconnectReason disconnectReason) {
    reputationManager.reportDisconnection(peerAddress, Optional.of(disconnectReason), true);

    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();

    timeProvider.advanceTimeBySeconds(MORE_THAN_BAN_PERIOD);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  static Stream<DisconnectReason> getBanDisconnectReasons() {
    return Stream.of(
        DisconnectReason.IRRELEVANT_NETWORK,
        DisconnectReason.UNABLE_TO_VERIFY_NETWORK,
        DisconnectReason.BAD_SCORE,
        DisconnectReason.REMOTE_FAULT);
  }

  @Test
  void shouldDisconnectIfFirstAdjustmentIsALargePenalty() {
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isTrue();
  }

  @Test
  void shouldBanAfterScoreDropsBelowThreshold() {
    reputationManager.adjustReputation(peerAddress, LARGE_PENALTY);
    timeProvider.advanceTimeBySeconds(MORE_THAN_COOLDOWN_PERIOD);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();

    timeProvider.advanceTimeBySeconds(MORE_THAN_BAN_PERIOD);
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();
  }

  @Test
  void shouldRejectInboundConnectionAfterScoreDropsBelowThreshold() {
    reputationManager.adjustReputation(peerAddress, LARGE_PENALTY);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress))
        .contains(DisconnectReason.BAD_SCORE);
  }

  @Test
  void shouldStopRejectingInboundConnectionAfterBanExpires() {
    reputationManager.adjustReputation(peerAddress, LARGE_PENALTY);

    timeProvider.advanceTimeBySeconds(MORE_THAN_BAN_PERIOD);

    assertThat(reputationManager.getInboundConnectionRejectionReason(peerAddress)).isEmpty();
  }

  @Test
  void shouldDisconnectAfterFourSmallPenalties() {
    assertThat(reputationManager.adjustReputation(peerAddress, SMALL_PENALTY)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, SMALL_PENALTY)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, SMALL_PENALTY)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, SMALL_PENALTY)).isTrue();
  }

  @Test
  void shouldCapPositiveScoreAtTwoLargeChanges() {
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_REWARD)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_REWARD)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_REWARD)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_REWARD)).isFalse();

    // Two large penalties should get from the max positive value back to 0.
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isFalse();
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isFalse();

    // And one more gets disconnected
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isTrue();
  }

  @Test
  void shouldBeUnsuitableToConnectToAfterBeingDisconnected() {
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isTrue();
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(PeerConnectionType.class)
  void checkReputationNotAdjustedWhenStaticType(final PeerConnectionType type) {
    peerPools.addPeerToPool(peerAddress.getId(), type);
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY))
        .isEqualTo(!type.equals(PeerConnectionType.STATIC));
  }

  @Test
  void shouldSwitchReputationWhenTypeChanges() {
    // starting as score based should activate reputation tracking
    peerPools.addPeerToPool(peerAddress.getId(), PeerConnectionType.SCORE_BASED);
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isTrue();
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();

    // switching to static should switch to a static reputation (which ignores penalties)
    peerPools.addPeerToPool(peerAddress.getId(), PeerConnectionType.STATIC);
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isFalse();
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isTrue();

    // switching back to score based should switch back to a new score based reputation
    peerPools.addPeerToPool(peerAddress.getId(), PeerConnectionType.RANDOMLY_SELECTED);
    assertThat(reputationManager.adjustReputation(peerAddress, LARGE_PENALTY)).isTrue();
    assertThat(reputationManager.isConnectionInitiationAllowed(peerAddress)).isFalse();
  }
}
