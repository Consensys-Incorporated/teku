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
import static org.assertj.core.api.Assertions.catchThrowable;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2RpcResponseHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientUpdatesByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;

public class LightClientReqRespIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(3);
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();
  private final LightClientUpdateStore serverStore = new LightClientUpdateStore(spec);

  private NodeManager server;
  private Eth2Peer serverPeer;
  private BeaconChainMethods methods;

  @BeforeEach
  public void setUp() throws Exception {
    server =
        NodeManager.create(
            spec,
            asyncRunner,
            networkFactory,
            validatorKeys,
            b -> b.lightClientUpdateStore(serverStore));
    final NodeManager client =
        NodeManager.create(
            spec,
            asyncRunner,
            networkFactory,
            validatorKeys,
            b -> b.lightClientUpdateStore(new LightClientUpdateStore(spec)));

    waitFor(client.connect(server));
    waitFor(() -> assertThat(client.network().getPeerCount()).isEqualTo(1));
    serverPeer = client.network().getPeer(server.network().getNodeId()).orElseThrow();
    methods = ((ActiveEth2P2PNetwork) client.network()).getBeaconChainMethods();
  }

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldReturnLatestFinalityUpdate() throws Exception {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            UInt64.valueOf(9), UInt64.ONE, UInt64.valueOf(10));
    serverStore.addFinalityUpdate(update, Bytes32.ZERO, (slot, root) -> true);

    assertThat(waitFor(requestFinalityUpdate())).isEqualTo(update);
  }

  @Test
  public void shouldReturnResourceUnavailableWhenNoFinalityUpdate() {
    assertResourceUnavailable(requestFinalityUpdate());
  }

  @Test
  public void shouldReturnLatestOptimisticUpdate() throws Exception {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(UInt64.valueOf(9), UInt64.valueOf(10));
    serverStore.addOptimisticUpdate(update, Bytes32.ZERO, (slot, root) -> true);

    assertThat(waitFor(requestOptimisticUpdate())).isEqualTo(update);
  }

  @Test
  public void shouldReturnResourceUnavailableWhenNoOptimisticUpdate() {
    assertResourceUnavailable(requestOptimisticUpdate());
  }

  @Test
  public void shouldReturnConsecutiveBestUpdatesByRange() throws Exception {
    final LightClientUpdate period0 = updateInPeriod(0);
    final LightClientUpdate period1 = updateInPeriod(1);
    serverStore.addUpdate(period0, Bytes32.ZERO, (slot, root) -> true);
    serverStore.addUpdate(period1, Bytes32.ZERO, (slot, root) -> true);

    assertThat(requestUpdatesByRange(UInt64.ZERO, UInt64.valueOf(5)))
        .containsExactly(period0, period1);
  }

  @Test
  public void shouldReturnNoUpdatesByRangeWhenNoneKnown() throws Exception {
    assertThat(requestUpdatesByRange(UInt64.ZERO, UInt64.valueOf(5))).isEmpty();
  }

  @Test
  public void shouldReturnBootstrapForFinalizedEpochBoundaryBlock() throws Exception {
    server.getChainUpdater().advanceChain(1);
    server
        .getChainUpdater()
        .updateBestBlock(
            server
                .getChainUpdater()
                .advanceChain(spec.computeStartSlotAtEpoch(UInt64.ONE).plus(1)));
    final SignedBlockAndState blockAndState = server.getChainUpdater().finalizeEpoch(1);

    final LightClientBootstrap bootstrap = waitFor(requestBootstrap(blockAndState.getRoot()));

    assertThat(bootstrap)
        .isEqualTo(
            spec.getLightClientUtilRequired(blockAndState.getSlot())
                .createLightClientBootstrap(blockAndState.getState(), blockAndState.getBlock()));
  }

  @Test
  public void shouldReturnResourceUnavailableForUnknownBootstrapBlock() {
    assertResourceUnavailable(requestBootstrap(Bytes32.random()));
  }

  private SafeFuture<LightClientFinalityUpdate> requestFinalityUpdate() {
    return serverPeer.requestSingleItem(
        methods.lightClientFinalityUpdate().orElseThrow(), EmptyMessage.EMPTY_MESSAGE);
  }

  private SafeFuture<LightClientOptimisticUpdate> requestOptimisticUpdate() {
    return serverPeer.requestSingleItem(
        methods.lightClientOptimisticUpdate().orElseThrow(), EmptyMessage.EMPTY_MESSAGE);
  }

  private SafeFuture<LightClientBootstrap> requestBootstrap(final Bytes32 blockRoot) {
    return serverPeer.requestSingleItem(
        methods.lightClientBootstrap().orElseThrow(),
        new LightClientBootstrapRequestMessage(blockRoot));
  }

  private List<LightClientUpdate> requestUpdatesByRange(
      final UInt64 startPeriod, final UInt64 count) throws Exception {
    final List<LightClientUpdate> received = new ArrayList<>();
    final Eth2RpcResponseHandler<LightClientUpdate, Void> responseHandler =
        Eth2RpcResponseHandler.expectMultipleResponses(RpcResponseListener.from(received::add));
    waitFor(
        serverPeer
            .sendRequest(
                methods.lightClientUpdatesByRange().orElseThrow(),
                new LightClientUpdatesByRangeRequestMessage(startPeriod, count),
                responseHandler)
            .thenCompose(
                controller -> {
                  controller
                      .getRequiredOutgoingRequestHandler()
                      .handleInitialPayloadSent(controller.getRpcStream());
                  return controller.getRequiredOutgoingRequestHandler().getCompletedFuture();
                })
            .thenCompose(__ -> responseHandler.getResult()));
    return received;
  }

  private LightClientUpdate updateInPeriod(final int period) {
    final long slotsPerPeriod =
        (long)
                SpecConfigAltair.required(spec.getGenesisSpecConfig())
                    .getEpochsPerSyncCommitteePeriod()
            * spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final UInt64 attestedSlot = UInt64.valueOf(period * slotsPerPeriod + 1);
    return dataStructureUtil
        .createRandomLightClientUpdateBuilder(attestedSlot)
        .attestedSlot(attestedSlot)
        .signatureSlot(attestedSlot.plus(1))
        .build();
  }

  private static void assertResourceUnavailable(final SafeFuture<?> future) {
    final Throwable error = catchThrowable(() -> waitFor(future));
    assertThat(error).hasCauseInstanceOf(RpcException.class);
    assertThat(((RpcException) error.getCause()).getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
  }
}
