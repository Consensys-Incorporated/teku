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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;

public class DataColumnSidecarSubnetBackboneSubscriber implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final Eth2P2PNetwork eth2P2PNetwork;
  private final UInt256 nodeId;
  private final CustodyGroupCountManager custodyGroupCountManager;
  private final Spec spec;

  private IntSet currentSubscribedSubnets = IntSet.of();
  private UInt64 lastEpoch = UInt64.MAX_VALUE;

  public DataColumnSidecarSubnetBackboneSubscriber(
      final Spec spec,
      final Eth2P2PNetwork eth2P2PNetwork,
      final UInt256 nodeId,
      final CustodyGroupCountManager custodyGroupCountManager) {
    this.spec = spec;
    this.eth2P2PNetwork = eth2P2PNetwork;
    this.nodeId = nodeId;
    this.custodyGroupCountManager = custodyGroupCountManager;
    LOG.debug(
        "Initial sampling group count value: {}", custodyGroupCountManager.getSamplingGroupCount());
  }

  private void subscribeToSubnets(final Collection<Integer> newSubscriptions) {
    final IntOpenHashSet newSubscriptionsSet = new IntOpenHashSet(newSubscriptions);

    for (int oldSubnet : currentSubscribedSubnets) {
      if (!newSubscriptionsSet.contains(oldSubnet)) {
        eth2P2PNetwork.unsubscribeFromDataColumnSidecarSubnetId(oldSubnet);
      }
    }

    for (int newSubnet : newSubscriptionsSet) {
      if (!currentSubscribedSubnets.contains(newSubnet)) {
        eth2P2PNetwork.subscribeToDataColumnSidecarSubnetId(newSubnet);
      }
    }

    currentSubscribedSubnets = newSubscriptionsSet;
  }

  private void onEpoch(final UInt64 epoch) {
    spec.atEpoch(epoch)
        .miscHelpers()
        .toVersionFulu()
        .ifPresent(
            miscHelpersFulu -> {
              LOG.debug(
                  "Sampling group count for epoch {}: {}",
                  epoch,
                  custodyGroupCountManager.getSamplingGroupCount());
              final List<UInt64> subnets =
                  miscHelpersFulu.computeDataColumnSidecarBackboneSubnets(
                      nodeId, custodyGroupCountManager.getSamplingGroupCount());
              subscribeToSubnets(subnets.stream().map(UInt64::intValue).toList());
            });
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    if (!epoch.equals(lastEpoch)) {
      lastEpoch = epoch;
      onEpoch(epoch);
    }
  }
}
