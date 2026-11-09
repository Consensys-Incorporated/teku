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

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlockEventsListenerRouter implements BlockEventsListener {
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  private final Supplier<DataAvailabilitySampler> dasSamplerSupplier;
  private final RecentChainData recentChainData;
  private final Spec spec;

  public BlockEventsListenerRouter(
      final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool,
      final Supplier<DataAvailabilitySampler> dasSamplerSupplier,
      final RecentChainData recentChainData,
      final Spec spec) {
    this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
    this.dasSamplerSupplier = dasSamplerSupplier;
    this.recentChainData = recentChainData;
    this.spec = spec;
  }

  private BlockEventsListener lookupBlockEventsListener(final UInt64 slot) {
    final SpecMilestone blockMilestone = spec.atSlot(slot).getMilestone();
    if (blockMilestone.isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return dasSamplerSupplier.get();
    } else if (blockMilestone.isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      return blockBlobSidecarsTrackersPool;
    }

    return BlockEventsListener.NOOP;
  }

  @Override
  public void onNewBlock(final SignedBeaconBlock block, final Optional<RemoteOrigin> remoteOrigin) {
    if (recentChainData.containsBlock(block.getRoot())) {
      return;
    }
    lookupBlockEventsListener(block.getSlot()).onNewBlock(block, remoteOrigin);
  }

  @Override
  public void removeAllForBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    lookupBlockEventsListener(slotAndBlockRoot.getSlot()).removeAllForBlock(slotAndBlockRoot);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block) {
    lookupBlockEventsListener(block.getSlot()).onBlockImported(block);
  }

  @Override
  public void enableBlockImportOnCompletion(final SignedBeaconBlock block) {
    lookupBlockEventsListener(block.getSlot()).enableBlockImportOnCompletion(block);
  }
}
