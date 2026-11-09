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

package tech.pegasys.teku.storage.protoarray;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ProtoArrayIndicesTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkChoiceNode root1 = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
  private final ForkChoiceNode root2 = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
  private final ForkChoiceNode root3 = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
  private final ProtoArrayIndices indices = new ProtoArrayIndices();

  @Test
  void shouldAddNodesToMap() {
    indices.add(root1, 1);
    indices.add(root2, 2);
    assertThat(indices.getNodeIndices().keySet()).containsExactlyInAnyOrder(root1, root2);
    assertThat(indices.get(root1)).contains(1);
    assertThat(indices.get(root2)).contains(2);
  }

  @Test
  void shouldPruneNodesFromList() {
    indices.add(root1, 1);
    indices.add(root2, 2);
    indices.add(root3, 3);
    indices.remove(root1);
    assertThat(indices.getNodeIndices().keySet()).containsExactlyInAnyOrder(root2, root3);
    assertThat(indices.contains(root1)).isFalse();
  }

  @Test
  void shouldGetFirstItemSuccessfully() {
    indices.add(root1, 1);
    assertThat(indices.get(root1)).contains(1);
  }

  @Test
  void shouldGetEmptyIfHashMissing() {
    assertThat(indices.get(root1)).isEmpty();
  }

  @Test
  void shouldCallContains() {
    assertThat(indices.contains(root1)).isFalse();
    indices.add(root1, 1);
    assertThat(indices.contains(root1)).isTrue();
  }
}
