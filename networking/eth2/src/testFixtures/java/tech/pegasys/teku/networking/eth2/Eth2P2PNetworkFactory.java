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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Supplier;
import java.net.BindException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.networking.eth2.gossip.config.GossipConfigurator;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkManager;
import tech.pegasys.teku.networking.eth2.gossip.forks.GossipForkSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsAltair;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsBellatrix;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsCapella;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsDeneb;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsElectra;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsFulu;
import tech.pegasys.teku.networking.eth2.gossip.forks.versions.GossipForkSubscriptionsGloas;
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
import tech.pegasys.teku.networking.eth2.gossip.topics.VerifiedBlockAttestationsSubscriptionProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager;
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerSelectionStrategy;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.connection.PeerPools;
import tech.pegasys.teku.networking.p2p.connection.TargetPeerRange;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder;
import tech.pegasys.teku.networking.p2p.libp2p.gossip.GossipTopicFilter;
import tech.pegasys.teku.networking.p2p.mock.MockDiscoveryNodeIdGenerator;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.networking.p2p.network.PeerHandler;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.reputation.DefaultReputationManager;
import tech.pegasys.teku.networking.p2p.reputation.ReputationManager;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.block.VerifiedBlockOperationsListener;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StubStorageQueryChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

public class Eth2P2PNetworkFactory {

  private static final Logger LOG = LogManager.getLogger();
  protected static final NoOpMetricsSystem METRICS_SYSTEM = new NoOpMetricsSystem();
  private static final MockDiscoveryNodeIdGenerator DISCOVERY_NODE_ID_GENERATOR =
      new MockDiscoveryNodeIdGenerator();
  private static final int MIN_PORT = 6000;
  private static final int MAX_PORT = 9000;

  private final List<Eth2P2PNetwork> networks = new ArrayList<>();

  public Eth2P2PNetworkBuilder builder() {
    return new Eth2P2PNetworkBuilder();
  }

  public void stopAll() throws InterruptedException, ExecutionException, TimeoutException {
    Waiter.waitFor(
        SafeFuture.allOf(networks.stream().map(P2PNetwork::stop).toArray(SafeFuture[]::new)));
  }

  public class Eth2P2PNetworkBuilder {

    protected List<Eth2P2PNetwork> peers = new ArrayList<>();
    protected AsyncRunner asyncRunner;
    protected EventChannels eventChannels;
    protected RecentChainData recentChainData;
    protected StorageQueryChannel historicalChainData = new StubStorageQueryChannel();
    protected OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor;
    protected OperationProcessor<BlobSidecar> gossipedBlobSidecarProcessor;
    protected OperationProcessor<ValidatableAttestation> gossipedAttestationProcessor;
    protected OperationProcessor<ValidatableAttestation> gossipedAggregateProcessor;
    protected OperationProcessor<AttesterSlashing> attesterSlashingProcessor;
    protected OperationProcessor<ProposerSlashing> proposerSlashingProcessor;
    protected OperationProcessor<SignedVoluntaryExit> voluntaryExitProcessor;
    protected OperationProcessor<SignedContributionAndProof> signedContributionAndProofProcessor;
    protected OperationProcessor<ValidatableSyncCommitteeMessage> syncCommitteeMessageProcessor;
    protected OperationProcessor<SignedBlsToExecutionChange> signedBlsToExecutionChangeProcessor;
    protected OperationProcessor<DataColumnSidecar> dataColumnSidecarOperationProcessor;
    protected OperationProcessor<ExecutionProof> executionProofOperationProcessor;
    protected OperationProcessor<SignedExecutionPayloadEnvelope> executionPayloadProcessor;
    protected OperationProcessor<PayloadAttestationMessage> payloadAttestationMessageProcessor;
    protected OperationProcessor<SignedExecutionPayloadBid> executionPayloadBidProcessor;
    protected OperationProcessor<SignedProposerPreferences> proposerPreferencesProcessor;
    protected ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider;
    protected VerifiedBlockAttestationsSubscriptionProvider
        verifiedBlockAttestationsSubscriptionProvider;
    protected Function<RpcMethod<?, ?, ?>, Stream<RpcMethod<?, ?, ?>>> rpcMethodsModifier =
        Stream::of;
    protected List<PeerHandler> peerHandlers = new ArrayList<>();
    protected RpcEncoding rpcEncoding;
    protected GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
    private Optional<Checkpoint> requiredCheckpoint = Optional.empty();
    protected Duration eth2RpcPingInterval;
    protected Integer eth2RpcOutstandingPingThreshold;
    protected Duration eth2StatusUpdateInterval;
    protected Spec spec = TestSpecFactory.createMinimalPhase0();
    protected DebugDataDumper debugDataDumper;
    protected Supplier<Boolean> isSuperNodeSupplier;

