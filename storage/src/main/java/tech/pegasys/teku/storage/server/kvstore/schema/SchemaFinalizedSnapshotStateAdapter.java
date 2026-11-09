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

package tech.pegasys.teku.storage.server.kvstore.schema;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public class SchemaFinalizedSnapshotStateAdapter implements SchemaFinalizedSnapshotState {

  private final SchemaCombinedSnapshotState snapshotDelegate;
  private final SchemaCombined delegate;

  public SchemaFinalizedSnapshotStateAdapter(final SchemaCombinedSnapshotState delegate) {
    this.delegate = delegate;
    this.snapshotDelegate = delegate;
  }

  @Override
  public KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return snapshotDelegate.getColumnFinalizedStatesBySlot();
  }

  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnBlobSidecarBySlotRootBlobIndex() {
    return delegate.getColumnBlobSidecarBySlotRootBlobIndex();
  }

  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex() {
    return delegate.getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex();
  }

  public KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      getColumnSidecarByColumnSlotAndIdentifier() {
    return delegate.getColumnSidecarByColumnSlotAndIdentifier();
  }

  public KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      getColumnNonCanonicalSidecarByColumnSlotAndIdentifier() {
    return delegate.getColumnNonCanonicalSidecarByColumnSlotAndIdentifier();
  }

  public KvStoreColumn<UInt64, List<List<KZGProof>>> getColumnDataColumnSidecarsProofsBySlot() {
    return delegate.getColumnDataColumnSidecarsProofsBySlot();
  }

  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return ImmutableMap.<String, KvStoreColumn<?, ?>>builder()
        .put("SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot())
        .put("FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot())
        .put(
            "BLINDED_EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT",
            getColumnBlindedExecutionPayloadEnvelopesByRoot())
        .put("FINALIZED_STATES_BY_SLOT", getColumnFinalizedStatesBySlot())
        .put("SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot())
        .put("NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot())
        .put("NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot())
        .put(
            "BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX",
            getColumnBlobSidecarBySlotRootBlobIndex())
        .put(
            "NON_CANONICAL_BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX",
            getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex())
        .put("SIDECAR_BY_COLUMN_SLOT_AND_IDENTIFIER", getColumnSidecarByColumnSlotAndIdentifier())
        .put(
            "NON_CANONICAL_SIDECAR_BY_COLUMN_SLOT_AND_IDENTIFIER",
            getColumnNonCanonicalSidecarByColumnSlotAndIdentifier())
        .put("DATA_COLUMN_SIDECARS_PROOFS_BY_SLOT", getColumnDataColumnSidecarsProofsBySlot())
        .build();
  }

  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  public Collection<Bytes> getDeletedVariableIds() {
    return snapshotDelegate.getDeletedVariableIds();
  }

  public Collection<Bytes> getDeletedColumnIds() {
    return snapshotDelegate.getDeletedColumnIds();
  }

  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return delegate.getColumnSlotsByFinalizedRoot();
  }

  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return delegate.getColumnFinalizedBlocksBySlot();
  }

  public KvStoreColumn<Bytes32, SignedBlindedExecutionPayloadEnvelope>
      getColumnBlindedExecutionPayloadEnvelopesByRoot() {
    return delegate.getColumnBlindedExecutionPayloadEnvelopesByRoot();
  }

  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return delegate.getColumnSlotsByFinalizedStateRoot();
  }

  public KvStoreVariable<Bytes32> getVariableLatestCanonicalBlockRoot() {
    return delegate.getVariableLatestCanonicalBlockRoot();
  }

  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return delegate.getColumnNonCanonicalBlocksByRoot();
  }

  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return delegate.getColumnNonCanonicalRootsBySlot();
  }

  public KvStoreVariable<UInt64> getOptimisticTransitionBlockSlot() {
    return delegate.getOptimisticTransitionBlockSlot();
  }

  public KvStoreVariable<UInt64> getVariableEarliestBlobSidecarSlot() {
    return delegate.getVariableEarliestBlobSidecarSlot();
  }

  public KvStoreVariable<UInt64> getVariableEarliestAvailableDataColumnSlot() {
    return delegate.getVariableEarliestAvailableDataColumnSlot();
  }

  public KvStoreVariable<UInt64> getVariableLastDataColumnSidecarPrunedSlot() {
    return delegate.getVariableLastDataColumnSidecarPrunedSlot();
  }

  public KvStoreVariable<UInt64> getVariableEarliestBlockSlot() {
    return delegate.getVariableEarliestBlockSlot();
  }

  public KvStoreVariable<UInt64> getVariableFirstCustodyIncompleteSlot() {
    return delegate.getVariableFirstCustodyIncompleteSlot();
  }

  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of(
        "OPTIMISTIC_TRANSITION_BLOCK_SLOT",
        getOptimisticTransitionBlockSlot(),
        "EARLIEST_BLOB_SIDECAR_SLOT",
        getVariableEarliestBlobSidecarSlot(),
        "EARLIEST_BLOCK_SLOT_AVAILABLE",
        getVariableEarliestBlockSlot(),
        "FIRST_CUSTODY_INCOMPLETE_SLOT",
        getVariableFirstCustodyIncompleteSlot(),
        "EARLIEST_AVAILABLE_DATA_COLUMN_SLOT",
        getVariableEarliestAvailableDataColumnSlot(),
        "LAST_DATA_COLUMN_SLOT_PRUNED",
        getVariableLastDataColumnSidecarPrunedSlot());
  }
}
