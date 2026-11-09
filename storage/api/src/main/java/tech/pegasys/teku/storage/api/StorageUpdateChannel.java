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

package tech.pegasys.teku.storage.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface StorageUpdateChannel extends ChannelInterface {

  SafeFuture<UpdateResult> onStorageUpdate(StorageUpdate event);

  SafeFuture<Void> onFinalizedBlocks(
      Collection<SignedBeaconBlock> finalizedBlocks,
      Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      Optional<UInt64> maybeEarliestBlobSidecarSlot);

  SafeFuture<Void> onReconstructedFinalizedState(BeaconState finalizedState, Bytes32 blockRoot);

  SafeFuture<Void> onWeakSubjectivityUpdate(WeakSubjectivityUpdate weakSubjectivityUpdate);

  SafeFuture<Void> onFinalizedDepositSnapshot(DepositTreeSnapshot depositTreeSnapshot);

  void onChainInitialized(AnchorPoint initialAnchor);
}
