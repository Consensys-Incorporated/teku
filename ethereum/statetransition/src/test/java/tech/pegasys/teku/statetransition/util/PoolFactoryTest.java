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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.FutureBlocks;

class PoolFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final PoolFactory poolFactory = new PoolFactory(new StubMetricsSystem());
  private final FutureBlocks futureBlocks =
      poolFactory.createFutureBlockPool(spec, mock(SettableLabelledGauge.class));

  @Test
  void createFutureBlockPool_limitsBlocksPerSlot() {
    futureBlocks.onSlot(UInt64.ZERO);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
      blocks.add(block);
      assertThat(futureBlocks.add(block)).isTrue();
    }

    assertThat(futureBlocks.size()).isEqualTo(4);
    assertThat(futureBlocks.contains(blocks.getFirst())).isFalse();
    assertThat(futureBlocks.contains(blocks.getLast())).isTrue();
  }
}
