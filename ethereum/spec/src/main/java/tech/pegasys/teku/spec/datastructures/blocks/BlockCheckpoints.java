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

package tech.pegasys.teku.spec.datastructures.blocks;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class BlockCheckpoints {
  private final Checkpoint justifiedCheckpoint;
  private final Checkpoint finalizedCheckpoint;
  private final Checkpoint unrealizedJustifiedCheckpoint;
  private final Checkpoint unrealizedFinalizedCheckpoint;

  public BlockCheckpoints(
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint unrealizedJustifiedCheckpoint,
      final Checkpoint unrealizedFinalizedCheckpoint) {
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.unrealizedJustifiedCheckpoint = unrealizedJustifiedCheckpoint;
    this.unrealizedFinalizedCheckpoint = unrealizedFinalizedCheckpoint;
  }

  public BlockCheckpoints realizeNextEpoch() {
    return new BlockCheckpoints(
        unrealizedJustifiedCheckpoint,
        unrealizedFinalizedCheckpoint,
        unrealizedJustifiedCheckpoint,
        unrealizedFinalizedCheckpoint);
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public Checkpoint getUnrealizedJustifiedCheckpoint() {
    return unrealizedJustifiedCheckpoint;
  }

  public Checkpoint getUnrealizedFinalizedCheckpoint() {
    return unrealizedFinalizedCheckpoint;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlockCheckpoints that = (BlockCheckpoints) o;
    return Objects.equals(justifiedCheckpoint, that.justifiedCheckpoint)
        && Objects.equals(finalizedCheckpoint, that.finalizedCheckpoint)
        && Objects.equals(unrealizedJustifiedCheckpoint, that.unrealizedJustifiedCheckpoint)
        && Objects.equals(unrealizedFinalizedCheckpoint, that.unrealizedFinalizedCheckpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        justifiedCheckpoint,
        finalizedCheckpoint,
        unrealizedJustifiedCheckpoint,
        unrealizedFinalizedCheckpoint);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("justifiedCheckpoint", justifiedCheckpoint)
        .add("finalizedCheckpoint", finalizedCheckpoint)
        .add("unrealizedJustifiedCheckpoint", unrealizedJustifiedCheckpoint)
        .add("unrealizedFinalizedCheckpoint", unrealizedFinalizedCheckpoint)
        .toString();
  }
}
