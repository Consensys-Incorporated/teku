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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.ExecutionPayloadEnvelopesByRootResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionPayloadEnvelopesByRootListenerValidatingProxyTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<SignedExecutionPayloadEnvelope> listener =
      mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  void shouldAcceptRequestedBeaconBlockRoots() {
    final SignedExecutionPayloadEnvelope envelope1 =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final SignedExecutionPayloadEnvelope envelope2 =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(2);
    final ExecutionPayloadEnvelopesByRootListenerValidatingProxy listenerWrapper =
        new ExecutionPayloadEnvelopesByRootListenerValidatingProxy(
            peer,
            listener,
            List.of(envelope1.getBeaconBlockRoot(), envelope2.getBeaconBlockRoot()));

    assertDoesNotThrow(() -> listenerWrapper.onResponse(envelope1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(envelope2).join());

    verify(listener).onResponse(envelope1);
    verify(listener).onResponse(envelope2);
  }

  @Test
  void shouldRejectUnexpectedBeaconBlockRoot() {
    final SignedExecutionPayloadEnvelope unexpectedEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final ExecutionPayloadEnvelopesByRootListenerValidatingProxy listenerWrapper =
        new ExecutionPayloadEnvelopesByRootListenerValidatingProxy(
            peer, listener, List.of(dataStructureUtil.randomBytes32()));

    final SafeFuture<?> result = listenerWrapper.onResponse(unexpectedEnvelope);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(
            ExecutionPayloadEnvelopesByRootResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(InvalidResponseType.ENVELOPE_UNEXPECTED_BEACON_BLOCK_ROOT.describe());
    verify(listener, never()).onResponse(any());
  }

  @Test
  void shouldRejectDuplicateBeaconBlockRootResponse() {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final ExecutionPayloadEnvelopesByRootListenerValidatingProxy listenerWrapper =
        new ExecutionPayloadEnvelopesByRootListenerValidatingProxy(
            peer, listener, List.of(envelope.getBeaconBlockRoot()));

    assertDoesNotThrow(() -> listenerWrapper.onResponse(envelope).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(envelope);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(
            ExecutionPayloadEnvelopesByRootResponseInvalidResponseException.class);
    verify(listener).onResponse(envelope);
  }
}
