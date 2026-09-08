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

package tech.pegasys.teku.services.beaconchain;

import static tech.pegasys.teku.infrastructure.exceptions.ExitConstants.FATAL_EXIT_CODE;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_KZG_PRECOMPUTE;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_KZG_PRECOMPUTE_SUPERNODE;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MIN_WAIT_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_TARGET_WAIT_MILLIS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.beacon.sync.DefaultSyncServiceFactory;
import tech.pegasys.teku.beacon.sync.SyncConfig;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.SyncServiceFactory;
import tech.pegasys.teku.beacon.sync.events.CoalescingChainHeadChannel;
import tech.pegasys.teku.beacon.sync.events.SyncPreImportBlockChannel;
import tech.pegasys.teku.beacon.sync.gossip.blobs.RecentBlobSidecarsFetcher;
import tech.pegasys.teku.beacon.sync.gossip.blocks.RecentBlocksFetcher;
import tech.pegasys.teku.beacon.sync.gossip.executionpayloads.RecentExecutionPayloadsFetcher;
import tech.pegasys.teku.beaconrestapi.BeaconRestApi;
import tech.pegasys.teku.beaconrestapi.JsonTypeDefinitionBeaconRestApi;
import tech.pegasys.teku.builder.rest.StakedBuilderClientProvider;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.dataproviders.lookup.UnblindingExecutionPayloadProvider;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionChannel;
import tech.pegasys.teku.ethereum.executionclient.ExecutionClientVersionProvider;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionMetrics;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.PortAvailability;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionProofGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSubnetsSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AllSyncCommitteeSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetBackboneSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.NodeBasedStableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.StableSubnetSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.networking.eth2.mock.NoOpEth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.DataColumnPeerManagerImpl;
import tech.pegasys.teku.networking.eth2.peers.MetadataDasPeerCustodyTracker;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.networks.StateBoostrapConfig;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.executionlayer.ExecutionLayerBlockManagerFactory;
import tech.pegasys.teku.services.timer.TimerService;
import tech.pegasys.teku.services.zkchain.ZkChainConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.EpochCachePrimer;
import tech.pegasys.teku.statetransition.LocalOperationAcceptedFilter;
import tech.pegasys.teku.statetransition.MappedOperationPool;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.OperationsReOrgManager;
import tech.pegasys.teku.statetransition.SimpleOperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolV2;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfilerCSV;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManagerImpl;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.blobs.BlockEventsListenerRouter;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.block.BlockImportMetrics;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.BlockManager;
import tech.pegasys.teku.statetransition.block.FailedExecutionPool;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;
import tech.pegasys.teku.statetransition.datacolumns.CanonicalBlockResolver;
import tech.pegasys.teku.statetransition.datacolumns.CurrentSlotProvider;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManagerImpl;
import tech.pegasys.teku.statetransition.datacolumns.DasCustodyBackfiller;
import tech.pegasys.teku.statetransition.datacolumns.DasPreSampler;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasic;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasicImpl;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerManager;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructorImpl;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarCustodyImpl;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManagerImpl;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarRecoveringCustody;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarRecoveringCustodyImpl;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDB;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipBatchLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.LoggingBatchDataColumnsByRootReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRootReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DasPeerCustodyCountSupplier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnReqRespBatchingImpl;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.retriever.SimpleSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.SidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.util.SuperNodeSupplier;
import tech.pegasys.teku.statetransition.execution.BuilderBidFetcher;
import tech.pegasys.teku.statetransition.execution.DefaultExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.DefaultExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.DefaultProposerPreferencesManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidCircuitBreaker;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBidOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidSelector;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.FailedExecutionPayloadPool;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofGenerator;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofGeneratorImpl;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManagerImpl;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierImpl;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.forkchoice.TerminalPowBlockMonitor;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessingPerformance;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationEventChannel;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.lightclient.LightClientServerService;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.statetransition.payloadattestation.AggregatingPayloadAttestationPool;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationMessageGossipValidator;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessageValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeStateUtils;
import tech.pegasys.teku.statetransition.util.BlockBlobSidecarsTrackersPoolImpl;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.util.DebugDataFileDumper;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingBlockPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttesterSlashingValidator;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.BlockValidator;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;
import tech.pegasys.teku.statetransition.validation.ProposerSlashingValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.statetransition.validation.signatures.AggregatingSignatureVerificationService;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.CombinedStorageChannel;
import tech.pegasys.teku.storage.api.DataColumnSidecarNetworkRetriever;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.SidecarArchivePrunableChannel;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.ThrottlingStorageQueryChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.client.StorageBackedRecentChainData;
import tech.pegasys.teku.storage.client.ValidatorIsConnectedProvider;
import tech.pegasys.teku.storage.server.DataColumnSidecarNetworkRetrieverImpl;
import tech.pegasys.teku.storage.store.FileKeyValueStore;
import tech.pegasys.teku.storage.store.KeyValueStore;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactory;
import tech.pegasys.teku.validator.coordinator.DepositProvider;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;
import tech.pegasys.teku.validator.coordinator.Eth1DataProvider;
import tech.pegasys.teku.validator.coordinator.Eth1VotingPeriod;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactoryGloas;
import tech.pegasys.teku.validator.coordinator.FutureBlockProductionPreparationTrigger;
import tech.pegasys.teku.validator.coordinator.GraffitiBuilder;
import tech.pegasys.teku.validator.coordinator.MilestoneBasedBlockFactory;
import tech.pegasys.teku.validator.coordinator.StoredLatestCanonicalBlockUpdater;
import tech.pegasys.teku.validator.coordinator.ValidatorApiHandler;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.NoOpPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.SyncCommitteePerformanceTracker;
import tech.pegasys.teku.validator.coordinator.performance.ValidatorPerformanceMetrics;
import tech.pegasys.teku.validator.coordinator.publisher.BlockPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisherGloas;
import tech.pegasys.teku.validator.coordinator.publisher.MilestoneBasedBlockPublisher;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityCalculator;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

/**
 * The central class which assembles together and initializes Beacon Chain components
 *
 * <p>CAUTION: This class can be overridden by custom implementation to tweak creation and
 * initialization behavior (see {@link BeaconChainControllerFactory}} however this class may change
 * in a backward incompatible manner and either break compilation or runtime behavior
 */
public class BeaconChainController extends Service implements BeaconChainControllerFacade {

  private static final Logger LOG = LogManager.getLogger();
  private final EphemerySlotValidationService ephemerySlotValidationService;

  protected static final String KEY_VALUE_STORE_SUBDIRECTORY = "kvstore";

  protected volatile BeaconChainConfiguration beaconConfig;
  protected volatile Spec spec;
  protected volatile Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier;
  protected volatile EventChannels eventChannels;
  protected volatile MetricsSystem metricsSystem;
  protected volatile AsyncRunner beaconAsyncRunner;
  protected volatile TimeProvider timeProvider;
  protected volatile SlotEventsChannel slotEventsChannelPublisher;
  protected volatile ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher;
  protected volatile AsyncRunner networkAsyncRunner;
  protected volatile Optional<AsyncRunner> executionProofAsyncRunner;
  protected volatile AsyncRunnerFactory asyncRunnerFactory;
  protected volatile AsyncRunner eventAsyncRunner;
  protected volatile Path beaconDataDirectory;
  protected volatile WeakSubjectivityInitializer wsInitializer = new WeakSubjectivityInitializer();
  protected volatile AsyncRunnerEventThread forkChoiceExecutor;
  protected volatile CustodyGroupCountManager custodyGroupCountManager;

  private final AsyncRunner operationPoolAsyncRunner;
  private final AsyncRunner dasAsyncRunner;
  private final FastConfirmationTracker fastConfirmationTracker;
  protected final AtomicReference<DataColumnSidecarRecoveringCustody> dataColumnSidecarCustodyRef =
      new AtomicReference<>(DataColumnSidecarRecoveringCustody.NOOP);

  protected volatile ForkChoice forkChoice;
  protected volatile ForkChoiceTrigger forkChoiceTrigger;
  protected volatile BlockImporter blockImporter;
  protected volatile DataProvider dataProvider;
  protected volatile RecentChainData recentChainData;
  protected volatile Eth2P2PNetwork p2pNetwork;
  protected volatile Optional<BeaconRestApi> beaconRestAPI = Optional.empty();
  protected volatile AggregatingAttestationPool aggregatingAttestationPool;
  protected volatile DepositProvider depositProvider;
  protected volatile SyncService syncService;
  protected volatile AttestationManager attestationManager;
  protected volatile SignatureVerificationService signatureVerificationService;
  protected volatile CombinedChainDataClient combinedChainDataClient;
  protected volatile BlobKzgCommitmentsProvider blobKzgCommitmentsProvider;
  protected volatile OperationsReOrgManager operationsReOrgManager;
  protected volatile Eth1DataCache eth1DataCache;
  protected volatile SlotProcessor slotProcessor;
  protected volatile OperationPool<AttesterSlashing> attesterSlashingPool;
  protected volatile OperationPool<ProposerSlashing> proposerSlashingPool;
  protected volatile OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  protected volatile MappedOperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;
  protected volatile SyncCommitteeContributionPool syncCommitteeContributionPool;
  protected volatile SyncCommitteeMessagePool syncCommitteeMessagePool;
  protected volatile PayloadAttestationPool payloadAttestationPool;
  protected volatile WeakSubjectivityValidator weakSubjectivityValidator;
  protected volatile PerformanceTracker performanceTracker;
  protected volatile PendingPool<SignedBeaconBlock> pendingBlocks;
  protected volatile PendingBlockPool pendingBlockPool;
  protected volatile PendingAttestationPool pendingAttestationPool;
  protected volatile PendingPool<PayloadAttestationMessage> pendingPayloadAttestations;
  protected volatile BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
  protected volatile DataColumnSidecarELManager dataColumnSidecarELManager;
  protected volatile Map<Bytes32, BlockImportResult> invalidBlockRoots;
  protected volatile Set<Bytes32> blockRootsWithInvalidExecutionPayload;
  protected volatile CoalescingChainHeadChannel coalescingChainHeadChannel;
  protected volatile ActiveValidatorTracker activeValidatorTracker;
  protected volatile AttestationTopicSubscriber attestationTopicSubscriber;
  protected volatile ForkChoiceNotifier forkChoiceNotifier;
  protected volatile ForkChoiceStateProvider forkChoiceStateProvider;
  protected volatile ExecutionLayerChannel executionLayer;
  protected volatile GossipValidationHelper gossipValidationHelper;
  protected volatile DasGossipLogger dasGossipLogger;
  protected volatile DasReqRespLogger dasReqRespLogger;
  protected volatile LightClientUpdateStore lightClientUpdateStore;
  protected volatile LightClientServerService lightClientServerService;
  protected volatile KZG kzg;
  protected volatile BlobSidecarManager blobSidecarManager;
  protected volatile BlobSidecarGossipValidator blobSidecarValidator;
  protected volatile DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator;
  protected volatile DataColumnSidecarManager dataColumnSidecarManager;
  protected volatile ProposerPreferencesManager proposerPreferencesManager;
  protected volatile ExecutionPayloadBidManager executionPayloadBidManager;
  protected volatile ExecutionPayloadManager executionPayloadManager;
  protected volatile ExecutionProofManager executionProofManager;
  protected volatile BlockGossipValidator blockGossipValidator;
  protected volatile Optional<DataColumnSidecarDB> sidecarDB = Optional.empty();
  protected volatile Optional<DasCustodyBackfiller> dasCustodyBackfiller = Optional.empty();
  protected volatile Optional<DataColumnSidecarRetriever> recoveringSidecarRetriever =
      Optional.empty();
  protected volatile Optional<DataColumnSidecarRetriever> simpleSidecarRetriever = Optional.empty();
  protected volatile AvailabilityCheckerFactory<UInt64> dasSamplerManager;
  protected volatile DasSamplerBasic dasSamplerBasic = DasSamplerBasic.NOOP;
  protected volatile DataAvailabilitySampler dataAvailabilitySampler;
  protected volatile Optional<TerminalPowBlockMonitor> terminalPowBlockMonitor = Optional.empty();
  protected volatile ProposersDataManager proposersDataManager;
  protected volatile KeyValueStore<String, Bytes> keyValueStore;
  protected volatile StorageQueryChannel storageQueryChannel;
  protected volatile ExecutionPayloadProvider executionPayloadProvider;
  protected volatile StorageUpdateChannel storageUpdateChannel;
  protected volatile SyncPreImportBlockChannel syncPreImportBlockChannel;
  protected volatile StableSubnetSubscriber stableSubnetSubscriber;
  protected volatile ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;
  protected volatile RewardCalculator rewardCalculator;
  protected UInt64 genesisTimeTracker = ZERO;
  protected BlockManager blockManager;
  protected Service timerService;
  protected PoolFactory poolFactory;
  protected SettableLabelledGauge futureItemsMetric;
  protected IntSupplier rejectedExecutionCountSupplier;
  protected DebugDataDumper debugDataDumper;
  protected Path debugDataDirectory;
  protected volatile UInt256 nodeId;
  protected volatile BlobSidecarReconstructionProvider blobSidecarReconstructionProvider;
  protected volatile BlobReconstructionProvider blobReconstructionProvider;
  protected volatile ValidatorApiHandler validatorApiHandler;

