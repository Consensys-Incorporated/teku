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

package tech.pegasys.teku.storage.server.kvstore.schema;

import static tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn.asColumnId;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BLOCK_ROOTS_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.COLUMN_SLOT_AND_IDENTIFIER_KEY_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.DATA_COLUMN_SIDECARS_PROOFS_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public class V6SchemaCombinedTreeState extends V6SchemaCombined implements SchemaCombinedTreeState {

  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedRoot;
  private final KvStoreColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final KvStoreColumn<Bytes32, SignedBlindedExecutionPayloadEnvelope>
      blindedExecutionPayloadEnvelopesByRoot;
  private final KvStoreColumn<Bytes32, SignedBeaconBlock> nonCanonicalBlocksByRoot;
  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedStateRoot;
  private final KvStoreColumn<UInt64, Set<Bytes32>> nonCanonicalBlockRootsBySlot;
  private final KvStoreColumn<UInt64, Bytes32> finalizedStateRootsBySlot;
  private final KvStoreColumn<Bytes32, Bytes> finalizedStateTreeLeavesByRoot;
  private final KvStoreColumn<Bytes32, CompressedBranchInfo> finalizedStateTreeBranchesByRoot;

  private final KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes> blobSidecarBySlotRootBlobIndex;
  private final KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      nonCanonicalBlobSidecarBySlotRootBlobIndex;
  private final KvStoreColumn<DataColumnSlotAndIdentifier, Bytes> sidecarByColumnSlotAndIdentifier;
  private final KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      nonCanonicalSidecarByColumnSlotAndIdentifier;
  private final KvStoreColumn<UInt64, List<List<KZGProof>>> dataColumnSidecarsProofsBySlot;
  private final List<Bytes> deletedColumnIds;

  public V6SchemaCombinedTreeState(final Spec spec) {
    super(spec, V6_FINALIZED_OFFSET);
    slotsByFinalizedRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    slotsByFinalizedStateRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 2, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    nonCanonicalBlockRootsBySlot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 3, UINT64_SERIALIZER, BLOCK_ROOTS_SERIALIZER);
    finalizedStateRootsBySlot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 4, UINT64_SERIALIZER, BYTES32_SERIALIZER);
    finalizedStateTreeLeavesByRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 5, BYTES32_SERIALIZER, BYTES_SERIALIZER);
    finalizedStateTreeBranchesByRoot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 6,
            BYTES32_SERIALIZER,
            COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER);
    finalizedBlocksBySlot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 7,
            UINT64_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));
    nonCanonicalBlocksByRoot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 8,
            BYTES32_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));
    blobSidecarBySlotRootBlobIndex =
        KvStoreColumn.create(
            finalizedOffset + 14,
            SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER,
            BYTES_SERIALIZER);
    nonCanonicalBlobSidecarBySlotRootBlobIndex =
        KvStoreColumn.create(
            finalizedOffset + 15,
            SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER,
            BYTES_SERIALIZER);
    sidecarByColumnSlotAndIdentifier =
        KvStoreColumn.create(
            finalizedOffset + 16, COLUMN_SLOT_AND_IDENTIFIER_KEY_SERIALIZER, BYTES_SERIALIZER);
    nonCanonicalSidecarByColumnSlotAndIdentifier =
        KvStoreColumn.create(
            finalizedOffset + 17, COLUMN_SLOT_AND_IDENTIFIER_KEY_SERIALIZER, BYTES_SERIALIZER);
    dataColumnSidecarsProofsBySlot =
        KvStoreColumn.create(
            finalizedOffset + 19, UINT64_SERIALIZER, DATA_COLUMN_SIDECARS_PROOFS_SERIALIZER);
    blindedExecutionPayloadEnvelopesByRoot =
        KvStoreColumn.create(
            finalizedOffset + 20,
            BYTES32_SERIALIZER,
            KvStoreSerializer.createSignedBlindedExecutionPayloadEnvelopeSerializer(spec));
    deletedColumnIds =
        List.of(
            asColumnId(finalizedOffset + 9),
            asColumnId(finalizedOffset + 10),
            asColumnId(finalizedOffset + 11),
            asColumnId(finalizedOffset + 12),
            asColumnId(finalizedOffset + 13),
            asColumnId(finalizedOffset + 18));
  }

  @Override
  public KvStoreColumn<UInt64, Bytes32> getColumnFinalizedStateRootsBySlot() {
    return finalizedStateRootsBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, Bytes> getColumnFinalizedStateMerkleTreeLeaves() {
    return finalizedStateTreeLeavesByRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, CompressedBranchInfo> getColumnFinalizedStateMerkleTreeBranches() {
    return finalizedStateTreeBranchesByRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return slotsByFinalizedRoot;
  }

  @Override
  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return finalizedBlocksBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBlindedExecutionPayloadEnvelope>
      getColumnBlindedExecutionPayloadEnvelopesByRoot() {
    return blindedExecutionPayloadEnvelopesByRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return slotsByFinalizedStateRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return nonCanonicalBlocksByRoot;
  }

  @Override
  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return nonCanonicalBlockRootsBySlot;
  }

  @Override
  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnBlobSidecarBySlotRootBlobIndex() {
    return blobSidecarBySlotRootBlobIndex;
  }

  @Override
  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex() {
    return nonCanonicalBlobSidecarBySlotRootBlobIndex;
  }

  @Override
  public KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      getColumnSidecarByColumnSlotAndIdentifier() {
    return sidecarByColumnSlotAndIdentifier;
  }

  @Override
  public KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      getColumnNonCanonicalSidecarByColumnSlotAndIdentifier() {
    return nonCanonicalSidecarByColumnSlotAndIdentifier;
  }

  @Override
  public KvStoreColumn<UInt64, List<List<KZGProof>>> getColumnDataColumnSidecarsProofsBySlot() {
    return dataColumnSidecarsProofsBySlot;
  }

  @Override
  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return ImmutableMap.<String, KvStoreVariable<?>>builder()
        .put("GENESIS_TIME", getVariableGenesisTime())
        .put("JUSTIFIED_CHECKPOINT", getVariableJustifiedCheckpoint())
        .put("BEST_JUSTIFIED_CHECKPOINT", getVariableBestJustifiedCheckpoint())
        .put("FINALIZED_CHECKPOINT", getVariableFinalizedCheckpoint())
        .put("LATEST_FINALIZED_STATE", getVariableLatestFinalizedState())
        .put("MIN_GENESIS_TIME_BLOCK", getVariableMinGenesisTimeBlock())
        .put("WEAK_SUBJECTIVITY_CHECKPOINT", getVariableWeakSubjectivityCheckpoint())
        .put("ANCHOR_CHECKPOINT", getVariableAnchorCheckpoint())
        .put("OPTIMISTIC_TRANSITION_BLOCK_SLOT", getOptimisticTransitionBlockSlot())
        .put("FINALIZED_DEPOSIT_SNAPSHOT", getVariableFinalizedDepositSnapshot())
        .put("EARLIEST_BLOB_SIDECAR_SLOT", getVariableEarliestBlobSidecarSlot())
        .put("EARLIEST_BLOCK_SLOT", getVariableEarliestBlockSlot())
        .put("LATEST_CANONICAL_BLOCK_ROOT", getVariableLatestCanonicalBlockRoot())
        .put("CUSTODY_GROUP_COUNT", getVariableCustodyGroupCount())
        .put("FIRST_CUSTODY_INCOMPLETE_SLOT", getVariableFirstCustodyIncompleteSlot())
        .put("EARLIEST_AVAILABLE_DATA_COLUMN_SLOT", getVariableEarliestAvailableDataColumnSlot())
        .put("LAST_DATA_COLUMN_SLOT_PRUNED", getVariableLastDataColumnSidecarPrunedSlot())
        .build();
  }

  @Override
  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return ImmutableMap.<String, KvStoreColumn<?, ?>>builder()
        .put("HOT_BLOCKS_BY_ROOT", getColumnHotBlocksByRoot())
        .put("CHECKPOINT_STATES", getColumnCheckpointStates())
        .put("VOTES", getColumnVotes())
        .put("DEPOSITS_FROM_BLOCK_EVENTS", getColumnDepositsFromBlockEvents())
        .put("STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT", getColumnStateRootToSlotAndBlockRoot())
        .put("HOT_STATES_BY_ROOT", getColumnHotStatesByRoot())
        .put("HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", getColumnHotBlockCheckpointEpochsByRoot())
        .put("SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot())
        .put("FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot())
        .put(
            "BLINDED_EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT",
            getColumnBlindedExecutionPayloadEnvelopesByRoot())
        .put("FINALIZED_STATE_ROOTS_BY_SLOT", getColumnFinalizedStateRootsBySlot())
        .put("FINALIZED_STATE_TREE_LEAVES", getColumnFinalizedStateMerkleTreeLeaves())
        .put("FINALIZED_STATE_TREE_BRANCHES", getColumnFinalizedStateMerkleTreeBranches())
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
        .put("LIGHT_CLIENT_UPDATES_BY_PERIOD", getBestLightClientUpdatesByPeriod())
        .put(
            "LIGHT_CLIENT_UPDATE_SIGNATURE_BLOCK_ROOTS_BY_PERIOD",
            getBestLightClientUpdateSignatureBlockRootsByPeriod())
        .build();
  }

  @Override
  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  @Override
  public Collection<Bytes> getDeletedColumnIds() {
    return deletedColumnIds;
  }
}
