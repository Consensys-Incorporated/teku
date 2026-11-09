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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ContextPayloadMismatchException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkDigestRpcContextCodecTest {
  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.valueOf(2);
  private static final Bytes4 FULU_FORK_DIGEST = Bytes4.fromHexStringLenient("0x01");
  private static final Bytes4 GLOAS_FORK_DIGEST = Bytes4.fromHexStringLenient("0x02");
  private static final Bytes4 OTHER_FULU_DIGEST = Bytes4.fromHexStringLenient("0x03");

  private final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkDigestRpcContextCodec<DataColumnSidecar> sidecarCodec =
      new ForkDigestRpcContextCodec<>(
          spec, recentChainData, ForkDigestPayloadContext.DATA_COLUMN_SIDECAR);
  private final ForkDigestRpcContextCodec<SignedBeaconBlock> blockCodec =
      new ForkDigestRpcContextCodec<>(
          spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK);

  private final UInt64 lastFuluSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH).decrement();
  private final UInt64 firstGloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH);

  @BeforeEach
  void setUp() {
    when(recentChainData.getForkDigest(eq(SpecMilestone.FULU), any())).thenReturn(FULU_FORK_DIGEST);
    when(recentChainData.getForkDigest(eq(SpecMilestone.GLOAS), any()))
        .thenReturn(GLOAS_FORK_DIGEST);
  }

  @Test
  void verifyPayloadMatchesContext_acceptsSidecarWithTheDigestOfItsSlot() {
    final DataColumnSidecar fuluSidecar = sidecarAtSlot(lastFuluSlot);
    final DataColumnSidecar gloasSidecar = sidecarAtSlot(firstGloasSlot);

    assertThatCode(() -> sidecarCodec.verifyPayloadMatchesContext(FULU_FORK_DIGEST, fuluSidecar))
        .doesNotThrowAnyException();
    assertThatCode(() -> sidecarCodec.verifyPayloadMatchesContext(GLOAS_FORK_DIGEST, gloasSidecar))
        .doesNotThrowAnyException();
  }

  @Test
  void verifyPayloadMatchesContext_rejectsSidecarFromALaterForkThanTheContext() {
    final DataColumnSidecar gloasSidecar = sidecarAtSlot(firstGloasSlot);

    assertThatThrownBy(
            () -> sidecarCodec.verifyPayloadMatchesContext(FULU_FORK_DIGEST, gloasSidecar))
        .isInstanceOf(ContextPayloadMismatchException.class)
        .hasMessageContaining(FULU_FORK_DIGEST.toHexString())
        .hasMessageContaining(GLOAS_FORK_DIGEST.toHexString());
  }

  @Test
  void verifyPayloadMatchesContext_rejectsSidecarFromAnEarlierForkThanTheContext() {
    final DataColumnSidecar fuluSidecar = sidecarAtSlot(lastFuluSlot);

    assertThatThrownBy(
            () -> sidecarCodec.verifyPayloadMatchesContext(GLOAS_FORK_DIGEST, fuluSidecar))
        .isInstanceOf(ContextPayloadMismatchException.class);
  }

  @Test
  void verifyPayloadMatchesContext_rejectsAnyDigestOtherThanTheSlotsOwn() {
    final DataColumnSidecar fuluSidecar = sidecarAtSlot(lastFuluSlot);

    assertThatThrownBy(
            () -> sidecarCodec.verifyPayloadMatchesContext(OTHER_FULU_DIGEST, fuluSidecar))
        .isInstanceOf(ContextPayloadMismatchException.class);
  }

  @Test
  void verifyPayloadMatchesContext_toleratesMismatchForPayloadsThatDoNotRequireIt() {
    final SignedBeaconBlock fuluBlock = dataStructureUtil.randomSignedBeaconBlock(lastFuluSlot);
    final SignedBeaconBlock gloasBlock = dataStructureUtil.randomSignedBeaconBlock(firstGloasSlot);

    assertThatCode(() -> blockCodec.verifyPayloadMatchesContext(FULU_FORK_DIGEST, gloasBlock))
        .doesNotThrowAnyException();
    assertThatCode(() -> blockCodec.verifyPayloadMatchesContext(GLOAS_FORK_DIGEST, fuluBlock))
        .doesNotThrowAnyException();
  }

  private DataColumnSidecar sidecarAtSlot(final UInt64 slot) {
    return dataStructureUtil.randomDataColumnSidecar(
        dataStructureUtil.randomSignedBeaconBlockHeader(slot), UInt64.ZERO);
  }
}