    public Eth2P2PNetwork startNetwork() throws Exception {
      setDefaults();
      return buildAndStartNetwork();
    }

    protected Eth2P2PNetwork buildAndStartNetwork() throws Exception {
      int attempt = 1;
      while (true) {
        final P2PConfig config = generateConfig();
        final Eth2P2PNetwork network = buildNetwork(config);
        try {
          final int expected = peers.size();
          final SafeFuture<Void> allPeersReady = new SafeFuture<>();
          final Set<NodeId> readyPeers = ConcurrentHashMap.newKeySet();
          final long subscriptionId =
              network.subscribeConnect(
                  eth2Peer -> {
                    readyPeers.add(eth2Peer.getId());
                    if (readyPeers.size() >= expected) {
                      allPeersReady.complete(null);
                    }
                  });
          try {
            network.start().get(30, TimeUnit.SECONDS);
            // Register for cleanup as soon as the network is bound to a port, so that any
            // subsequent failure (timeout waiting for peers, symmetric check, etc.) still results
            // in the network being stopped by stopAll() during teardown.
            networks.add(network);
            if (expected == 0) {
              allPeersReady.complete(null);
            } else {
              // Handle the "already arrived" case where peers connected before we subscribed.
              network.streamPeers().forEach(p -> readyPeers.add(p.getId()));
              if (readyPeers.size() >= expected) {
                allPeersReady.complete(null);
              }
            }
            allPeersReady.get(30, TimeUnit.SECONDS);

            // Symmetric check: wait for each pre-existing peer to observe THIS network on its end.
            for (Eth2P2PNetwork remote : peers) {
              Waiter.waitFor(
                  () ->
                      assertThat(
                              remote
                                  .streamPeers()
                                  .anyMatch(p -> p.getId().equals(network.getNodeId())))
                          .isTrue());
            }
            return network;
          } finally {
            network.unsubscribeConnect(subscriptionId);
          }
        } catch (ExecutionException e) {
          if (e.getCause() instanceof BindException) {
            if (attempt > 10) {
              throw new RuntimeException("Failed to find a free port after multiple attempts", e);
            }
            LOG.info(
                "Port conflict detected, retrying with a new port. Original message: {}",
                e.getMessage());
            attempt++;
            Waiter.waitFor(network.stop());
          } else {
            throw e;
          }
        }
      }
    }

