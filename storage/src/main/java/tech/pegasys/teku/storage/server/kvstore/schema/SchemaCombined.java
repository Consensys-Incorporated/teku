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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

public interface SchemaCombined extends Schema {
  // Columns
  KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnHotBlocksByRoot();

  KvStoreColumn<Bytes32, BlockCheckpoints> getColumnHotBlockCheckpointEpochsByRoot();

  // Checkpoint states are no longer stored, keeping only for backwards compatibility.
  KvStoreColumn<Checkpoint, BeaconState> getColumnCheckpointStates();

  KvStoreColumn<UInt64, VoteTracker> getColumnVotes();

  KvStoreColumn<UInt64, DepositsFromBlockEvent> getColumnDepositsFromBlockEvents();

  KvStoreColumn<Bytes32, SlotAndBlockRoot> getColumnStateRootToSlotAndBlockRoot();

  KvStoreColumn<Bytes32, BeaconState> getColumnHotStatesByRoot();

  KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot();

  KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot();

  KvStoreColumn<Bytes32, SignedBlindedExecutionPayloadEnvelope>
      getColumnBlindedExecutionPayloadEnvelopesByRoot();

  KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot();

  KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot();

  KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot();

  KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes> getColumnBlobSidecarBySlotRootBlobIndex();

  KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnNonCanonicalBlobSidecarBySlotRootBlobIndex();

  KvStoreColumn<DataColumnSlotAndIdentifier, Bytes> getColumnSidecarByColumnSlotAndIdentifier();

  KvStoreColumn<DataColumnSlotAndIdentifier, Bytes>
      getColumnNonCanonicalSidecarByColumnSlotAndIdentifier();

  KvStoreColumn<UInt64, List<List<KZGProof>>> getColumnDataColumnSidecarsProofsBySlot();

  KvStoreColumn<UInt64, LightClientUpdate> getBestLightClientUpdatesByPeriod();

  // Variables
  KvStoreVariable<UInt64> getVariableGenesisTime();

  KvStoreVariable<Checkpoint> getVariableJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableBestJustifiedCheckpoint();

  KvStoreVariable<Checkpoint> getVariableFinalizedCheckpoint();

  KvStoreVariable<BeaconState> getVariableLatestFinalizedState();

  KvStoreVariable<MinGenesisTimeBlockEvent> getVariableMinGenesisTimeBlock();

  KvStoreVariable<Checkpoint> getVariableWeakSubjectivityCheckpoint();

  KvStoreVariable<Checkpoint> getVariableAnchorCheckpoint();

  KvStoreVariable<UInt64> getOptimisticTransitionBlockSlot();

  KvStoreVariable<UInt64> getVariableEarliestBlobSidecarSlot();

  KvStoreVariable<UInt64> getVariableEarliestAvailableDataColumnSlot();

  KvStoreVariable<UInt64> getVariableLastDataColumnSidecarPrunedSlot();

  KvStoreVariable<Bytes32> getVariableLatestCanonicalBlockRoot();

  KvStoreVariable<UInt64> getVariableCustodyGroupCount();

  KvStoreVariable<UInt64> getVariableEarliestBlockSlot();

  KvStoreVariable<DepositTreeSnapshot> getVariableFinalizedDepositSnapshot();

  KvStoreVariable<UInt64> getVariableFirstCustodyIncompleteSlot();

  Map<String, KvStoreColumn<?, ?>> getColumnMap();

  Map<String, KvStoreVariable<?>> getVariableMap();

  @Override
  Collection<KvStoreColumn<?, ?>> getAllColumns();

  @Override
  Collection<KvStoreVariable<?>> getAllVariables();

  default SchemaHotAdapter asSchemaHot() {
    return new SchemaHotAdapter(this);
  }
}
