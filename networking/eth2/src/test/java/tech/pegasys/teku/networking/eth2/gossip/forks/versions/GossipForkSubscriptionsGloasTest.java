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

package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.AggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttestationGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.AttesterSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadBidGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PqAggregateGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.PqPayloadAttestationMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerPreferencesGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.ProposerSlashingGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.SignedBlsToExecutionChangeGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.SignedContributionAndProofGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.SyncCommitteeMessageGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.VoluntaryExitGossipManager;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class GossipForkSubscriptionsGloasTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();
  private final Fork fork = spec.getForkSchedule().getFork(UInt64.ZERO);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void shouldAddRequiredGossipManagers() {
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        spy(createGossipForkSubscriptionGloas());

    gossipForkSubscriptions.addGossipManagers(forkInfo(), forkDigest());

    verify(gossipForkSubscriptions, times(14)).addGossipManager(any());
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadGossipManager.class));
    verify(gossipForkSubscriptions)
        .addGossipManager(any(PayloadAttestationMessageGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadBidGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ProposerPreferencesGossipManager.class));
  }

  @Test
  public void shouldAddOnlyPqTopicsFromThePqFork() {
    final Spec pqSpec = TestSpecFactory.createMinimalPq();
    final Fork pqFork = pqSpec.getForkSchedule().getFork(UInt64.ZERO);
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        spy(createGossipForkSubscriptionGloas(pqSpec, pqFork));

    gossipForkSubscriptions.addGossipManagers(
        new ForkInfo(pqFork, dataStructureUtil.randomBytes32()), forkDigest());

    verify(gossipForkSubscriptions, times(10)).addGossipManager(any());
    verify(gossipForkSubscriptions).addGossipManager(any(AttestationGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(BlockGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(PqAggregateGossipManager.class));
    verify(gossipForkSubscriptions)
        .addGossipManager(any(PqPayloadAttestationMessageGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ProposerSlashingGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(AttesterSlashingGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(DataColumnSidecarGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ExecutionPayloadBidGossipManager.class));
    verify(gossipForkSubscriptions).addGossipManager(any(ProposerPreferencesGossipManager.class));
    // Removed in PQ
    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(VoluntaryExitGossipManager.class));
    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(SignedBlsToExecutionChangeGossipManager.class));
    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(SyncCommitteeMessageGossipManager.class));
    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(SignedContributionAndProofGossipManager.class));
    // Earlier forks' aggregate and payload attestation message types
    verify(gossipForkSubscriptions, never()).addGossipManager(any(AggregateGossipManager.class));
    verify(gossipForkSubscriptions, never())
        .addGossipManager(any(PayloadAttestationMessageGossipManager.class));
  }

  @Test
  public void shouldIgnoreRemovedTopicsFromThePqFork() {
    final Spec pqSpec = TestSpecFactory.createMinimalPq();
    final Fork pqFork = pqSpec.getForkSchedule().getFork(UInt64.ZERO);
    final GossipForkSubscriptionsGloas gossipForkSubscriptions =
        createGossipForkSubscriptionGloas(pqSpec, pqFork);
    gossipForkSubscriptions.addGossipManagers(
        new ForkInfo(pqFork, dataStructureUtil.randomBytes32()), forkDigest());

    // Their gossip managers don't exist in PQ, so these must not reach them
    assertThatNoException()
        .isThrownBy(
            () -> {
              gossipForkSubscriptions.subscribeToSyncCommitteeSubnet(1);
              gossipForkSubscriptions.unsubscribeFromSyncCommitteeSubnet(1);
              gossipForkSubscriptions.publishSyncCommitteeMessage(
                  mock(ValidatableSyncCommitteeMessage.class));
              gossipForkSubscriptions.publishSyncCommitteeContribution(
                  mock(SignedContributionAndProof.class));
              gossipForkSubscriptions.publishVoluntaryExit(mock(SignedVoluntaryExit.class));
            });
  }

  private ForkInfo forkInfo() {
    return new ForkInfo(fork, dataStructureUtil.randomBytes32());
  }

  private Bytes4 forkDigest() {
    return dataStructureUtil.randomBytes4();
  }

  private GossipForkSubscriptionsGloas createGossipForkSubscriptionGloas() {
    return createGossipForkSubscriptionGloas(spec, fork);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private GossipForkSubscriptionsGloas createGossipForkSubscriptionGloas(
      final Spec spec, final Fork fork) {
    final DiscoveryNetwork discoveryNetwork = mock(DiscoveryNetwork.class);
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
    final GossipEncoding gossipEncoding = mock(GossipEncoding.class);
    final OperationProcessor noopOperationProcessor = OperationProcessor.NOOP;

    return new GossipForkSubscriptionsGloas(
        fork,
        spec,
        new StubAsyncRunner(),
        new StubMetricsSystem(),
        discoveryNetwork,
        recentChainData,
        gossipEncoding,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        Optional.empty(),
        Optional.empty(),
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        noopOperationProcessor,
        DebugDataDumper.NOOP,
        DasGossipLogger.NOOP,
        noopOperationProcessor,
        false,
        () -> false);
  }
}
