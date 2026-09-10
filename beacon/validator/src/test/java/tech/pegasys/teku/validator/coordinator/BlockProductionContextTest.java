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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;

@TestSpecContext(milestone = {SpecMilestone.FULU, SpecMilestone.GLOAS})
class BlockProductionContextTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ChainBuilder chainBuilder;

  @BeforeEach
  void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    chainBuilder = ChainBuilder.create(spec);
  }

  @TestTemplate
  void create_shouldDeriveParentRootFromStateAndPreservePayloadStatus() throws Exception {
    final SignedBlockAndState parentBlock = generateParentBlock();
    final UInt64 proposalSlot = parentBlock.getSlot().plus(1);
    final BeaconState blockSlotState = spec.processSlots(parentBlock.getState(), proposalSlot);
    final ChainHead parentChainHead = chainHead(parentBlock, PAYLOAD_STATUS_FULL);
    final Optional<BuilderConfig> builderConfig =
        Optional.of(BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(42)));

    final BlockProductionContext context =
        createBlockProductionContext(proposalSlot, blockSlotState, parentChainHead, builderConfig);

    assertThat(context.proposalSlot()).isEqualTo(proposalSlot);
    assertThat(context.blockSlotState()).isSameAs(blockSlotState);
    final Bytes32 derivedParentRoot =
        spec.getBlockRootAtSlot(blockSlotState, proposalSlot.minus(1));
    assertThat(context.parentRoot()).isEqualTo(derivedParentRoot);
    assertThat(context.parentForkChoiceNode())
        .isEqualTo(ForkChoiceNode.createFull(parentBlock.getRoot()));
    assertThat(context.parentPayloadStatus()).isEqualTo(PAYLOAD_STATUS_FULL);
    assertThat(context.parentExecutionBlockHash())
        .isEqualTo(parentChainHead.getExecutionBlockHash());
    assertThat(context.builderConfig()).isEqualTo(builderConfig);
    assertThat(context.blockProductionPerformance()).isSameAs(BlockProductionPerformance.NOOP);
  }

  @TestTemplate
  void create_shouldRejectStateAtDifferentSlot() {
    final SignedBlockAndState parentBlock = generateParentBlock();
    final UInt64 proposalSlot = parentBlock.getSlot().plus(1);

    assertThatThrownBy(
            () ->
                createBlockProductionContext(
                    proposalSlot,
                    parentBlock.getState(),
                    chainHead(parentBlock, PAYLOAD_STATUS_FULL),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Block slot state for slot")
        .hasMessageContaining("but should be for slot");
  }

  @TestTemplate
  void create_shouldRejectParentRootNotDerivedFromState() throws Exception {
    final SignedBlockAndState parentBlock = generateParentBlock();
    final UInt64 proposalSlot = parentBlock.getSlot().plus(1);
    final BeaconState blockSlotState = spec.processSlots(parentBlock.getState(), proposalSlot);
    final Bytes32 mismatchedParentRoot = dataStructureUtil.randomBytes32();

    assertThatThrownBy(
            () ->
                createBlockProductionContext(
                    proposalSlot,
                    blockSlotState,
                    chainHead(parentBlock, mismatchedParentRoot, PAYLOAD_STATUS_FULL),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Block slot state parent root")
        .hasMessageContaining("does not match selected production parent root");
  }

  private ChainHead chainHead(
      final SignedBlockAndState blockAndState, final ForkChoicePayloadStatus payloadStatus) {
    return chainHead(blockAndState, blockAndState.getRoot(), payloadStatus);
  }

  private SignedBlockAndState generateParentBlock() {
    chainBuilder.generateGenesis();
    return chainBuilder.generateBlockAtSlot(UInt64.ONE);
  }

  private BlockProductionContext createBlockProductionContext(
      final UInt64 proposalSlot,
      final BeaconState blockSlotState,
      final ChainHead parentChainHead,
      final Optional<BuilderConfig> builderConfig) {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(dataStructureUtil.randomBytes32());
    return BlockProductionContext.create(
        spec,
        proposalSlot,
        blockSlotState,
        parentChainHead,
        randaoReveal,
        graffiti,
        false,
        builderConfig,
        BlockProductionPerformance.NOOP);
  }

  private ChainHead chainHead(
      final SignedBlockAndState blockAndState,
      final Bytes32 root,
      final ForkChoicePayloadStatus payloadStatus) {
    final ProtoNodeData protoNodeData =
        new ProtoNodeData(
            blockAndState.getSlot(),
            root,
            blockAndState.getParentRoot(),
            blockAndState.getStateRoot(),
            blockAndState.getExecutionBlockNumber().orElse(UInt64.ZERO),
            blockAndState.getExecutionBlockHash().orElse(Bytes32.ZERO),
            UInt64.ZERO,
            ProtoNodeValidationStatus.VALID,
            spec.calculateBlockCheckpoints(blockAndState.getState()),
            UInt64.ZERO,
            payloadStatus);
    return ChainHead.create(
        protoNodeData, completedFuture(StateAndBlockSummary.create(blockAndState)));
  }
}
