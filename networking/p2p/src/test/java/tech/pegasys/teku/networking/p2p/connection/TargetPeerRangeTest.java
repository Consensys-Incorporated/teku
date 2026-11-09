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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TargetPeerRangeTest {
  @Test
  public void shouldNotAddPeersWhenPeerCountIsEqualToRangeBottom() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToAdd(5)).isZero();
  }

  @Test
  public void shouldNotAddPeersWhenPeerCountIsWithinRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToAdd(6)).isZero();
  }

  @Test
  public void shouldNotAddPeersWhenPeerCountIsAboveRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToAdd(26)).isZero();
  }

  @Test
  public void shouldAddEnoughPeersToReachUpperBoundWhenPeerCountIsBelowRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToAdd(4)).isEqualTo(6);
  }

  @Test
  public void shouldNotDropPeersWhenPeerCountIsEqualToTopOfRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToDrop(10)).isZero();
  }

  @Test
  public void shouldNotDropPeersWhenPeerCountIsWithinRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToDrop(8)).isZero();
  }

  @Test
  public void shouldDropPeersToReachUpperBoundWhenPeerCountIsAboveRange() {
    assertThat(new TargetPeerRange(5, 10, 0).getPeersToDrop(12)).isEqualTo(2);
  }

  @Test
  void shouldAddRandomlySelectedPeersWhenCountIsBelowMinimum() {
    assertThat(new TargetPeerRange(5, 10, 5).getRandomlySelectedPeersToAdd(2)).isEqualTo(3);
  }

  @Test
  void shouldAddRandomlySelectedPeersWhenCountIsBelowMinimumEvenWhenPeerLimitExceeded() {
    assertThat(new TargetPeerRange(5, 5, 10).getRandomlySelectedPeersToAdd(7)).isEqualTo(3);
  }

  @Test
  void shouldNotAddRandomlySelectedPeersWhenCountIsEqualToMinimum() {
    assertThat(new TargetPeerRange(5, 10, 5).getRandomlySelectedPeersToAdd(5)).isZero();
  }

  @Test
  void shouldNotAddRandomlySelectedPeersWhenCountIsAboveMinimum() {
    assertThat(new TargetPeerRange(5, 10, 5).getRandomlySelectedPeersToAdd(6)).isZero();
  }

  @Test
  void shouldDropRandomlySelectedPeerWhenTotalPeerCountAndMinimumExceeded() {
    assertThat(new TargetPeerRange(5, 10, 3).getRandomlySelectedPeersToDrop(5, 15)).isEqualTo(2);
  }

  @Test
  void shouldOnlyDropEnoughRandomlySelectedPeersToReachTheTargetPeerCountRange() {
    assertThat(new TargetPeerRange(5, 10, 3).getRandomlySelectedPeersToDrop(10, 15)).isEqualTo(5);
  }

  @Test
  void shouldNotDropRandomlySelectedPeersWhenEqualToMinimum() {
    assertThat(new TargetPeerRange(5, 10, 3).getRandomlySelectedPeersToDrop(3, 15)).isZero();
  }

  @Test
  void shouldNotDropRandomlySelectedPeersWhenBelowMinimum() {
    assertThat(new TargetPeerRange(5, 10, 3).getRandomlySelectedPeersToDrop(2, 15)).isZero();
  }

  @Test
  void shouldDropRemotelyInitiatedPeersAboveReservedPeerLimit() {
    assertThat(new TargetPeerRange(5, 10, 3).getRemotelyInitiatedPeersToDrop(8, 3, 10))
        .isEqualTo(1);
  }

  @Test
  void shouldDropAllRemotelyInitiatedPeersWhenOutboundRequirementExceedsUpperBound() {
    assertThat(new TargetPeerRange(5, 10, 3).getRemotelyInitiatedPeersToDrop(8, 12, 15))
        .isEqualTo(8);
  }

  @Test
  void shouldNotDropRemotelyInitiatedPeersWhenPeerCountIsBelowLowerBound() {
    assertThat(new TargetPeerRange(25, 35, 25).getRemotelyInitiatedPeersToDrop(10, 26, 18))
        .isZero();
  }

  @Test
  void shouldLimitRemotelyInitiatedPeerDropsToAvoidDroppingBelowLowerBound() {
    assertThat(new TargetPeerRange(25, 35, 25).getRemotelyInitiatedPeersToDrop(30, 26, 30))
        .isEqualTo(5);
  }
}
