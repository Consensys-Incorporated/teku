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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.EQUIVOCATION;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.GOSSIP;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.NOT_REQUIRED;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDuties;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionAndPublishingPerformanceFactory;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
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
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;
import tech.pegasys.teku.validator.coordinator.publisher.BlockPublisher;
import tech.pegasys.teku.validator.coordinator.publisher.ExecutionPayloadPublisher;

class ValidatorApiHandlerTest {
  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 EPOCH = UInt64.valueOf(13);
  private static final UInt64 PREVIOUS_EPOCH = EPOCH.minus(ONE);

  private final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
  private final ChainHead chainHead = mock(ChainHead.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final SyncStateProvider syncStateProvider = mock(SyncStateProvider.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriptions =
      mock(AttestationTopicSubscriber.class);
  private final ActiveValidatorTracker activeValidatorTracker = mock(ActiveValidatorTracker.class);
  private final BlockPublisher blockPublisher = mock(BlockPublisher.class);
  private final ExecutionProofManager executionProofManager = ExecutionProofManager.NOOP;
  private final DefaultPerformanceTracker performanceTracker =
      mock(DefaultPerformanceTracker.class);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  private final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  private final NetworkDataProvider networkDataProvider = mock(NetworkDataProvider.class);
  private final DutyMetrics dutyMetrics = mock(DutyMetrics.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final PayloadAttestationPool payloadAttestationPool = mock(PayloadAttestationPool.class);
  private final DataAvailabilitySampler dataAvailabilitySampler =
      mock(DataAvailabilitySampler.class);
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

  private final SyncCommitteeMessagePool syncCommitteeMessagePool =
      mock(SyncCommitteeMessagePool.class);
  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
      mock(SyncCommitteeSubscriptionManager.class);

  private final BlockProductionAndPublishingPerformanceFactory blockProductionPerformanceFactory =
      spy(
          new BlockProductionAndPublishingPerformanceFactory(
              StubTimeProvider.withTimeInMillis(0),
              __ -> ZERO,
              false,
              0,
              0,
              0,
              0,
              Optional.empty()));

  private Spec spec;
  private UInt64 epochStartSlot;
  private UInt64 previousEpochStartSlot;

  private DataStructureUtil dataStructureUtil;
  private ValidatorApiHandler validatorApiHandler;

  @BeforeEach
  public void setUp() {
    this.spec = TestSpecFactory.createMinimalGloas();
    this.epochStartSlot = spec.computeStartSlotAtEpoch(EPOCH);
    this.previousEpochStartSlot = spec.computeStartSlotAtEpoch(PREVIOUS_EPOCH);
    this.dataStructureUtil = new DataStructureUtil(spec);
    when(dutyMetrics.getValidatorDutyMetric())
        .thenReturn(ValidatorDutyMetricUtils.createValidatorDutyMetric(new StubMetricsSystem()));
    this.validatorApiHandler =
        new ValidatorApiHandler(
            chainDataProvider,
            nodeDataProvider,
            networkDataProvider,
            chainDataClient,
            syncStateProvider,
            blockFactory,
            attestationPool,
            attestationManager,
            attestationTopicSubscriptions,
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

    doReturn(BlockProductionPerformance.NOOP)
        .when(blockProductionPerformanceFactory)
        .createForProduction(any());
    when(chainDataClient.getStore()).thenReturn(store);
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(forkChoiceTrigger.prepareForBlockProduction(any(), any()))
        .thenReturn(SafeFuture.completedFuture(chainHead));
    when(chainHead.getPayloadStatus()).thenReturn(PAYLOAD_STATUS_PENDING);
    when(chainDataClient.isOptimisticBlock(any())).thenReturn(false);
    doAnswer(invocation -> SafeFuture.completedFuture(invocation.getArgument(0)))
        .when(blockFactory)
        .unblindSignedBlockIfBlinded(any(), any());
    when(proposersDataManager.updateValidatorRegistrations(any(), any()))
        .thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(2));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadALittleBehind() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(1));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_syncIsActiveAndHeadIsCaughtUp() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_inSyncAndHeadIsRecent() {
    setupSyncingState(SyncState.IN_SYNC, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_inSyncAndHeadIsOld() {
    setupSyncingState(SyncState.IN_SYNC, EPOCH, EPOCH.minus(5));
    assertThat(validatorApiHandler.isSyncActive()).isFalse();
  }

  @Test
  public void isSyncActive_startingUpAndHeadIsBehind() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH.minus(2));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_startingUpAndHeadALittleBehind() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH.minus(1));
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  @Test
  public void isSyncActive_startingUpAndHeadIsCaughtUp() {
    setupSyncingState(SyncState.START_UP, EPOCH, EPOCH);
    assertThat(validatorApiHandler.isSyncActive()).isTrue();
  }

  private void nodeIsSyncing() {
    setupSyncingState(SyncState.SYNCING, EPOCH, EPOCH.minus(2));
  }

  private void setupSyncingState(
      final SyncState syncState, final UInt64 currentEpoch, final UInt64 headEpoch) {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(syncState);
    when(chainDataClient.getCurrentEpoch()).thenReturn(currentEpoch);
    when(chainDataClient.getHeadEpoch()).thenReturn(headEpoch);
  }

  @Test
  public void getAttestationDuties_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<AttesterDuties>> duties =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1));
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnNoDutiesWhenNoIndicesSpecified() {
    final BeaconState state = createStateWithActiveValidators();
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties()).isEmpty();
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldUsePreviousDutyDependentRootWhenStateFromSameEpoch() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of());
    final AttesterDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getPreviousDutyDependentRoot(state));
  }

  @Test
  public void getAttestationDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(3));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1));
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getAttestationDuties_shouldReturnDutiesAndSkipMissingValidators() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkeyBytes());
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1, 32));
    final Optional<AttesterDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .containsExactly(new AttesterDuty(validator1Key, 1, 4, 0, 1, 1, UInt64.valueOf(108)));
  }

  @Test
  public void getAttestationDuties_shouldAllowOneEpochTolerance() {
    final BeaconState state = createStateWithActiveValidators();
    final BLSPublicKey validator1Key =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(1).getPubkeyBytes());
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(2));

    final SafeFuture<Optional<AttesterDuties>> result =
        validatorApiHandler.getAttestationDuties(EPOCH, IntList.of(1, 32));
    final Optional<AttesterDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .containsExactly(new AttesterDuty(validator1Key, 1, 4, 0, 1, 1, UInt64.valueOf(108)));
  }

  @Test
  public void getProposerDuties_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<ProposerDuties>> duties =
        validatorApiHandler.getProposerDuties(EPOCH, false);
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getProposerDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(3));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH, false);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getProposerDuties_shouldReturnDutiesForNextEpoch() {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH.increment(), true);
    final ProposerDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties().size()).isEqualTo(spec.slotsPerEpoch(EPOCH.increment()));
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getProposerDuties_shouldReturnDutiesForCurrentEpoch() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH, true);
    final ProposerDuties duties = assertCompletedSuccessfully(result).orElseThrow();
    assertThat(duties.getDuties().size()).isEqualTo(spec.slotsPerEpoch(EPOCH));
    assertThat(duties.getDependentRoot()).isEqualTo(spec.getCurrentDutyDependentRoot(state));
  }

  @Test
  public void getProposerDuties_shouldAllowOneEpochTolerance() {
    final UInt64 epoch = EPOCH.minus(1);
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    final UInt64 querySlot = spec.computeStartSlotAtEpoch(epoch);
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();

    LOG.debug(
        "Epoch Start slot {}, Test Epoch {}, expect query slot {}",
        epochStartSlot,
        epoch,
        querySlot);
    when(chainDataClient.getStateAtSlotExact(querySlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(epoch);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH, false);
    final Optional<ProposerDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties().size()).isEqualTo(spec.slotsPerEpoch(EPOCH));
  }

  @Test
  void getProposerDuties_shouldReturnDutiesInOrder() {
    final BeaconState state = createStateWithActiveValidators(epochStartSlot);
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();
    when(chainDataClient.getStateAtSlotExact(epochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestBlockRoot()).thenReturn(Optional.of(headRoot));

    final SafeFuture<Optional<ProposerDuties>> result =
        validatorApiHandler.getProposerDuties(EPOCH, true);
    final Optional<ProposerDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().getDuties())
        .isSortedAccordingTo(Comparator.comparing(ProposerDuty::getSlot));
  }

  @Test
  void getSyncCommitteeDuties_shouldFailForEpochTooFarAhead() {
    final BeaconState state = dataStructureUtil.stateBuilderAltair().slot(epochStartSlot).build();
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    final int epochsPerSyncCommitteePeriod =
        SpecConfigAltair.required(spec.getSpecConfig(EPOCH)).getEpochsPerSyncCommitteePeriod();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(epochStartSlot);
    final UInt64 firstSlotAfterNextSyncCommitteePeriod =
        syncCommitteeUtil
            .computeFirstEpochOfCurrentSyncCommitteePeriod(EPOCH)
            .plus(epochsPerSyncCommitteePeriod * 2L);
    assertThatSafeFuture(
            validatorApiHandler.getSyncCommitteeDuties(
                firstSlotAfterNextSyncCommitteePeriod, IntList.of(1)))
        .isCompletedExceptionallyWith(IllegalArgumentException.class)
        .hasMessageContaining("not within the current or next sync committee periods");
  }

  @Test
  void getSyncCommitteeDuties_shouldNotUseEpochPriorToFork() {
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(EPOCH);
    final ValidatorApiHandler validatorApiHandler =
        new ValidatorApiHandler(
            chainDataProvider,
            nodeDataProvider,
            networkDataProvider,
            chainDataClient,
            syncStateProvider,
            blockFactory,
            attestationPool,
            attestationManager,
            attestationTopicSubscriptions,
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
    dataStructureUtil = new DataStructureUtil(spec);
    // Best state is still in Phase0
    final BeaconState state =
        dataStructureUtil.stateBuilderPhase0().slot(previousEpochStartSlot.minus(1)).build();
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH);
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(chainDataClient.getStateAtSlotExact(any())).thenReturn(new SafeFuture<>());

    final SafeFuture<Optional<SyncCommitteeDuties>> result =
        validatorApiHandler.getSyncCommitteeDuties(EPOCH, IntList.of(1));
    assertThat(result).isNotDone();

    // The start of the sync committee period is prior to the fork block so we should use the
    // fork block to ensure we actually have sync committees available.
    verify(chainDataClient).getStateAtSlotExact(epochStartSlot);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorApiHandler.createUnsignedBlock(
            ONE, dataStructureUtil.randomSignature(), Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedBlock_shouldFailWhenParentBlockIsOptimistic() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    mockRequiredMethodsForBlockProduction(blockSlotState, newSlot);
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, newSlot.minus(1));
    when(chainDataClient.isOptimisticBlock(parentRoot)).thenReturn(true);

    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, dataStructureUtil.randomSignature(), Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
    verifyNoInteractions(blockFactory);
  }

  @Test
  public void createUnsignedBlock_shouldCreateBlock() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(newSlot);

    mockRequiredMethodsForBlockProduction(blockSlotState, newSlot);
    when(blockFactory.createUnsignedBlock(any()))
        .thenReturn(SafeFuture.completedFuture(blockContainerAndMetaData));

    // even if passing a non-empty requestedBlinded and requestedBuilderBoostFactor isn't a valid
    // combination,
    // we still want to check that all parameters are passed down the line to the block factory
    SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, randaoReveal, Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedWithValue(Optional.of(blockContainerAndMetaData));

    // further calls in the same slot should return the same block
    result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, randaoReveal, Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedWithValue(Optional.of(blockContainerAndMetaData));

    // only produced once
    verify(blockFactory).createUnsignedBlock(any());

    verify(performanceTracker).reportBlockProductionAttempt(spec.computeEpochAtSlot(newSlot));
    verify(performanceTracker)
        .saveProducedBlock(
            blockContainerAndMetaData.blockContainer().getBlock().getSlotAndBlockRoot());
  }

  @Test
  public void createUnsignedBlock_shouldAllowProducingBlockTwiceIfFirstAttemptFailed() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(newSlot);

    mockRequiredMethodsForBlockProduction(blockSlotState, newSlot);

    when(blockFactory.createUnsignedBlock(any()))
        .thenThrow(new IllegalStateException("oopsy"))
        .thenReturn(SafeFuture.completedFuture(blockContainerAndMetaData));

    // first call should fail
    SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, randaoReveal, Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedExceptionally();

    // second call in the same slot should succeed and return the block
    result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, randaoReveal, Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedWithValue(Optional.of(blockContainerAndMetaData));

    // attempted to produce twice
    verify(blockFactory, times(2)).createUnsignedBlock(any());
  }

  @Test
  public void onBlockProductionPreparationDue_shouldReusePreparedChainHeadWhenCreatingBlock() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(newSlot);
    final ChainHead selectedChainHead = mock(ChainHead.class);
    final Bytes32 parentRoot =
        mockRequiredMethodsForBlockProduction(
            selectedChainHead, blockSlotState, newSlot, PAYLOAD_STATUS_FULL);

    when(blockFactory.createUnsignedBlock(any()))
        .thenReturn(SafeFuture.completedFuture(blockContainerAndMetaData));

    validatorApiHandler.onBlockProductionPreparationDue(newSlot);

    final SafeFuture<Optional<BlockContainerAndMetaData>> result =
        validatorApiHandler.createUnsignedBlock(
            newSlot, randaoReveal, Optional.empty(), Optional.of(ONE));

    assertThat(result).isCompletedWithValue(Optional.of(blockContainerAndMetaData));

    verify(forkChoiceTrigger).prepareForBlockProduction(eq(newSlot), any());
    verify(chainDataClient).getStateForBlockProduction(same(selectedChainHead), eq(newSlot));

    final ArgumentCaptor<BlockProductionContext> blockProductionContextCaptor =
        ArgumentCaptor.forClass(BlockProductionContext.class);
    verify(blockFactory).createUnsignedBlock(blockProductionContextCaptor.capture());

    final BlockProductionContext blockProductionContext = blockProductionContextCaptor.getValue();
    assertThat(blockProductionContext.blockSlotState()).isSameAs(blockSlotState);
    assertThat(blockProductionContext.parentRoot()).isEqualTo(parentRoot);
    assertThat(blockProductionContext.parentPayloadStatus()).isEqualTo(PAYLOAD_STATUS_FULL);
  }

  @Test
  public void createAttestationData_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(ONE, 1);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAttestationData_shouldFailWhenHeadIsOptimistic() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);

    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(epochStartSlot);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    when(chainDataClient.isOptimisticBlock(blockAndState.getRoot())).thenReturn(true);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAttestationData_shouldCreateAttestation() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(epochStartSlot);

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompleted();
    final Optional<AttestationData> maybeAttestation = safeJoin(result);
    assertThat(maybeAttestation).isPresent();
    final AttestationData attestationData = maybeAttestation.orElseThrow();
    assertThat(attestationData)
        .isEqualTo(
            spec.getGenericAttestationData(
                slot,
                blockAndState.getState(),
                blockAndState.getBlock().getMessage(),
                UInt64.valueOf(committeeIndex)));
    assertThat(attestationData.getIndex()).isEqualTo(ZERO);
    assertThat(attestationData.getSlot()).isEqualTo(slot);
    final InOrder inOrder = inOrder(forkChoiceTrigger, chainDataClient);

    // Ensure we prepare for attestation production prior to getting the block to attest to
    inOrder.verify(forkChoiceTrigger).prepareForAttestationProduction(slot);
    inOrder.verify(chainDataClient).getSignedBlockAndStateInEffectAtSlot(slot);
  }

  @Test
  public void createAttestationData_shouldRejectRequestWhenSlotIsInTheFuture() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH).plus(ONE);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot.minus(1));

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThatSafeFuture(result).isCompletedExceptionallyWith(IllegalArgumentException.class);
  }

  @Test
  public void createAttestationData_shouldUseCorrectSourceWhenEpochTransitionRequired() {
    final UInt64 slot = spec.computeStartSlotAtEpoch(EPOCH);
    when(chainDataClient.getCurrentSlot()).thenReturn(slot);
    // Slot is from before the current epoch, so we need to ensure we process the epoch transition
    final UInt64 blockSlot = slot.minus(1);

    final BeaconState rightState = createStateWithActiveValidators(slot);
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(blockSlot);
    final SignedBeaconBlock block = blockAndState.getBlock();

    final SafeFuture<Optional<SignedBlockAndState>> blockAndStateResult =
        completedFuture(Optional.of(blockAndState));
    when(chainDataClient.getSignedBlockAndStateInEffectAtSlot(slot))
        .thenReturn(blockAndStateResult);

    when(chainDataClient.getCheckpointState(EPOCH, blockAndState))
        .thenReturn(
            SafeFuture.completedFuture(
                CheckpointState.create(
                    spec, new Checkpoint(EPOCH, block.getRoot()), block, rightState)));
    when(forkChoiceTrigger.prepareForAttestationProduction(slot)).thenReturn(SafeFuture.COMPLETE);

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        validatorApiHandler.createAttestationData(slot, committeeIndex);

    assertThat(result).isCompleted();
    final Optional<AttestationData> maybeAttestation = safeJoin(result);
    assertThat(maybeAttestation).isPresent();
    final AttestationData attestationData = maybeAttestation.orElseThrow();
    assertThat(attestationData)
        .isEqualTo(
            spec.getGenericAttestationData(
                slot, rightState, block.getMessage(), UInt64.valueOf(committeeIndex)));
    assertThat(attestationData.getIndex()).isEqualTo(ZERO);
    assertThat(attestationData.getSlot()).isEqualTo(slot);
  }

  @Test
  public void createAggregate_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<Attestation>> result =
        validatorApiHandler.createAggregate(
            ONE, dataStructureUtil.randomAttestationData().hashTreeRoot(), Optional.empty());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createSyncCommitteeContribution() {
    nodeIsSyncing();
    final SafeFuture<Optional<SyncCommitteeContribution>> result =
        validatorApiHandler.createSyncCommitteeContribution(
            ONE, 0, dataStructureUtil.randomBytes32());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createAggregate_shouldReturnAggregateFromAttestationPool() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final Optional<Attestation> aggregate = Optional.of(dataStructureUtil.randomAttestation());
    when(attestationPool.createAggregateFor(
            eq(attestationData.hashTreeRoot()), eq(Optional.empty())))
        .thenReturn(aggregate);

    assertThat(
            validatorApiHandler.createAggregate(
                aggregate.get().getData().getSlot(),
                attestationData.hashTreeRoot(),
                Optional.empty()))
        .isCompletedWithValue(aggregate);
  }

  @Test
  public void subscribeToBeaconCommittee_shouldSubscribeViaAttestationTopicSubscriptions() {
    final int committeeIndex = 10;
    final UInt64 aggregationSlot = UInt64.valueOf(13);
    final UInt64 committeesAtSlot = UInt64.valueOf(10);
    final int validatorIndex = 1;

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToBeaconCommittee(
            List.of(
                new CommitteeSubscriptionRequest(
                    validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, true)));

    assertThat(result).isCompleted();
    verify(attestationTopicSubscriptions)
        .subscribeToCommitteeForAggregation(committeeIndex, committeesAtSlot, aggregationSlot);
    verify(activeValidatorTracker).onCommitteeSubscriptionRequest(validatorIndex, aggregationSlot);
  }

  @Test
  public void subscribeToBeaconCommittee_shouldUpdateActiveValidatorTrackerWhenNotAggregating() {
    final int committeeIndex = 10;
    final UInt64 aggregationSlot = UInt64.valueOf(13);
    final UInt64 committeesAtSlot = UInt64.valueOf(10);
    final int validatorIndex = 1;

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToBeaconCommittee(
            List.of(
                new CommitteeSubscriptionRequest(
                    validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, false)));

    assertThat(result).isCompleted();
    verifyNoInteractions(attestationTopicSubscriptions);
    verify(activeValidatorTracker).onCommitteeSubscriptionRequest(validatorIndex, aggregationSlot);
  }

  @Test
  void subscribeToSyncCommitteeSubnets_shouldConvertCommitteeIndexToSubnetId() {
    final SyncCommitteeSubnetSubscription subscription1 =
        new SyncCommitteeSubnetSubscription(1, IntSet.of(1, 2, 15, 30), UInt64.valueOf(44));
    final SyncCommitteeSubnetSubscription subscription2 =
        new SyncCommitteeSubnetSubscription(1, IntSet.of(5, 10), UInt64.valueOf(35));

    final SafeFuture<Void> result =
        validatorApiHandler.subscribeToSyncCommitteeSubnets(List.of(subscription1, subscription2));

    assertThat(result).isCompleted();

    final UInt64 unsubscribeSlotSubscription1 = spec.computeStartSlotAtEpoch(UInt64.valueOf(44));
    final UInt64 unsubscribeSlotSubscription2 = spec.computeStartSlotAtEpoch(UInt64.valueOf(35));

    verify(syncCommitteeSubscriptionManager).subscribe(0, unsubscribeSlotSubscription1);
    verify(syncCommitteeSubscriptionManager).subscribe(1, unsubscribeSlotSubscription1);
    verify(syncCommitteeSubscriptionManager).subscribe(3, unsubscribeSlotSubscription1);

    verify(syncCommitteeSubscriptionManager).subscribe(0, unsubscribeSlotSubscription2);
    verify(syncCommitteeSubscriptionManager).subscribe(1, unsubscribeSlotSubscription2);
    verifyNoMoreInteractions(syncCommitteeSubscriptionManager);
  }

  @Test
  public void sendSignedAttestations_shouldAddAttestationToAttestationManager() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidatableAttestation.class), any()))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(attestationManager)
        .addAttestation(ValidatableAttestation.from(spec, attestation), Optional.empty());
  }

  @Test
  void sendSignedAttestations_shouldSaveConvertedAttestationFromSingleAttestation() {
    final Attestation attestation = dataStructureUtil.randomSingleAttestation();
    final Attestation convertedAttestation = dataStructureUtil.randomAttestation();
    doAnswer(
            invocation -> {
              invocation
                  .getArgument(0, ValidatableAttestation.class)
                  .convertToAggregatedFormatFromSingleAttestation(convertedAttestation);
              return completedFuture(InternalValidationResult.ACCEPT);
            })
        .when(attestationManager)
        .addAttestation(any(ValidatableAttestation.class), any());

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(dutyMetrics).onAttestationPublished(convertedAttestation.getData().getSlot());
    verify(performanceTracker).saveProducedAttestation(convertedAttestation);
  }

  @Test
  void sendSignedAttestations_shouldAddToDutyMetricsAndPerformanceTrackerWhenNotInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidatableAttestation.class), any()))
        .thenReturn(completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(dutyMetrics).onAttestationPublished(attestation.getData().getSlot());
    verify(performanceTracker).saveProducedAttestation(attestation);
  }

  @Test
  void sendSignedAttestations_shouldNotAddToDutyMetricsAndPerformanceTrackerWhenInvalid() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(ValidatableAttestation.class), any()))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    verify(dutyMetrics, never()).onAttestationPublished(attestation.getData().getSlot());
    verify(performanceTracker, never()).saveProducedAttestation(attestation);
  }

  @Test
  void sendSignedAttestations_shouldProcessMixOfValidAndInvalidAttestations() {
    final Attestation invalidAttestation = dataStructureUtil.randomAttestation();
    final Attestation validAttestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(validatableAttestationOf(invalidAttestation), any()))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));
    when(attestationManager.addAttestation(validatableAttestationOf(validAttestation), any()))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(invalidAttestation, validAttestation));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    verify(dutyMetrics, never()).onAttestationPublished(invalidAttestation.getData().getSlot());
    verify(dutyMetrics).onAttestationPublished(validAttestation.getData().getSlot());
    verify(performanceTracker, never()).saveProducedAttestation(invalidAttestation);
    verify(performanceTracker).saveProducedAttestation(validAttestation);
  }

  private ValidatableAttestation validatableAttestationOf(final Attestation validAttestation) {
    return argThat(
        argument -> argument != null && argument.getAttestation().equals(validAttestation));
  }

  @Test
  public void sendSignedBlock_shouldPublish() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockPublisher.sendSignedBlock(eq(block), eq(NOT_REQUIRED), any()))
        .thenReturn(SafeFuture.completedFuture(SendSignedBlockResult.success(block.getRoot())));
    final SafeFuture<SendSignedBlockResult> result =
        validatorApiHandler.sendSignedBlock(block, NOT_REQUIRED);

    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));
  }

  @Test
  public void sendSignedBlock_shouldCatchPublishFailure() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(blockPublisher.sendSignedBlock(eq(block), eq(NOT_REQUIRED), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed to publish block")));

    final SafeFuture<SendSignedBlockResult> result =
        validatorApiHandler.sendSignedBlock(block, NOT_REQUIRED);

    assertThat(result)
        .isCompletedWithValue(SendSignedBlockResult.rejected("Failed to publish block"));
  }

  @Test
  public void
      sendSignedBlock_shouldOnlyDoEquivocationValidationIfBlockIsLocallyCreatedAngGossipValidationRequested() {
    // creating a block first in order to cache the block root
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(newSlot);
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(newSlot);

    mockRequiredMethodsForBlockProduction(blockSlotState, newSlot);
    when(blockFactory.createUnsignedBlock(any()))
        .thenReturn(SafeFuture.completedFuture(blockContainerAndMetaData));

    assertThat(
            validatorApiHandler.createUnsignedBlock(
                newSlot, randaoReveal, Optional.empty(), Optional.of(ONE)))
        .isCompleted();

    final SignedBeaconBlock block =
        dataStructureUtil
            .getSpec()
            .atSlot(newSlot)
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .create(
                blockContainerAndMetaData.blockContainer().getBlock(),
                dataStructureUtil.randomSignature());

    when(blockPublisher.sendSignedBlock(eq(block), eq(EQUIVOCATION), any()))
        .thenReturn(SafeFuture.completedFuture(SendSignedBlockResult.success(block.getRoot())));

    // require GOSSIP validation
    final SafeFuture<SendSignedBlockResult> result =
        validatorApiHandler.sendSignedBlock(block, GOSSIP);

    assertThat(result).isCompletedWithValue(SendSignedBlockResult.success(block.getRoot()));

    // for locally created blocks, the validation level should have been changed to EQUIVOCATION
    verify(blockPublisher).sendSignedBlock(eq(block), eq(EQUIVOCATION), any());
  }

  @Test
  public void sendAggregateAndProofs_shouldPostAggregateAndProof() {
    final SignedAggregateAndProof aggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.addAggregate(any(ValidatableAttestation.class), any()))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendAggregateAndProofs(List.of(aggregateAndProof));
    assertThat(result).isCompletedWithValue(emptyList());

    verify(attestationManager)
        .addAggregate(
            ValidatableAttestation.aggregateFromValidator(spec, aggregateAndProof),
            Optional.empty());
  }

  @Test
  void sendAggregateAndProofs_shouldProcessMixOfValidAndInvalidAggregates() {
    final SignedAggregateAndProof invalidAggregate =
        dataStructureUtil.randomSignedAggregateAndProof();
    final SignedAggregateAndProof validAggregate =
        dataStructureUtil.randomSignedAggregateAndProof();
    when(attestationManager.addAggregate(
            ValidatableAttestation.aggregateFromValidator(spec, invalidAggregate),
            Optional.empty()))
        .thenReturn(completedFuture(InternalValidationResult.reject("Bad juju")));
    when(attestationManager.addAggregate(
            ValidatableAttestation.aggregateFromValidator(spec, validAggregate), Optional.empty()))
        .thenReturn(completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendAggregateAndProofs(List.of(invalidAggregate, validAggregate));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(ZERO, "Bad juju")));

    // Should send both to the attestation manager.
    verify(attestationManager)
        .addAggregate(
            argThat(
                validatableAttestation ->
                    validatableAttestation.getSignedAggregateAndProof().equals(validAggregate)),
            any());
    verify(attestationManager)
        .addAggregate(
            argThat(
                validatableAttestation ->
                    validatableAttestation.getSignedAggregateAndProof().equals(invalidAggregate)),
            any());
  }

  @Test
  void getValidatorIndices_shouldThrowExceptionWhenBestStateNotAvailable() {
    when(chainDataClient.getBestState()).thenReturn(Optional.empty());

    // The validator client needs to be able to differentiate between the state not yet being loaded
    // and the requested validators not existing so it doesn't skip scheduling duties.
    assertThatSafeFuture(
            validatorApiHandler.getValidatorIndices(List.of(dataStructureUtil.randomPublicKey())))
        .isCompletedExceptionallyWith(IllegalStateException.class);
  }

  @Test
  void getValidatorIndices_shouldReturnMapWithKnownValidatorsWhenBestStateAvailable() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final BLSPublicKey validator0 =
        BLSPublicKey.fromBytesCompressed(state.getValidators().get(0).getPubkeyBytes());
    final BLSPublicKey unknownValidator = dataStructureUtil.randomPublicKey();
    when(chainDataClient.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));

    assertThatSafeFuture(
            validatorApiHandler.getValidatorIndices(List.of(validator0, unknownValidator)))
        .isCompletedWithValue(Map.of(validator0, 0));
  }

  @Test
  void sendSyncCommitteeMessages_shouldAllowEmptyRequest() {
    final List<SyncCommitteeMessage> messages = List.of();
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompleted();
  }

  @Test
  void sendSyncCommitteeMessages_shouldAddMessagesToPool() {
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final List<SyncCommitteeMessage> messages = List.of(message);
    when(syncCommitteeMessagePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompletedWithValue(emptyList());
    verify(performanceTracker).saveProducedSyncCommitteeMessage(message);
  }

  @Test
  void sendSyncCommitteeMessages_shouldRaiseErrors() {
    final SyncCommitteeMessage message = dataStructureUtil.randomSyncCommitteeMessage();
    final List<SyncCommitteeMessage> messages = List.of(message);
    when(syncCommitteeMessagePool.addLocal(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Rejected")));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSyncCommitteeMessages(messages);
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(UInt64.ZERO, "Rejected")));
    verify(performanceTracker, never()).saveProducedSyncCommitteeMessage(message);
  }

  @Test
  void sendSignedContributionAndProofs_shouldAllowEmptyRequest() {
    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(emptyList());
    assertThat(result).isCompleted();
  }

  @Test
  void sendSignedContributionAndProofs_shouldAddContributionsToPool() {
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(5);
    when(syncCommitteeContributionPool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(List.of(contribution));
    assertThat(result).isCompleted();
  }

  @Test
  void sendSignedContributionAndProofs_shouldReportErrors() {
    final SignedContributionAndProof contribution1 =
        dataStructureUtil.randomSignedContributionAndProof(5);
    final SignedContributionAndProof contribution2 =
        dataStructureUtil.randomSignedContributionAndProof(5);
    when(syncCommitteeContributionPool.addLocal(contribution1))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Bad")));
    when(syncCommitteeContributionPool.addLocal(contribution2))
        .thenReturn(
            SafeFuture.completedFuture(
                InternalValidationResult.create(ValidationResultCode.REJECT, "Worse")));

    final SafeFuture<Void> result =
        validatorApiHandler.sendSignedContributionAndProofs(List.of(contribution1, contribution2));
    assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(IllegalArgumentException.class)
        .hasMessageContainingAll("Bad", "Worse");
  }

  @Test
  void registerValidators_shouldUpdateRegistrations() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    setupValidatorsState(validatorRegistrations);

    when(chainDataClient.getCurrentSlot()).thenReturn(ONE);

    final SafeFuture<Void> result = validatorApiHandler.registerValidators(validatorRegistrations);

    assertThat(result).isCompleted();

    verify(proposersDataManager).updateValidatorRegistrations(validatorRegistrations, ONE);
  }

  @Test
  public void checkValidatorsDoppelganger_ShouldReturnEmptyResult()
      throws ExecutionException, InterruptedException {
    final List<UInt64> validatorIndices =
        List.of(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final Optional<UInt64> currentEpoch = Optional.of(epoch.plus(dataStructureUtil.randomEpoch()));

    when(nodeDataProvider.getValidatorLiveness(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(chainDataProvider.getCurrentEpoch()).thenReturn(currentEpoch);
    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        validatorApiHandler.getValidatorsLiveness(validatorIndices, epoch);

    verify(nodeDataProvider).getValidatorLiveness(validatorIndices, epoch, currentEpoch);
    assertThat(result).isCompleted();
    assertThat(result.get()).isEmpty();
  }

  @Test
  public void checkValidatorsDoppelganger_ShouldReturnDoppelgangerDetectionResult()
      throws ExecutionException, InterruptedException {
    final UInt64 firstIndex = dataStructureUtil.randomUInt64();
    final UInt64 secondIndex = dataStructureUtil.randomUInt64();
    final UInt64 thirdIndex = dataStructureUtil.randomUInt64();

    final UInt64 epoch = dataStructureUtil.randomEpoch();

    final Optional<UInt64> currentEpoch = Optional.of(epoch.plus(dataStructureUtil.randomEpoch()));

    List<UInt64> validatorIndices = List.of(firstIndex, secondIndex, thirdIndex);

    List<ValidatorLivenessAtEpoch> validatorLivenessAtEpochs =
        List.of(
            new ValidatorLivenessAtEpoch(firstIndex, false),
            new ValidatorLivenessAtEpoch(secondIndex, true),
            new ValidatorLivenessAtEpoch(thirdIndex, true));

    when(nodeDataProvider.getValidatorLiveness(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(validatorLivenessAtEpochs)));

    when(chainDataProvider.getCurrentEpoch()).thenReturn(currentEpoch);

    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        validatorApiHandler.getValidatorsLiveness(validatorIndices, epoch);

    verify(nodeDataProvider).getValidatorLiveness(validatorIndices, epoch, currentEpoch);
    assertThat(result).isCompleted();
    assertThat(result.get()).isPresent();
    List<ValidatorLivenessAtEpoch> validatorLivenessAtEpochsResult = result.get().get();
    assertThat(validatorIsLive(validatorLivenessAtEpochsResult, firstIndex)).isFalse();
    assertThat(validatorIsLive(validatorLivenessAtEpochsResult, secondIndex)).isTrue();
    assertThat(validatorIsLive(validatorLivenessAtEpochsResult, thirdIndex)).isTrue();
  }

  @Test
  public void getBeaconCommitteeSelectionProofShouldNotBeImplementedByBeaconNode() {
    assertThatThrownBy(() -> validatorApiHandler.getBeaconCommitteeSelectionProof(List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void getSyncCommitteeSelectionProofShouldNotBeImplementedByBeaconNode() {
    assertThatThrownBy(() -> validatorApiHandler.getSyncCommitteeSelectionProof(List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void shouldReportPerformanceWhenAttestationIsIgnored() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(), any()))
        .thenReturn(completedFuture(InternalValidationResult.IGNORE));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    verify(attestationManager)
        .addAttestation(ValidatableAttestation.fromValidator(spec, attestation), Optional.empty());
    verify(performanceTracker).saveProducedAttestation(attestation);
    verify(dutyMetrics).onAttestationPublished(attestation.getData().getSlot());
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void shouldReportPerformanceWhenAttestationIsSavedForTheFuture() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    when(attestationManager.addAttestation(any(), any()))
        .thenReturn(completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));
    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendSignedAttestations(List.of(attestation));
    verify(attestationManager)
        .addAttestation(ValidatableAttestation.fromValidator(spec, attestation), Optional.empty());
    verify(performanceTracker).saveProducedAttestation(attestation);
    verify(dutyMetrics).onAttestationPublished(attestation.getData().getSlot());
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void createUnsignedExecutionPayload_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<ExecutionPayloadEnvelope>> result =
        validatorApiHandler.createUnsignedExecutionPayload(ONE, dataStructureUtil.randomBytes32());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createUnsignedExecutionPayload_shouldFailWhenParentBlockIsOptimistic() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(newSlot);
    when(chainDataClient.getBlockAndStateInEffectAtSlot(eq(newSlot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));
    when(chainDataClient.isOptimisticBlock(blockAndState.getParentRoot())).thenReturn(true);

    final SafeFuture<Optional<ExecutionPayloadEnvelope>> result =
        validatorApiHandler.createUnsignedExecutionPayload(newSlot, blockAndState.getRoot());

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
    verifyNoInteractions(blockFactory);
  }

  @Test
  public void createUnsignedExecutionPayload_shouldReturnEmptyWhenThereIsBeaconBlockRootMismatch() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(newSlot);
    when(chainDataClient.getBlockAndStateInEffectAtSlot(eq(newSlot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));

    final SafeFuture<Optional<ExecutionPayloadEnvelope>> result =
        validatorApiHandler.createUnsignedExecutionPayload(
            newSlot, dataStructureUtil.randomBytes32());

    assertThat(result).isCompletedWithValue(Optional.empty());
    verifyNoInteractions(blockFactory);
  }

  @Test
  public void createUnsignedExecutionPayload_shouldCreateExecutionPayload() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final BeaconBlockAndState blockAndState = dataStructureUtil.randomBlockAndState(newSlot);
    final ExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomExecutionPayloadEnvelope(newSlot);

    when(chainDataClient.getBlockAndStateInEffectAtSlot(eq(newSlot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndState)));
    when(executionPayloadFactory.createUnsignedExecutionPayload(blockAndState))
        .thenReturn(SafeFuture.completedFuture(executionPayload));

    SafeFuture<Optional<ExecutionPayloadEnvelope>> result =
        validatorApiHandler.createUnsignedExecutionPayload(newSlot, blockAndState.getRoot());

    assertThat(result).isCompletedWithValue(Optional.of(executionPayload));
  }

  @Test
  public void publishSignedExecutionPayload_shouldPublish() {
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(5);
    final PublishSignedExecutionPayloadResult publishResult =
        PublishSignedExecutionPayloadResult.success(signedExecutionPayload.getBeaconBlockRoot());
    when(executionPayloadPublisher.publishSignedExecutionPayload(
            eq(signedExecutionPayload), eq(Optional.empty())))
        .thenReturn(SafeFuture.completedFuture(publishResult));

    assertThat(
            validatorApiHandler.publishSignedExecutionPayload(
                signedExecutionPayload, Optional.empty()))
        .isCompletedWithValue(publishResult);

    verify(executionPayloadPublisher)
        .publishSignedExecutionPayload(signedExecutionPayload, Optional.empty());
  }

  @Test
  public void publishSignedExecutionPayload_shouldPublishWithBroadcastValidationLevel() {
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(5);
    final PublishSignedExecutionPayloadResult publishResult =
        PublishSignedExecutionPayloadResult.success(signedExecutionPayload.getBeaconBlockRoot());
    final Optional<BroadcastValidationLevel> broadcastValidationLevel = Optional.of(GOSSIP);

    when(executionPayloadPublisher.publishSignedExecutionPayload(
            eq(signedExecutionPayload), eq(broadcastValidationLevel)))
        .thenReturn(SafeFuture.completedFuture(publishResult));

    assertThat(
            validatorApiHandler.publishSignedExecutionPayload(
                signedExecutionPayload, broadcastValidationLevel))
        .isCompletedWithValue(publishResult);

    verify(executionPayloadPublisher)
        .publishSignedExecutionPayload(signedExecutionPayload, broadcastValidationLevel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldHandleExceptions() {
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(5);
    final PublishSignedExecutionPayloadResult failedResult =
        PublishSignedExecutionPayloadResult.rejected(
            signedExecutionPayload.getBeaconBlockRoot(), "oopsy");
    when(executionPayloadPublisher.publishSignedExecutionPayload(
            eq(signedExecutionPayload), eq(Optional.empty())))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("oopsy")));

    assertThat(
            validatorApiHandler.publishSignedExecutionPayload(
                signedExecutionPayload, Optional.empty()))
        .isCompletedWithValue(failedResult);

    verify(executionPayloadPublisher)
        .publishSignedExecutionPayload(signedExecutionPayload, Optional.empty());
  }

  @Test
  public void getPtcDuties_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<PtcDuties>> duties =
        validatorApiHandler.getPtcDuties(EPOCH, IntList.of(1));
    assertThat(duties).isCompletedExceptionally();
    assertThatThrownBy(duties::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void getPtcDuties_shouldFailForEpochTooFarAhead() {
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(3));

    final SafeFuture<Optional<PtcDuties>> result =
        validatorApiHandler.getPtcDuties(EPOCH, IntList.of(3, 8));
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getPtcDuties_shouldReturnDutiesAndSkipMissingValidators() {
    final BeaconState state = createStateWithActiveValidatorsAndInitialisedPtcWindow();
    when(chainDataClient.getStateAtSlotExact(previousEpochStartSlot))
        .thenReturn(completedFuture(Optional.of(state)));
    when(chainDataClient.getCurrentEpoch()).thenReturn(EPOCH.minus(ONE));

    final SafeFuture<Optional<PtcDuties>> result =
        validatorApiHandler.getPtcDuties(EPOCH, IntList.of(3, 8, 42));
    final Optional<PtcDuties> duties = assertCompletedSuccessfully(result);
    assertThat(duties.orElseThrow().duties())
        .containsExactly(
            new PtcDuty(
                state.getValidators().get(3).getPublicKey(),
                UInt64.valueOf(3),
                UInt64.valueOf(110)),
            new PtcDuty(
                state.getValidators().get(8).getPublicKey(),
                UInt64.valueOf(8),
                UInt64.valueOf(108)));
  }

  @Test
  public void createPayloadAttestationData_shouldFailWhenNodeIsSyncing() {
    nodeIsSyncing();
    final SafeFuture<Optional<PayloadAttestationData>> result =
        validatorApiHandler.createPayloadAttestationData(ONE);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get).hasRootCauseInstanceOf(NodeSyncingException.class);
  }

  @Test
  public void createPayloadAttestationData_shouldCreatePayloadAttestationData() {
    final UInt64 newSlot = UInt64.valueOf(25);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(newSlot);

    when(chainDataClient.getBlockAtSlotExact(eq(newSlot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(block.getRoot()))
        .thenReturn(true);
    when(dataAvailabilitySampler.isDataAvailable(block)).thenReturn(true);

    final Optional<PayloadAttestationData> result =
        SafeFutureAssert.safeJoin(validatorApiHandler.createPayloadAttestationData(newSlot));

    assertThat(result)
        .hasValueSatisfying(
            payloadAttestationData -> {
              assertThat(payloadAttestationData.getBeaconBlockRoot()).isEqualTo(block.getRoot());
              assertThat(payloadAttestationData.getSlot()).isEqualTo(newSlot);
              assertThat(payloadAttestationData.isPayloadPresent()).isTrue();
              assertThat(payloadAttestationData.isBlobDataAvailable()).isTrue();
            });
  }

  @Test
  public void createPayloadAttestationData_shouldReturnEmptyWhenNoBlockAtSlot() {
    final UInt64 skippedSlot = UInt64.valueOf(25);

    when(chainDataClient.getBlockAtSlotExact(eq(skippedSlot)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Optional<PayloadAttestationData> result =
        SafeFutureAssert.safeJoin(validatorApiHandler.createPayloadAttestationData(skippedSlot));

    assertThat(result).isEmpty();
    verify(executionPayloadManager, never()).isExecutionPayloadSeenBeforeDeadline(any());
    verify(dataAvailabilitySampler, never()).isDataAvailable(any());
  }

  @Test
  void sendPayloadAttestationMessages_shouldAddPayloadAttestationsToPool() {
    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage();
    when(payloadAttestationPool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final SafeFuture<List<SubmitDataError>> result =
        validatorApiHandler.sendPayloadAttestationMessages(List.of(payloadAttestationMessage));
    assertThat(result).isCompletedWithValue(List.of());
  }

  private void mockRequiredMethodsForBlockProduction(
      final BeaconState blockSlotState, final UInt64 newSlot) {
    mockRequiredMethodsForBlockProduction(
        chainHead, blockSlotState, newSlot, PAYLOAD_STATUS_PENDING);
  }

  private Bytes32 mockRequiredMethodsForBlockProduction(
      final ChainHead chainHead,
      final BeaconState blockSlotState,
      final UInt64 newSlot,
      final ForkChoicePayloadStatus payloadStatus) {
    when(forkChoiceTrigger.prepareForBlockProduction(eq(newSlot), any()))
        .thenReturn(SafeFuture.completedFuture(chainHead));
    when(chainDataClient.getStateForBlockProduction(same(chainHead), eq(newSlot)))
        .thenReturn(SafeFuture.completedFuture(blockSlotState));
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, newSlot.decrement());
    when(chainHead.getRoot()).thenReturn(parentRoot);
    when(chainHead.getForkChoiceNode()).thenReturn(new ForkChoiceNode(parentRoot, payloadStatus));
    when(chainHead.getPayloadStatus()).thenReturn(payloadStatus);
    return parentRoot;
  }

  private boolean validatorIsLive(
      final List<ValidatorLivenessAtEpoch> validatorLivenessAtEpochs, final UInt64 validatorIndex) {
    return validatorLivenessAtEpochs.stream()
        .anyMatch(
            validatorLivenessAtEpoch ->
                validatorLivenessAtEpoch.index().equals(validatorIndex)
                    && validatorLivenessAtEpoch.isLive());
  }

  private <T> Optional<T> assertCompletedSuccessfully(final SafeFuture<Optional<T>> result) {
    assertThat(result).isCompleted();
    return safeJoin(result);
  }

  private BeaconState createStateWithActiveValidators() {
    return createStateWithActiveValidators(previousEpochStartSlot);
  }

  private BeaconState createStateWithActiveValidatorsAndInitialisedPtcWindow() {
    return createStateWithActiveValidatorsAndInitialisedPtcWindow(previousEpochStartSlot);
  }

  private BeaconState createStateWithActiveValidators(final UInt64 slot) {
    return dataStructureUtil.randomBeaconStateWithActiveValidators(32, slot);
  }

  private BeaconState createStateWithActiveValidatorsAndInitialisedPtcWindow(final UInt64 slot) {
    return dataStructureUtil.randomBeaconStateWithActiveValidatorsAndInitialisedPtcWindow(32, slot);
  }

  private void setupValidatorsState(
      final SszList<SignedValidatorRegistration> validatorRegistrations) {
    setupValidatorsState(
        validatorRegistrations, validatorRegistrations.size(), Collections.emptyMap());
  }

  private void setupValidatorsState(
      final SszList<SignedValidatorRegistration> validatorRegistrations,
      final int numOfValidatorsToSetup,
      final Map<BLSPublicKey, ValidatorStatus> statusOverrides) {
    final List<StateValidatorData> data =
        IntStream.range(0, numOfValidatorsToSetup)
            .mapToObj(
                index -> {
                  final SignedValidatorRegistration validatorRegistration =
                      validatorRegistrations.get(index);
                  final BLSPublicKey publicKey = validatorRegistration.getMessage().getPublicKey();
                  return new StateValidatorData(
                      UInt64.valueOf(index),
                      dataStructureUtil.randomUInt64(),
                      statusOverrides.getOrDefault(publicKey, ValidatorStatus.active_ongoing),
                      dataStructureUtil.validatorBuilder().publicKey(publicKey).build());
                })
            .collect(Collectors.toList());

    final ObjectAndMetaData<List<StateValidatorData>> stateValidators =
        new ObjectAndMetaData<>(data, SpecMilestone.BELLATRIX, false, true, false);

    final List<String> validators =
        validatorRegistrations.stream()
            .map(SignedValidatorRegistration::getMessage)
            .map(ValidatorRegistration::getPublicKey)
            .map(BLSPublicKey::toString)
            .collect(toList());

    when(chainDataProvider.getStateValidators("head", validators, new HashSet<>()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(stateValidators)));
  }
}
