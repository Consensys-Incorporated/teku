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
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconBlocksByRootListenerValidatingProxyTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<SignedBeaconBlock> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  void shouldAcceptRequestedBlockRoots() {
    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    final BeaconBlocksByRootListenerValidatingProxy listenerWrapper =
        new BeaconBlocksByRootListenerValidatingProxy(
            peer, listener, List.of(block1.getRoot(), block2.getRoot()));

    assertDoesNotThrow(() -> listenerWrapper.onResponse(block1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block2).join());

    verify(listener).onResponse(block1);
    verify(listener).onResponse(block2);
  }

  @Test
  void shouldRejectUnexpectedBlockRoot() {
    final SignedBeaconBlock requestedBlock = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock unexpectedBlock = dataStructureUtil.randomSignedBeaconBlock(2);
    final BeaconBlocksByRootListenerValidatingProxy listenerWrapper =
        new BeaconBlocksByRootListenerValidatingProxy(
            peer, listener, List.of(requestedBlock.getRoot()));

    final SafeFuture<?> result = listenerWrapper.onResponse(unexpectedBlock);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRootResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRootResponseInvalidResponseException.InvalidResponseType.BLOCK_UNEXPECTED_ROOT
                .describe());
    verify(listener, never()).onResponse(any());
  }

  @Test
  void shouldRejectDuplicateBlockRootResponse() {
    final SignedBeaconBlock requestedBlock = dataStructureUtil.randomSignedBeaconBlock(1);
    final BeaconBlocksByRootListenerValidatingProxy listenerWrapper =
        new BeaconBlocksByRootListenerValidatingProxy(
            peer, listener, List.of(requestedBlock.getRoot()));

    assertDoesNotThrow(() -> listenerWrapper.onResponse(requestedBlock).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(requestedBlock);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRootResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRootResponseInvalidResponseException.InvalidResponseType.BLOCK_UNEXPECTED_ROOT
                .describe());
    verify(listener).onResponse(requestedBlock);
  }
}
