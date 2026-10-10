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

package tech.pegasys.teku.statetransition.inclusionlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.SignedInclusionListValidator;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;

class InclusionListManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create().specProvider(spec).numberOfValidators(16).build();
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final InclusionListStore inclusionListStore =
      new InclusionListStore(StoreConfig.DEFAULT_INCLUSION_LIST_CACHE_SIZE);
  private final SignedInclusionListValidator signedInclusionListValidator =
      mock(SignedInclusionListValidator.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final InclusionListManager inclusionListManager =
      new InclusionListManager(
          signedInclusionListValidator, forkChoice, spec, recentChainData, inclusionListStore);

  @ParameterizedTest
  @CsvSource({"ACCEPT, true", "REJECT, false", "IGNORE, false", "SAVE_FOR_FUTURE, false"})
  void shouldNotifySubscribersOnlyForAcceptedInclusionLists(
      final ValidationResultCode validationResultCode, final boolean shouldNotify) {
    final SignedInclusionList signedInclusionList =
        createSignedInclusionList(UInt64.ONE, UInt64.ONE, dataStructureUtil.randomBytes32());
    final List<SignedInclusionList> receivedInclusionLists = new ArrayList<>();
    inclusionListManager.subscribeToInclusionLists(receivedInclusionLists::add);
    when(signedInclusionListValidator.validate(signedInclusionList))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(validationResultCode, "Test validation result")));
    when(forkChoice.onInclusionList(signedInclusionList)).thenReturn(new SafeFuture<>());

    safeJoin(inclusionListManager.addSignedInclusionList(signedInclusionList, Optional.empty()));

    assertThat(receivedInclusionLists)
        .containsExactlyElementsOf(shouldNotify ? List.of(signedInclusionList) : List.of());
  }

  @Test
  void getInclusionListBits_shouldUsePreviousSlotAndIncludeLateLists() {
    final SignedBlockAndState genesis = storageSystem.chainBuilder().generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    final IntList committee =
        spec.atSlot(UInt64.ZERO)
            .getInclusionListUtil()
            .orElseThrow()
            .getInclusionListCommittee(genesis.getState(), UInt64.ZERO);
    final UInt64 validatorIndex = UInt64.valueOf(committee.getInt(1));
    inclusionListStore.processInclusionList(
        createSignedInclusionList(UInt64.ZERO, validatorIndex, genesis.getRoot()), false);

    assertThat(
            safeJoin(inclusionListManager.getInclusionListBits(UInt64.ONE, genesis.getRoot()))
                .orElseThrow()
                .streamAllSetBits())
        .containsExactly(1, 3, 5, 7, 9, 11, 13, 15);
  }

  @Test
  void getInclusionListBits_shouldReturnEmptyWhenParentStateIsUnavailable() {
    final SignedBlockAndState genesis = storageSystem.chainBuilder().generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    assertThat(
            safeJoin(
                inclusionListManager.getInclusionListBits(
                    UInt64.ONE, dataStructureUtil.randomBytes32())))
        .isEmpty();
  }

  @Test
  void getInclusionLists_shouldMapRequestedBitsToCommitteePositions() {
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState genesis = initializeFromGenesis();
    final IntList committee = inclusionListCommittee(slot, genesis.getRoot());
    // A requested position whose validator index differs from the position itself
    final int position =
        IntStream.range(0, committee.size())
            .filter(i -> committee.getInt(i) != i)
            .findFirst()
            .orElseThrow();
    final UInt64 requestedValidator = UInt64.valueOf(committee.getInt(position));
    final UInt64 otherCommitteeValidator =
        committee
            .intStream()
            .filter(index -> index != requestedValidator.intValue())
            .mapToObj(UInt64::valueOf)
            .findFirst()
            .orElseThrow();

    final SignedInclusionList expected =
        createSignedInclusionList(slot, requestedValidator, genesis.getRoot());
    inclusionListManager.add(expected);
    // Same validator under another dependent root
    inclusionListManager.add(
        createSignedInclusionList(slot, requestedValidator, dataStructureUtil.randomBytes32()));
    // Committee member at a position that wasn't requested
    inclusionListManager.add(
        createSignedInclusionList(slot, otherCommitteeValidator, genesis.getRoot()));
    // Validator whose index equals the requested position
    inclusionListManager.add(
        createSignedInclusionList(slot, UInt64.valueOf(position), genesis.getRoot()));

    assertThat(
            safeJoin(
                inclusionListManager.getInclusionLists(
                    slot, genesis.getRoot(), requestedPositions(slot, position))))
        .containsExactly(expected);
  }

  @Test
  void getInclusionLists_shouldNotReturnListsFromEquivocatingValidator() {
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState genesis = initializeFromGenesis();
    final IntList committee = inclusionListCommittee(slot, genesis.getRoot());
    final UInt64 validatorIndex = UInt64.valueOf(committee.getInt(0));
    inclusionListManager.add(createSignedInclusionList(slot, validatorIndex, genesis.getRoot()));
    inclusionListManager.add(
        createSignedInclusionList(
            slot,
            validatorIndex,
            genesis.getRoot(),
            List.of(dataStructureUtil.randomExecutionPayloadTransaction())));

    assertThat(
            safeJoin(
                inclusionListManager.getInclusionLists(
                    slot, genesis.getRoot(), requestedPositions(slot, 0))))
        .isEmpty();
  }

  @Test
  void getInclusionLists_shouldReturnEmptyWhenDependentRootIsUnknown() {
    final UInt64 slot = UInt64.ONE;
    initializeFromGenesis();
    final Bytes32 unknownRoot = dataStructureUtil.randomBytes32();
    inclusionListManager.add(createSignedInclusionList(slot, UInt64.ZERO, unknownRoot));

    assertThat(
            safeJoin(
                inclusionListManager.getInclusionLists(
                    slot, unknownRoot, requestedPositions(slot, 0))))
        .isEmpty();
  }

  @Test
  void getInclusionLists_shouldIgnorePositionsOutsideCommittee() {
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState genesis = initializeFromGenesis();
    final IntList committee = inclusionListCommittee(slot, genesis.getRoot());
    final SignedInclusionList expected =
        createSignedInclusionList(slot, UInt64.valueOf(committee.getInt(0)), genesis.getRoot());
    inclusionListManager.add(expected);
    final SszBitvector requestedPositions =
        SszBitvectorSchema.create(committee.size() * 2).ofBits(0, committee.size() + 1);

    assertThat(
            safeJoin(
                inclusionListManager.getInclusionLists(
                    slot, genesis.getRoot(), requestedPositions)))
        .containsExactly(expected);
  }

  @Test
  void getInclusionLists_shouldReturnEmptyBeforeHeze() {
    final Spec hezeAtEpochOneSpec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    final InclusionListManager manager =
        new InclusionListManager(
            signedInclusionListValidator,
            forkChoice,
            hezeAtEpochOneSpec,
            recentChainData,
            inclusionListStore);
    final UInt64 preHezeSlot = UInt64.ONE;
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    manager.add(createSignedInclusionList(preHezeSlot, UInt64.ZERO, dependentRoot));

    assertThat(
            safeJoin(
                manager.getInclusionLists(
                    preHezeSlot, dependentRoot, requestedPositions(preHezeSlot, 0))))
        .isEmpty();
  }

  @Test
  void getInclusionLists_shouldNotRetrieveStateWhenNoListsAreHeldForSlotAndRoot() {
    final UInt64 slot = UInt64.ONE;
    final SignedBlockAndState genesis = initializeFromGenesis();
    final RecentChainData spiedRecentChainData = spy(recentChainData);
    final InclusionListManager manager = managerWith(spiedRecentChainData);
    // A list for the slot under another dependent root doesn't count
    manager.add(createSignedInclusionList(slot, UInt64.ZERO, dataStructureUtil.randomBytes32()));

    assertThat(
            safeJoin(
                manager.getInclusionLists(slot, genesis.getRoot(), requestedPositions(slot, 0))))
        .isEmpty();
    // Nothing at all held for this slot
    assertThat(
            safeJoin(
                manager.getInclusionLists(
                    UInt64.ZERO, genesis.getRoot(), requestedPositions(UInt64.ZERO, 0))))
        .isEmpty();
    verify(spiedRecentChainData, never()).retrieveBlockState(any(SlotAndBlockRoot.class));
  }

  @Test
  void getInclusionLists_shouldNotRetrieveStateForSlotsBeyondNextSlot() {
    final SignedBlockAndState genesis = initializeFromGenesis();
    final UInt64 farFutureSlot = UInt64.valueOf(1_000);
    final RecentChainData spiedRecentChainData = spy(recentChainData);
    final InclusionListManager manager = managerWith(spiedRecentChainData);
    manager.add(createSignedInclusionList(farFutureSlot, UInt64.ZERO, genesis.getRoot()));

    assertThat(
            safeJoin(
                manager.getInclusionLists(
                    farFutureSlot, genesis.getRoot(), requestedPositions(farFutureSlot, 0))))
        .isEmpty();
    verify(spiedRecentChainData, never()).retrieveBlockState(any(SlotAndBlockRoot.class));
  }

  @Test
  void getInclusionLists_shouldReturnEmptyWhenDependentBlockIsAfterSlot() {
    final UInt64 slot = UInt64.ONE;
    initializeFromGenesis();
    final Bytes32 laterBlockRoot = dataStructureUtil.randomBytes32();
    final RecentChainData spiedRecentChainData = spy(recentChainData);
    doReturn(Optional.of(UInt64.valueOf(2)))
        .when(spiedRecentChainData)
        .getSlotForBlockRoot(laterBlockRoot);
    final InclusionListManager manager = managerWith(spiedRecentChainData);
    manager.add(createSignedInclusionList(slot, UInt64.ZERO, laterBlockRoot));

    assertThat(
            safeJoin(manager.getInclusionLists(slot, laterBlockRoot, requestedPositions(slot, 0))))
        .isEmpty();
    verify(spiedRecentChainData, never()).retrieveBlockState(any(SlotAndBlockRoot.class));
  }

  private InclusionListManager managerWith(final RecentChainData chainData) {
    return new InclusionListManager(
        signedInclusionListValidator, forkChoice, spec, chainData, inclusionListStore);
  }

  private SignedBlockAndState initializeFromGenesis() {
    final SignedBlockAndState genesis = storageSystem.chainBuilder().generateGenesis();
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    return genesis;
  }

  private IntList inclusionListCommittee(final UInt64 slot, final Bytes32 dependentRoot) {
    final BeaconState state =
        safeJoin(recentChainData.retrieveBlockState(new SlotAndBlockRoot(slot, dependentRoot)))
            .orElseThrow();
    return spec.atSlot(slot)
        .getInclusionListUtil()
        .orElseThrow()
        .getInclusionListCommittee(state, slot);
  }

  private SszBitvector requestedPositions(final UInt64 slot, final int... positions) {
    final int committeeSize =
        SpecConfigHeze.required(spec.atSlot(slot).getConfig()).getInclusionListCommitteeSize();
    return SszBitvectorSchema.create(committeeSize).ofBits(positions);
  }

  private SignedInclusionList createSignedInclusionList(
      final UInt64 slot, final UInt64 validatorIndex, final Bytes32 dependentRoot) {
    return createSignedInclusionList(slot, validatorIndex, dependentRoot, List.of());
  }

  private SignedInclusionList createSignedInclusionList(
      final UInt64 slot,
      final UInt64 validatorIndex,
      final Bytes32 dependentRoot,
      final List<Transaction> transactions) {
    final SchemaDefinitionsHeze schemaDefinitions =
        SchemaDefinitionsHeze.required(spec.atSlot(slot).getSchemaDefinitions());
    final InclusionList inclusionList =
        schemaDefinitions
            .getInclusionListSchema()
            .create(slot, validatorIndex, dependentRoot, transactions);
    return schemaDefinitions
        .getSignedInclusionListSchema()
        .create(inclusionList, dataStructureUtil.randomSignature());
  }
}
