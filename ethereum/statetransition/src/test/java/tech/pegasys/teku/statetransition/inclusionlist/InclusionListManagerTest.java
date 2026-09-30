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
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.SignedInclusionListValidator;
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
  private final InclusionListManager inclusionListManager =
      new InclusionListManager(
          mock(SignedInclusionListValidator.class),
          mock(ForkChoice.class),
          spec,
          recentChainData,
          inclusionListStore);

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
  void shouldReturnOnlyListsMatchingDependentRootAndRequestedIndex() {
    final UInt64 slot = UInt64.ONE;
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    final SignedInclusionList expected = createSignedInclusionList(slot, UInt64.ONE, dependentRoot);
    inclusionListManager.add(expected);
    inclusionListManager.add(
        createSignedInclusionList(slot, UInt64.ONE, dataStructureUtil.randomBytes32()));
    inclusionListManager.add(createSignedInclusionList(slot, UInt64.valueOf(2), dependentRoot));
    final int committeeSize =
        SpecConfigHeze.required(spec.atSlot(slot).getConfig()).getInclusionListCommitteeSize();
    final SszBitvector requestedIndices = SszBitvectorSchema.create(committeeSize).ofBits(1);

    assertThat(inclusionListManager.getInclusionLists(slot, dependentRoot, requestedIndices))
        .containsExactly(expected);
  }

  @Test
  void shouldNotReturnListsFromEquivocatingValidator() {
    final UInt64 slot = UInt64.ONE;
    final UInt64 validatorIndex = UInt64.ONE;
    final Bytes32 dependentRoot = dataStructureUtil.randomBytes32();
    inclusionListManager.add(createSignedInclusionList(slot, validatorIndex, dependentRoot));
    inclusionListManager.add(
        createSignedInclusionList(
            slot,
            validatorIndex,
            dependentRoot,
            List.of(dataStructureUtil.randomExecutionPayloadTransaction())));
    final int committeeSize =
        SpecConfigHeze.required(spec.atSlot(slot).getConfig()).getInclusionListCommitteeSize();
    final SszBitvector requestedIndices = SszBitvectorSchema.create(committeeSize).ofBits(1);

    assertThat(inclusionListManager.getInclusionLists(slot, dependentRoot, requestedIndices))
        .isEmpty();
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
