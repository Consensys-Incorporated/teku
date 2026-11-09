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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage.DecodedMessagePrefix;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.FutureBlockPool;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(allMilestones = true)
class FarFutureBlockFilterTest {
  private static final UInt64 CURRENT_SLOT = UInt64.valueOf(10);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;
  private FarFutureBlockFilter filter;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem.chainUpdater().setCurrentSlot(CURRENT_SLOT);
    filter = new FarFutureBlockFilter(spec, storageSystem.recentChainData(), metricsSystem);
  }

  @TestTemplate
  void getSlot_shouldReadSlotFromSerializedBlock() {
    final UInt64 slot = UInt64.valueOf(123_456_789);
    final Bytes serialized = dataStructureUtil.randomSignedBeaconBlock(slot).sszSerialize();

    assertThat(FarFutureBlockFilter.getSlot(prefixOf(serialized))).contains(slot);
  }

  @TestTemplate
  void getSlot_shouldBeEmptyWhenPrefixIsTooShort() {
    final Bytes serialized = dataStructureUtil.randomSignedBeaconBlock(1).sszSerialize();

    assertThat(
            FarFutureBlockFilter.getSlot(
                serialized.slice(0, FarFutureBlockFilter.MESSAGE_OFFSET + 7)))
        .isEmpty();
  }

  @TestTemplate
  void getSlot_shouldBeEmptyWhenMessageOffsetIsUnexpected() {
    final Bytes serialized = dataStructureUtil.randomSignedBeaconBlock(1).sszSerialize();
    final Bytes wrongOffset =
        Bytes.concatenate(Bytes.fromHexString("0x65000000"), serialized.slice(4));

    assertThat(FarFutureBlockFilter.getSlot(prefixOf(wrongOffset))).isEmpty();
  }

  @TestTemplate
  void shouldProcess_shouldAcceptBlocksUpToFutureSlotTolerance() {
    assertThat(filter.shouldProcess(blockPrefix(CURRENT_SLOT))).isTrue();
    assertThat(filter.shouldProcess(blockPrefix(UInt64.ZERO))).isTrue();
    assertThat(
            filter.shouldProcess(
                blockPrefix(CURRENT_SLOT.plus(FutureBlockPool.FUTURE_SLOT_TOLERANCE))))
        .isTrue();
    assertThat(getIgnoredCount()).isZero();
  }

  @TestTemplate
  void shouldProcess_shouldAllowForClockDisparity() {
    // just before the next slot starts, a block for the slot after the tolerance is accepted
    final UInt64 nextSlotStartMillis =
        spec.computeTimeMillisAtSlot(
            CURRENT_SLOT.plus(1), storageSystem.recentChainData().getGenesisTimeMillis());
    storageSystem.chainUpdater().setTimeMillis(nextSlotStartMillis.minus(1));

    assertThat(
            filter.shouldProcess(
                blockPrefix(CURRENT_SLOT.plus(FutureBlockPool.FUTURE_SLOT_TOLERANCE).plus(1))))
        .isTrue();
  }

  @TestTemplate
  void shouldProcess_shouldIgnoreBlocksBeyondFutureSlotTolerance() {
    assertThat(
            filter.shouldProcess(
                blockPrefix(CURRENT_SLOT.plus(FutureBlockPool.FUTURE_SLOT_TOLERANCE).plus(1))))
        .isFalse();
    assertThat(filter.shouldProcess(blockPrefix(UInt64.valueOf(1_000_000)))).isFalse();
    assertThat(getIgnoredCount()).isEqualTo(2);
  }

  @TestTemplate
  void shouldProcess_shouldLeaveMalformedBlocksToDecoder() {
    assertThat(filter.shouldProcess(Bytes.fromHexString("0x0102"))).isTrue();
  }

  private Bytes blockPrefix(final UInt64 slot) {
    return prefixOf(dataStructureUtil.randomSignedBeaconBlock(slot).sszSerialize());
  }

  private static Bytes prefixOf(final Bytes serialized) {
    return DecodedMessagePrefix.of(serialized).prefix();
  }

  private long getIgnoredCount() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK, "gossip_far_future_blocks_ignored_total");
  }
}
