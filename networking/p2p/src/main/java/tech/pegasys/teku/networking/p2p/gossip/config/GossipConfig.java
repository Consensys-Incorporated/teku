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
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipPeerScoringConfig.DirectPeerManager;

/**
 * Gossip options
 * https://github.com/ethereum/consensus-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#the-gossip-domain-gossipsub
 */
public class GossipConfig {
  public static final int DEFAULT_D = 8;
  public static final int DEFAULT_D_LOW = 6;
  public static final int DEFAULT_D_HIGH = 12;
  public static final int DEFAULT_D_LAZY = 6;
  private static final Duration DEFAULT_FANOUT_TTL = Duration.ofSeconds(60);
  private static final int DEFAULT_ADVERTISE = 3;
  private static final int DEFAULT_HISTORY = 6;
  static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(700);
  // After EIP-7045, attestations are valid for up to 2 full epochs, so TTL is 65
  // slots 1115 * HEARTBEAT = 1115 * 0.7 / 12 = 65.125
  static final Duration DEFAULT_SEEN_TTL = DEFAULT_HEARTBEAT_INTERVAL.multipliedBy(1115);
  public static final int DEFAULT_FLOOD_PUBLISH_MAX_MESSAGE_SIZE_THRESHOLD = 1 << 14; // 16KiB

  private final int d;
  private final int dLow;
  private final int dHigh;
  private final int dLazy;
  private final Duration fanoutTTL;
  private final int advertise;
  private final int history;
  private final Duration heartbeatInterval;
  private final Duration seenTTL;
  private final int floodPublishMaxMessageSizeThreshold;
  private final GossipScoringConfig scoringConfig;

  private GossipConfig(
      final int d,
      final int dLow,
      final int dHigh,
      final int dLazy,
      final Duration fanoutTTL,
      final int advertise,
      final int history,
      final Duration heartbeatInterval,
      final Duration seenTTL,
      final int floodPublishMaxMessageSizeThreshold,
      final GossipScoringConfig scoringConfig) {
    this.d = d;
    this.dLow = dLow;
    this.dHigh = dHigh;
    this.dLazy = dLazy;
    this.fanoutTTL = fanoutTTL;
    this.advertise = advertise;
    this.history = history;
    this.heartbeatInterval = heartbeatInterval;
    this.seenTTL = seenTTL;
    this.floodPublishMaxMessageSizeThreshold = floodPublishMaxMessageSizeThreshold;
    this.scoringConfig = scoringConfig;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static GossipConfig createDefault() {
    return builder().build();
  }

  public int getD() {
    return d;
  }

  public int getDLow() {
    return dLow;
  }

  public int getDHigh() {
    return dHigh;
  }

  public int getDLazy() {
    return dLazy;
  }

  public Duration getFanoutTTL() {
    return fanoutTTL;
  }

  public int getAdvertise() {
    return advertise;
  }

  public int getHistory() {
    return history;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public Duration getSeenTTL() {
    return seenTTL;
  }

  public int getFloodPublishMaxMessageSizeThreshold() {
    return floodPublishMaxMessageSizeThreshold;
  }

  public GossipScoringConfig getScoringConfig() {
    return scoringConfig;
  }

  public static class Builder {
    private final GossipScoringConfig.Builder scoringConfigBuilder = GossipScoringConfig.builder();

    private Integer d = DEFAULT_D;
    private Integer dLow = DEFAULT_D_LOW;
    private Integer dHigh = DEFAULT_D_HIGH;
    private Integer dLazy = DEFAULT_D_LAZY;
    private Duration fanoutTTL = DEFAULT_FANOUT_TTL;
    private Integer advertise = DEFAULT_ADVERTISE;
    private Integer history = DEFAULT_HISTORY;
    private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
    private Duration seenTTL = DEFAULT_SEEN_TTL;
    private int floodPublishMaxMessageSizeThreshold =
        DEFAULT_FLOOD_PUBLISH_MAX_MESSAGE_SIZE_THRESHOLD;

    private Builder() {}

    public GossipConfig build() {
      return new GossipConfig(
          d,
          dLow,
          dHigh,
          dLazy,
          fanoutTTL,
          advertise,
          history,
          heartbeatInterval,
          seenTTL,
          floodPublishMaxMessageSizeThreshold,
          scoringConfigBuilder.build());
    }

    public Builder scoring(final Consumer<GossipScoringConfig.Builder> consumer) {
      consumer.accept(scoringConfigBuilder);
      return this;
    }

    public Builder d(final Integer d) {
      checkNotNull(d);
      this.d = d;
      return this;
    }

    public Builder dLow(final Integer dLow) {
      checkNotNull(dLow);
      this.dLow = dLow;
      return this;
    }

    public Builder dHigh(final Integer dHigh) {
      checkNotNull(dHigh);
      this.dHigh = dHigh;
      return this;
    }

    public Builder dLazy(final Integer dLazy) {
      checkNotNull(dLazy);
      this.dLazy = dLazy;
      return this;
    }

    public Builder fanoutTTL(final Duration fanoutTTL) {
      checkNotNull(fanoutTTL);
      if (fanoutTTL.isNegative()) {
        throw new InvalidConfigurationException(String.format("Invalid fanoutTTL: %s", fanoutTTL));
      }
      this.fanoutTTL = fanoutTTL;
      return this;
    }

    public Builder advertise(final Integer advertise) {
      checkNotNull(advertise);
      this.advertise = advertise;
      return this;
    }

    public Builder history(final Integer history) {
      checkNotNull(history);
      this.history = history;
      return this;
    }

    public Builder heartbeatInterval(final Duration heartbeatInterval) {
      checkNotNull(heartbeatInterval);
      if (heartbeatInterval.isNegative()) {
        throw new InvalidConfigurationException(
            String.format("Invalid heartbeatInterval: %s", heartbeatInterval));
      }
      this.heartbeatInterval = heartbeatInterval;
      return this;
    }

    public Builder seenTTL(final Duration seenTTL) {
      checkNotNull(seenTTL);
      if (seenTTL.isNegative()) {
        throw new InvalidConfigurationException(String.format("Invalid seenTTL: %s", seenTTL));
      }
      this.seenTTL = seenTTL;
      return this;
    }

    public Builder floodPublishMaxMessageSizeThreshold(
        final int floodPublishMaxMessageSizeThreshold) {
      this.floodPublishMaxMessageSizeThreshold = floodPublishMaxMessageSizeThreshold;
      return this;
    }

    public Builder directPeerManager(final DirectPeerManager directPeerManager) {
      checkNotNull(directPeerManager);
      this.scoringConfigBuilder.directPeerManager(directPeerManager);
      return this;
    }
  }
}
