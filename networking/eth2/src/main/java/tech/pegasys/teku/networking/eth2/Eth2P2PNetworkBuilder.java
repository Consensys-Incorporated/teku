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

package tech.pegasys.teku.networking.eth2;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsAltair;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsBellatrix;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsCapella;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsDeneb;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsElectra;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsFulu;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsFuluBpo;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsGloas;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsGloasBpo;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsPhase0;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeIdToDataColumnSidecarSubnetsCalculator;
import tech.pegasys.teku.networking.eth2.gossip.subnets.PeerSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetPeerCountLogger;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubnetTopicProvider;
import tech.pegasys.teku.networking.eth2.gossip.topics.Eth2GossipTopicFilter;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.ProcessedAttestationSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.DiscoveryNodeIdExtractor;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.peers.LibP2PDiscoveryNodeIdExtractor;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessageFactory;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPrivateKeyLoader;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.reputation.DefaultReputationManager;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.KeyValueStore;

/**
 * CAUTION: this API is unstable and primarily intended for debugging and testing purposes this API
 * might be changed in any version in backward incompatible way
 */
public class Eth2P2PNetworkBuilder {

  public static final Duration DEFAULT_ETH2_RPC_PING_INTERVAL = Duration.ofSeconds(10);
  public static final int DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD = 2;
  public static final Duration DEFAULT_ETH2_STATUS_UPDATE_INTERVAL = Duration.ofMinutes(5);

  protected P2PConfig config;
  protected EventChannels eventChannels;
  protected CombinedChainDataClient combinedChainDataClient;
  protected BlobKzgCommitmentsProvider blobKzgCommitmentsProvider;
  protected Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier;
  protected MetadataMessagesFactory metadataMessagesFactory = new MetadataMessagesFactory();
  protected OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
  protected OperationProcessor<BlobSidecar> gossipedBlobSidecarProcessor;
  protected OperationProcessor<ValidatableAttestation> gossipedAttestationConsumer;
  protected OperationProcessor<ValidatableAttestation> gossipedAggregateProcessor;
  protected OperationProcessor<AttesterSlashing> gossipedAttesterSlashingConsumer;
  protected OperationProcessor<ProposerSlashing> gossipedProposerSlashingConsumer;
  protected OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitConsumer;
  protected OperationProcessor<SignedBlsToExecutionChange>
      gossipedSignedBlsToExecutionChangeProcessor;
  protected OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor;
  protected OperationProcessor<PayloadAttestationMessage> payloadAttestationMessageProcessor;
  protected OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidProcessor;
  protected OperationProcessor<SignedProposerPreferences> proposerPreferencesProcessor;
  protected ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
  protected MetricsSystem metricsSystem;
  protected final List<RpcMethod<?, ?, ?>> rpcMethods = new ArrayList<>();
  protected final List<PeerHandler> peerHandlers = new ArrayList<>();
  protected TimeProvider timeProvider;
  protected AsyncRunner asyncRunner;
  protected KeyValueStore<String, Bytes> keyValueStore;
  protected Duration eth2RpcPingInterval = DEFAULT_ETH2_RPC_PING_INTERVAL;
  protected int eth2RpcOutstandingPingThreshold = DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
  protected final Duration eth2StatusUpdateInterval = DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
  protected Optional<Checkpoint> requiredCheckpoint = Optional.empty();
  protected Spec spec;
  protected OperationProcessor<SignedContributionAndProof>
      gossipedSignedContributionAndProofProcessor;
  protected OperationProcessor<ValidatableSyncCommitteeMessage>
      gossipedSyncCommitteeMessageProcessor;
  protected OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor;
  protected OperationProcessor<ExecutionProof> executionProofOperationProcessor;
  protected StatusMessageFactory statusMessageFactory;
  protected boolean recordMessageArrival;
  protected DebugDataDumper debugDataDumper;
  private DasGossipLogger dasGossipLogger;
  private DasReqRespLogger dasReqRespLogger;
  private Supplier<Boolean> isSuperNodeSupplier;
  private DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor;

  protected Eth2P2PNetworkBuilder() {}

  public static Eth2P2PNetworkBuilder create() {
    return new Eth2P2PNetworkBuilder();
  }

