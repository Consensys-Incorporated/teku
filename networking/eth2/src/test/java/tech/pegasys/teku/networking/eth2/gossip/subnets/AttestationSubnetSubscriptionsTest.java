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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class AttestationSubnetSubscriptionsTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("unchecked")
  private final OperationProcessor<ValidatableAttestation> processor =
      mock(OperationProcessor.class);

  private AttestationSubnetSubscriptions subnetSubscriptions;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            recentChainData,
            processor,
            recentChainData.getCurrentForkInfo().orElseThrow(),
            recentChainData.getCurrentForkDigest().orElseThrow(),
            DebugDataDumper.NOOP);
    subnetSubscriptions.subscribe();

    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void getChannelReturnsEmptyIfNotSubscribedToSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation(6);
    final Attestation attestation2 = dataStructureUtil.randomAttestation(5);
    int subnetId = computeSubnetId(attestation);
    assertThat(computeSubnetId(attestation2)).isNotEqualTo(subnetId); // Sanity check
    subnetSubscriptions.subscribeToSubnetId(subnetId);
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithNonEmptyOptional();
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation2))
        .isCompletedWithEmptyOptional();
  }

  @Test
  void getChannelReturnsTheChannelFromSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation(4);
    int subnetId = computeSubnetId(attestation);
    subnetSubscriptions.subscribeToSubnetId(subnetId);
    assertThatSafeFuture(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithNonEmptyOptional();
  }

  @Test
  void shouldSubscribeToCommitteesOnDifferentSubnets() {
    final Attestation attestation1 = dataStructureUtil.randomAttestation(3);
    final Attestation attestation2 = dataStructureUtil.randomAttestation(2);
    int subnetId1 = computeSubnetId(attestation1);
    int subnetId2 = computeSubnetId(attestation2);
    assertThat(subnetId1).isNotEqualTo(subnetId2); // Sanity check

    TopicChannel topicChannel1 = mock(TopicChannel.class);
    TopicChannel topicChannel2 = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId1), any()))
        .thenReturn(topicChannel1);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId2), any()))
        .thenReturn(topicChannel2);

    subnetSubscriptions.subscribeToSubnetId(subnetId1);
    subnetSubscriptions.subscribeToSubnetId(subnetId2);

    verifyNoInteractions(topicChannel2);

    verify(gossipNetwork)
        .subscribe(argThat(i -> i.contains("beacon_attestation_" + subnetId1)), any());
    verify(gossipNetwork)
        .subscribe(argThat(i -> i.contains("beacon_attestation_" + subnetId2)), any());

    assertThat(subnetSubscriptions.getChannel(attestation1))
        .isCompletedWithValue(Optional.of(topicChannel1));
    assertThat(subnetSubscriptions.getChannel(attestation2))
        .isCompletedWithValue(Optional.of(topicChannel2));
  }

  @Test
  void shouldUnsubscribeFromOnlyCommitteeOnSubnet() {
    final Attestation attestation = dataStructureUtil.randomAttestation(5);
    final int subnetId = computeSubnetId(attestation);
    TopicChannel topicChannel = mock(TopicChannel.class);
    when(gossipNetwork.subscribe(contains("beacon_attestation_" + subnetId), any()))
        .thenReturn(topicChannel);

    subnetSubscriptions.subscribeToSubnetId(subnetId);

    verify(gossipNetwork).subscribe(any(), any());

    assertThat(subnetSubscriptions.getChannel(attestation))
        .isCompletedWithValue(Optional.of(topicChannel));

    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);

    verify(topicChannel).close();
  }

  @Test
  void shouldCreateHandlerForSingleAttestationWhenMilestoneIsElectra() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final RecentChainData recentChainData = storageSystem.recentChainData();
    subnetSubscriptions =
        new AttestationSubnetSubscriptions(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            recentChainData,
            processor,
            recentChainData.getCurrentForkInfo().orElseThrow(),
            recentChainData.getCurrentForkDigest().orElseThrow(),
            DebugDataDumper.NOOP);
    assertDoesNotThrow(
        () -> subnetSubscriptions.getAttestationSchema().toSingleAttestationSchemaRequired());
  }

  private int computeSubnetId(final Attestation attestation) {
    return spec.computeSubnetForAttestation(
        safeJoin(recentChainData.getBestState().orElseThrow()), attestation);
  }
}
