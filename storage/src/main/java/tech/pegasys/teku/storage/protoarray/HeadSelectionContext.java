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

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record HeadSelectionContext(
    Function<UInt64, ForkChoiceModel> modelSelector,
    BlockNodeVariantsIndex blockNodeIndex,
    UInt64 currentSlot,
    Optional<Bytes32> proposerBoostRoot) {

  public HeadSelectionContext(
      final ForkChoiceModel model,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    this(__ -> model, blockNodeIndex, currentSlot, proposerBoostRoot);
  }

  public HeadSelectionContext(
      final ForkChoiceModelFactory modelFactory,
      final BlockNodeVariantsIndex blockNodeIndex,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    this(modelFactory::forSlot, blockNodeIndex, currentSlot, proposerBoostRoot);
  }

  public ForkChoiceModel modelForSlot(final UInt64 slot) {
    return modelSelector.apply(slot);
  }

  public int compareViableChildren(
      final ProtoNode candidateChild,
      final ProtoNode currentBestChild,
      final ProtoNode parent,
      final ProtoArray protoArray) {
    final int modelComparison =
        modelForSlot(parent.getBlockSlot())
            .compareViableChildren(
                candidateChild,
                currentBestChild,
                parent,
                protoArray,
                blockNodeIndex,
                currentSlot,
                proposerBoostRoot);
    if (modelComparison != 0) {
      return modelComparison;
    }
    if (candidateChild.getWeight().equals(currentBestChild.getWeight())) {
      // Spec tie-breaker: when viable children have equal weight, prefer the higher root.
      return candidateChild
          .getBlockRoot()
          .toHexString()
          .compareTo(currentBestChild.getBlockRoot().toHexString());
    }
    // Choose the winner by weight.
    return candidateChild.getWeight().compareTo(currentBestChild.getWeight());
  }

  public ProtoNode resolveBestDescendant(final ProtoNode candidate, final ProtoArray protoArray) {
    return modelForSlot(candidate.getBlockSlot())
        .resolveBestDescendant(
            candidate, protoArray, blockNodeIndex, currentSlot, proposerBoostRoot);
  }
}
