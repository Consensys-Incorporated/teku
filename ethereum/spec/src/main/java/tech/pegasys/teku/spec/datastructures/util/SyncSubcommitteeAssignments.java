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

package tech.pegasys.teku.spec.datastructures.util;

import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;

/**
 * Holds information about a validators assignment to a sync committee in a more efficiently queried
 * form.
 */
public class SyncSubcommitteeAssignments {

  public static final SyncSubcommitteeAssignments NONE =
      new SyncSubcommitteeAssignments(Int2ObjectMaps.emptyMap(), IntSets.emptySet());

  private final Int2ObjectMap<IntSet> subcommitteeToParticipationIndices;
  private final IntSet committeeIndices;

  private SyncSubcommitteeAssignments(
      final Int2ObjectMap<IntSet> subcommitteeToParticipationIndices,
      final IntSet committeeIndices) {
    this.subcommitteeToParticipationIndices = subcommitteeToParticipationIndices;
    this.committeeIndices = committeeIndices;
  }

  public static SyncSubcommitteeAssignments.Builder builder() {
    return new SyncSubcommitteeAssignments.Builder();
  }

  public IntSet getAssignedSubcommittees() {
    return IntSets.unmodifiable(subcommitteeToParticipationIndices.keySet());
  }

  public IntSet getCommitteeIndices() {
    return committeeIndices;
  }

  public IntSet getParticipationBitIndices(final int subcommitteeIndex) {
    return IntSets.unmodifiable(
        subcommitteeToParticipationIndices.getOrDefault(subcommitteeIndex, IntSets.emptySet()));
  }

  public boolean isEmpty() {
    return subcommitteeToParticipationIndices.isEmpty();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subcommitteeToParticipationIndices", subcommitteeToParticipationIndices)
        .toString();
  }

  public static class Builder {
    private final Int2ObjectMap<IntSet> subcommitteeToParticipationIndices =
        new Int2ObjectOpenHashMap<>();
    private final IntSet committeeIndices = new IntOpenHashSet();

    public Builder addAssignment(
        final int subcommitteeIndex, final int subcommitteeParticipationIndex) {
      subcommitteeToParticipationIndices
          .computeIfAbsent(subcommitteeIndex, __ -> new IntOpenHashSet())
          .add(subcommitteeParticipationIndex);
      return this;
    }

    public Builder addCommitteeIndex(final int index) {
      committeeIndices.add(index);
      return this;
    }

    public SyncSubcommitteeAssignments build() {
      return new SyncSubcommitteeAssignments(subcommitteeToParticipationIndices, committeeIndices);
    }
  }
}
