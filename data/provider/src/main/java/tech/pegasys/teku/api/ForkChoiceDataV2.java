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

package tech.pegasys.teku.api;

import java.util.List;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class ForkChoiceDataV2 {
  private final Checkpoint justifiedCheckpoint;
  private final Checkpoint finalizedCheckpoint;
  private final List<ForkChoiceNodeDataV2> nodes;

  public ForkChoiceDataV2(
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final List<ForkChoiceNodeDataV2> nodes) {
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.nodes = nodes;
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public List<ForkChoiceNodeDataV2> getNodes() {
    return nodes;
  }
}
