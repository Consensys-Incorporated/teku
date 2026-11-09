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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class NodeBasedStableSubnetSubscriberTest {
  protected Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Eth2P2PNetwork network = mock(Eth2P2PNetwork.class);

  private final AttestationTopicSubscriber attestationTopicSubscriber =
      new AttestationTopicSubscriber(spec, network, mock(SettableLabelledGauge.class));
  private final int attestationSubnetCount = spec.getNetworkingConfig().getAttestationSubnetCount();

  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void shouldSubscribeToExpectedSubnets() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ZERO);
    verify(network, times(spec.getNetworkingConfig().getSubnetsPerNode()))
        .subscribeToAttestationSubnetId(intThat(i -> i >= 0 && i < attestationSubnetCount));
  }

  @Test
  void shouldSubscribeToNewSubnetsAfterExpiration() {
    final NodeBasedStableSubnetSubscriber subscriber = createSubscriber();
    subscriber.onSlot(UInt64.ONE);
    final ArgumentCaptor<Integer> firstSubscriptions = ArgumentCaptor.forClass(Integer.class);

    verify(network, times(spec.getNetworkingConfig().getSubnetsPerNode()))
        .subscribeToAttestationSubnetId(firstSubscriptions.capture());
    assertThat(firstSubscriptions.getAllValues())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());

    final UInt64 nextSlot =
        UInt64.ONE.plus(
            UInt64.valueOf(
                (long) spec.getNetworkingConfig().getEpochsPerSubnetSubscription()
                    * spec.getSlotsPerEpoch(UInt64.ONE)));
    subscriber.onSlot(nextSlot);

    final ArgumentCaptor<Integer> secondSubscriptions = ArgumentCaptor.forClass(Integer.class);
    verify(network, times(spec.getNetworkingConfig().getSubnetsPerNode() * 2))
        .subscribeToAttestationSubnetId(secondSubscriptions.capture());

    assertThat(secondSubscriptions.getAllValues())
        .allMatch(subnetId -> subnetId >= 0 && subnetId < attestationSubnetCount);
    assertThat(secondSubscriptions.getAllValues())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode() * 2);

    attestationTopicSubscriber.onSlot(nextSlot);
    verify(network, times(1))
        .unsubscribeFromAttestationSubnetId(firstSubscriptions.getAllValues().get(0));
    verify(network, times(1))
        .unsubscribeFromAttestationSubnetId(firstSubscriptions.getAllValues().get(1));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldUpdateUnsubscriptionSlotWhenAlreadySubscribed() {
    final Spec specMock = mock(Spec.class);
    final SpecVersion specVersionMock = mock(SpecVersion.class);
    final MiscHelpers miscHelpersMock = mock(MiscHelpers.class);

    // Subscribe to subnet 12 and 63 at first
    // Subscribe to subnet 12 again and 25 at the next round
    when(miscHelpersMock.computeSubscribedSubnets(any(), any()))
        .thenReturn(List.of(UInt64.valueOf(12), UInt64.valueOf(63)))
        .thenReturn(List.of(UInt64.valueOf(12), UInt64.valueOf(25)));

    // Subnet 12 expires at slot 8 and subnet 63 at slot 4
    when(miscHelpersMock.calculateNodeSubnetUnsubscriptionSlot(any(), eq(UInt64.ZERO)))
        .thenReturn(UInt64.valueOf(8))
        .thenReturn(UInt64.valueOf(4));
    // Subnet 12 and 25 of the next round expire both at slot 12
    when(miscHelpersMock.calculateNodeSubnetUnsubscriptionSlot(any(), eq(UInt64.valueOf(5))))
        .thenReturn(UInt64.valueOf(12));

    when(specVersionMock.miscHelpers()).thenReturn(miscHelpersMock);
    when(specMock.atSlot(any())).thenReturn(specVersionMock);
    NetworkingSpecConfig networkingSpecConfigMock = mock(NetworkingSpecConfig.class);
    when(networkingSpecConfigMock.getSubnetsPerNode()).thenReturn(2);
    when(specMock.getNetworkingConfig()).thenReturn(networkingSpecConfigMock);

    final AttestationTopicSubscriber attestationTopicSubscriberMock =
        mock(AttestationTopicSubscriber.class);

    final NodeBasedStableSubnetSubscriber subscriber =
        new NodeBasedStableSubnetSubscriber(
            attestationTopicSubscriberMock, specMock, dataStructureUtil.randomUInt256());

    final ArgumentCaptor<Set<SubnetSubscription>> subscriptionCaptor =
        ArgumentCaptor.forClass(Set.class);

    // Should subscribe to subnet 12 until slot 8 and subnet 63 until slot 4
    subscriber.onSlot(UInt64.ZERO);
    verify(attestationTopicSubscriberMock, times(1))
        .subscribeToPersistentSubnets(subscriptionCaptor.capture());
    Set<SubnetSubscription> actual = subscriptionCaptor.getValue();
    assertThat(actual).hasSize(2);
    assertThat(actual).contains(new SubnetSubscription(12, UInt64.valueOf(8)));
    assertThat(actual).contains(new SubnetSubscription(63, UInt64.valueOf(4)));

    // Subnet 63 expires, subnet 12 is still active
    // Subnet 12 expiration should be updated to slot 12
    // subscribe to subnet 25 until slot 12
    subscriber.onSlot(UInt64.valueOf(5));
    verify(attestationTopicSubscriberMock, times(2))
        .subscribeToPersistentSubnets(subscriptionCaptor.capture());
    actual = subscriptionCaptor.getValue();
    assertThat(actual).hasSize(2);
    assertThat(actual).contains(new SubnetSubscription(12, UInt64.valueOf(12)));
    assertThat(actual).contains(new SubnetSubscription(25, UInt64.valueOf(12)));
  }

  @NotNull
  private NodeBasedStableSubnetSubscriber createSubscriber() {
    return new NodeBasedStableSubnetSubscriber(
        attestationTopicSubscriber, spec, dataStructureUtil.randomUInt256());
  }
}