    protected Eth2P2PNetwork buildNetwork(final P2PConfig config) {
      {
        // Setup eth2 handlers
        final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
        final SubnetSubscriptionService attestationSubnetService = new SubnetSubscriptionService();
        final SubnetSubscriptionService syncCommitteeSubnetService =
            new SubnetSubscriptionService();
        final SubnetSubscriptionService dataColumnSidecarSubnetService =
            new SubnetSubscriptionService();
        final SubnetSubscriptionService executionProofSubnetService =
            new SubnetSubscriptionService();
        final CombinedChainDataClient combinedChainDataClient =
            new CombinedChainDataClient(recentChainData, historicalChainData, spec);
        final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider =
            new BlobKzgCommitmentsProvider(spec, combinedChainDataClient, 128);
        final DataColumnSidecarSubnetTopicProvider dataColumnSidecarSubnetTopicProvider =
            new DataColumnSidecarSubnetTopicProvider(
                combinedChainDataClient.getRecentChainData(), gossipEncoding);

        final MetadataMessagesFactory metadataMessagesFactory = new MetadataMessagesFactory();
        if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
          eventChannels.subscribe(CustodyGroupCountChannel.class, metadataMessagesFactory);
        }

        if (rpcEncoding == null) {
          rpcEncoding =
              RpcEncoding.createSszSnappyEncoding(
                  spec.getNetworkingConfig().getMaxPayloadSize(),
                  config.isRpcSnappyAircompressorEnabled());
        }
        final UInt256 discoveryNodeId = DISCOVERY_NODE_ID_GENERATOR.next();
        final int numberOfColumns = spec.getNumberOfDataColumns().orElse(0);
        final Set<UInt64> allColumns =
            IntStream.range(0, numberOfColumns)
                .mapToObj(UInt64::valueOf)
                .collect(Collectors.toSet());
        final CustodyGroupCountManager custodyGroupCountManager =
            new CustodyGroupCountManager() {
              @Override
              public int getCustodyGroupCount() {
                return numberOfColumns;
              }

              @Override
              public Set<UInt64> getCustodyColumnIndices() {
                return allColumns;
              }

              @Override
              public int getSamplingGroupCount() {
                return numberOfColumns;
              }

              @Override
              public Set<UInt64> getSamplingColumnIndices() {
                return allColumns;
              }
            };

        final Eth2PeerManager eth2PeerManager =
            Eth2PeerManager.create(
                asyncRunner,
                combinedChainDataClient,
                blobKzgCommitmentsProvider,
                () -> custodyGroupCountManager,
                metadataMessagesFactory,
                METRICS_SYSTEM,
                attestationSubnetService,
                syncCommitteeSubnetService,
                rpcEncoding,
                new StatusMessageFactory(spec, combinedChainDataClient, METRICS_SYSTEM),
                requiredCheckpoint,
                eth2RpcPingInterval,
                eth2RpcOutstandingPingThreshold,
                eth2StatusUpdateInterval,
                timeProvider,
                P2PConfig.DEFAULT_PEER_BLOCKS_RATE_LIMIT,
                P2PConfig.DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT,
                P2PConfig.DEFAULT_PEER_REQUEST_LIMIT,
                spec,
                __ -> Optional.of(discoveryNodeId),
                DataColumnSidecarArchiveReconstructor.NOOP,
                DasReqRespLogger.NOOP);

        List<RpcMethod<?, ?, ?>> rpcMethods =
            eth2PeerManager.getBeaconChainMethods().all().stream()
                .flatMap(rpcMethodsModifier)
                .toList();

        this.peerHandler(eth2PeerManager);

        final NoOpMetricsSystem metricsSystem = new NoOpMetricsSystem();
        final PeerPools peerPools = new PeerPools();
        final ReputationManager reputationManager =
            new DefaultReputationManager(
                metricsSystem, timeProvider, Constants.REPUTATION_MANAGER_CAPACITY, peerPools);
        final AttestationSubnetTopicProvider attestationSubnetTopicProvider =
            new AttestationSubnetTopicProvider(recentChainData, gossipEncoding);
        final SyncCommitteeSubnetTopicProvider syncCommitteeTopicProvider =
            new SyncCommitteeSubnetTopicProvider(recentChainData, gossipEncoding);
        final GossipTopicFilter gossipTopicsFilter =
            new Eth2GossipTopicFilter(recentChainData, gossipEncoding, spec, config);
        final KeyValueStore<String, Bytes> keyValueStore = new MemKeyValueStore<>();
        final DiscoveryConfig discoConfig = config.getDiscoveryConfig();
        final TargetPeerRange targetPeerRange =
            new TargetPeerRange(
                discoConfig.getMinPeers(),
                discoConfig.getMaxPeers(),
                discoConfig.getMinRandomlySelectedPeers());
        final Supplier<SpecVersion> currentSpecVersionSupplier =
            () -> combinedChainDataClient.getRecentChainData().getCurrentSpec();
        final SchemaDefinitionsSupplier currentSchemaDefinitions =
            () ->
                combinedChainDataClient
                    .getRecentChainData()
                    .getCurrentSpec()
                    .getSchemaDefinitions();
        final Supplier<Optional<UInt64>> currentSlotSupplier =
            () -> combinedChainDataClient.getRecentChainData().getCurrentSlot();
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
        final DiscoveryNetwork<?> network =
            DiscoveryNetworkBuilder.create()
                .metricsSystem(metricsSystem)
                .asyncRunner(asyncRunner)
                .kvStore(keyValueStore)
                .p2pNetwork(
                    LibP2PNetworkBuilder.create()
                        .asyncRunner(DelayedExecutorAsyncRunner.create())
                        .config(config.getNetworkConfig())
                        .networkingSpecConfig(config.getNetworkingSpecConfig())
                        .privateKeyProvider(PrivateKeyGenerator::generate)
                        .reputationManager(reputationManager)
                        .metricsSystem(METRICS_SYSTEM)
                        .rpcMethods(new ArrayList<>(rpcMethods))
                        .peerHandlers(peerHandlers)
                        .preparedGossipMessageFactory(
                            gossipEncoding.createPreparedGossipMessageFactory(
                                recentChainData::getMilestoneByForkDigest))
                        .gossipTopicFilter(gossipTopicsFilter)
                        .timeProvider(timeProvider)
                        .build())
                .peerPools(peerPools)
                .peerSelectionStrategy(
                    new Eth2PeerSelectionStrategy(
                        targetPeerRange,
                        gossipNetwork ->
                            PeerSubnetSubscriptions.create(
                                currentSpecVersionSupplier.get(),
                                NodeIdToDataColumnSidecarSubnetsCalculator.create(
                                    spec, currentSlotSupplier),
                                gossipNetwork,
                                eth2PeerManager,
                                attestationSubnetTopicProvider,
                                syncCommitteeTopicProvider,
                                syncCommitteeSubnetService,
                                dataColumnSidecarSubnetTopicProvider,
                                dataColumnSidecarSubnetService,
                                config.getTargetSubnetSubscriberCount(),
                                subnetPeerCountGauge,
                                syncCommitteeSubnetPeerCountLogger),
                        reputationManager,
                        Collections::shuffle))
                .discoveryConfig(config.getDiscoveryConfig())
                .p2pConfig(config.getNetworkConfig())
                .spec(config.getSpec())
                .timeProvider(timeProvider)
                .currentSchemaDefinitionsSupplier(currentSchemaDefinitions)
                .build();

        final GossipForkManager.Builder gossipForkManagerBuilder =
            GossipForkManager.builder().spec(spec).recentChainData(recentChainData);

        spec.getEnabledMilestones().stream()
            .map(
                forkAndSpecMilestone ->
                    createSubscriptions(
                        forkAndSpecMilestone, metricsSystem, network, gossipEncoding, config))
            .forEach(gossipForkManagerBuilder::fork);

        final GossipForkManager gossipForkManager = gossipForkManagerBuilder.build();

        return new ActiveEth2P2PNetwork(
            spec,
            asyncRunner,
            network,
            eth2PeerManager,
            gossipForkManager,
            eventChannels,
            recentChainData,
            attestationSubnetService,
            syncCommitteeSubnetService,
            dataColumnSidecarSubnetService,
            executionProofSubnetService,
            gossipEncoding,
            GossipConfigurator.NOOP,
            processedAttestationSubscriptionProvider,
            config.isAllTopicsFilterEnabled());
      }
    }

