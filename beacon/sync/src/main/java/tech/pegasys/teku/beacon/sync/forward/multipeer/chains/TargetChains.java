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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

/**
 * Tracks the set of potential target chains to sync. Designed to be usable for either finalized and
 * non-finalized chains.
 */
public class TargetChains {

  private static final Comparator<TargetChain> CHAIN_COMPARATOR =
      Comparator.comparingInt(TargetChain::getPeerCount)
          .thenComparing(chain -> chain.getChainHead().getSlot())
          .reversed();

  private final Map<SlotAndBlockRoot, TargetChain> chains = new HashMap<>();
  private final Map<SyncSource, SlotAndBlockRoot> lastPeerTarget = new HashMap<>();
  private final SettableLabelledGauge targetChainCountGauge;
  private final String chainType;

  public TargetChains(final SettableLabelledGauge targetChainCountGauge, final String chainType) {
    this.targetChainCountGauge = targetChainCountGauge;
    this.chainType = chainType;
  }

  public void onPeerStatusUpdated(final SyncSource peer, final SlotAndBlockRoot chainHead) {
    removePeerFromLastChain(peer);
    chains.computeIfAbsent(chainHead, TargetChain::new).addPeer(peer);
    lastPeerTarget.put(peer, chainHead);
    targetChainCountGauge.set(chains.size(), chainType);
  }

  public void onPeerDisconnected(final SyncSource peer) {
    removePeerFromLastChain(peer);
    lastPeerTarget.remove(peer);
  }

  public Stream<TargetChain> streamChains() {
    return chains.values().stream().sorted(CHAIN_COMPARATOR);
  }

  private void removePeerFromLastChain(final SyncSource peer) {
    final TargetChain previousChain = chains.get(lastPeerTarget.get(peer));
    if (previousChain != null) {
      previousChain.removePeer(peer);
      if (previousChain.getPeerCount() == 0) {
        chains.remove(previousChain.getChainHead());
        targetChainCountGauge.set(chains.size(), chainType);
      }
    }
  }
}
