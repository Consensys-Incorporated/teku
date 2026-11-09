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

package tech.pegasys.teku.networking.p2p.gossip.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

public class GossipPeerScoringConfig {
  private final double topicScoreCap;
  private final double appSpecificWeight;
  private final Optional<PeerScorer> peerScorer;
  private final Optional<WhitelistedIpManager> whitelistManager;
  private final Optional<DirectPeerManager> directPeerManager;
  private final double ipColocationFactorWeight;
  private final int ipColocationFactorThreshold;
  private final double behaviourPenaltyWeight;
  private final double behaviourPenaltyDecay;
  private final double behaviourPenaltyThreshold;
  private final Duration decayInterval;
  private final double decayToZero;
  private final Duration retainScore;

  private GossipPeerScoringConfig(
      final double topicScoreCap,
      final Optional<DirectPeerManager> directPeerManager,
      final Optional<PeerScorer> peerScorer,
      final double appSpecificWeight,
      final Optional<WhitelistedIpManager> whitelistManager,
      final double ipColocationFactorWeight,
      final int ipColocationFactorThreshold,
      final double behaviourPenaltyWeight,
      final double behaviourPenaltyDecay,
      final double behaviourPenaltyThreshold,
      final Duration decayInterval,
      final double decayToZero,
      final Duration retainScore) {
    this.topicScoreCap = topicScoreCap;
    this.directPeerManager = directPeerManager;
    this.peerScorer = peerScorer;
    this.appSpecificWeight = appSpecificWeight;
    this.whitelistManager = whitelistManager;
    this.ipColocationFactorWeight = ipColocationFactorWeight;
    this.ipColocationFactorThreshold = ipColocationFactorThreshold;
    this.behaviourPenaltyWeight = behaviourPenaltyWeight;
    this.behaviourPenaltyDecay = behaviourPenaltyDecay;
    this.behaviourPenaltyThreshold = behaviourPenaltyThreshold;
    this.decayInterval = decayInterval;
    this.decayToZero = decayToZero;
    this.retainScore = retainScore;
  }

  public static Builder builder() {
    return new Builder();
  }

  public double getTopicScoreCap() {
    return topicScoreCap;
  }

  public double getAppSpecificWeight() {
    return appSpecificWeight;
  }

  public Optional<PeerScorer> getAppSpecificScorer() {
    return peerScorer;
  }

  public Optional<WhitelistedIpManager> getWhitelistManager() {
    return whitelistManager;
  }

  public Optional<DirectPeerManager> getDirectPeerManager() {
    return directPeerManager;
  }

  public double getIpColocationFactorWeight() {
    return ipColocationFactorWeight;
  }

  public int getIpColocationFactorThreshold() {
    return ipColocationFactorThreshold;
  }

  public double getBehaviourPenaltyWeight() {
    return behaviourPenaltyWeight;
  }

  public double getBehaviourPenaltyDecay() {
    return behaviourPenaltyDecay;
  }

  public double getBehaviourPenaltyThreshold() {
    return behaviourPenaltyThreshold;
  }

  public Duration getDecayInterval() {
    return decayInterval;
  }

  public double getDecayToZero() {
    return decayToZero;
  }

  public Duration getRetainScore() {
    return retainScore;
  }

  public static class Builder {
    private Double topicScoreCap = 0.0;
    private Double appSpecificWeight = 0.0;
    private Optional<PeerScorer> appSpecificScorer = Optional.empty();
    private Optional<WhitelistedIpManager> whitelistManager = Optional.empty();
    private Optional<DirectPeerManager> directPeerManager = Optional.empty();
    private Double ipColocationFactorWeight = 0.0;
    private Integer ipColocationFactorThreshold = 0;
    private Double behaviourPenaltyWeight = 0.0;
    private Double behaviourPenaltyDecay = 0.9;
    private Double behaviourPenaltyThreshold = 1.0;
    private Duration decayInterval = Duration.ofMinutes(1);
    private Double decayToZero = 0.0;
    private Duration retainScore = Duration.ofMinutes(10);