  public Eth2P2PNetwork build() {
    validate();

    // Setup eth2 handlers
    final SubnetSubscriptionService attestationSubnetService = new SubnetSubscriptionService();
    final SubnetSubscriptionService syncCommitteeSubnetService = new SubnetSubscriptionService();
    final SubnetSubscriptionService dataColumnSidecarSubnetService =
        new SubnetSubscriptionService();
    final SubnetSubscriptionService executionProofSubnetService = new SubnetSubscriptionService();
    final DiscoveryNodeIdExtractor discoveryNodeIdExtractor = new LibP2PDiscoveryNodeIdExtractor();
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(
            spec.getNetworkingConfig().getMaxPayloadSize(),
            config.isRpcSnappyAircompressorEnabled());
    if (statusMessageFactory == null) {
      statusMessageFactory = new StatusMessageFactory(spec, combinedChainDataClient, metricsSystem);
      eventChannels.subscribe(SlotEventsChannel.class, statusMessageFactory);
    }
    if (metadataMessagesFactory != null && spec.isMilestoneSupported(SpecMilestone.FULU)) {
      eventChannels.subscribe(CustodyGroupCountChannel.class, metadataMessagesFactory);
    }
    final Eth2PeerManager eth2PeerManager =
        Eth2PeerManager.create(
            asyncRunner,
            combinedChainDataClient,
            blobKzgCommitmentsProvider,
            custodyGroupCountManagerSupplier,
            metadataMessagesFactory,
            metricsSystem,
            attestationSubnetService,
            syncCommitteeSubnetService,
            rpcEncoding,
            statusMessageFactory,
            requiredCheckpoint,
            eth2RpcPingInterval,
            eth2RpcOutstandingPingThreshold,
            eth2StatusUpdateInterval,
            timeProvider,
            config.getPeerBlocksRateLimit(),
            config.getPeerBlobSidecarsRateLimit(),
            config.getPeerRequestLimit(),
            spec,
            discoveryNodeIdExtractor,
            dataColumnSidecarArchiveReconstructor,
            dasReqRespLogger);
    final Collection<RpcMethod<?, ?, ?>> eth2RpcMethods =
        eth2PeerManager.getBeaconChainMethods().all();
    rpcMethods.addAll(eth2RpcMethods);
    peerHandlers.add(eth2PeerManager);

    final GossipEncoding gossipEncoding = config.getGossipEncoding();
    // Build core network and inject eth2 handlers
    final DiscoveryNetwork<?> network =
        buildNetwork(
            gossipEncoding,
            syncCommitteeSubnetService,
            dataColumnSidecarSubnetService,
            eth2PeerManager);

    final GossipForkManager gossipForkManager = buildGossipForkManager(gossipEncoding, network);

    return new ActiveEth2P2PNetwork(
        config.getSpec(),
        asyncRunner,
        network,
        eth2PeerManager,
        gossipForkManager,
        eventChannels,
        combinedChainDataClient.getRecentChainData(),
        attestationSubnetService,
        syncCommitteeSubnetService,
        dataColumnSidecarSubnetService,
        executionProofSubnetService,
        gossipEncoding,
        config.getGossipConfigurator(),
        processedAttestationSubscriptionProvider,
        config.isAllTopicsFilterEnabled());
  }

  private GossipForkManager buildGossipForkManager(
      final GossipEncoding gossipEncoding, final DiscoveryNetwork<?> network) {
    final GossipForkManager.Builder gossipForkManagerBuilder =
        GossipForkManager.builder()
            .spec(spec)
            .recentChainData(combinedChainDataClient.getRecentChainData());
    spec.getEnabledMilestones().stream()
        .map(
            forkAndSpecMilestone ->
                createSubscriptions(forkAndSpecMilestone, network, gossipEncoding))
        .forEach(gossipForkManagerBuilder::fork);
    // BPO
    spec.getBpoForks().stream()
        .map(
            bpo -> {
              final Fork fork = spec.getForkSchedule().getFork(bpo.epoch());
              final SpecMilestone milestone =
                  spec.getForkSchedule().getSpecMilestoneAtEpoch(bpo.epoch());
              final ForkAndSpecMilestone forkAndSpecMilestone =
                  new ForkAndSpecMilestone(fork, milestone);
              return createBpoSubscriptions(forkAndSpecMilestone, network, gossipEncoding, bpo);
            })
        .forEach(gossipForkManagerBuilder::bpoFork);

    return gossipForkManagerBuilder.build();
  }

