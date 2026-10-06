/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.SemiDuplexNoOutboundStreamException;
import io.libp2p.pubsub.NoPeersForOutboundMessageException;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetSubscriptions;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;

class SyncCommitteeMessageGossipManagerTest {
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());

  final Spec spec = mock(Spec.class);
  final SyncCommitteeUtil syncCommitteeUtil = mock(SyncCommitteeUtil.class);

  private final SyncCommitteeStateUtils syncCommitteeStateUtils =
      mock(SyncCommitteeStateUtils.class);
  private final SyncCommitteeSubnetSubscriptions subnetSubscriptions =
      mock(SyncCommitteeSubnetSubscriptions.class);
  private final Runnable peerSearchRequester = mock(Runnable.class);

  private final SyncCommitteeMessageGossipManager gossipManager =
      new SyncCommitteeMessageGossipManager(
          metricsSystem, spec, syncCommitteeStateUtils, subnetSubscriptions, peerSearchRequester);

  @BeforeEach
  void setUp() {
    when(subnetSubscriptions.gossip(any(), anyInt())).thenReturn(SafeFuture.completedFuture(null));
    when(spec.computeEpochAtSlot(any())).thenReturn(UInt64.ZERO);
    when(spec.getSyncCommitteeUtilRequired(any())).thenReturn(syncCommitteeUtil);
  }

  @Test
  void shouldPublishToReceivedSubnetWhenPresent() {
    final int subnetId = 3;
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(
            dataStructureUtil.randomSyncCommitteeMessage(), subnetId);

    gossipManager.publish(message);

    verify(subnetSubscriptions).gossip(message.getMessage(), subnetId);
  }

  @Test
  void shouldPublishToAllApplicableSubnetsWhenNoReceivedSubnetsPresent() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());

    withApplicableSubnets(message, 1, 3, 5);
    gossipManager.publish(message);

    verify(subnetSubscriptions).gossip(message.getMessage(), 1);
    verify(subnetSubscriptions).gossip(message.getMessage(), 3);
    verify(subnetSubscriptions).gossip(message.getMessage(), 5);
  }

  @Test
  void shouldCalculateAndPublishToAllApplicableSubnetsWhenAlreadyNotCached() {
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromValidator(
            dataStructureUtil.randomSyncCommitteeMessage());

    final UInt64 dutyEpoch = UInt64.valueOf(333);
    final BeaconStateAltair state = dataStructureUtil.stateBuilderAltair().build();
    when(syncCommitteeStateUtils.getStateForSyncCommittee(message.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    when(syncCommitteeUtil.getEpochForDutiesAtSlot(any())).thenReturn(dutyEpoch);
    when(syncCommitteeUtil.getSubcommitteeAssignments(any(), any(), any()))
        .thenReturn(
            SyncSubcommitteeAssignments.builder()
                .addAssignment(1, 1)
                .addAssignment(3, 1)
                .addAssignment(5, 1)
                .build());

    gossipManager.publish(message);

    verify(syncCommitteeUtil)
        .getSubcommitteeAssignments(state, dutyEpoch, message.getMessage().getValidatorIndex());
    verify(subnetSubscriptions).gossip(message.getMessage(), 1);
    verify(subnetSubscriptions).gossip(message.getMessage(), 3);
    verify(subnetSubscriptions).gossip(message.getMessage(), 5);
  }

  @Test
  void shouldRequestPeerSearchWhenNoPeersAreAvailableForSyncCommitteeSubnet() {
    final int subnetId = 3;
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(
            dataStructureUtil.randomSyncCommitteeMessage(), subnetId);
    when(subnetSubscriptions.gossip(message.getMessage(), subnetId))
        .thenReturn(
            SafeFuture.failedFuture(new NoPeersForOutboundMessageException("no peers available")));

    gossipManager.publish(message);

    verify(peerSearchRequester).run();
    assertThat(peerSearchRequestedCount("no_peers")).isEqualTo(1);
    assertThat(peerSearchRequestedCount("no_outbound_stream")).isZero();
  }

  @Test
  void shouldRequestPeerSearchWhenNoOutboundStreamIsAvailableForSyncCommitteeSubnet() {
    final int subnetId = 3;
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(
            dataStructureUtil.randomSyncCommitteeMessage(), subnetId);
    when(subnetSubscriptions.gossip(message.getMessage(), subnetId))
        .thenReturn(SafeFuture.failedFuture(new SemiDuplexNoOutboundStreamException("no stream")));

    gossipManager.publish(message);

    verify(peerSearchRequester).run();
    assertThat(peerSearchRequestedCount("no_outbound_stream")).isEqualTo(1);
    assertThat(peerSearchRequestedCount("no_peers")).isZero();
  }

  @Test
  void shouldNotRequestPeerSearchForOtherPublishFailures() {
    final int subnetId = 3;
    final ValidatableSyncCommitteeMessage message =
        ValidatableSyncCommitteeMessage.fromNetwork(
            dataStructureUtil.randomSyncCommitteeMessage(), subnetId);
    when(subnetSubscriptions.gossip(message.getMessage(), subnetId))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("boom")));

    gossipManager.publish(message);

    verify(peerSearchRequester, never()).run();
    assertThat(peerSearchRequestedCount("no_peers")).isZero();
    assertThat(peerSearchRequestedCount("no_outbound_stream")).isZero();
  }

  private long peerSearchRequestedCount(final String reason) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.BEACON, "sync_committee_message_peer_search_requested_total", reason);
  }

  private void withApplicableSubnets(
      final ValidatableSyncCommitteeMessage message, final int... subnetIds) {
    final SyncSubcommitteeAssignments.Builder assignmentBuilder =
        SyncSubcommitteeAssignments.builder();
    IntStream.of(subnetIds).forEach(subnetId -> assignmentBuilder.addAssignment(subnetId, 1));
    message.setSubcommitteeAssignments(assignmentBuilder.build());
  }
}
