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

package tech.pegasys.teku.networking.p2p.connection;

import com.google.common.base.Preconditions;

public class TargetPeerRange {
  private final int lowerBound;
  private final int upperBound;
  private final int minimumRandomlySelectedPeerCount;

  public TargetPeerRange(
      final int lowerBound, final int upperBound, final int minimumRandomlySelectedPeerCount) {
    Preconditions.checkArgument(lowerBound <= upperBound, "Invalid target peer count range");
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.minimumRandomlySelectedPeerCount = minimumRandomlySelectedPeerCount;
  }

  public int getPeersToAdd(final int currentPeerCount) {
    return currentPeerCount < lowerBound ? upperBound - currentPeerCount : 0;
  }

  public int getPeersToDrop(final int currentPeerCount) {
    return currentPeerCount > upperBound ? currentPeerCount - upperBound : 0;
  }

  public int getRandomlySelectedPeersToAdd(final int currentRandomlySelectedPeerCount) {
    return currentRandomlySelectedPeerCount < minimumRandomlySelectedPeerCount
        ? minimumRandomlySelectedPeerCount - currentRandomlySelectedPeerCount
        : 0;
  }

  public int getMinimumRandomlySelectedPeerCount() {
    return minimumRandomlySelectedPeerCount;
  }

  public int getRandomlySelectedPeersToDrop(
      final int currentRandomlySelectedPeerCount, final int currentTotalPeerCount) {
    final int totalPeersToDrop = getPeersToDrop(currentTotalPeerCount);
    if (totalPeersToDrop == 0) {
      return 0;
    }
    return currentRandomlySelectedPeerCount > minimumRandomlySelectedPeerCount
        ? Math.min(
            currentRandomlySelectedPeerCount - minimumRandomlySelectedPeerCount, totalPeersToDrop)
        : 0;
  }

  public int getRemotelyInitiatedPeersToDrop(
      final int currentRemotelyInitiatedPeerCount,
      final int totalOutboundRequirement,
      final int currentTotalPeerCount) {
    final int remotelyInitiatedPeerLimit = Math.max(0, upperBound - totalOutboundRequirement);
    final int maxPeersToDropWithoutGoingBelowLowerBound =
        Math.max(0, currentTotalPeerCount - lowerBound);

    return Math.clamp(
        currentRemotelyInitiatedPeerCount - remotelyInitiatedPeerLimit,
        0,
        maxPeersToDropWithoutGoingBelowLowerBound);
  }
}
