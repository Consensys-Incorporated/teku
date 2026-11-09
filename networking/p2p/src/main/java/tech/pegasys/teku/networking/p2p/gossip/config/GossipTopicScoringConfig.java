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
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

/** Scoring config for a single topic */
public class GossipTopicScoringConfig {
  private final double topicWeight;
  private final double timeInMeshWeight;
  private final Duration timeInMeshQuantum;
  private final double timeInMeshCap;
  private final double firstMessageDeliveriesWeight;
  private final double firstMessageDeliveriesDecay;
  private final double firstMessageDeliveriesCap;
  private final double meshMessageDeliveriesWeight;
  private final double meshMessageDeliveriesDecay;
  private final double meshMessageDeliveriesThreshold;
  private final double meshMessageDeliveriesCap;
  private final Duration meshMessageDeliveriesActivation;
  private final Duration meshMessageDeliveryWindow;
  private final double meshFailurePenaltyWeight;
  private final double meshFailurePenaltyDecay;
  private final double invalidMessageDeliveriesWeight;
  private final double invalidMessageDeliveriesDecay;

  private GossipTopicScoringConfig(
      final double topicWeight,
      final double timeInMeshWeight,
      final Duration timeInMeshQuantum,
      final double timeInMeshCap,
      final double firstMessageDeliveriesWeight,
      final double firstMessageDeliveriesDecay,
      final double firstMessageDeliveriesCap,
      final double meshMessageDeliveriesWeight,
      final double meshMessageDeliveriesDecay,
      final double meshMessageDeliveriesThreshold,
      final double meshMessageDeliveriesCap,
      final Duration meshMessageDeliveriesActivation,
      final Duration meshMessageDeliveryWindow,
      final double meshFailurePenaltyWeight,
      final double meshFailurePenaltyDecay,
      final double invalidMessageDeliveriesWeight,
      final double invalidMessageDeliveriesDecay) {
    this.topicWeight = topicWeight;
    this.timeInMeshWeight = timeInMeshWeight;
    this.timeInMeshQuantum = timeInMeshQuantum;
    this.timeInMeshCap = timeInMeshCap;
    this.firstMessageDeliveriesWeight = firstMessageDeliveriesWeight;
    this.firstMessageDeliveriesDecay = firstMessageDeliveriesDecay;
    this.firstMessageDeliveriesCap = firstMessageDeliveriesCap;
    this.meshMessageDeliveriesWeight = meshMessageDeliveriesWeight;
    this.meshMessageDeliveriesDecay = meshMessageDeliveriesDecay;
    this.meshMessageDeliveriesThreshold = meshMessageDeliveriesThreshold;
    this.meshMessageDeliveriesCap = meshMessageDeliveriesCap;
    this.meshMessageDeliveriesActivation = meshMessageDeliveriesActivation;
    this.meshMessageDeliveryWindow = meshMessageDeliveryWindow;
    this.meshFailurePenaltyWeight = meshFailurePenaltyWeight;
    this.meshFailurePenaltyDecay = meshFailurePenaltyDecay;
    this.invalidMessageDeliveriesWeight = invalidMessageDeliveriesWeight;
    this.invalidMessageDeliveriesDecay = invalidMessageDeliveriesDecay;
  }

  public static Builder builder() {
    return new Builder();
  }

  public double getTopicWeight() {
    return topicWeight;
  }

  public double getTimeInMeshWeight() {
    return timeInMeshWeight;
  }

  public Duration getTimeInMeshQuantum() {
    return timeInMeshQuantum;
  }

  public double getTimeInMeshCap() {
    return timeInMeshCap;
  }

  public double getFirstMessageDeliveriesWeight() {
    return firstMessageDeliveriesWeight;
  }

  public double getFirstMessageDeliveriesDecay() {
    return firstMessageDeliveriesDecay;
  }

  public double getFirstMessageDeliveriesCap() {
    return firstMessageDeliveriesCap;
  }

