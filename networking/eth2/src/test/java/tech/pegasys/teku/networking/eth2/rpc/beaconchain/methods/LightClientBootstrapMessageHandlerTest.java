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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientBootstrapProvider;

class LightClientBootstrapMessageHandlerTest {
  private static final String PROTOCOL_ID =
      "/eth2/beacon_chain/req/light_client_bootstrap/1/ssz_snappy";

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final LightClientBootstrapProvider lightClientBootstrapProvider =
      mock(LightClientBootstrapProvider.class);
  private final LightClientBootstrapMessageHandler handler =
      new LightClientBootstrapMessageHandler(lightClientBootstrapProvider);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<LightClientBootstrap> callback = mock(ResponseCallback.class);

  private final Bytes32 blockRoot = Bytes32.random();

  @BeforeEach
  void setUp() {
    when(peer.approveRequest()).thenReturn(true);
  }

  @Test
  void shouldRespondWithBootstrap() {
    final LightClientBootstrap bootstrap = dataStructureUtil.randomLightClientBoostrap(UInt64.ONE);
    when(lightClientBootstrapProvider.getLightClientBootstrap(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(bootstrap)));

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockRoot), callback);

    verify(callback).respondAndCompleteSuccessfully(bootstrap);
  }

  @Test
  void shouldRespondResourceUnavailableWhenBootstrapUnavailable() {
    when(lightClientBootstrapProvider.getLightClientBootstrap(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockRoot), callback);

    final ArgumentCaptor<RpcException> error = ArgumentCaptor.forClass(RpcException.class);
    verify(callback).completeWithErrorResponse(error.capture());
    assertThat(error.getValue().getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
  }

  @Test
  void shouldIgnoreRateLimitedRequest() {
    when(peer.approveRequest()).thenReturn(false);

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(blockRoot), callback);

    verifyNoInteractions(callback, lightClientBootstrapProvider);
  }

  private static LightClientBootstrapRequestMessage request(final Bytes32 blockRoot) {
    return new LightClientBootstrapRequestMessage(blockRoot);
  }
}