  public BeaconChainController(
      final ServiceConfig serviceConfig, final BeaconChainConfiguration beaconConfig) {
    final Eth2NetworkConfiguration eth2NetworkConfig = beaconConfig.eth2NetworkConfig();
    final DataDirLayout dataDirLayout = serviceConfig.getDataDirLayout();
    this.beaconConfig = beaconConfig;
    this.spec = beaconConfig.getSpec();
    this.beaconBlockSchemaSupplier =
        slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
    this.beaconDataDirectory = dataDirLayout.getBeaconDataDirectory();
    this.asyncRunnerFactory = serviceConfig.getAsyncRunnerFactory();
    this.beaconAsyncRunner =
        serviceConfig.createAsyncRunner(
            "beaconchain",
            eth2NetworkConfig.getAsyncBeaconChainMaxThreads(),
            eth2NetworkConfig.getAsyncBeaconChainMaxQueue());
    this.eventAsyncRunner = serviceConfig.createAsyncRunner("events", 10);
    this.networkAsyncRunner =
        serviceConfig.createAsyncRunner(
            "p2p",
            eth2NetworkConfig.getAsyncP2pMaxThreads(),
            eth2NetworkConfig.getAsyncP2pMaxQueue());
    this.executionProofAsyncRunner =
        beaconConfig.zkChainConfiguration().statelessValidationEnabled()
            ? Optional.ofNullable(serviceConfig.createAsyncRunner("executionproof", 1))
            : Optional.empty();
    this.operationPoolAsyncRunner = serviceConfig.createAsyncRunner("operationPoolUpdater", 1);
    // there are several operations that may be performed in the das runner, so it has more threads,
    // larger default size. das runner should be separate to the operation pool runner as it's a
    // bunch of tasks, not just operation pool activities
    this.dasAsyncRunner = serviceConfig.createAsyncRunner("das", 4, 20_000);
    if (eth2NetworkConfig.isFastConfirmationEnabled()) {
      final AsyncRunner fastConfirmationAsyncRunner =
          serviceConfig.createAsyncRunner("fastconfirmation", 1);
      this.fastConfirmationTracker =
          FastConfirmationTracker.create(
              spec,
              Optional.of(fastConfirmationAsyncRunner),
              serviceConfig.getEventChannels().getPublisher(FastConfirmationEventChannel.class),
              serviceConfig.getMetricsSystem(),
              serviceConfig.getTimeProvider());
    } else {
      this.fastConfirmationTracker = FastConfirmationTracker.NOOP;
    }
    this.timeProvider = serviceConfig.getTimeProvider();
    this.eventChannels = serviceConfig.getEventChannels();
    this.metricsSystem = serviceConfig.getMetricsSystem();
    this.poolFactory = new PoolFactory(this.metricsSystem);
    this.rejectedExecutionCountSupplier = serviceConfig.getRejectedExecutionsSupplier();
    this.slotEventsChannelPublisher = eventChannels.getPublisher(SlotEventsChannel.class);
    this.receivedBlockEventsChannelPublisher =
        eventChannels.getPublisher(ReceivedBlockEventsChannel.class);
    this.forkChoiceExecutor = new AsyncRunnerEventThread("forkchoice", asyncRunnerFactory);
    this.debugDataDumper =
        dataDirLayout.isDebugDataDumpingEnabled()
            ? new DebugDataFileDumper(dataDirLayout.getDebugDataDirectory())
            : DebugDataDumper.NOOP;
    this.futureItemsMetric =
        SettableLabelledGauge.create(
            metricsSystem,
            BEACON,
            "future_items_size",
            "Current number of items held for future slots, labelled by type",
            "type");
    this.dasGossipLogger = new DasGossipBatchLogger(dasAsyncRunner, timeProvider);
    this.dasReqRespLogger = DasReqRespLogger.create(timeProvider);
    final SpecConfig genesisConfig = spec.getGenesisSpec().getConfig();
    this.ephemerySlotValidationService =
        new EphemerySlotValidationService(
            genesisConfig.getEphemeryResetPeriod(), genesisConfig.getSecondsPerSlot());
    this.debugDataDirectory = serviceConfig.getDataDirLayout().getDebugDataDirectory();
  }

  @Override
  protected SafeFuture<?> doStart() {
    LOG.debug("Starting {}", this.getClass().getSimpleName());
    forkChoiceExecutor.start();
    return initialize()
        .thenCompose(
            (__) ->
                beaconRestAPI.map(BeaconRestApi::start).orElse(SafeFuture.completedFuture(null)));
  }

  protected void startServices() {
    final RecentBlocksFetcher recentBlocksFetcher = syncService.getRecentBlocksFetcher();
    recentBlocksFetcher.subscribeBlockFetched(
        block -> {
          // Make RPC-fetched block commitments visible before import can trigger sidecar
          // validation.
          blobKzgCommitmentsProvider.onNewBlock(block);
          blockManager
              .importBlock(block, RemoteOrigin.RPC)
              .thenCompose(BlockImportAndBroadcastValidationResults::blockImportResult)
              .finish(err -> LOG.error("Failed to process recently fetched block.", err));
        });
    eventChannels.subscribe(ReceivedBlockEventsChannel.class, recentBlocksFetcher);
    final RecentBlobSidecarsFetcher recentBlobSidecarsFetcher =
        syncService.getRecentBlobSidecarsFetcher();
    recentBlobSidecarsFetcher.subscribeBlobSidecarFetched(
        (blobSidecar) -> blobSidecarManager.prepareForBlockImport(blobSidecar, RemoteOrigin.RPC));
    blobSidecarManager.subscribeToReceivedBlobSidecar(
        blobSidecar ->
            recentBlobSidecarsFetcher.cancelRecentBlobSidecarRequest(
                new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex())));
    executionProofManager.subscribeToValidExecutionProofs(
        (executionProof, remoteOrigin) ->
            // TODO add actual logic to handle valid execution proofs
            LOG.debug("Received valid execution proof: {}", executionProof));
    final RecentExecutionPayloadsFetcher recentExecutionPayloadsFetcher =
        syncService.getRecentExecutionPayloadsFetcher();
    recentExecutionPayloadsFetcher.subscribeExecutionPayloadFetched(
        executionPayload ->
            executionPayloadManager
                .importExecutionPayload(executionPayload, false)
                .finish(
                    err ->
                        LOG.error("Failed to process recently fetched execution payload.", err)));
    pendingAttestationPool.subscribeRequiredFullPayload(
        recentExecutionPayloadsFetcher::requestRecentExecutionPayload);
    blockManager.subscribeRequiredParentExecutionPayload(
        parentExecutionPayloadDependency ->
            recentExecutionPayloadsFetcher.requestRecentExecutionPayload(
                parentExecutionPayloadDependency.parentBeaconBlockRoot()));
    eventChannels.subscribe(
        ReceivedExecutionPayloadEventsChannel.class, recentExecutionPayloadsFetcher);