  public double getMeshMessageDeliveriesWeight() {
    return meshMessageDeliveriesWeight;
  }

  public double getMeshMessageDeliveriesDecay() {
    return meshMessageDeliveriesDecay;
  }

  public double getMeshMessageDeliveriesThreshold() {
    return meshMessageDeliveriesThreshold;
  }

  public double getMeshMessageDeliveriesCap() {
    return meshMessageDeliveriesCap;
  }

  public Duration getMeshMessageDeliveriesActivation() {
    return meshMessageDeliveriesActivation;
  }

  public Duration getMeshMessageDeliveryWindow() {
    return meshMessageDeliveryWindow;
  }

  public double getMeshFailurePenaltyWeight() {
    return meshFailurePenaltyWeight;
  }

  public double getMeshFailurePenaltyDecay() {
    return meshFailurePenaltyDecay;
  }

  public double getInvalidMessageDeliveriesWeight() {
    return invalidMessageDeliveriesWeight;
  }

  public double getInvalidMessageDeliveriesDecay() {
    return invalidMessageDeliveriesDecay;
  }

  public static class Builder {
    private Double topicWeight = 0.0;
    private Double timeInMeshWeight = 0.0;
    private Duration timeInMeshQuantum = Duration.ofSeconds(1);
    private Double timeInMeshCap = 0.0;
    private Double firstMessageDeliveriesWeight = 0.0;
    private Double firstMessageDeliveriesDecay = 0.0;
    private Double firstMessageDeliveriesCap = 0.0;
    private Double meshMessageDeliveriesWeight = 0.0;
    private Double meshMessageDeliveriesDecay = 0.0;
    private Double meshMessageDeliveriesThreshold = 0.0;
    private Double meshMessageDeliveriesCap = 0.0;
    private Duration meshMessageDeliveriesActivation = Duration.ofMinutes(1);
    private Duration meshMessageDeliveryWindow = Duration.ofMillis(10);
    private Double meshFailurePenaltyWeight = 0.0;
    private Double meshFailurePenaltyDecay = 0.0;
    private Double invalidMessageDeliveriesWeight = 0.0;
    private Double invalidMessageDeliveriesDecay = 0.0;

    private Builder() {}

    public GossipTopicScoringConfig build() {
      return new GossipTopicScoringConfig(
          topicWeight,
          timeInMeshWeight,
          timeInMeshQuantum,
          timeInMeshCap,
          firstMessageDeliveriesWeight,
          firstMessageDeliveriesDecay,
          firstMessageDeliveriesCap,
          meshMessageDeliveriesWeight,
          meshMessageDeliveriesDecay,
          meshMessageDeliveriesThreshold,
          meshMessageDeliveriesCap,
          meshMessageDeliveriesActivation,
          meshMessageDeliveryWindow,
          meshFailurePenaltyWeight,
          meshFailurePenaltyDecay,
          invalidMessageDeliveriesWeight,
          invalidMessageDeliveriesDecay);
    }

    public Builder topicWeight(final Double topicWeight) {
      checkNotNull(topicWeight);
      this.topicWeight = topicWeight;
      return this;
    }

    public Builder timeInMeshWeight(final Double timeInMeshWeight) {
      checkNotNull(timeInMeshWeight);
      this.timeInMeshWeight = timeInMeshWeight;
      return this;
    }

    public Builder timeInMeshQuantum(final Duration timeInMeshQuantum) {
      checkNotNull(timeInMeshQuantum);
      if (timeInMeshQuantum.isNegative()) {
        throw new InvalidConfigurationException(
            String.format("Invalid timeInMeshQuantum: %s", timeInMeshQuantum));
      }
      this.timeInMeshQuantum = timeInMeshQuantum;
      return this;
    }

    public Builder timeInMeshCap(final Double timeInMeshCap) {
      checkNotNull(timeInMeshCap);
      this.timeInMeshCap = timeInMeshCap;
      return this;
    }

