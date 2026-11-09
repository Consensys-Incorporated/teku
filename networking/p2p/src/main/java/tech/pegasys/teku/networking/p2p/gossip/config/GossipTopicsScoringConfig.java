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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Scoring config for a collection a set of topics */
public class GossipTopicsScoringConfig {
  private final Map<String, GossipTopicScoringConfig> topicConfigs;

  private GossipTopicsScoringConfig(final Map<String, GossipTopicScoringConfig> topicConfigs) {
    this.topicConfigs = topicConfigs;
  }

  public boolean isEmpty() {
    return topicConfigs.isEmpty();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, GossipTopicScoringConfig> getTopicConfigs() {
    return topicConfigs;
  }

  public static class Builder {
    private final Map<String, GossipTopicScoringConfig.Builder> topicScoringConfigBuilders =
        new HashMap<>();

    public GossipTopicsScoringConfig build() {
      final Map<String, GossipTopicScoringConfig> topicConfig =
          topicScoringConfigBuilders.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
      return new GossipTopicsScoringConfig(topicConfig);
    }

    public Builder topicScoring(
        final String topic, final Consumer<GossipTopicScoringConfig.Builder> consumer) {
      GossipTopicScoringConfig.Builder builder =
          topicScoringConfigBuilders.computeIfAbsent(
              topic, __ -> GossipTopicScoringConfig.builder());
      consumer.accept(builder);
      return this;
    }

    public Builder clear() {
      topicScoringConfigBuilders.clear();
      return this;
    }
  }
}
