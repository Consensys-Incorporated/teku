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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilReorgTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.ONE;

  @ParameterizedTest
  @MethodSource("isProposingOnTimeCases")
  void isProposingOnTimeHandlesBoundaryConditions(
      final long millisFromGenesis, final boolean expectedResult) {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withTimeInMillis(setup.genesisTimeMillis.plus(millisFromGenesis));

    assertThat(setup.baseForkChoiceUtil.isProposingOnTime(setup.store, slot))
        .isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @MethodSource("forkChoiceStabilityCases")
  void isForkChoiceStableAndFinalizationOkHandlesBoundaryConditions(
      final int slot, final boolean expectedResult) {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.baseForkChoiceUtil.isForkChoiceStableAndFinalizationOk(
                setup.store, UInt64.valueOf(slot)))
        .isEqualTo(expectedResult);
  }

  @Test
  void getProposerHeadReturnsHeadWhenTimely() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), true);

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
    assertThat(setup.harness.headWeakChecks).isZero();
    assertThat(setup.harness.parentStrongChecks).isZero();
  }

  @Test
  void getProposerHeadReturnsHeadWhenProposerBoostIsActive() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    when(setup.store.getProposerBoostRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenShufflingIsNotStable() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(8)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenHeadBlockIsMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenFinalizationIsNotOk() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(25)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenFfgIsNotCompetitive() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgNotCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenParentSlotIsMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenProposalSlotSkipsAhead() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(3)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsParentWhenAllChecksPass() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    final ForkChoiceNode parent =
        ForkChoiceNode.createBase(setup.signedBlockAndState.getParentRoot());
    when(setup.forkChoiceStrategy.getParentBeaconBlockNode(head)).thenReturn(Optional.of(parent));

    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(parent);
  }

  @Test
  void getProposerHeadReturnsParentForConfirmedEquivocationAgainstWeakHead() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), true);
    setup.context.proposerEquivocation = true;
    setup.harness.headWeak = true;

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    final ForkChoiceNode parent =
        ForkChoiceNode.createBase(setup.signedBlockAndState.getParentRoot());
    when(setup.forkChoiceStrategy.getParentBeaconBlockNode(head)).thenReturn(Optional.of(parent));

    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(parent);
  }

  @Test
  void getProposerHeadReturnsHeadWhenHeadIsStrong() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.parentStrong = true;

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void getProposerHeadReturnsHeadWhenParentIsWeak() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;

    final ForkChoiceNode head = ForkChoiceNode.createBase(setup.signedBlockAndState.getRoot());
    assertThat(setup.harness.getProposerHead(setup.context, head, UInt64.valueOf(2)))
        .isEqualTo(head);
  }

  @Test
  void isProposerBoostActiveMatchesLateBlockReorgLogicSemantics() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    final Bytes32 headRoot = setup.signedBlockAndState.getRoot();

    when(setup.store.getProposerBoostRoot()).thenReturn(Optional.of(headRoot));
    assertThat(setup.baseForkChoiceUtil.isProposerBoostActive(setup.store, headRoot)).isFalse();

    when(setup.store.getProposerBoostRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    assertThat(setup.baseForkChoiceUtil.isProposerBoostActive(setup.store, headRoot)).isTrue();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenHeadIsTimely() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), true);

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenHeadBlockMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenFfgIsNotCompetitive() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.withFfgNotCompetitive();

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenParentSlotMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withFfgCompetitive();

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenParentStateMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenWeightChecksFail() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    when(setup.store.getBlockStateIfAvailable(any()))
        .thenReturn(Optional.of(setup.signedBlockAndState.getState()));
    setup.context.validatorConnected = true;

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsTrueWhenAllChecksPass() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;
    when(setup.store.getBlockStateIfAvailable(any()))
        .thenReturn(Optional.of(setup.signedBlockAndState.getState()));
    setup.context.validatorConnected = true;

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context,
                setup.signedBlockAndState.getRoot(),
                setup.signedBlockAndState.getSlot()))
        .isTrue();
  }

  @Test
  void shouldOverrideFcuCheckWeightsReturnsFalseForMultiSlotReorg() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;

    assertThat(
            setup.harness.shouldOverrideFcuCheckWeights(
                setup.context,
                setup.signedBlockAndState.getBlock(),
                setup.signedBlockAndState.getRoot(),
                UInt64.valueOf(3),
                UInt64.valueOf(3)))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckWeightsReturnsTrueForWeakHeadAndStrongParent() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;

    assertThat(
            setup.harness.shouldOverrideFcuCheckWeights(
                setup.context,
                setup.signedBlockAndState.getBlock(),
                setup.signedBlockAndState.getRoot(),
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isTrue();
  }

  @Test
  void shouldOverrideFcuCheckWeightsReturnsFalseForStrongHead() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.parentStrong = true;

    assertThat(
            setup.harness.shouldOverrideFcuCheckWeights(
                setup.context,
                setup.signedBlockAndState.getBlock(),
                setup.signedBlockAndState.getRoot(),
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckWeightsReturnsFalseForWeakParent() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;

    assertThat(
            setup.harness.shouldOverrideFcuCheckWeights(
                setup.context,
                setup.signedBlockAndState.getBlock(),
                setup.signedBlockAndState.getRoot(),
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreStateReturnsFalseWhenStateIsMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.harness.shouldOverrideFcuCheckProposerPreState(
                setup.context, UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreStateReturnsFalseWhenValidatorIsNotConnected() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    when(setup.store.getBlockStateIfAvailable(any()))
        .thenReturn(Optional.of(setup.signedBlockAndState.getState()));
    setup.context.validatorConnected = false;

    assertThat(
            setup.harness.shouldOverrideFcuCheckProposerPreState(
                setup.context, UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreStateReturnsTrueWhenValidatorIsConnected() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    when(setup.store.getBlockStateIfAvailable(any()))
        .thenReturn(Optional.of(setup.signedBlockAndState.getState()));

    assertThat(
            setup.harness.shouldOverrideFcuCheckProposerPreState(
                setup.context, UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isTrue();
  }

  @Test
  void isParentSlotOkReturnsTrueWhenParentIsOneSlotBeforeHead() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withParentSlot(Optional.of(UInt64.ZERO));

    assertThat(setup.harness.isParentSlotOk(setup.store, setup.signedBlockAndState.getBlock()))
        .isTrue();
  }

  @Test
  void isCurrentSlotOkReturnsTrueWhenProposalIsOneSlotAfterHead() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.harness.isCurrentSlotOk(setup.signedBlockAndState.getBlock(), UInt64.valueOf(2)))
        .isTrue();
  }

  @Test
  void isCurrentSlotOkReturnsFalseWhenProposalSlotSkipsAhead() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.harness.isCurrentSlotOk(setup.signedBlockAndState.getBlock(), UInt64.valueOf(3)))
        .isFalse();
  }

  @Test
  void isHeadWeakDefaultsToFalseWhenNodeMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.baseForkChoiceUtil.isHeadWeak(
                setup.store, setup.signedBlockAndState.getRoot(), UInt64.ONE))
        .isFalse();
  }

  @Test
  void isHeadWeakUsesThreshold() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withNodeWeight(setup.signedBlockAndState.getRoot(), UInt64.ONE);

    assertThat(
            setup.baseForkChoiceUtil.isHeadWeak(
                setup.store, setup.signedBlockAndState.getRoot(), UInt64.valueOf(2)))
        .isTrue();
    assertThat(
            setup.baseForkChoiceUtil.isHeadWeak(
                setup.store, setup.signedBlockAndState.getRoot(), UInt64.ONE))
        .isFalse();
  }

  @Test
  void isParentStrongDefaultsToTrueWhenNodeMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.baseForkChoiceUtil.isParentStrong(
                setup.store, setup.signedBlockAndState.getBlock(), UInt64.ONE))
        .isTrue();
  }

  @Test
  void isParentStrongUsesThreshold() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withNodeWeight(setup.signedBlockAndState.getBlock().getParentRoot(), UInt64.valueOf(3));

    assertThat(
            setup.baseForkChoiceUtil.isParentStrong(
                setup.store, setup.signedBlockAndState.getBlock(), UInt64.valueOf(2)))
        .isTrue();
    assertThat(
            setup.baseForkChoiceUtil.isParentStrong(
                setup.store, setup.signedBlockAndState.getBlock(), UInt64.valueOf(3)))
        .isFalse();
  }

  private static Stream<Arguments> isProposingOnTimeCases() {
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(UInt64.ONE).getForkChoiceUtil();
    final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
    final int proposerReorgCutoffMillis = forkChoiceUtil.getProposerReorgCutoffMillis();

    return Stream.of(
        Arguments.of((long) millisPerSlot - 500, true),
        Arguments.of((long) millisPerSlot, true),
        Arguments.of((long) millisPerSlot + proposerReorgCutoffMillis, true),
        Arguments.of((long) millisPerSlot + proposerReorgCutoffMillis + 1, false),
        Arguments.of((long) millisPerSlot + forkChoiceUtil.getAttestationDueMillis(), false));
  }

  private static Stream<Arguments> forkChoiceStabilityCases() {
    return Stream.of(
        Arguments.of(0, false),
        Arguments.of(1, true),
        Arguments.of(7, true),
        Arguments.of(8, false),
        Arguments.of(23, true),
        Arguments.of(24, false),
        Arguments.of(25, false));
  }

  private class ReorgTestSetup {
    private final SignedBlockAndState signedBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(slot);
    private final UInt64 genesisTime = signedBlockAndState.getState().getGenesisTime();
    private final UInt64 genesisTimeMillis = genesisTime.times(1000);
    private final ForkChoiceUtil baseForkChoiceUtil;
    private final ForkChoiceUtilHarness harness;
    private final ReadOnlyStore store = mock(ReadOnlyStore.class);
    private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        mock(ReadOnlyForkChoiceStrategy.class);
    private final TestForkChoiceReorgContext context = new TestForkChoiceReorgContext(store);

    private ReorgTestSetup() {
      final SpecVersion specVersion = spec.atSlot(slot);
      baseForkChoiceUtil = specVersion.getForkChoiceUtil();
      harness =
          new ForkChoiceUtilHarness(
              specVersion.getConfig(),
              specVersion.beaconStateAccessors(),
              specVersion.getEpochProcessor(),
              specVersion.getAttestationUtil(),
              specVersion.miscHelpers());

      when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);
      when(store.getGenesisTime()).thenReturn(genesisTime);
      when(store.getGenesisTimeMillis()).thenReturn(genesisTimeMillis);
      when(store.getTimeInMillis()).thenReturn(genesisTimeMillis);
      when(store.getTimeSeconds()).thenReturn(genesisTime);
      when(store.getFinalizedCheckpoint())
          .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
      when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
      when(store.getBlockIfAvailable(any())).thenReturn(Optional.empty());
      when(store.getBlockStateIfAvailable(any())).thenReturn(Optional.empty());
      when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.empty());
      when(forkChoiceStrategy.blockSlot(any())).thenReturn(Optional.empty());
    }

    private void withHeadBlock() {
      when(store.getBlockIfAvailable(any())).thenReturn(signedBlockAndState.getSignedBeaconBlock());
    }

    private void withNodeWeight(final Bytes32 root, final UInt64 weight) {
      final ProtoNodeData blockData = mock(ProtoNodeData.class);
      when(blockData.getWeight()).thenReturn(weight);
      when(forkChoiceStrategy.getBlockData(root)).thenReturn(Optional.of(blockData));
    }

    private void withParentSlot(final Optional<UInt64> maybeSlot) {
      when(forkChoiceStrategy.blockSlot(signedBlockAndState.getParentRoot())).thenReturn(maybeSlot);
    }

    private void withFfgCompetitive() {
      when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.of(true));
    }

    private void withFfgNotCompetitive() {
      when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.of(false));
    }

    private void withTimeInMillis(final UInt64 currentTimeMillis) {
      when(store.getTimeInMillis()).thenReturn(currentTimeMillis);
      when(store.getTimeSeconds()).thenReturn(currentTimeMillis.dividedBy(1000));
    }

    private void withCurrentSlot(final UInt64 currentSlot) {
      final UInt64 currentTimeMillis =
          genesisTimeMillis.plus(
              currentSlot.times(spec.getGenesisSpecConfig().getSlotDurationMillis()));
      withTimeInMillis(currentTimeMillis);
    }
  }

  private static class TestForkChoiceReorgContext implements ForkChoiceReorgContext {
    private final ReadOnlyStore store;
    private final Map<Bytes32, ForkChoiceUtil.BlockTimeliness> blockTimeliness = new HashMap<>();
    private boolean validatorConnected = true;
    private boolean proposerEquivocation = false;

    private TestForkChoiceReorgContext(final ReadOnlyStore store) {
      this.store = store;
    }

    @Override
    public ReadOnlyStore getStore() {
      return store;
    }

    @Override
    public Optional<ForkChoiceUtil.BlockTimeliness> getBlockTimeliness(final Bytes32 root) {
      return Optional.ofNullable(blockTimeliness.get(root));
    }

    @Override
    public boolean isValidatorConnected(final int validatorIndex, final UInt64 slot) {
      return validatorConnected;
    }

    @Override
    public boolean isProposerEquivocation(
        final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
      return proposerEquivocation;
    }

    @Override
    public BeaconState processSlots(final BeaconState state, final UInt64 slot)
        throws SlotProcessingException, EpochProcessingException {
      return state;
    }

    private void setBlockTimeliness(final Bytes32 root, final boolean isTimely) {
      blockTimeliness.put(root, new ForkChoiceUtil.BlockTimeliness(isTimely, false));
    }
  }
}
