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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.StubVoteUpdater;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ProtoArrayTest {
  private static final Checkpoint GENESIS_CHECKPOINT = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalPhase0());
  private final VoteUpdater voteUpdater = new StubVoteUpdater();
  private final StatusLogger statusLog = mock(StatusLogger.class);
  private final SpecConfigGloas gloasSpecConfig =
      SpecConfigGloas.required(
          TestSpecFactory.createMinimalGloas().forMilestone(SpecMilestone.GLOAS).getConfig());
  private final ForkChoiceModelGloas gloasModel = new ForkChoiceModelGloas(gloasSpecConfig);

  private final Bytes32 block1a = dataStructureUtil.randomBytes32();
  private final Bytes32 block1b = dataStructureUtil.randomBytes32();
  private final Bytes32 block2a = dataStructureUtil.randomBytes32();
  private final Bytes32 block2b = dataStructureUtil.randomBytes32();
  private final Bytes32 block3a = dataStructureUtil.randomBytes32();
  private final Bytes32 block4a = dataStructureUtil.randomBytes32();

  private TestProtoArrayFacade protoArray =
      new TestProtoArrayFacade(
          new ProtoArrayBuilder()
              .spec(dataStructureUtil.getSpec())
              .statusLog(statusLog)
              .currentEpoch(ZERO)
              .justifiedCheckpoint(GENESIS_CHECKPOINT)
              .finalizedCheckpoint(GENESIS_CHECKPOINT)
              .build());

  @BeforeEach
  void setUp() {
    addOptimisticBlock(0, Bytes32.ZERO, Bytes32.ZERO);
  }

  @Test
  void findHead_shouldAlwaysConsiderJustifiedNodeAsViableHead() {
    // The justified checkpoint is the default chain head to use when there are no other blocks
    // considered viable, so ensure we always accept it as head
    final Bytes32 justifiedRoot = dataStructureUtil.randomBytes32();
    final Checkpoint justifiedCheckpoint = new Checkpoint(UInt64.ONE, justifiedRoot);
    final Checkpoint finalizedCheckpoint = new Checkpoint(UInt64.ONE, justifiedRoot);
    protoArray =
        new TestProtoArrayFacade(
            new ProtoArrayBuilder()
                .spec(dataStructureUtil.getSpec())
                .currentEpoch(ZERO)
                .justifiedCheckpoint(justifiedCheckpoint)
                .finalizedCheckpoint(finalizedCheckpoint)
                .initialEpoch(UInt64.ZERO)
                .build());
    // Justified block will have justified and finalized epoch of 0 which doesn't match the current
    // so would normally be not viable, but we should allow it anyway.
    addValidBlock(12, justifiedRoot, dataStructureUtil.randomBytes32());
    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), justifiedCheckpoint, finalizedCheckpoint);
    assertThat(head).isEqualTo(protoArray.getProtoNode(justifiedRoot).orElseThrow());
  }

  @Test
  void findOptimisticHead_shouldIncludeOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldIncludeValidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldFollowBestDescendantChainWithoutScoreRecalculation() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);
    addValidBlock(3, block3a, block2a);

    assertThat(
            protoArray
                .getProtoNode(GENESIS_CHECKPOINT.getRoot())
                .orElseThrow()
                .getBestDescendantIndex())
        .contains(protoArray.getIndexByRoot(block1a).orElseThrow());
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .contains(protoArray.getIndexByRoot(block2a).orElseThrow());
    assertThat(protoArray.getProtoNode(block2a).orElseThrow().getBestDescendantIndex())
        .contains(protoArray.getIndexByRoot(block3a).orElseThrow());

    assertHead(block3a);
  }

  @Test
  void findOptimisticHead_shouldExcludeInvalidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);

    assertHead(block2a);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    assertHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldExcludeBlocksDescendedFromAnInvalidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    applyScoreChanges();

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    assertHead(block1a);
  }

  @Test
  void findOptimisticHead_shouldAcceptOptimisticBlocksWithInvalidAncestors() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    // Apply score changes to ensure that the best descendant index is updated
    applyScoreChanges();

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.empty());

    assertHead(block2a);
  }

  @Test
  void findOptimisticHead_shouldThrowExceptionWhenAllBlocksAreInvalid() {
    // ProtoArray always contains the finalized block, if it's invalid there's a big problem.
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(GENESIS_CHECKPOINT.getRoot(), Optional.empty());

    assertThatThrownBy(
            () ->
                protoArray.findOptimisticHead(
                    UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT))
        .isInstanceOf(FatalServiceFailureException.class);
  }

  @Test
  void shouldConsiderWeightFromVotesForValidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);
    addValidBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b));
    applyScoreChanges();

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a));
    applyScoreChanges();

    // And our head should switch
    assertHead(block2a);
  }

  @Test
  void shouldConsiderWeightFromVotesForOptimisticBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b));
    applyScoreChanges();

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a));
    applyScoreChanges();

    // And our head should switch
    assertHead(block2a);
  }

  @Test
  void shouldNotConsiderWeightFromVotesForInvalidBlocks() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b));
    applyScoreChanges();

    assertHead(block2b);

    protoArray.markNodeInvalid(block2a, Optional.empty());

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a));
    applyScoreChanges();

    // Votes for 2a don't count because it's invalid so we stick with chain b.
    assertHead(block2b);
  }

  @Test
  void markNodeInvalid_shouldRemoveWeightWhenBlocksMarkedAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(2, block2b, block1b);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block1b));
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block1b));
    applyScoreChanges();

    assertHead(block2b);

    // Validators 0 and 1 switch forks to chain a
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(block1b, block2a));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(block1b, block2a));
    applyScoreChanges();

    // We switch to chain a because it has the greater weight now
    assertHead(block2a);

    // But oh no! It turns out to be invalid.
    protoArray.markNodeInvalid(block2a, Optional.empty());

    // So we switch back to chain b (notably without having to applyScoreChanges)
    assertHead(block2b);
  }

  @Test
  void markNodeInvalid_shouldMarkAllNodesAfterLastValidHashAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.of(getExecutionBlockHash(block1a)));

    assertHead(block1a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  @Test
  void markNodeInvalid_shouldLeaveLastValidBlockAndAncestorsAsOptimistic() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    addOptimisticBlock(4, block4a, block3a);

    assertHead(block4a);

    protoArray.markNodeInvalid(block4a, Optional.of(getExecutionBlockHash(block2a)));

    assertHead(block2a);
    assertThat(protoArray.contains(block4a)).isFalse();
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeInvalid_shouldNotMarkAncestorsValidWhenLastValidBlockNotProvided() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.empty());

    assertHead(block2a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeInvalid_shouldOnlyMarkSpecifiedNodeAsInvalidWhenLastValidHashUnknown() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markNodeInvalid(block3a, Optional.of(dataStructureUtil.randomBytes32()));

    assertHead(block2a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isTrue();
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).map(ProtoNode::isOptimistic)).contains(true);
    assertThat(protoArray.getProtoNode(block2a).map(ProtoNode::isOptimistic)).contains(true);
  }

  @Test
  void markNodeValid_shouldConsiderAllAncestorsValidWhenMarkedAsValid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    applyScoreChanges();

    // Check the best descendant has been updated
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().getBestDescendantIndex())
        .isEqualTo(protoArray.getIndexByRoot(block3a));
    assertHead(block3a);

    protoArray.markNodeValid(block3a);

    assertHead(block3a);
  }

  @Test
  void findAndMarkInvalidChain_shouldNotDoAnythingWhenLatestValidHashFromHead() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(getExecutionBlockHash(block3a)));

    assertHead(block3a);
    assertThat(protoArray.contains(block3a)).isTrue();
    verifyNoInteractions(statusLog);
  }

  @Test
  void findAndMarkInvalidChain_shouldNotDoAnythingWhenLatestValidHashNodeNotFound() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(dataStructureUtil.randomBytes32()));

    assertHead(block3a);
    assertThat(protoArray.contains(block3a)).isTrue();
  }

  @Test
  void findAndMarkInvalidChain_shouldMarkAllNodesAfterLatestValidHashAsInvalid() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);

    assertHead(block3a);

    protoArray.markParentChainInvalid(block3a, Optional.of(getExecutionBlockHash(block1a)));

    assertHead(block1a);
    assertThat(protoArray.contains(block3a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  @Test
  void contains_shouldContainValidBlock() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    assertThat(protoArray.contains(block1a)).isTrue();
  }

  @Test
  void contains_shouldContainOptimisticBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    assertThat(protoArray.contains(block1a)).isTrue();
  }

  @Test
  void contains_shouldNotContainInvalidBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.markNodeInvalid(block1a, Optional.empty());
    assertThat(protoArray.contains(block1a)).isFalse();
  }

  @Test
  void contains_shouldNotContainDescendantsOfInvalidBlock() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(1, block2a, block1a);
    protoArray.markNodeInvalid(block1a, Optional.empty());
    assertThat(protoArray.contains(block1a)).isFalse();
    assertThat(protoArray.contains(block2a)).isFalse();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenHeadNotKnown() {
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block1a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenTransitionNotYetHappened() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block1a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenTransitionBeforeStartOfProtoArray() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_emptyWhenAfterSpecifiedHead() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a, Bytes32.ZERO);
    addOptimisticBlock(3, block3a, block2a, Bytes32.ZERO);
    addOptimisticBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a)).isEmpty();
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsHead() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a, Bytes32.ZERO);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a))
        .isEqualTo(protoArray.getProtoNode(block3a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsParent() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block3a))
        .isEqualTo(protoArray.getProtoNode(block2a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsDescendant() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(2, block2a, block1a);
    addOptimisticBlock(3, block3a, block2a);
    addOptimisticBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block4a))
        .isEqualTo(protoArray.getProtoNode(block2a));
  }

  @Test
  void findOptimisticallySyncedMergeTransitionBlock_transitionBlockIsFullyValidated() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addValidBlock(2, block2a, block1a);
    addValidBlock(3, block3a, block2a);
    addValidBlock(4, block4a, block3a);
    assertThat(protoArray.findOptimisticallySyncedMergeTransitionBlock(block4a)).isEmpty();
  }

  @Test
  void onBlock_shouldNotMarkDefaultPayloadAsOptimistic() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), Bytes32.ZERO);
    addOptimisticBlock(1, block1b, GENESIS_CHECKPOINT.getRoot(), dataStructureUtil.randomBytes32());

    assertThat(protoArray.getProtoNode(block1a).orElseThrow().isOptimistic()).isFalse();
    assertThat(protoArray.getProtoNode(block1b).orElseThrow().isOptimistic()).isTrue();
  }

  @Test
  void setInitialCanonicalBlockRoot_shouldEnsureCanonicalHeadIsSet() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);
    addValidBlock(2, block2b, block1b);

    // due to tie breaking block2b is the head
    assertHead(block2b);

    protoArray.setInitialCanonicalBlockRoot(block2a);

    // block2a is now the head due to weight
    assertHead(block2a);
  }

  @Test
  void setInitialCanonicalBlockRoot_shouldEnsureCanonicalHeadIsSetWhenBlockRootIsNotAChainTip() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1b, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(2, block2a, block1a);
    addValidBlock(2, block2b, block1b);

    // due to tie breaking block2b is the head
    assertHead(block2b);

    // setting chain a as the canonical chain via non-tip block
    protoArray.setInitialCanonicalBlockRoot(block1a);

    // block2a is now the head due to weight
    assertHead(block2a);
  }

  private static final UInt64 EXECUTION_BLOCK_NUMBER = UInt64.valueOf(42);
  private static final Bytes32 EXECUTION_BLOCK_HASH =
      Bytes32.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");

  // --- Three-state fork choice (Gloas FULL node) tests ---

  @Test
  void onExecutionPayload_shouldCreateFullNode() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());

    assertThat(protoArray.getFullNodeIndices().containsKey(block1a)).isFalse();
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    assertThat(protoArray.getFullNodeIndices().containsKey(block1a)).isTrue();
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    final ProtoNode fullNode = protoArray.getNodeByIndex(fullNodeIndex);
    assertThat(fullNode.getBlockRoot()).isEqualTo(block1a);
    assertThat(fullNode.getPayloadStatus()).isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
    assertThat(fullNode.getExecutionBlockNumber()).isEqualTo(EXECUTION_BLOCK_NUMBER);
    assertThat(fullNode.getExecutionBlockHash()).isEqualTo(EXECUTION_BLOCK_HASH);
    // FULL node parent should be the block node
    final int blockNodeIndex = protoArray.getIndexByRoot(block1a).orElseThrow();
    assertThat(fullNode.getParentIndex()).isEqualTo(Optional.of(blockNodeIndex));
  }

  @Test
  void onExecutionPayload_shouldBeIdempotent() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    final int totalBefore = protoArray.getTotalTrackedNodeCount();

    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    assertThat(protoArray.getTotalTrackedNodeCount()).isEqualTo(totalBefore);
  }

  @Test
  void onExecutionPayload_shouldDoNothingForUnknownRoot() {
    final int totalBefore = protoArray.getTotalTrackedNodeCount();
    protoArray.onExecutionPayload(
        dataStructureUtil.randomBytes32(), EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    assertThat(protoArray.getTotalTrackedNodeCount()).isEqualTo(totalBefore);
  }

  @Test
  void createEmptyNode_shouldCreateEmptyChildNode() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());

    assertThat(protoArray.getEmptyNodeIndices().containsKey(block1a)).isFalse();
    protoArray.createEmptyNode(block1a);

    assertThat(protoArray.getEmptyNodeIndices().containsKey(block1a)).isTrue();
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    final ProtoNode emptyNode = protoArray.getNodeByIndex(emptyNodeIndex);
    assertThat(emptyNode.getBlockRoot()).isEqualTo(block1a);
    assertThat(emptyNode.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
    // EMPTY node parent should be the block (PENDING) node
    final int blockNodeIndex = protoArray.getIndexByRoot(block1a).orElseThrow();
    assertThat(emptyNode.getParentIndex()).isEqualTo(Optional.of(blockNodeIndex));
  }

  @Test
  void createEmptyNode_shouldBeIdempotent() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    final int totalBefore = protoArray.getTotalTrackedNodeCount();

    protoArray.createEmptyNode(block1a);
    assertThat(protoArray.getTotalTrackedNodeCount()).isEqualTo(totalBefore);
  }

  @Test
  void resolveParentIndex_shouldResolveFull_whenBlockHashMatches() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    // When child's parent_block_hash matches FULL node's execution hash → FULL
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(protoArray.resolveParentIndex(block1a, EXECUTION_BLOCK_HASH))
        .isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void resolveParentIndex_shouldResolveEmpty_whenBlockHashDoesNotMatch() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    // When child's parent_block_hash does NOT match FULL node's execution hash → EMPTY
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    assertThat(protoArray.resolveParentIndex(block1a, Bytes32.ZERO))
        .isEqualTo(Optional.of(emptyNodeIndex));
  }

  @Test
  void resolveParentIndex_shouldFallbackToEmpty_whenNoFull() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);

    // No FULL exists, should resolve to EMPTY regardless of hash
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    assertThat(protoArray.resolveParentIndex(block1a, EXECUTION_BLOCK_HASH))
        .isEqualTo(Optional.of(emptyNodeIndex));
  }

  @Test
  void resolveParentIndex_shouldThrowWhenPostGloasParentHasNoVariants() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());

    assertThatThrownBy(() -> protoArray.resolveParentIndex(block1a, Bytes32.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing GLOAS parent variants");
  }

  @Test
  void resolveParentIndex_shouldFallbackToBaseForPreGloasParent() {
    final SpecConfigGloas delayedGloasSpecConfig =
        SpecConfigGloas.required(
            TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.ONE)
                .forMilestone(SpecMilestone.GLOAS)
                .getConfig());
    final ForkChoiceModelGloas delayedGloasModel = new ForkChoiceModelGloas(delayedGloasSpecConfig);
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());

    assertThat(
            delayedGloasModel
                .resolveParentNode(
                    protoArray.protoArray(), protoArray.blockNodeIndex(), block1a, Bytes32.ZERO)
                .map(ProtoNode::getForkChoiceNode))
        .contains(ForkChoiceNode.createBase(block1a));
  }

  @Test
  void threeStateTree_childAttachesToFullParent() {
    // Build tree: genesis -> block1a (with EMPTY and FULL children)
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    // block2a resolves parent via the Gloas model with matching hash → should attach to
    // FULL
    final Optional<Integer> resolvedParent =
        protoArray.resolveParentIndex(block1a, EXECUTION_BLOCK_HASH);
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(resolvedParent).isEqualTo(Optional.of(fullNodeIndex));

    // Add block2a with resolved parent
    addValidBlockWithParentIndex(2, block2a, block1a, resolvedParent);

    // block2a's parent should be the FULL node
    assertThat(protoArray.getProtoNode(block2a).orElseThrow().getParentIndex())
        .isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void threeStateTree_childAttachesToEmptyWhenNoFull() {
    // Build tree: genesis -> block1a (with EMPTY child only, no FULL yet)
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.markNodeValid(block1a);

    // block2a resolves parent via the Gloas model with non-matching hash → should attach to
    // EMPTY
    final Optional<Integer> resolvedParent = protoArray.resolveParentIndex(block1a, Bytes32.ZERO);
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    assertThat(resolvedParent).isEqualTo(Optional.of(emptyNodeIndex));

    // Add block2a with resolved parent
    addValidBlockWithParentIndex(2, block2a, block1a, resolvedParent);

    // block2a's parent should be the EMPTY node
    assertThat(protoArray.getProtoNode(block2a).orElseThrow().getParentIndex())
        .isEqualTo(Optional.of(emptyNodeIndex));
  }

  @Test
  void threeStateTree_fullPathChildAndEmptyPathChild_shouldCompeteByWeight() {
    // Build tree: genesis -> block1a (with EMPTY and FULL children)
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);

    // block2a attaches to FULL path
    addValidBlockWithParentIndex(2, block2a, block1a, Optional.of(fullNodeIndex));

    // block2b attaches to EMPTY path
    addValidBlockWithParentIndex(2, block2b, block1a, Optional.of(emptyNodeIndex));
    // Every Gloas block has an EMPTY variant; the head walk must end on a variant, never on BASE
    protoArray.createEmptyNode(block2a);
    protoArray.createEmptyNode(block2b);

    // Vote for block2a (FULL path child)
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block2a, false, false));

    // Apply deltas with Gloas tiebreaker — currentSlot far from block slot so tiebreaker
    // defaults to payload status ordering (FULL=2 > EMPTY=1)
    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    // block2a should be head (FULL path wins via tiebreaker)
    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(head.getBlockRoot()).isEqualTo(block2a);
  }

  @Test
  void markNodeValid_shouldAlsoMarkFullAndEmptyNodesValid() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isOptimistic()).isTrue();
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isOptimistic()).isTrue();

    protoArray.markNodeValid(block1a);

    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isFullyValidated()).isTrue();
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isFullyValidated()).isTrue();
  }

  @Test
  void maybePrune_shouldRemoveFullAndEmptyNodesFromIndices() {
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    addValidBlock(2, block2a, block1a);
    addValidBlock(3, block3a, block2a);

    // Finalize at block2a
    final Checkpoint finalized = new Checkpoint(UInt64.ONE, block2a);
    protoArray.applyScoreChanges(
        computeDeltas(),
        UInt64.valueOf(5),
        finalized,
        finalized,
        ForkChoiceModelPhase0.INSTANCE,
        UInt64.valueOf(5),
        Optional.empty());
    protoArray.setPruneThreshold(0);
    protoArray.maybePrune(block2a);

    // block1a's FULL and EMPTY nodes should have been removed from indices
    assertThat(protoArray.getFullNodeIndices().containsKey(block1a)).isFalse();
    assertThat(protoArray.getEmptyNodeIndices().containsKey(block1a)).isFalse();
    assertThat(protoArray.contains(block1a)).isFalse();
  }

  // --- Gloas head-selection policy tests ---

  @Test
  void tiebreaker_fullWinsOverEmpty_whenNotPreviousSlot() {
    // Block at slot 5, currentSlot = 100 → not previous slot → tiebreaker uses raw payload
    // status: FULL(2) > EMPTY(1)
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    // FULL should be bestChild of block1a (PENDING node)
    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void tiebreaker_fullWinsOverEmpty_whenShouldExtendPayload_noBoost() {
    // Block at slot 5, currentSlot = 6 → previous slot → should_extend_payload
    // No proposer boost → should_extend_payload returns true → FULL wins (score 2)
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    applyScoreChanges(gloasModel, UInt64.valueOf(6), Optional.empty());

    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void tiebreaker_fullWinsOverEmpty_whenBoostOnChildBuiltOnFull() {
    // Block at slot 5, currentSlot = 6 → previous slot → should_extend_payload evaluated.
    // Proposer boost on block2a (child of block1a). block2a was built on FULL(block1a)
    // (matching execution hash) → is_parent_node_full = true → should_extend = true → FULL wins.
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    // block2a builds on FULL(block1a) — execution hash matches parent's FULL
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    addValidBlockWithParentIndex(
        6, block2a, block1a, Optional.of(fullNodeIndex), EXECUTION_BLOCK_HASH);

    applyScoreChanges(gloasModel, UInt64.valueOf(6), Optional.of(block2a));

    // FULL wins: is_parent_node_full(block1a) = true → should_extend_payload = true
    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void tiebreaker_fullWinsOverEmpty_whenPayloadTimelyAndDataAvailable() {
    // Block at slot 5, currentSlot = 6, proposer boost on a child of block1a.
    // PTC payload and data-availability votes exceed threshold
    // → should_extend_payload = true → FULL keeps score 2.
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    addValidBlockWithParentIndex(6, block2a, block1a, Optional.of(emptyNodeIndex));

    // threshold = 5, ptcVoteCount = 6 → timely (6 > 5)
    applyScoreChanges(
        createGloasModel(5, 5, block1a, 6, 6), UInt64.valueOf(6), Optional.of(block2a));

    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void tiebreaker_emptyWinsOverFull_whenPayloadTimelyButDataUnavailable() {
    // Block at slot 5, currentSlot = 6, proposer boost on a child of block1a built on EMPTY.
    // PTC payload votes exceed threshold but data-availability votes do not
    // → should_extend_payload = false
    // → EMPTY wins (score 1) over FULL (score 0).
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    addValidBlockWithParentIndex(6, block2a, block1a, Optional.of(emptyNodeIndex));

    applyScoreChanges(
        createGloasModel(5, 5, block1a, 6, 0), UInt64.valueOf(6), Optional.of(block2a));

    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(emptyNodeIndex));
  }

  @Test
  void tiebreaker_fullWinsOverEmpty_whenPayloadNotTimely_butBoostOnChildWithFullParent() {
    // Block at slot 5, currentSlot = 6, proposer boost on block2a (child building on FULL).
    // PTC votes ≤ threshold → is_payload_timely = false.
    // But is_parent_node_full = true (block2a's execution hash matches parent's FULL hash)
    // → should_extend_payload = true → FULL keeps score 2.
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    addValidBlockWithParentIndex(
        6, block2a, block1a, Optional.of(fullNodeIndex), EXECUTION_BLOCK_HASH);

    // threshold = 5, ptcVoteCount = 3 → NOT timely (3 ≤ 5)
    applyScoreChanges(
        createGloasModel(5, 5, block1a, 3, 0), UInt64.valueOf(6), Optional.of(block2a));

    // FULL still wins because is_parent_node_full(block1a) = true
    final ProtoNode block1aNode = protoArray.getProtoNode(block1a).orElseThrow();
    assertThat(block1aNode.getBestChildIndex()).isEqualTo(Optional.of(fullNodeIndex));
  }

  @Test
  void emptyPathWinsOverFullPath_whenEmptyHasMoreWeight_notPreviousSlot() {
    // Block at slot 5, currentSlot = 100 → not previous slot → effectiveWeight = node.getWeight()
    // EMPTY path has more attestation weight than FULL path → EMPTY wins by weight,
    // overriding tiebreaker (which would favor FULL)
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);

    // block2a attaches to EMPTY path, block2b attaches to FULL path
    addValidBlockWithParentIndex(6, block2a, block1a, Optional.of(emptyNodeIndex));
    addValidBlockWithParentIndex(6, block2b, block1a, Optional.of(fullNodeIndex));
    protoArray.createEmptyNode(block2a);
    protoArray.createEmptyNode(block2b);

    // Vote for block2a (EMPTY path child) — gives EMPTY path more weight
    // Need two votes so the balance list covers validator 0
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block2a, false, false));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block2a, false, false));

    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    // EMPTY path wins by weight, even though tiebreaker would favor FULL
    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(head.getBlockRoot()).isEqualTo(block2a);
  }

  @Test
  void tiebreakerDecides_whenBothZeroWeight_atPreviousSlot() {
    // Block at slot 5, currentSlot = 6 → previous slot → effectiveWeight = 0 for both
    // EMPTY and FULL siblings. Both have descendant votes, but effective weight is 0 for both
    // at current_slot-1, so tiebreaker decides (FULL wins via should_extend_payload)
    addValidBlock(5, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(block1a);

    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);

    // block2a on EMPTY path, block2b on FULL path — both get votes
    addValidBlockWithParentIndex(6, block2a, block1a, Optional.of(emptyNodeIndex));
    addValidBlockWithParentIndex(6, block2b, block1a, Optional.of(fullNodeIndex));
    protoArray.createEmptyNode(block2a);
    protoArray.createEmptyNode(block2b);

    // Vote for EMPTY path child (more votes) — validators 0 and 1
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, block2a, false, false));
    voteUpdater.putVote(UInt64.ONE, new VoteTracker(Bytes32.ZERO, block2a, false, false));
    // Vote for FULL path child (less votes) — validator 2
    voteUpdater.putVote(UInt64.valueOf(2), new VoteTracker(Bytes32.ZERO, block2b, false, false));
    // Dummy vote to ensure balance list covers all validators above
    voteUpdater.putVote(UInt64.valueOf(3), new VoteTracker(Bytes32.ZERO, block2b, false, false));

    // currentSlot = 6 = block slot + 1 → effective weight is 0 for both EMPTY and FULL
    // No proposer boost → should_extend_payload = true → FULL wins tiebreaker
    applyScoreChanges(gloasModel, UInt64.valueOf(6), Optional.empty());

    // Even though EMPTY path has more votes, at previous slot both have effectiveWeight 0,
    // so tiebreaker decides → FULL wins
    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(head.getBlockRoot()).isEqualTo(block2b);
  }

  @Test
  void findOptimisticHead_shouldSelectFullNode_atGloasActivationSlot() {
    // Replicates a 9-node ProtoArray snapshot taken at the GLOAS activation boundary:
    //
    //   genesis (slot 0) ── set up by @BeforeEach (root = Bytes32.ZERO)
    //     ├── slotA (slot 3)
    //     │     └── slotC (slot 5)
    //     │           └── slotD (slot 6)
    //     │                 └── slotE (slot 7)
    //     │                       └── slot8 (slot 8) PENDING ── GLOAS activates here
    //     │                              ├── slot8 EMPTY
    //     │                              └── slot8 FULL
    //     └── slotB (slot 4) [unweighted sibling of slotA]
    //
    // The slot-8 block carries the GLOAS three-state structure (PENDING base + EMPTY/FULL
    // children sharing the same blockRoot). With currentSlot far past slot 8 and equal
    // EMPTY/FULL weights, the GLOAS tiebreaker should select FULL over EMPTY.
    final Bytes32 slotA = dataStructureUtil.randomBytes32();
    final Bytes32 slotB = dataStructureUtil.randomBytes32();
    final Bytes32 slotC = dataStructureUtil.randomBytes32();
    final Bytes32 slotD = dataStructureUtil.randomBytes32();
    final Bytes32 slotE = dataStructureUtil.randomBytes32();
    final Bytes32 slot8 = dataStructureUtil.randomBytes32();

    // Pre-GLOAS chain: only base nodes
    addValidBlock(3, slotA, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(4, slotB, GENESIS_CHECKPOINT.getRoot());
    addValidBlock(5, slotC, slotA);
    addValidBlock(6, slotD, slotC);
    addValidBlock(7, slotE, slotD);

    // Slot-8 base (PENDING) + EMPTY arrive together (matches block import order)
    addValidBlock(8, slot8, slotE);
    protoArray.createEmptyNode(slot8);

    // Vote for the slot-8 block and apply score changes BEFORE the FULL node exists.
    // applyScoreChanges runs the full applyToNodes propagation, so chain ancestors end up with
    // bestDescendantIndex pointing to the EMPTY node (matching the snapshot's idx 0..5 → 7).
    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, slot8, false, false));
    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    // FULL node arrives later (e.g. payload becomes available). The facade only locally
    // updates bestChild/bestDescendant for PENDING's parent — propagation up the chain does
    // NOT happen, so chain ancestors keep bestDescendantIndex pointing at EMPTY even though
    // PENDING.bestChild flips to FULL via the GLOAS tiebreaker.
    protoArray.onExecutionPayload(slot8, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(slot8);

    final ProtoNode head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);

    assertThat(head.getBlockRoot()).isEqualTo(slot8);
    assertThat(head.getPayloadStatus()).isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);

    final int slot8FullIndex = protoArray.getFullNodeIndices().getInt(slot8);
    assertThat(protoArray.getNodeByIndex(slot8FullIndex)).isEqualTo(head);

    // Extend the chain: import slot-9 (PENDING+EMPTY) built on slot8.FULL, then add slot-9 FULL,
    // all without re-running applyScoreChanges. Chain ancestors still hold bestDescendantIndex
    // pointing at slot8.EMPTY, but slot8.FULL.bestDesc now stale-points to slot9.BASE and
    // slot9.BASE has slot9.FULL as its current bestChild. The findHead descent loop must walk
    // multiple resolveBestDescendant redirects:
    //   ancestor → slot8.EMPTY → resolveBestDescendant → slot8.FULL
    //                          → descend               → slot9.BASE
    //                          → resolveBestDescendant → slot9.FULL
    final Bytes32 slot9 = dataStructureUtil.randomBytes32();
    final UInt64 slot9ExecutionBlockNumber = EXECUTION_BLOCK_NUMBER.plus(1);
    final Bytes32 slot9ExecutionBlockHash = dataStructureUtil.randomBytes32();
    addValidBlockWithParentIndex(
        9, slot9, slot8, Optional.of(slot8FullIndex), EXECUTION_BLOCK_HASH);
    protoArray.createEmptyNode(slot9);
    protoArray.onExecutionPayload(slot9, slot9ExecutionBlockNumber, slot9ExecutionBlockHash);
    protoArray.markNodeValid(slot9);

    final ProtoNode extendedHead =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(extendedHead.getBlockRoot()).isEqualTo(slot9);
    assertThat(extendedHead.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);

    final int slot9FullIndex = protoArray.getFullNodeIndices().getInt(slot9);
    assertThat(protoArray.getNodeByIndex(slot9FullIndex)).isEqualTo(extendedHead);
  }

  @Test
  void findOptimisticHead_shouldContinuePastStaleEmptyLandingToNewGloasChild() {
    final Bytes32 slot8 = dataStructureUtil.randomBytes32();
    final Bytes32 slot9 = dataStructureUtil.randomBytes32();
    final Bytes32 slot10 = dataStructureUtil.randomBytes32();
    final Bytes32 slot9ExecutionBlockHash = dataStructureUtil.randomBytes32();

    addValidBlock(8, slot8, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(slot8);
    protoArray.onExecutionPayload(slot8, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    protoArray.markNodeValid(slot8);

    voteUpdater.putVote(UInt64.ZERO, new VoteTracker(Bytes32.ZERO, slot8, false, false));
    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    final int slot8FullIndex = protoArray.getFullNodeIndices().getInt(slot8);
    final ProtoNode slot8Head =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(slot8Head).isEqualTo(protoArray.getNodeByIndex(slot8FullIndex));

    // Import the next block and then its payload. Updating the variants locally leaves
    // slot8.FULL.bestDescendantIndex pointing at slot9.EMPTY, while slot9.BASE now prefers
    // slot9.FULL.
    addValidBlockWithParentIndex(
        9, slot9, slot8, Optional.of(slot8FullIndex), EXECUTION_BLOCK_HASH);
    protoArray.createEmptyNode(slot9);
    protoArray.onExecutionPayload(slot9, EXECUTION_BLOCK_NUMBER.plus(1), slot9ExecutionBlockHash);
    protoArray.markNodeValid(slot9);

    final int slot9EmptyIndex = protoArray.getEmptyNodeIndices().getInt(slot9);
    final int slot9FullIndex = protoArray.getFullNodeIndices().getInt(slot9);
    assertThat(protoArray.getNodeByIndex(slot8FullIndex).getBestDescendantIndex())
        .contains(slot9EmptyIndex);
    assertThat(protoArray.getProtoNode(slot9).orElseThrow().getBestChildIndex())
        .contains(slot9FullIndex);

    // Import another block built on slot9.FULL but do not add its FULL node yet. Head selection
    // must redirect the stale slot9.EMPTY landing point to slot9.FULL, then continue down to the
    // newly imported slot10.EMPTY node.
    addValidBlockWithParentIndex(
        10, slot10, slot9, Optional.of(slot9FullIndex), slot9ExecutionBlockHash);
    protoArray.createEmptyNode(slot10);

    final ProtoNode importedChildHead =
        protoArray.findOptimisticHead(UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT);
    assertThat(importedChildHead.getBlockRoot()).isEqualTo(slot10);
    assertThat(importedChildHead.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);

    final int slot10EmptyIndex = protoArray.getEmptyNodeIndices().getInt(slot10);
    assertThat(protoArray.getNodeByIndex(slot10EmptyIndex)).isEqualTo(importedChildHead);
  }

  @Test
  void gloas_onExecutionPayloadResult_invalid_shouldNotDirectlyInvalidateFullNode() {
    // Set up a Gloas block with base + EMPTY + FULL nodes (FULL stays optimistic)
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    // Verify FULL node exists and is optimistic
    assertThat(protoArray.getFullNodeIndices().containsKey(block1a)).isTrue();
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isOptimistic()).isTrue();

    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);

    // Mark payload as INVALID via the block-root API. Gloas should ignore the direct-invalid flag
    // here because exact invalidation requires the node-aware FCU path.
    gloasModel.onExecutionPayloadResult(
        protoArray.protoArray(),
        protoArray.blockNodeIndex(),
        block1a,
        ExecutionPayloadStatus.INVALID,
        Optional.empty(),
        true,
        new HeadSelectionContext(
            gloasModel, protoArray.blockNodeIndex(), UInt64.ZERO, Optional.empty()));

    // Base + EMPTY + FULL nodes should remain optimistic.
    assertThat(protoArray.contains(block1a)).isTrue();
    assertThat(protoArray.getProtoNode(block1a).orElseThrow().isOptimistic()).isTrue();
    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isOptimistic()).isTrue();
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isOptimistic()).isTrue();
  }

  @Test
  void gloas_onForkChoiceUpdatedResult_valid_shouldMarkExactNodeValid() {
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    final ForkChoiceNode emptyNode =
        protoArray.blockNodeIndex().getEmptyNode(block1a).orElseThrow();
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);
    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isOptimistic()).isTrue();
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isOptimistic()).isTrue();

    gloasModel.onForkChoiceUpdatedResult(
        protoArray.protoArray(),
        protoArray.blockNodeIndex(),
        emptyNode,
        ExecutionPayloadStatus.VALID,
        Optional.empty(),
        true,
        new HeadSelectionContext(
            gloasModel, protoArray.blockNodeIndex(), UInt64.ZERO, Optional.empty()));

    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isFullyValidated()).isTrue();
    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isOptimistic()).isTrue();
  }

  @Test
  void findOptimisticHead_gloasShouldThrowWhenWalkEndsOnPendingNodeWithoutEmptyVariant() {
    // Every Gloas block gets its EMPTY variant on import, so a PENDING leaf without one is a broken
    // tree rather than a legitimate head. The walk must fail loudly instead of returning PENDING.
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    applyScoreChanges(gloasModel, UInt64.valueOf(100), Optional.empty());

    assertThatThrownBy(
            () ->
                protoArray.findOptimisticHead(
                    UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has no valid EMPTY variant");
  }

  @Test
  void gloas_onForkChoiceUpdatedResult_invalidOnFull_shouldKeepBaseAndEmptyNodes() {
    // A FULL node owns its payload, so an INVALID verdict for it (without latestValidHash) marks
    // exactly that node: the block itself and its EMPTY variant stay.
    addOptimisticBlock(1, block1a, GENESIS_CHECKPOINT.getRoot());
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);

    final ForkChoiceNode fullNode = protoArray.blockNodeIndex().getFullNode(block1a).orElseThrow();
    final int baseNodeIndex = protoArray.getIndexByRoot(block1a).orElseThrow();
    final int emptyNodeIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    final int fullNodeIndex = protoArray.getFullNodeIndices().getInt(block1a);

    gloasModel.onForkChoiceUpdatedResult(
        protoArray.protoArray(),
        protoArray.blockNodeIndex(),
        fullNode,
        ExecutionPayloadStatus.INVALID,
        Optional.empty(),
        true,
        new HeadSelectionContext(
            gloasModel, protoArray.blockNodeIndex(), UInt64.ZERO, Optional.empty()));

    assertThat(protoArray.getNodeByIndex(fullNodeIndex).isInvalid()).isTrue();
    assertThat(protoArray.getNodeByIndex(baseNodeIndex).isOptimistic()).isTrue();
    assertThat(protoArray.getNodeByIndex(emptyNodeIndex).isOptimistic()).isTrue();
  }

  @Test
  void
      gloas_onForkChoiceUpdatedResult_invalidOnEmptyWithoutLatestValidHash_shouldInvalidateOwner() {
    // BASE and EMPTY nodes own no payload: they inherit the execution block hash of the nearest
    // FULL ancestor. An INVALID verdict for EMPTY(block2a) without latestValidHash is therefore a
    // verdict on FULL(block1a)'s payload and must invalidate that node and everything built on it,
    // while block1a itself and its EMPTY variant stay.
    final Bytes32 genesisPayloadHash = getExecutionBlockHash(GENESIS_CHECKPOINT.getRoot());
    addValidBlock(1, block1a, GENESIS_CHECKPOINT.getRoot(), genesisPayloadHash);
    protoArray.createEmptyNode(block1a);
    protoArray.onExecutionPayload(block1a, EXECUTION_BLOCK_NUMBER, EXECUTION_BLOCK_HASH);
    final int block1aBaseIndex = protoArray.getIndexByRoot(block1a).orElseThrow();
    final int block1aEmptyIndex = protoArray.getEmptyNodeIndices().getInt(block1a);
    final int block1aFullIndex = protoArray.getFullNodeIndices().getInt(block1a);

    // block2a builds on FULL(block1a) and has no payload of its own yet
    protoArray.onBlock(
        UInt64.valueOf(2),
        block2a,
        block1a,
        Optional.of(block1aFullIndex),
        dataStructureUtil.randomBytes32(),
        new BlockCheckpoints(
            GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT),
        EXECUTION_BLOCK_NUMBER,
        EXECUTION_BLOCK_HASH,
        true);
    protoArray.createEmptyNode(block2a);
    final ForkChoiceNode block2aEmptyNode =
        protoArray.blockNodeIndex().getEmptyNode(block2a).orElseThrow();
    final int block2aBaseIndex = protoArray.getIndexByRoot(block2a).orElseThrow();
    final int block2aEmptyIndex = protoArray.getEmptyNodeIndices().getInt(block2a);
    assertThat(protoArray.getNodeByIndex(block2aEmptyIndex).getExecutionBlockHash())
        .isEqualTo(EXECUTION_BLOCK_HASH);

    gloasModel.onForkChoiceUpdatedResult(
        protoArray.protoArray(),
        protoArray.blockNodeIndex(),
        block2aEmptyNode,
        ExecutionPayloadStatus.INVALID,
        Optional.empty(),
        true,
        new HeadSelectionContext(
            gloasModel, protoArray.blockNodeIndex(), UInt64.ZERO, Optional.empty()));

    assertThat(protoArray.getNodeByIndex(block1aFullIndex).isInvalid()).isTrue();
    assertThat(protoArray.getNodeByIndex(block2aBaseIndex).isInvalid()).isTrue();
    assertThat(protoArray.getNodeByIndex(block2aEmptyIndex).isInvalid()).isTrue();
    assertThat(protoArray.getNodeByIndex(block1aBaseIndex).isFullyValidated()).isTrue();
    assertThat(protoArray.getNodeByIndex(block1aEmptyIndex).isFullyValidated()).isTrue();
  }

  private void assertHead(final Bytes32 expectedBlockHash) {
    final ProtoNode node = protoArray.getProtoNode(expectedBlockHash).orElseThrow();
    assertThat(
            protoArray.findOptimisticHead(
                UInt64.valueOf(5), GENESIS_CHECKPOINT, GENESIS_CHECKPOINT))
        .isEqualTo(node);

    assertThat(node.getBestDescendantIndex()).isEmpty();
    assertThat(node.getBestChildIndex()).isEmpty();
  }

  private void applyScoreChanges() {
    applyScoreChanges(ForkChoiceModelPhase0.INSTANCE, UInt64.valueOf(5), Optional.empty());
  }

  private void applyScoreChanges(
      final ForkChoiceModel forkChoiceModel,
      final UInt64 currentSlot,
      final Optional<Bytes32> proposerBoostRoot) {
    protoArray.applyScoreChanges(
        computeDeltas(),
        UInt64.valueOf(5),
        GENESIS_CHECKPOINT,
        GENESIS_CHECKPOINT,
        forkChoiceModel,
        currentSlot,
        proposerBoostRoot);
  }

  private ForkChoiceModelGloas createGloasModel(
      final int payloadTimelyThreshold,
      final int dataAvailabilityTimelyThreshold,
      final Bytes32 blockRoot,
      final int payloadPresentVoteCount,
      final int dataAvailableVoteCount) {
    final PayloadTimelinessCommitteeVoteTracker payloadTimelinessCommitteeVoteTracker =
        new PayloadTimelinessCommitteeVoteTracker();
    for (int validatorIndex = 0;
        validatorIndex < Math.max(payloadPresentVoteCount, dataAvailableVoteCount);
        validatorIndex++) {
      payloadTimelinessCommitteeVoteTracker.recordVote(
          blockRoot,
          IntSet.of(validatorIndex),
          validatorIndex < payloadPresentVoteCount,
          validatorIndex < dataAvailableVoteCount);
    }
    return new ForkChoiceModelGloas(
        gloasSpecConfig,
        payloadTimelyThreshold,
        dataAvailabilityTimelyThreshold,
        payloadTimelinessCommitteeVoteTracker);
  }

  private void addValidBlock(final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    addValidBlock(slot, blockRoot, parentRoot, getExecutionBlockHash(blockRoot));
  }

  private void addValidBlock(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 executionBlockHash) {
    addOptimisticBlock(slot, blockRoot, parentRoot, executionBlockHash);
    protoArray.markNodeValid(blockRoot);
  }

  private void addValidBlockWithParentIndex(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> resolvedParentIndex) {
    addValidBlockWithParentIndex(
        slot, blockRoot, parentRoot, resolvedParentIndex, getExecutionBlockHash(blockRoot));
  }

  private void addValidBlockWithParentIndex(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Optional<Integer> resolvedParentIndex,
      final Bytes32 executionBlockHash) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        resolvedParentIndex,
        dataStructureUtil.randomBytes32(),
        new BlockCheckpoints(
            GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT),
        ZERO,
        executionBlockHash,
        true);
    protoArray.markNodeValid(blockRoot);
  }

  private void addOptimisticBlock(
      final long slot, final Bytes32 blockRoot, final Bytes32 parentRoot) {
    final Bytes32 executionBlockHash = getExecutionBlockHash(blockRoot);
    addOptimisticBlock(slot, blockRoot, parentRoot, executionBlockHash);
  }

  private void addOptimisticBlock(
      final long slot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 executionBlockHash) {
    protoArray.onBlock(
        UInt64.valueOf(slot),
        blockRoot,
        parentRoot,
        dataStructureUtil.randomBytes32(),
        new BlockCheckpoints(
            GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT, GENESIS_CHECKPOINT),
        ZERO,
        executionBlockHash,
        true);
  }

  private Bytes32 getExecutionBlockHash(final Bytes32 blockRoot) {
    return Hash.sha256(blockRoot);
  }

  private LongList computeDeltas() {
    final List<UInt64> balances =
        Collections.nCopies(voteUpdater.getHighestVotedValidatorIndex().intValue(), UInt64.ONE);
    return ProtoArrayScoreCalculator.computeDeltas(
        voteUpdater,
        protoArray.getTotalTrackedNodeCount(),
        protoArray.protoArray()::getNodeIndex,
        Optional.empty(),
        Optional.empty(),
        balances,
        balances,
        UInt64.ZERO,
        UInt64.ZERO,
        protoArray.protoArray(),
        protoArray.blockNodeIndex(),
        ForkChoiceModelPhase0.INSTANCE);
  }

  private class TestProtoArrayFacade {
    private final ProtoArray protoArray;
    private final BlockNodeVariantsIndex blockNodeIndex = new BlockNodeVariantsIndex();
    private HeadSelectionContext lastHeadSelectionContext =
        new HeadSelectionContext(
            ForkChoiceModelPhase0.INSTANCE, blockNodeIndex, UInt64.ZERO, Optional.empty());

    private TestProtoArrayFacade(final ProtoArray protoArray) {
      this.protoArray = protoArray;
    }

    private ProtoArray protoArray() {
      return protoArray;
    }

    private BlockNodeVariantsIndex blockNodeIndex() {
      return blockNodeIndex;
    }

    private boolean contains(final Bytes32 blockRoot) {
      return protoArray.containsNode(ForkChoiceNode.createBase(blockRoot));
    }

    private Optional<ProtoNode> getProtoNode(final Bytes32 blockRoot) {
      return protoArray.getNode(ForkChoiceNode.createBase(blockRoot));
    }

    private Optional<Integer> getIndexByRoot(final Bytes32 blockRoot) {
      return protoArray.getNodeIndex(ForkChoiceNode.createBase(blockRoot));
    }

    private Object2IntMap<Bytes32> getEmptyNodeIndices() {
      return getProjectionIndices(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
    }

    private Object2IntMap<Bytes32> getFullNodeIndices() {
      return getProjectionIndices(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
    }

    private Object2IntMap<Bytes32> getProjectionIndices(
        final ForkChoicePayloadStatus payloadStatus) {
      final Object2IntMap<Bytes32> indices = new Object2IntOpenHashMap<>();
      blockNodeIndex
          .variants()
          .forEach(
              variants ->
                  variants
                      .getNode(payloadStatus)
                      .flatMap(protoArray::getNodeIndex)
                      .ifPresent(index -> indices.put(variants.baseNode().blockRoot(), index)));
      return indices;
    }

    private ProtoNode getNodeByIndex(final int index) {
      return protoArray.getNodeByIndex(index);
    }

    private int getTotalTrackedNodeCount() {
      return protoArray.getTotalTrackedNodeCount();
    }

    private ProtoNode findOptimisticHead(
        final UInt64 currentEpoch,
        final Checkpoint justifiedCheckpoint,
        final Checkpoint finalizedCheckpoint) {
      return protoArray.findOptimisticHead(
          currentEpoch, justifiedCheckpoint, finalizedCheckpoint, lastHeadSelectionContext);
    }

    private Optional<ProtoNode> findOptimisticallySyncedMergeTransitionBlock(
        final Bytes32 blockRoot) {
      return protoArray.findOptimisticallySyncedMergeTransitionBlock(
          ForkChoiceNode.createBase(blockRoot));
    }

    private void applyScoreChanges(
        final LongList deltas,
        final UInt64 currentEpoch,
        final Checkpoint justifiedCheckpoint,
        final Checkpoint finalizedCheckpoint,
        final ForkChoiceModel forkChoiceModel,
        final UInt64 currentSlot,
        final Optional<Bytes32> proposerBoostRoot) {
      protoArray.applyScoreChanges(
          deltas,
          currentEpoch,
          justifiedCheckpoint,
          finalizedCheckpoint,
          new HeadSelectionContext(
              forkChoiceModel, blockNodeIndex, currentSlot, proposerBoostRoot));
      lastHeadSelectionContext =
          new HeadSelectionContext(forkChoiceModel, blockNodeIndex, currentSlot, proposerBoostRoot);
    }

    private void markNodeInvalid(final Bytes32 blockRoot, final Optional<Bytes32> latestValidHash) {
      protoArray.markNodeInvalid(
          ForkChoiceNode.createBase(blockRoot),
          latestValidHash,
          new HeadSelectionContext(
              ForkChoiceModelPhase0.INSTANCE, blockNodeIndex, UInt64.ZERO, Optional.empty()));
      pruneRemovedProjections();
    }

    private void markNodeValid(final Bytes32 blockRoot) {
      blockNodeIndex
          .getVariants(blockRoot)
          .ifPresentOrElse(
              variants -> variants.allNodes().forEach(protoArray::markNodeValid),
              () -> protoArray.markNodeValid(ForkChoiceNode.createBase(blockRoot)));
    }

    private void markParentChainInvalid(
        final Bytes32 blockRoot, final Optional<Bytes32> latestValidHash) {
      protoArray.markParentChainInvalid(
          ForkChoiceNode.createBase(blockRoot),
          latestValidHash,
          new HeadSelectionContext(
              ForkChoiceModelPhase0.INSTANCE, blockNodeIndex, UInt64.ZERO, Optional.empty()));
      pruneRemovedProjections();
    }

    private void setInitialCanonicalBlockRoot(final Bytes32 blockRoot) {
      protoArray.setInitialCanonicalBlockRoot(blockRoot, lastHeadSelectionContext);
    }

    private void setPruneThreshold(final int pruneThreshold) {
      protoArray.setPruneThreshold(pruneThreshold);
    }

    private void maybePrune(final Bytes32 finalizedRoot) {
      protoArray.maybePrune(ForkChoiceNode.createBase(finalizedRoot));
      pruneRemovedProjections();
    }

    private void updateParentBestChildAndDescendantForBlockVariants(final Bytes32 blockRoot) {
      blockNodeIndex
          .getVariants(blockRoot)
          .ifPresent(
              variants ->
                  variants
                      .allNodes()
                      .forEach(
                          nodeIdentity ->
                              protoArray.updateBestChildAndDescendantOfParent(
                                  nodeIdentity, lastHeadSelectionContext)));
    }

    private void onBlock(
        final UInt64 blockSlot,
        final Bytes32 blockRoot,
        final Bytes32 parentRoot,
        final Bytes32 stateRoot,
        final BlockCheckpoints checkpoints,
        final UInt64 executionBlockNumber,
        final Bytes32 executionBlockHash,
        final boolean optimisticallyProcessed) {
      onBlock(
          blockSlot,
          blockRoot,
          parentRoot,
          protoArray.containsNode(ForkChoiceNode.createBase(parentRoot))
              ? Optional.of(
                  protoArray.getNodeIndex(ForkChoiceNode.createBase(parentRoot)).orElseThrow())
              : Optional.empty(),
          stateRoot,
          checkpoints,
          executionBlockNumber,
          executionBlockHash,
          optimisticallyProcessed);
    }

    private void onBlock(
        final UInt64 blockSlot,
        final Bytes32 blockRoot,
        final Bytes32 parentRoot,
        final Optional<Integer> parentIndex,
        final Bytes32 stateRoot,
        final BlockCheckpoints checkpoints,
        final UInt64 executionBlockNumber,
        final Bytes32 executionBlockHash,
        final boolean optimisticallyProcessed) {
      protoArray.addNode(
          ForkChoiceNode.createBase(blockRoot),
          blockSlot,
          parentRoot,
          parentIndex.map(protoArray::getNodeByIndex).map(ProtoNode::getForkChoiceNode),
          stateRoot,
          checkpoints,
          executionBlockNumber,
          executionBlockHash,
          optimisticallyProcessed);
      blockNodeIndex.putBaseNode(blockRoot, blockSlot, ForkChoiceNode.createBase(blockRoot));
      updateParentBestChildAndDescendantForBlockVariants(blockRoot);
    }

    private void createEmptyNode(final Bytes32 blockRoot) {
      if (blockNodeIndex.getEmptyNode(blockRoot).isPresent()) {
        return;
      }
      final Optional<ProtoNode> maybeBaseNode = getProtoNode(blockRoot);
      if (maybeBaseNode.isEmpty()) {
        return;
      }

      final ProtoNodeData baseNode = maybeBaseNode.get().getBlockData();
      final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
      protoArray.addNode(
          emptyNode,
          baseNode.getSlot(),
          baseNode.getParentRoot(),
          Optional.of(ForkChoiceNode.createBase(blockRoot)),
          baseNode.getStateRoot(),
          baseNode.getCheckpoints(),
          baseNode.getExecutionBlockNumber(),
          baseNode.getExecutionBlockHash(),
          baseNode.isOptimistic());
      blockNodeIndex.attachEmptyNode(blockRoot, emptyNode);
      updateParentBestChildAndDescendantForBlockVariants(blockRoot);
    }

    private void onExecutionPayload(
        final Bytes32 blockRoot,
        final UInt64 executionBlockNumber,
        final Bytes32 executionBlockHash) {
      gloasModel.onExecutionPayload(
          protoArray, blockNodeIndex, blockRoot, executionBlockNumber, executionBlockHash, true);
      updateParentBestChildAndDescendantForBlockVariants(blockRoot);
    }

    private Optional<Integer> resolveParentIndex(
        final Bytes32 parentRoot, final Bytes32 childParentBlockHash) {
      return gloasModel
          .resolveParentNode(protoArray, blockNodeIndex, parentRoot, childParentBlockHash)
          .flatMap(node -> protoArray.getNodeIndex(node.getForkChoiceNode()));
    }

    private void pruneRemovedProjections() {
      blockNodeIndex.removeIf(
          blockRoot ->
              blockNodeIndex.getBaseNode(blockRoot).flatMap(protoArray::getNode).isEmpty());
    }
  }
}
