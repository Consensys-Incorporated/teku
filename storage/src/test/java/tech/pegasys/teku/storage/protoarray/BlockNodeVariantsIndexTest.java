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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

class BlockNodeVariantsIndexTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalPhase0();
  private static final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(ZERO, Bytes32.ZERO);
  private static final BlockCheckpoints GENESIS_BLOCK_CHECKPOINTS =
      new BlockCheckpoints(
          GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

  @Test
  void attachProjectedNodes_requiresBaseNodeToExistFirst() {
    final Bytes32 blockRoot = Bytes32.fromHexStringLenient("0x01");
    final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
    final ForkChoiceNode fullNode = ForkChoiceNode.createFull(blockRoot);

    assertThatThrownBy(() -> new BlockNodeVariantsIndex().attachEmptyNode(blockRoot, emptyNode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot attach EMPTY node");
    assertThatThrownBy(() -> new BlockNodeVariantsIndex().attachFullNode(blockRoot, fullNode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot attach FULL node");
  }

  @Test
  void fromProtoArray_rebuildsVariantsWhenBaseNodePrecedesDerivedNodes() {
    final UInt64 slot = UInt64.ONE;
    final Bytes32 blockRoot = Bytes32.fromHexStringLenient("0x02");
    final Bytes32 parentRoot = Bytes32.ZERO;
    final ForkChoiceNode baseNode = ForkChoiceNode.createBase(blockRoot);
    final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
    final ForkChoiceNode fullNode = ForkChoiceNode.createFull(blockRoot);
    final ProtoArray protoArray =
        ProtoArray.builder()
            .spec(SPEC)
            .currentEpoch(ZERO)
            .justifiedCheckpoint(GENESIS_CHECKPOINT)
            .finalizedCheckpoint(GENESIS_CHECKPOINT)
            .build();

    protoArray.addNode(
        baseNode,
        slot,
        parentRoot,
        Optional.empty(),
        Bytes32.ZERO,
        GENESIS_BLOCK_CHECKPOINTS,
        ZERO,
        Bytes32.ZERO,
        false);
    protoArray.addNode(
        emptyNode,
        slot,
        parentRoot,
        Optional.of(baseNode),
        Bytes32.ZERO,
        GENESIS_BLOCK_CHECKPOINTS,
        ZERO,
        Bytes32.ZERO,
        false);
    protoArray.addNode(
        fullNode,
        slot,
        parentRoot,
        Optional.of(baseNode),
        Bytes32.ZERO,
        GENESIS_BLOCK_CHECKPOINTS,
        ZERO,
        Bytes32.ZERO,
        false);

    final BlockNodeVariantsIndex rebuiltIndex = BlockNodeVariantsIndex.fromProtoArray(protoArray);

    assertThat(rebuiltIndex.getSlot(blockRoot)).contains(slot);
    assertThat(rebuiltIndex.getBaseNode(blockRoot)).contains(baseNode);
    assertThat(rebuiltIndex.getEmptyNode(blockRoot)).contains(emptyNode);
    assertThat(rebuiltIndex.getFullNode(blockRoot)).contains(fullNode);
  }

  @Test
  void indexesBaseBlockRootsBySlotWithoutDuplicatingPayloadVariants() {
    final UInt64 slot = UInt64.ONE;
    final Bytes32 firstRoot = Bytes32.fromHexStringLenient("0x03");
    final Bytes32 secondRoot = Bytes32.fromHexStringLenient("0x04");
    final BlockNodeVariantsIndex index = new BlockNodeVariantsIndex();

    index.putBaseNode(firstRoot, slot, ForkChoiceNode.createBase(firstRoot));
    index.attachEmptyNode(firstRoot, ForkChoiceNode.createEmpty(firstRoot));
    index.attachFullNode(firstRoot, ForkChoiceNode.createFull(firstRoot));
    index.putBaseNode(secondRoot, slot, ForkChoiceNode.createBase(secondRoot));

    assertThat(index.getBlockRootsAtSlot(slot)).containsExactly(firstRoot, secondRoot);

    index.remove(firstRoot);
    assertThat(index.getBlockRootsAtSlot(slot)).containsExactly(secondRoot);

    index.removeIf(root -> root.equals(secondRoot));
    assertThat(index.getBlockRootsAtSlot(slot)).isEmpty();
  }
}
