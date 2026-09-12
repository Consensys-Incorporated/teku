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

package tech.pegasys.teku.spec.logic.versions.gloas.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilGloasTest {

  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.ONE;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ForkChoiceUtilGloas forkChoiceUtil;
  private UInt64 gloasSlot;
  private BeaconState justifiedState;

  @BeforeEach
  void setUp() {
    spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    dataStructureUtil = new DataStructureUtil(spec);
    gloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH);
    forkChoiceUtil = ForkChoiceUtilGloas.required(spec.atSlot(gloasSlot).getForkChoiceUtil());
    justifiedState = dataStructureUtil.randomBeaconState(gloasSlot);
  }

  @Test
  void isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope_shouldReturnTrue() {
    assertThat(forkChoiceUtil.isDataAvailabilityCheckDeferredToExecutionPayloadEnvelope()).isTrue();
  }

  @Test
  void getPayloadAttributeWithdrawalsUsesEffectiveState() {
    final UInt64 stateSlot = gloasSlot.plus(spec.getSlotsPerEpoch(gloasSlot));
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(stateSlot).getSchemaDefinitions());
    final ExecutionRequests parentExecutionRequests =
        schemaDefinitions.getExecutionRequestsSchema().getDefault();
    final Bytes32 latestExecutionPayloadBlockHash = Bytes32.fromHexString("0x01");
    final Bytes32 latestBlockHash = Bytes32.fromHexString("0x02");
    final UInt64 builderIndex = UInt64.ZERO;
    final UInt64 parentPayloadValue = UInt64.valueOf(1234);
    final ExecutionPayloadBid latestExecutionPayloadBid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .create(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                latestExecutionPayloadBlockHash,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomEth1Address(),
                dataStructureUtil.randomUInt64(),
                builderIndex,
                UInt64.ZERO,
                parentPayloadValue,
                UInt64.ZERO,
                schemaDefinitions
                    .getExecutionPayloadBidSchema()
                    .getBlobKzgCommitmentsSchema()
                    .createFromElements(dataStructureUtil.randomBlobKzgCommitments().asList()),
                parentExecutionRequests.hashTreeRoot());

    final BeaconState originalState =
        dataStructureUtil
            .stateBuilderGloas(100, 1, 100)
            .slot(stateSlot)
            .build()
            .updated(
                state -> {
                  final MutableBeaconStateGloas stateGloas =
                      MutableBeaconStateGloas.required(state);
                  stateGloas.setLatestBlockHash(latestBlockHash);
                  stateGloas.setLatestExecutionPayloadBid(latestExecutionPayloadBid);
                  stateGloas.setNextWithdrawalBuilderIndex(builderIndex);
                  stateGloas.setBuilders(
                      BeaconStateSchemaGloas.required(schemaDefinitions.getBeaconStateSchema())
                          .getBuildersSchema()
                          .createFromElements(
                              List.of(
                                  dataStructureUtil
                                      .builderBuilder()
                                      .balance(parentPayloadValue)
                                      .build())));
                });
    final Bytes32 originalStateRoot = originalState.hashTreeRoot();

    final SszList<Withdrawal> actual =
        forkChoiceUtil.getPayloadAttributeWithdrawals(originalState, parentExecutionRequests);

    assertThat(actual).isNotEmpty();
    assertThat(actual).extracting(Withdrawal::getAmount).contains(parentPayloadValue);
    assertThat(actual).isNotEqualTo(spec.getExpectedWithdrawals(originalState).orElseThrow());
    assertThat(originalState.hashTreeRoot()).isEqualTo(originalStateRoot);
    assertThat(BeaconStateGloas.required(originalState).getLatestBlockHash())
        .isEqualTo(latestBlockHash);
  }

  @Test
  void
      computeBlockTimeliness_shouldMarkBlockTimelyForAttestationsAndPtcBeforeAttestationDeadline() {
    final int millisIntoSlot = forkChoiceUtil.getAttestationDueMillis() - 1;

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, millisIntoSlot))
        .isEqualTo(new BlockTimeliness(true, true));
  }

  @Test
  void computeBlockTimeliness_shouldKeepPtcTimelyAfterAttestationDeadlineButBeforePtcDeadline() {
    final int attestationDueMillis = forkChoiceUtil.getAttestationDueMillis();
    final int ptcDueMillis = forkChoiceUtil.getPayloadAttestationDueMillis().orElseThrow();

    assertThat(attestationDueMillis).isLessThan(ptcDueMillis);

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, attestationDueMillis))
        .isEqualTo(new BlockTimeliness(false, true));
  }

  @Test
  void computeBlockTimeliness_shouldMarkBlockLateForAttestationsAndPtcAtPtcDeadline() {
    final int ptcDueMillis = forkChoiceUtil.getPayloadAttestationDueMillis().orElseThrow();

    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot, ptcDueMillis))
        .isEqualTo(new BlockTimeliness(false, false));
  }

  @Test
  void getPayloadDueMillis_shouldUsePayloadDueBps() {
    // PAYLOAD_DUE_BPS is 5000 (50% of SLOT_DURATION_MS)
    assertThat(forkChoiceUtil.getPayloadDueMillis())
        .hasValue(spec.getSlotDurationMillis(gloasSlot) / 2);
    // PAYLOAD_ATTESTATION_DUE_BPS remains 7500 (75%), so the payload deadline is now earlier
    assertThat(forkChoiceUtil.getPayloadDueMillis())
        .isNotEqualTo(forkChoiceUtil.getPayloadAttestationDueMillis());
  }

  @Test
  void
      computeBlockTimeliness_shouldMarkBlockLateForAttestationsAndPtcWhenBlockIsNotFromCurrentSlot() {
    assertThat(forkChoiceUtil.computeBlockTimeliness(gloasSlot, gloasSlot.plus(1), 0))
        .isEqualTo(new BlockTimeliness(false, false));
  }

  @Test
  void getParentPayloadStatus_shouldReturnFull_whenParentBlockHashMatchesMessageBlockHash() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockIsPreGloas() {
    // Create parent block which is pre-Gloas
    final UInt64 preGloasSlot = spec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH).minusMinZero(1);
    final BeaconBlock parentBlock = dataStructureUtil.randomBeaconBlock(preGloasSlot);

    // Create current block that references any block hash (doesn't matter since the check is not
    // done) but its parent is the pre-Gloas block
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(
            parentBlock.getRoot(), dataStructureUtil.randomBytes32());

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldReturnEmpty_whenParentBlockHashDoesNotMatch() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<ForkChoicePayloadStatus> result =
        forkChoiceUtil.getParentPayloadStatus(store, currentBlock);

    assertThat(result).isCompletedWithValue(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void getParentPayloadStatus_shouldThrowException_whenParentBlockNotFound() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.getParentPayloadStatus(store, currentBlock.getMessage()))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  @Test
  void isParentNodeFull_shouldReturnTrue_whenParentHasFullPayload() {
    // Create parent block with a specific block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references the parent's block hash
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), parentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(true);
  }

  @Test
  void isParentNodeFull_shouldReturnFalse_whenParentHasEmptyPayload() {
    // Create parent block with one block hash
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(parentBlockHash);

    // Create current block that references a DIFFERENT parent block hash
    final Bytes32 differentParentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconBlock currentBlock =
        createBlockWithParentAndParentBlockHash(parentBlock.getRoot(), differentParentBlockHash);

    // Mock store
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentBlock)));

    // Test
    final SafeFuture<Boolean> result = forkChoiceUtil.isParentNodeFull(store, currentBlock);

    assertThat(result).isCompletedWithValue(false);
  }

  @Test
  void isParentNodeFull_shouldThrowException_whenParentBlockNotFound() {
    final BeaconBlock currentBlock = dataStructureUtil.randomBeaconBlock();

    // Mock store with no parent block
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.retrieveBlock(currentBlock.getParentRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    // Test
    assertThat(forkChoiceUtil.isParentNodeFull(store, currentBlock))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableThat()
        .withCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("Parent block not found");
  }

  @Test
  void isPayloadVerified_shouldReturnTrue_whenPayloadIsAvailable() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getExecutionPayloadIfAvailable(currentBlock.getRoot()))
        .thenReturn(
            Optional.of(
                dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(currentBlock)));
    assertThat(forkChoiceUtil.isPayloadVerified(store, currentBlock.getRoot())).isTrue();
  }

  @Test
  void isPayloadVerified_shouldReturnFalse_whenPayloadIsNotAvailable() {
    final SignedBeaconBlock currentBlock = dataStructureUtil.randomSignedBeaconBlock();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    when(store.getExecutionPayloadIfAvailable(currentBlock.getRoot())).thenReturn(Optional.empty());
    assertThat(forkChoiceUtil.isPayloadVerified(store, currentBlock.getRoot())).isFalse();
  }

  @Test
  void getFullPayloadVoteHint_matchesAttestationIndex() {
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.ZERO)).isFalse();
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.ONE)).isTrue();
    assertThat(forkChoiceUtil.getFullPayloadVoteHint(UInt64.valueOf(2))).isFalse();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenProposerBoostRootIsUnknown() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.empty());
    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, context, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentNotFromPreviousSlot() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(UInt64.valueOf(5)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(UInt64.valueOf(3))); // gap > 1

    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, context, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentIsFromSameSlot() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));

    assertThat(
            forkChoiceUtil.shouldApplyProposerBoost(
                boostRoot, context, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentIsNotWeak() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    final ForkChoiceUtilGloas forkChoiceUtilSpy = spy(forkChoiceUtil);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot.plus(1)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot)); // consecutive
    doReturn(false).when(forkChoiceUtilSpy).isHeadWeak(store, parentRoot, UInt64.ZERO);
    assertThat(
            forkChoiceUtilSpy.shouldApplyProposerBoost(
                boostRoot, context, UInt64.ZERO, justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsTrue_whenParentIsWeakWithoutPtcTimelyEquivocation() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    final ForkChoiceUtilGloas forkChoiceUtilSpy = spy(forkChoiceUtil);
    final SignedBeaconBlock parent = mock(SignedBeaconBlock.class);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot.plus(1)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot)); // consecutive
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(parent));
    when(parent.getSlot()).thenReturn(gloasSlot);
    when(parent.getProposerIndex()).thenReturn(UInt64.ONE);
    doReturn(true).when(forkChoiceUtilSpy).isHeadWeak(store, parentRoot, UInt64.valueOf(100));
    assertThat(
            forkChoiceUtilSpy.shouldApplyProposerBoost(
                boostRoot, context, UInt64.valueOf(100), justifiedState))
        .isTrue();
  }

  @Test
  void shouldApplyProposerBoost_returnsFalse_whenParentIsWeakWithPtcTimelyEquivocation() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    final ForkChoiceUtilGloas forkChoiceUtilSpy = spy(forkChoiceUtil);
    final SignedBeaconBlock parent = mock(SignedBeaconBlock.class);
    when(context.getStore()).thenReturn(store);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(strategy.blockParentRoot(boostRoot)).thenReturn(Optional.of(parentRoot));
    when(strategy.blockSlot(boostRoot)).thenReturn(Optional.of(gloasSlot.plus(1)));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(parent));
    when(parent.getSlot()).thenReturn(gloasSlot);
    when(parent.getProposerIndex()).thenReturn(UInt64.ONE);
    when(context.isPtcTimelyProposerEquivocation(gloasSlot, UInt64.ONE, parentRoot))
        .thenReturn(true);
    doReturn(true).when(forkChoiceUtilSpy).isHeadWeak(store, parentRoot, UInt64.valueOf(100));

    assertThat(
            forkChoiceUtilSpy.shouldApplyProposerBoost(
                boostRoot, context, UInt64.valueOf(100), justifiedState))
        .isFalse();
  }

  @Test
  void isHeadWeak_shouldIgnoreAppliedProposerBoostWhenUsingProtoArrayWeight() {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final BeaconState headState = dataStructureUtil.randomBeaconState(gloasSlot);
    final ProtoNodeData headNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(headNode.getWeight()).thenReturn(proposerBoostAmount);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockStateIfAvailable(headRoot)).thenReturn(Optional.of(headState));
    when(store.getVote(ArgumentMatchers.any())).thenReturn(VoteTracker.DEFAULT);
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(boostRoot));
    when(strategy.getBlockData(headRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(headNode));
    when(strategy.blockSlot(headRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(ForkChoiceNode.createBase(boostRoot), gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(headRoot)));

    assertThat(forkChoiceUtil.isHeadWeak(store, headRoot, UInt64.ONE)).isTrue();
  }

  @Test
  void isHeadWeak_shouldReturnFalseWhenRequiredStatesAreUnavailable() {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.empty());
    when(store.getBlockStateIfAvailable(headRoot)).thenReturn(Optional.empty());

    assertThat(forkChoiceUtil.isHeadWeak(store, headRoot, UInt64.ONE)).isFalse();
  }

  @Test
  void isParentStrong_shouldIgnoreAppliedProposerBoostWhenEvaluatingResolvedParentNode() {
    final Bytes32 boostRoot = dataStructureUtil.randomBytes32();
    final BeaconBlock parentBlock = createBlockWithBlockHash(dataStructureUtil.randomBytes32());
    final Bytes32 parentRoot = parentBlock.getRoot();
    final BeaconBlock headBlock =
        createBlockWithParentAndParentBlockHash(
            parentRoot,
            BeaconBlockBodyGloas.required(parentBlock.getBody())
                .getSignedExecutionPayloadBid()
                .getMessage()
                .getBlockHash());
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final SignedBeaconBlock signedParent = mock(SignedBeaconBlock.class);
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final ProtoNodeData parentNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(parentNode.getWeight()).thenReturn(proposerBoostAmount);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.of(signedParent));
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(boostRoot));
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(headBlock);
    when(signedParent.getMessage()).thenReturn(parentBlock);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(parentNode));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(ForkChoiceNode.createBase(boostRoot), gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(parentRoot)));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, UInt64.ZERO)).isFalse();
  }

  @Test
  void isParentStrong_shouldScoreParentWithPendingVariantRegardlessOfPayloadLinkage() {
    final UInt64 parentThreshold = UInt64.ONE;
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final ProtoNodeData pendingNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(pendingNode.getWeight()).thenReturn(parentThreshold.plus(1));
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(pendingNode));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, parentThreshold)).isTrue();
  }

  @Test
  void isParentStrong_shouldReturnFalseWhenImmediateInputsAreUnavailable() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.empty());
    when(store.getBlockIfAvailable(parentRoot)).thenReturn(Optional.empty());
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(signedHead.getMessage()).thenReturn(dataStructureUtil.randomBeaconBlock(gloasSlot));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, UInt64.ONE)).isFalse();
  }

  @Test
  void isParentStrong_shouldExcludeProposerBoostFromPendingParentWeight() {
    assertParentStrengthWithProposerBoost(UInt64.ONE, UInt64.ONE, false);
  }

  @Test
  void isParentStrong_shouldReturnTrueWhenUnboostedPendingParentWeightExceedsThreshold() {
    assertParentStrengthWithProposerBoost(UInt64.valueOf(2), UInt64.ONE, true);
  }

  private void assertParentStrengthWithProposerBoost(
      final UInt64 attestationWeight,
      final UInt64 parentThreshold,
      final boolean expectedToBeStrong) {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final SignedBeaconBlock signedHead = mock(SignedBeaconBlock.class);
    final UInt64 proposerBoostAmount = spec.getProposerBoostAmount(justifiedState);
    final UInt64 boostedWeight = proposerBoostAmount.plus(attestationWeight);
    final ProtoNodeData parentNode = mock(ProtoNodeData.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);

    when(parentNode.getWeight()).thenReturn(boostedWeight);
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(parentRoot));
    when(signedHead.getParentRoot()).thenReturn(parentRoot);
    when(strategy.getBlockData(parentRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(parentNode));
    when(strategy.blockSlot(parentRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(ForkChoiceNode.createBase(parentRoot), gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(parentRoot)));

    assertThat(forkChoiceUtil.isParentStrong(store, signedHead, parentThreshold))
        .isEqualTo(expectedToBeStrong);
  }

  @Test
  void getProposerHead_preservesFullParentPayloadStatusOnReorg() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode fullParent = ForkChoiceNode.createFull(parentRoot);

    assertThat(getProposerHeadAfterLateReorg(fullParent, true)).isEqualTo(fullParent);
  }

  @Test
  void getProposerHead_downgradesReorgedFullParentToEmptyWhenNotBuildingOnFull() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode fullParent = ForkChoiceNode.createFull(parentRoot);

    assertThat(getProposerHeadAfterLateReorg(fullParent, false))
        .isEqualTo(ForkChoiceNode.createEmpty(parentRoot));
  }

  @Test
  void getProposerHead_returnsPendingReorgedParentAsIs() {
    final ForkChoiceNode pendingParent =
        ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());

    assertThat(getProposerHeadAfterLateReorg(pendingParent, false)).isEqualTo(pendingParent);
  }

  @Test
  void isAncestor_returnsTrueWhenRootAndPayloadStatusMatch() {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Bytes32 ancestorRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode node = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    final ForkChoiceNode ancestor = ForkChoiceNode.createFull(ancestorRoot);

    when(strategy.blockSlot(ancestorRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(node, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createFull(ancestorRoot)));

    assertThat(forkChoiceUtil.isAncestor(strategy, node, ancestor)).isTrue();
  }

  @Test
  void isAncestor_returnsTrueWhenAncestorIsPendingWildcard() {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Bytes32 ancestorRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode node = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    // A PENDING ancestor matches any resolved payload status for the same block root.
    final ForkChoiceNode ancestor = ForkChoiceNode.createBase(ancestorRoot);

    when(strategy.blockSlot(ancestorRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(node, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createFull(ancestorRoot)));

    assertThat(forkChoiceUtil.isAncestor(strategy, node, ancestor)).isTrue();
  }

  @Test
  void isAncestor_returnsFalseWhenPayloadStatusDiffersAndAncestorIsNotPending() {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Bytes32 ancestorRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode node = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    final ForkChoiceNode ancestor = ForkChoiceNode.createFull(ancestorRoot);

    when(strategy.blockSlot(ancestorRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(node, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createEmpty(ancestorRoot)));

    assertThat(forkChoiceUtil.isAncestor(strategy, node, ancestor)).isFalse();
  }

  @Test
  void isAncestor_returnsFalseWhenResolvedRootDiffers() {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Bytes32 ancestorRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode node = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    final ForkChoiceNode ancestor = ForkChoiceNode.createBase(ancestorRoot);

    when(strategy.blockSlot(ancestorRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(node, gloasSlot))
        .thenReturn(Optional.of(ForkChoiceNode.createBase(dataStructureUtil.randomBytes32())));

    assertThat(forkChoiceUtil.isAncestor(strategy, node, ancestor)).isFalse();
  }

  @Test
  void isAncestor_returnsFalseWhenAncestorNodeCannotBeResolved() {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Bytes32 ancestorRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode node = ForkChoiceNode.createBase(dataStructureUtil.randomBytes32());
    final ForkChoiceNode ancestor = ForkChoiceNode.createBase(ancestorRoot);

    when(strategy.blockSlot(ancestorRoot)).thenReturn(Optional.of(gloasSlot));
    when(strategy.getAncestorNode(node, gloasSlot)).thenReturn(Optional.empty());

    assertThat(forkChoiceUtil.isAncestor(strategy, node, ancestor)).isFalse();
  }

  // Helper methods to create blocks with specific properties
  private BeaconBlock createBlockWithBlockHash(final Bytes32 blockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                blockHash, // Set the desired block hash
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations())
                      .parentExecutionRequests(body.getParentExecutionRequests());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            newBody);
  }

  private BeaconBlock createBlockWithParentAndParentBlockHash(
      final Bytes32 parentRoot, final Bytes32 parentBlockHash) {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(gloasSlot);
    final BeaconBlockBodyGloas body = BeaconBlockBodyGloas.required(block.getBody());
    final SignedExecutionPayloadBid signedBid = body.getSignedExecutionPayloadBid();
    final ExecutionPayloadBid bid = signedBid.getMessage();

    // Create a new bid with the desired parent block hash
    final ExecutionPayloadBid newBid =
        bid.getSchema()
            .create(
                parentBlockHash, // Set the desired parent block hash
                bid.getParentBlockRoot(),
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());

    // Create a new signed bid with the new bid
    final SignedExecutionPayloadBid newSignedBid =
        signedBid.getSchema().create(newBid, signedBid.getSignature());

    // Create a new body with the new signed bid
    final BeaconBlockBody newBody =
        body.getSchema()
            .createBlockBody(
                builder -> {
                  builder
                      .randaoReveal(body.getRandaoReveal())
                      .eth1Data(body.getEth1Data())
                      .graffiti(body.getGraffiti())
                      .attestations(body.getAttestations())
                      .proposerSlashings(body.getProposerSlashings())
                      .attesterSlashings(body.getAttesterSlashings())
                      .deposits(body.getDeposits())
                      .voluntaryExits(body.getVoluntaryExits())
                      .syncAggregate(body.getSyncAggregate())
                      .blsToExecutionChanges(body.getBlsToExecutionChanges())
                      .signedExecutionPayloadBid(newSignedBid)
                      .payloadAttestations(body.getPayloadAttestations())
                      .parentExecutionRequests(body.getParentExecutionRequests());
                  return SafeFuture.COMPLETE;
                })
            .join();

    // Create a new block with the new body and parent root
    return block
        .getSchema()
        .create(
            block.getSlot(),
            block.getProposerIndex(),
            parentRoot, // Set the desired parent root
            block.getStateRoot(),
            newBody);
  }

  private ForkChoiceNode getProposerHeadAfterLateReorg(
      final ForkChoiceNode parentNode, final boolean shouldBuildOnFull) {
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ForkChoiceReorgContext context = mock(ForkChoiceReorgContext.class);
    final SignedBeaconBlock head = dataStructureUtil.randomSignedBeaconBlock(gloasSlot);
    final ForkChoiceNode headNode = ForkChoiceNode.createFull(head.getRoot());
    final UInt64 proposalSlot = gloasSlot.increment();
    final ProtoNodeData parentData = mock(ProtoNodeData.class);

    when(context.getStore()).thenReturn(store);
    when(context.getBlockTimeliness(head.getRoot()))
        .thenReturn(Optional.of(new BlockTimeliness(false, false)));
    when(store.getForkChoiceStrategy()).thenReturn(strategy);
    when(store.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(store.getTimeInMillis()).thenReturn(UInt64.ZERO);
    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
    when(store.getJustifiedStateIfAvailable()).thenReturn(Optional.of(justifiedState));
    when(store.getBlockStateIfAvailable(head.getRoot())).thenReturn(Optional.of(justifiedState));
    when(store.getParentThreshold()).thenReturn(UInt64.ZERO);
    when(store.getReorgThreshold()).thenReturn(UInt64.MAX_VALUE);
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getBlockIfAvailable(ArgumentMatchers.any())).thenReturn(Optional.of(head));
    when(store.isFfgCompetitive(head.getRoot(), head.getParentRoot()))
        .thenReturn(Optional.of(true));
    when(parentData.getWeight()).thenReturn(UInt64.ONE);
    when(strategy.getBlockData(
            head.getParentRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING))
        .thenReturn(Optional.of(parentData));
    when(strategy.blockSlot(head.getParentRoot())).thenReturn(Optional.of(gloasSlot.minus(1)));
    when(strategy.getParentBeaconBlockNode(headNode)).thenReturn(Optional.of(parentNode));
    when(strategy.shouldBuildOnFull(store, proposalSlot, parentNode)).thenReturn(shouldBuildOnFull);

    return forkChoiceUtil.getProposerHead(context, headNode, proposalSlot);
  }
}
