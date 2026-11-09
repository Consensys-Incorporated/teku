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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;

public class SyncCommitteeSubscriptionManagerTest {
  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);
  private final SyncCommitteeSubscriptionManager manager =
      new SyncCommitteeSubscriptionManager(network);

  @Test
  void shouldSubscribeToCommittee() {
    manager.subscribe(1, UInt64.ONE);
    verify(network).subscribeToSyncCommitteeSubnetId(1);
  }

  @Test
  void shouldUnsubscribeFromCommittee() {
    manager.subscribe(1, UInt64.ONE);
    manager.onSlot(UInt64.ONE);
    verify(network).subscribeToSyncCommitteeSubnetId(1);
    verify(network).unsubscribeFromSyncCommitteeSubnetId(1);
  }

  @Test
  void shouldUpdateCommitteeSubscription() {
    manager.subscribe(1, UInt64.ONE);

    manager.subscribe(1, UInt64.valueOf(2));
    manager.onSlot(UInt64.valueOf(1));
    // we've called subscribe twice with 2 different unsubscribe slots, but only 1 subscription
    // should be registered
    verify(network, Mockito.times(1)).subscribeToSyncCommitteeSubnetId(1);
    // the last subscription had a later unsubscribe than the onSlot, so it should not be
    // de-registered yet
    verify(network, never()).unsubscribeFromSyncCommitteeSubnetId(1);

    manager.onSlot(UInt64.valueOf(2));
    verify(network).unsubscribeFromSyncCommitteeSubnetId(1);
  }

  @Test
  void shouldUnsubscribeFromAllExpiredSubnets() {
    manager.subscribe(1, UInt64.ONE);
    manager.subscribe(2, UInt64.valueOf(2));
    manager.subscribe(3, UInt64.valueOf(3));
    manager.subscribe(4, UInt64.valueOf(4));

    manager.onSlot(UInt64.valueOf(3));
    verify(network).unsubscribeFromSyncCommitteeSubnetId(1);
    verify(network).unsubscribeFromSyncCommitteeSubnetId(2);
    verify(network).unsubscribeFromSyncCommitteeSubnetId(3);
    verify(network, never()).unsubscribeFromSyncCommitteeSubnetId(4);
  }

  @Test
  void getValidatorsWithDuty_shouldReturnRecordedValidators() {
    manager.recordDuty(1, UInt64.valueOf(16));
    manager.recordDuty(2, UInt64.valueOf(32));

    assertThat(manager.getValidatorsWithDuty()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void getValidatorsWithDuty_shouldForgetEndedDuties() {
    manager.recordDuty(1, UInt64.valueOf(16));
    manager.recordDuty(2, UInt64.valueOf(32));
    manager.onSlot(UInt64.valueOf(16));

    assertThat(manager.getValidatorsWithDuty()).containsExactly(2);
  }

  @Test
  void getValidatorsWithDuty_shouldKeepLatestEndSlot() {
    manager.recordDuty(1, UInt64.valueOf(32));
    manager.recordDuty(1, UInt64.valueOf(16));
    manager.onSlot(UInt64.valueOf(16));

    assertThat(manager.getValidatorsWithDuty()).containsExactly(1);
  }
}
