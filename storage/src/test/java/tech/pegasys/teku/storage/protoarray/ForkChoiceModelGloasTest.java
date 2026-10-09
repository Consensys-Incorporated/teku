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
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceModelGloasTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Checkpoint checkpoint = new Checkpoint(ZERO, Bytes32.ZERO);
  private final BlockCheckpoints checkpoints =
      new BlockCheckpoints(checkpoint, checkpoint, checkpoint, checkpoint);
  private final ProtoArray protoArray =
      ProtoArray.builder()
          .spec(spec)
          .currentEpoch(ZERO)
          .justifiedCheckpoint(checkpoint)
          .finalizedCheckpoint(checkpoint)
          .build();
  private final BlockNodeVariantsIndex variants = new BlockNodeVariantsIndex();
  private final ForkChoiceModelGloas model =
      new ForkChoiceModelGloas(SpecConfigGloas.required(spec.getGenesisSpecConfig()));

  @Test
  void processBlock_shouldPreserveFullVariantAndChildAncestryOnDuplicateImport() {
    final Bytes32 genesisRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 childRoot = dataStructureUtil.randomBytes32();
    final Bytes32 payloadHash = dataStructureUtil.randomBytes32();
    final UInt64 gasLimit = UInt64.valueOf(30_000_000);
    processBlock(ZERO, genesisRoot, Bytes32.ZERO, Bytes32.ZERO);
    processBlock(ONE, parentRoot, genesisRoot, Bytes32.ZERO);
    model.onExecutionPayload(protoArray, variants, parentRoot, ONE, payloadHash, gasLimit, false);
    final int nodeCount = protoArray.getTotalTrackedNodeCount();

    processBlock(ONE, parentRoot, genesisRoot, Bytes32.ZERO);

    assertThat(protoArray.getTotalTrackedNodeCount()).isEqualTo(nodeCount);
    assertThat(variants.getFullNode(parentRoot)).contains(ForkChoiceNode.createFull(parentRoot));
    processBlock(UInt64.valueOf(2), childRoot, parentRoot, payloadHash);
    final ProtoNode child = protoArray.getNode(ForkChoiceNode.createBase(childRoot)).orElseThrow();
    assertThat(child.getParentIndex())
        .isEqualTo(protoArray.getNodeIndex(ForkChoiceNode.createFull(parentRoot)));
    assertThat(child.getExecutionBlockHash()).isEqualTo(payloadHash);
    assertThat(child.getExecutionBlockNumber()).isEqualTo(ONE);
    assertThat(child.getExecutionGasLimit()).isEqualTo(gasLimit);
  }

  private void processBlock(
      final UInt64 slot,
      final Bytes32 root,
      final Bytes32 parentRoot,
      final Bytes32 parentPayloadHash) {
    model.processBlock(
        protoArray,
        variants,
        slot,
        root,
        parentRoot,
        Bytes32.ZERO,
        checkpoints,
        Optional.empty(),
        Optional.of(parentPayloadHash),
        Optional.empty(),
        false);
  }
}
