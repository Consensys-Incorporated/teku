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

import com.google.common.base.MoreObjects;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.connection.PeerConnectionType;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.network.PeerAddress;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class DefaultReputationManager implements ReputationManager {
  // This is not a big ban, we expect the peer could be usable very soon
  public static final UInt64 COOLDOWN_PERIOD = UInt64.valueOf(TimeUnit.MINUTES.toSeconds(2));
  // It's a big ban, we expect that the peer is not useful until major changes, for example,
  // software update
  public static final UInt64 BAN_PERIOD = UInt64.valueOf(TimeUnit.HOURS.toSeconds(12));

  public static final int LARGE_CHANGE = 10;
  public static final int SMALL_CHANGE = 3;
  private static final int DISCONNECT_THRESHOLD = -LARGE_CHANGE;
  private static final int MAX_REPUTATION_SCORE = 2 * LARGE_CHANGE;

  private final TimeProvider timeProvider;
  private final Cache<NodeId, Reputation> peerReputations;
  private final PeerPools peerPools;

  public DefaultReputationManager(
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final int capacity,
      final PeerPools peerPools) {
    this.timeProvider = timeProvider;
    this.peerReputations = LRUCache.create(capacity);
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.NETWORK,
        "peer_reputation_cache_size",
        "Total number of peer reputations tracked",
        peerReputations::size);
    this.peerPools = peerPools;
  }

  @Override
  public void reportInitiatedConnectionFailed(final PeerAddress peerAddress) {
    getOrCreateReputation(peerAddress)
        .reportInitiatedConnectionFailed(timeProvider.getTimeInSeconds());
  }

  @Override
  public boolean isConnectionInitiationAllowed(final PeerAddress peerAddress) {
    return peerReputations
        .getCached(peerAddress.getId())
        .map(reputation -> reputation.shouldInitiateConnection(timeProvider.getTimeInSeconds()))
        .orElse(true);
  }

  @Override
  public Optional<DisconnectReason> getInboundConnectionRejectionReason(
      final PeerAddress peerAddress) {
    if (isStaticPeer(peerAddress)) {
      return Optional.empty();
    }
    return peerReputations
        .getCached(peerAddress.getId())
        .flatMap(
            reputation ->
                reputation.getInboundConnectionRejectionReason(timeProvider.getTimeInSeconds()));
  }

  @Override
  public void reportInitiatedConnectionSuccessful(final PeerAddress peerAddress) {
    getOrCreateReputation(peerAddress).reportInitiatedConnectionSuccessful();
  }

  @Override
  public void reportDisconnection(
      final PeerAddress peerAddress,
      final Optional<DisconnectReason> reason,
      final boolean locallyInitiated) {
    getOrCreateReputation(peerAddress)
        .reportDisconnection(timeProvider.getTimeInSeconds(), reason, locallyInitiated);
  }

  /**
   * Adjust the reputation score for a peer either positively or negatively.
   *
   * @param peerAddress the address of the peer to adjust the score for.
   * @param effect the reputation change to apply.
   * @return true if the peer should be disconnected, otherwise false.
   */
  @Override
  public boolean adjustReputation(
      final PeerAddress peerAddress, final ReputationAdjustment effect) {
    return getOrCreateReputation(peerAddress)
        .adjustReputation(effect, timeProvider.getTimeInSeconds());
  }

  private Reputation getOrCreateReputation(final PeerAddress peerAddress) {
    final boolean isStatic = isStaticPeer(peerAddress);
    final Reputation reputation =
        peerReputations.get(
            peerAddress.getId(), key -> isStatic ? new StaticPeerReputation() : new Reputation());

    if (isStatic != (reputation instanceof StaticPeerReputation)) {
      // In case peer changed type after the cache was already populated
      // we replace reputation with correct type
      final Reputation newReputation = isStatic ? new StaticPeerReputation() : new Reputation();
      peerReputations.invalidateWithNewValue(peerAddress.getId(), newReputation);
      return newReputation;
    }

    return reputation;
  }

  private boolean isStaticPeer(final PeerAddress peerAddress) {
    return peerPools.getPeerConnectionType(peerAddress.getId()).equals(PeerConnectionType.STATIC);
  }

  private static class Reputation {
    private static final int DEFAULT_SCORE = 0;
    private static final EnumSet<DisconnectReason> BAN_REASONS =
        EnumSet.of(
            DisconnectReason.IRRELEVANT_NETWORK,
            DisconnectReason.UNABLE_TO_VERIFY_NETWORK,
            DisconnectReason.BAD_SCORE,
            DisconnectReason.REMOTE_FAULT);
    private static final EnumSet<DisconnectReason> INBOUND_REJECTION_DURING_COOLDOWN_REASONS =
        EnumSet.of(DisconnectReason.RATE_LIMITING);

    private volatile Optional<UInt64> suitableAfter = Optional.empty();
    private volatile Optional<DisconnectReason> inboundConnectionRejectionReason = Optional.empty();
    private final AtomicInteger score = new AtomicInteger(DEFAULT_SCORE);

    public void reportInitiatedConnectionFailed(final UInt64 failureTime) {
      suitableAfter = Optional.of(failureTime.plus(COOLDOWN_PERIOD));
      inboundConnectionRejectionReason = Optional.empty();
    }

    public boolean shouldInitiateConnection(final UInt64 currentTime) {
      return isSuitableAt(currentTime);
    }

    private boolean isSuitableAt(final UInt64 someTime) {
      return suitableAfter.map(suitableAfter -> suitableAfter.compareTo(someTime) < 0).orElse(true);
    }

    public void reportInitiatedConnectionSuccessful() {
      suitableAfter = Optional.empty();
      inboundConnectionRejectionReason = Optional.empty();
    }

    public void reportDisconnection(
        final UInt64 disconnectTime,
        final Optional<DisconnectReason> reason,
        final boolean locallyInitiated) {
      if (shouldBan(reason, locallyInitiated)) {
        suitableAfter = Optional.of(disconnectTime.plus(BAN_PERIOD));
        inboundConnectionRejectionReason = reason;
        score.set(DEFAULT_SCORE);
      } else if (isLocallyConsideredUnsuitableDuringCooldown(reason, locallyInitiated)) {
        suitableAfter = Optional.of(disconnectTime.plus(COOLDOWN_PERIOD));
        inboundConnectionRejectionReason = reason;
      } else if (suitableAfter.isEmpty()) {
        suitableAfter = Optional.of(disconnectTime.plus(COOLDOWN_PERIOD));
        inboundConnectionRejectionReason = Optional.empty();
      }
    }

    private static boolean shouldBan(
        final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
      return reason
          .map(
              disconnectReason -> {
                if (disconnectReason.isPermanent()) {
                  return true;
                }
                if (!locallyInitiated) {
                  return false;
                }
                return BAN_REASONS.contains(disconnectReason);
              })
          .orElse(false);
    }

    private static boolean isLocallyConsideredUnsuitableDuringCooldown(
        final Optional<DisconnectReason> reason, final boolean locallyInitiated) {
      return locallyInitiated
          && reason.map(INBOUND_REJECTION_DURING_COOLDOWN_REASONS::contains).orElse(false);
    }

    public boolean adjustReputation(final ReputationAdjustment effect, final UInt64 currentTime) {
      // No extra penalizing if already not suitable
      if (!isSuitableAt(currentTime)) {
        return score.get() <= DISCONNECT_THRESHOLD;
      }
      final int newScore =
          score.updateAndGet(
              current -> Math.min(MAX_REPUTATION_SCORE, current + effect.getScoreChange()));
      final boolean shouldDisconnect = newScore <= DISCONNECT_THRESHOLD;
      if (shouldDisconnect) {
        // Prevent our node from connecting out to the remote peer for a long time
        suitableAfter = Optional.of(currentTime.plus(BAN_PERIOD));
        inboundConnectionRejectionReason = Optional.of(DisconnectReason.BAD_SCORE);
        score.set(DEFAULT_SCORE);
      }
      return shouldDisconnect;
    }

    public Optional<DisconnectReason> getInboundConnectionRejectionReason(
        final UInt64 currentTime) {
      if (isSuitableAt(currentTime)) {
        return Optional.empty();
      }
      return inboundConnectionRejectionReason;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("suitableAfter", suitableAfter)
          .add("score", score)
          .toString();
    }
  }

  private static class StaticPeerReputation extends Reputation {
    @Override
    public boolean shouldInitiateConnection(final UInt64 currentTime) {
      return true;
    }

    @Override
    public boolean adjustReputation(final ReputationAdjustment effect, final UInt64 currentTime) {
      return false;
    }

    @Override
    public Optional<DisconnectReason> getInboundConnectionRejectionReason(
        final UInt64 currentTime) {
      return Optional.empty();
    }

    @Override
    public void reportDisconnection(
        final UInt64 disconnectTime,
        final Optional<DisconnectReason> reason,
        final boolean locallyInitiated) {
      // Do nothing, static peers are not affected by disconnections
    }
  }
}
