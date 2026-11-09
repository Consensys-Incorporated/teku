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

package tech.pegasys.teku.statetransition.synccommittee;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeStateUtils {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final RecentChainData recentChainData;

  public SyncCommitteeStateUtils(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<Optional<BeaconStateAltair>> getStateForSyncCommittee(final UInt64 slot) {
    final Optional<ChainHead> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return getStateForBlockAtSlot(slot, chainHead.get());
  }

  private SafeFuture<Optional<BeaconStateAltair>> getStateForBlockAtSlot(
      final UInt64 slot, final ChainHead chainHead) {
    final UInt64 chainHeadSlot = chainHead.getSlot();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(slot);
    final UInt64 requiredEpoch =
        syncCommitteeUtil.getMinEpochForSyncCommitteeAssignments(spec.computeEpochAtSlot(slot));

    final UInt64 requiredSlot = spec.computeStartSlotAtEpoch(requiredEpoch);

    if (chainHeadSlot.isGreaterThanOrEqualTo(requiredSlot)) {
      return chainHead.getState().thenApply(BeaconState::toVersionAltair);
    }
    if (chainHeadSlot.plus(spec.getSlotsPerEpoch(chainHeadSlot)).isLessThan(requiredSlot)) {
      LOG.warn(
          "Ignoring sync committee gossip because the chain head is too old. "
              + "Chain head slot {} is more than an epoch before the required slot {}",
          chainHeadSlot,
          requiredSlot);
      return SafeFuture.completedFuture(Optional.empty());
    }
    return recentChainData
        .retrieveBlockState(
            new SlotAndBlockRoot(chainHeadSlot.max(requiredSlot), chainHead.getRoot()))
        .thenApply(maybeState -> maybeState.flatMap(BeaconState::toVersionAltair));
  }
}
