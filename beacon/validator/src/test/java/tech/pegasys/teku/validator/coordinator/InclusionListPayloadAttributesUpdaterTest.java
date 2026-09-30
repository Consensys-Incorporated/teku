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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class InclusionListPayloadAttributesUpdaterTest {

  private final Spec spec = mock(Spec.class);
  private final Spec hezeSpec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(hezeSpec);
  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final BeaconState state = mock(BeaconState.class);
  private final BeaconState proposerState = mock(BeaconState.class);
  private final InclusionListPayloadAttributesUpdater inclusionListPayloadAttributesUpdater =
      new InclusionListPayloadAttributesUpdater(
          forkChoiceNotifier, proposersDataManager, combinedChainDataClient, spec);

  @Test
  void onInclusionListDue_shouldNotRefreshWhenNotProposer() throws Exception {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();

    when(combinedChainDataClient.getStateAtSlotExact(inclusionListSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(spec.processSlots(state, proposerSlot)).thenReturn(proposerState);

    assertThatSafeFuture(
            inclusionListPayloadAttributesUpdater.onInclusionListDue(inclusionListSlot))
        .isCompletedWithEmptyOptional();

    verifyNoInteractions(forkChoiceNotifier);
  }

  @Test
  void onInclusionListDue_shouldRefreshPayloadIdWithInclusionListTransactions() throws Exception {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode parentForkChoiceNode = ForkChoiceNode.createFull(parentRoot);
    final ChainHead chainHead = mock(ChainHead.class);
    final Bytes8 payloadId = Bytes8.fromHexString("0x0102030405060708");
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(
            payloadId, mock(ForkChoiceState.class), mock(PayloadBuildingAttributes.class));

    when(combinedChainDataClient.getStateAtSlotExact(inclusionListSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(spec.processSlots(state, proposerSlot)).thenReturn(proposerState);
    when(proposersDataManager.isProposerForSlot(proposerSlot, proposerState)).thenReturn(true);
    when(spec.getBlockRootAtSlot(proposerState, inclusionListSlot)).thenReturn(parentRoot);
    when(combinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
    when(chainHead.getRoot()).thenReturn(parentRoot);
    when(chainHead.getForkChoiceNode()).thenReturn(parentForkChoiceNode);
    when(forkChoiceNotifier.preparePayloadAttributes(parentForkChoiceNode, proposerSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    assertThatSafeFuture(
            inclusionListPayloadAttributesUpdater.onInclusionListDue(inclusionListSlot))
        .isCompletedWithOptionalContaining(payloadId);

    verify(forkChoiceNotifier).preparePayloadAttributes(parentForkChoiceNode, proposerSlot);
  }
}
