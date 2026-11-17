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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
class BeaconStateCacheRetentionTest {

  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  void shouldNotRetainAncestorStatesThroughViewCache() throws InterruptedException {
    final StateLineage lineage = createStateLineage();
    try {
      for (int attempt = 0;
          attempt < 20 && (!lineage.root().refersTo(null) || !lineage.middle().refersTo(null));
          attempt++) {
        System.gc();
        Thread.sleep(50);
      }

      assertThat(lineage.middle().refersTo(null))
          .as("intermediate state should be collected")
          .isTrue();
      assertThat(lineage.root().refersTo(null)).as("lineage root should be collected").isTrue();
    } finally {
      Reference.reachabilityFence(lineage.last());
    }
  }

  private StateLineage createStateLineage() {
    final BeaconState randomState = dataStructureUtil.randomBeaconState(64);
    final BeaconState root =
        randomState.getBeaconStateSchema().createFromBackingNode(randomState.getBackingNode());
    final BeaconState middle = root.updated(state -> state.setSlot(state.getSlot().plus(1)));
    final BeaconState last = middle.updated(state -> state.setSlot(state.getSlot().plus(1)));
    // Keep ancestor locals outside the GC loop's stack frame.
    return new StateLineage(new WeakReference<>(root), new WeakReference<>(middle), last);
  }

  private record StateLineage(
      WeakReference<BeaconState> root, WeakReference<BeaconState> middle, BeaconState last) {}
}
