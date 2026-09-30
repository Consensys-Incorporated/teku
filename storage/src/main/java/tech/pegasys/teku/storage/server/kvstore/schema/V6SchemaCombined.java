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

import static tech.pegasys.teku.storage.server.kvstore.schema.KvStoreVariable.asVariableId;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.CHECKPOINT_EPOCHS_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.CHECKPOINT_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.DEPOSIT_SNAPSHOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.SLOT_AND_BLOCK_ROOT_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public abstract class V6SchemaCombined implements SchemaCombined {

  public static final int V4_FINALIZED_OFFSET = 0;
  public static final int V6_FINALIZED_OFFSET = 128;

  // column ids should be distinct across different DAOs to make possible using
  // schemes both for a single and separated DBs
  protected final int finalizedOffset;

  private final KvStoreColumn<Bytes32, SignedBeaconBlock> hotBlocksByRoot;

  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  private final KvStoreColumn<Checkpoint, BeaconState> checkpointStates;
  private final KvStoreColumn<UInt64, VoteTracker> votes;
  private static final KvStoreColumn<UInt64, DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENTS =
      KvStoreColumn.create(4, UINT64_SERIALIZER, DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER);
  private static final KvStoreColumn<Bytes32, SlotAndBlockRoot> STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT =
      KvStoreColumn.create(5, BYTES32_SERIALIZER, SLOT_AND_BLOCK_ROOT_SERIALIZER);
  private final KvStoreColumn<Bytes32, BeaconState> hotStatesByRoot;
  private static final KvStoreColumn<Bytes32, BlockCheckpoints>
      HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT =
          KvStoreColumn.create(7, BYTES32_SERIALIZER, CHECKPOINT_EPOCHS_SERIALIZER);
  private final KvStoreColumn<UInt64, LightClientUpdate> lightClientUpdatesByPeriod;
  private final KvStoreColumn<UInt64, Bytes32> lightClientUpdateSignatureBlockRootsByPeriod;

  // Variables
  private static final KvStoreVariable<UInt64> GENESIS_TIME =
      KvStoreVariable.create(1, UINT64_SERIALIZER);
  private static final KvStoreVariable<Checkpoint> JUSTIFIED_CHECKPOINT =
      KvStoreVariable.create(2, CHECKPOINT_SERIALIZER);
  private static final KvStoreVariable<Checkpoint> BEST_JUSTIFIED_CHECKPOINT =
      KvStoreVariable.create(3, CHECKPOINT_SERIALIZER);
  private static final KvStoreVariable<Checkpoint> FINALIZED_CHECKPOINT =
      KvStoreVariable.create(4, CHECKPOINT_SERIALIZER);
  private final KvStoreVariable<BeaconState> latestFinalizedState;
  private static final KvStoreVariable<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK =
      KvStoreVariable.create(6, MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER);
  // 7 was the protoarray snapshot variable but is no longer used.
  private static final KvStoreVariable<Checkpoint> WEAK_SUBJECTIVITY_CHECKPOINT =
      KvStoreVariable.create(8, CHECKPOINT_SERIALIZER);
  private static final KvStoreVariable<Checkpoint> ANCHOR_CHECKPOINT =
      KvStoreVariable.create(9, CHECKPOINT_SERIALIZER);
  private static final KvStoreVariable<DepositTreeSnapshot> FINALIZED_DEPOSIT_SNAPSHOT =
      KvStoreVariable.create(10, DEPOSIT_SNAPSHOT_SERIALIZER);
  private static final KvStoreVariable<Bytes32> LATEST_CANONICAL_BLOCK_ROOT =
      KvStoreVariable.create(11, BYTES32_SERIALIZER);
  private static final KvStoreVariable<UInt64> CUSTODY_GROUP_COUNT =
      KvStoreVariable.create(12, UINT64_SERIALIZER);

  private final KvStoreVariable<UInt64> optimisticTransitionBlockSlot;
  private final KvStoreVariable<UInt64> earliestBlobSidecarSlot;
  private final KvStoreVariable<UInt64> earliestBlockSlot;
  private final KvStoreVariable<UInt64> firstCustodyIncompleteSlot;
  private final KvStoreVariable<UInt64> earliestAvailableDataColumnSlot;
  private final KvStoreVariable<UInt64> lastDataColumnSidecarPrunedSlot;

  private final List<Bytes> deletedVariableIds;

  protected V6SchemaCombined(final Spec spec, final int finalizedOffset) {
    this.finalizedOffset = finalizedOffset;
    final KvStoreSerializer<SignedBeaconBlock> signedBlockSerializer =
        KvStoreSerializer.createSignedBlockSerializer(spec);
    hotBlocksByRoot = KvStoreColumn.create(1, BYTES32_SERIALIZER, signedBlockSerializer);
    final KvStoreSerializer<BeaconState> stateSerializer =
        KvStoreSerializer.createStateSerializer(spec);
    checkpointStates = KvStoreColumn.create(2, CHECKPOINT_SERIALIZER, stateSerializer);
    hotStatesByRoot = KvStoreColumn.create(6, BYTES32_SERIALIZER, stateSerializer);
    latestFinalizedState = KvStoreVariable.create(5, stateSerializer);
    final KvStoreSerializer<LightClientUpdate> lightClientUpdateSerializer =
        KvStoreSerializer.createLightClientUpdateSerializer(spec);
    lightClientUpdatesByPeriod =
        KvStoreColumn.create(8, UINT64_SERIALIZER, lightClientUpdateSerializer);
    lightClientUpdateSignatureBlockRootsByPeriod =
        KvStoreColumn.create(9, UINT64_SERIALIZER, BYTES32_SERIALIZER);

    votes =
        KvStoreColumn.create(
            3, UINT64_SERIALIZER, KvStoreSerializer.createVoteTrackerSerializer(spec));

    optimisticTransitionBlockSlot = KvStoreVariable.create(finalizedOffset + 1, UINT64_SERIALIZER);
    earliestBlobSidecarSlot = KvStoreVariable.create(finalizedOffset + 2, UINT64_SERIALIZER);
    earliestBlockSlot = KvStoreVariable.create(finalizedOffset + 3, UINT64_SERIALIZER);
    firstCustodyIncompleteSlot = KvStoreVariable.create(finalizedOffset + 4, UINT64_SERIALIZER);
    // finalizedOffset + 5 has been deleted
    earliestAvailableDataColumnSlot =
        KvStoreVariable.create(finalizedOffset + 6, UINT64_SERIALIZER);
    lastDataColumnSidecarPrunedSlot =
        KvStoreVariable.create(finalizedOffset + 7, UINT64_SERIALIZER);

    deletedVariableIds = List.of(asVariableId(finalizedOffset + 5));
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot() {
    return hotBlocksByRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, BlockCheckpoints> getColumnHotBlockCheckpointEpochsByRoot() {
    return HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT;
  }

  @Override
  public KvStoreColumn<Checkpoint, BeaconState> getColumnCheckpointStates() {
    return checkpointStates;
  }

  @Override
  public KvStoreColumn<UInt64, VoteTracker> getColumnVotes() {
    return votes;
  }

  @Override
  public KvStoreColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents() {
    return DEPOSITS_FROM_BLOCK_EVENTS;
  }

  @Override
  public KvStoreColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot() {
    return STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT;
  }

  @Override
  public KvStoreColumn<UInt64, LightClientUpdate> getBestLightClientUpdatesByPeriod() {
    return lightClientUpdatesByPeriod;
  }

  @Override
  public KvStoreColumn<UInt64, Bytes32> getBestLightClientUpdateSignatureBlockRootsByPeriod() {
    return lightClientUpdateSignatureBlockRootsByPeriod;
  }

  @Override
  public KvStoreColumn<Bytes32, BeaconState> getColumnHotStatesByRoot() {
    return hotStatesByRoot;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableGenesisTime() {
    return GENESIS_TIME;
  }

  @Override
  public KvStoreVariable<Checkpoint> getVariableJustifiedCheckpoint() {
    return JUSTIFIED_CHECKPOINT;
  }

  @Override
  public KvStoreVariable<Checkpoint> getVariableBestJustifiedCheckpoint() {
    return BEST_JUSTIFIED_CHECKPOINT;
  }

  @Override
  public KvStoreVariable<Checkpoint> getVariableFinalizedCheckpoint() {
    return FINALIZED_CHECKPOINT;
  }

  @Override
  public KvStoreVariable<BeaconState> getVariableLatestFinalizedState() {
    return latestFinalizedState;
  }

  @Override
  public KvStoreVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock() {
    return MIN_GENESIS_TIME_BLOCK;
  }

  @Override
  public KvStoreVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint() {
    return WEAK_SUBJECTIVITY_CHECKPOINT;
  }

  @Override
  public KvStoreVariable<Checkpoint> getVariableAnchorCheckpoint() {
    return ANCHOR_CHECKPOINT;
  }

  @Override
  public KvStoreVariable<UInt64> getOptimisticTransitionBlockSlot() {
    return optimisticTransitionBlockSlot;
  }

  @Override
  public KvStoreVariable<DepositTreeSnapshot> getVariableFinalizedDepositSnapshot() {
    return FINALIZED_DEPOSIT_SNAPSHOT;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableEarliestBlobSidecarSlot() {
    return earliestBlobSidecarSlot;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableEarliestAvailableDataColumnSlot() {
    return earliestAvailableDataColumnSlot;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableLastDataColumnSidecarPrunedSlot() {
    return lastDataColumnSidecarPrunedSlot;
  }

  @Override
  public KvStoreVariable<Bytes32> getVariableLatestCanonicalBlockRoot() {
    return LATEST_CANONICAL_BLOCK_ROOT;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableCustodyGroupCount() {
    return CUSTODY_GROUP_COUNT;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableEarliestBlockSlot() {
    return earliestBlockSlot;
  }

  @Override
  public KvStoreVariable<UInt64> getVariableFirstCustodyIncompleteSlot() {
    return firstCustodyIncompleteSlot;
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
        .put("SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot())
        .put("NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot())
        .put("NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot())
        .put(
            "BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX",
            getColumnBlobSidecarBySlotRootBlobIndex())
        .put("LIGHT_CLIENT_UPDATES_BY_PERIOD", getBestLightClientUpdatesByPeriod())
        .put(
            "LIGHT_CLIENT_UPDATE_SIGNATURE_BLOCK_ROOTS_BY_PERIOD",
            getBestLightClientUpdateSignatureBlockRootsByPeriod())
        .build();
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
        .put("EARLIEST_BLOCK_SLOT_AVAILABLE", getVariableEarliestBlockSlot())
        .put("LATEST_CANONICAL_BLOCK_ROOT", getVariableLatestCanonicalBlockRoot())
        .put("CUSTODY_GROUP_COUNT", getVariableCustodyGroupCount())
        .put("FIRST_CUSTODY_INCOMPLETE_SLOT", getVariableFirstCustodyIncompleteSlot())
        .put("EARLIEST_AVAILABLE_DATA_COLUMN_SLOT", getVariableEarliestAvailableDataColumnSlot())
        .put("LAST_DATA_COLUMN_SLOT_PRUNED", getVariableLastDataColumnSidecarPrunedSlot())
        .build();
  }

  @Override
  public Collection<Bytes> getDeletedVariableIds() {
    return deletedVariableIds;
  }
}