  private GossipForkSubscriptions createSubscriptions(
      final ForkAndSpecMilestone forkAndSpecMilestone,
      final DiscoveryNetwork<?> network,
      final GossipEncoding gossipEncoding) {
    return switch (forkAndSpecMilestone.getSpecMilestone()) {
      case PHASE0 ->
          new GossipForkSubscriptionsPhase0(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              debugDataDumper);
      case ALTAIR ->
          new GossipForkSubscriptionsAltair(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              debugDataDumper);
      case BELLATRIX ->
          new GossipForkSubscriptionsBellatrix(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              debugDataDumper);
      case CAPELLA ->
          new GossipForkSubscriptionsCapella(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              debugDataDumper);
      case DENEB ->
          new GossipForkSubscriptionsDeneb(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              debugDataDumper);
      case ELECTRA ->
          new GossipForkSubscriptionsElectra(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              debugDataDumper,
              executionProofOperationProcessor,
              config.isExecutionProofTopicEnabled());
      case FULU ->
          new GossipForkSubscriptionsFulu(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              dataColumnSidecarOperationProcessor,
              debugDataDumper,
              dasGossipLogger,
              executionProofOperationProcessor,
              config.isExecutionProofTopicEnabled(),
              isSuperNodeSupplier);
      case GLOAS, HEZE ->
          new GossipForkSubscriptionsGloas(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              dataColumnSidecarOperationProcessor,
              executionPayloadProcessor,
              payloadAttestationMessageProcessor,
              executionPayloadBidProcessor,
              proposerPreferencesProcessor,
              debugDataDumper,
              dasGossipLogger,
              executionProofOperationProcessor,
              config.isExecutionProofTopicEnabled(),
              isSuperNodeSupplier);
    };
  }

  private GossipForkSubscriptions createBpoSubscriptions(
      final ForkAndSpecMilestone forkAndSpecMilestone,
      final DiscoveryNetwork<?> network,
      final GossipEncoding gossipEncoding,
      final BlobParameters bpo) {
    return switch (forkAndSpecMilestone.getSpecMilestone()) {
      case FULU ->
          new GossipForkSubscriptionsFuluBpo(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              dataColumnSidecarOperationProcessor,
              executionProofOperationProcessor,
              debugDataDumper,
              dasGossipLogger,
              bpo,
              config.isExecutionProofTopicEnabled(),
              isSuperNodeSupplier);
      case GLOAS, HEZE ->
          new GossipForkSubscriptionsGloasBpo(
              forkAndSpecMilestone.getFork(),
              spec,
              asyncRunner,
              metricsSystem,
              network,
              combinedChainDataClient.getRecentChainData(),
              gossipEncoding,
              gossipedBlockProcessor,
              gossipedBlobSidecarProcessor,
              gossipedAttestationConsumer,
              gossipedAggregateProcessor,
              gossipedAttesterSlashingConsumer,
              gossipedProposerSlashingConsumer,
              gossipedVoluntaryExitConsumer,
              gossipedSignedContributionAndProofProcessor,
              gossipedSyncCommitteeMessageProcessor,
              gossipedSignedBlsToExecutionChangeProcessor,
              dataColumnSidecarOperationProcessor,
              executionPayloadProcessor,
              payloadAttestationMessageProcessor,
              executionPayloadBidProcessor,
              proposerPreferencesProcessor,
              executionProofOperationProcessor,
              debugDataDumper,
              dasGossipLogger,
              bpo,
              config.isExecutionProofTopicEnabled(),
              isSuperNodeSupplier);
      default ->
          throw new IllegalStateException(
              "BPO is not supported for: " + forkAndSpecMilestone.getSpecMilestone());
    };
  }

