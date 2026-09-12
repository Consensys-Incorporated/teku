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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteSnapshot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

class FastConfirmationCalculatorTest {

  // Minimal preset: SLOTS_PER_EPOCH == 8
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private static final Spec GLOAS_SPEC = TestSpecFactory.createMinimalGloas();
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);

  // A canonical linear chain where block at index i has slot i and parent = chain(i - 1).
  private final List<Bytes32> chain = new ArrayList<>();
  private final Map<Bytes32, Integer> slotByRoot = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    // Default to no votes; the weight tests re-stub with specific votes before building a
    // calculator.
    when(store.getVoteSnapshot())
        .thenReturn(VoteSnapshot.create(UInt64.ZERO, new VoteTracker[] {VoteTracker.DEFAULT}));
    when(forkChoice.getSupportedNode(any(), any(), any(), anyBoolean()))
        .thenAnswer(
            invocation ->
                Optional.of(ForkChoiceNode.createBase(invocation.<Bytes32>getArgument(1))));
  }

  @Test
  void shouldReturnBlockSlotAndEpoch() {
    buildLinearChain(12);
    final FastConfirmationCalculator calculator = calculator(chain.get(11), 11);

    assertThat(calculator.getBlockSlot(chain.get(10))).isEqualTo(UInt64.valueOf(10));
    // slot 10 -> epoch 1 with SLOTS_PER_EPOCH == 8
    assertThat(calculator.getBlockEpoch(chain.get(10))).isEqualTo(UInt64.ONE);
    assertThat(calculator.getBlockEpoch(chain.get(7))).isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldCollectAncestorRootsDownToButExcludingTerminal() {
    buildLinearChain(6);
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    // Ancestors of block 5 down to (excluding) block 2, oldest first.
    assertThat(calculator.getAncestorRoots(chain.get(5), chain.get(2)))
        .containsExactly(chain.get(3), chain.get(4), chain.get(5));
  }

  @Test
  void shouldReturnEmptyAncestorRootsWhenTerminalNotInChain() {
    buildLinearChain(6);
    // A terminal block that exists (has a slot) but is not on the chain of block 5.
    final Bytes32 sideTerminal = Bytes32.random();
    when(forkChoice.blockSlot(sideTerminal)).thenReturn(Optional.of(UInt64.valueOf(3)));
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    assertThat(calculator.getAncestorRoots(chain.get(5), sideTerminal)).isEmpty();
  }

  @Test
  void shouldResolveIsAncestor() {
    buildLinearChain(6);
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    assertThat(calculator.isAncestor(chain.get(5), chain.get(2))).isTrue();
    // A block is its own ancestor
    assertThat(calculator.isAncestor(chain.get(3), chain.get(3))).isTrue();
    // The descendant/ancestor relationship is not symmetric
    assertThat(calculator.isAncestor(chain.get(2), chain.get(5))).isFalse();
    // An unknown descendant is not a descendant of anything
    assertThat(calculator.isAncestor(Bytes32.random(), chain.get(2))).isFalse();
  }

  @Test
  void shouldComputeCheckpointForBlockAndCurrentTarget() {
    buildLinearChain(11);
    // currentSlot 10 -> currentEpoch 1
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    // Checkpoint block for epoch 0 is the block at the epoch-0 start slot (0).
    assertThat(calculator.getCheckpointForBlock(chain.get(5), UInt64.ZERO))
        .isEqualTo(new Checkpoint(UInt64.ZERO, chain.get(0)));

    // Current target = checkpoint for the head at the current epoch (1), start slot 8.
    assertThat(calculator.getCurrentTarget()).isEqualTo(new Checkpoint(UInt64.ONE, chain.get(8)));
  }

  @Test
  void shouldUseUnrealizedJustificationAsVotingSourceForPriorEpochBlock() {
    buildLinearChain(11);
    final Checkpoint realized = checkpoint(0);
    final Checkpoint unrealized = checkpoint(1);
    setCheckpoints(chain.get(5), realized, unrealized);
    // currentSlot 10 -> currentEpoch 1; block 5 is in epoch 0 (prior epoch)
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    assertThat(calculator.getVotingSource(chain.get(5))).isEqualTo(unrealized);
    assertThat(calculator.getUnrealizedJustification(chain.get(5))).isEqualTo(unrealized);
  }

  @Test
  void shouldUseRealizedJustificationAsVotingSourceForCurrentEpochBlock() {
    buildLinearChain(11);
    final Checkpoint realized = checkpoint(1);
    final Checkpoint unrealized = checkpoint(2);
    setCheckpoints(chain.get(9), realized, unrealized);
    // currentSlot 10 -> currentEpoch 1; block 9 is in epoch 1 (current epoch)
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    assertThat(calculator.getVotingSource(chain.get(9))).isEqualTo(realized);
  }

  @Test
  void shouldReturnTheProvidedPulledUpHeadState() {
    final BeaconState pulledUpHeadState = genesisState();
    // The loader supplies the head state already pulled up to the current epoch (via the store's
    // checkpoint-state cache when the head lags); the calculator uses it as-is.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(pulledUpHeadState, 0);

    assertThat(calculator.getPulledUpHeadState()).isSameAs(pulledUpHeadState);
  }

  @Test
  void shouldUnionAllSlotCommitteesToTheActiveValidatorSetOverAnEpoch() {
    final BeaconState headState = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(headState, 0);

    final IntSet allCommitteeMembers = new IntOpenHashSet();
    for (int slot = 0; slot < spec.getSlotsPerEpoch(UInt64.ZERO); slot++) {
      allCommitteeMembers.addAll(calculator.getSlotCommittee(UInt64.valueOf(slot)));
    }

    // Every active validator is assigned to exactly one committee per epoch, so the union of all
    // slot committees over epoch 0 must be exactly the active validator set.
    final IntSet activeValidators =
        new IntOpenHashSet(spec.getActiveValidatorIndices(headState, UInt64.ZERO));
    assertThat(allCommitteeMembers).isEqualTo(activeValidators);
    assertThat(calculator.getSlotCommittee(UInt64.ZERO)).isNotEmpty();
  }

  @Test
  void shouldQueryCommitteesFromThePulledUpHeadState() throws Exception {
    // A head lagging multiple epochs arrives from the loader already advanced to the current epoch
    // start (slot 16 for currentSlot 20 -> epoch 2). Committee queries for current-epoch slots
    // shuffle from it directly; the raw (genesis) head state would throw StateTooOldException.
    final BeaconState pulledUpHeadState = spec.processSlots(genesisState(), UInt64.valueOf(16));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(pulledUpHeadState, 20);

    // Slot 19 is in epoch 2 (SLOTS_PER_EPOCH == 8).
    assertThat(calculator.getSlotCommittee(UInt64.valueOf(19))).isNotEmpty();
  }

  @Test
  void shouldSumAttestationScoreForNonEquivocatingUnslashedVotersSupportingADescendant() {
    buildLinearChain(6);
    // Validator 5 votes for a descendant too, but is slashed, so it must be excluded.
    final BeaconState balanceSource = withSlashedValidator(genesisState(), 5);
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    0, vote(chain.get(5)), // descendant of chain[3] -> counts
                    1, vote(chain.get(2)), // ancestor of chain[3], not a descendant -> excluded
                    2, equivocatingVote(chain.get(5)), // equivocating -> excluded
                    4, vote(chain.get(3)), // chain[3] itself -> counts
                    5, vote(chain.get(5))))); // descendant but slashed -> excluded
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    final UInt64 expected =
        effectiveBalance(balanceSource, 0).plus(effectiveBalance(balanceSource, 4));
    assertThat(calculator.getAttestationScore(chain.get(3), balanceSource)).isEqualTo(expected);
  }

  @Test
  void shouldComputeChainAttestationScoresInOnePassMatchingPerBlockScores() {
    buildLinearChain(8);
    // Validator 5 votes for a descendant of the whole chain but is slashed -> excluded everywhere.
    final BeaconState balanceSource = withSlashedValidator(genesisState(), 5);
    final Bytes32 offChainRoot = Bytes32.random();
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    0, vote(chain.get(7)), // supports every scored block
                    1, vote(chain.get(4)), // supports chain[3..4] only
                    2, vote(chain.get(2)), // below the scored chain -> supports none of it
                    3, equivocatingVote(chain.get(7)), // equivocating -> excluded
                    4, vote(offChainRoot), // unknown to fork choice -> supports none
                    5, vote(chain.get(7))))); // slashed -> excluded
    final FastConfirmationCalculator calculator = calculator(chain.get(7), 7);

    final List<Bytes32> scoredChain =
        List.of(chain.get(3), chain.get(4), chain.get(5), chain.get(6), chain.get(7));
    final Map<Bytes32, UInt64> batchScores =
        calculator.computeChainAttestationScores(scoredChain, balanceSource);

    assertThat(batchScores).containsOnlyKeys(scoredChain);
    for (final Bytes32 root : scoredChain) {
      assertThat(batchScores.get(root))
          .isEqualTo(calculator.getAttestationScore(root, balanceSource));
    }
  }

  @Test
  void shouldMemoizeSlotCommitteesWithinTheSlotRun() {
    final BeaconState headState = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(headState, 0);

    assertThat(calculator.getSlotCommittee(UInt64.ZERO))
        .isSameAs(calculator.getSlotCommittee(UInt64.ZERO));
  }

  @Test
  void shouldSkipCommitteeComputationEntirelyWhenNoValidatorIsEquivocating() {
    // No equivocating votes: the score is zero by definition and no slot committee is materialized
    // — a committee query this far beyond the head state's epoch would otherwise throw.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(genesisState(), 0);

    assertThat(
            calculator.getEquivocationScore(
                genesisState(), UInt64.valueOf(1_000_000), UInt64.valueOf(1_000_001)))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldResolveGloasVoteToSupportedNodeBeforeCheckingAncestry() {
    buildLinearChain(6);
    final BeaconState balanceSource = gloasGenesisState();
    final Bytes32 votedRoot = chain.get(5);
    final UInt64 voteSlot = UInt64.valueOf(6);
    final ForkChoiceNode supportedNode =
        new ForkChoiceNode(votedRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    0,
                    new VoteTracker(
                        Bytes32.ZERO,
                        votedRoot,
                        false,
                        false,
                        voteSlot,
                        true,
                        UInt64.ZERO,
                        false))));
    when(forkChoice.getSupportedNode(voteSlot, votedRoot, voteSlot, true))
        .thenReturn(Optional.of(supportedNode));
    // The FULL path does not descend from chain[3], while the base PENDING path configured by
    // buildLinearChain does. Scoring the root alone would incorrectly include this vote.
    when(forkChoice.getAncestorNode(supportedNode, UInt64.valueOf(3)))
        .thenReturn(
            Optional.of(
                new ForkChoiceNode(Bytes32.random(), ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)));
    final FastConfirmationCalculator calculator =
        gloasCalculator(balanceSource, votedRoot, voteSlot.longValue());

    assertThat(calculator.getAttestationScore(chain.get(3), balanceSource)).isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldSumBlockSupportOnlyForInRangeVotersOfTheExactBlockRoot() {
    final BeaconState balanceSource = genesisState();
    final int voterForBlock = firstCommitteeMember(balanceSource, UInt64.ZERO); // slot 0
    final int voterForOther = firstCommitteeMember(balanceSource, UInt64.ONE); // slot 1
    final int outOfRangeVoter = firstCommitteeMember(balanceSource, UInt64.valueOf(3)); // slot 3
    final Bytes32 blockRoot = Bytes32.random();
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    voterForBlock, vote(blockRoot),
                    voterForOther, vote(Bytes32.random()),
                    outOfRangeVoter, vote(blockRoot))));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 0);

    // Range [0,1] covers voterForBlock (counts) and voterForOther (wrong root); slot 3 is excluded.
    assertThat(
            calculator.getBlockSupportBetweenSlots(
                balanceSource, blockRoot, UInt64.ZERO, UInt64.ONE))
        .isEqualTo(effectiveBalance(balanceSource, voterForBlock));
  }

  @Test
  void shouldExcludeEquivocatingVotersFromBlockSupport() {
    final BeaconState balanceSource = genesisState();
    final int voter = firstCommitteeMember(balanceSource, UInt64.ZERO);
    final Bytes32 blockRoot = Bytes32.random();
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(Map.of(voter, equivocatingVote(blockRoot))));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 0);

    assertThat(
            calculator.getBlockSupportBetweenSlots(
                balanceSource, blockRoot, UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldSumEquivocationScoreForInRangeEquivocatingValidators() {
    final BeaconState balanceSource = genesisState();
    final int equivocator = firstCommitteeMember(balanceSource, UInt64.ZERO); // slot 0
    final int outOfRange = firstCommitteeMember(balanceSource, UInt64.valueOf(3)); // slot 3
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    equivocator, equivocatingVote(Bytes32.random()),
                    outOfRange, equivocatingVote(Bytes32.random()))));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 0);

    // Only the equivocator assigned to slot 0 is inside the [0,0] range.
    assertThat(calculator.getEquivocationScore(balanceSource, UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(effectiveBalance(balanceSource, equivocator));
  }

  @Test
  void shouldComputeAdversarialWeightAsByzantineFractionWhenNoEquivocation() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 10);

    final UInt64 total = spec.getTotalActiveBalance(balanceSource);
    final UInt64 expected =
        estimate(total, 3, 6)
            .dividedBy(100)
            .times(FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    assertThat(
            calculator.computeAdversarialWeight(
                balanceSource, UInt64.valueOf(3), UInt64.valueOf(6)))
        .isEqualTo(expected);
  }

  @Test
  void shouldReduceAdversarialWeightByEquivocationScore() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    final UInt64 withoutEquivocation =
        calculatorWithHeadState(balanceSource, 10)
            .computeAdversarialWeight(balanceSource, UInt64.valueOf(3), UInt64.valueOf(6));

    // Make the slot-3 committee member equivocate; it is inside the [3,6] range.
    final int equivocator = firstCommitteeMember(balanceSource, UInt64.valueOf(3));
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(Map.of(equivocator, equivocatingVote(Bytes32.random()))));
    final UInt64 withEquivocation =
        calculatorWithHeadState(balanceSource, 10)
            .computeAdversarialWeight(balanceSource, UInt64.valueOf(3), UInt64.valueOf(6));

    assertThat(withEquivocation).isLessThan(withoutEquivocation);
  }

  @Test
  void shouldMemoizeAdversarialWeightPerSlotRange() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 10);

    // Identity, not just equality: the second call must return the memoized instance.
    assertThat(
            calculator.computeAdversarialWeight(
                balanceSource, UInt64.valueOf(3), UInt64.valueOf(6)))
        .isSameAs(
            calculator.computeAdversarialWeight(
                balanceSource, UInt64.valueOf(3), UInt64.valueOf(6)));
  }

  @Test
  void shouldMemoizeSafetyThresholdPerBlock() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 5);

    // Identity, not just equality: the second call must return the memoized instance.
    assertThat(calculator.computeSafetyThreshold(chain.get(3), balanceSource))
        .isSameAs(calculator.computeSafetyThreshold(chain.get(3), balanceSource));
  }

  @Test
  void shouldMemoizeHonestFfgSupportForCurrentTarget() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 12);

    // Identity, not just equality: the second call must return the memoized instance.
    assertThat(calculator.computeHonestFfgSupportForCurrentTarget())
        .isSameAs(calculator.computeHonestFfgSupportForCurrentTarget());
  }

  @Test
  void shouldDiscountParentSupportInEmptySlotsBeyondAdversarialWeight() {
    final BeaconState balanceSource = genesisState();
    // Block at slot 4 whose parent is at slot 2 leaves slot 3 empty.
    final Bytes32 parent = Bytes32.random();
    final Bytes32 block = Bytes32.random();
    when(forkChoice.blockSlot(parent)).thenReturn(Optional.of(UInt64.valueOf(2)));
    when(forkChoice.blockSlot(block)).thenReturn(Optional.of(UInt64.valueOf(4)));
    when(forkChoice.blockParentRoot(block)).thenReturn(Optional.of(parent));
    // A slot-3 committee member supports the parent across the empty slot.
    final int voter = firstCommitteeMember(balanceSource, UInt64.valueOf(3));
    when(store.getVoteSnapshot()).thenReturn(voteSnapshot(Map.of(voter, vote(parent))));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 10);

    final UInt64 total = spec.getTotalActiveBalance(balanceSource);
    final UInt64 adversarial =
        estimate(total, 3, 3)
            .dividedBy(100)
            .times(FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    final UInt64 expected = effectiveBalance(balanceSource, voter).minus(adversarial);
    assertThat(calculator.computeEmptySlotSupportDiscount(balanceSource, block))
        .isEqualTo(expected);
  }

  @Test
  void shouldComputeSafetyThresholdFromWeightsProposerScoreAndAdversarialWeight() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // Block chain[3] (slot 3), parent chain[2] (slot 2): no empty slot, so no support discount.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 5);

    final UInt64 total = spec.getTotalActiveBalance(balanceSource);
    // parentSlot + 1 = 3 .. currentSlot - 1 = 4
    final UInt64 maximumSupport = estimate(total, 3, 4);
    final UInt64 proposerScore = spec.getProposerBoostAmount(balanceSource);
    // Not crossing an epoch boundary, so the adversarial range starts at the block slot (3).
    final UInt64 adversarial =
        estimate(total, 3, 4)
            .dividedBy(100)
            .times(FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    final UInt64 expected =
        maximumSupport.plus(proposerScore).plus(adversarial.times(2)).dividedBy(2);
    assertThat(calculator.computeSafetyThreshold(chain.get(3), balanceSource)).isEqualTo(expected);
  }

  @Test
  void shouldNotConfirmBlockThatIsNotFullyValidated() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    when(forkChoice.isFullyValidated(chain.get(3))).thenReturn(false);
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 5);

    assertThat(calculator.isOneConfirmed(balanceSource, chain.get(3))).isFalse();
  }

  @Test
  void shouldConfirmBlockWhenSupportExceedsSafetyThreshold() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    when(forkChoice.isFullyValidated(chain.get(3))).thenReturn(true);
    // All active validators vote for the block, so support equals the total active balance.
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(allValidatorsVotingFor(balanceSource, chain.get(3))));
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 5);

    assertThat(calculator.isOneConfirmed(balanceSource, chain.get(3))).isTrue();
  }

  @Test
  void shouldNotConfirmBlockWithoutSupport() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    when(forkChoice.isFullyValidated(chain.get(3))).thenReturn(true);
    // No votes -> zero support, which cannot exceed the positive safety threshold.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(balanceSource, 5);

    assertThat(calculator.isOneConfirmed(balanceSource, chain.get(3))).isFalse();
  }

  @Test
  void shouldNotConfirmGloasBlockThatIsNotFullyValidated() {
    buildLinearChain(11);
    final BeaconState balanceSource = gloasGenesisState();
    // The is_one_confirmed spec MUST return false when the block is not VALID per optimistic sync.
    // A Gloas block that is present in fork choice but still OPTIMISTIC (e.g. inheriting an
    // unvalidated execution parent) must not be confirmed, even with full attestation support.
    when(forkChoice.isFullyValidated(chain.get(3))).thenReturn(false);
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(allValidatorsVotingFor(balanceSource, chain.get(3))));
    final FastConfirmationCalculator calculator = gloasCalculator(balanceSource, chain.get(5), 5);

    assertThat(calculator.isOneConfirmed(balanceSource, chain.get(3))).isFalse();
  }

  @Test
  void shouldConfirmGloasBlockWhenFullyValidatedAndSupportExceedsThreshold() {
    buildLinearChain(11);
    final BeaconState balanceSource = gloasGenesisState();
    when(forkChoice.isFullyValidated(chain.get(3))).thenReturn(true);
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(allValidatorsVotingFor(balanceSource, chain.get(3))));
    final FastConfirmationCalculator calculator = gloasCalculator(balanceSource, chain.get(5), 5);

    assertThat(calculator.isOneConfirmed(balanceSource, chain.get(3))).isTrue();
  }

  @Test
  void shouldScoreOnlyVotesWhoseTargetMatchesTheCurrentTarget() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // currentSlot 10 -> currentEpoch 1; current target = checkpoint(1, chain[8]).
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    0, vote(chain.get(9), 9), // target checkpoint(1, chain[8]) -> counts
                    1, vote(chain.get(9), 9), // counts
                    2, vote(chain.get(3), 3), // target checkpoint(0, chain[0]) -> excluded
                    3, equivocatingVote(chain.get(9))))); // equivocating -> excluded
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 10);

    final BeaconState pulledUp = calculator.getPulledUpHeadState();
    final UInt64 expected = effectiveBalance(pulledUp, 0).plus(effectiveBalance(pulledUp, 1));
    assertThat(calculator.getCurrentTargetScore()).isEqualTo(expected);
  }

  @Test
  void shouldSkipVotesWhoseCheckpointBlockHasBeenPrunedRatherThanThrowing() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // currentSlot 10 -> currentEpoch 1; current target = checkpoint(1, chain[8]).
    // A validator that has not voted since epoch 0 keeps an old message: its checkpoint block sits
    // below the finalized checkpoint and has been pruned from fork choice, so get_ancestor returns
    // empty. The block it voted for is still tracked (contains -> true), so the presence guard
    // passes. The vote must be skipped on the epoch mismatch (it can never match the current-epoch
    // target) instead of resolving its checkpoint block, which would otherwise throw. Regression
    // test for a Missing-ancestor crash seen replaying a fast-finalizing incident.
    final Bytes32 staleVotedRoot = Bytes32.random();
    when(forkChoice.contains(staleVotedRoot)).thenReturn(true);
    when(store.getVoteSnapshot())
        .thenReturn(
            voteSnapshot(
                Map.of(
                    0, vote(chain.get(9), 9), // target checkpoint(1, chain[8]) -> counts
                    1, vote(chain.get(9), 9), // counts
                    2, vote(staleVotedRoot, 3)))); // epoch-0 vote, checkpoint pruned -> skipped
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 10);

    final BeaconState pulledUp = calculator.getPulledUpHeadState();
    final UInt64 expected = effectiveBalance(pulledUp, 0).plus(effectiveBalance(pulledUp, 1));
    assertThat(calculator.getCurrentTargetScore()).isEqualTo(expected);
  }

  @Test
  void shouldComputeHonestFfgSupportFromRemainingWeightWhenNoVotes() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // No votes: honest support is just the honest fraction of the not-yet-assigned FFG weight.
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 12);

    final UInt64 total = spec.getTotalActiveBalance(calculator.getPulledUpHeadState());
    // epochStart(epoch 1) = 8, currentSlot - 1 = 11
    final UInt64 ffgWeightTillNow = estimate(total, 8, 11);
    final UInt64 expected =
        total
            .minusMinZero(ffgWeightTillNow)
            .dividedBy(100)
            .times(100 - FastConfirmationRuleUtil.CONFIRMATION_BYZANTINE_THRESHOLD);
    assertThat(calculator.computeHonestFfgSupportForCurrentTarget()).isEqualTo(expected);
  }

  @Test
  void shouldReportNoConflictingCheckpointWhenTargetIsGreatestUnrealizedJustified() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // currentSlot 10 -> current target = checkpoint(1, chain[8]); make it the greatest unrealized.
    final Checkpoint target = new Checkpoint(UInt64.ONE, chain.get(8));
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    when(store.getJustifiedCheckpoint()).thenReturn(zero);
    final ProtoNodeData blockData = mock(ProtoNodeData.class);
    when(blockData.getCheckpoints()).thenReturn(new BlockCheckpoints(zero, zero, target, zero));
    when(forkChoice.getBlockData()).thenReturn(List.of(blockData));
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 10);

    assertThat(calculator.willNoConflictingCheckpointBeJustified()).isTrue();
  }

  @Test
  void shouldExpectCurrentTargetToBeJustifiedUnderFullParticipation() {
    buildLinearChain(11);
    final BeaconState balanceSource = genesisState();
    // Everyone votes for the target block (chain[8]) in the current epoch.
    final Map<Integer, VoteTracker> allVotes = new HashMap<>();
    for (final int index : spec.getActiveValidatorIndices(balanceSource, UInt64.ONE)) {
      allVotes.put(index, vote(chain.get(8), 8));
    }
    when(store.getVoteSnapshot()).thenReturn(voteSnapshot(allVotes));
    final FastConfirmationCalculator calculator = calculator(balanceSource, chain.get(10), 10);

    assertThat(calculator.willCurrentTargetBeJustified()).isTrue();
  }

  @Test
  void shouldNotReconfirmWhenObservedJustifiedCheckpointNotInConfirmedChain() {
    buildLinearChain(11);
    final BeaconState genesis = genesisState();
    // Observed justified checkpoint references a block that is not on chain[3]'s chain.
    final Checkpoint observed = new Checkpoint(UInt64.ZERO, Bytes32.random());
    final FastConfirmationCalculator calculator =
        reconfirmationCalculator(genesis, chain.get(3), observed, 5);

    assertThat(calculator.isConfirmedChainSafe(chain.get(3))).isFalse();
  }

  @Test
  void shouldReconfirmWhenEveryChainBlockIsOneConfirmed() {
    buildLinearChain(11);
    final BeaconState genesis = genesisState();
    when(forkChoice.isFullyValidated(any())).thenReturn(true);
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(allValidatorsVotingFor(genesis, chain.get(3))));
    // Observed justified checkpoint = (0, chain[0]); reconfirms chain[1..3].
    final Checkpoint observed = new Checkpoint(UInt64.ZERO, chain.get(0));
    final FastConfirmationCalculator calculator =
        reconfirmationCalculator(genesis, chain.get(3), observed, 5);

    assertThat(calculator.isConfirmedChainSafe(chain.get(3))).isTrue();
  }

  @Test
  void shouldNotReconfirmWhenAChainBlockIsNotOneConfirmed() {
    buildLinearChain(11);
    final BeaconState genesis = genesisState();
    when(forkChoice.isFullyValidated(any())).thenReturn(true);
    // No votes -> zero support, so no chain block is one-confirmed.
    final Checkpoint observed = new Checkpoint(UInt64.ZERO, chain.get(0));
    final FastConfirmationCalculator calculator =
        reconfirmationCalculator(genesis, chain.get(3), observed, 5);

    assertThat(calculator.isConfirmedChainSafe(chain.get(3))).isFalse();
  }

  @Test
  void shouldNotAdvanceConfirmedRootWhenItIsAlreadyTheHead() {
    buildLinearChain(11);
    // Head's unrealized justification (epoch 0) satisfies the phase-2 guard.
    setCheckpoints(chain.get(10), checkpoint(0), checkpoint(0));
    // currentSlot 10 -> currentEpoch 1; head == confirmed == chain[10].
    final FastConfirmationCalculator calculator =
        descendantCalculator(genesisState(), chain.get(10), chain.get(9), 10);

    assertThat(calculator.findLatestConfirmedDescendant(chain.get(10))).isEqualTo(chain.get(10));
  }

  @Test
  void shouldAdvanceConfirmedRootToHeadAtEpochStartUnderFullParticipation() {
    buildLinearChain(11);
    final BeaconState genesis = genesisState();
    when(forkChoice.isFullyValidated(any())).thenReturn(true);
    // Everyone votes for the head, supporting every ancestor.
    when(store.getVoteSnapshot())
        .thenReturn(voteSnapshot(allValidatorsVotingFor(genesis, chain.get(10))));
    // Voting source of the previous slot head (chain[9]) for the phase-1 guard.
    setCheckpoints(chain.get(9), checkpoint(0), checkpoint(0));
    // currentSlot 8 = epoch 1 start; head=chain[10], previousSlotHead=chain[9], confirmed=chain[6].
    final FastConfirmationCalculator calculator =
        descendantCalculator(genesis, chain.get(10), chain.get(9), 8);

    assertThat(calculator.findLatestConfirmedDescendant(chain.get(6))).isEqualTo(chain.get(10));
  }

  @Test
  void shouldRevertToFinalizedWhenConfirmedRootIsStale() {
    buildLinearChain(18);
    when(store.getFinalizedCheckpoint()).thenReturn(new Checkpoint(UInt64.ZERO, chain.get(0)));
    // confirmed = chain[0] (epoch 0); currentSlot 17 -> currentEpoch 2, so confirmed is >1 epoch
    // old
    // and reverts to the finalized block, which is itself too old to advance.
    final FastConfirmationCalculator calculator =
        latestConfirmedCalculator(genesisState(), chain.get(0), chain.get(17), 17);

    assertThat(calculator.getLatestConfirmed()).isEqualTo(chain.get(0));
  }

  @Test
  void shouldKeepConfirmedRootWhenPreviousSlotHeadHasBeenPrunedFromForkChoice() {
    buildLinearChain(18);
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    // Head's unrealized justification is too old to trigger the current-epoch advance.
    setCheckpoints(chain.get(17), zero, zero);
    // Finalized at (epoch 1, chain[8]); the confirmed root starts there too.
    when(store.getFinalizedCheckpoint()).thenReturn(new Checkpoint(UInt64.ONE, chain.get(8)));
    // The previous slot head persisted in the FCR store is no longer tracked by fork choice
    // (protoarray pruned it below the finalized anchor).
    final Bytes32 prunedPreviousSlotHead = Bytes32.random();

    final FastConfirmationStore fcrStore =
        new FastConfirmationStore(
            store, chain.get(8), zero, zero, zero, prunedPreviousSlotHead, chain.get(17));
    final BeaconState state = genesisState();
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.of(state), state, state);
    // currentSlot 17 -> currentEpoch 2. The previous-epoch advance would dereference the pruned
    // previous slot head; the calculator must skip it and keep the finalized confirmed root.
    final FastConfirmationCalculator calculator =
        new FastConfirmationCalculator(spec, fcrStore, states, UInt64.valueOf(17));

    assertThat(calculator.getLatestConfirmed()).isEqualTo(chain.get(8));
  }

  @Test
  void shouldAdvanceRecentConfirmedRootTowardHead() {
    buildLinearChain(11);
    // Head's unrealized justification (epoch 0) satisfies find_latest_confirmed_descendant phase 2.
    setCheckpoints(chain.get(10), checkpoint(0), checkpoint(0));
    when(store.getFinalizedCheckpoint()).thenReturn(new Checkpoint(UInt64.ZERO, chain.get(0)));
    // confirmed == head == chain[10]; currentSlot 10 -> currentEpoch 1, not an epoch start.
    final FastConfirmationCalculator calculator =
        latestConfirmedCalculator(genesisState(), chain.get(10), chain.get(10), 10);

    assertThat(calculator.getLatestConfirmed()).isEqualTo(chain.get(10));
  }

  private UInt64 estimate(
      final UInt64 totalActiveBalance, final long startSlot, final long endSlot) {
    return FastConfirmationRuleUtil.estimateCommitteeWeightBetweenSlots(
        spec, totalActiveBalance, UInt64.valueOf(startSlot), UInt64.valueOf(endSlot));
  }

  private Map<Integer, VoteTracker> allValidatorsVotingFor(
      final BeaconState state, final Bytes32 root) {
    final Map<Integer, VoteTracker> votesByIndex = new HashMap<>();
    for (final int index : spec.getActiveValidatorIndices(state, UInt64.ZERO)) {
      votesByIndex.put(index, vote(root));
    }
    return votesByIndex;
  }

  private FastConfirmationCalculator calculator(final Bytes32 head, final long currentSlot) {
    return new FastConfirmationCalculator(
        spec, fcrStore(head), placeholderStates(), UInt64.valueOf(currentSlot));
  }

  private FastConfirmationCalculator calculatorWithHeadState(
      final BeaconState headState, final long currentSlot) {
    return calculator(headState, Bytes32.random(), currentSlot);
  }

  private FastConfirmationCalculator calculator(
      final BeaconState headState, final Bytes32 head, final long currentSlot) {
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.empty(), mock(BeaconState.class), headState);
    return new FastConfirmationCalculator(
        spec, fcrStore(head), states, UInt64.valueOf(currentSlot));
  }

  private FastConfirmationCalculator reconfirmationCalculator(
      final BeaconState state,
      final Bytes32 head,
      final Checkpoint currentObservedJustified,
      final long currentSlot) {
    // The state doubles as the previous balance source and the committee shuffling source.
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.of(state), mock(BeaconState.class), state);
    return new FastConfirmationCalculator(
        spec, fcrStore(head, currentObservedJustified), states, UInt64.valueOf(currentSlot));
  }

  private FastConfirmationCalculator descendantCalculator(
      final BeaconState state,
      final Bytes32 head,
      final Bytes32 previousSlotHead,
      final long currentSlot) {
    // The state doubles as the current balance source and the committee shuffling source.
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    final FastConfirmationStore fcrStore =
        new FastConfirmationStore(store, Bytes32.ZERO, zero, zero, zero, previousSlotHead, head);
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.empty(), state, state);
    return new FastConfirmationCalculator(spec, fcrStore, states, UInt64.valueOf(currentSlot));
  }

  private FastConfirmationCalculator latestConfirmedCalculator(
      final BeaconState state,
      final Bytes32 confirmedRoot,
      final Bytes32 head,
      final long currentSlot) {
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    // The observed justified checkpoint root must be an in-tree block; get_latest_confirmed reads
    // its slot unconditionally. chain[0] exists in every test chain.
    final Checkpoint observedJustified = new Checkpoint(UInt64.ZERO, chain.get(0));
    final FastConfirmationStore fcrStore =
        new FastConfirmationStore(store, confirmedRoot, zero, observedJustified, zero, head, head);
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.of(state), state, state);
    return new FastConfirmationCalculator(spec, fcrStore, states, UInt64.valueOf(currentSlot));
  }

  private FastConfirmationStore fcrStore(final Bytes32 head) {
    return fcrStore(head, new Checkpoint(UInt64.ZERO, Bytes32.ZERO));
  }

  private FastConfirmationStore fcrStore(
      final Bytes32 head, final Checkpoint currentObservedJustified) {
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    return new FastConfirmationStore(
        store, Bytes32.ZERO, zero, currentObservedJustified, zero, Bytes32.ZERO, head);
  }

  private FastConfirmationStates placeholderStates() {
    return new FastConfirmationStates(
        Optional.empty(), mock(BeaconState.class), mock(BeaconState.class));
  }

  private BeaconState genesisState() {
    return ChainBuilder.create(spec).generateGenesis().getState();
  }

  private BeaconState gloasGenesisState() {
    return ChainBuilder.create(GLOAS_SPEC).generateGenesis().getState();
  }

  private FastConfirmationCalculator gloasCalculator(
      final BeaconState headState, final Bytes32 head, final long currentSlot) {
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.empty(), mock(BeaconState.class), headState);
    return new FastConfirmationCalculator(
        GLOAS_SPEC, fcrStore(head), states, UInt64.valueOf(currentSlot));
  }

  private void buildLinearChain(final int length) {
    for (int slot = 0; slot < length; slot++) {
      final Bytes32 root = Bytes32.random();
      chain.add(root);
      slotByRoot.put(root, slot);
    }
    for (int slot = 0; slot < length; slot++) {
      final Bytes32 root = chain.get(slot);
      when(forkChoice.blockSlot(root)).thenReturn(Optional.of(UInt64.valueOf(slot)));
      when(forkChoice.contains(root)).thenReturn(true);
      final Bytes32 parent = slot > 0 ? chain.get(slot - 1) : Bytes32.ZERO;
      when(forkChoice.blockParentRoot(root)).thenReturn(Optional.of(parent));
    }
    // get_ancestor(root, slot): walk up the parent chain until reaching a block at or before slot.
    when(forkChoice.getAncestor(any(), any()))
        .thenAnswer(
            invocation -> {
              final Bytes32 root = invocation.getArgument(0);
              final UInt64 targetSlot = invocation.getArgument(1);
              return ancestorIndex(root, targetSlot).map(chain::get);
            });
    // Node-based ancestry used by ForkChoiceUtil.isAncestor.
    when(forkChoice.getAncestorNode(any(), any()))
        .thenAnswer(
            invocation -> {
              final ForkChoiceNode node = invocation.getArgument(0);
              final UInt64 targetSlot = invocation.getArgument(1);
              return ancestorIndex(node.blockRoot(), targetSlot)
                  .map(chain::get)
                  .map(ForkChoiceNode::createBase);
            });
  }

  private Optional<Integer> ancestorIndex(final Bytes32 root, final UInt64 targetSlot) {
    final Integer index = slotByRoot.get(root);
    if (index == null) {
      return Optional.empty();
    }
    int i = index;
    while (UInt64.valueOf(i).isGreaterThan(targetSlot)) {
      i--;
    }
    return Optional.of(i);
  }

  private void setCheckpoints(
      final Bytes32 root, final Checkpoint justified, final Checkpoint unrealizedJustified) {
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    final ProtoNodeData data = mock(ProtoNodeData.class);
    when(data.getCheckpoints())
        .thenReturn(new BlockCheckpoints(justified, zero, unrealizedJustified, zero));
    when(forkChoice.getBlockData(root)).thenReturn(Optional.of(data));
  }

  private Checkpoint checkpoint(final int epoch) {
    return new Checkpoint(UInt64.valueOf(epoch), Bytes32.random());
  }

  private VoteSnapshot voteSnapshot(final Map<Integer, VoteTracker> votesByIndex) {
    final int size = votesByIndex.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    final VoteTracker[] voteArray = new VoteTracker[size];
    votesByIndex.forEach((index, voteTracker) -> voteArray[index] = voteTracker);
    return VoteSnapshot.create(UInt64.valueOf(size - 1), voteArray);
  }

  private VoteTracker vote(final Bytes32 root) {
    return new VoteTracker(Bytes32.ZERO, root);
  }

  private VoteTracker vote(final Bytes32 root, final long slot) {
    return new VoteTracker(
        Bytes32.ZERO, root, false, false, UInt64.valueOf(slot), false, UInt64.ZERO, false);
  }

  private VoteTracker equivocatingVote(final Bytes32 root) {
    return vote(root).createNextEquivocating();
  }

  private UInt64 effectiveBalance(final BeaconState state, final int validatorIndex) {
    return state.getValidators().get(validatorIndex).getEffectiveBalance();
  }

  private int firstCommitteeMember(final BeaconState state, final UInt64 slot) {
    return spec.getBeaconCommittee(state, slot, UInt64.ZERO).getInt(0);
  }

  private BeaconState withSlashedValidator(final BeaconState state, final int index) {
    return state.updated(
        mutable ->
            mutable
                .getValidators()
                .set(index, mutable.getValidators().get(index).withSlashed(true)));
  }
}
