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

import com.google.common.base.MoreObjects;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

/** A chain which some set of peers claim to have that may be used as a target to sync to. */
public class TargetChain {
  private final SlotAndBlockRoot chainHead;
  private final Set<SyncSource> peers = new HashSet<>();

  public TargetChain(final SlotAndBlockRoot chainHead) {
    this.chainHead = chainHead;
  }

  void addPeer(final SyncSource peer) {
    peers.add(peer);
  }

  void removePeer(final SyncSource peer) {
    peers.remove(peer);
  }

  public int getPeerCount() {
    return peers.size();
  }

  public Collection<SyncSource> getPeers() {
    return Collections.unmodifiableSet(peers);
  }

  public SlotAndBlockRoot getChainHead() {
    return chainHead;
  }

  public Optional<SyncSource> selectRandomPeer(final SyncSource... excluding) {
    final Set<SyncSource> excludedPeers = Set.of(excluding);
    return peers.stream()
        .filter(peer -> !excludedPeers.contains(peer))
        .skip((int) ((peers.size() - excludedPeers.size()) * Math.random()))
        .findFirst();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TargetChain that = (TargetChain) o;
    return Objects.equals(chainHead, that.chainHead) && Objects.equals(peers, that.peers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chainHead, peers);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("chainHead", chainHead)
        .add("peers", peers)
        .toString();
  }
}
