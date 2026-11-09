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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

@TestSpecContext(milestone = {SpecMilestone.FULU, SpecMilestone.GLOAS, SpecMilestone.HEZE})
class CombinedChainDataClientForkChoiceNodeTest {

  private Spec spec;
  private SpecContext specContext;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private RecentChainData recentChainData;
  private CombinedChainDataClient client;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    this.specContext = specContext;
    spec = specContext.getSpec();
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.PRUNE)
            .storeConfig(StoreConfig.builder().build())
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    client = storageSystem.combinedChainDataClient();
  }

  @TestTemplate
  void shouldReturnEmptyWhenThereIsNoChainHead() {
    assertThat(client.getForkChoiceNodeInEffectAtSlot(UInt64.ONE)).isEmpty();
  }

  @TestTemplate
  void shouldReturnBaseNodePreGloas() {
    specContext.assumeIsOneOf(SpecMilestone.FULU);
    initializeFromGenesis();
    final SignedBlockAndState block = importBlockAtSlot(1);
    final SignedBlockAndState head = importBlockAtSlot(3);
    recentChainData.updateHead(head.getRoot(), head.getSlot());

    assertNodeInEffectAtSlot(2, block, PAYLOAD_STATUS_PENDING);
    assertNodeInEffectAtSlot(3, head, PAYLOAD_STATUS_PENDING);
  }

  @TestTemplate
  void shouldReturnHeadWhenHeadIsAtSlot() {
    specContext.assumeIsNotOneOf(SpecMilestone.FULU);
    initializeFromGenesis();
    final SignedBlockAndState head = importBlockAndPayloadAtSlot(1);
    recentChainData.updateHead(ForkChoiceNode.createFull(head.getRoot()), head.getSlot());

    assertNodeInEffectAtSlot(1, head, PAYLOAD_STATUS_FULL);
  }

  @TestTemplate
  void shouldReturnHeadWhenHeadIsBeforeSlot() {
    specContext.assumeIsNotOneOf(SpecMilestone.FULU);
    initializeFromGenesis();
    final SignedBlockAndState head = importBlockAndPayloadAtSlot(1);
    recentChainData.updateHead(ForkChoiceNode.createEmpty(head.getRoot()), head.getSlot());

    // The payload has been imported, but fork choice selected the EMPTY variant
    assertNodeInEffectAtSlot(3, head, PAYLOAD_STATUS_EMPTY);
  }

  @TestTemplate
  void shouldReturnFullAncestorWhenHeadChainBuildsOnPayload() {
    specContext.assumeIsNotOneOf(SpecMilestone.FULU);
    initializeFromGenesis();
    final SignedBlockAndState ancestor = importBlockAndPayloadAtSlot(1);
    // Built on the FULL parent (the chain builder reveals the parent payload)
    final SignedBlockAndState head = importBlockAtSlot(3);
    recentChainData.updateHead(ForkChoiceNode.createEmpty(head.getRoot()), head.getSlot());

    assertNodeInEffectAtSlot(1, ancestor, PAYLOAD_STATUS_FULL);
    // Skipped slot resolves to the latest ancestor before it
    assertNodeInEffectAtSlot(2, ancestor, PAYLOAD_STATUS_FULL);
  }

  @TestTemplate
  void shouldReturnEmptyAncestorWhenHeadChainDoesNotBuildOnPayload() {
    specContext.assumeIsNotOneOf(SpecMilestone.FULU);
    initializeFromGenesis();
    final SignedBlockAndState ancestor = importBlockAndPayloadAtSlot(1);
    // Built on the EMPTY parent even though the parent payload has been imported
    final SignedBlockAndState head =
        importBlock(
            chainBuilder.generateBlockAtSlot(
                3, chainBuilder.createBlockOptionsBuildingOnEmptyParent(UInt64.valueOf(3))));
    recentChainData.updateHead(ForkChoiceNode.createEmpty(head.getRoot()), head.getSlot());

    assertNodeInEffectAtSlot(1, ancestor, PAYLOAD_STATUS_EMPTY);
    assertNodeInEffectAtSlot(2, ancestor, PAYLOAD_STATUS_EMPTY);
  }

  @TestTemplate
  void shouldReturnEmptyForSlotBeforeForkChoiceAnchor() {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(spec.slotsPerEpoch(UInt64.ZERO) * 2L);
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(2);
    final SignedBlockAndState anchor =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();
    recentChainData.initializeFromAnchorPoint(
        AnchorPoint.create(spec, anchorCheckpoint, anchor), UInt64.ZERO);

    assertThat(client.getForkChoiceNodeInEffectAtSlot(anchor.getSlot().minusMinZero(1))).isEmpty();
    assertThat(client.getForkChoiceNodeInEffectAtSlot(anchor.getSlot()).map(ProtoNodeData::getRoot))
        .contains(anchor.getRoot());
  }

  @TestTemplate
  void shouldReturnEmptyWhenHeadNodeWasInvalidated() {
    initializeFromGenesis();
    final SignedBlockAndState ancestor = importValidBlockAtSlot(1);
    final SignedBlockAndState head = importOptimisticBlockAtSlot(2);
    final ForkChoiceNode headNode = optimisticHeadNode(head);
    recentChainData.updateHead(headNode, head.getSlot());
    assertThat(client.getForkChoiceNodeInEffectAtSlot(head.getSlot())).isPresent();

    // The head is not re-selected, so it still points at the now untracked node
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .onForkChoiceUpdatedResult(
            new SlotAndForkChoiceNode(head.getSlot(), headNode),
            PayloadStatus.invalid(Optional.empty(), Optional.empty()),
            true);

    assertThat(recentChainData.getChainHead().map(ChainHead::getForkChoiceNode)).contains(headNode);
    assertThat(client.getForkChoiceNodeInEffectAtSlot(head.getSlot())).isEmpty();
    assertThat(client.getForkChoiceNodeInEffectAtSlot(ancestor.getSlot())).isEmpty();
  }

  @TestTemplate
  void shouldReportOptimisticStatusOfTheNodeAtSlot() {
    initializeFromGenesis();
    final SignedBlockAndState ancestor = importValidBlockAtSlot(1);
    final SignedBlockAndState head = importOptimisticBlockAtSlot(3);
    recentChainData.updateHead(optimisticHeadNode(head), head.getSlot());

    assertThat(client.getForkChoiceNodeInEffectAtSlot(UInt64.ONE))
        .hasValueSatisfying(
            node -> {
              assertThat(node.getRoot()).isEqualTo(ancestor.getRoot());
              assertThat(node.isOptimistic()).isFalse();
            });
    assertThat(client.getForkChoiceNodeInEffectAtSlot(UInt64.valueOf(3)))
        .hasValueSatisfying(
            node -> {
              assertThat(node.getRoot()).isEqualTo(head.getRoot());
              assertThat(node.isOptimistic()).isTrue();
            });
  }

  private void initializeFromGenesis() {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
  }

  private SignedBlockAndState importBlockAtSlot(final long slot) {
    return importBlock(chainBuilder.generateBlockAtSlot(slot));
  }

  private SignedBlockAndState importBlockAndPayloadAtSlot(final long slot) {
    final SignedBlockAndState block = importBlockAtSlot(slot);
    storageSystem
        .chainUpdater()
        .saveExecutionPayload(
            chainBuilder.getExecutionPayloadAtSlot(block.getSlot()).orElseThrow());
    return block;
  }

  private boolean isGloas() {
    return spec.atSlot(UInt64.ZERO).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
  }

  /** Pre-Gloas the block is validated; from Gloas its payload is imported and validated. */
  private SignedBlockAndState importValidBlockAtSlot(final long slot) {
    return isGloas() ? importBlockAndPayloadAtSlot(slot) : importBlockAtSlot(slot);
  }

  /**
   * Imports the block (and from Gloas its payload) without an execution result, leaving the node
   * returned by {@link #optimisticHeadNode} optimistic.
   */
  private SignedBlockAndState importOptimisticBlockAtSlot(final long slot) {
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(slot);
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(block, spec.calculateBlockCheckpoints(block.getState()));
    if (isGloas()) {
      tx.putExecutionPayload(
          chainBuilder.getExecutionPayloadAtSlot(block.getSlot()).orElseThrow(), true);
    }
    assertThat(tx.commit()).isCompleted();
    return block;
  }

  /**
   * From Gloas the EMPTY node inherits the validity of the parent payload, so only the FULL node of
   * a block whose payload has no execution result yet is optimistic.
   */
  private ForkChoiceNode optimisticHeadNode(final SignedBlockAndState block) {
    return isGloas()
        ? ForkChoiceNode.createFull(block.getRoot())
        : ForkChoiceNode.createBase(block.getRoot());
  }

  private SignedBlockAndState importBlock(final SignedBlockAndState block) {
    storageSystem.chainUpdater().saveBlock(block);
    return block;
  }

  private void assertNodeInEffectAtSlot(
      final long slot,
      final SignedBlockAndState expectedBlock,
      final ForkChoicePayloadStatus expectedPayloadStatus) {
    final ForkChoiceNode expectedNode =
        new ForkChoiceNode(expectedBlock.getRoot(), expectedPayloadStatus);
    final ProtoNodeData nodeData =
        client.getForkChoiceNodeInEffectAtSlot(UInt64.valueOf(slot)).orElseThrow();
    assertThat(nodeData.getForkChoiceNode()).isEqualTo(expectedNode);
    assertThat(nodeData.getSlot()).isEqualTo(expectedBlock.getSlot());
    // Node data, including optimistic status, is the fork choice snapshot of that node
    assertThat(nodeData)
        .isEqualTo(
            recentChainData
                .getForkChoiceStrategy()
                .orElseThrow()
                .getNodeData(expectedNode)
                .orElseThrow());
  }
}
