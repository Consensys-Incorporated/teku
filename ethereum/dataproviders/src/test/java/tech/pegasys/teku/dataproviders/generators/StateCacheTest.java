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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

public class StateCacheTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  private final int chainSize = 10;
  private final int maxSize = 2;

  private List<SignedBlockAndState> chain;
  private SignedBlockAndState knownState1;
  private SignedBlockAndState knownState2;
  private Map<Bytes32, BeaconState> knownStates;
  private StateCache cache;

  @BeforeEach
  void setup() {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(chainSize);
    chain = chainBuilder.streamBlocksAndStates().collect(Collectors.toList());

    knownState1 = chain.get(0);
    knownState2 = chain.get(1);
    knownStates =
        Map.of(
            knownState1.getRoot(),
            knownState1.getState(),
            knownState2.getRoot(),
            knownState2.getState());

    cache = new StateCache(maxSize, knownStates);
  }

  @Test
  public void put_exceedsMaxSize() {
    List<SignedBlockAndState> toAdd =
        chain.stream()
            .filter(b -> !knownStates.containsKey(b.getRoot()))
            .limit(maxSize + 1)
            .toList();
    for (int i = 0; i < toAdd.size(); i++) {
      SignedBlockAndState blockAndState = toAdd.get(i);
      cache.put(blockAndState.getRoot(), blockAndState.getState());
      assertThat(cache.countCachedStates()).isEqualTo(Math.min(i + 1, maxSize));
    }

    // Known states should still be available
    assertThat(cache.get(knownState1.getRoot())).contains(knownState1.getState());
    assertThat(cache.get(knownState2.getRoot())).contains(knownState2.getState());
  }

  @Test
  public void get_knownState() {
    assertThat(cache.get(knownState1.getRoot())).contains(knownState1.getState());
    assertThat(cache.get(knownState2.getRoot())).contains(knownState2.getState());

    cache.clear();

    assertThat(cache.get(knownState1.getRoot())).contains(knownState1.getState());
    assertThat(cache.get(knownState2.getRoot())).contains(knownState2.getState());
  }

  @Test
  public void put_knownState() {
    cache.put(knownState1.getRoot(), knownState1.getState());
    assertThat(cache.countCachedStates()).isEqualTo(0);
  }
}
