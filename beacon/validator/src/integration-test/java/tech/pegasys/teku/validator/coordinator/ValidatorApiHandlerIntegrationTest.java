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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.NOT_REQUIRED;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.beacon.sync.events.SyncStateTracker;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.MilestoneBasedBlockPublisher;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.DENEB, SpecMilestone.GLOAS})
public class ValidatorApiHandlerIntegrationTest {
  private final AsyncRunner asyncRunner = DelayedExecutorAsyncRunner.create();

  // Use full storage system
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final CombinedChainDataClient combinedChainDataClient =
      storageSystem.combinedChainDataClient();

  // Other dependencies are mocked, but these can be updated as needed
  private final SyncStateProvider syncStateProvider = mock(SyncStateTracker.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);
  private final ActiveValidatorTracker activeValidatorTracker = mock(ActiveValidatorTracker.class);
  private final DefaultPerformanceTracker performanceTracker =
      mock(DefaultPerformanceTracker.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  private final BlobSidecarGossipChannel blobSidecarGossipChannel =
      mock(BlobSidecarGossipChannel.class);
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
      mock(DataColumnSidecarGossipChannel.class);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  private final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);
  private final ExecutionPayloadFactory executionPayloadFactory =
      mock(ExecutionPayloadFactory.class);
  private final ExecutionPayloadPublisher executionPayloadPublisher =
      mock(ExecutionPayloadPublisher.class);
  private final ExecutionPayloadBidManager executionPayloadBidManager =
      mock(ExecutionPayloadBidManager.class);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  private final ChainUpdater chainUpdater = storageSystem.chainUpdater();
  private final SyncCommitteeMessagePool syncCommitteeMessagePool =
      mock(SyncCommitteeMessagePool.class);
  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
      mock(SyncCommitteeSubscriptionManager.class);
  private final PayloadAttestationPool payloadAttestationPool = mock(PayloadAttestationPool.class);

  private final DutyMetrics dutyMetrics = mock(DutyMetrics.class);

  private ValidatorApiHandler handler;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(forkChoiceTrigger.prepareForAttestationProduction(any())).thenReturn(SafeFuture.COMPLETE);
    when(dutyMetrics.getValidatorDutyMetric())
        .thenReturn(ValidatorDutyMetricUtils.createValidatorDutyMetric(new StubMetricsSystem()));

    when(blockGossipChannel.publishBlock(any())).thenReturn(SafeFuture.COMPLETE);
    when(blobSidecarGossipChannel.publishBlobSidecar(any())).thenReturn(SafeFuture.COMPLETE);
    when(blobSidecarGossipChannel.publishBlobSidecars(any())).thenReturn(SafeFuture.COMPLETE);

    doAnswer(invocation -> SafeFuture.completedFuture(Optional.of(invocation.getArgument(0))))
        .when(blockFactory)
        .unblindSignedBlockIfBlinded(any(), any());

    // BlobSidecar builder
    doAnswer(
            invocation -> {
              final SignedBlockContainer blockContainer = invocation.getArgument(0);
              final SpecVersion asspecVersion =
                  specContext.getSpec().forMilestone(SpecMilestone.DENEB);
              if (asspecVersion == null) {
                return List.of();
              }
              final MiscHelpersDeneb miscHelpersDeneb =
                  MiscHelpersDeneb.required(asspecVersion.miscHelpers());
              if (blockContainer.getBlobs().isEmpty()) {
                return List.of();
              }
              final SszList<Blob> blobs = blockContainer.getBlobs().orElseThrow();
              final SszList<SszKZGProof> proofs = blockContainer.getKzgProofs().orElseThrow();
              return IntStream.range(0, blobs.size())
                  .mapToObj(
                      index ->
                          miscHelpersDeneb.constructBlobSidecar(
                              blockContainer.getSignedBlock(),
                              UInt64.valueOf(index),
                              blobs.get(index),
                              proofs.get(index)))
                  .toList();
            })
        .when(blockFactory)
        .createBlobSidecars(any());

