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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionListSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

class InclusionListStoreTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);
  private static final UInt64 OTHER_SLOT = UInt64.valueOf(43);
  private static final UInt64 THIRD_SLOT = UInt64.valueOf(44);
  private static final UInt64 VALIDATOR_INDEX = UInt64.valueOf(7);
  private static final UInt64 OTHER_VALIDATOR_INDEX = UInt64.valueOf(8);
  private static final Bytes32 DEPENDENT_ROOT_1 = Bytes32.fromHexStringLenient("0x01");
  private static final Bytes32 DEPENDENT_ROOT_2 = Bytes32.fromHexStringLenient("0x02");
  private static final Bytes32 DEPENDENT_ROOT_3 = Bytes32.fromHexStringLenient("0x03");

  private final SchemaDefinitionsHeze schemaDefinitionsHeze =
      SchemaDefinitionsHeze.required(
          TestSpecFactory.createMinimalHeze().getGenesisSchemaDefinitions());
  private final InclusionListSchema inclusionListSchema =
      schemaDefinitionsHeze.getInclusionListSchema();
  private final SignedInclusionListSchema signedInclusionListSchema =
      schemaDefinitionsHeze.getSignedInclusionListSchema();

  @Test
  void shouldDeduplicateTransactionsAndFilterUntimelyLists() {
    final InclusionListStore store = new InclusionListStore(4);
    final Transaction firstTransaction =
        inclusionListSchema.getTransactionSchema().fromBytes(Bytes.of(1));
    final Transaction secondTransaction =
        inclusionListSchema.getTransactionSchema().fromBytes(Bytes.of(2));
    store.processInclusionList(
        createSignedInclusionList(
            SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1, List.of(firstTransaction, firstTransaction)),
        true);
    store.processInclusionList(
        createSignedInclusionList(
            SLOT,
            OTHER_VALIDATOR_INDEX,
            DEPENDENT_ROOT_1,
            List.of(firstTransaction, secondTransaction)),
        false);
    final SlotAndBlockRoot key = new SlotAndBlockRoot(SLOT, DEPENDENT_ROOT_1);

    assertThat(store.getInclusionListTransactions(key, false))
        .containsExactly(Bytes.of(1), Bytes.of(2));
    assertThat(store.getInclusionListTransactions(key, true)).containsExactly(Bytes.of(1));
  }

  @Test
  void shouldComputeBitsByCommitteePositionIncludingRepeatedValidators() {
    final InclusionListStore store = new InclusionListStore(4);
    store.processInclusionList(
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1), true);
    store.processInclusionList(
        createSignedInclusionList(SLOT, OTHER_VALIDATOR_INDEX, DEPENDENT_ROOT_1), false);
    final IntList committee = IntList.of(8, 9, 7, 8);
    final SlotAndBlockRoot key = new SlotAndBlockRoot(SLOT, DEPENDENT_ROOT_1);

    assertThat(store.getInclusionListBits(committee, key, false).streamAllSetBits().toArray())
        .containsExactly(0, 2, 3);
    assertThat(store.getInclusionListBits(committee, key, true).streamAllSetBits().toArray())
        .containsExactly(2);
  }

  @Test
  void shouldExcludeEquivocatorsAndIsolateBitsBySlotAndRoot() {
    final InclusionListStore store = new InclusionListStore(4);
    store.processInclusionList(
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1), true);
    store.processInclusionList(
        createSignedInclusionList(
            SLOT,
            VALIDATOR_INDEX,
            DEPENDENT_ROOT_1,
            List.of(inclusionListSchema.getTransactionSchema().fromBytes(Bytes.of(1)))),
        true);
    store.processInclusionList(
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_2), true);
    store.processInclusionList(
        createSignedInclusionList(OTHER_SLOT, OTHER_VALIDATOR_INDEX, DEPENDENT_ROOT_1), true);
    final IntList committee = IntList.of(7, 8);

    assertThat(
            store
                .getInclusionListBits(
                    committee, new SlotAndBlockRoot(SLOT, DEPENDENT_ROOT_1), false)
                .streamAllSetBits()
                .toArray())
        .isEmpty();
    assertThat(
            store
                .getInclusionListBits(
                    committee, new SlotAndBlockRoot(SLOT, DEPENDENT_ROOT_2), false)
                .streamAllSetBits()
                .toArray())
        .containsExactly(0);
    assertThat(
            store
                .getInclusionListBits(
                    committee, new SlotAndBlockRoot(OTHER_SLOT, DEPENDENT_ROOT_1), false)
                .streamAllSetBits()
                .toArray())
        .containsExactly(1);
    assertThat(
            store
                .getInclusionListBits(
                    committee, new SlotAndBlockRoot(THIRD_SLOT, DEPENDENT_ROOT_1), false)
                .streamAllSetBits()
                .toArray())
        .isEmpty();
  }

  @Test
  void shouldStoreAndRetrieveSignedEntriesByKeyAndSlot() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList1 =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);
    final SignedInclusionList inclusionList2 =
        createSignedInclusionList(SLOT, OTHER_VALIDATOR_INDEX, DEPENDENT_ROOT_2);

    inclusionListStore.processInclusionList(inclusionList1, true);
    inclusionListStore.processInclusionList(inclusionList2, false);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(inclusionList1, true)));
    assertThat(inclusionListStore.getInclusionLists(SLOT))
        .hasValueSatisfying(
            entries ->
                assertThat(entries).containsExactly(new InclusionListEntry(inclusionList1, true)));
    assertThat(inclusionListStore.getInclusionLists(OTHER_SLOT))
        .hasValueSatisfying(entries -> assertThat(entries).isEmpty());
  }

  @Test
  void shouldReturnStableSnapshotForExactKeyLookup() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList1 =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);
    final SignedInclusionList inclusionList2 =
        createSignedInclusionList(SLOT, OTHER_VALIDATOR_INDEX, DEPENDENT_ROOT_1);

    inclusionListStore.processInclusionList(inclusionList1, true);
    final Map<UInt64, InclusionListEntry> snapshot =
        inclusionListStore.getInclusionLists(keyFor(inclusionList1)).orElseThrow();

    inclusionListStore.processInclusionList(inclusionList2, false);

    assertThat(snapshot)
        .containsOnly(Map.entry(VALIDATOR_INDEX, new InclusionListEntry(inclusionList1, true)));
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1)))
        .hasValue(
            Map.of(
                VALIDATOR_INDEX,
                new InclusionListEntry(inclusionList1, true),
                OTHER_VALIDATOR_INDEX,
                new InclusionListEntry(inclusionList2, false)));
  }

  @Test
  void shouldIgnoreDuplicateAndRetainFirstTimeliness() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);

    inclusionListStore.processInclusionList(inclusionList, true);
    inclusionListStore.processInclusionList(inclusionList, false);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(inclusionList, true)));
    assertThat(
            inclusionListStore.isInclusionListEquivocator(keyFor(inclusionList), VALIDATOR_INDEX))
        .isFalse();
  }

  @Test
  void shouldUpgradeTimelinessWhenDuplicateArrivesTimely() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);

    inclusionListStore.processInclusionList(inclusionList, false);
    inclusionListStore.processInclusionList(inclusionList, true);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(inclusionList, true)));
    assertThat(
            inclusionListStore.isInclusionListEquivocator(keyFor(inclusionList), VALIDATOR_INDEX))
        .isFalse();
  }

  @Test
  void shouldMarkConflictingListAsEquivocationAndRetainFirstEntry() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);
    final Transaction transaction =
        inclusionListSchema.getTransactionSchema().fromBytes(Bytes.of(1));
    final SignedInclusionList conflictingInclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1, List.of(transaction));

    inclusionListStore.processInclusionList(inclusionList, true);
    inclusionListStore.processInclusionList(conflictingInclusionList, false);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(inclusionList, true)));
    assertThat(
            inclusionListStore.isInclusionListEquivocator(keyFor(inclusionList), VALIDATOR_INDEX))
        .isTrue();
    assertThat(inclusionListStore.getInclusionLists(SLOT))
        .hasValueSatisfying(entries -> assertThat(entries).isEmpty());
  }

  @Test
  void shouldIsolateEntriesAndEquivocatorsByDependentRoot() {
    final InclusionListStore inclusionListStore = new InclusionListStore(4);
    final SignedInclusionList inclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);
    final SignedInclusionList otherRootInclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_2);
    final Transaction transaction =
        inclusionListSchema.getTransactionSchema().fromBytes(Bytes.of(1));
    final SignedInclusionList conflictingInclusionList =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1, List.of(transaction));

    inclusionListStore.processInclusionList(inclusionList, true);
    inclusionListStore.processInclusionList(otherRootInclusionList, true);
    inclusionListStore.processInclusionList(conflictingInclusionList, true);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(inclusionList, true)));
    assertThat(inclusionListStore.getInclusionLists(keyFor(otherRootInclusionList)))
        .hasValue(Map.of(VALIDATOR_INDEX, new InclusionListEntry(otherRootInclusionList, true)));
    assertThat(
            inclusionListStore.isInclusionListEquivocator(
                keyFor(otherRootInclusionList), VALIDATOR_INDEX))
        .isFalse();
    assertThat(inclusionListStore.getInclusionLists(SLOT))
        .hasValueSatisfying(
            entries ->
                assertThat(entries)
                    .containsExactly(new InclusionListEntry(otherRootInclusionList, true)));
  }

  @Test
  void shouldEvictOldestKeyWhenCacheSizeIsExceeded() {
    final InclusionListStore inclusionListStore = new InclusionListStore(2);
    final SignedInclusionList inclusionList1 =
        createSignedInclusionList(SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_1);
    final SignedInclusionList inclusionList2 =
        createSignedInclusionList(OTHER_SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_2);
    final SignedInclusionList inclusionList3 =
        createSignedInclusionList(THIRD_SLOT, VALIDATOR_INDEX, DEPENDENT_ROOT_3);

    inclusionListStore.processInclusionList(inclusionList1, true);
    inclusionListStore.processInclusionList(inclusionList2, true);
    inclusionListStore.processInclusionList(inclusionList3, true);

    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList1))).isEmpty();
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList2))).isPresent();
    assertThat(inclusionListStore.getInclusionLists(keyFor(inclusionList3))).isPresent();
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
    final InclusionList inclusionList =
        inclusionListSchema.create(slot, validatorIndex, dependentRoot, transactions);
    return signedInclusionListSchema.create(inclusionList, BLSSignature.empty());
  }

  private SlotAndBlockRoot keyFor(final SignedInclusionList signedInclusionList) {
    final InclusionList inclusionList = signedInclusionList.getMessage();
    return new SlotAndBlockRoot(inclusionList.getSlot(), inclusionList.getDependentRoot());
  }
}
