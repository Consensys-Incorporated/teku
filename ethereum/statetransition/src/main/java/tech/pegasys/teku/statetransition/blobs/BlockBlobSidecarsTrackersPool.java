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

package tech.pegasys.teku.statetransition.blobs;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public interface BlockBlobSidecarsTrackersPool extends BlockEventsListener, SlotEventsChannel {

  BlockBlobSidecarsTrackersPool NOOP =
      new BlockBlobSidecarsTrackersPool() {
        @Override
        public void onSlot(final UInt64 slot) {}

        @Override
        public void onNewBlobSidecar(
            final BlobSidecar blobSidecar, final RemoteOrigin remoteOrigin) {}

        @Override
        public void onNewBlock(
            final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {}

        @Override
        public void onCompletedBlockAndBlobSidecars(
            final SignedBeaconBlock block, final List<BlobSidecar> blobSidecars) {}

        @Override
        public void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {}

        @Override
        public boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
          return false;
        }

        @Override
        public Optional<BlobSidecar> getBlobSidecar(final Bytes32 blockRoot, final UInt64 index) {
          return Optional.empty();
        }

        @Override
        public boolean containsBlock(final Bytes32 blockRoot) {
          return false;
        }

        @Override
        public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
          return Optional.empty();
        }

        @Override
        public BlockBlobSidecarsTracker getOrCreateBlockBlobSidecarsTracker(
            final SignedBeaconBlock block) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Optional<BlockBlobSidecarsTracker> getBlockBlobSidecarsTracker(
            final SignedBeaconBlock block) {
          return Optional.empty();
        }

        @Override
        public Set<BlobIdentifier> getAllRequiredBlobSidecars() {
          return Collections.emptySet();
        }

        @Override
        public void enableBlockImportOnCompletion(final SignedBeaconBlock block) {}

        @Override
        public void subscribeRequiredBlobSidecar(
            final RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber) {}

        @Override
        public void subscribeRequiredBlobSidecarDropped(
            final RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber) {}

        @Override
        public void subscribeRequiredBlockRoot(
            final RequiredBlockRootSubscriber requiredBlockRootSubscriber) {}

        @Override
        public void subscribeRequiredBlockRootDropped(
            final RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber) {}

        @Override
        public void subscribeNewBlobSidecar(NewBlobSidecarSubscriber newBlobSidecarSubscriber) {}
      };

  void onNewBlobSidecar(BlobSidecar blobSidecar, RemoteOrigin remoteOrigin);

  void onCompletedBlockAndBlobSidecars(SignedBeaconBlock block, List<BlobSidecar> blobSidecars);

  boolean containsBlobSidecar(BlobIdentifier blobIdentifier);

  Optional<BlobSidecar> getBlobSidecar(Bytes32 blockRoot, UInt64 index);

  boolean containsBlock(Bytes32 blockRoot);

  Optional<SignedBeaconBlock> getBlock(Bytes32 blockRoot);

  Set<BlobIdentifier> getAllRequiredBlobSidecars();

  BlockBlobSidecarsTracker getOrCreateBlockBlobSidecarsTracker(SignedBeaconBlock block);

  Optional<BlockBlobSidecarsTracker> getBlockBlobSidecarsTracker(SignedBeaconBlock block);

  void subscribeRequiredBlobSidecar(RequiredBlobSidecarSubscriber requiredBlobSidecarSubscriber);

  void subscribeRequiredBlobSidecarDropped(
      RequiredBlobSidecarDroppedSubscriber requiredBlobSidecarDroppedSubscriber);

  void subscribeRequiredBlockRoot(RequiredBlockRootSubscriber requiredBlockRootSubscriber);

  void subscribeRequiredBlockRootDropped(
      RequiredBlockRootDroppedSubscriber requiredBlockRootDroppedSubscriber);

  void subscribeNewBlobSidecar(NewBlobSidecarSubscriber newBlobSidecarSubscriber);

  interface RequiredBlobSidecarSubscriber {
    void onRequiredBlobSidecar(BlobIdentifier blobIdentifier);
  }

  interface RequiredBlobSidecarDroppedSubscriber {
    void onRequiredBlobSidecarDropped(BlobIdentifier blobIdentifier);
  }

  interface RequiredBlockRootSubscriber {
    void onRequiredBlockRoot(Bytes32 blockRoot);
  }

  interface RequiredBlockRootDroppedSubscriber {
    void onRequiredBlockRootDropped(Bytes32 blockRoot);
  }

  interface NewBlobSidecarSubscriber {
    void onNewBlobSidecar(BlobSidecar blobSidecar);
  }
}
