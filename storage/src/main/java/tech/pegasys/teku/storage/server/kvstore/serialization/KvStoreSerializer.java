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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
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

public interface KvStoreSerializer<T> {
  KvStoreSerializer<UInt64> UINT64_SERIALIZER = new UInt64Serializer();
  KvStoreSerializer<Bytes> BYTES_SERIALIZER = new BytesSerializer<>(Bytes::wrap);
  KvStoreSerializer<Bytes32> BYTES32_SERIALIZER = new BytesSerializer<>(Bytes32::wrap);
  KvStoreSerializer<Checkpoint> CHECKPOINT_SERIALIZER = new SszSerializer<>(Checkpoint.SSZ_SCHEMA);
  KvStoreSerializer<DepositTreeSnapshot> DEPOSIT_SNAPSHOT_SERIALIZER =
      new SszSerializer<>(DepositTreeSnapshot.SSZ_SCHEMA);
  KvStoreSerializer<DepositsFromBlockEvent> DEPOSITS_FROM_BLOCK_EVENT_SERIALIZER =
      new DepositsFromBlockEventSerializer();
  KvStoreSerializer<MinGenesisTimeBlockEvent> MIN_GENESIS_TIME_BLOCK_EVENT_SERIALIZER =
      new MinGenesisTimeBlockEventSerializer();
  KvStoreSerializer<SlotAndBlockRoot> SLOT_AND_BLOCK_ROOT_SERIALIZER =
      new SlotAndBlockRootSerializer();
  KvStoreSerializer<BlockCheckpoints> CHECKPOINT_EPOCHS_SERIALIZER =
      new BlockCheckpointsSerializer();
  KvStoreSerializer<Set<Bytes32>> BLOCK_ROOTS_SERIALIZER = new Bytes32SetSerializer();
  KvStoreSerializer<CompressedBranchInfo> COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER =
      new CompressedBranchInfoSerializer();

  static KvStoreSerializer<VoteTracker> createVoteTrackerSerializer(final Spec spec) {
    return new VoteTrackerSerializer(spec);
  }

  KvStoreSerializer<Void> VOID_SERIALIZER = new VoidSerializer();
  KvStoreSerializer<SlotAndBlockRoot> SLOT_AND_BLOCK_ROOT_KEY_SERIALIZER =
      new SlotAndBlockRootKeySerializer();
  KvStoreSerializer<SlotAndBlockRootAndBlobIndex>
      SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER =
          new SlotAndBlockRootAndBlobIndexKeySerializer();
  KvStoreSerializer<DataColumnSlotAndIdentifier> COLUMN_SLOT_AND_IDENTIFIER_KEY_SERIALIZER =
      new ColumnSlotAndIdentifierKeySerializer();
  KvStoreSerializer<List<List<KZGProof>>> DATA_COLUMN_SIDECARS_PROOFS_SERIALIZER =
      new DataColumnSidecarsProofsSerializer();

  static KvStoreSerializer<BeaconState> createStateSerializer(final Spec spec) {
    return new BeaconStateSerializer(spec);
  }

  static KvStoreSerializer<SignedBeaconBlock> createSignedBlockSerializer(final Spec spec) {
    return new SignedBeaconBlockSerializer(spec);
  }

  static KvStoreSerializer<SignedBlindedExecutionPayloadEnvelope>
      createSignedBlindedExecutionPayloadEnvelopeSerializer(final Spec spec) {
    return new SignedBlindedExecutionPayloadEnvelopeSerializer(spec);
  }

  static KvStoreSerializer<LightClientUpdate> createLightClientUpdateSerializer(final Spec spec) {
    return new LightClientUpdateSerializer(spec);
  }

  T deserialize(final byte[] data);

  byte[] serialize(final T value);
}