    final BlockProductionAndPublishingPerformanceFactory blockProductionPerformanceFactory =
        new BlockProductionAndPublishingPerformanceFactory(
            new SystemTimeProvider(), __ -> UInt64.ZERO, true, 0, 0, 0, 0, Optional.empty());
    final MilestoneBasedBlockPublisher blockPublisher =
        new MilestoneBasedBlockPublisher(
            asyncRunner,
            specContext.getSpec(),
            blockFactory,
            blockImportChannel,
            blockGossipChannel,
            blockBlobSidecarsTrackersPool,
            blobSidecarGossipChannel,
            dataColumnSidecarGossipChannel,
            dutyMetrics,
            CustodyGroupCountManager.NOOP,
            OptionalInt.empty(),
            P2PConfig.DEFAULT_GOSSIP_BLOBS_AFTER_BLOCK_ENABLED);
    handler =
        new ValidatorApiHandler(
            chainDataProvider,
            nodeDataProvider,
            networkDataProvider,
            combinedChainDataClient,
            syncStateProvider,
            blockFactory,
            attestationPool,
            attestationManager,
            attestationTopicSubscriber,
            activeValidatorTracker,
            dutyMetrics,
            performanceTracker,
            specContext.getSpec(),
            forkChoiceTrigger,
            proposersDataManager,
            syncCommitteeMessagePool,
            syncCommitteeContributionPool,
            syncCommitteeSubscriptionManager,
            blockProductionPerformanceFactory,
            blockPublisher,
            payloadAttestationPool,
            DataAvailabilitySampler.NOOP,
            executionPayloadManager,
            executionPayloadFactory,
            executionPayloadPublisher,
            executionPayloadBidManager,
            proposerPreferencesManager,
            ExecutionProofManager.NOOP);
  }

  @TestTemplate
  public void createAttestationData_withRecentBlockAvailable(final SpecContext specContext) {
    specContext.assumeIsNotOneOf(SpecMilestone.DENEB);
    final UInt64 targetEpoch = UInt64.valueOf(3);
    final UInt64 targetEpochStartSlot = specContext.getSpec().computeStartSlotAtEpoch(targetEpoch);
    final UInt64 targetSlot = targetEpochStartSlot.plus(2);

    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final Checkpoint genesisCheckpoint = genesis.getState().getFinalizedCheckpoint();

    SignedBlockAndState latestBlock = null;
    SignedBlockAndState epochBoundaryBlock = null;
    while (chainUpdater.getHeadSlot().compareTo(targetSlot) < 0) {
      latestBlock = chainUpdater.advanceChain();
      chainUpdater.updateBestBlock(latestBlock);
      if (latestBlock.getSlot().equals(targetEpochStartSlot)) {
        epochBoundaryBlock = latestBlock;
      }
    }
    assertThat(latestBlock).isNotNull();
    assertThat(epochBoundaryBlock).isNotNull();
    final Checkpoint expectedTarget = new Checkpoint(targetEpoch, epochBoundaryBlock.getRoot());

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        handler.createAttestationData(targetSlot, committeeIndex);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final AttestationData attestation = safeJoin(result).orElseThrow();
    assertThat(attestation.getBeaconBlockRoot()).isEqualTo(latestBlock.getRoot());
    assertThat(attestation.getSource()).isEqualTo(genesisCheckpoint);
    assertThat(attestation.getTarget()).isEqualTo(expectedTarget);
  }

  @TestTemplate
  public void createUnsignedAttestation_withLatestBlockFromAnOldEpoch(
      final SpecContext specContext) {
    specContext.assumeIsNotOneOf(SpecMilestone.DENEB);
    final UInt64 latestEpoch = UInt64.valueOf(2);
    final UInt64 latestSlot = specContext.getSpec().computeStartSlotAtEpoch(latestEpoch).plus(ONE);
    final UInt64 targetEpoch = latestEpoch.plus(3);
    final UInt64 targetEpochStartSlot = specContext.getSpec().computeStartSlotAtEpoch(targetEpoch);
    final UInt64 targetSlot = targetEpochStartSlot.plus(2);

    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final Checkpoint genesisCheckpoint = genesis.getState().getFinalizedCheckpoint();

    SignedBlockAndState latestBlock = null;
    while (chainUpdater.getHeadSlot().compareTo(latestSlot) < 0) {
      latestBlock = chainUpdater.advanceChain();
      chainUpdater.updateBestBlock(latestBlock);
    }
    chainUpdater.setCurrentSlot(targetSlot);
    assertThat(latestBlock).isNotNull();
    final Checkpoint expectedTarget = new Checkpoint(targetEpoch, latestBlock.getRoot());

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        handler.createAttestationData(targetSlot, committeeIndex);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final AttestationData attestation = safeJoin(result).orElseThrow();
    assertThat(attestation.getBeaconBlockRoot()).isEqualTo(latestBlock.getRoot());
    assertThat(attestation.getSource()).isEqualTo(genesisCheckpoint);
    assertThat(attestation.getTarget()).isEqualTo(expectedTarget);
  }

  @TestTemplate
  void sendSignedBlock_shouldImportAndPublishBlock(final SpecContext specContext) {
    final SignedBeaconBlock block = specContext.getDataStructureUtil().randomSignedBeaconBlock(5);

    when(blockImportChannel.importBlock(block, NOT_REQUIRED))
        .thenReturn(prepareBlockImportResult(BlockImportResult.successful(block)));
    final SafeFuture<SendSignedBlockResult> result = handler.sendSignedBlock(block, NOT_REQUIRED);
    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));

    if (specContext.getSpecMilestone() == SpecMilestone.DENEB) {
      verify(blobSidecarGossipChannel).publishBlobSidecars(any());
    }
    verify(blockGossipChannel).publishBlock(block);
    verify(blockImportChannel).importBlock(block, NOT_REQUIRED);
  }

  @TestTemplate
  void sendSignedProposerPreferences_shouldReturnIndexedRejections(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final SignedProposerPreferences accepted =
        specContext.getDataStructureUtil().randomSignedProposerPreferences();
    final SignedProposerPreferences rejected =
        specContext.getDataStructureUtil().randomSignedProposerPreferences();
    final String rejectionDescription = "Invalid proposer preferences signature";

    when(proposerPreferencesManager.addLocal(accepted))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(proposerPreferencesManager.addLocal(rejected))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResult.reject(rejectionDescription)));

    final List<SignedProposerPreferences> signedProposerPreferences = List.of(accepted, rejected);
    final SafeFuture<List<SubmitDataError>> result =
        handler.sendSignedProposerPreferences(signedProposerPreferences);

    assertThatSafeFuture(result)
        .isCompletedWithValue(List.of(new SubmitDataError(ONE, rejectionDescription)));
    verify(proposerPreferencesManager).addLocal(accepted);
    verify(proposerPreferencesManager).addLocal(rejected);
    verify(proposersDataManager)
        .updatePreparedProposersFromProposerPreferences(signedProposerPreferences, UInt64.ZERO);
  }

  @TestTemplate
  void sendSignedProposerPreferences_shouldIgnoreNonRejects(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final SignedProposerPreferences ignored =
        specContext.getDataStructureUtil().randomSignedProposerPreferences();
    final SignedProposerPreferences savedForFuture =
        specContext.getDataStructureUtil().randomSignedProposerPreferences();

    when(proposerPreferencesManager.addLocal(ignored))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    when(proposerPreferencesManager.addLocal(savedForFuture))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final List<SignedProposerPreferences> signedProposerPreferences =
        List.of(ignored, savedForFuture);
    final SafeFuture<List<SubmitDataError>> result =
        handler.sendSignedProposerPreferences(signedProposerPreferences);

    assertThatSafeFuture(result).isCompletedWithValue(List.of());
    verify(proposerPreferencesManager).addLocal(ignored);
    verify(proposerPreferencesManager).addLocal(savedForFuture);
    verify(proposersDataManager)
        .updatePreparedProposersFromProposerPreferences(signedProposerPreferences, UInt64.ZERO);
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults> prepareBlockImportResult(
      final BlockImportResult blockImportResult) {
    return SafeFuture.completedFuture(
        new BlockImportAndBroadcastValidationResults(
            SafeFuture.completedFuture(blockImportResult)));
  }
}
