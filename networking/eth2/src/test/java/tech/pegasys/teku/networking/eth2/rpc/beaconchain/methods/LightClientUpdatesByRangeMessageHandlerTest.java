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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientUpdatesByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;

class LightClientUpdatesByRangeMessageHandlerTest {
  private static final String PROTOCOL_ID =
      "/eth2/beacon_chain/req/light_client_updates_by_range/1/ssz_snappy";

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final LightClientUpdateStore store = mock(LightClientUpdateStore.class);
  private final LightClientUpdatesByRangeMessageHandler handler =
      new LightClientUpdatesByRangeMessageHandler(store);

  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final RequestKey requestKey = new RequestKey(UInt64.ZERO, 0);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<LightClientUpdate> callback = mock(ResponseCallback.class);

  @BeforeEach
  void setUp() {
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveLightClientUpdatesRequest(any(), anyLong()))
        .thenReturn(Optional.of(requestKey));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void shouldRespondWithUpdatesInOrderThenComplete() {
    final LightClientUpdate update1 = dataStructureUtil.randomLightClientUpdate(UInt64.ONE);
    final LightClientUpdate update2 = dataStructureUtil.randomLightClientUpdate(UInt64.ONE);
    when(store.getBestUpdatesInRange(UInt64.valueOf(3), 2)).thenReturn(List.of(update1, update2));

    handler.onIncomingMessage(
        PROTOCOL_ID, peer, request(UInt64.valueOf(3), UInt64.valueOf(2)), callback);

    final InOrder inOrder = inOrder(callback);
    inOrder.verify(callback).respond(update1);
    inOrder.verify(callback).respond(update2);
    inOrder.verify(callback).completeSuccessfully();
    verify(peer).approveLightClientUpdatesRequest(callback, 2);
    verify(peer, never()).adjustLightClientUpdatesRequest(any(), anyLong());
  }

  @Test
  void shouldCapCountAtMaxRequestLightClientUpdates() {
    when(store.getBestUpdatesInRange(any(), anyInt())).thenReturn(List.of());

    handler.onIncomingMessage(
        PROTOCOL_ID, peer, request(UInt64.ZERO, UInt64.valueOf(1000)), callback);

    verify(store).getBestUpdatesInRange(UInt64.ZERO, MAX_REQUEST_LIGHT_CLIENT_UPDATES);
    verify(peer).approveLightClientUpdatesRequest(callback, MAX_REQUEST_LIGHT_CLIENT_UPDATES);
    verify(peer).adjustLightClientUpdatesRequest(requestKey, 0);
    verify(callback).completeSuccessfully();
  }

  @Test
  void shouldCompleteEmptyWhenCountIsZero() {
    handler.onIncomingMessage(PROTOCOL_ID, peer, request(UInt64.ONE, UInt64.ZERO), callback);

    verifyNoInteractions(store);
    verify(callback).completeSuccessfully();
  }

  @Test
  void shouldCompleteEmptyWhenRangeWouldOverflow() {
    handler.onIncomingMessage(PROTOCOL_ID, peer, request(UInt64.MAX_VALUE, UInt64.ONE), callback);

    verifyNoInteractions(store);
    verify(callback).completeSuccessfully();
  }

  @Test
  void shouldIgnoreRateLimitedRequest() {
    when(peer.approveRequest()).thenReturn(false);

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(UInt64.ZERO, UInt64.ONE), callback);

    verifyNoInteractions(store, callback);
  }

  @Test
  void shouldIgnoreRequestWhenObjectsRateLimited() {
    when(peer.approveLightClientUpdatesRequest(any(), anyLong())).thenReturn(Optional.empty());

    handler.onIncomingMessage(PROTOCOL_ID, peer, request(UInt64.ZERO, UInt64.ONE), callback);

    verifyNoInteractions(store, callback);
  }

  private static LightClientUpdatesByRangeRequestMessage request(
      final UInt64 startPeriod, final UInt64 count) {
    return new LightClientUpdatesByRangeRequestMessage(startPeriod, count);
  }
}