    final Optional<Eth2Network> network = beaconConfig.eth2NetworkConfig().getEth2Network();
    if (network.isPresent() && network.get() == Eth2Network.EPHEMERY) {
      LOG.debug("BeaconChainController: subscribing to slot events");
      eventChannels.subscribe(SlotEventsChannel.class, ephemerySlotValidationService);
    }
    SafeFuture.allOfFailFast(
            forkChoice.getFastConfirmationService().start(),
            attestationManager.start(),
            p2pNetwork.start(),
            blockManager.start(),
            syncService.start(),
            dasCustodyBackfiller.isPresent()
                ? dasCustodyBackfiller.get().start()
                : SafeFuture.COMPLETE,
            SafeFuture.fromRunnable(
                () -> {
                  terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::start);
                  recoveringSidecarRetriever.ifPresent(DataColumnSidecarRetriever::start);
                }))
        .finish(
            error -> {
              Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof BindException) {
                final String errorWhilePerformingDescription =
                    "starting P2P services on port(s) "
                        + p2pNetwork.getListenPorts().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(","))
                        + ".";
                STATUS_LOG.fatalError(errorWhilePerformingDescription, rootCause);
                System.exit(FATAL_EXIT_CODE);
              } else {
                Thread.currentThread()
                    .getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), error);
              }
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    LOG.debug("Stopping {}", this.getClass().getSimpleName());
    return SafeFuture.allOf(
            // Stopped before the fork-choice executor below, so no in-flight fast confirmation
            // work is submitted to a stopped event thread and logged as an error.
            forkChoice.getFastConfirmationService().stop(),
            beaconRestAPI.map(BeaconRestApi::stop).orElse(SafeFuture.completedFuture(null)),
            syncService.stop(),
            blockManager.stop(),
            attestationManager.stop(),
            p2pNetwork.stop(),
            timerService.stop(),
            ephemerySlotValidationService.doStop(),
            dasCustodyBackfiller.isPresent()
                ? dasCustodyBackfiller.get().stop()
                : SafeFuture.COMPLETE,
            SafeFuture.fromRunnable(
                () -> {
                  terminalPowBlockMonitor.ifPresent(TerminalPowBlockMonitor::stop);
                  recoveringSidecarRetriever.ifPresent(DataColumnSidecarRetriever::stop);
                }))
        .thenRun(forkChoiceExecutor::stop);
  }

  protected SafeFuture<?> initialize() {
    final StoreConfig storeConfig = beaconConfig.storeConfig();
    coalescingChainHeadChannel =
        new CoalescingChainHeadChannel(
            eventChannels.getPublisher(ChainHeadChannel.class), EVENT_LOG);
    timerService = new TimerService(this::onTick);

    final CombinedStorageChannel combinedStorageChannel =
        eventChannels.getPublisher(CombinedStorageChannel.class, beaconAsyncRunner);
    storageQueryChannel = combinedStorageChannel;
    storageUpdateChannel = combinedStorageChannel;
    final VoteUpdateChannel voteUpdateChannel = eventChannels.getPublisher(VoteUpdateChannel.class);

    final ValidatorIsConnectedProvider validatorIsConnectedProvider =
        new ValidatorIsConnectedProviderReference(() -> proposersDataManager);

    final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider =
        createBlindedExecutionPayloadProvider(storageQueryChannel);

    executionPayloadProvider = createExecutionPayloadProvider(blindedExecutionPayloadProvider);

    // Used to optimize the case where we receive a block and are still importing its blobs/data
    // columns,
    // in the meantime we can serve the block if we receive an RPC request
    final SingleBlockProvider singleBlockProviderResolver =
        new SingleBlockProviderResolver(
            (blockRoot) -> blockBlobSidecarsTrackersPool.getBlock(blockRoot),
            (blockRoot) -> dasSamplerBasic.getBlock(blockRoot));

    // Init other services
    return initWeakSubjectivity(storageQueryChannel, storageUpdateChannel)
        .thenCompose(
            __ ->
                StorageBackedRecentChainData.create(
                    metricsSystem,
                    storeConfig,
                    beaconAsyncRunner,
                    singleBlockProviderResolver,
                    (blockRoot, index) ->
                        blockBlobSidecarsTrackersPool.getBlobSidecar(blockRoot, index),
                    blockRoot -> dasSamplerBasic.getSlotForBlockRoot(blockRoot),
                    executionPayloadProvider,
                    blindedExecutionPayloadProvider,
                    storageQueryChannel,
                    storageUpdateChannel,
                    voteUpdateChannel,
                    eventChannels.getPublisher(FinalizedCheckpointChannel.class, beaconAsyncRunner),
                    coalescingChainHeadChannel,
                    validatorIsConnectedProvider,
                    spec))
        .thenCompose(
            client -> {
              if (isAllowSyncOutsideWeakSubjectivityPeriod()) {
                STATUS_LOG.warnIgnoringWeakSubjectivityPeriod();
              }

              // Setup chain storage
              this.recentChainData = client;
              if (recentChainData.isPreGenesis()) {
                setupInitialState(client);
              } else {
                if (isUsingCustomInitialState()) {
                  STATUS_LOG.warnInitialStateIgnored();
                }
                if (!isAllowSyncOutsideWeakSubjectivityPeriod()) {
                  validateWeakSubjectivityPeriod(client);
                }
              }
              return SafeFuture.completedFuture(client);
            })
        // Init other services
        .thenRun(this::initAll)
        .thenRun(
            () -> {
              // complete spec initialization
              spec.initialize(blobSidecarManager, dasSamplerManager, kzg);

              recentChainData.subscribeStoreInitialized(this::onStoreInitialized);
              recentChainData.subscribeBestBlockInitialized(this::startServices);
            })
        .thenCompose(__ -> timerService.start());
  }

  private boolean isUsingCustomInitialState() {
    return beaconConfig.eth2NetworkConfig().getNetworkBoostrapConfig().isUsingCustomInitialState();
  }

  private boolean isAllowSyncOutsideWeakSubjectivityPeriod() {
    return beaconConfig
        .eth2NetworkConfig()
        .getNetworkBoostrapConfig()
        .isAllowSyncOutsideWeakSubjectivityPeriod();
  }

  private void validateWeakSubjectivityPeriod(final RecentChainData client) {
    final AnchorPoint latestFinalizedAnchor = client.getStore().getLatestFinalized();
    final UInt64 currentSlot = getCurrentSlot(client.getGenesisTime());
    wsInitializer.validateAnchorIsWithinWeakSubjectivityPeriod(
        latestFinalizedAnchor, currentSlot, spec, WeakSubjectivityCalculator.create(spec));
  }

  public void initAll() {
    initKeyValueStore();
    initExecutionLayer();
    initExecutionLayerBlockProductionManager();
    initRewardCalculator();
    initGossipValidationHelper();
    initPendingPoolsAndCaches();
    initKzg();
    initBlockBlobSidecarsTrackersPool();
    initBlobSidecarManager();
    initDasSamplerManager();
    initDataColumnSidecarManager();
    initZkChain();
    initForkChoiceStateProvider();
    initProposerPreferencesManager();
    initForkChoiceNotifier();
    initMergeMonitors();
    initSignatureVerificationService();
    initForkChoice();
    initBlockImporter();
    initCombinedChainDataClient();
    initBlobKzgCommitmentsProvider();
    initLightClientUpdateStore();
    initLightClientServerService();

    initAggregatingAttestationPool();
    initAttesterSlashingPool();
    initProposerSlashingPool();
    initVoluntaryExitPool();
    initSignedBlsToExecutionChangePool();
    initPayloadAttestationPool();
    initEth1DataCache();
    initDepositProvider();
    initGenesisHandler();
    initAttestationManager();
    initBlockManager();
    initExecutionPayloadBidManager();
    initExecutionPayloadManager();
    initSyncCommitteePools();
    initP2PNetwork();
    initCustodyGroupCountManager();
    initDasCustody();
    initDataColumnSidecarELManager();
    initDasSyncPreSampler();
    completeDasClassesWiring();
    initSyncService();
    initSlotProcessor();
    initMetrics();
    initAttestationTopicSubscriber();
    initActiveValidatorTracker();
    initSubnetSubscriber();
    initDataColumnSidecarSubnetBackboneSubscriber();
    initSlashingEventsSubscriptions();
    initPerformanceTracker();
    initBlobSidecarReconstructionProvider();
    initBlobReconstructionProvider();
    initDataProvider();
    initValidatorApiHandler();
    initRestAPI();
    initOperationsReOrgManager();
    initStoredLatestCanonicalBlockUpdater();
  }

  private void initKeyValueStore() {
    keyValueStore =
        new FileKeyValueStore(beaconDataDirectory.resolve(KEY_VALUE_STORE_SUBDIRECTORY));
  }

  protected BlindedExecutionPayloadProvider createBlindedExecutionPayloadProvider(
      final StorageQueryChannel storageQueryChannel) {
    if (!spec.supportsExecutionPayloadEnvelopes()) {
      return BlindedExecutionPayloadProvider.NOOP;
    }
    return storageQueryChannel::getBlindedExecutionPayloadEnvelopesByBlockRoot;
  }

  protected ExecutionPayloadProvider createExecutionPayloadProvider(
      final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider) {
    if (!spec.supportsExecutionPayloadEnvelopes()) {
      return ExecutionPayloadProvider.NOOP;
    }
    return new UnblindingExecutionPayloadProvider(
        spec,
        blindedExecutionPayloadProvider,
        blockHashes -> executionLayer.engineGetPayloadBodiesByHash(blockHashes));
  }

  protected void initExecutionLayer() {
    executionLayer = eventChannels.getPublisher(ExecutionLayerChannel.class, beaconAsyncRunner);
  }

  protected void initZkChain() {
    LOG.debug("BeaconChainController.initZkChain()");
    final ZkChainConfiguration zkConfig = beaconConfig.zkChainConfiguration();
    // TODO: We will eventually need the Gossip in the EP Manager for publishing the proofs we
    // produce?
    // comment for now this will be used in the future
    if (zkConfig.statelessValidationEnabled()) {
      final ExecutionProofGossipChannel executionProofGossipChannel =
          eventChannels.getPublisher(ExecutionProofGossipChannel.class, networkAsyncRunner);
      final ExecutionProofGossipValidator executionProofGossipValidator =
          ExecutionProofGossipValidator.create();
      final SpecVersion specVersionElectra = spec.forMilestone(SpecMilestone.ELECTRA);
      final SchemaDefinitionsElectra schemaDefinitionsElectra =
          SchemaDefinitionsElectra.required(specVersionElectra.getSchemaDefinitions());
      final ExecutionProofGenerator executionProofGenerator =
          new ExecutionProofGeneratorImpl(schemaDefinitionsElectra);

      executionProofManager =
          new ExecutionProofManagerImpl(
              executionProofGossipValidator,
              executionProofGenerator,
              executionProofGossipChannel::publishExecutionProof,
              zkConfig.generateExecutionProofsEnabled(),
              zkConfig.statelessMinProofsRequired(),
              zkConfig.proofDelayDurationInMs(),
              executionProofAsyncRunner.get());

    } else {
      executionProofManager = ExecutionProofManager.NOOP;
    }
  }

  protected void initKzg() {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      kzg = KZG.getInstance(beaconConfig.eth2NetworkConfig().isRustKzgEnabled());
      final String trustedSetupFile =
          beaconConfig
              .eth2NetworkConfig()
              .getTrustedSetup()
              .orElseThrow(
                  () ->
                      new InvalidConfigurationException(
                          "Trusted setup should be configured when Deneb is enabled"));

      final int kzgPrecompute =
          beaconConfig
              .eth2NetworkConfig()
              .getKzgPrecompute()
              .orElseGet(
                  () -> {
                    // Default to a different value if this is a supernode
                    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
                      final SpecVersion specVersionFulu = spec.forMilestone(SpecMilestone.FULU);
                      final int totalCustodyGroups =
                          beaconConfig.p2pConfig().getTotalCustodyGroupCount(specVersionFulu);
                      final int numberOfColumns =
                          SpecConfigFulu.required(specVersionFulu.getConfig()).getNumberOfColumns();
                      if (totalCustodyGroups == numberOfColumns) {
                        return DEFAULT_KZG_PRECOMPUTE_SUPERNODE;
                      }
                    }
                    return DEFAULT_KZG_PRECOMPUTE;
                  });

      if (kzgPrecompute <= DEFAULT_KZG_PRECOMPUTE_SUPERNODE) {
        // By default, run loadTrustedSetup on the current thread with a 1MB stack
        kzg.loadTrustedSetup(trustedSetupFile, kzgPrecompute);
      } else {
        // Higher kzgPrecompute values require a larger stack. If the --Xkzg-precompute flag
        // is used to specify a higher kzgPrecompute value, run loadTrustedSetup on a
        // dedicated thread with an 8MB stack.
        final long stackSize = 8 * 1024 * 1024; // 8MB
        final AtomicReference<Throwable> loadException = new AtomicReference<>();
        final Thread loaderThread =
            new Thread(
                null,
                () -> {
                  try {
                    kzg.loadTrustedSetup(trustedSetupFile, kzgPrecompute);
                  } catch (final Throwable t) {
                    loadException.set(t);
                  }
                },
                "kzg-loader",
                stackSize);
        loaderThread.start();
        try {
          loaderThread.join(Duration.ofMinutes(5).toMillis());
          if (loaderThread.isAlive()) {
            loaderThread.interrupt();
            throw new RuntimeException("KZG trusted setup loading timed out after five minutes");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("KZG trusted setup loading was interrupted", e);
        }
        if (loadException.get() != null) {
          throw new RuntimeException("Failed to load KZG trusted setup", loadException.get());
        }
      }
    } else {
      kzg = KZG.DISABLED;
    }
  }

  protected void initBlobSidecarManager() {
    if (spec.isMilestoneSupported(SpecMilestone.DENEB) && !isSafeToDeactivateDenebFeatures()) {
      final FutureItems<BlobSidecar> futureBlobSidecars =
          FutureItems.create(BlobSidecar::getSlot, futureItemsMetric, "blob_sidecars");

      final Map<Bytes32, InternalValidationResult> invalidBlobSidecarRoots =
          LimitedMap.createSynchronizedLRU(500);
      final MiscHelpersDeneb miscHelpers =
          MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
      blobSidecarValidator =
          BlobSidecarGossipValidator.create(
              spec, invalidBlockRoots, gossipValidationHelper, miscHelpers);
      final BlobSidecarManagerImpl blobSidecarManagerImpl =
          new BlobSidecarManagerImpl(
              spec,
              recentChainData,
              blockBlobSidecarsTrackersPool,
              blobSidecarValidator,
              futureBlobSidecars,
              invalidBlobSidecarRoots);
      eventChannels.subscribe(SlotEventsChannel.class, blobSidecarManagerImpl);

      blobSidecarManager = blobSidecarManagerImpl;
    } else {
      blobSidecarManager = BlobSidecarManager.NOOP;
    }
  }

  private void initDasSamplerManager() {
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      LOG.info("Activated DAS Sampler Manager for Fulu");
      this.dasSamplerManager =
          new DasSamplerManager(() -> dataAvailabilitySampler, spec, recentChainData);
    } else {
      LOG.info("Using NOOP DAS Sampler Manager");
      this.dasSamplerManager = DasSamplerManager.NOOP;
    }
  }

  protected void initDataColumnSidecarManager() {
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      dataColumnSidecarGossipValidator =
          DataColumnSidecarGossipValidator.create(
              spec,
              invalidBlockRoots,
              gossipValidationHelper,
              () -> blobKzgCommitmentsProvider,
              metricsSystem,
              timeProvider);
      dataColumnSidecarManager =
          new DataColumnSidecarManagerImpl(
              dataColumnSidecarGossipValidator, dasGossipLogger, metricsSystem, timeProvider);
    } else {
      dataColumnSidecarManager = DataColumnSidecarManager.NOOP;
    }
  }

  protected void initProposerPreferencesManager() {
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final ProposerPreferencesGossipValidator proposerPreferencesGossipValidator =
          new ProposerPreferencesGossipValidator(spec, gossipValidationHelper, recentChainData);
      final DefaultProposerPreferencesManager defaultProposerPreferencesManager =
          new DefaultProposerPreferencesManager(
              proposerPreferencesGossipValidator,
              poolFactory.createPendingPoolForProposerPreferences(spec));
      eventChannels.subscribe(SlotEventsChannel.class, defaultProposerPreferencesManager);
      eventChannels.subscribe(ReceivedBlockEventsChannel.class, defaultProposerPreferencesManager);
      proposerPreferencesManager = defaultProposerPreferencesManager;
    } else {
      proposerPreferencesManager = ProposerPreferencesManager.NOOP;
    }
  }

  protected void initExecutionPayloadBidManager() {
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator =
          new ExecutionPayloadBidGossipValidator(
              spec,
              gossipValidationHelper,
              proposerPreferencesManager,
              beaconConfig.p2pConfig().getMinBidIncrementPercentage());
      final ReceivedExecutionPayloadBidEventsChannel
          receivedExecutionPayloadBidEventsChannelPublisher =
              eventChannels.getPublisher(ReceivedExecutionPayloadBidEventsChannel.class);
      final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker =
          beaconConfig
              .executionPayloadBidCircuitBreakerFactory()
              .create(recentChainData::getForkChoiceStrategy);
      final StakedBuilderClientProvider stakedBuilderClientProvider =
          new StakedBuilderClientProvider(spec, beaconAsyncRunner);
      final BuilderBidFetcher builderBidFetcher =
          new BuilderBidFetcher(spec, stakedBuilderClientProvider);
      final ExecutionPayloadBidSelector executionPayloadBidSelector =
          new ExecutionPayloadBidSelector(
              beaconConfig.executionLayerConfig().getUseShouldOverrideBuilderFlag(),
              executionPayloadBidCircuitBreaker);
      final DefaultExecutionPayloadBidManager defaultExecutionPayloadBidManager =
          new DefaultExecutionPayloadBidManager(
              spec,
              executionPayloadBidGossipValidator,
              executionPayloadBidCircuitBreaker,
              receivedExecutionPayloadBidEventsChannelPublisher,
              poolFactory.createPendingPoolForExecutionPayloadBids(spec),
              builderBidFetcher,
              executionPayloadBidSelector);
      proposerPreferencesManager.subscribeOperationAdded(defaultExecutionPayloadBidManager);
      eventChannels.subscribe(SlotEventsChannel.class, defaultExecutionPayloadBidManager);
      eventChannels.subscribe(ReceivedBlockEventsChannel.class, defaultExecutionPayloadBidManager);
      eventChannels.subscribe(
          ReceivedExecutionPayloadEventsChannel.class, defaultExecutionPayloadBidManager);
      executionPayloadBidManager = defaultExecutionPayloadBidManager;
    } else {
      executionPayloadBidManager = ExecutionPayloadBidManager.NOOP;
    }
  }

  protected void initExecutionPayloadManager() {
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final ExecutionPayloadGossipValidator executionPayloadGossipValidator =
          new ExecutionPayloadGossipValidator(
              spec, gossipValidationHelper, blockGossipValidator, invalidBlockRoots);
      final ReceivedExecutionPayloadEventsChannel receivedExecutionPayloadEventsChannelPublisher =
          eventChannels.getPublisher(ReceivedExecutionPayloadEventsChannel.class);
      final ExecutionPayloadGossipChannel executionPayloadGossipChannel =
          eventChannels.getPublisher(ExecutionPayloadGossipChannel.class, beaconAsyncRunner);
      final DefaultExecutionPayloadManager executionPayloadManager =
          new DefaultExecutionPayloadManager(
              spec,
              beaconAsyncRunner,
              executionPayloadGossipValidator,
              forkChoice,
              executionLayer,
              receivedExecutionPayloadEventsChannelPublisher,
              recentChainData,
              blockRootsWithInvalidExecutionPayload,
              executionPayloadGossipChannel::publishExecutionPayload);
      final FailedExecutionPayloadPool failedExecutionPayloadPool =
          new FailedExecutionPayloadPool(executionPayloadManager, beaconAsyncRunner);
      executionPayloadManager.subscribeFailedPayloadExecution(
          failedExecutionPayloadPool::addFailedExecutionPayload);
      eventChannels.subscribe(ReceivedBlockEventsChannel.class, executionPayloadManager);
      this.executionPayloadManager = executionPayloadManager;
    } else {
      executionPayloadManager = ExecutionPayloadManager.NOOP;
    }
  }

  protected void initDasCustody() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return;
    }
    LOG.info("Activating DAS Custody for Fulu");
    final SpecVersion specVersionFulu = spec.forMilestone(SpecMilestone.FULU);
    final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(specVersionFulu.getConfig());
    final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator =
        MinCustodyPeriodSlotCalculator.createFromSpec(spec);

    final DataColumnSidecarDB sidecarDB =
        DataColumnSidecarDB.create(
            combinedChainDataClient,
            eventChannels.getPublisher(SidecarUpdateChannel.class, beaconAsyncRunner));
    this.sidecarDB = Optional.of(sidecarDB);
    final DataColumnSidecarDbAccessor dbAccessor =
        DataColumnSidecarDbAccessor.builder(sidecarDB).spec(spec).build();
    final CanonicalBlockResolver canonicalBlockResolver =
        slot ->
            combinedChainDataClient
                .getBlockAtSlotExact(slot)
                .thenApply(sbb -> sbb.flatMap(SignedBeaconBlock::getBeaconBlock));

    final int minCustodyGroupRequirement = specConfigFulu.getCustodyRequirement();
    final int maxGroups = specConfigFulu.getNumberOfCustodyGroups();

    // note - must be called AFTER custodyGroupCountManager initialized
    final DataColumnSidecarCustodyImpl dataColumnSidecarCustodyImpl =
        new DataColumnSidecarCustodyImpl(
            spec,
            canonicalBlockResolver,
            dbAccessor,
            minCustodyPeriodSlotCalculator,
            custodyGroupCountManager);
    eventChannels.subscribe(SlotEventsChannel.class, dataColumnSidecarCustodyImpl);

    final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
        eventChannels.getPublisher(DataColumnSidecarGossipChannel.class);

    final DataColumnSidecarRecoveringCustody dataColumnSidecarRecoveringCustody =
        new DataColumnSidecarRecoveringCustodyImpl(
            dataColumnSidecarCustodyImpl,
            dasAsyncRunner,
            spec,
            dataColumnSidecarGossipChannel::publishDataColumnSidecar,
            custodyGroupCountManager,
            specConfigFulu.getNumberOfColumns(),
            specConfigFulu.getNumberOfCustodyGroups(),
            slot -> {
              final long dataColumnSidecarRecoveryMaxDelayMillis =
                  beaconConfig
                      .eth2NetworkConfig()
                      .getDataColumnSidecarRecoveryMaxDelayMillis()
                      .orElse(spec.getAttestationDueMillis(slot));
              return Duration.ofMillis(dataColumnSidecarRecoveryMaxDelayMillis);
            },
            metricsSystem,
            timeProvider);
    this.dataColumnSidecarCustodyRef.set(dataColumnSidecarRecoveringCustody);
    eventChannels.subscribe(SlotEventsChannel.class, dataColumnSidecarRecoveringCustody);

    final DataColumnPeerManagerImpl dasPeerManager = new DataColumnPeerManagerImpl();
    p2pNetwork.subscribeConnect(dasPeerManager);

    final BatchDataColumnsByRootReqResp loggingByRootReqResp =
        new LoggingBatchDataColumnsByRootReqResp(dasPeerManager, dasReqRespLogger);
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        SchemaDefinitionsFulu.required(specVersionFulu.getSchemaDefinitions());

    final DataColumnReqResp dasRpc =
        new DataColumnReqRespBatchingImpl(
            loggingByRootReqResp,
            schemaDefinitionsFulu.getDataColumnsByRootIdentifierSchema(),
            spec);

    final MetadataDasPeerCustodyTracker peerCustodyTracker =
        new MetadataDasPeerCustodyTracker(maxGroups);
    p2pNetwork.subscribeConnect(peerCustodyTracker);
    final DasPeerCustodyCountSupplier custodyCountSupplier =
        DasPeerCustodyCountSupplier.capped(
            peerCustodyTracker, minCustodyGroupRequirement, maxGroups);

    final DataColumnSidecarRetriever sidecarRetriever =
        new SimpleSidecarRetriever(
            spec,
            dasPeerManager,
            custodyCountSupplier,
            dasRpc,
            dasAsyncRunner,
            timeProvider,
            beaconConfig.p2pConfig().getSidecarRetrievalOverlapFraction());

    simpleSidecarRetriever = Optional.of(sidecarRetriever);

    final DataColumnSidecarRetriever recoveringSidecarRetriever =
        new SidecarRetriever(
            sidecarRetriever,
            spec,
            dbAccessor,
            dasAsyncRunner,
            Duration.ofMillis(beaconConfig.p2pConfig().getSidecarRecoveryTimeout()),
            Duration.ofMillis(beaconConfig.p2pConfig().getSidecarDownloadTimeout()),
            Duration.ofSeconds(15),
            timeProvider,
            specConfigFulu.getNumberOfColumns(),
            custodyGroupCountManager,
            metricsSystem);

    final DasCustodyBackfiller custodyBackfiller =
        new DasCustodyBackfiller(
            combinedChainDataClient,
            Duration.ofSeconds(beaconConfig.p2pConfig().getSidecarSyncPollPeriod()),
            dataColumnSidecarRecoveringCustody,
            custodyGroupCountManager,
            recoveringSidecarRetriever,
            minCustodyPeriodSlotCalculator,
            dasAsyncRunner,
            sidecarDB::getEarliestAvailableDataColumnSlot,
            sidecarDB::setEarliestAvailableDataColumnSlot,
            beaconConfig.p2pConfig().getSidecarSyncBatchSize(),
            spec);
    eventChannels.subscribe(CustodyGroupCountChannel.class, custodyBackfiller);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, custodyBackfiller);
    dasCustodyBackfiller = Optional.of(custodyBackfiller);

    final CurrentSlotProvider currentSlotProvider =
        CurrentSlotProvider.create(spec, recentChainData.getStore());
    final RPCFetchDelayProvider rpcFetchDelayProvider =
        RPCFetchDelayProvider.create(
            spec,
            timeProvider,
            recentChainData,
            currentSlotProvider,
            DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS,
            DEFAULT_MIN_WAIT_MILLIS,
            DEFAULT_TARGET_WAIT_MILLIS);

    final BlockImportChannel dasBlockImportChannel =
        eventChannels.getPublisher(BlockImportChannel.class, beaconAsyncRunner);

    final DasSamplerBasicImpl dasSampler =
        new DasSamplerBasicImpl(
            spec,
            beaconAsyncRunner,
            currentSlotProvider,
            rpcFetchDelayProvider,
            dataColumnSidecarRecoveringCustody,
            recoveringSidecarRetriever,
            custodyGroupCountManager,
            recentChainData,
            beaconConfig.p2pConfig().isColumnsDataAvailabilityHalfCheckEnabled(),
            metricsSystem,
            beaconConfig.syncConfig().getMaxRecentlySampledBlocks(),
            dasBlockImportChannel);
    LOG.info(
        "DAS Basic Sampler initialized with {} groups to sample",
        custodyGroupCountManager.getSamplingGroupCount());
    eventChannels.subscribe(SlotEventsChannel.class, dasSampler);

    this.dasSamplerBasic = dasSampler;
    this.dataAvailabilitySampler = dasSampler;
    this.recoveringSidecarRetriever = Optional.of(recoveringSidecarRetriever);
  }

  protected void initDasSyncPreSampler() {
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      final DasPreSampler dasPreSampler =
          new DasPreSampler(
              this.dataAvailabilitySampler,
              this.dataColumnSidecarCustodyRef.get(),
              custodyGroupCountManager,
              blobKzgCommitmentsProvider);
      eventChannels.subscribe(SyncPreImportBlockChannel.class, dasPreSampler::onNewPreImportBlocks);
    }
  }

  protected void initCustodyGroupCountManager() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return;
    }
    final int totalMyCustodyGroups =
        beaconConfig.p2pConfig().getTotalCustodyGroupCount(spec.forMilestone(SpecMilestone.FULU));
    final CustodyGroupCountManagerImpl manager =
        new CustodyGroupCountManagerImpl(
            spec,
            proposersDataManager,
            eventChannels.getPublisher(CustodyGroupCountChannel.class),
            combinedChainDataClient,
            totalMyCustodyGroups,
            nodeId,
            metricsSystem);
    eventChannels.subscribe(SlotEventsChannel.class, manager);
    this.custodyGroupCountManager = manager;
  }

  protected void completeDasClassesWiring() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return;
    }
    final DataColumnSidecarRecoveringCustody dataColumnSidecarRecoveringCustody =
        dataColumnSidecarCustodyRef.get();
    final DataColumnSidecarRetriever dataColumnSidecarRetriever =
        recoveringSidecarRetriever.orElseThrow();

    // *************************************
    // ****** BLOCK pre-import source ******
    // *************************************

    // EL Recovery
    blockManager.subscribePreImportBlocks(dataColumnSidecarELManager::onNewBlock);

    // ***************************
    // ****** GOSSIP source ******
    // ***************************

    // RecoveringCustody
    dataColumnSidecarManager.subscribeToValidDataColumnSidecars(
        (dataColumnSidecar, remoteOrigin) ->
            dataColumnSidecarRecoveringCustody
                .onNewValidatedDataColumnSidecar(dataColumnSidecar, remoteOrigin)
                .finishError(LOG));

    // EL Recovery
    dataColumnSidecarManager.subscribeToValidDataColumnSidecars(
        dataColumnSidecarELManager::onNewDataColumnSidecar);

    // SidecarRetriever
    dataColumnSidecarManager.subscribeToValidDataColumnSidecars(
        dataColumnSidecarRetriever::onNewValidatedSidecar);

    // sampler
    dataColumnSidecarManager.subscribeToValidDataColumnSidecars(
        dataAvailabilitySampler::onNewValidatedDataColumnSidecar);

    // ********************************
    // ****** EL Recovery source ******
    // ********************************

    // SidecarRetriever
    dataColumnSidecarELManager.subscribeToRecoveredColumnSidecar(
        dataColumnSidecarRetriever::onNewValidatedSidecar);

    // sampler
    dataColumnSidecarELManager.subscribeToRecoveredColumnSidecar(
        dataAvailabilitySampler::onNewValidatedDataColumnSidecar);

    // custody
    dataColumnSidecarELManager.subscribeToRecoveredColumnSidecar(
        (sidecar, origin) ->
            dataColumnSidecarRecoveringCustody
                .onNewValidatedDataColumnSidecar(sidecar, origin)
                .finishError(LOG));

    // *******************************************
    // ********* RecoveringCustody source ********
    // **** (reconstruction on 50%+ columns) ****
    // *******************************************

    // SidecarRetriever
    dataColumnSidecarRecoveringCustody.subscribeToRecoveredColumnSidecar(
        dataColumnSidecarRetriever::onNewValidatedSidecar);

    // sampler
    dataColumnSidecarRecoveringCustody.subscribeToRecoveredColumnSidecar(
        dataAvailabilitySampler::onNewValidatedDataColumnSidecar);

    // *******************************************
    // ********* Sidecar Publisher source ********
    // *******************************************
    // (possible origins: LOCAL_EL, LOCAL_PROPOSAL, RECOVERED)

    eventChannels.subscribe(
        DataColumnSidecarGossipChannel.class,
        (sidecar, origin) -> {
          if (origin != RemoteOrigin.LOCAL_PROPOSAL) {
            // we explicitly subscribe to dataColumnSidecarELManager (LOCAL_EL)
            // and dataColumnSidecarRecoveringCustody (RECOVERED),
            // so let's distribute only for the LOCAL_PROPOSAL case.
            return;
          }

          // sampler
          dataAvailabilitySampler.onNewValidatedDataColumnSidecar(sidecar, origin);

          // retriever
          dataColumnSidecarRetriever.onNewValidatedSidecar(sidecar, origin);

          // custody
          dataColumnSidecarRecoveringCustody
              .onNewValidatedDataColumnSidecar(sidecar, origin)
              .finishError(LOG);
        });
  }

  protected void initMergeMonitors() {
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      terminalPowBlockMonitor =
          Optional.of(
              new TerminalPowBlockMonitor(
                  executionLayer,
                  spec,
                  recentChainData,
                  forkChoiceNotifier,
                  beaconAsyncRunner,
                  EVENT_LOG));
    }
  }

  protected void initPendingPoolsAndCaches() {
    LOG.debug("BeaconChainController.initPendingPoolsAndCaches()");
    pendingBlockPool = poolFactory.createPendingBlockPool(spec);
    pendingBlocks = pendingBlockPool.getBlocksWaitingForParent();
    pendingAttestationPool =
        poolFactory.createPendingAttestationPool(
            spec, beaconConfig.eth2NetworkConfig().getPendingAttestationsMaxQueue());
    eventChannels
        .subscribe(FinalizedCheckpointChannel.class, pendingBlockPool)
        .subscribe(SlotEventsChannel.class, pendingBlockPool)
        .subscribe(FinalizedCheckpointChannel.class, pendingAttestationPool)
        .subscribe(SlotEventsChannel.class, pendingAttestationPool);
    invalidBlockRoots = LimitedMap.createSynchronizedLRU(500);
    blockRootsWithInvalidExecutionPayload = LimitedSet.createSynchronizedLRU(500);
  }

  protected void initBlockBlobSidecarsTrackersPool() {
    LOG.debug("BeaconChainController.initBlockBlobSidecarsTrackersPool()");
    if (spec.isMilestoneSupported(SpecMilestone.DENEB) && !isSafeToDeactivateDenebFeatures()) {
      final BlockImportChannel blockImportChannel =
          eventChannels.getPublisher(BlockImportChannel.class, beaconAsyncRunner);
      final BlobSidecarGossipChannel blobSidecarGossipChannel =
          eventChannels.getPublisher(BlobSidecarGossipChannel.class, beaconAsyncRunner);

      final BlockBlobSidecarsTrackersPoolImpl pool =
          poolFactory.createPoolForBlockBlobSidecarsTrackers(
              blockImportChannel,
              spec,
              timeProvider,
              beaconAsyncRunner,
              recentChainData,
              executionLayer,
              () -> blobSidecarValidator,
              blobSidecarGossipChannel::publishBlobSidecar);
      eventChannels.subscribe(FinalizedCheckpointChannel.class, pool);
      blockBlobSidecarsTrackersPool = pool;
    } else {
      blockBlobSidecarsTrackersPool = BlockBlobSidecarsTrackersPool.NOOP;
    }
  }

  protected void initDataColumnSidecarELManager() {
    LOG.debug("BeaconChainController.initDataColumnSidecarELManager()");
    if (beaconConfig.p2pConfig().isDasDisableElRecovery()) {
      LOG.warn(
          "DataColumnSidecarELRecoveryManager is NOOP: blobs recovery from local EL is disabled.");
    }
    if (spec.isMilestoneSupported(SpecMilestone.FULU)
        && !beaconConfig.p2pConfig().isDasDisableElRecovery()) {

      final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
          eventChannels.getPublisher(DataColumnSidecarGossipChannel.class);

      final DataColumnSidecarELManager recoveryManager =
          poolFactory.createDataColumnSidecarELManager(
              spec,
              beaconAsyncRunner,
              recentChainData,
              executionLayer,
              dataColumnSidecarGossipChannel::publishDataColumnSidecars,
              dataColumnSidecarGossipValidator,
              custodyGroupCountManager,
              metricsSystem,
              timeProvider);
      eventChannels.subscribe(SlotEventsChannel.class, recoveryManager);
      dataColumnSidecarELManager = recoveryManager;
    } else {
      dataColumnSidecarELManager = DataColumnSidecarELManager.NOOP;
    }
  }

  protected void initGossipValidationHelper() {
    LOG.debug("BeaconChainController.initGossipValidationHelper()");
    gossipValidationHelper = new GossipValidationHelper(spec, recentChainData, metricsSystem);
  }

  protected void initPerformanceTracker() {
    LOG.debug("BeaconChainController.initPerformanceTracker()");
    ValidatorPerformanceTrackingMode mode =
        beaconConfig.validatorConfig().getValidatorPerformanceTrackingMode();
    if (mode.isEnabled()) {
      final SettableGauge performanceTrackerTimings =
          SettableGauge.create(
              metricsSystem,
              BEACON,
              "performance_tracker_timings",
              "Tracks how much time (in millis) performance tracker takes to perform calculations");
      performanceTracker =
          new DefaultPerformanceTracker(
              combinedChainDataClient,
              STATUS_LOG,
              new ValidatorPerformanceMetrics(metricsSystem),
              beaconConfig.validatorConfig().getValidatorPerformanceTrackingMode(),
              activeValidatorTracker,
              new SyncCommitteePerformanceTracker(spec, combinedChainDataClient),
              spec,
              performanceTrackerTimings);
      eventChannels.subscribe(SlotEventsChannel.class, performanceTracker);
    } else {
      performanceTracker = new NoOpPerformanceTracker();
    }
  }

  protected void initAttesterSlashingPool() {
    LOG.debug("BeaconChainController.initAttesterSlashingPool()");
    attesterSlashingPool =
        new SimpleOperationPool<>(
            "AttesterSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
            new AttesterSlashingValidator(recentChainData, spec),
            // Prioritise slashings that include more validators at a time
            Comparator.<AttesterSlashing>comparingInt(
                    slashing -> slashing.getIntersectingValidatorIndices().size())
                .reversed());
    blockImporter.subscribeToVerifiedBlockAttesterSlashings(attesterSlashingPool::removeAll);
    attesterSlashingPool.subscribeOperationAdded(forkChoice::onAttesterSlashing);
  }

  protected void initProposerSlashingPool() {
    LOG.debug("BeaconChainController.initProposerSlashingPool()");
    ProposerSlashingValidator validator = new ProposerSlashingValidator(spec, recentChainData);
    proposerSlashingPool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    blockImporter.subscribeToVerifiedBlockProposerSlashings(proposerSlashingPool::removeAll);
  }

  protected void initSlashingEventsSubscriptions() {
    if (beaconConfig.validatorConfig().isShutdownWhenValidatorSlashedEnabled()) {
      final ValidatorTimingChannel validatorTimingChannel =
          eventChannels.getPublisher(ValidatorTimingChannel.class);
      attesterSlashingPool.subscribeOperationAdded(
          (operation, validationStatus, fromNetwork) ->
              validatorTimingChannel.onAttesterSlashing(operation));
      proposerSlashingPool.subscribeOperationAdded(
          (operation, validationStatus, fromNetwork) ->
              validatorTimingChannel.onProposerSlashing(operation));
    }
  }

  protected void initVoluntaryExitPool() {
    LOG.debug("BeaconChainController.initVoluntaryExitPool()");
    final VoluntaryExitValidator validator =
        new VoluntaryExitValidator(spec, recentChainData, timeProvider);
    voluntaryExitPool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator,
            operationPoolAsyncRunner,
            timeProvider);
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(voluntaryExitPool::removeAll);
  }

  protected void initSignedBlsToExecutionChangePool() {
    LOG.debug("BeaconChainController.initSignedBlsToExecutionChangePool()");
    final SignedBlsToExecutionChangeValidator validator =
        new SignedBlsToExecutionChangeValidator(
            spec, timeProvider, recentChainData, signatureVerificationService);

    blsToExecutionChangePool =
        new MappedOperationPool<>(
            "SignedBlsToExecutionChangePool",
            metricsSystem,
            beaconBlockSchemaSupplier
                .andThen(BeaconBlockBodySchema::toVersionCapella)
                .andThen(Optional::orElseThrow)
                .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema),
            validator,
            operationPoolAsyncRunner,
            timeProvider);
    blockImporter.subscribeToVerifiedBlockBlsToExecutionChanges(
        blsToExecutionChangePool::removeAll);
  }

  protected void initPayloadAttestationPool() {
    LOG.debug("BeaconChainController.initPayloadAttestationPool()");
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final PayloadAttestationMessageGossipValidator validator =
          new PayloadAttestationMessageGossipValidator(
              spec, gossipValidationHelper, invalidBlockRoots);
      pendingPayloadAttestations =
          poolFactory.createPendingPoolForPayloadAttestations(
              spec, beaconConfig.eth2NetworkConfig().getPendingPayloadAttestationsMaxQueue());
      final AggregatingPayloadAttestationPool aggregatingPayloadAttestationPool =
          new AggregatingPayloadAttestationPool(
              spec, validator, pendingPayloadAttestations, metricsSystem);
      payloadAttestationPool = aggregatingPayloadAttestationPool;
      eventChannels
          .subscribe(SlotEventsChannel.class, aggregatingPayloadAttestationPool)
          .subscribe(ReceivedBlockEventsChannel.class, aggregatingPayloadAttestationPool)
          .subscribe(FinalizedCheckpointChannel.class, pendingPayloadAttestations);
      payloadAttestationPool.subscribeValidatedOperationAdded(
          forkChoice::onPayloadAttestationMessage);
    } else {
      pendingPayloadAttestations = poolFactory.createNoOpPendingPool(spec);
      payloadAttestationPool = PayloadAttestationPool.NOOP;
    }
  }

  protected void initBlobSidecarReconstructionProvider() {
    LOG.debug("BeaconChainController.initBlobSidecarReconstructionProvider()");
    this.blobSidecarReconstructionProvider =
        new BlobSidecarReconstructionProvider(combinedChainDataClient, spec);
  }

  protected void initBlobReconstructionProvider() {
    LOG.debug("BeaconChainController.initBlobReconstructionProvider()");

    final DataColumnSidecarNetworkRetriever networkRetriever;
    if (simpleSidecarRetriever.isPresent()
        && beaconConfig.beaconRestApiConfig().isGetBlobsSidecarsDownloadEnabled()) {
      networkRetriever =
          new DataColumnSidecarNetworkRetrieverImpl(
              simpleSidecarRetriever.get()::retrieve,
              simpleSidecarRetriever.get()::flush,
              sidecarDB.orElseThrow()::addSidecar,
              beaconConfig.beaconRestApiConfig().getGetBlobsSidecarsDownloadTimeoutSeconds());
    } else {
      networkRetriever = DataColumnSidecarNetworkRetriever.DISABLED;
    }

    this.blobReconstructionProvider =
        BlobReconstructionProvider.create(combinedChainDataClient, networkRetriever, spec);
  }

  protected void initDataProvider() {
    dataProvider =
        DataProvider.builder()
            .spec(spec)
            .recentChainData(recentChainData)
            .combinedChainDataClient(combinedChainDataClient)
            .rewardCalculator(rewardCalculator)
            .blobSidecarReconstructionProvider(blobSidecarReconstructionProvider)
            .blobReconstructionProvider(blobReconstructionProvider)
            .lightClientUpdateStore(lightClientUpdateStore)
            .p2pNetwork(p2pNetwork)
            .syncService(syncService)
            .validatorApiChannel(
                eventChannels.getPublisher(ValidatorApiChannel.class, beaconAsyncRunner))
            .attestationPool(aggregatingAttestationPool)
            .blockBlobSidecarsTrackersPool(blockBlobSidecarsTrackersPool)
            .attestationManager(attestationManager)
            .isLivenessTrackingEnabled(getLivenessTrackingEnabled(beaconConfig))
            .activeValidatorChannel(
                eventChannels.getPublisher(ActiveValidatorChannel.class, beaconAsyncRunner))
            .attesterSlashingPool(attesterSlashingPool)
            .proposerSlashingPool(proposerSlashingPool)
            .voluntaryExitPool(voluntaryExitPool)
            .blsToExecutionChangePool(blsToExecutionChangePool)
            .syncCommitteeContributionPool(syncCommitteeContributionPool)
            .proposersDataManager(proposersDataManager)
            .forkChoiceNotifier(forkChoiceNotifier)
            .rejectedExecutionSupplier(rejectedExecutionCountSupplier)
            .custodyGroupCountManager(custodyGroupCountManager)
            .dataColumnSidecarManager(dataColumnSidecarManager)
            .payloadAttestationPool(payloadAttestationPool)
            .proposerPreferencesManager(proposerPreferencesManager)
            .build();
  }

  private boolean getLivenessTrackingEnabled(final BeaconChainConfiguration beaconConfig) {
    return beaconConfig.beaconRestApiConfig().isBeaconLivenessTrackingEnabled()
        || beaconConfig.validatorConfig().isDoppelgangerDetectionEnabled();
  }

  protected void initCombinedChainDataClient() {
    LOG.debug("BeaconChainController.initCombinedChainDataClient()");
    combinedChainDataClient =
        new CombinedChainDataClient(
            recentChainData, storageQueryChannel, spec, executionPayloadProvider);
  }

  protected void initBlobKzgCommitmentsProvider() {
    LOG.debug("BeaconChainController.initBlobKzgCommitmentsProvider()");
    final SyncConfig syncConfig = beaconConfig.syncConfig();
    // Keep one extra batch for overlap between feeding a new forward-sync batch and validating data
    // column responses for batches already in flight.
    final int maxCacheSize =
        Math.max(
            128,
            syncConfig.getForwardSyncBatchSize()
                * (syncConfig.getForwardSyncMaxPendingBatches() + 1));
    blobKzgCommitmentsProvider =
        new BlobKzgCommitmentsProvider(spec, combinedChainDataClient, maxCacheSize);
    eventChannels
        .subscribe(ReceivedBlockEventsChannel.class, blobKzgCommitmentsProvider)
        .subscribe(FinalizedCheckpointChannel.class, blobKzgCommitmentsProvider);
  }

  protected void initLightClientUpdateStore() {
    LOG.debug("BeaconChainController.initLightClientUpdateStore()");
    lightClientUpdateStore = new LightClientUpdateStore(spec);
  }

  protected void initLightClientServerService() {
    if (!beaconConfig.eth2NetworkConfig().isLightClientServerEnabled()) {
      LOG.debug("BeaconChainController.initLightClientServerService() - disabled");
      return;
    }
    LOG.debug("BeaconChainController.initLightClientServerService()");
    lightClientServerService =
        new LightClientServerService(spec, lightClientUpdateStore, combinedChainDataClient);
    eventChannels
        .subscribe(ReceivedBlockEventsChannel.class, lightClientServerService)
        .subscribe(FinalizedCheckpointChannel.class, lightClientServerService)
        .subscribe(ChainHeadChannel.class, lightClientServerService);
  }

  protected SafeFuture<Void> initWeakSubjectivity(
      final StorageQueryChannel queryChannel, final StorageUpdateChannel updateChannel) {
    return wsInitializer
        .finalizeAndStoreConfig(beaconConfig.weakSubjectivity(), queryChannel, updateChannel)
        .thenAccept(
            finalConfig ->
                this.weakSubjectivityValidator = WeakSubjectivityValidator.moderate(finalConfig));
  }

  protected void initForkChoice() {
    LOG.debug("BeaconChainController.initForkChoice()");
    forkChoice =
        new ForkChoice(
            spec,
            forkChoiceExecutor,
            recentChainData,
            forkChoiceNotifier,
            forkChoiceStateProvider,
            new TickProcessor(spec, recentChainData),
            new MergeTransitionBlockValidator(spec, recentChainData),
            fastConfirmationTracker,
            beaconConfig.eth2NetworkConfig().isForkChoiceLateBlockReorgEnabled(),
            (slot, blockRoot) ->
                beaconAsyncRunner.runAsync(
                    () -> operationsReOrgManager.onLateBlockReorgPreparation(slot, blockRoot)),
            debugDataDumper,
            metricsSystem,
            signatureVerificationService);
    forkChoiceTrigger =
        new ForkChoiceTrigger(
            forkChoice,
            beaconConfig.eth2NetworkConfig().getAttestationWaitLimitMillis(),
            timeProvider);
    // Only required for Gloas genesis, so skip it fast in all other cases
    if (spec.getGenesisSpecConfig().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      try {
        forkChoice.applyGenesisExecutionPayloadForGloas().get(1, TimeUnit.MINUTES);
      } catch (Exception e) {
        throw new InvalidConfigurationException(
            "Failed to apply genesis execution payload for Gloas", e);
      }
    }
  }

  public void initMetrics() {
    LOG.debug("BeaconChainController.initMetrics()");
    final SyncCommitteeMetrics syncCommitteeMetrics =
        new SyncCommitteeMetrics(spec, recentChainData, metricsSystem);
    final BeaconChainMetrics beaconChainMetrics =
        new BeaconChainMetrics(
            spec,
            recentChainData,
            slotProcessor.getNodeSlot(),
            metricsSystem,
            p2pNetwork,
            eth1DataCache);
    eventChannels
        .subscribe(SlotEventsChannel.class, beaconChainMetrics)
        .subscribe(SlotEventsChannel.class, syncCommitteeMetrics)
        .subscribe(ChainHeadChannel.class, syncCommitteeMetrics);
  }

  protected void initEth1DataCache() {
    LOG.debug("BeaconChainController.initEth1DataCache");
    eth1DataCache = new Eth1DataCache(spec, metricsSystem, new Eth1VotingPeriod(spec));
  }

  public void initDepositProvider() {
    LOG.debug("BeaconChainController.initDepositProvider()");
    depositProvider =
        new DepositProvider(
            metricsSystem,
            recentChainData,
            eth1DataCache,
            storageUpdateChannel,
            eventChannels.getPublisher(Eth1DepositStorageChannel.class, beaconAsyncRunner),
            spec);
    eventChannels
        .subscribe(Eth1EventsChannel.class, depositProvider)
        .subscribe(FinalizedCheckpointChannel.class, depositProvider);
  }

  protected void initAttestationTopicSubscriber() {
    LOG.debug("BeaconChainController.initAttestationTopicSubscriber");
    final SettableLabelledGauge subnetSubscriptionsGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.NETWORK,
            "subnet_subscriptions",
            "Tracks attestations subnet subscriptions",
            "type");
    this.attestationTopicSubscriber =
        new AttestationTopicSubscriber(spec, p2pNetwork, subnetSubscriptionsGauge);
  }

  protected void initActiveValidatorTracker() {
    LOG.debug("BeaconChainController.initActiveValidatorTracker");
    this.activeValidatorTracker = new ActiveValidatorTracker(spec);
  }

  protected void initSubnetSubscriber() {
    LOG.debug("BeaconChainController.initSubnetSubscriber");
    if (beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()) {
      LOG.info("Subscribing to all attestation subnets");
      this.stableSubnetSubscriber =
          AllSubnetsSubscriber.create(attestationTopicSubscriber, spec.getNetworkingConfig());
    } else {
      if (p2pNetwork.getDiscoveryNodeId().isPresent()) {
        this.stableSubnetSubscriber =
            new NodeBasedStableSubnetSubscriber(
                attestationTopicSubscriber, spec, p2pNetwork.getDiscoveryNodeId().get());
      } else {
        LOG.warn("Discovery nodeId is not defined, disabling stable subnet subscriptions");
        this.stableSubnetSubscriber = StableSubnetSubscriber.NOOP;
      }
    }
    eventChannels.subscribe(SlotEventsChannel.class, stableSubnetSubscriber);
  }

  protected void initDataColumnSidecarSubnetBackboneSubscriber() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return;
    }
    LOG.debug("BeaconChainController.initDataColumnSidecarSubnetBackboneSubscriber");
    final DataColumnSidecarSubnetBackboneSubscriber subnetBackboneSubscriber =
        new DataColumnSidecarSubnetBackboneSubscriber(
            spec, p2pNetwork, nodeId, custodyGroupCountManager);

    eventChannels.subscribe(SlotEventsChannel.class, subnetBackboneSubscriber);
  }

  public void initExecutionLayerBlockProductionManager() {
    LOG.debug("BeaconChainController.initExecutionLayerBlockProductionManager()");
    this.executionLayerBlockProductionManager =
        ExecutionLayerBlockManagerFactory.create(executionLayer, eventChannels);
  }

  public void initRewardCalculator() {
    LOG.debug("BeaconChainController.initRewardCalculator()");
    rewardCalculator = new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));
  }

  public void initValidatorApiHandler() {
    LOG.debug("BeaconChainController.initValidatorApiHandler()");
    final GraffitiBuilder graffitiBuilder =
        new GraffitiBuilder(beaconConfig.validatorConfig().getClientGraffitiAppendFormat());
    eventChannels.subscribe(ExecutionClientVersionChannel.class, graffitiBuilder);
    eventChannels.subscribe(
        ExecutionClientVersionChannel.class, dataProvider.getExecutionClientDataProvider());
    final ExecutionClientVersionProvider executionClientVersionProvider =
        new ExecutionClientVersionProvider(
            executionLayer,
            eventChannels.getPublisher(ExecutionClientVersionChannel.class),
            graffitiBuilder.getConsensusClientVersion());
    final BlockFactory blockFactory =
        new MilestoneBasedBlockFactory(
            spec,
            new BlockOperationSelectorFactory(
                spec,
                aggregatingAttestationPool,
                attesterSlashingPool,
                proposerSlashingPool,
                voluntaryExitPool,
                blsToExecutionChangePool,
                syncCommitteeContributionPool,
                payloadAttestationPool,
                depositProvider,
                eth1DataCache,
                graffitiBuilder,
                forkChoiceNotifier,
                executionLayerBlockProductionManager,
                executionPayloadBidManager,
                executionPayloadManager,
                metricsSystem,
                timeProvider));
    SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
        beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()
            ? new AllSyncCommitteeSubscriptions(p2pNetwork, spec)
            : new SyncCommitteeSubscriptionManager(p2pNetwork);
    final BlockImportChannel blockImportChannel =
        eventChannels.getPublisher(BlockImportChannel.class, beaconAsyncRunner);
    final BlockGossipChannel blockGossipChannel =
        eventChannels.getPublisher(BlockGossipChannel.class, beaconAsyncRunner);
    final BlobSidecarGossipChannel blobSidecarGossipChannel;
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      blobSidecarGossipChannel =
          eventChannels.getPublisher(BlobSidecarGossipChannel.class, beaconAsyncRunner);
    } else {
      blobSidecarGossipChannel = BlobSidecarGossipChannel.NOOP;
    }
    final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel;
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      dataColumnSidecarGossipChannel =
          eventChannels.getPublisher(DataColumnSidecarGossipChannel.class, beaconAsyncRunner);
    } else {
      dataColumnSidecarGossipChannel = DataColumnSidecarGossipChannel.NOOP;
    }

    final Optional<BlockProductionMetrics> blockProductionMetrics =
        beaconConfig.getMetricsConfig().isBlockProductionPerformanceEnabled()
            ? Optional.of(BlockProductionMetrics.create(metricsSystem))
            : Optional.empty();

    final BlockProductionAndPublishingPerformanceFactory blockProductionPerformanceFactory =
        new BlockProductionAndPublishingPerformanceFactory(
            timeProvider,
            (slot) -> secondsToMillis(recentChainData.computeTimeAtSlot(slot)),
            beaconConfig.getMetricsConfig().isBlockProductionAndPublishingPerformanceEnabled(),
            beaconConfig.getMetricsConfig().getBlockProductionPerformanceWarningLocalThreshold(),
            beaconConfig.getMetricsConfig().getBlockProductionPerformanceWarningBuilderThreshold(),
            beaconConfig.getMetricsConfig().getBlockPublishingPerformanceWarningLocalThreshold(),
            beaconConfig.getMetricsConfig().getBlockPublishingPerformanceWarningBuilderThreshold(),
            blockProductionMetrics);

    final DutyMetrics dutyMetrics =
        DutyMetrics.create(metricsSystem, timeProvider, recentChainData, spec);

    final BlockPublisher blockPublisher =
        new MilestoneBasedBlockPublisher(
            beaconAsyncRunner,
            spec,
            blockFactory,
            blockImportChannel,
            blockGossipChannel,
            blockBlobSidecarsTrackersPool,
            blobSidecarGossipChannel,
            dataColumnSidecarGossipChannel,
            dutyMetrics,
            custodyGroupCountManager,
            beaconConfig.p2pConfig().getDasPublishWithholdColumnsEverySlots(),
            beaconConfig.p2pConfig().isGossipBlobsAfterBlockEnabled());

    final ExecutionPayloadFactory executionPayloadFactory;
    final ExecutionPayloadPublisher executionPayloadPublisher;

    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final ExecutionPayloadGossipChannel executionPayloadGossipChannel =
          eventChannels.getPublisher(ExecutionPayloadGossipChannel.class, beaconAsyncRunner);
      executionPayloadFactory =
          new ExecutionPayloadFactoryGloas(spec, executionLayerBlockProductionManager);
      executionPayloadPublisher =
          new ExecutionPayloadPublisherGloas(
              executionPayloadFactory,
              executionPayloadGossipChannel,
              dataColumnSidecarGossipChannel,
              executionPayloadManager,
              recentChainData);
    } else {
      executionPayloadFactory = ExecutionPayloadFactory.NOOP;
      executionPayloadPublisher = ExecutionPayloadPublisher.NOOP;
    }
    this.validatorApiHandler =
        new ValidatorApiHandler(
            dataProvider.getChainDataProvider(),
            dataProvider.getNodeDataProvider(),
            dataProvider.getNetworkDataProvider(),
            combinedChainDataClient,
            syncService,
            blockFactory,
            aggregatingAttestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            dutyMetrics,
            performanceTracker,
            spec,
            forkChoiceTrigger,
            proposersDataManager,
            syncCommitteeMessagePool,
            syncCommitteeContributionPool,
            syncCommitteeSubscriptionManager,
            blockProductionPerformanceFactory,
            blockPublisher,
            payloadAttestationPool,
            dataAvailabilitySampler,
            executionPayloadManager,
            executionPayloadFactory,
            executionPayloadPublisher,
            executionPayloadBidManager,
            proposerPreferencesManager,
            executionProofManager);

    eventChannels
        .subscribe(SlotEventsChannel.class, activeValidatorTracker)
        .subscribe(ExecutionClientEventsChannel.class, executionClientVersionProvider)
        .subscribe(SlotEventsChannel.class, validatorApiHandler)
        .subscribeMultithreaded(
            ValidatorApiChannel.class,
            validatorApiHandler,
            beaconConfig.beaconRestApiConfig().getValidatorThreads());

    // if subscribeAllSubnets is set, the slot events in these handlers are empty,
    // so don't subscribe.
    if (!beaconConfig.p2pConfig().isSubscribeAllSubnetsEnabled()) {
      eventChannels
          .subscribe(SlotEventsChannel.class, attestationTopicSubscriber)
          .subscribe(SlotEventsChannel.class, syncCommitteeSubscriptionManager);
    }
  }

  protected void initGenesisHandler() {
    if (!recentChainData.isPreGenesis()) {
      // We already have a genesis block - no need for a genesis handler
      return;
    }
    // Genesis can no longer be derived from the Eth1 chain (deposit-log fetching has been removed).
    // If we are still pre-genesis here, an initial anchor state must be supplied via a custom
    // initial state, checkpoint sync, or interop.
    throw new IllegalStateException("No initial state is set and genesis is not available.");
  }

  protected void initSignatureVerificationService() {
    final P2PConfig p2PConfig = beaconConfig.p2pConfig();
    signatureVerificationService =
        new AggregatingSignatureVerificationService(
            metricsSystem,
            asyncRunnerFactory,
            beaconAsyncRunner,
            p2PConfig.getBatchVerifyMaxThreads(),
            p2PConfig.getBatchVerifyQueueCapacity(),
            p2PConfig.getBatchVerifyMaxBatchSize(),
            p2PConfig.isBatchVerifyStrictThreadLimitEnabled());
  }

  protected void initAttestationManager() {
    final FutureItems<ValidatableAttestation> futureAttestations =
        FutureItems.create(
            ValidatableAttestation::getEarliestSlotForForkChoiceProcessing,
            UInt64.valueOf(3),
            futureItemsMetric,
            "attestations");
    AttestationValidator attestationValidator =
        new AttestationValidator(
            spec,
            signatureVerificationService,
            gossipValidationHelper,
            invalidBlockRoots,
            blockRootsWithInvalidExecutionPayload);
    AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(spec, attestationValidator, signatureVerificationService);
    blockImporter.subscribeToVerifiedBlockAttestations(
        (slot, attestations) ->
            attestations.forEach(
                attestation ->
                    aggregateValidator.addSeenAggregate(
                        ValidatableAttestation.from(spec, attestation))));
    attestationManager =
        AttestationManager.create(
            pendingAttestationPool,
            futureAttestations,
            forkChoice,
            aggregatingAttestationPool,
            attestationValidator,
            aggregateValidator,
            signatureVerificationService,
            eventChannels.getPublisher(ActiveValidatorChannel.class, beaconAsyncRunner));
    eventChannels
        .subscribe(SlotEventsChannel.class, attestationManager)
        .subscribe(ReceivedBlockEventsChannel.class, attestationManager)
        .subscribe(ReceivedExecutionPayloadEventsChannel.class, attestationManager);
  }

  protected void initSyncCommitteePools() {
    final SyncCommitteeStateUtils syncCommitteeStateUtils =
        new SyncCommitteeStateUtils(spec, recentChainData);
    syncCommitteeContributionPool =
        new SyncCommitteeContributionPool(
            spec,
            new SignedContributionAndProofValidator(
                spec,
                recentChainData,
                syncCommitteeStateUtils,
                signatureVerificationService,
                gossipValidationHelper));

    syncCommitteeMessagePool =
        new SyncCommitteeMessagePool(
            spec,
            new SyncCommitteeMessageValidator(
                spec,
                recentChainData,
                syncCommitteeStateUtils,
                signatureVerificationService,
                gossipValidationHelper));
    eventChannels
        .subscribe(SlotEventsChannel.class, syncCommitteeContributionPool)
        .subscribe(SlotEventsChannel.class, syncCommitteeMessagePool);
  }

  protected void initP2PNetwork() {
    LOG.debug("BeaconChainController.initP2PNetwork()");
    if (!beaconConfig.p2pConfig().getNetworkConfig().isEnabled()) {
      this.p2pNetwork = new NoOpEth2P2PNetwork(spec);
      return;
    }

    DiscoveryConfig discoveryConfig = beaconConfig.p2pConfig().getDiscoveryConfig();
    final Optional<Integer> maybeUdpPort =
        discoveryConfig.isDiscoveryEnabled()
            ? Optional.of(discoveryConfig.getListenUdpPort())
            : Optional.empty();

    PortAvailability.checkPortsAvailable(
        beaconConfig.p2pConfig().getNetworkConfig().getListenPort(), maybeUdpPort);

    // Using a throttled historical query retrieval when handling RPC requests to avoid
    // overwhelming the node in case of various DDOS attacks
    Optional<CombinedChainDataClient> throttlingCombinedChainDataClient = Optional.empty();
    if (beaconConfig.p2pConfig().getHistoricalDataMaxConcurrentQueries() > 0) {
      final ThrottlingStorageQueryChannel throttlingStorageQueryChannel =
          new ThrottlingStorageQueryChannel(
              storageQueryChannel,
              beaconConfig.p2pConfig().getHistoricalDataMaxConcurrentQueries(),
              beaconConfig.p2pConfig().getHistoricalDataMaxQueryQueueSize(),
              metricsSystem);
      // The envelope fallback reads from historical data too, so it goes through the throttled
      // channel as well - otherwise RPC requests for finalized envelopes would bypass the
      // concurrency limit this client exists to enforce.
      final ExecutionPayloadProvider throttlingExecutionPayloadProvider =
          createExecutionPayloadProvider(
              createBlindedExecutionPayloadProvider(throttlingStorageQueryChannel));
      throttlingCombinedChainDataClient =
          Optional.of(
              new CombinedChainDataClient(
                  recentChainData,
                  throttlingStorageQueryChannel,
                  spec,
                  throttlingExecutionPayloadProvider));
    }

    final SuperNodeSupplier isSuperNodeSupplier =
        new SuperNodeSupplier(spec, () -> custodyGroupCountManager);

    final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor;
    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      dataColumnSidecarArchiveReconstructor =
          new DataColumnSidecarArchiveReconstructorImpl(
              throttlingCombinedChainDataClient.orElse(combinedChainDataClient),
              asyncRunnerFactory.create("data_column_sidecar_archive_reconstruction", 2),
              isSuperNodeSupplier,
              spec,
              beaconConfig.eth2NetworkConfig().getDataColumnSidecarExtensionRetentionEpochs(),
              eventChannels.getPublisher(SidecarArchivePrunableChannel.class),
              metricsSystem,
              timeProvider);
      eventChannels.subscribe(
          FinalizedCheckpointChannel.class, dataColumnSidecarArchiveReconstructor);
    } else {
      dataColumnSidecarArchiveReconstructor = DataColumnSidecarArchiveReconstructor.NOOP;
    }

    this.p2pNetwork =
        createEth2P2PNetworkBuilder()
            .config(beaconConfig.p2pConfig())
            .eventChannels(eventChannels)
            .combinedChainDataClient(
                throttlingCombinedChainDataClient.orElse(combinedChainDataClient))
            .blobKzgCommitmentsProvider(blobKzgCommitmentsProvider)
            .custodyGroupCountManagerSupplier(() -> custodyGroupCountManager)
            .gossipedBlockProcessor(blockManager::validateAndImportBlock)
            .gossipedBlobSidecarProcessor(blobSidecarManager::validateAndPrepareForBlockImport)
            .gossipedDataColumnSidecarOperationProcessor(
                dataColumnSidecarManager::onDataColumnSidecarGossip)
            .gossipedExecutionProofOperationProcessor(
                executionProofManager::onReceivedExecutionProofGossip)
            .gossipedAttestationProcessor(attestationManager::addAttestation)
            .gossipedAggregateProcessor(attestationManager::addAggregate)
            .gossipedAttesterSlashingProcessor(attesterSlashingPool::addRemote)
            .gossipedProposerSlashingProcessor(proposerSlashingPool::addRemote)
            .gossipedVoluntaryExitProcessor(voluntaryExitPool::addRemote)
            .gossipedSignedContributionAndProofProcessor(syncCommitteeContributionPool::addRemote)
            .gossipedSyncCommitteeMessageProcessor(syncCommitteeMessagePool::addRemote)
            .gossipedSignedBlsToExecutionChangeProcessor(blsToExecutionChangePool::addRemote)
            .gossipedExecutionPayloadProcessor(
                executionPayloadManager::validateAndImportExecutionPayload)
            .gossipedPayloadAttestationMessageProcessor(payloadAttestationPool::addRemote)
            .gossipedExecutionPayloadBidProcessor(
                (signedBid, arrivalTimestamp) ->
                    executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P))
            .gossipedProposerPreferencesProcessor(
                (signedProposerPreferences, arrivalTimestamp) ->
                    proposerPreferencesManager.addRemote(signedProposerPreferences))
            .gossipDasLogger(dasGossipLogger)
            .dataColumnSidecarArchiveReconstructor(dataColumnSidecarArchiveReconstructor)
            .reqRespDasLogger(dasReqRespLogger)
            .isSuperNodeSupplier(isSuperNodeSupplier)
            .processedAttestationSubscriptionProvider(
                attestationManager::subscribeToAttestationsToSend)
            .metricsSystem(metricsSystem)
            .timeProvider(timeProvider)
            .asyncRunner(networkAsyncRunner)
            .keyValueStore(keyValueStore)
            .requiredCheckpoint(weakSubjectivityValidator.getWSCheckpoint())
            .specProvider(spec)
            .recordMessageArrival(true)
            .p2pDebugDataDumper(debugDataDumper)
            .build();

    syncCommitteeMessagePool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSyncCommitteeMessage));
    syncCommitteeContributionPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSyncCommitteeContribution));
    proposerSlashingPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishProposerSlashing));
    attesterSlashingPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishAttesterSlashing));
    voluntaryExitPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishVoluntaryExit));
    blsToExecutionChangePool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishSignedBlsToExecutionChange));
    payloadAttestationPool.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishPayloadAttestationMessage));
    proposerPreferencesManager.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishProposerPreferences));
    executionPayloadBidManager.subscribeOperationAdded(
        new LocalOperationAcceptedFilter<>(p2pNetwork::publishExecutionPayloadBid));

    eventChannels.subscribe(
        CustodyGroupCountChannel.class,
        CustodyGroupCountChannel.createCustodyGroupCountSubscriber(
            cgc ->
                p2pNetwork
                    .getDiscoveryNetwork()
                    .ifPresent(
                        discoveryNetwork -> discoveryNetwork.setDASTotalCustodyGroupCount(cgc))));

    this.nodeId =
        p2pNetwork
            .getDiscoveryNodeId()
            .orElseThrow(
                () ->
                    new InvalidConfigurationException(
                        "Failed to get NodeId from Discovery System"));
  }

  protected Eth2P2PNetworkBuilder createEth2P2PNetworkBuilder() {
    return Eth2P2PNetworkBuilder.create();
  }

  protected void initSlotProcessor() {
    final FutureBlockProductionPreparationTrigger futureBlockProductionPreparationTrigger;

    if (beaconConfig.eth2NetworkConfig().isPrepareBlockProductionEnabled()) {
      futureBlockProductionPreparationTrigger =
          new FutureBlockProductionPreparationTrigger(
              recentChainData,
              beaconAsyncRunner,
              slot -> validatorApiHandler.onBlockProductionPreparationDue(slot));

      syncService.subscribeToSyncStateChangesAndUpdate(
          event ->
              futureBlockProductionPreparationTrigger.onSyncingStatusChanged(event.isInSync()));
    } else {
      futureBlockProductionPreparationTrigger = FutureBlockProductionPreparationTrigger.NOOP;
    }

    slotProcessor =
        new SlotProcessor(
            spec,
            recentChainData,
            syncService,
            forkChoiceTrigger,
            futureBlockProductionPreparationTrigger,
            forkChoiceNotifier,
            p2pNetwork,
            slotEventsChannelPublisher,
            new EpochCachePrimer(spec, recentChainData, beaconAsyncRunner));
  }

  public void initAggregatingAttestationPool() {
    LOG.debug("BeaconChainController.initAggregatingAttestationPool()");
    final Eth2NetworkConfiguration eth2NetworkConfiguration = beaconConfig.eth2NetworkConfig();

    final AggregatingAttestationPoolProfiler profiler =
        eth2NetworkConfiguration.isAggregatingAttestationPoolProfilingEnabled()
            ? new AggregatingAttestationPoolProfilerCSV(debugDataDirectory)
            : AggregatingAttestationPoolProfiler.NOOP;

    aggregatingAttestationPool =
        new AggregatingAttestationPoolV2(
            spec,
            recentChainData,
            metricsSystem,
            DEFAULT_MAXIMUM_ATTESTATION_COUNT,
            profiler,
            eth2NetworkConfiguration.getAggregatingAttestationPoolV2BlockAggregationTimeLimit(),
            eth2NetworkConfiguration
                .getAggregatingAttestationPoolV2TotalBlockAggregationTimeLimit());

    eventChannels.subscribe(SlotEventsChannel.class, aggregatingAttestationPool);
    blockImporter.subscribeToVerifiedBlockAttestations(
        aggregatingAttestationPool::onAttestationsIncludedInBlock);
  }

  public void initRestAPI() {
    LOG.debug("BeaconChainController.initRestAPI()");
    if (!beaconConfig.beaconRestApiConfig().isRestApiEnabled()) {
      LOG.info("rest-api-enabled is false, not starting rest api.");
      return;
    }
    final Eth1DataProvider eth1DataProvider = new Eth1DataProvider(eth1DataCache, depositProvider);

    final ExecutionClientDataProvider executionClientDataProvider =
        dataProvider.getExecutionClientDataProvider();

    eventChannels.subscribe(ExecutionClientEventsChannel.class, executionClientDataProvider);

    beaconRestAPI =
        Optional.of(
            new JsonTypeDefinitionBeaconRestApi(
                dataProvider,
                eth1DataProvider,
                beaconConfig.beaconRestApiConfig(),
                eventChannels,
                eventAsyncRunner,
                timeProvider,
                spec));

    if (getLivenessTrackingEnabled(beaconConfig)) {
      final int initialValidatorsCount =
          spec.getGenesisSpec().getConfig().getMinGenesisActiveValidatorCount();
      eventChannels.subscribe(
          ActiveValidatorChannel.class, new ActiveValidatorCache(spec, initialValidatorsCount));
    }
  }

  public void initBlockImporter() {
    LOG.debug("BeaconChainController.initBlockImporter()");
    blockImporter =
        new BlockImporter(
            beaconAsyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            executionLayer);
  }

  public void initBlockManager() {
    LOG.debug("BeaconChainController.initBlockManager()");
    final FutureItems<SignedBeaconBlock> futureBlocks =
        FutureItems.create(SignedBeaconBlock::getSlot, futureItemsMetric, "blocks");
    blockGossipValidator =
        new BlockGossipValidator(spec, gossipValidationHelper, receivedBlockEventsChannelPublisher);
    final BlockValidator blockValidator = new BlockValidator(blockGossipValidator);
    final Optional<BlockImportMetrics> importMetrics =
        beaconConfig.getMetricsConfig().isBlockPerformanceEnabled()
            ? Optional.of(BlockImportMetrics.create(metricsSystem))
            : Optional.empty();

    final BlockEventsListenerRouter blockEventsListenerRouter =
        new BlockEventsListenerRouter(
            blockBlobSidecarsTrackersPool, () -> dataAvailabilitySampler, recentChainData, spec);

    blockManager =
        new BlockManager(
            recentChainData,
            blockImporter,
            blockEventsListenerRouter,
            () -> dataAvailabilitySampler,
            pendingBlockPool,
            futureBlocks,
            invalidBlockRoots,
            blockValidator,
            timeProvider,
            EVENT_LOG,
            importMetrics);
    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      final FailedExecutionPool failedExecutionPool =
          new FailedExecutionPool(blockManager, beaconAsyncRunner);
      blockManager.subscribeFailedPayloadExecution(failedExecutionPool::addFailedBlock);
    }
    eventChannels
        .subscribe(SlotEventsChannel.class, blockManager)
        .subscribe(BlockImportChannel.class, blockManager)
        .subscribe(ReceivedBlockEventsChannel.class, blockManager)
        .subscribe(ReceivedExecutionPayloadEventsChannel.class, blockManager);
  }

  protected SyncServiceFactory createSyncServiceFactory() {
    syncPreImportBlockChannel = eventChannels.getPublisher(SyncPreImportBlockChannel.class);
    return new DefaultSyncServiceFactory(
        beaconConfig.syncConfig(),
        beaconConfig.eth2NetworkConfig().getNetworkBoostrapConfig().getGenesisState(),
        metricsSystem,
        asyncRunnerFactory,
        beaconAsyncRunner,
        timeProvider,
        recentChainData,
        combinedChainDataClient,
        storageUpdateChannel,
        syncPreImportBlockChannel,
        p2pNetwork,
        blockImporter,
        blobSidecarManager,
        executionPayloadManager,
        payloadAttestationPool,
        pendingBlocks,
        pendingAttestationPool.getAttestationsWaitingForBlock(),
        pendingPayloadAttestations,
        blockBlobSidecarsTrackersPool,
        dasSamplerBasic,
        beaconConfig.eth2NetworkConfig().getStartupTargetPeerCount(),
        signatureVerificationService,
        Duration.ofSeconds(beaconConfig.eth2NetworkConfig().getStartupTimeoutSeconds()),
        spec);
  }

  public void initSyncService() {
    LOG.debug("BeaconChainController.initSyncService()");
    syncService = createSyncServiceFactory().create(eventChannels);

    // chainHeadChannel subscription
    syncService.getForwardSync().subscribeToSyncChanges(coalescingChainHeadChannel);

    // forkChoiceNotifier subscription
    syncService.subscribeToSyncStateChangesAndUpdate(
        syncState -> forkChoiceNotifier.onSyncingStatusChanged(syncState.isInSync()));

    // forkChoice subscription
    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(syncService.getOptimisticSyncSubscriber());

    // terminalPowBlockMonitor subscription
    terminalPowBlockMonitor.ifPresent(
        monitor ->
            syncService.subscribeToSyncStateChangesAndUpdate(
                syncState -> monitor.onNodeSyncStateChanged(syncState.isInSync())));

    // p2pNetwork subscription so gossip can be enabled and disabled appropriately
    syncService.subscribeToSyncStateChangesAndUpdate(
        state ->
            p2pNetwork.onSyncStateChanged(recentChainData.isCloseToInSync(), state.isOptimistic()));

    syncService.subscribeToSyncStateChangesAndUpdate(
        event -> dataColumnSidecarCustodyRef.get().onSyncingStatusChanged(event.isInSync()));

    syncService.subscribeToSyncStateChangesAndUpdate(
        event -> dataColumnSidecarELManager.onSyncingStatusChanged(event.isInSync()));

    dasCustodyBackfiller.ifPresent(
        backfiller ->
            syncService.subscribeToSyncStateChangesAndUpdate(
                syncState -> backfiller.onNodeSyncStateChanged(syncState.isInSync())));
  }

  protected void initOperationsReOrgManager() {
    LOG.debug("BeaconChainController.initOperationsReOrgManager()");
    this.operationsReOrgManager =
        new OperationsReOrgManager(
            proposerSlashingPool,
            attesterSlashingPool,
            voluntaryExitPool,
            aggregatingAttestationPool,
            attestationManager,
            blsToExecutionChangePool,
            recentChainData);
    eventChannels.subscribe(ChainHeadChannel.class, operationsReOrgManager);
  }

  protected void initStoredLatestCanonicalBlockUpdater() {
    LOG.debug("BeaconChainController.initStoredLatestCanonicalBlockUpdater()");
    final StoredLatestCanonicalBlockUpdater storedLatestCanonicalBlockUpdater =
        new StoredLatestCanonicalBlockUpdater(recentChainData, spec);

    eventChannels.subscribe(SlotEventsChannel.class, storedLatestCanonicalBlockUpdater);
  }

  protected void initForkChoiceStateProvider() {
    LOG.debug("BeaconChainController.initForkChoiceStateProvider()");
    // When fast confirmation is enabled, the FCU safe_block_hash is derived from the confirmed
    // root (get_safe_execution_block_hash); otherwise the supplier is empty and the safe hash
    // defaults to the justified block, as before.
    forkChoiceStateProvider =
        new ForkChoiceStateProvider(
            forkChoiceExecutor,
            recentChainData,
            () ->
                fastConfirmationTracker
                    .getFastConfirmationStore()
                    .map(FastConfirmationStore::confirmedRoot));
  }

  protected void initForkChoiceNotifier() {
    LOG.debug("BeaconChainController.initForkChoiceNotifier()");
    final AsyncRunnerEventThread eventThread =
        new AsyncRunnerEventThread("forkChoiceNotifier", asyncRunnerFactory);
    eventThread.start();
    proposersDataManager =
        new ProposersDataManager(
            eventThread,
            spec,
            metricsSystem,
            executionLayer,
            recentChainData,
            getProposerDefaultFeeRecipient(),
            beaconConfig.eth2NetworkConfig().isForkChoiceUpdatedAlwaysSendPayloadAttributes(),
            proposerPreferencesManager);
    eventChannels.subscribe(SlotEventsChannel.class, proposersDataManager);
    forkChoiceNotifier =
        new ForkChoiceNotifierImpl(
            eventThread,
            timeProvider,
            spec,
            executionLayer,
            recentChainData,
            proposersDataManager,
            beaconConfig.eth2NetworkConfig().isForkChoiceLateBlockReorgEnabled());
  }

  private Optional<Eth1Address> getProposerDefaultFeeRecipient() {
    if (!spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      return Optional.of(Eth1Address.ZERO);
    }

    final Optional<Eth1Address> defaultFeeRecipient =
        beaconConfig.validatorConfig().getProposerDefaultFeeRecipient();

    if (defaultFeeRecipient.isEmpty() && beaconConfig.beaconRestApiConfig().isRestApiEnabled()) {
      STATUS_LOG.warnMissingProposerDefaultFeeRecipientWithRestAPIEnabled();
    }

    return defaultFeeRecipient;
  }

  protected void setupInitialState(final RecentChainData client) {
    final Eth2NetworkConfiguration networkConfiguration = beaconConfig.eth2NetworkConfig();

    final Optional<AnchorPoint> initialAnchor =
        tryLoadingAnchorPointFromInitialState(networkConfiguration)
            .or(
                () ->
                    attemptToLoadAnchorPoint(
                        networkConfiguration.getNetworkBoostrapConfig().getGenesisState()));

    /*
     If flag to allow sync outside of weak subjectivity period has been set, we pass an instance of
     WeakSubjectivityPeriodCalculator to the WeakSubjectivityInitializer. Otherwise, we pass an Optional.empty().
    */
    final Optional<WeakSubjectivityCalculator> maybeWsCalculator;
    if (isAllowSyncOutsideWeakSubjectivityPeriod()) {
      maybeWsCalculator = Optional.empty();
    } else {
      maybeWsCalculator = Optional.of(WeakSubjectivityCalculator.create(spec));
    }

    // Validate
    initialAnchor.ifPresent(
        anchor -> {
          final UInt64 currentSlot = getCurrentSlot(anchor.getState().getGenesisTime());
          wsInitializer.validateInitialAnchor(anchor, currentSlot, spec, maybeWsCalculator);
        });

    if (initialAnchor.isPresent()) {
      final AnchorPoint anchor = initialAnchor.get();
      client.initializeFromAnchorPoint(anchor, timeProvider.getTimeInSeconds());
      if (anchor.isGenesis()) {
        EVENT_LOG.genesisEvent(
            anchor.getStateRoot(),
            recentChainData.getBestBlockRoot().orElseThrow(),
            anchor.getState().getGenesisTime());
      }
    } else if (beaconConfig.interopConfig().isInteropEnabled()) {
      setupInteropState();
    } else if (!beaconConfig.powchainConfig().isEnabled()) {
      throw new InvalidConfigurationException(
          "ETH1 is disabled but initial state is unknown. Enable ETH1 or specify an initial state"
              + ".");
    }
  }

  private Optional<AnchorPoint> tryLoadingAnchorPointFromInitialState(
      final Eth2NetworkConfiguration networkConfiguration) {
    Optional<AnchorPoint> initialAnchor = Optional.empty();

    try {
      initialAnchor =
          attemptToLoadAnchorPoint(
              networkConfiguration.getNetworkBoostrapConfig().getInitialState());
    } catch (final InvalidConfigurationException e) {
      final StateBoostrapConfig stateBoostrapConfig =
          networkConfiguration.getNetworkBoostrapConfig();
      if (stateBoostrapConfig.isUsingCustomInitialState()
          && !stateBoostrapConfig.isUsingCheckpointSync()) {
        throw e;
      }
      STATUS_LOG.warnFailedToLoadInitialState(e.getMessage());
    }

    return initialAnchor;
  }

  protected Optional<AnchorPoint> attemptToLoadAnchorPoint(final Optional<String> initialState) {
    return wsInitializer.loadInitialAnchorPoint(spec, initialState);
  }

  protected void setupInteropState() {
    final InteropConfig config = beaconConfig.interopConfig();
    STATUS_LOG.generatingMockStartGenesis(
        config.getInteropGenesisTime(), config.getInteropNumberOfValidators());

    Optional<ExecutionPayloadHeader> executionPayloadHeader = Optional.empty();
    if (config.getInteropGenesisPayloadHeader().isPresent()) {
      try {
        executionPayloadHeader =
            Optional.of(
                spec.deserializeJsonExecutionPayloadHeader(
                    new ObjectMapper(),
                    config.getInteropGenesisPayloadHeader().get().toFile(),
                    GENESIS_SLOT));
      } catch (IOException e) {
        throw new RuntimeException(
            "Unable to load payload header from " + config.getInteropGenesisPayloadHeader().get(),
            e);
      }
    }

    final BeaconState genesisState =
        new GenesisStateBuilder()
            .spec(spec)
            .genesisTime(config.getInteropGenesisTime())
            .addMockValidators(config.getInteropNumberOfValidators())
            .executionPayloadHeader(executionPayloadHeader)
            .build();

    recentChainData.initializeFromGenesis(genesisState, timeProvider.getTimeInSeconds());

    EVENT_LOG.genesisEvent(
        genesisState.hashTreeRoot(),
        recentChainData.getBestBlockRoot().orElseThrow(),
        genesisState.getGenesisTime());
  }

  protected void onStoreInitialized() {
    UInt64 genesisTime = recentChainData.getGenesisTime();
    UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 currentSlot = getCurrentSlot(genesisTime, currentTime);
    if (currentTime.compareTo(genesisTime) >= 0) {
      // Validate that we're running within the weak subjectivity period
      validateChain(currentSlot);
    } else {
      UInt64 timeUntilGenesis = genesisTime.minus(currentTime);
      genesisTimeTracker = currentTime;
      STATUS_LOG.timeUntilGenesis(timeUntilGenesis.longValue(), p2pNetwork.getPeerCount());
    }
    slotProcessor.setCurrentSlot(currentSlot);
    performanceTracker.start(currentSlot);
  }

  protected UInt64 getCurrentSlot(final UInt64 genesisTime) {
    return getCurrentSlot(genesisTime, timeProvider.getTimeInSeconds());
  }

  protected UInt64 getCurrentSlot(final UInt64 genesisTime, final UInt64 currentTime) {
    return spec.getCurrentSlot(currentTime, genesisTime);
  }

  protected void validateChain(final UInt64 currentSlot) {
    weakSubjectivityValidator
        .validateChainIsConsistentWithWSCheckpoint(combinedChainDataClient)
        .thenCompose(
            __ ->
                SafeFuture.of(
                    () -> recentChainData.getStore().retrieveFinalizedCheckpointAndState()))
        .thenAccept(
            finalizedCheckpointState -> {
              final UInt64 slot = currentSlot.max(recentChainData.getCurrentSlot().orElse(ZERO));
              weakSubjectivityValidator.validateLatestFinalizedCheckpoint(
                  finalizedCheckpointState, slot);
            })
        .finish(
            err -> {
              weakSubjectivityValidator.handleValidationFailure(
                  "Encountered an error while trying to validate latest finalized checkpoint", err);
              throw new RuntimeException(err);
            });
  }

  private void onTick() {
    if (recentChainData.isPreGenesis()) {
      return;
    }

    final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
    final UInt64 currentTimeSeconds = millisToSeconds(currentTimeMillis);
    final Optional<TickProcessingPerformance> performanceRecord =
        beaconConfig.getMetricsConfig().isTickPerformanceEnabled()
            ? Optional.of(new TickProcessingPerformance(timeProvider, currentTimeMillis))
            : Optional.empty();

    forkChoice.onTick(currentTimeMillis, performanceRecord);

    final UInt64 genesisTime = recentChainData.getGenesisTime();
    if (genesisTime.isGreaterThan(currentTimeSeconds)) {
      // notify every 10 minutes
      if (genesisTimeTracker.plus(600L).isLessThanOrEqualTo(currentTimeSeconds)) {
        genesisTimeTracker = currentTimeSeconds;
        STATUS_LOG.timeUntilGenesis(
            genesisTime.minus(currentTimeSeconds).longValue(), p2pNetwork.getPeerCount());
      }
    }

    slotProcessor.onTick(currentTimeMillis, performanceRecord);
    performanceRecord.ifPresent(TickProcessingPerformance::complete);
  }

  boolean isSafeToDeactivateDenebFeatures() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return false;
    }
    final UInt64 fuluForkEpoch = spec.getGenesisSpec().getConfig().getFuluForkEpoch();
    final int minEpochsForBlobSidecarsRequests =
        SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig())
            .getMinEpochsForBlobSidecarsRequests();
    return recentChainData
        .getCurrentEpoch()
        .map(
            currentEpoch ->
                currentEpoch
                    .minusMinZero(fuluForkEpoch)
                    .isGreaterThan(minEpochsForBlobSidecarsRequests))
        .orElse(false);
  }

  @Override
  public Spec getSpec() {
    return spec;
  }

  @Override
  public TimeProvider getTimeProvider() {
    return timeProvider;
  }

  @Override
  public AsyncRunnerFactory getAsyncRunnerFactory() {
    return asyncRunnerFactory;
  }

  @Override
  public SignatureVerificationService getSignatureVerificationService() {
    return signatureVerificationService;
  }

  @Override
  public RecentChainData getRecentChainData() {
    return recentChainData;
  }

  @Override
  public CombinedChainDataClient getCombinedChainDataClient() {
    return combinedChainDataClient;
  }

  @Override
  public Eth2P2PNetwork getP2pNetwork() {
    return p2pNetwork;
  }

  @Override
  public Optional<BeaconRestApi> getBeaconRestAPI() {
    return beaconRestAPI;
  }

  @Override
  public SyncService getSyncService() {
    return syncService;
  }

  @Override
  public ForkChoice getForkChoice() {
    return forkChoice;
  }
}