    private GossipForkSubscriptions createSubscriptions(
        final ForkAndSpecMilestone forkAndSpecMilestone,
        final NoOpMetricsSystem metricsSystem,
        final DiscoveryNetwork<?> network,
        final GossipEncoding gossipEncoding,
        final P2PConfig p2PConfig) {
      return switch (forkAndSpecMilestone.getSpecMilestone()) {
        case PHASE0 ->
            new GossipForkSubscriptionsPhase0(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                debugDataDumper);
        case ALTAIR ->
            new GossipForkSubscriptionsAltair(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                debugDataDumper);
        case BELLATRIX ->
            new GossipForkSubscriptionsBellatrix(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                debugDataDumper);
        case CAPELLA ->
            new GossipForkSubscriptionsCapella(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                signedBlsToExecutionChangeProcessor,
                debugDataDumper);
        case DENEB ->
            new GossipForkSubscriptionsDeneb(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedBlobSidecarProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                signedBlsToExecutionChangeProcessor,
                debugDataDumper);
        case ELECTRA ->
            new GossipForkSubscriptionsElectra(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedBlobSidecarProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                signedBlsToExecutionChangeProcessor,
                debugDataDumper,
                executionProofOperationProcessor,
                p2PConfig.isExecutionProofTopicEnabled());
        case FULU ->
            new GossipForkSubscriptionsFulu(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedBlobSidecarProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                signedBlsToExecutionChangeProcessor,
                dataColumnSidecarOperationProcessor,
                debugDataDumper,
                DasGossipLogger.NOOP,
                executionProofOperationProcessor,
                p2PConfig.isExecutionProofTopicEnabled(),
                isSuperNodeSupplier);
        case GLOAS, HEZE ->
            new GossipForkSubscriptionsGloas(
                forkAndSpecMilestone.getFork(),
                spec,
                asyncRunner,
                metricsSystem,
                network,
                recentChainData,
                gossipEncoding,
                gossipedBlockProcessor,
                gossipedBlobSidecarProcessor,
                gossipedAttestationProcessor,
                gossipedAggregateProcessor,
                attesterSlashingProcessor,
                proposerSlashingProcessor,
                voluntaryExitProcessor,
                signedContributionAndProofProcessor,
                syncCommitteeMessageProcessor,
                signedBlsToExecutionChangeProcessor,
                dataColumnSidecarOperationProcessor,
                executionPayloadProcessor,
                payloadAttestationMessageProcessor,
                executionPayloadBidProcessor,
                proposerPreferencesProcessor,
                debugDataDumper,
                DasGossipLogger.NOOP,
                executionProofOperationProcessor,
                p2PConfig.isExecutionProofTopicEnabled(),
                isSuperNodeSupplier);
      };
    }

