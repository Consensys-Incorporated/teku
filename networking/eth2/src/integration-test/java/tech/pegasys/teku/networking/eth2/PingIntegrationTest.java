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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManagerAccess;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class PingIntegrationTest {
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private Eth2P2PNetwork network1;
  private Eth2P2PNetwork network2;
  private Eth2Peer peer1;
  private Eth2Peer peer2;
  private final Spec spec = TestSpecFactory.createDefault();
  private final int attestationSubnetCount = spec.getNetworkingConfig().getAttestationSubnetCount();

  public void setUp(final Duration pingInterval) throws Exception {
    network1 = networkFactory.builder().eth2RpcPingInterval(pingInterval).startNetwork();
    network2 =
        networkFactory.builder().eth2RpcPingInterval(pingInterval).peer(network1).startNetwork();
    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void testPingSimple() throws Exception {
    setUp(Duration.ofDays(1));

    peer1.sendPing().get(10, TimeUnit.SECONDS);
    // response received in 10 seconds
  }

  @Test
  public void testPingUpdatesMetadata() throws Exception {
    setUp(Duration.ofDays(1));

    Optional<SszBitvector> initialAttNets1 = peer1.getRemoteAttestationSubnets();
    Optional<SszBitvector> initialAttNets2 = peer2.getRemoteAttestationSubnets();

    assertThat(initialAttNets1.isEmpty() || initialAttNets1.get().getBitCount() == 0).isTrue();
    assertThat(initialAttNets2.isEmpty() || initialAttNets2.get().getBitCount() == 0).isTrue();

    MetadataMessage md1 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);
    MetadataMessage md2 = peer1.requestMetadata().get(10, TimeUnit.SECONDS);

    UInt64 firstPing1 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UInt64 firstPing2 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(firstPing1).isEqualTo(md1.getSeqNumber());
    assertThat(firstPing2).isEqualTo(md2.getSeqNumber());

    UInt64 secondPing1 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    UInt64 secondPing2 = peer2.sendPing().get(10, TimeUnit.SECONDS);

    assertThat(secondPing1).isEqualTo(md1.getSeqNumber());
    assertThat(secondPing2).isEqualTo(md2.getSeqNumber());

    network1.setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    Thread.sleep(100);
    UInt64 thirdPing1 = peer1.sendPing().get(10, TimeUnit.SECONDS);
    assertThat(thirdPing1).isGreaterThan(secondPing1);

    SszBitvector expectedBitvector1 =
        SszBitvectorSchema.create(attestationSubnetCount).ofBits(0, 1, 8);

    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector1));
    waitFor(() -> assertThat(peer2.getRemoteAttestationSubnets()).isNotEmpty());

    network1.setLongTermAttestationSubnetSubscriptions(List.of(2, 4));
    Thread.sleep(100);
    UInt64 thirdPing2 = peer2.sendPing().get(10, TimeUnit.SECONDS);
    assertThat(thirdPing2).isEqualTo(secondPing2);

    SszBitvector expectedBitvector2 =
        SszBitvectorSchema.create(attestationSubnetCount).ofBits(2, 4);

    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector2));
  }

  @Test
  public void testPingPeriodically() throws Exception {
    setUp(Duration.ofMillis(100));

    network1.setLongTermAttestationSubnetSubscriptions(List.of(0, 1, 8));
    SszBitvector expectedBitvector1 =
        SszBitvectorSchema.create(attestationSubnetCount).ofBits(0, 1, 8);

    // detecting that ping was automatically sent via updated att subnets of the remote peer
    waitFor(() -> assertThat(peer1.getRemoteAttestationSubnets()).contains(expectedBitvector1));
  }

  @Test
  public void testManualPingTimeout() throws Exception {
    // sending PING manually to check eth2RpcOutstandingPingThreshold works correctly
    Duration pingPeriod = Duration.ofDays(1);
    network1 =
        networkFactory
            .builder()
            .eth2RpcPingInterval(pingPeriod)
            .eth2RpcOutstandingPingThreshold(2)
            // remove PING method
            .rpcMethodsModifier(
                m ->
                    Stream.of(m)
                        .filter(t -> t.getIds().stream().noneMatch(id -> id.contains("/ping")))
                        .map(n -> ((RpcMethod<?, ?, ?>) n)))
            .startNetwork();
    network2 =
        networkFactory
            .builder()
            .eth2RpcPingInterval(pingPeriod)
            .eth2RpcOutstandingPingThreshold(2)
            .peer(network1)
            .startNetwork();

    peer1 = network2.getPeer(network1.getNodeId()).orElseThrow();
    peer2 = network1.getPeer(network2.getNodeId()).orElseThrow();

    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    assertThat(peer1.isConnected()).isTrue();
    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    assertThat(peer1.isConnected()).isTrue();
    // the 3rd ping attempt should disconnect the peer since there are 2 unanswered PING requests
    Eth2PeerManagerAccess.invokeSendPeriodicPing(getPeerManager(network2), peer1);
    waitFor(() -> assertThat(peer1.isConnected()).isFalse());
  }

  private Eth2PeerManager getPeerManager(final Eth2P2PNetwork eth2P2PNetwork) {
    return ((ActiveEth2P2PNetwork) eth2P2PNetwork).getPeerManager();
  }
}