    public Builder firstMessageDeliveriesWeight(final Double firstMessageDeliveriesWeight) {
      checkNotNull(firstMessageDeliveriesWeight);
      this.firstMessageDeliveriesWeight = firstMessageDeliveriesWeight;
      return this;
    }

    public Builder firstMessageDeliveriesDecay(final Double firstMessageDeliveriesDecay) {
      checkNotNull(firstMessageDeliveriesDecay);
      this.firstMessageDeliveriesDecay = firstMessageDeliveriesDecay;
      return this;
    }

    public Builder firstMessageDeliveriesCap(final Double firstMessageDeliveriesCap) {
      checkNotNull(firstMessageDeliveriesCap);
      this.firstMessageDeliveriesCap = firstMessageDeliveriesCap;
      return this;
    }

    public Builder meshMessageDeliveriesWeight(final Double meshMessageDeliveriesWeight) {
      checkNotNull(meshMessageDeliveriesWeight);
      this.meshMessageDeliveriesWeight = meshMessageDeliveriesWeight;
      return this;
    }

    public Builder meshMessageDeliveriesDecay(final Double meshMessageDeliveriesDecay) {
      checkNotNull(meshMessageDeliveriesDecay);
      this.meshMessageDeliveriesDecay = meshMessageDeliveriesDecay;
      return this;
    }

    public Builder meshMessageDeliveriesThreshold(final Double meshMessageDeliveriesThreshold) {
      checkNotNull(meshMessageDeliveriesThreshold);
      this.meshMessageDeliveriesThreshold = meshMessageDeliveriesThreshold;
      return this;
    }

    public Builder meshMessageDeliveriesCap(final Double meshMessageDeliveriesCap) {
      checkNotNull(meshMessageDeliveriesCap);
      this.meshMessageDeliveriesCap = meshMessageDeliveriesCap;
      return this;
    }

    public Builder meshMessageDeliveriesActivation(final Duration meshMessageDeliveriesActivation) {
      checkNotNull(meshMessageDeliveriesActivation);
      if (meshMessageDeliveriesActivation.isNegative()) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid meshMessageDeliveriesActivation: %s", meshMessageDeliveriesActivation));
      }
      this.meshMessageDeliveriesActivation = meshMessageDeliveriesActivation;
      return this;
    }

    public Builder meshMessageDeliveryWindow(final Duration meshMessageDeliveryWindow) {
      checkNotNull(meshMessageDeliveryWindow);
      if (meshMessageDeliveryWindow.isNegative()) {
        throw new InvalidConfigurationException(
            String.format("Invalid meshMessageDeliveryWindow: %s", meshMessageDeliveryWindow));
      }
      this.meshMessageDeliveryWindow = meshMessageDeliveryWindow;
      return this;
    }

    public Builder meshFailurePenaltyWeight(final Double meshFailurePenaltyWeight) {
      checkNotNull(meshFailurePenaltyWeight);
      this.meshFailurePenaltyWeight = meshFailurePenaltyWeight;
      return this;
    }

    public Builder meshFailurePenaltyDecay(final Double meshFailurePenaltyDecay) {
      checkNotNull(meshFailurePenaltyDecay);
      this.meshFailurePenaltyDecay = meshFailurePenaltyDecay;
      return this;
    }

    public Builder invalidMessageDeliveriesWeight(final Double invalidMessageDeliveriesWeight) {
      checkNotNull(invalidMessageDeliveriesWeight);
      this.invalidMessageDeliveriesWeight = invalidMessageDeliveriesWeight;
      return this;
    }

    public Builder invalidMessageDeliveriesDecay(final Double invalidMessageDeliveriesDecay) {
      checkNotNull(invalidMessageDeliveriesDecay);
      this.invalidMessageDeliveriesDecay = invalidMessageDeliveriesDecay;
      return this;
    }
  }
}