    private P2PConfig generateConfig() {
      final List<String> peerAddresses =
          peers.stream().flatMap(peer -> peer.getNodeAddresses().stream()).collect(toList());

      final Random random = new Random();
      final int tcpPort = MIN_PORT + random.nextInt(MAX_PORT - MIN_PORT - 1);
      final int quicPort = tcpPort + 1;

      return P2PConfig.builder()
          .specProvider(spec)
          .targetSubnetSubscriberCount(P2PConfig.DEFAULT_P2P_TARGET_SUBNET_SUBSCRIBER_COUNT)
          .network(
              b ->
                  b.listenPort(tcpPort)
                      .listenQuicPort(quicPort)
                      .networkInterface("127.0.0.1")
                      .wireLogs(w -> w.logWireMuxFrames(true)))
          .discovery(
              d ->
                  d.isDiscoveryEnabled(false)
                      .staticPeers(peerAddresses)
                      .minPeers(20)
                      .maxPeers(30)
                      .minRandomlySelectedPeers(0))
          .build();
    }

    @SuppressWarnings("deprecation")
    private void setDefaults() {
      if (eventChannels == null) {
        eventChannels =
            EventChannels.createSyncChannels(
                (error, subscriber, invokedMethod, args) -> {
                  throw new RuntimeException(error);
                },
                new NoOpMetricsSystem());
      }
      if (asyncRunner == null) {
        asyncRunner = DelayedExecutorAsyncRunner.create();
      }
      if (eth2RpcPingInterval == null) {
        eth2RpcPingInterval =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder.DEFAULT_ETH2_RPC_PING_INTERVAL;
      }
      if (eth2StatusUpdateInterval == null) {
        eth2StatusUpdateInterval =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder
                .DEFAULT_ETH2_STATUS_UPDATE_INTERVAL;
      }
      if (eth2RpcOutstandingPingThreshold == null) {
        eth2RpcOutstandingPingThreshold =
            tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder
                .DEFAULT_ETH2_RPC_OUTSTANDING_PING_THRESHOLD;
      }
      if (recentChainData == null) {
        recentChainData = MemoryOnlyRecentChainData.create();
        ChainBuilder chainBuilder = ChainBuilder.create(spec);
        ChainUpdater chainUpdater = new ChainUpdater(recentChainData, chainBuilder);
        chainUpdater.initializeGenesis();
      }
      if (processedAttestationSubscriptionProvider == null) {
        Subscribers<ProcessedAttestationListener> subscribers = Subscribers.create(false);
        processedAttestationSubscriptionProvider = subscribers::subscribe;
      }
      if (verifiedBlockAttestationsSubscriptionProvider == null) {
        Subscribers<VerifiedBlockOperationsListener<Attestation>> subscribers =
            Subscribers.create(false);
        verifiedBlockAttestationsSubscriptionProvider = subscribers::subscribe;
      }
      if (gossipedBlockProcessor == null) {
        gossipedBlockProcessor = OperationProcessor.noop();
      }
      if (gossipedBlobSidecarProcessor == null) {
        gossipedBlobSidecarProcessor = OperationProcessor.noop();
      }
      if (gossipedAttestationProcessor == null) {
        gossipedAttestationProcessor = OperationProcessor.noop();
      }
      if (gossipedAggregateProcessor == null) {
        gossipedAggregateProcessor = OperationProcessor.noop();
      }
      if (attesterSlashingProcessor == null) {
        attesterSlashingProcessor = OperationProcessor.noop();
      }
      if (proposerSlashingProcessor == null) {
        proposerSlashingProcessor = OperationProcessor.noop();
      }
      if (voluntaryExitProcessor == null) {
        voluntaryExitProcessor = OperationProcessor.noop();
      }
      if (signedContributionAndProofProcessor == null) {
        syncCommitteeMessageProcessor = OperationProcessor.noop();
      }
      if (voluntaryExitProcessor == null) {
        voluntaryExitProcessor = OperationProcessor.noop();
      }
      if (signedBlsToExecutionChangeProcessor == null) {
        signedBlsToExecutionChangeProcessor = OperationProcessor.noop();
      }
      if (executionProofOperationProcessor == null) {
        executionProofOperationProcessor = OperationProcessor.noop();
      }
      if (executionPayloadProcessor == null) {
        executionPayloadProcessor = OperationProcessor.noop();
      }
      if (payloadAttestationMessageProcessor == null) {
        payloadAttestationMessageProcessor = OperationProcessor.noop();
      }
      if (executionPayloadBidProcessor == null) {
        executionPayloadBidProcessor = OperationProcessor.noop();
      }
      if (proposerPreferencesProcessor == null) {
        proposerPreferencesProcessor = OperationProcessor.noop();
      }
      if (isSuperNodeSupplier == null) {
        isSuperNodeSupplier = () -> false;
      }
    }

