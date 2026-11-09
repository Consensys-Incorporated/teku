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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class LightClientLatestUpdateMessageHandlerTest {
  private static final String PROTOCOL_ID =
      "/eth2/beacon_chain/req/light_client_finality_update/1/ssz_snappy";

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final AtomicReference<Optional<LightClientFinalityUpdate>> latest =
      new AtomicReference<>(Optional.empty());
  private final LightClientLatestUpdateMessageHandler<LightClientFinalityUpdate> handler =
      new LightClientLatestUpdateMessageHandler<>("light client finality update", latest::get);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<LightClientFinalityUpdate> callback = mock(ResponseCallback.class);

  @BeforeEach
  void setUp() {
    when(peer.approveRequest()).thenReturn(true);
  }

  @Test
  void shouldRespondWithLatestUpdate() {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(UInt64.valueOf(9));
    latest.set(Optional.of(update));

    handler.onIncomingMessage(PROTOCOL_ID, peer, EmptyMessage.EMPTY_MESSAGE, callback);

    verify(callback).respondAndCompleteSuccessfully(update);
    verify(callback, never()).completeWithErrorResponse(any());
  }

  @Test
  void shouldRespondResourceUnavailableWhenNoUpdate() {
    handler.onIncomingMessage(PROTOCOL_ID, peer, EmptyMessage.EMPTY_MESSAGE, callback);

    final ArgumentCaptor<RpcException> error = ArgumentCaptor.forClass(RpcException.class);
    verify(callback).completeWithErrorResponse(error.capture());
    assertThat(error.getValue().getResponseCode()).isEqualTo(RESOURCE_UNAVAILABLE);
  }

  @Test
  void shouldIgnoreRateLimitedRequest() {
    when(peer.approveRequest()).thenReturn(false);

    handler.onIncomingMessage(PROTOCOL_ID, peer, EmptyMessage.EMPTY_MESSAGE, callback);

    verifyNoInteractions(callback);
  }
}
