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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlocksByRangeListenerWrapperTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private BlocksByRangeListenerWrapper listenerWrapper;
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<SignedBeaconBlock> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp() {
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
  }

  @Test
  void blockSlotSmallerThanFromSlot() {
    UInt64 startSlot = UInt64.valueOf(1);
    UInt64 count = UInt64.valueOf(4);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(0);

    SafeFuture<?> result = listenerWrapper.onResponse(block1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blockSlotIsCorrect() {
    UInt64 startSlot = UInt64.valueOf(1);
    UInt64 count = UInt64.valueOf(4);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2, block1.getRoot());
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(3, block2.getRoot());
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(4, block3.getRoot());

    assertDoesNotThrow(() -> listenerWrapper.onResponse(block1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block2).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block3).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(block4).join());
  }

  @Test
  void blockSlotGreaterThanToSlot() {
    UInt64 startSlot = UInt64.valueOf(1);
    UInt64 count = UInt64.valueOf(8);
    // This requests 8 slots (1, 2, 3, 4, 5, 6, 7, 8) so 9 will be unexpected.
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2, block1.getRoot());
    final SignedBeaconBlock block3 = dataStructureUtil.randomSignedBeaconBlock(5, block2.getRoot());
    final SignedBeaconBlock block4 = dataStructureUtil.randomSignedBeaconBlock(8, block3.getRoot());
    final SignedBeaconBlock block5 = dataStructureUtil.randomSignedBeaconBlock(9, block4.getRoot());
    listenerWrapper.onResponse(block1).join();
    listenerWrapper.onResponse(block2).join();
    listenerWrapper.onResponse(block3).join();
    listenerWrapper.onResponse(block4).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block5);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_IN_RANGE
                .describe());
  }

  @Test
  void blockParentRootDoesNotMatch() {
    UInt64 startSlot = UInt64.valueOf(1);
    UInt64 count = UInt64.valueOf(4);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    listenerWrapper.onResponse(block1).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_PARENT_ROOT_DOES_NOT_MATCH
                .describe());
  }

  @Test
  void blockSlotGreaterThanPreviousBlockSlot() {
    UInt64 startSlot = UInt64.valueOf(1);
    UInt64 count = UInt64.valueOf(4);
    listenerWrapper = new BlocksByRangeListenerWrapper(peer, listener, startSlot, count);

    final SignedBeaconBlock block1 = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(1);

    listenerWrapper.onResponse(block1).join();

    SafeFuture<?> result = listenerWrapper.onResponse(block2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlocksByRangeResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlocksByRangeResponseInvalidResponseException.InvalidResponseType
                .BLOCK_SLOT_NOT_GREATER_THAN_PREVIOUS_BLOCK_SLOT
                .describe());
  }
}
