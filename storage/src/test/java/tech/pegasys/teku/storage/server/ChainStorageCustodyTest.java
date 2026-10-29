/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.archive.BlobSidecarsArchiver;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreDeserializationException;

class ChainStorageCustodyTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final Database database = mock(Database.class);

  @SuppressWarnings("unchecked")
  private final Consumer<UInt64> listener = mock(Consumer.class);

  private final ChainStorage storage =
      ChainStorage.create(
          database, spec, StateStorageMode.PRUNE, 10, BlobSidecarsArchiver.NOOP, listener);
  private final UInt64 slot = UInt64.valueOf(100);
  private final Bytes32 root = Bytes32.ZERO;
  private final int columns = spec.getNumberOfDataColumns().orElseThrow();
  private final List<DataColumnSlotAndIdentifier> firstHalf =
      IntStream.range(0, columns / 2)
          .mapToObj(index -> new DataColumnSlotAndIdentifier(slot, root, UInt64.valueOf(index)))
          .toList();

  @BeforeEach
  void setUp() {
    when(database.streamDataColumnIdentifiers(slot)).thenAnswer(__ -> firstHalf.stream());
    when(database.getSlotForFinalizedBlockRoot(root)).thenReturn(Optional.of(slot));
    when(database.getDataColumnSidecarsProofs(slot))
        .thenReturn(
            Optional.of(
                IntStream.range(0, columns / 2).mapToObj(__ -> List.<KZGProof>of()).toList()));
  }

  @Test
  void archiveSatisfiesCustodyWithoutChangingPhysicalIdentifiers() {
    assertThat(storage.getCustodyDataColumnIdentifiers(slot))
        .isCompletedWithValueMatching(ids -> ids.size() == columns);
    assertThat(storage.getDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
    verify(database).getSlotForFinalizedBlockRoot(root);
    verify(database, never()).getFinalizedBlockRootBySlot(any());
  }

  @Test
  void firstHalfWithoutProofsDoesNotSatisfyFullCustody() {
    when(database.getDataColumnSidecarsProofs(slot)).thenReturn(Optional.empty());
    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
    verify(database, never()).getFinalizedBlockRootBySlot(slot);
    verify(database, never()).getSlotForFinalizedBlockRoot(any());
  }

  @Test
  void incompleteProofArchiveDoesNotSatisfyFullCustody() {
    when(database.getDataColumnSidecarsProofs(slot)).thenReturn(Optional.of(List.of()));
    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
  }

  @Test
  void malformedProofArchiveReturnsPhysicalIdentifiers() {
    when(database.getDataColumnSidecarsProofs(slot))
        .thenThrow(new KvStoreDeserializationException("malformed proofs"));

    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
    verify(database, never()).getSlotForFinalizedBlockRoot(any());
  }

  @Test
  void proofArchiveReadFailurePropagates() {
    when(database.getDataColumnSidecarsProofs(slot))
        .thenThrow(new IllegalStateException("proof read failed"));

    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedExceptionally();
  }

  @Test
  void rangeQueryContinuesAfterMalformedProofArchive() {
    final UInt64 malformedSlot = slot.decrement();
    final List<DataColumnSlotAndIdentifier> physical = identifiers(malformedSlot, columns / 2);
    when(database.streamDataColumnIdentifiers(malformedSlot, slot))
        .thenReturn(Stream.concat(physical.stream(), firstHalf.stream()));
    when(database.getDataColumnSidecarsProofs(malformedSlot))
        .thenThrow(new KvStoreDeserializationException("malformed proofs"));

    assertThat(storage.getCustodyDataColumnIdentifiers(malformedSlot, slot))
        .isCompletedWithValue(
            Stream.concat(physical.stream(), identifiers(slot, columns).stream()).toList());
  }

  @Test
  void unfinalizedSlotDoesNotUseArchivedProofs() {
    when(database.getSlotForFinalizedBlockRoot(root)).thenReturn(Optional.empty());
    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
  }

  @Test
  void strayFirstHalfDoesNotEstablishCanonicalCustody() {
    when(database.getSlotForFinalizedBlockRoot(root)).thenReturn(Optional.of(slot.increment()));
    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(firstHalf);
  }

  @Test
  void missingFirstHalfDoesNotEstablishFullCustody() {
    final List<DataColumnSlotAndIdentifier> incomplete = firstHalf.subList(1, firstHalf.size());
    when(database.streamDataColumnIdentifiers(slot)).thenAnswer(__ -> incomplete.stream());
    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(incomplete);
    verify(database, never()).getFinalizedBlockRootBySlot(slot);
    verify(database, never()).getDataColumnSidecarsProofs(slot);
    verify(database, never()).getSlotForFinalizedBlockRoot(any());
  }

  @Test
  void unarchivedFullCustodyReturnsPhysicalIdentifiers() {
    final List<DataColumnSlotAndIdentifier> allColumns =
        IntStream.range(0, columns)
            .mapToObj(index -> new DataColumnSlotAndIdentifier(slot, root, UInt64.valueOf(index)))
            .toList();
    when(database.streamDataColumnIdentifiers(slot)).thenAnswer(__ -> allColumns.stream());
    when(database.getDataColumnSidecarsProofs(slot)).thenReturn(Optional.empty());

    assertThat(storage.getCustodyDataColumnIdentifiers(slot)).isCompletedWithValue(allColumns);
    verify(database, never()).getDataColumnSidecarsProofs(slot);
    verify(database, never()).getFinalizedBlockRootBySlot(slot);
    verify(database, never()).getSlotForFinalizedBlockRoot(any());
  }

  @Test
  void competingBlockColumnsDoNotSkipArchiveLookup() {
    final Bytes32 competingRoot = Bytes32.fromHexStringLenient("0x01");
    final List<DataColumnSlotAndIdentifier> mixed = new ArrayList<>(firstHalf);
    IntStream.range(columns / 2, columns)
        .mapToObj(
            index -> new DataColumnSlotAndIdentifier(slot, competingRoot, UInt64.valueOf(index)))
        .forEach(mixed::add);
    when(database.streamDataColumnIdentifiers(slot)).thenAnswer(__ -> mixed.stream());

    assertThat(storage.getCustodyDataColumnIdentifiers(slot))
        .isCompletedWithValueMatching(
            ids -> ids.stream().filter(id -> id.blockRoot().equals(root)).count() == columns);
    verify(database).getDataColumnSidecarsProofs(slot);
  }

  @Test
  void findsCanonicalRootAfterStrayRootUsingIndexOnly() {
    final Bytes32 strayRoot = Bytes32.fromHexStringLenient("0x01");
    final List<DataColumnSlotAndIdentifier> mixed = new ArrayList<>();
    firstHalf.forEach(
        id -> mixed.add(new DataColumnSlotAndIdentifier(slot, strayRoot, id.columnIndex())));
    mixed.addAll(firstHalf);
    when(database.streamDataColumnIdentifiers(slot)).thenAnswer(__ -> mixed.stream());

    assertThat(storage.getCustodyDataColumnIdentifiers(slot))
        .isCompletedWithValueMatching(
            ids -> ids.stream().filter(id -> id.blockRoot().equals(root)).count() == columns);

    verify(database).getSlotForFinalizedBlockRoot(strayRoot);
    verify(database).getSlotForFinalizedBlockRoot(root);
    verify(database, never()).getFinalizedBlockRootBySlot(any());
  }

  @Test
  void rangeQueryScansOnceAndExpandsOnlyArchivedSlots() {
    final UInt64 fullSlot = slot.plus(2);
    final UInt64 partialSlot = slot.plus(4);
    final UInt64 unarchivedSlot = slot.plus(6);
    final UInt64 endSlot = slot.plus(100);
    final List<DataColumnSlotAndIdentifier> full = identifiers(fullSlot, columns);
    final List<DataColumnSlotAndIdentifier> partial = identifiers(partialSlot, columns / 2 - 1);
    final List<DataColumnSlotAndIdentifier> unarchived = identifiers(unarchivedSlot, columns / 2);
    final Runnable onClose = mock(Runnable.class);
    when(database.streamDataColumnIdentifiers(slot, endSlot))
        .thenReturn(
            Stream.of(firstHalf, full, partial, unarchived).flatMap(List::stream).onClose(onClose));
    when(database.getDataColumnSidecarsProofs(unarchivedSlot)).thenReturn(Optional.empty());
    final List<DataColumnSlotAndIdentifier> expected =
        Stream.of(identifiers(slot, columns), full, partial, unarchived)
            .flatMap(List::stream)
            .toList();

    assertThat(storage.getCustodyDataColumnIdentifiers(slot, endSlot))
        .isCompletedWithValue(expected);

    verify(database).streamDataColumnIdentifiers(slot, endSlot);
    verify(database).getDataColumnSidecarsProofs(slot);
    verify(database).getSlotForFinalizedBlockRoot(root);
    verify(database).getDataColumnSidecarsProofs(unarchivedSlot);
    verifyNoMoreInteractions(database);
    verify(onClose).run();
  }

  @Test
  void emptyRangeDoesNotPerformPerSlotLookups() {
    final UInt64 endSlot = slot.plus(1000);
    final Runnable onClose = mock(Runnable.class);
    when(database.streamDataColumnIdentifiers(slot, endSlot))
        .thenReturn(Stream.<DataColumnSlotAndIdentifier>empty().onClose(onClose));

    assertThat(storage.getCustodyDataColumnIdentifiers(slot, endSlot))
        .isCompletedWithValue(List.of());

    verify(database).streamDataColumnIdentifiers(slot, endSlot);
    verifyNoMoreInteractions(database);
    verify(onClose).run();
  }

  @Test
  void rangeQueryClosesStreamOnFailure() {
    final Runnable onClose = mock(Runnable.class);
    when(database.streamDataColumnIdentifiers(slot, slot))
        .thenReturn(firstHalf.stream().onClose(onClose));
    when(database.getDataColumnSidecarsProofs(slot))
        .thenThrow(new IllegalStateException("proof read failed"));

    assertThat(storage.getCustodyDataColumnIdentifiers(slot, slot)).isCompletedExceptionally();

    verify(onClose).run();
  }

  private List<DataColumnSlotAndIdentifier> identifiers(final UInt64 slot, final int count) {
    return IntStream.range(0, count)
        .mapToObj(index -> new DataColumnSlotAndIdentifier(slot, root, UInt64.valueOf(index)))
        .toList();
  }

  @Test
  void notifiesOnlyAfterSuccessfulWrite() {
    final DataColumnSidecar sidecar = mock(DataColumnSidecar.class);
    when(sidecar.getSlot()).thenReturn(slot);
    doThrow(new IllegalStateException("write failed"))
        .doNothing()
        .when(database)
        .addSidecar(sidecar);
    assertThat(storage.onNewSidecar(sidecar)).isCompletedExceptionally();
    verifyNoInteractions(listener);
    assertThat(storage.onNewSidecar(sidecar)).isCompleted();
    verify(listener).accept(slot);
  }
}
