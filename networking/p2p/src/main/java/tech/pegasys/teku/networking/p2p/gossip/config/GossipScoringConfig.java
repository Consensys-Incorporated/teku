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

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;

/** Gossip scoring config. Contains peer scoring and topic scoring configs. */
public class GossipScoringConfig {
  private final GossipPeerScoringConfig peerScoringConfig;
  private final GossipTopicScoringConfig defaultTopicScoringConfig;
  private final Map<String, GossipTopicScoringConfig> topicScoringConfig;
  private final double gossipThreshold;
  private final double publishThreshold;
  private final double graylistThreshold;
  private final double acceptPXThreshold;
  private final double opportunisticGraftThreshold;

  private GossipScoringConfig(
      final GossipPeerScoringConfig peerScoringConfig,
      final GossipTopicScoringConfig defaultTopicScoringConfig,
      final Map<String, GossipTopicScoringConfig> topicScoringConfig,
      final double gossipThreshold,
      final double publishThreshold,
      final double graylistThreshold,
      final double acceptPXThreshold,
      final double opportunisticGraftThreshold) {
    this.peerScoringConfig = peerScoringConfig;
    this.defaultTopicScoringConfig = defaultTopicScoringConfig;
    this.topicScoringConfig = topicScoringConfig;
    this.gossipThreshold = gossipThreshold;
    this.publishThreshold = publishThreshold;
    this.graylistThreshold = graylistThreshold;
    this.acceptPXThreshold = acceptPXThreshold;
    this.opportunisticGraftThreshold = opportunisticGraftThreshold;
  }

  public GossipPeerScoringConfig getPeerScoringConfig() {
    return peerScoringConfig;
  }

  public GossipTopicScoringConfig getDefaultTopicScoringConfig() {
    return defaultTopicScoringConfig;
  }

  public Map<String, GossipTopicScoringConfig> getTopicScoringConfig() {
    return topicScoringConfig;
  }

  public double getGossipThreshold() {
    return gossipThreshold;
  }

  public double getPublishThreshold() {
    return publishThreshold;
  }

  public double getGraylistThreshold() {
    return graylistThreshold;
  }

  public double getAcceptPXThreshold() {
    return acceptPXThreshold;
  }

  public double getOpportunisticGraftThreshold() {
    return opportunisticGraftThreshold;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final GossipPeerScoringConfig.Builder peerScoringConfigBuilder =
        GossipPeerScoringConfig.builder();
    private final GossipTopicScoringConfig.Builder defaultTopicScoringConfigBuilder =
        GossipTopicScoringConfig.builder();
    private final GossipTopicsScoringConfig.Builder topicsScoringBuilder =
        GossipTopicsScoringConfig.builder();
    private Double gossipThreshold = 0.0;
    private Double publishThreshold = 0.0;
    private Double graylistThreshold = 0.0;
    private Double acceptPXThreshold = 0.0;
    private Double opportunisticGraftThreshold = 0.0;

    private Builder() {}

    public GossipScoringConfig build() {
      final Map<String, GossipTopicScoringConfig> topicConfigs =
          topicsScoringBuilder.build().getTopicConfigs();
      return new GossipScoringConfig(
          peerScoringConfigBuilder.build(),
          defaultTopicScoringConfigBuilder.build(),
          topicConfigs,
          gossipThreshold,
          publishThreshold,
          graylistThreshold,
          acceptPXThreshold,
          opportunisticGraftThreshold);
    }

    public Builder peerScoring(final Consumer<GossipPeerScoringConfig.Builder> consumer) {
      consumer.accept(peerScoringConfigBuilder);
      return this;
    }

    public Builder topicScoring(
        final String topic, final Consumer<GossipTopicScoringConfig.Builder> consumer) {
      topicsScoringBuilder.topicScoring(topic, consumer);
      return this;
    }

    public Builder topicScoring(final Consumer<GossipTopicsScoringConfig.Builder> consumer) {
      consumer.accept(topicsScoringBuilder);
      return this;
    }

    public Builder defaultTopicScoring(final Consumer<GossipTopicScoringConfig.Builder> consumer) {
      consumer.accept(defaultTopicScoringConfigBuilder);
      return this;
    }

    public Builder gossipThreshold(final Double gossipThreshold) {
      checkNotNull(gossipThreshold);
      this.gossipThreshold = gossipThreshold;
      return this;
    }

    public Builder publishThreshold(final Double publishThreshold) {
      checkNotNull(publishThreshold);
      this.publishThreshold = publishThreshold;
      return this;
    }

    public Builder graylistThreshold(final Double graylistThreshold) {
      checkNotNull(graylistThreshold);
      this.graylistThreshold = graylistThreshold;
      return this;
    }

    public Builder acceptPXThreshold(final Double acceptPXThreshold) {
      checkNotNull(acceptPXThreshold);
      this.acceptPXThreshold = acceptPXThreshold;
      return this;
    }

    public Builder opportunisticGraftThreshold(final Double opportunisticGraftThreshold) {
      checkNotNull(opportunisticGraftThreshold);
      this.opportunisticGraftThreshold = opportunisticGraftThreshold;
      return this;
    }

    public Builder directPeerManager(final DirectPeerManager directPeerManager) {
      checkNotNull(directPeerManager);

      this.peerScoringConfigBuilder.directPeerManager(Optional.of(directPeerManager));
      return this;
    }
  }
}
