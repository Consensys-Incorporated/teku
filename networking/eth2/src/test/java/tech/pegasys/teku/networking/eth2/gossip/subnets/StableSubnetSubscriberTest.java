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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("unchecked")
public class StableSubnetSubscriberTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final UInt256 nodeId = dataStructureUtil.randomUInt256();

  @Test
  void shouldSubscribeToSubnetsPerNodeAtStart() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();
    final UInt64 currentSlot = ZERO;
    stableSubnetSubscriber.onSlot(currentSlot);
    ArgumentCaptor<Set<SubnetSubscription>> subnetSubscriptions =
        ArgumentCaptor.forClass(Set.class);

    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(subnetSubscriptions.capture());
    assertThat(subnetSubscriptions.getValue())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());
    final UInt64 unsubscriptionSlot =
        spec.getGenesisSpec()
            .miscHelpers()
            .calculateNodeSubnetUnsubscriptionSlot(nodeId, currentSlot);
    subnetSubscriptions
        .getValue()
        .forEach(
            subnetSubscription ->
                assertThat(subnetSubscription.unsubscriptionSlot()).isEqualTo(unsubscriptionSlot));
    assertSubnetsAreDistinct(subnetSubscriptions.getValue());
  }

  @Test
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    StableSubnetSubscriber stableSubnetSubscriber = createStableSubnetSubscriber();

    stableSubnetSubscriber.onSlot(ZERO);

    ArgumentCaptor<Set<SubnetSubscription>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);
    ArgumentCaptor<Set<SubnetSubscription>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Set.class);

    verify(attestationTopicSubscriber)
        .subscribeToPersistentSubnets(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue())
        .hasSize(spec.getNetworkingConfig().getSubnetsPerNode());

    UInt64 firstUnsubscriptionSlot =
        firstSubscriptionUpdate.getValue().stream().findFirst().get().unsubscriptionSlot();

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UInt64.ONE));

    verifyNoMoreInteractions(attestationTopicSubscriber);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(attestationTopicSubscriber, times(spec.getNetworkingConfig().getSubnetsPerNode()))
        .subscribeToPersistentSubnets(secondSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue())
        .isNotEqualTo(secondSubscriptionUpdate.getValue());

    UInt64 secondUnsubscriptionSlot =
        secondSubscriptionUpdate.getValue().stream().findFirst().get().unsubscriptionSlot();

    assertThat(firstUnsubscriptionSlot).isNotEqualByComparingTo(secondUnsubscriptionSlot);
    assertThat(
            secondUnsubscriptionSlot
                .minus(firstUnsubscriptionSlot)
                .dividedBy(spec.getGenesisSpecConfig().getSlotsPerEpoch()))
        .isEqualTo(UInt64.valueOf(spec.getNetworkingConfig().getEpochsPerSubnetSubscription()));
  }

  private void assertSubnetsAreDistinct(final Set<SubnetSubscription> subnetSubscriptions) {
    IntSet subnetIds =
        IntOpenHashSet.toSet(
            subnetSubscriptions.stream()
                .map(SubnetSubscription::subnetId)
                .mapToInt(Integer::intValue));
    assertThat(subnetSubscriptions).hasSameSizeAs(subnetIds);
  }

  private StableSubnetSubscriber createStableSubnetSubscriber() {
    return new NodeBasedStableSubnetSubscriber(attestationTopicSubscriber, spec, nodeId);
  }
}
