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

package tech.pegasys.teku.networking.p2p.libp2p.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.libp2p.pubsub.gossip.GossipParams;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;

public class LibP2PParamsFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  @Test
  void createGossipParams_checkZeroDsSucceed() {
    final GossipConfig gossipConfig = GossipConfig.builder().d(0).dLow(0).dHigh(0).build();

    final GossipParams gossipParams =
        LibP2PParamsFactory.createGossipParams(gossipConfig, spec.getNetworkingConfig());

    assertThat(gossipParams.getDOut()).isEqualTo(0);
  }

  @Test
  public void createGossipParams_setGossipMaxSizeFromNetworkSpecConfig() {
    final GossipConfig gossipConfig = GossipConfig.builder().build();
    final NetworkingSpecConfig networkingSpecConfig = spy(spec.getNetworkingConfig());
    final int expectedGossipMaxSize = 12234442;
    reset(networkingSpecConfig);

    final GossipParams gossipParams =
        LibP2PParamsFactory.createGossipParams(gossipConfig, networkingSpecConfig);

    assertThat(gossipParams.getMaxGossipMessageSize()).isEqualTo(expectedGossipMaxSize);
    verify(networkingSpecConfig).getMaxPayloadSize();
  }

  @Test
  void createGossipParams_configuresRpcLimits() {
    final GossipParams gossipParams =
        LibP2PParamsFactory.createGossipParams(
            GossipConfig.builder().build(), spec.getNetworkingConfig());

    assertThat(gossipParams.getMaxPublishedMessages()).isEqualTo(1000);
    assertThat(gossipParams.getMaxTopicsPerPublishedMessage()).isEqualTo(1);
    assertThat(gossipParams.getMaxIHaveLength()).isEqualTo(5000);
    assertThat(gossipParams.getMaxPeersAcceptedInPruneMsg()).isEqualTo(0);
    assertThat(gossipParams.getMaxIDontWantMessageIds()).isEqualTo(5000);
  }

  @Test
  void createGossipParams_boundsOutboundSubscriptionsToOurOwnInboundLimit() {
    final GossipParams gossipParams =
        LibP2PParamsFactory.createGossipParams(
            GossipConfig.builder().build(), spec.getNetworkingConfig());

    assertThat(gossipParams.getMaxSubscriptionsPerRpc())
        .isEqualTo(LibP2PParamsFactory.MAX_SUBSCRIPTIONS_PER_MESSAGE);
  }
}
