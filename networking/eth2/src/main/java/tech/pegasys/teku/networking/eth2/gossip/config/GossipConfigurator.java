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

package tech.pegasys.teku.networking.eth2.gossip.config;

import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig;
import tech.pegasys.teku.spec.Spec;

public interface GossipConfigurator {
  GossipConfigurator NOOP =
      new GossipConfigurator() {
        @Override
        public void configure(
            final GossipConfig.Builder gossipConfigBuilder, final Eth2Context eth2Context) {}

        @Override
        public void configureAllTopics(
            final GossipTopicsScoringConfig.Builder topicsConfigBuilder,
            final Eth2Context eth2Context) {}

        @Override
        public void configureDynamicTopics(
            final GossipTopicsScoringConfig.Builder topicsConfigBuilder,
            final Eth2Context eth2Context) {}
      };

  static GossipConfigurator scoringEnabled(final Spec spec) {
    return new GossipScoringConfigurator(spec);
  }

  /**
   * Configure gossip
   *
   * @param gossipConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  void configure(final GossipConfig.Builder gossipConfigBuilder, final Eth2Context eth2Context);

  /**
   * Configure scoring for all topics
   *
   * @param topicsConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  void configureAllTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context);

  /**
   * Configure scoring for all topics
   *
   * @param eth2Context Contextual information about the current chain
   */
  default GossipTopicsScoringConfig configureAllTopics(final Eth2Context eth2Context) {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configureAllTopics(builder, eth2Context);
    return builder.build();
  }

  /**
   * Configure scoring for dynamic topics that should be updated over time
   *
   * @param topicsConfigBuilder The builder to be configured
   * @param eth2Context Contextual information about the current chain
   */
  void configureDynamicTopics(
      final GossipTopicsScoringConfig.Builder topicsConfigBuilder, final Eth2Context eth2Context);

  /**
   * Configure scoring for dynamic topics that should be updated over time
   *
   * @param eth2Context Contextual information about the current chain
   */
  default GossipTopicsScoringConfig configureDynamicTopics(final Eth2Context eth2Context) {
    final GossipTopicsScoringConfig.Builder builder = GossipTopicsScoringConfig.builder();
    configureDynamicTopics(builder, eth2Context);
    return builder.build();
  }
}
