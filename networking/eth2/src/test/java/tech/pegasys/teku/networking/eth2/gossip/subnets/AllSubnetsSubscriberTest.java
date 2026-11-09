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

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;

class AllSubnetsSubscriberTest {
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);
  private final Spec spec = TestSpecFactory.createDefault();

  @SuppressWarnings("unchecked")
  @Test
  void shouldSubscribeToAllSubnetsWhenCreated() {
    final StableSubnetSubscriber stableSubnetSubscriber =
        AllSubnetsSubscriber.create(attestationTopicSubscriber, spec.getNetworkingConfig());
    final ArgumentCaptor<Set<SubnetSubscription>> captor = ArgumentCaptor.forClass(Set.class);
    verify(attestationTopicSubscriber).subscribeToPersistentSubnets(captor.capture());

    final Set<SubnetSubscription> actual = captor.getValue();
    // Should subscribe to all subnets with a far future unsubscription slot
    assertThat(actual).hasSize(spec.getNetworkingConfig().getAttestationSubnetCount());
    assertThat(actual.stream().mapToInt(SubnetSubscription::subnetId))
        .containsExactlyInAnyOrderElementsOf(
            IntStream.range(0, spec.getNetworkingConfig().getAttestationSubnetCount())
                .boxed()
                .collect(toSet()));
    assertThat(actual.stream().map(SubnetSubscription::unsubscriptionSlot))
        .containsOnly(UInt64.MAX_VALUE);

    stableSubnetSubscriber.onSlot(UInt64.ONE);
    verifyNoMoreInteractions(attestationTopicSubscriber);
  }
}