    private Builder() {}

    public GossipPeerScoringConfig build() {
      return new GossipPeerScoringConfig(
          topicScoreCap,
          directPeerManager,
          appSpecificScorer,
          appSpecificWeight,
          whitelistManager,
          ipColocationFactorWeight,
          ipColocationFactorThreshold,
          behaviourPenaltyWeight,
          behaviourPenaltyDecay,
          behaviourPenaltyThreshold,
          decayInterval,
          decayToZero,
          retainScore);
    }

    public Builder topicScoreCap(final Double topicScoreCap) {
      checkNotNull(topicScoreCap);
      this.topicScoreCap = topicScoreCap;
      return this;
    }

    public Builder appSpecificWeight(final Double appSpecificWeight) {
      checkNotNull(appSpecificWeight);
      this.appSpecificWeight = appSpecificWeight;
      return this;
    }

    public Builder appSpecificScorer(final Optional<PeerScorer> appSpecificScorer) {
      checkNotNull(appSpecificScorer);
      this.appSpecificScorer = appSpecificScorer;
      return this;
    }

    public Builder whitelistManager(final Optional<WhitelistedIpManager> whitelistManager) {
      checkNotNull(whitelistManager);
      this.whitelistManager = whitelistManager;
      return this;
    }

    public Builder directPeerManager(final Optional<DirectPeerManager> directPeerManager) {
      checkNotNull(directPeerManager);
      this.directPeerManager = directPeerManager;
      return this;
    }

    public Builder ipColocationFactorWeight(final Double ipColocationFactorWeight) {
      checkNotNull(ipColocationFactorWeight);
      this.ipColocationFactorWeight = ipColocationFactorWeight;
      return this;
    }

    public Builder ipColocationFactorThreshold(final Integer ipColocationFactorThreshold) {
      checkNotNull(ipColocationFactorThreshold);
      this.ipColocationFactorThreshold = ipColocationFactorThreshold;
      return this;
    }

    public Builder behaviourPenaltyWeight(final Double behaviourPenaltyWeight) {
      checkNotNull(behaviourPenaltyWeight);
      this.behaviourPenaltyWeight = behaviourPenaltyWeight;
      return this;
    }

    public Builder behaviourPenaltyDecay(final Double behaviourPenaltyDecay) {
      checkNotNull(behaviourPenaltyDecay);
      this.behaviourPenaltyDecay = behaviourPenaltyDecay;
      return this;
    }

    public Builder behaviourPenaltyThreshold(final Double behaviourPenaltyThreshold) {
      checkNotNull(behaviourPenaltyThreshold);
      this.behaviourPenaltyThreshold = behaviourPenaltyThreshold;
      return this;
    }

    public Builder decayInterval(final Duration decayInterval) {
      checkNotNull(decayInterval);
      if (decayInterval.isNegative()) {
        throw new InvalidConfigurationException(
            String.format("Invalid decayInterval: %s", decayInterval));
      }
      this.decayInterval = decayInterval;
      return this;
    }

    public Builder decayToZero(final Double decayToZero) {
      checkNotNull(decayToZero);
      this.decayToZero = decayToZero;
      return this;
    }

    public Builder retainScore(final Duration retainScore) {
      checkNotNull(retainScore);
      if (retainScore.isNegative()) {
        throw new InvalidConfigurationException(
            String.format("Invalid retainScore: %s", retainScore));
      }
      this.retainScore = retainScore;
      return this;
    }
  }

  public interface PeerScorer {
    double scorePeer(final NodeId peer);
  }

  public interface DirectPeerManager {
    boolean isDirectPeer(final NodeId peer);
  }

  public interface WhitelistedIpManager {
    boolean isWhitelisted(final String ipAddress);
  }
}
