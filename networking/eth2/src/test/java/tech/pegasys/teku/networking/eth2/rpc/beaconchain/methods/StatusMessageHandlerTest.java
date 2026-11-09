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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler.NODE_NOT_READY;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageSchemaFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;

class StatusMessageHandlerTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();

  private static final StatusMessage LOCAL_STATUS = statusMessagePhase0("0x22", "0x22");
  private static final StatusMessage REMOTE_STATUS = statusMessagePhase0("0x11", "0x11");
  private static final PeerStatus PEER_STATUS = PeerStatus.fromStatusMessage(REMOTE_STATUS);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<StatusMessage> callback = mock(ResponseCallback.class);

  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  private static final String PROTOCOL_ID_V1 = protocolIdForVersion(1);
  private static final String PROTOCOL_ID_V2 = protocolIdForVersion(2);
  private final StatusMessageHandler handler = new StatusMessageHandler(spec, statusMessageFactory);

  @BeforeEach
  public void setUp() {
    when(statusMessageFactory.createStatusMessage(any(StatusMessageSchema.class)))
        .thenReturn(Optional.of(LOCAL_STATUS));
    when(peer.approveRequest()).thenReturn(true);
  }

  @Test
  public void shouldReturnLocalStatus() {
    when(statusMessageFactory.createStatusMessage(any(StatusMessageSchema.class)))
        .thenReturn(Optional.of(LOCAL_STATUS));
    handler.onIncomingMessage(PROTOCOL_ID_V1, peer, REMOTE_STATUS, callback);

    verify(peer).updateStatus(PEER_STATUS);
    verify(callback).respondAndCompleteSuccessfully(LOCAL_STATUS);
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldRespondWithErrorIfStatusUnavailable() {
    when(statusMessageFactory.createStatusMessage(any(StatusMessageSchema.class)))
        .thenReturn(Optional.empty());
    handler.onIncomingMessage(PROTOCOL_ID_V1, peer, REMOTE_STATUS, callback);

    verify(peer).updateStatus(PEER_STATUS);
    verify(callback).completeWithErrorResponse(NODE_NOT_READY);
  }

  @Test
  public void shouldFailWhenHandlingInvalidVersion() {
    final String invalidVersion = protocolIdForVersion(99);
    assertThatThrownBy(
            () -> handler.onIncomingMessage(invalidVersion, peer, REMOTE_STATUS, callback))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unexpected protocol version: 99");
  }

  @ParameterizedTest
  @SuppressWarnings("unchecked")
  @MethodSource("protocolVersionAndSchemaSource")
  public void shouldHandleDifferentVersionsOfStatusMethod(
      final String protocolId,
      final StatusMessage statusMessage,
      final Class<?> statusMessageClass) {
    when(statusMessageFactory.createStatusMessage(any(StatusMessageSchema.class)))
        .thenReturn(Optional.of(statusMessage));

    handler.onIncomingMessage(protocolId, peer, REMOTE_STATUS, callback);

    ArgumentCaptor<StatusMessageSchema<?>> argumentCaptor =
        ArgumentCaptor.forClass(StatusMessageSchema.class);

    verify(statusMessageFactory).createStatusMessage(argumentCaptor.capture());

    final StatusMessageSchema<?> statusMessageSchema = argumentCaptor.getValue();
    assertThat(statusMessageSchema).isInstanceOf(statusMessageClass);
  }

  private static Stream<Arguments> protocolVersionAndSchemaSource() {
    return Stream.of(
        Arguments.of(PROTOCOL_ID_V1, statusMessagePhase0(), StatusMessageSchemaPhase0.class),
        Arguments.of(PROTOCOL_ID_V2, statusMessageFulu(), StatusMessageSchemaFulu.class));
  }

  private static StatusMessagePhase0 statusMessagePhase0() {
    return statusMessagePhase0("0x22", "0x22");
  }

  private static StatusMessagePhase0 statusMessagePhase0(
      final String finalizedRoot, final String headRoot) {
    return new StatusMessagePhase0(
        Bytes4.rightPad(Bytes.of(4)),
        Bytes32.fromHexStringLenient(finalizedRoot),
        UInt64.ZERO,
        Bytes32.fromHexStringLenient(headRoot),
        UInt64.ZERO);
  }

  private static StatusMessageFulu statusMessageFulu() {
    return new StatusMessageFulu(
        Bytes4.rightPad(Bytes.of(4)),
        Bytes32.fromHexStringLenient("0x22"),
        UInt64.ZERO,
        Bytes32.fromHexStringLenient("0x22"),
        UInt64.ZERO,
        UInt64.ONE);
  }

  private static String protocolIdForVersion(final int version) {
    return BeaconChainMethodIds.getStatusMethodId(
        version,
        RpcEncoding.createSszSnappyEncoding(
            TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize()));
  }
}
