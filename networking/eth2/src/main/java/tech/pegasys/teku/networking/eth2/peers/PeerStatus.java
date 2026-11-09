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

package tech.pegasys.teku.networking.eth2.peers;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class PeerStatus {

  private final Bytes4 forkDigest;
  private final Checkpoint finalizedCheckpoint;
  private final Bytes32 headRoot;
  private final UInt64 headSlot;
  private final Optional<UInt64> earliestAvailableSlot;

  public static PeerStatus fromStatusMessage(final StatusMessage message) {
    return new PeerStatus(
        message.getForkDigest().copy(),
        message.getFinalizedRoot().copy(),
        message.getFinalizedEpoch(),
        message.getHeadRoot().copy(),
        message.getHeadSlot(),
        message.getEarliestAvailableSlot());
  }

  public PeerStatus(
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot,
      final Optional<UInt64> earliestAvailableSlot) {
    this.forkDigest = forkDigest;
    this.finalizedCheckpoint = new Checkpoint(finalizedEpoch, finalizedRoot);
    this.headRoot = headRoot;
    this.headSlot = headSlot;
    this.earliestAvailableSlot = earliestAvailableSlot;
  }

  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  public Bytes32 getFinalizedRoot() {
    return finalizedCheckpoint.getRoot();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedCheckpoint.getEpoch();
  }

  public Bytes32 getHeadRoot() {
    return headRoot;
  }

  public UInt64 getHeadSlot() {
    return headSlot;
  }

  public Optional<UInt64> getEarliestAvailableSlot() {
    return earliestAvailableSlot;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkDigest", forkDigest)
        .add("finalizedRoot", getFinalizedRoot())
        .add("finalizedEpoch", getFinalizedEpoch())
        .add("headRoot", headRoot)
        .add("headSlot", headSlot)
        .add("earliestAvailableSlot", earliestAvailableSlot)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PeerStatus)) {
      return false;
    }
    final PeerStatus that = (PeerStatus) o;
    return Objects.equals(forkDigest, that.forkDigest)
        && Objects.equals(finalizedCheckpoint, that.finalizedCheckpoint)
        && Objects.equals(headRoot, that.headRoot)
        && Objects.equals(headSlot, that.headSlot)
        && Objects.equals(earliestAvailableSlot, that.earliestAvailableSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkDigest, finalizedCheckpoint, headRoot, headSlot, earliestAvailableSlot);
  }
}
