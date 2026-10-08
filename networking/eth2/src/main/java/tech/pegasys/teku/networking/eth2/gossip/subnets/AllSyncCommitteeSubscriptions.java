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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class AllSyncCommitteeSubscriptions extends SyncCommitteeSubscriptionManager {
  public AllSyncCommitteeSubscriptions(final Eth2P2PNetwork p2PNetwork, final Spec spec) {
    super(p2PNetwork);
    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      for (int i = 0; i < SYNC_COMMITTEE_SUBNET_COUNT; i++) {
        p2PNetwork.subscribeToSyncCommitteeSubnetId(i);
      }
    }
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    // we don't need to ever unsubscribe
    pruneDutyEndSlots(slot);
  }

  @Override
  public synchronized void subscribe(final int committeeSubnet, final UInt64 unsubscribeSlot) {
    // already subscribed to all subnets
    recordDutyEndSlot(unsubscribeSlot);
  }
}
