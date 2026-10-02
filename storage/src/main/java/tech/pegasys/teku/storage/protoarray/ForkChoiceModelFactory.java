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

package tech.pegasys.teku.storage.protoarray;

import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;

/** Centralizes the storage-layer milestone split for forkchoice models. */
public class ForkChoiceModelFactory {

  private final ForkChoiceModel phase0Model = ForkChoiceModelPhase0.INSTANCE;
  private final ForkChoiceModel gloasModel;
  private final UInt64 firstGloasSlot;

  public ForkChoiceModelFactory(final Spec spec) {
    this(spec, __ -> true);
  }

  public ForkChoiceModelFactory(final Spec spec, final Predicate<Bytes32> satisfiesInclusionList) {
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      gloasModel =
          new ForkChoiceModelGloas(
              SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig()),
              satisfiesInclusionList);
      firstGloasSlot =
          spec.computeStartSlotAtEpoch(
              spec.getForkSchedule().getFork(SpecMilestone.GLOAS).getEpoch());
    } else {
      gloasModel = phase0Model;
      firstGloasSlot = UInt64.MAX_VALUE;
    }
  }

  ForkChoiceModel forSlot(final UInt64 slot) {
    return slot.isGreaterThanOrEqualTo(firstGloasSlot) ? gloasModel : phase0Model;
  }

  public HeadSelectionContext createHeadSelectionContext(
      final UInt64 currentSlot,
      final BlockNodeVariantsIndex blockNodeIndex,
      final Optional<Bytes32> proposerBoostRoot) {
    return new HeadSelectionContext(this, blockNodeIndex, currentSlot, proposerBoostRoot);
  }

  public void rebuildBlockNodesFromMetadata(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final StoredBlockMetadata block,
      final boolean optimisticallyProcessed) {
    forSlot(block.getBlockSlot())
        .rebuildBlockNodesFromMetadata(protoArray, blockNodeIndex, block, optimisticallyProcessed);
  }

  public void rebuildAnchorBlockNodesFromMetadata(
      final ProtoArray protoArray,
      final BlockNodeVariantsIndex blockNodeIndex,
      final StoredBlockMetadata block,
      final boolean optimisticallyProcessed) {
    forSlot(block.getBlockSlot())
        .rebuildAnchorBlockNodesFromMetadata(
            protoArray, blockNodeIndex, block, optimisticallyProcessed);
  }

  void onPrunedBlocks(final BlockNodeVariantsIndex blockNodeIndex) {
    phase0Model.onPrunedBlocks(blockNodeIndex);
    gloasModel.onPrunedBlocks(blockNodeIndex);
  }
}
