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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

/** A section of a particular target chain that can be downloaded in parallel. */
public interface Batch {
  UInt64 getFirstSlot();

  UInt64 getLastSlot();

  UInt64 getCount();

  Optional<SignedBeaconBlock> getFirstBlock();

  Optional<SignedBeaconBlock> getLastBlock();

  List<SignedBeaconBlock> getBlocks();

  Map<Bytes32, List<BlobSidecar>> getBlobSidecarsByBlockRoot();

  Map<Bytes32, SignedExecutionPayloadEnvelope> getExecutionPayloadsByBlockRoot();

  Optional<SyncSource> getSource();

  void markComplete();

  boolean isComplete();

  boolean isConfirmed();

  boolean isFirstBlockConfirmed();

  boolean isContested();

  boolean isEmpty();

  boolean isAwaitingBlocks();

  void markFirstBlockConfirmed();

  void markLastBlockConfirmed();

  void markAsContested();

  /**
   * Discard the batch data and report the current sync source for having provided it. Unless the
   * batch is contested, the source is penalized as for any invalid data ({@code LARGE_PENALTY}, a
   * single occurrence of which disconnects and bans the peer), so only call this for failures the
   * peer is responsible for.
   */
  void markAsInvalid();

  void requestMoreBlocks(Runnable callback);

  TargetChain getTargetChain();
}
