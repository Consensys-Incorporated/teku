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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;

/**
 * Milestone-parameterized counterpart to {@link ForkChoiceUtilReorgTest}, covering proposer re-org
 * behaviour that differs across forks.
 */
@TestSpecContext(milestone = {BELLATRIX, FULU})
class ForkChoiceUtilReorgMilestoneTest {

  @TestTemplate
  void getProposerHeadHandlesShufflingStabilityAtEpochBoundary(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();
    final SpecMilestone milestone = specContext.getSpecMilestone();
    final UInt64 proposalSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final UInt64 headSlot = proposalSlot.minus(UInt64.ONE);
    final SignedBlockAndState headBlockAndState =
        specContext.getDataStructureUtil().randomSignedBlockAndState(headSlot);
    final ForkChoiceNode head = ForkChoiceNode.createBase(headBlockAndState.getRoot());
    final ForkChoiceNode parent = ForkChoiceNode.createBase(headBlockAndState.getParentRoot());

    final ReadOnlyStore store = mock(ReadOnlyStore.class);
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = mock(ReadOnlyForkChoiceStrategy.class);
    final ProtoNodeData headNodeData = mock(ProtoNodeData.class);
    final UInt64 genesisTimeMillis = headBlockAndState.getState().getGenesisTime().times(1000);

    when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);
    when(store.getGenesisTimeMillis()).thenReturn(genesisTimeMillis);
    when(store.getTimeInMillis())
        .thenReturn(
            genesisTimeMillis.plus(
                proposalSlot.times(spec.getGenesisSpecConfig().getSlotDurationMillis())));
    when(store.getFinalizedCheckpoint())
        .thenReturn(specContext.getDataStructureUtil().randomCheckpoint(UInt64.ZERO));
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getBlockIfAvailable(head.blockRoot()))
        .thenReturn(headBlockAndState.getSignedBeaconBlock());
    when(store.isFfgCompetitive(head.blockRoot(), parent.blockRoot()))
        .thenReturn(Optional.of(true));
    when(store.getReorgThreshold()).thenReturn(UInt64.ONE);
    when(store.getParentThreshold()).thenReturn(UInt64.ONE);
    when(forkChoiceStrategy.blockSlot(parent.blockRoot()))
        .thenReturn(Optional.of(headSlot.minus(UInt64.ONE)));
    when(forkChoiceStrategy.getBlockData(head.blockRoot())).thenReturn(Optional.of(headNodeData));
    when(headNodeData.getWeight()).thenReturn(UInt64.ZERO);
    when(forkChoiceStrategy.getParentBeaconBlockNode(head)).thenReturn(Optional.of(parent));

    final ForkChoiceReorgContext context = new LateBlockReorgContext(store);
    final ForkChoiceNode proposerHead =
        spec.forMilestone(milestone)
            .getForkChoiceUtil()
            .getProposerHead(context, head, proposalSlot);

    assertThat(proposerHead).isEqualTo(milestone.isGreaterThanOrEqualTo(FULU) ? parent : head);
  }

  private record LateBlockReorgContext(ReadOnlyStore store) implements ForkChoiceReorgContext {

    @Override
    public ReadOnlyStore getStore() {
      return store;
    }

    @Override
    public Optional<ForkChoiceUtil.BlockTimeliness> getBlockTimeliness(final Bytes32 root) {
      return Optional.of(new ForkChoiceUtil.BlockTimeliness(false, false));
    }

    @Override
    public boolean isValidatorConnected(final int validatorIndex, final UInt64 slot) {
      return false;
    }

    @Override
    public BeaconState processSlots(final BeaconState state, final UInt64 slot)
        throws SlotProcessingException, EpochProcessingException {
      return state;
    }
  }
}