    public Eth2P2PNetworkBuilder spec(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Eth2P2PNetworkBuilder rpcEncoding(final RpcEncoding rpcEncoding) {
      checkNotNull(rpcEncoding);
      this.rpcEncoding = rpcEncoding;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipEncoding(final GossipEncoding gossipEncoding) {
      checkNotNull(gossipEncoding);
      this.gossipEncoding = gossipEncoding;
      return this;
    }

    public Eth2P2PNetworkBuilder setRequiredCheckpoint(
        final Optional<Checkpoint> requiredCheckpoint) {
      this.requiredCheckpoint = requiredCheckpoint;
      return this;
    }

    public Eth2P2PNetworkBuilder peer(final Eth2P2PNetwork peer) {
      this.peers.add(peer);
      return this;
    }

    public Eth2P2PNetworkBuilder eventChannels(final EventChannels eventChannels) {
      checkNotNull(eventChannels);
      this.eventChannels = eventChannels;
      return this;
    }

    public Eth2P2PNetworkBuilder recentChainData(final RecentChainData recentChainData) {
      checkNotNull(recentChainData);
      this.recentChainData = recentChainData;
      return this;
    }

    public Eth2P2PNetworkBuilder historicalChainData(
        final StorageQueryChannel historicalChainData) {
      checkNotNull(historicalChainData);
      this.historicalChainData = historicalChainData;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedBlockProcessor(
        final OperationProcessor<SignedBeaconBlock> gossipedBlockProcessor) {
      checkNotNull(gossipedBlockProcessor);
      this.gossipedBlockProcessor = gossipedBlockProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedBlobSidecarProcessor(
        final OperationProcessor<BlobSidecar> gossipedBlobSidecarProcessor) {
      checkNotNull(gossipedBlobSidecarProcessor);
      this.gossipedBlobSidecarProcessor = gossipedBlobSidecarProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAggregateProcessor(
        final OperationProcessor<ValidatableAttestation> gossipedAggregateProcessor) {
      checkNotNull(gossipedAggregateProcessor);
      this.gossipedAggregateProcessor = gossipedAggregateProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAttestationProcessor(
        final OperationProcessor<ValidatableAttestation> gossipedAttestationProcessor) {
      checkNotNull(gossipedAttestationProcessor);
      this.gossipedAttestationProcessor = gossipedAttestationProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedAttesterSlashingProcessor(
        final OperationProcessor<AttesterSlashing> gossipedAttesterSlashingProcessor) {
      checkNotNull(gossipedAttesterSlashingProcessor);
      this.attesterSlashingProcessor = gossipedAttesterSlashingProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedProposerSlashingProcessor(
        final OperationProcessor<ProposerSlashing> gossipedProposerSlashingProcessor) {
      checkNotNull(gossipedProposerSlashingProcessor);
      this.proposerSlashingProcessor = gossipedProposerSlashingProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedVoluntaryExitProcessor(
        final OperationProcessor<SignedVoluntaryExit> gossipedVoluntaryExitProcessor) {
      checkNotNull(gossipedVoluntaryExitProcessor);
      this.voluntaryExitProcessor = gossipedVoluntaryExitProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSignedContributionAndProofProcessor(
        final OperationProcessor<SignedContributionAndProof>
            gossipedSignedContributionAndProofProcessor) {
      checkNotNull(gossipedSignedContributionAndProofProcessor);
      this.signedContributionAndProofProcessor = gossipedSignedContributionAndProofProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSyncCommitteeMessageProcessor(
        final OperationProcessor<ValidatableSyncCommitteeMessage> gossipedSyncCommitteeProcessor) {
      checkNotNull(gossipedSyncCommitteeProcessor);
      this.syncCommitteeMessageProcessor = gossipedSyncCommitteeProcessor;
      return this;
    }

    public Eth2P2PNetworkBuilder gossipedSignedBlsToExecutionChangeProcessor(
        final OperationProcessor<SignedBlsToExecutionChange>
            gossipedSignedBlsToExecutionChangeProcessor) {
      checkNotNull(gossipedSignedBlsToExecutionChangeProcessor);
      this.signedBlsToExecutionChangeProcessor = gossipedSignedBlsToExecutionChangeProcessor;
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
        final OperationProcessor<SignedExecutionPayloadEnvelope>
            gossipedExecutionPayloadProcessor) {
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

    public Eth2P2PNetworkBuilder processedAttestationSubscriptionProvider(
        final ProcessedAttestationSubscriptionProvider processedAttestationSubscriptionProvider) {
      checkNotNull(processedAttestationSubscriptionProvider);
      this.processedAttestationSubscriptionProvider = processedAttestationSubscriptionProvider;
      return this;
    }

    public Eth2P2PNetworkBuilder verifiedBlockAttestationsSubscriptionProvider(
        final VerifiedBlockAttestationsSubscriptionProvider
            verifiedBlockAttestationsSubscriptionProvider) {
      checkNotNull(verifiedBlockAttestationsSubscriptionProvider);
      this.verifiedBlockAttestationsSubscriptionProvider =
          verifiedBlockAttestationsSubscriptionProvider;
      return this;
    }

    public Eth2P2PNetworkBuilder rpcMethodsModifier(
        final Function<RpcMethod<?, ?, ?>, Stream<RpcMethod<?, ?, ?>>> rpcMethodsModifier) {
      checkNotNull(rpcMethodsModifier);
      this.rpcMethodsModifier = rpcMethodsModifier;
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

    public Eth2P2PNetworkBuilder eth2StatusUpdateInterval(final Duration eth2StatusUpdateInterval) {
      checkNotNull(eth2StatusUpdateInterval);
      this.eth2StatusUpdateInterval = eth2StatusUpdateInterval;
      return this;
    }

    public Eth2P2PNetworkBuilder p2pDebugDataDumper(final DebugDataDumper debugDataDumper) {
      checkNotNull(debugDataDumper);
      this.debugDataDumper = debugDataDumper;
      return this;
    }

    public Eth2P2PNetworkBuilder isSuperNodeSupplier(final Supplier<Boolean> isSuperNodeSupplier) {
      checkNotNull(isSuperNodeSupplier);
      this.isSuperNodeSupplier = isSuperNodeSupplier;
      return this;
    }
  }
}