  protected DiscoveryNetwork<?> buildNetwork(
      final GossipEncoding gossipEncoding,
      final SubnetSubscriptionService syncCommitteeSubnetService,
      final SubnetSubscriptionService dataColumnSidecarSubnetService,
      final PeerLookup peerLookup) {
    final PeerPools peerPools = new PeerPools();
    final ReputationManager reputationManager =
        new DefaultReputationManager(
            metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY, peerPools);
    PreparedGossipMessageFactory defaultMessageFactory =
        gossipEncoding.createPreparedGossipMessageFactory(
            combinedChainDataClient.getRecentChainData()::getMilestoneByForkDigest);
    final GossipTopicFilter gossipTopicsFilter =
        new Eth2GossipTopicFilter(
            combinedChainDataClient.getRecentChainData(), gossipEncoding, spec, config);
    final NetworkConfig networkConfig = config.getNetworkConfig();
    final DiscoveryConfig discoConfig = config.getDiscoveryConfig();

    final P2PNetwork<Peer> p2pNetwork =
        createLibP2PNetworkBuilder()
            .asyncRunner(asyncRunner)
            .metricsSystem(metricsSystem)
            .config(networkConfig)
            .networkingSpecConfig(config.getNetworkingSpecConfig())
            .privateKeyProvider(
                new LibP2PPrivateKeyLoader(keyValueStore, networkConfig.getPrivateKeySource()))
            .reputationManager(reputationManager)
            .rpcMethods(rpcMethods)
            .peerHandlers(peerHandlers)
            .preparedGossipMessageFactory(defaultMessageFactory)
            .gossipTopicFilter(gossipTopicsFilter)
            .timeProvider(timeProvider)
            .recordMessageArrival(recordMessageArrival)
            .build();

    final AttestationSubnetTopicProvider attestationSubnetTopicProvider =
        new AttestationSubnetTopicProvider(
            combinedChainDataClient.getRecentChainData(), gossipEncoding);
    final SyncCommitteeSubnetTopicProvider syncCommitteeSubnetTopicProvider =
        new SyncCommitteeSubnetTopicProvider(
            combinedChainDataClient.getRecentChainData(), gossipEncoding);
    final DataColumnSidecarSubnetTopicProvider dataColumnSidecarSubnetTopicProvider =
        new DataColumnSidecarSubnetTopicProvider(
            combinedChainDataClient.getRecentChainData(), gossipEncoding);

    final TargetPeerRange targetPeerRange =
        new TargetPeerRange(
            discoConfig.getMinPeers(),
            discoConfig.getMaxPeers(),
            discoConfig.getMinRandomlySelectedPeers());
    final SchemaDefinitionsSupplier currentSchemaDefinitions =
        () -> combinedChainDataClient.getRecentChainData().getCurrentSpec().getSchemaDefinitions();

    final NodeIdToDataColumnSidecarSubnetsCalculator nodeIdToDataColumnSidecarSubnetsCalculator =
        NodeIdToDataColumnSidecarSubnetsCalculator.create(
            spec, () -> combinedChainDataClient.getRecentChainData().getCurrentSlot());

    final SettableLabelledGauge subnetPeerCountGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.NETWORK,
            "subnet_peer_count",
            "Number of currently connected peers subscribed to each subnet",
            "subnet");
    final SyncCommitteeSubnetPeerCountLogger syncCommitteeSubnetPeerCountLogger =
        new SyncCommitteeSubnetPeerCountLogger(
            timeProvider, config.getTargetSubnetSubscriberCount());
    return createDiscoveryNetworkBuilder()
        .metricsSystem(metricsSystem)
        .asyncRunner(asyncRunner)
        .kvStore(keyValueStore)
        .p2pNetwork(p2pNetwork)
        .peerPools(peerPools)
        .peerSelectionStrategy(
            new Eth2PeerSelectionStrategy(
                targetPeerRange,
                network ->
                    PeerSubnetSubscriptions.create(
                        combinedChainDataClient.getRecentChainData().getCurrentSpec(),
                        nodeIdToDataColumnSidecarSubnetsCalculator,
                        network,
                        peerLookup,
                        attestationSubnetTopicProvider,
                        syncCommitteeSubnetTopicProvider,
                        syncCommitteeSubnetService,
                        dataColumnSidecarSubnetTopicProvider,
                        dataColumnSidecarSubnetService,
                        config.getTargetSubnetSubscriberCount(),
                        subnetPeerCountGauge,
                        syncCommitteeSubnetPeerCountLogger),
                reputationManager,
                Collections::shuffle))
        .discoveryConfig(discoConfig)
        .p2pConfig(networkConfig)
        .spec(config.getSpec())
        .timeProvider(timeProvider)
        .currentSchemaDefinitionsSupplier(currentSchemaDefinitions)
        .build();
  }

  protected DiscoveryNetworkBuilder createDiscoveryNetworkBuilder() {
    return DiscoveryNetworkBuilder.create();
  }

  protected LibP2PNetworkBuilder createLibP2PNetworkBuilder() {
    return LibP2PNetworkBuilder.create();
  }

  private void validate() {
    assertNotNull("config", config);
    assertNotNull("eventChannels", eventChannels);
    assertNotNull("metricsSystem", metricsSystem);
    assertNotNull("combinedChainDataClient", combinedChainDataClient);
    assertNotNull("blobKzgCommitmentsProvider", blobKzgCommitmentsProvider);
    assertNotNull("custodyGroupCountManagerSupplier", custodyGroupCountManagerSupplier);
    assertNotNull("metadataMessagesFactory", metadataMessagesFactory);
    assertNotNull("keyValueStore", keyValueStore);
    assertNotNull("timeProvider", timeProvider);
    assertNotNull("gossipedBlockProcessor", gossipedBlockProcessor);
    assertNotNull("gossipedBlobSidecarProcessor", gossipedBlobSidecarProcessor);
    assertNotNull("gossipedAttestationProcessor", gossipedAttestationConsumer);
    assertNotNull("gossipedAggregateProcessor", gossipedAggregateProcessor);
    assertNotNull("gossipedAttesterSlashingProcessor", gossipedAttesterSlashingConsumer);
    assertNotNull("gossipedProposerSlashingProcessor", gossipedProposerSlashingConsumer);
    assertNotNull("gossipedVoluntaryExitProcessor", gossipedVoluntaryExitConsumer);
    assertNotNull(
        "gossipedSignedContributionAndProofProcessor", gossipedSignedContributionAndProofProcessor);
    assertNotNull("gossipedSyncCommitteeMessageProcessor", gossipedSyncCommitteeMessageProcessor);
    assertNotNull(
        "gossipedSignedBlsToExecutionChangeProcessor", gossipedSignedBlsToExecutionChangeProcessor);
    assertNotNull(
        "gossipedDataColumnSidecarOperationProcessor", dataColumnSidecarOperationProcessor);
    assertNotNull("gossipedExecutionPayloadProcessor", executionPayloadProcessor);
    assertNotNull("gossipedPayloadAttestationMessageProcessor", payloadAttestationMessageProcessor);
    assertNotNull("gossipedExecutionProofOperationProcessor", executionProofOperationProcessor);
    assertNotNull("gossipedExecutionPayloadBidProcessor", executionPayloadBidProcessor);
    assertNotNull("gossipedProposerPreferencesProcessor", proposerPreferencesProcessor);
  }

  private void assertNotNull(final String fieldName, final Object fieldValue) {
    checkState(fieldValue != null, "Field %s must be set.", fieldName);
  }

  public Eth2P2PNetworkBuilder config(final P2PConfig config) {
    checkNotNull(config);
    this.config = config;
    return this;
  }

  public Eth2P2PNetworkBuilder eventChannels(final EventChannels eventChannels) {
    checkNotNull(eventChannels);
    this.eventChannels = eventChannels;
    return this;
  }

  public Eth2P2PNetworkBuilder combinedChainDataClient(
      final CombinedChainDataClient combinedChainDataClient) {
    checkNotNull(combinedChainDataClient);
    this.combinedChainDataClient = combinedChainDataClient;
    return this;
  }

  public Eth2P2PNetworkBuilder blobKzgCommitmentsProvider(
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider) {
    checkNotNull(blobKzgCommitmentsProvider);
    this.blobKzgCommitmentsProvider = blobKzgCommitmentsProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder custodyGroupCountManagerSupplier(
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier) {
    checkNotNull(custodyGroupCountManagerSupplier);
    this.custodyGroupCountManagerSupplier = custodyGroupCountManagerSupplier;
    return this;
  }

  public Eth2P2PNetworkBuilder metadataMessagesFactory(
      final MetadataMessagesFactory metadataMessagesFactory) {
    checkNotNull(metadataMessagesFactory);
    this.metadataMessagesFactory = metadataMessagesFactory;
    return this;
  }

  public Eth2P2PNetworkBuilder keyValueStore(final KeyValueStore<String, Bytes> kvStore) {
    checkNotNull(kvStore);
    this.keyValueStore = kvStore;
    return this;
  }

  public Eth2P2PNetworkBuilder processedAttestationSubscriptionProvider(
      final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
    checkNotNull(processedAttestationSubscriptionProvider);
    this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedBlockProcessor(
      final OperationProcessor<SignedBeaconBlock> blockProcessor) {
    checkNotNull(blockProcessor);
    this.gossipedBlockProcessor = blockProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedBlobSidecarProcessor(
      final OperationProcessor<BlobSidecar> blobSidecarProcessor) {
    checkNotNull(blobSidecarProcessor);
    this.gossipedBlobSidecarProcessor = blobSidecarProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAttestationProcessor(
      final OperationProcessor<ValidatableAttestation> gossipedAttestationProcessor) {
    checkNotNull(gossipedAttestationProcessor);
    this.gossipedAttestationConsumer = gossipedAttestationProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAggregateProcessor(
      final OperationProcessor<ValidatableAttestation> gossipedAggregateProcessor) {
    checkNotNull(gossipedAggregateProcessor);
    this.gossipedAggregateProcessor = gossipedAggregateProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedAttesterSlashingProcessor(
      final OperationProcessor<AttesterSlashing> gossipedAttesterSlashingProcessor) {
    checkNotNull(gossipedAttesterSlashingProcessor);
    this.gossipedAttesterSlashingConsumer = gossipedAttesterSlashingProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedProposerSlashingProcessor(
      final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
    checkNotNull(gossipedProposerSlashingProcessor);
    this.gossipedProposerSlashingConsumer = gossipedProposerSlashingProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedVoluntaryExitProcessor(
      final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
    checkNotNull(gossipedVoluntaryExitProcessor);
    this.gossipedVoluntaryExitConsumer = gossipedVoluntaryExitProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSignedContributionAndProofProcessor(
      final OperationProcessor<SignedContributionAndProof>
          gossipedSignedContributionAndProofProcessor) {
    checkNotNull(gossipedSignedContributionAndProofProcessor);
    this.gossipedSignedContributionAndProofProcessor = gossipedSignedContributionAndProofProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSyncCommitteeMessageProcessor(
      final OperationProcessor<ValidatableSyncCommitteeMessage>
          gossipedSyncCommitteeMessageProcessor) {
    checkNotNull(gossipedSyncCommitteeMessageProcessor);
    this.gossipedSyncCommitteeMessageProcessor = gossipedSyncCommitteeMessageProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedSignedBlsToExecutionChangeProcessor(
      final OperationProcessor<SignedBlsToExecutionChange>
          gossipedSignedBlsToExecutionChangeProcessor) {
    checkNotNull(gossipedSignedBlsToExecutionChangeProcessor);
    this.gossipedSignedBlsToExecutionChangeProcessor = gossipedSignedBlsToExecutionChangeProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedDataColumnSidecarOperationProcessor(
      final OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor) {
    checkNotNull(dataColumnSidecarOperationProcessor);
    this.dataColumnSidecarOperationProcessor = dataColumnSidecarOperationProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedExecutionProofOperationProcessor(
      final OperationProcessor<ExecutionProof> executionProofOperationProcessor) {
    checkNotNull(executionProofOperationProcessor);
    this.executionProofOperationProcessor = executionProofOperationProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedExecutionPayloadProcessor(
      final OperationProcessor<SignedExecutionPayloadEnvelope> gossipedExecutionPayloadProcessor) {
    checkNotNull(gossipedExecutionPayloadProcessor);
    this.executionPayloadProcessor = gossipedExecutionPayloadProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedPayloadAttestationMessageProcessor(
      final OperationProcessor<PayloadAttestationMessage>
          gossipedPayloadAttestationMessageProcessor) {
    checkNotNull(gossipedPayloadAttestationMessageProcessor);
    this.payloadAttestationMessageProcessor = gossipedPayloadAttestationMessageProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedExecutionPayloadBidProcessor(
      final OperationProcessor<SignedExecutionPayloadBid> gossipedExecutionPayloadBidProcessor) {
    checkNotNull(gossipedExecutionPayloadBidProcessor);
    this.executionPayloadBidProcessor = gossipedExecutionPayloadBidProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipedProposerPreferencesProcessor(
      final OperationProcessor<SignedProposerPreferences> gossipedProposerPreferencesProcessor) {
    checkNotNull(gossipedProposerPreferencesProcessor);
    this.proposerPreferencesProcessor = gossipedProposerPreferencesProcessor;
    return this;
  }

  public Eth2P2PNetworkBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public Eth2P2PNetworkBuilder timeProvider(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
    return this;
  }

  public Eth2P2PNetworkBuilder rpcMethod(final RpcMethod<?, ?, ?> rpcMethod) {
    checkNotNull(rpcMethod);
    rpcMethods.add(rpcMethod);
    return this;
  }

  public Eth2P2PNetworkBuilder peerHandler(final PeerHandler peerHandler) {
    checkNotNull(peerHandler);
    peerHandlers.add(peerHandler);
    return this;
  }

  public Eth2P2PNetworkBuilder asyncRunner(final AsyncRunner asyncRunner) {
    checkNotNull(asyncRunner);
    this.asyncRunner = asyncRunner;
    return this;
  }

  public Eth2P2PNetworkBuilder eth2RpcPingInterval(final Duration eth2RpcPingInterval) {
    checkNotNull(eth2RpcPingInterval);
    this.eth2RpcPingInterval = eth2RpcPingInterval;
    return this;
  }

  public Eth2P2PNetworkBuilder eth2RpcOutstandingPingThreshold(
      final int eth2RpcOutstandingPingThreshold) {
    checkArgument(eth2RpcOutstandingPingThreshold > 0);
    this.eth2RpcOutstandingPingThreshold = eth2RpcOutstandingPingThreshold;
    return this;
  }

  public Eth2P2PNetworkBuilder requiredCheckpoint(final Optional<Checkpoint> requiredCheckpoint) {
    checkNotNull(requiredCheckpoint);
    this.requiredCheckpoint = requiredCheckpoint;
    return this;
  }

  public Eth2P2PNetworkBuilder specProvider(final Spec spec) {
    checkNotNull(spec);
    this.spec = spec;
    return this;
  }

  public Eth2P2PNetworkBuilder statusMessageFactory(
      final StatusMessageFactory statusMessageFactory) {
    checkNotNull(statusMessageFactory);
    this.statusMessageFactory = statusMessageFactory;
    return this;
  }

  public Eth2P2PNetworkBuilder recordMessageArrival(final boolean recordMessageArrival) {
    this.recordMessageArrival = recordMessageArrival;
    return this;
  }

  public Eth2P2PNetworkBuilder p2pDebugDataDumper(final DebugDataDumper debugDataDumper) {
    this.debugDataDumper = debugDataDumper;
    return this;
  }

  public Eth2P2PNetworkBuilder gossipDasLogger(final DasGossipLogger dasGossipLogger) {
    this.dasGossipLogger = dasGossipLogger;
    return this;
  }

  public Eth2P2PNetworkBuilder reqRespDasLogger(final DasReqRespLogger dasReqRespLogger) {
    this.dasReqRespLogger = dasReqRespLogger;
    return this;
  }

  public Eth2P2PNetworkBuilder isSuperNodeSupplier(final Supplier<Boolean> isSuperNodeSupplier) {
    this.isSuperNodeSupplier = isSuperNodeSupplier;
    return this;
  }

  public Eth2P2PNetworkBuilder dataColumnSidecarArchiveReconstructor(
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor) {
    this.dataColumnSidecarArchiveReconstructor = dataColumnSidecarArchiveReconstructor;
    return this;
  }
}
