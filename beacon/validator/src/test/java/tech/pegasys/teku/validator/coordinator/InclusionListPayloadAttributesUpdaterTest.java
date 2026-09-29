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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListEntry;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
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
  private final InclusionListStore inclusionListStore = mock(InclusionListStore.class);
  private final BeaconState state = mock(BeaconState.class);
  private final BeaconState proposerState = mock(BeaconState.class);
  private final InclusionListPayloadAttributesUpdater inclusionListPayloadAttributesUpdater =
      new InclusionListPayloadAttributesUpdater(
          forkChoiceNotifier,
          proposersDataManager,
          inclusionListStore,
          combinedChainDataClient,
          spec);

  @Test
  void onInclusionListDue_shouldNotRequestPayloadIdWhenNoTransactions() throws Exception {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();
    final InclusionList emptyInclusionList = dataStructureUtil.randomInclusionList(0);

    when(combinedChainDataClient.getStateAtSlotExact(inclusionListSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(spec.processSlots(state, proposerSlot)).thenReturn(proposerState);
    when(proposersDataManager.isProposerForSlot(proposerSlot, proposerState)).thenReturn(true);
    when(inclusionListStore.getInclusionLists(inclusionListSlot))
        .thenReturn(Optional.of(List.of(createEntry(emptyInclusionList))));

    assertThatSafeFuture(
            inclusionListPayloadAttributesUpdater.onInclusionListDue(inclusionListSlot))
        .isCompletedWithEmptyOptional();

    verifyNoInteractions(forkChoiceNotifier);
  }

  @Test
  void onInclusionListDue_shouldRefreshPayloadIdWithInclusionListTransactions() throws Exception {
    assertRefreshTransactions(false);
  }

  @Test
  void onInclusionListDue_shouldDeduplicateTransactionsWithinAndAcrossLists() throws Exception {
    assertRefreshTransactions(true);
  }

  private void assertRefreshTransactions(final boolean overlappingLists) throws Exception {
    final UInt64 inclusionListSlot = UInt64.valueOf(10);
    final UInt64 proposerSlot = inclusionListSlot.increment();
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ForkChoiceNode parentForkChoiceNode = ForkChoiceNode.createFull(parentRoot);
    final ChainHead chainHead = mock(ChainHead.class);
    final Bytes8 payloadId = Bytes8.fromHexString("0x0102030405060708");
    final InclusionList inclusionList = dataStructureUtil.randomInclusionList(2);
    final List<Bytes> transactions =
        inclusionList.getTransactions().stream()
            .map(transaction -> transaction.getBytes())
            .toList();
    final var schema =
        SchemaDefinitionsHeze.required(hezeSpec.getGenesisSchemaDefinitions())
            .getInclusionListSchema();
    final List<InclusionListEntry> entries =
        overlappingLists
            ? List.of(
                createEntry(
                    schema.create(
                        inclusionListSlot,
                        UInt64.ZERO,
                        parentRoot,
                        List.of(
                            schema.getTransactionSchema().fromBytes(transactions.getFirst()),
                            schema.getTransactionSchema().fromBytes(transactions.getFirst())))),
                createEntry(
                    schema.create(
                        inclusionListSlot,
                        UInt64.ONE,
                        parentRoot,
                        List.of(
                            schema.getTransactionSchema().fromBytes(transactions.getFirst()),
                            schema.getTransactionSchema().fromBytes(transactions.get(1))))))
            : List.of(createEntry(inclusionList));
    final ExecutionPayloadContext executionPayloadContext =
        new ExecutionPayloadContext(
            payloadId, mock(ForkChoiceState.class), mock(PayloadBuildingAttributes.class));

    when(combinedChainDataClient.getStateAtSlotExact(inclusionListSlot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(spec.processSlots(state, proposerSlot)).thenReturn(proposerState);
    when(proposersDataManager.isProposerForSlot(proposerSlot, proposerState)).thenReturn(true);
    when(inclusionListStore.getInclusionLists(inclusionListSlot)).thenReturn(Optional.of(entries));
    when(spec.getBlockRootAtSlot(proposerState, inclusionListSlot)).thenReturn(parentRoot);
    when(combinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
    when(chainHead.getRoot()).thenReturn(parentRoot);
    when(chainHead.getForkChoiceNode()).thenReturn(parentForkChoiceNode);
    when(forkChoiceNotifier.preparePayloadAttributes(
            parentForkChoiceNode, proposerSlot, transactions))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    assertThatSafeFuture(
            inclusionListPayloadAttributesUpdater.onInclusionListDue(inclusionListSlot))
        .isCompletedWithOptionalContaining(payloadId);

    verify(forkChoiceNotifier)
        .preparePayloadAttributes(parentForkChoiceNode, proposerSlot, transactions);
  }

  private InclusionListEntry createEntry(final InclusionList inclusionList) {
    return new InclusionListEntry(
        SchemaDefinitionsHeze.required(hezeSpec.getGenesisSchemaDefinitions())
            .getSignedInclusionListSchema()
            .create(inclusionList, BLSSignature.empty()),
        true);
  }
}
