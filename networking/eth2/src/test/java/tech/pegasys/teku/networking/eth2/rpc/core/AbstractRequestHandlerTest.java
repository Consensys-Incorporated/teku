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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

abstract class AbstractRequestHandlerTest<T extends RpcRequestHandler> {
  protected final Spec spec = TestSpecFactory.createMinimalPhase0();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  protected final PeerLookup peerLookup = mock(PeerLookup.class);
  protected final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  protected final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  protected final RecentChainData recentChainData = mock(RecentChainData.class);

  protected BeaconChainMethods beaconChainMethods;

  protected final NodeId nodeId = new MockNodeId();
  protected final RpcStream rpcStream = mock(RpcStream.class);
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected T reqHandler;

  @BeforeEach
  public void setup() {
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    beaconChainMethods =
        BeaconChainMethods.create(
            spec,
            asyncRunner,
            peerLookup,
            combinedChainDataClient,
            () -> CustodyGroupCountManager.NOOP,
            recentChainData,
            new NoOpMetricsSystem(),
            new StatusMessageFactory(spec, combinedChainDataClient, metricsSystem),
            new MetadataMessagesFactory(),
            getRpcEncoding(),
            DataColumnSidecarArchiveReconstructor.NOOP,
            DasReqRespLogger.NOOP,
            Optional.empty());

    reqHandler = createRequestHandler(beaconChainMethods);

    lenient().when(rpcStream.closeAbruptly()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.closeWriteStream()).thenReturn(SafeFuture.COMPLETE);
    lenient().when(rpcStream.writeBytes(any())).thenReturn(SafeFuture.COMPLETE);
    lenient().when(peerLookup.getConnectedPeer(nodeId)).thenReturn(Optional.of(peer));

    reqHandler.active(nodeId, rpcStream);
  }

  protected abstract T createRequestHandler(final BeaconChainMethods beaconChainMethods);

  protected abstract RpcEncoding getRpcEncoding();

  protected void deliverBytes(final Bytes bytes) {
    reqHandler.processData(nodeId, rpcStream, Utils.toByteBuf(bytes));
  }
}
