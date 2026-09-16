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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;
import static tech.pegasys.teku.statetransition.forkchoice.ForkChoice.BLOCK_CREATION_TOLERANCE_MS;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.mockito.verification.VerificationMode;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.SlotAndForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice.OptimisticHeadSubscriber;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationEventChannel;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.payloadattestation.ValidatablePayloadAttestationMessage;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.HeadEvent;
import tech.pegasys.teku.storage.api.TrackingChainHeadChannel.ReorgEvent;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class ForkChoiceTest {

  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @SuppressWarnings("unchecked")
  private final AvailabilityChecker<BlobSidecar> blobSidecarsAvailabilityChecker =
      mock(AvailabilityChecker.class);

  private AttestationSchema<?> attestationSchema;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private SignedBlockAndState genesis;
  private RecentChainData recentChainData;

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final OptimisticHeadSubscriber optimisticSyncStateTracker =
      mock(OptimisticHeadSubscriber.class);
  private ExecutionLayerChannelStub executionLayer;
  private final BlockBroadcastValidator blockBroadcastValidator =
      mock(BlockBroadcastValidator.class);
  private final MergeTransitionBlockValidator transitionBlockValidator =
      mock(MergeTransitionBlockValidator.class);
  private final DebugDataDumper debugDataDumper = mock(DebugDataDumper.class);

  private final InlineEventThread eventThread = new InlineEventThread();

  private ForkChoice forkChoice;

  @BeforeEach
  public void setup() {
    setupWithSpec(TestSpecFactory.createMinimalBellatrix());
  }

  private void setupWithSpec(final Spec unmockedSpec) {
    unmockedSpec.reinitializeForTesting(
        (block) -> blobSidecarsAvailabilityChecker,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        KZG.DISABLED);
    // Setting up spec and all dependants
    this.spec = spy(unmockedSpec);
    this.dataStructureUtil = new DataStructureUtil(spec);
    this.attestationSchema = spec.getGenesisSchemaDefinitions().getAttestationSchema();
    this.storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .specProvider(spec)
            .numberOfValidators(16)
            .build();
    this.chainBuilder = storageSystem.chainBuilder();
    this.genesis = chainBuilder.generateGenesis();
    this.recentChainData = storageSystem.recentChainData();
    this.executionLayer = new ExecutionLayerChannelStub(spec, false);
    this.forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            FastConfirmationTracker.NOOP,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            LateBlockReorgPreparationHandler.NOOP,
            debugDataDumper,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));

    // Starting and mocks
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    recentChainData.initializeFromGenesis(genesis.getState(), UInt64.ZERO);
    reset(
        forkChoiceNotifier,
        transitionBlockValidator); // Clear any notifications from setting genesis

    // by default everything is valid
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.SUCCESS));

    forkChoice.subscribeToOptimisticHeadChangesAndUpdate(optimisticSyncStateTracker);

    // blobs always available
    if (spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecars(2);
      setBlobSidecarsAvailabilityResult(DataAndValidationResult.validResult(blobSidecars));
    }
  }

  @Test
  void shouldNotInitializeFastConfirmationStoreWhenDisabled() {
    assertThat(forkChoice.isFastConfirmationEnabled()).isFalse();
    assertThat(forkChoice.getFastConfirmationStore()).isEmpty();
  }

  @Test
  void shouldInitializeFastConfirmationStoreWhenEnabled() {
    recreateForkChoice(true, LateBlockReorgPreparationHandler.NOOP);

    final Checkpoint finalizedCheckpoint = recentChainData.getStore().getFinalizedCheckpoint();
    final FastConfirmationStore fastConfirmationStore =
        forkChoice.getFastConfirmationStore().orElseThrow();
    assertThat(forkChoice.isFastConfirmationEnabled()).isTrue();
    assertThat(fastConfirmationStore.store()).isSameAs(recentChainData.getStore());
    assertThat(fastConfirmationStore.confirmedRoot()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.previousEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.currentEpochObservedJustifiedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousEpochGreatestUnrealizedCheckpoint())
        .isEqualTo(finalizedCheckpoint);
    assertThat(fastConfirmationStore.previousSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(finalizedCheckpoint.getRoot());
  }

  @Test
  void shouldScheduleFastConfirmationUpdateOnSlotTickWhenEnabled() {
    final StubAsyncRunner fastConfirmationAsyncRunner = new StubAsyncRunner();
    recreateForkChoice(
        FastConfirmationTracker.create(
            spec,
            Optional.of(fastConfirmationAsyncRunner),
            FastConfirmationEventChannel.NOOP,
            metricsSystem,
            StubTimeProvider.withTimeInMillis(0)),
        LateBlockReorgPreparationHandler.NOOP);
    final UInt64 nextSlot = recentChainData.getCurrentSlot().orElseThrow().plus(ONE);

    forkChoice.onTick(
        spec.computeTimeMillisAtSlot(nextSlot, recentChainData.getGenesisTimeMillis()),
        Optional.empty());

    assertThat(fastConfirmationAsyncRunner.countDelayedActions()).isOne();
  }

  @Test
  void processHead_shouldSendForkChoiceUpdatedWhenFastConfirmationDisabled() {
    // Default forkChoice uses FastConfirmationTracker.NOOP (FCR disabled). processHead must still
    // send the fcU itself, exactly as before the FCR fcU-ordering change.
    processHead(ONE);

    verify(forkChoiceNotifier).onForkChoiceUpdated(any(), eq(Optional.empty()));
  }

  @Test
  void prepareForBlockProduction_shouldRunFastConfirmationForProposalSlotWhenEnabled() {
    final StubAsyncRunner fastConfirmationAsyncRunner = new StubAsyncRunner();
    recreateForkChoice(
        FastConfirmationTracker.create(
            spec,
            Optional.of(fastConfirmationAsyncRunner),
            FastConfirmationEventChannel.NOOP,
            metricsSystem,
            StubTimeProvider.withTimeInMillis(0)),
        LateBlockReorgPreparationHandler.NOOP);
    // Move within tolerance of slot 1 so prepareForBlockProduction advances the store into it (as
    // when proposing), stealing the slot boundary from ForkChoice.onTick.
    storageSystem
        .chainUpdater()
        .setTimeMillis(
            spec.computeTimeMillisAtSlot(ONE, recentChainData.getGenesisTimeMillis())
                .minusMinZero(BLOCK_CREATION_TOLERANCE_MS - 100));

    assertThat(forkChoice.prepareForBlockProduction(ONE, BlockProductionPerformance.NOOP))
        .isCompleted();
    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ONE));

    // The fast confirmation update for the proposal slot was scheduled despite onTick not seeing a
    // boundary; run it and confirm the once-per-slot rotation happened for the proposal slot.
    fastConfirmationAsyncRunner.executeQueuedActions();

    final FastConfirmationStore fastConfirmationStore =
        forkChoice.getFastConfirmationStore().orElseThrow();
    assertThat(fastConfirmationStore.currentSlotHead()).isEqualTo(genesis.getRoot());
  }

  @Test
  void onTick_shouldNotSendForkChoiceUpdatedWhenFastConfirmationDisabled() {
    // With FCR disabled, the slot tick does not process the head or send an fcU (master behaviour);
    // the FCR path — which sends the fcU after on_fast_confirmation — is only taken when enabled.
    final UInt64 nextSlot = recentChainData.getCurrentSlot().orElseThrow().plus(ONE);

    forkChoice.onTick(
        spec.computeTimeMillisAtSlot(nextSlot, recentChainData.getGenesisTimeMillis()),
        Optional.empty());

    verify(forkChoiceNotifier, never()).onForkChoiceUpdated(any(), any());
  }

  @Test
  void shouldNotTriggerReorgWhenEmptyHeadSlotFilled() {
    // Run fork choice with an empty slot 1
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(genesis.getRoot());

    // Then rerun with a filled slot 1
    final SignedBlockAndState slot1Block = storageSystem.chainUpdater().advanceChain(ONE);
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(slot1Block.getRoot());

    final List<ReorgEvent> reorgEvents = storageSystem.chainHeadChannel().getReorgEvents();
    assertThat(reorgEvents).isEmpty();
  }

  @Test
  void onBlock_shouldCheckBlobsAvailability() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    importBlock(blockAndState);

    verify(blobSidecarsAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobSidecarsAvailabilityChecker).getAndLogAvailabilityCheckResult(any());
  }

  @Test
  void onBlock_shouldFailIfBlobsAreNotAvailable() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    setBlobSidecarsAvailabilityResult(DataAndValidationResult.notAvailable());

    importBlockAndAssertFailure(
        blockAndState, FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE);

    verify(blobSidecarsAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobSidecarsAvailabilityChecker).getAndLogAvailabilityCheckResult(any());
  }

  @Test
  void onBlock_shouldFailWhenDataColumnAvailabilityNeverCompletes() throws Exception {
    setupWithSpec(TestSpecFactory.createMinimalFulu());
    final DataAvailabilitySampler dataAvailabilitySampler = mock(DataAvailabilitySampler.class);
    when(dataAvailabilitySampler.checkSamplingEligibility(any()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(dataAvailabilitySampler.checkDataAvailability(any(), any()))
        .thenReturn(new SafeFuture<>());
    doReturn(true).when(spec).isAvailabilityOfDataColumnSidecarsRequiredAtSlot(any(), any());
    doReturn(1).when(spec).getSlotDurationMillis(any());
    spec.reinitializeForTesting(
        block -> blobSidecarsAvailabilityChecker,
        block ->
            new DataColumnSidecarAvailabilityChecker(
                dataAvailabilitySampler, spec, recentChainData, block),
        KZG.DISABLED);
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);

    final SafeFuture<BlockImportResult> importResult = importBlockNoResultCheck(blockAndState);

    final BlockImportResult result = importResult.get(5, TimeUnit.SECONDS);
    assertThat(result.getFailureReason())
        .isEqualTo(FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE);
    assertThat(recentChainData.getHeadBlock().map(MinimalBeaconBlockSummary::getRoot))
        .isNotEqualTo(Optional.of(blockAndState.getRoot()));
    verify(dataAvailabilitySampler)
        .checkDataAvailability(blockAndState.getSlot(), blockAndState.getRoot());
    verify(dataAvailabilitySampler).flush();
    verify(blockBroadcastValidator, never()).onConsensusValidationSucceeded();
  }

  @Test
  void onBlock_consensusValidationShouldNotResolveWhenDataAvailabilityFails() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    setBlobSidecarsAvailabilityResult(DataAndValidationResult.notAvailable());

    importBlockAndAssertFailure(
        blockAndState, FailureReason.FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE);

    verify(blockBroadcastValidator, never()).onConsensusValidationSucceeded();

    verify(blobSidecarsAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobSidecarsAvailabilityChecker).getAndLogAvailabilityCheckResult(any());
  }

  @Test
  void onBlock_shouldFailWhenBroadcastValidationFails() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.GOSSIP_FAILURE));

    importBlockAndAssertFailure(blockAndState, FailureReason.FAILED_BROADCAST_VALIDATION);

    verify(blockBroadcastValidator).onConsensusValidationSucceeded();

    verify(blobSidecarsAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobSidecarsAvailabilityChecker).getAndLogAvailabilityCheckResult(any());
  }

  @Test
  void onBlock_consensusValidationShouldNotResolveWhenEarlyFails() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final List<SignedBlockAndState> signedBlockAndStates = chainBuilder.generateBlocksUpToSlot(2);
    final SignedBlockAndState wrongBlockAndState = signedBlockAndStates.getLast();

    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(wrongBlockAndState.getSlot());

    importBlockAndAssertFailure(wrongBlockAndState, FailureReason.UNKNOWN_PARENT);

    verify(blockBroadcastValidator, never()).onConsensusValidationSucceeded();
  }

  @Test
  void onBlock_consensusValidationShouldNotResolveWhenStateTransitionFails()
      throws StateTransitionException {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    final BlockProcessor blockProcessor = mock(BlockProcessor.class);
    when(spec.getBlockProcessor(blockAndState.getSlot())).thenReturn(blockProcessor);
    final Exception blockException = new StateTransitionException("error!");
    when(blockProcessor.processAndValidateBlock(any(), any(), any(), any()))
        .thenThrow(blockException);

    importBlockAndAssertFailure(blockAndState, FailureReason.FAILED_STATE_TRANSITION);

    verify(debugDataDumper)
        .saveInvalidBlock(
            eq(blockAndState.getBlock()),
            eq(FailureReason.FAILED_STATE_TRANSITION.toString()),
            eq(Optional.of(blockException)));
    verify(blockBroadcastValidator, never()).onConsensusValidationSucceeded();
  }

  @Test
  void onBlock_consensusValidationShouldReturnRegardlessExecutionPayloadValidation() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    importBlock(blockAndState);
    reset(blockBroadcastValidator);

    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.SUCCESS));

    // let's prepare a mocked EL with lazy newPayload
    executionLayer = mock(ExecutionLayerChannelStub.class);
    final SafeFuture<PayloadStatus> payloadStatusSafeFuture = new SafeFuture<>();
    when(executionLayer.engineNewPayload(any(), any())).thenReturn(payloadStatusSafeFuture);

    // let's import a valid consensus block
    final SafeFuture<BlockImportResult> importResult =
        importBlockNoResultCheck(chainBuilder.generateNextBlock());

    // successful consensus check prior to EL validation
    verify(blockBroadcastValidator).onConsensusValidationSucceeded();

    assertThatSafeFuture(importResult).isNotDone();

    // resolve with a failure
    payloadStatusSafeFuture.complete(PayloadStatus.invalid(Optional.empty(), Optional.empty()));
    assertBlockImportFailure(importResult, FailureReason.FAILED_STATE_TRANSITION);
    verify(debugDataDumper)
        .saveInvalidBlock(any(), eq(FailureReason.FAILED_STATE_TRANSITION.toString()), any());
  }

  @Test
  void onBlock_shouldImportIfBlobsAreNotRequired() {
    setupWithSpec(TestSpecFactory.createMinimalDeneb());
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());

    setBlobSidecarsAvailabilityResult(DataAndValidationResult.notRequired());

    importBlock(blockAndState);

    verify(blobSidecarsAvailabilityChecker).initiateDataAvailabilityCheck();
    verify(blobSidecarsAvailabilityChecker).getAndLogAvailabilityCheckResult(any());
  }

  @Test
  void onBlock_shouldImmediatelyMakeChildOfCurrentHeadTheNewHead() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(
            blockAndState.getBlock(),
            Optional.empty(),
            BlockBroadcastValidator.NOOP,
            executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertThat(recentChainData.getHeadBlock().map(MinimalBeaconBlockSummary::getRoot))
        .contains(blockAndState.getRoot());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
  }

  @Test
  void onBlock_shouldNotTriggerReorgWhenSelectingChildOfChainHeadWhenForkChoiceSlotHasAdvanced() {
    // Advance the current head
    final UInt64 nodeSlot = UInt64.valueOf(5);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(UInt64.valueOf(5));
    processHead(nodeSlot);

    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(
            blockAndState.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertThat(recentChainData.getHeadBlock().map(MinimalBeaconBlockSummary::getRoot))
        .contains(blockAndState.getRoot());
    assertThat(recentChainData.getHeadSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(storageSystem.chainHeadChannel().getReorgEvents()).isEmpty();
  }

  private static Stream<Arguments> provideArgumentsForShouldReorg() {
    return Stream.of(
        Arguments.of(0, true),
        Arguments.of(500, true),
        Arguments.of(1700, true),
        Arguments.of(2300, false));
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsForShouldReorg")
  void onBlock_shouldReorgWhenProposerWeightingMakesForkBestChain(
      final long advanceTimeSlotMillis, final boolean shouldReorg) {
    storageSystem.chainUpdater().setCurrentSlot(UInt64.valueOf(2));
    final Spec spec = TestSpecFactory.createMinimalBellatrix();
    forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            FastConfirmationTracker.NOOP,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            LateBlockReorgPreparationHandler.NOOP,
            DebugDataDumper.NOOP,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));

    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final UInt64 lateBlockSlot = currentSlot.minus(1);
    final ChainBuilder chainB = chainBuilder.fork();
    final SignedBlockAndState chainBBlock1 =
        chainB.generateBlockAtSlot(
            lateBlockSlot,
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    final SignedBlockAndState chainABlock1 = chainBuilder.generateBlockAtSlot(lateBlockSlot);

    // All blocks received late for slot 1
    importBlock(chainABlock1);
    importBlock(chainBBlock1);

    // At this point fork choice is tied with no votes for either chain
    // The winner is the block with the greatest hash which is hard to control.
    // So just find which block won and check that we can switch forks based on proposer reward
    final SignedBlockAndState expectedChainHead;
    if (recentChainData.getChainHead().orElseThrow().getRoot().equals(chainABlock1.getRoot())) {
      // ChainA won, so try to switch to chain B
      expectedChainHead = chainB.generateBlockAtSlot(currentSlot);
    } else {
      // ChainB won so try to switch to chain A
      expectedChainHead = chainBuilder.generateBlockAtSlot(currentSlot);
    }

    // advance time into slot
    if (advanceTimeSlotMillis > 0) {
      StoreTransaction transaction = recentChainData.startStoreTransaction();
      UInt64 timeIntoSlotMillis =
          recentChainData.getStore().getTimeInMillis().plus(advanceTimeSlotMillis);
      transaction.setTimeMillis(timeIntoSlotMillis);
      safeJoin(transaction.commit());
    }

    importBlock(expectedChainHead);

    if (shouldReorg) {
      assertThat(recentChainData.getStore().getProposerBoostRoot())
          .contains(expectedChainHead.getRoot());
      // for reorging blocks with proposer boost processHead is executed by forkChoice.onBlock
      // Check we switched chains, if proposer reward wasn't considered we'd stay on the other fork
      assertThat(recentChainData.getBestBlockRoot()).hasValue(expectedChainHead.getRoot());
    } else {
      assertThat(recentChainData.getStore().getProposerBoostRoot()).isEmpty();
      assertThat(forkChoice.processHead()).isCompleted();
      // no reorg
      assertThat(recentChainData.getBestBlockRoot())
          .hasValueSatisfying(
              bestBlockRoot -> assertThat(bestBlockRoot).isNotEqualTo(expectedChainHead.getRoot()));
    }
  }

  @Test
  void onBlock_shouldUpdateVotesBasedOnAttestationsInBlocks() {
    final ChainBuilder forkChain = chainBuilder.fork();
    final SignedBlockAndState forkBlock =
        forkChain.generateBlockAtSlot(
            ONE,
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    // eventually better chain with an empty block
    final SignedBlockAndState betterBlock = chainBuilder.generateNextBlock(1);

    importBlock(forkBlock);
    // Should automatically follow the fork as it is the only one imported
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock.getRoot());

    // Add an attestation for the fork so that it initially has higher weight
    // Otherwise ties are split based on the hash which is too hard to control in the test
    final BlockOptions forkBlockOptions = BlockOptions.create();
    forkChain
        .streamValidAttestationsWithTargetBlock(forkBlock)
        .limit(1)
        .forEach(forkBlockOptions::addAttestation);
    final SignedBlockAndState forkBlock1 = forkChain.generateNextBlock(forkBlockOptions);
    importBlock(forkBlock1);

    // The fork is still the only option so gets selected
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Now import what will become the canonical chain
    importBlock(betterBlock);
    // Process head to ensure we clear any additional proposer weighting for this first block.
    // Should still pick forkBlock as it's the best option even though we have a competing chain
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Import a block with two attestations which makes this chain better than the fork
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsWithTargetBlock(betterBlock)
        .limit(2)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations = chainBuilder.generateNextBlock(options);
    importBlock(blockWithAttestations);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // When attestations are applied we should switch away from the fork to our better chain
    processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttestations.getRoot());
  }

  @Test
  void payloadAttestationsFromBlock_shouldExpandVotesForDuplicatedPtcValidators() {
    setupWithSpec(TestSpecFactory.createMinimalGloas());

    final UInt64 slot = UInt64.ONE;
    final BeaconState attestedBlockState = mock(BeaconState.class);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final PayloadAttestationData data =
        schemaDefinitions
            .getPayloadAttestationDataSchema()
            .create(dataStructureUtil.randomBytes32(), slot, true, true);
    final PayloadAttestation payloadAttestation =
        schemaDefinitions
            .getPayloadAttestationSchema()
            .create(
                schemaDefinitions
                    .getPayloadAttestationSchema()
                    .getAggregationBitsSchema()
                    .ofBits(0),
                data,
                dataStructureUtil.randomSignature());

    doReturn(IntList.of(42, 1, 42, 2, 42)).when(spec).getPtc(attestedBlockState, slot);

    final List<ValidatablePayloadAttestationMessage> messages =
        forkChoice.getPayloadAttestationMessagesFromBlock(attestedBlockState, payloadAttestation);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getValidatorIndex()).isEqualTo(UInt64.valueOf(42));
    assertThat(messages.get(0).calculatePtcPositions(spec, attestedBlockState))
        .isEqualTo(IntSet.of(0, 2, 4));
  }

  @Test
  void prepareForBlockProduction_shouldUseEmptyParentWhenPtcVotesDataUnavailable() {
    setupWithSpec(TestSpecFactory.createMinimalGloas());

    final UInt64 parentSlot = UInt64.ONE;
    final UInt64 proposalSlot = parentSlot.plus(ONE);
    final SignedBlockAndState parentBlock = storageSystem.chainUpdater().advanceChain(parentSlot);
    final ForkChoiceStrategy strategy = recentChainData.getStore().getForkChoiceStrategy();
    final int threshold =
        SpecConfigGloas.required(spec.atSlot(parentSlot).getConfig())
            .getDataAvailabilityTimelyThreshold();
    strategy.onPtcVote(parentBlock.getRoot(), ptcPositions(threshold + 1), true, false);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(proposalSlot);

    final ChainHead blockProductionHead =
        safeJoin(
            forkChoice.prepareForBlockProduction(proposalSlot, BlockProductionPerformance.NOOP));

    assertThat(blockProductionHead.getRoot()).isEqualTo(parentBlock.getRoot());
    assertThat(blockProductionHead.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void prepareForBlockProduction_shouldUseEmptyParentWhenPtcVotesPayloadUntimely() {
    setupWithSpec(TestSpecFactory.createMinimalGloas());

    final UInt64 parentSlot = UInt64.ONE;
    final UInt64 proposalSlot = parentSlot.plus(ONE);
    final SignedBlockAndState parentBlock = storageSystem.chainUpdater().advanceChain(parentSlot);
    final ForkChoiceStrategy strategy = recentChainData.getStore().getForkChoiceStrategy();
    final int threshold =
        SpecConfigGloas.required(spec.atSlot(parentSlot).getConfig()).getPayloadTimelyThreshold();
    strategy.onPtcVote(parentBlock.getRoot(), ptcPositions(threshold + 1), false, true);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(proposalSlot);

    final ChainHead blockProductionHead =
        safeJoin(
            forkChoice.prepareForBlockProduction(proposalSlot, BlockProductionPerformance.NOOP));

    assertThat(blockProductionHead.getRoot()).isEqualTo(parentBlock.getRoot());
    assertThat(blockProductionHead.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
  }

  @Test
  void onBlock_shouldUpdateVotesBasedOnAttesterSlashingEquivocationsInBlocks() {
    final ChainBuilder forkChain = chainBuilder.fork();
    final SignedBlockAndState forkBlock =
        forkChain.generateNextBlock(
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));
    // eventually better chain with an empty block
    final SignedBlockAndState betterBlock = chainBuilder.generateNextBlock(1);

    importBlock(forkBlock);

    // Should automatically follow the fork as it is the only one imported
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock.getRoot());

    // Add an attestation for the fork so that it initially has higher weight
    // Otherwise ties are split based on the hash which is too hard to control in the test
    final BlockOptions forkBlockOptions = BlockOptions.create();
    final List<Attestation> forkAttestations =
        forkChain.streamValidAttestationsWithTargetBlock(forkBlock).limit(2).toList();
    forkAttestations.forEach(forkBlockOptions::addAttestation);
    final SignedBlockAndState forkBlock1 = forkChain.generateNextBlock(forkBlockOptions);
    importBlock(forkBlock1);

    // The fork is still the only option so gets selected
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Now import what will become the canonical chain
    importBlock(betterBlock);
    // Process head to ensure we clear any additional proposer weighting for this first block.
    // Should still pick forkBlock as it's the best option even though we have a competing chain
    processHead(ONE);
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Import a block with one attestation on what will be better chain
    final BlockOptions options = BlockOptions.create();
    chainBuilder
        .streamValidAttestationsWithTargetBlock(betterBlock)
        .limit(1)
        .forEach(options::addAttestation);
    final SignedBlockAndState blockWithAttestations = chainBuilder.generateNextBlock(options);
    importBlock(blockWithAttestations);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Verify that fork is still better
    processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // Add 2 AttesterSlashing on betterBlock chain, so it will become finally better
    final BlockOptions options2 = BlockOptions.create();
    forkAttestations.forEach(
        attestation ->
            options2.addAttesterSlashing(
                chainBuilder.createAttesterSlashingForAttestation(attestation, forkBlock)));
    final SignedBlockAndState blockWithAttesterSlashings = chainBuilder.generateNextBlock(options2);
    importBlock(blockWithAttesterSlashings);

    // Haven't run fork choice so won't have re-orged yet - fork still has more applied votes
    assertThat(recentChainData.getBestBlockRoot()).contains(forkBlock1.getRoot());

    // When attester slashings are applied we should switch away from the fork to our better chain
    processHead(blockWithAttesterSlashings.getSlot());

    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttesterSlashings.getRoot());
  }

  @Test
  void onBlock_shouldNotProcessAttestationsForBlocksThatDoNotYetExist() {
    final ChainBuilder forkChain = chainBuilder.fork();
    // Create a fork block, but don't import it.
    final SignedBlockAndState forkBlock =
        forkChain.generateBlockAtSlot(
            UInt64.valueOf(2),
            BlockOptions.create()
                .setEth1Data(new Eth1Data(Bytes32.ZERO, UInt64.valueOf(6), Bytes32.ZERO)));

    // Now create the canonical chain and import.
    final List<SignedBlockAndState> betterChain = chainBuilder.generateBlocksUpToSlot(3);
    betterChain.forEach(this::importBlock);

    // And create a block containing an attestation for forkBlock
    final BlockOptions options = BlockOptions.create();
    final Attestation attestation =
        chainBuilder
            .streamValidAttestationsWithTargetBlock(forkBlock)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to create attestation for block "
                            + forkBlock.getRoot()
                            + " genesis root: "
                            + genesis.getRoot()
                            + " chain head: "
                            + chainBuilder.getLatestBlockAndState().getRoot()
                            + " fork block: "
                            + forkBlock.getRoot()
                            + " validators: "
                            + chainBuilder.getValidatorKeys().stream()
                                .map(BLSKeyPair::getPublicKey)
                                .map(BLSPublicKey::toString)
                                .collect(Collectors.joining(", "))));
    options.addAttestation(attestation);
    final SignedBlockAndState blockWithAttestations =
        chainBuilder.generateBlockAtSlot(UInt64.valueOf(4), options);
    importBlock(blockWithAttestations);

    // Apply these votes
    processHead(blockWithAttestations.getSlot());
    assertThat(recentChainData.getBestBlockRoot()).contains(blockWithAttestations.getRoot());

    // Now we import the fork block
    importBlock(forkBlock);

    // Then we get a later attestation from the same validator pointing to a different chain
    final UInt64 updatedAttestationSlot =
        applyAttestationFromValidator(UInt64.ZERO, blockWithAttestations);

    // And we should be able to apply the new weightings without making the fork block's weight
    // negative
    assertDoesNotThrow(() -> forkChoice.processHead(updatedAttestationSlot));
  }

  @Test
  void onBlock_shouldHandleNonCanonicalBlockThatUpdatesJustifiedCheckpoint() {
    // If the new block is not the child of the current head block we use `ProtoArray.findHead`
    // to check if it should become the new head.  If importing that block caused the justified
    // checkpoint to be updated, then the justified epoch in ProtoArray won't match the justified
    // epoch of the new head so it considers the head invalid.  Normally that update is done when
    // applying pending votes.

    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final UInt64 epoch4StartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    justifyEpoch(chainUpdater, 2);

    // Update ProtoArray to avoid the special case of considering the anchor
    // epoch as allowing all nodes to be a valid head.
    processHead(epoch4StartSlot);

    prepEpochForJustification(chainUpdater, epoch4StartSlot);

    // Switch head to a different fork so the next block has to use findHead
    chainUpdater.updateBestBlock(chainBuilder.fork().generateNextBlock());

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(epoch4StartSlot);
    importBlock(epoch4Block);

    // Should now have justified epoch 3
    assertThat(recentChainData.getJustifiedCheckpoint())
        .map(Checkpoint::getEpoch)
        .contains(UInt64.valueOf(3));

    // The only block with the newly justified checkpoint is epoch4Block so it should become head
    assertThat(recentChainData.getBestBlockRoot()).contains(epoch4Block.getRoot());
  }

  @Test
  void onBlock_shouldSendForkChoiceUpdatedNotification() {
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(blockAndState.getSlot());
    final SafeFuture<BlockImportResult> importResult =
        forkChoice.onBlock(
            blockAndState.getBlock(),
            Optional.empty(),
            BlockBroadcastValidator.NOOP,
            executionLayer);
    assertBlockImportedSuccessfully(importResult, false);

    assertForkChoiceUpdateNotification(blockAndState, false);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportRecentMergeBlock() {
    final SignedBlockAndState epoch4Block = generateMergeBlock();
    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    importBlockAndAssertFailure(
        epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportOnELFailure() {
    doMerge();
    UInt64 slotToImport = recentChainData.getHeadSlot().plus(1);

    // make EL returning low level error
    executionLayer.setPayloadStatus(
        PayloadStatus.failedExecution(new RuntimeException("net error")));

    // generate block which finalize epoch 2
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockAndAssertFailure(epoch4Block, FailureReason.FAILED_EXECUTION_PAYLOAD_EXECUTION);
  }

  @Test
  void onBlock_shouldNotOptimisticallyImportInvalidExecutionPayload() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // make EL returning INVALID
    executionLayer.setPayloadStatus(PayloadStatus.invalid(Optional.empty(), Optional.empty()));

    storageSystem.chainUpdater().setCurrentSlot(slotToImport.increment());
    importBlockAndAssertFailure(
        chainBuilder.generateNextBlock(), FailureReason.FAILED_STATE_TRANSITION);
    verify(debugDataDumper)
        .saveInvalidBlock(
            eq(chainBuilder.getLatestBlockAndState().getBlock()),
            eq(FailureReason.FAILED_STATE_TRANSITION.toString()),
            any());
  }

  @Test
  void onBlock_shouldChangeForkChoiceForLatestValidHashOnInvalidExecutionPayload() {
    doMerge();
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.ACCEPTED);
    final UInt64 slotToImport = prepFinalizeEpoch(2);
    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    // Make an optimistic chain
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    final SignedBlockAndState maybeValidBlock = chainBuilder.generateNextBlock();
    storageSystem.chainUpdater().setCurrentSlot(maybeValidBlock.getSlot());
    importBlockOptimistically(maybeValidBlock);

    storageSystem.chainUpdater().setCurrentSlot(maybeValidBlock.getSlot().increment());
    importBlockOptimistically(chainBuilder.generateNextBlock());

    final SignedBlockAndState latestOptimisticBlock = chainBuilder.generateNextBlock();
    storageSystem.chainUpdater().setCurrentSlot(latestOptimisticBlock.getSlot());
    importBlockOptimistically(latestOptimisticBlock);
    assertHeadIsOptimistic(latestOptimisticBlock);
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getChainHeads().get(0).getRoot())
        .isEqualTo(latestOptimisticBlock.getRoot());

    // make EL returning INVALID with latestValidHash in the past (maybeValidBlock)
    executionLayer.setPayloadStatus(
        PayloadStatus.invalid(
            Optional.of(maybeValidBlock.getExecutionBlockHash().get()), Optional.empty()));
    storageSystem.chainUpdater().setCurrentSlot(latestOptimisticBlock.getSlot().increment());
    SignedBlockAndState invalidBlock = chainBuilder.generateNextBlock();
    importBlockAndAssertFailure(invalidBlock, FailureReason.FAILED_STATE_TRANSITION);
    assertThat(forkChoice.processHead(invalidBlock.getSlot())).isCompleted();
    verify(debugDataDumper)
        .saveInvalidBlock(
            eq(invalidBlock.getBlock()),
            eq(FailureReason.FAILED_STATE_TRANSITION.toString()),
            any());
    assertHeadIsOptimistic(maybeValidBlock);
    assertThat(forkChoiceStrategy.getChainHeads().get(0).getRoot())
        .isEqualTo(maybeValidBlock.getRoot());
  }

  @Test
  void onBlock_shouldNotifyOptimisticSyncChangeOnlyWhenImportingOnCanonicalHead() {
    doMerge();
    UInt64 slotToImport = prepFinalizeEpoch(2);

    // since protoArray initializes with optimistic nodes,
    // we expect a first notification to be optimistic false
    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(false);

    final SignedBlockAndState epoch4Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlock(epoch4Block);

    slotToImport = prepFinalizeEpoch(4);

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    // generate block which finalize epoch 4
    final SignedBlockAndState epoch6Block = chainBuilder.generateBlockAtSlot(slotToImport);
    importBlockOptimistically(epoch6Block);

    verify(optimisticSyncStateTracker).onOptimisticHeadChanged(true);

    UInt64 forkSlot = slotToImport.increment();

    storageSystem.chainUpdater().setCurrentSlot(forkSlot);

    ChainBuilder alternativeChain = chainBuilder.fork();

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.VALID);

    importBlock(chainBuilder.generateBlockAtSlot(forkSlot));

    verify(optimisticSyncStateTracker, times(2)).onOptimisticHeadChanged(false);

    // builds atop the canonical chain
    storageSystem.chainUpdater().setCurrentSlot(forkSlot.plus(1));
    importBlock(chainBuilder.generateBlockAtSlot(forkSlot.plus(1)));

    processHead(forkSlot.plus(1));

    // make EL returning SYNCING
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    // import a fork which won't be canonical
    importBlockOptimistically(alternativeChain.generateBlockAtSlot(forkSlot));

    // no notification is expected
    verifyNoMoreInteractions(optimisticSyncStateTracker);
  }

  @Test
  void onBlock_shouldUpdateProposerBoostRootToFirstBlock() {
    final ChainBuilder forkChain = chainBuilder.fork();

    final SignedBlockAndState block = chainBuilder.generateNextBlock();
    final SignedBlockAndState forkBlock = forkChain.generateNextBlock();

    final BlockOptions forkBlockOptions = BlockOptions.create();
    final List<Attestation> forkAttestations =
        forkChain.streamValidAttestationsWithTargetBlock(forkBlock).limit(2).toList();
    forkAttestations.forEach(forkBlockOptions::addAttestation);
    final SignedBlockAndState forkBlock1 = forkChain.generateNextBlock(forkBlockOptions);

    importBlock(block);
    importBlock(forkBlock);
    importBlock(forkBlock1);

    // proposer boost is given to the first block despite the fork chain having bigger weight
    assertThat(recentChainData.getStore().getProposerBoostRoot()).hasValue(block.getRoot());
  }

  @Test
  void onBlock_shouldUpdateProposerBoostRootWhenDependentRootsMatch() {
    final UInt64 currentEpoch = getEpochAfterMinSeedLookahead();
    final UInt64 dependentSlot = getDependentSlotForEpoch(currentEpoch);
    final UInt64 importSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 parentSlot = importSlot.minus(1);

    final SignedBlockAndState dependentBlock =
        storageSystem.chainUpdater().advanceChainUntil(dependentSlot);
    final ChainBuilder forkChain = chainBuilder.fork();

    final SignedBlockAndState canonicalHead =
        storageSystem.chainUpdater().advanceChainUntil(parentSlot);
    saveDivergentNextBlock(forkChain);
    final SignedBlockAndState forkParent = saveChainUntil(forkChain, parentSlot);

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getAncestor(canonicalHead.getRoot(), dependentSlot))
        .contains(dependentBlock.getRoot());
    assertThat(forkChoiceStrategy.getAncestor(forkParent.getRoot(), dependentSlot))
        .contains(dependentBlock.getRoot());

    applyVotesToTargetBlock(canonicalHead);
    processHead(parentSlot);
    assertThat(recentChainData.getBestBlockRoot()).contains(canonicalHead.getRoot());

    tickToSlot(importSlot);
    final SignedBlockAndState forkBlock = forkChain.generateBlockAtSlot(importSlot);
    importBlock(forkBlock);

    assertThat(recentChainData.getStore().getProposerBoostRoot()).hasValue(forkBlock.getRoot());
  }

  @Test
  void onBlock_shouldNotUpdateProposerBoostRootWhenDependentRootsDiffer() {
    final ChainBuilder forkChain = chainBuilder.fork();
    final UInt64 currentEpoch = getEpochAfterMinSeedLookahead();
    final UInt64 dependentSlot = getDependentSlotForEpoch(currentEpoch);
    final UInt64 importSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 parentSlot = importSlot.minus(1);

    saveDivergentNextBlock(forkChain);
    final SignedBlockAndState canonicalHead =
        storageSystem.chainUpdater().advanceChainUntil(parentSlot);
    final SignedBlockAndState forkParent = saveChainUntil(forkChain, parentSlot);

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getAncestor(canonicalHead.getRoot(), dependentSlot))
        .isNotEqualTo(forkChoiceStrategy.getAncestor(forkParent.getRoot(), dependentSlot));

    applyVotesToTargetBlock(canonicalHead);
    processHead(parentSlot);
    assertThat(recentChainData.getBestBlockRoot()).contains(canonicalHead.getRoot());

    tickToSlot(importSlot);
    final SignedBlockAndState forkBlock = forkChain.generateBlockAtSlot(importSlot);
    importBlock(forkBlock);

    assertThat(recentChainData.getStore().getProposerBoostRoot()).isEmpty();
  }

  @Test
  void applyHead_shouldSendForkChoiceUpdatedNotification() {
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChainUntil(1);

    processHead(ONE);

    assertForkChoiceUpdateNotification(blockAndState, false);
  }

  @Test
  void applyHead_shouldSendForkChoiceUpdatedNotificationWhenOptimistic() {
    doMerge();
    finalizeEpoch(2);
    assertThat(recentChainData.isChainHeadOptimistic()).isFalse();

    final UInt64 nextBlockSlot = storageSystem.chainBuilder().getLatestSlot().plus(1);
    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot);
    final SignedBlockAndState blockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot);

    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    importBlockOptimistically(blockAndState);
    assertForkChoiceUpdateNotification(blockAndState, true);

    // Optimistic head should be tracked
    assertHeadIsOptimistic(blockAndState);

    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    processHead(recentChainData.getHeadSlot());

    assertForkChoiceUpdateNotification(blockAndState, true, times(2));
  }

  @Test
  void processHead_shouldValidateAncestorTransitionBlockWhenHeadNowValid() {
    doMerge();

    assertThat(forkChoice.processHead(recentChainData.getHeadSlot())).isCompleted();

    verify(transitionBlockValidator)
        .verifyAncestorTransitionBlock(recentChainData.getBestBlockRoot().orElseThrow());
  }

  @Test
  void onForkChoiceUpdatedResult_validGloasNode_shouldForwardExactHeadNode() {
    setupWithSpec(TestSpecFactory.createMinimalGloas());
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            FastConfirmationTracker.NOOP,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            LateBlockReorgPreparationHandler.NOOP,
            debugDataDumper,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    final Bytes32 blockRoot = Bytes32.random();
    final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);

    when(recentChainData.getUpdatableForkChoiceStrategy())
        .thenReturn(Optional.of(forkChoiceStrategy));
    when(transitionBlockValidator.verifyAncestorTransitionBlock(blockRoot))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));

    forkChoice.onForkChoiceUpdatedResult(
        new ForkChoiceUpdatedResultNotification(
            new ForkChoiceState(
                emptyNode, ONE, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO, false),
            Optional.empty(),
            false,
            SafeFuture.completedFuture(
                new ForkChoiceUpdatedResult(PayloadStatus.VALID, Optional.empty()))));

    verify(forkChoiceStrategy)
        .onForkChoiceUpdatedResult(
            new SlotAndForkChoiceNode(ONE, emptyNode), PayloadStatus.VALID, false);
    verify(transitionBlockValidator).verifyAncestorTransitionBlock(blockRoot);
    verify(forkChoiceStrategy, never())
        .onExecutionPayloadResult(eq(blockRoot), eq(PayloadStatus.VALID), anyBoolean());
  }

  @Test
  void onForkChoiceUpdatedResult_invalidGloasNode_shouldForwardExactHeadNode() {
    setupWithSpec(TestSpecFactory.createMinimalGloas());
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            FastConfirmationTracker.NOOP,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            LateBlockReorgPreparationHandler.NOOP,
            debugDataDumper,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    final Bytes32 blockRoot = Bytes32.random();
    final ForkChoiceNode emptyNode = ForkChoiceNode.createEmpty(blockRoot);
    final PayloadStatus invalidPayloadStatus =
        PayloadStatus.invalid(Optional.empty(), Optional.of("invalid payload"));

    when(recentChainData.getUpdatableForkChoiceStrategy())
        .thenReturn(Optional.of(forkChoiceStrategy));

    forkChoice.onForkChoiceUpdatedResult(
        new ForkChoiceUpdatedResultNotification(
            new ForkChoiceState(
                emptyNode, ONE, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO, false),
            Optional.empty(),
            false,
            SafeFuture.completedFuture(
                new ForkChoiceUpdatedResult(invalidPayloadStatus, Optional.empty()))));

    verify(forkChoiceStrategy)
        .onForkChoiceUpdatedResult(
            new SlotAndForkChoiceNode(ONE, emptyNode), invalidPayloadStatus, true);
    verify(forkChoiceStrategy, never())
        .onExecutionPayloadResult(eq(blockRoot), eq(invalidPayloadStatus), anyBoolean());
  }

  @Test
  void processHead_shouldMarkHeadInvalidAndRunForkChoiceWhenTransitionBlockFoundToBeInvalid() {
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);

    Bytes32 initialHeadRoot = recentChainData.getChainHead().orElseThrow().getRoot();

    doMerge(true);

    assertThat(recentChainData.isChainHeadOptimistic()).isTrue();

    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.VALID);
    final Bytes32 chainHeadRoot = recentChainData.getChainHead().orElseThrow().getRoot();
    when(transitionBlockValidator.verifyAncestorTransitionBlock(chainHeadRoot))
        .thenReturn(
            SafeFuture.completedFuture(
                new PayloadValidationResult(
                    PayloadStatus.invalid(Optional.empty(), Optional.empty()))));

    assertThat(recentChainData.getStore().containsBlock(chainHeadRoot)).isTrue();

    UInt64 headSlot = recentChainData.getHeadSlot();
    assertThat(forkChoice.processHead(headSlot)).isCompleted();

    // Chain head was marked invalid so removed from the store
    assertThat(recentChainData.getStore().containsBlock(chainHeadRoot)).isFalse();
    // Chain head reverted to the previous valid head
    assertThat(recentChainData.getChainHead().map(ChainHead::getRoot)).hasValue(initialHeadRoot);

    ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);

    verify(forkChoiceNotifier, times(2))
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.empty()));

    // EL should have been notified of the invalid head first and after that the valid
    // head
    List<ForkChoiceState> notifiedStates = forkChoiceStateCaptor.getAllValues();
    assertThat(notifiedStates.get(0).headBlock().blockRoot()).isEqualTo(chainHeadRoot);
    assertThat(notifiedStates.get(1).headBlock().blockRoot()).isEqualTo(initialHeadRoot);
  }

  @Test
  void processHead_shouldNotValidateAncestorTransitionBlockWhenHeadNotValid() {
    doMerge();
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);

    assertThat(forkChoice.processHead(recentChainData.getHeadSlot())).isCompleted();

    verifyNoInteractions(transitionBlockValidator);
  }

  @Test
  void onBlock_shouldUseLatestValidHashFromForkChoiceUpdated() {
    doMerge();
    finalizeEpoch(2);
    assertThat(recentChainData.isChainHeadOptimistic()).isFalse();

    final UInt64 nextBlockSlot = storageSystem.chainBuilder().getLatestSlot().plus(1);
    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot);
    final SignedBlockAndState blockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot);

    executionLayer.setPayloadStatus(PayloadStatus.SYNCING);
    setForkChoiceNotifierForkChoiceUpdatedResult(PayloadStatus.SYNCING);
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            blockAndState.getBlock(),
            Optional.empty(),
            BlockBroadcastValidator.NOOP,
            executionLayer);
    assertBlockImportedSuccessfully(result, true);

    assertForkChoiceUpdateNotification(blockAndState, true);

    // let's import a new block on top the optimistic head
    // let's make the EL return ACCEPTED on newPayload call but return INVALID on forkChoiceUpdated
    // call
    // INVALID will give us a lastValidHash corresponding to the previous block payload (imported
    // optimistically)

    executionLayer.setPayloadStatus(PayloadStatus.ACCEPTED);

    PayloadStatus invalidWithLastValidBlockHash =
        PayloadStatus.create(
            ExecutionPayloadStatus.INVALID,
            blockAndState
                .getBlock()
                .getMessage()
                .getBody()
                .getOptionalExecutionPayload()
                .map(ExecutionPayload::getBlockHash),
            Optional.empty());

    // first time, fork choice update block will be invalid and after that it will be valid
    setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(
        List.of(invalidWithLastValidBlockHash, PayloadStatus.VALID));

    storageSystem.chainUpdater().setCurrentSlot(nextBlockSlot.increment());
    final SignedBlockAndState blockAndStatePlus1 =
        storageSystem.chainBuilder().generateBlockAtSlot(nextBlockSlot.increment());

    // before importing, previous block is optimistic
    assertThat(isFullyValidated(blockAndState.getRoot())).isFalse();

    importBlockOptimistically(blockAndStatePlus1);

    // after importing, previous block is fully valid
    assertThat(isFullyValidated(blockAndState.getRoot())).isTrue();

    // running fork choice will be automatic because of the invalid head block, no need of manual
    // head processing in the test

    ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);
    verify(forkChoiceNotifier, atLeastOnce())
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.empty()));

    // last notification to EL should be a valid block
    ForkChoiceState lastNotifiedState = forkChoiceStateCaptor.getValue();
    assertThat(lastNotifiedState.headBlock().blockRoot()).isEqualTo(blockAndState.getRoot());

    // New head is optimistic because latestValidHash might still point to an optimistic block
    assertHeadIsOptimistic(blockAndState);
  }

  @ParameterizedTest(name = "onForkChoiceUpdatedResult_{0}")
  @MethodSource("getForkChoiceUpdatedResults")
  void onForkChoiceUpdatedResult_shouldLogWhenInvalidTerminalBlock(
      final ForkChoiceUpdatedResult result) {
    final ForkChoiceState state = mock(ForkChoiceState.class);
    when(state.headExecutionBlockHash()).thenReturn(Bytes32.random());
    when(state.headBlock()).thenReturn(ForkChoiceNode.createBase(Bytes32.random()));
    when(state.headBlockSlot()).thenReturn(ZERO);
    try (LogCaptor logCaptor = LogCaptor.forClass(ForkChoice.class)) {
      forkChoice.onForkChoiceUpdatedResult(
          new ForkChoiceUpdatedResultNotification(
              state, Optional.empty(), true, SafeFuture.completedFuture(result)));
      if (result.getPayloadStatus().hasInvalidStatus()) {
        final List<String> errorLogs = logCaptor.getErrorLogs();
        assertThat(errorLogs).singleElement().asString().contains("INVALID", "terminal");
      } else {
        assertThat(logCaptor.getErrorLogs()).isEmpty();
      }
    }
  }

  @Test
  void prepareForBlockProduction_NotYetInProposalSlotShouldRunOnTickWhenWithinTolerance() {
    final UInt64 newTime =
        spec.computeTimeMillisAtSlot(ONE, recentChainData.getGenesisTimeMillis())
            .minusMinZero(BLOCK_CREATION_TOLERANCE_MS - 100);
    storageSystem.chainUpdater().setTimeMillis(newTime);

    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));
    assertThat(forkChoice.prepareForBlockProduction(ONE, BlockProductionPerformance.NOOP))
        .isCompleted();
    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ONE));

    verify(forkChoiceNotifier, times(1)).onForkChoiceUpdated(any(), eq(Optional.of(ONE)));
  }

  @Test
  void prepareForBlockProduction_NotYetInProposalSlotShouldNotRunOnTickWhenOutOfTolerance() {
    final UInt64 newTime =
        spec.computeTimeMillisAtSlot(ONE, recentChainData.getGenesisTimeMillis())
            .minusMinZero(BLOCK_CREATION_TOLERANCE_MS + 100);
    storageSystem.chainUpdater().setTimeMillis(newTime);

    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));
    assertThat(forkChoice.prepareForBlockProduction(ONE, BlockProductionPerformance.NOOP))
        .isCompleted();
    assertThat(recentChainData.getCurrentSlot()).isEqualTo(Optional.of(ZERO));

    verifyNoInteractions(forkChoiceNotifier);
  }

  @Test
  void prepareForBlockProduction_shouldNotifyForkChoiceUpdatedWhenProposerHeadUnchanged() {
    final UInt64 proposalSlot = UInt64.valueOf(2);
    final SignedBlockAndState canonicalHead = storageSystem.chainUpdater().advanceChain(ONE);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(proposalSlot);

    final ChainHead blockProductionHead =
        safeJoin(
            forkChoice.prepareForBlockProduction(proposalSlot, BlockProductionPerformance.NOOP));

    assertThat(blockProductionHead.getRoot()).isEqualTo(canonicalHead.getRoot());

    final ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);
    verify(forkChoiceNotifier)
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.of(proposalSlot)));
    assertThat(forkChoiceStateCaptor.getValue().headBlock().blockRoot())
        .isEqualTo(canonicalHead.getRoot());
    assertThat(forkChoiceStateCaptor.getValue().headBlockSlot()).isEqualTo(canonicalHead.getSlot());
  }

  @Test
  void prepareForBlockProduction_shouldUseProposerHeadAndWaitForLateBlockReorgPreparation() {
    final SignedBlockAndState proposerHead = storageSystem.chainUpdater().advanceChain(ONE);
    final SignedBlockAndState canonicalHead = storageSystem.chainUpdater().advanceChain(2);
    final UInt64 proposalSlot = UInt64.valueOf(3);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(proposalSlot);
    final ForkChoiceNode proposerHeadNode =
        recentChainData
            .getStore()
            .getForkChoiceStrategy()
            .getParentBeaconBlockNode(ForkChoiceNode.createBase(canonicalHead.getRoot()))
            .orElseThrow();
    assertThat(proposerHeadNode.blockRoot()).isEqualTo(proposerHead.getRoot());

    final RecentChainData recentChainDataSpy = spy(recentChainData);
    doReturn(proposerHeadNode).when(recentChainDataSpy).getProposerHead(any(), eq(proposalSlot));
    recentChainData = recentChainDataSpy;

    final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler =
        mock(LateBlockReorgPreparationHandler.class);
    final SafeFuture<Void> preparationFuture = new SafeFuture<>();
    when(lateBlockReorgPreparationHandler.onLateBlockReorgPreparation(
            proposerHead.getSlot(), canonicalHead.getRoot()))
        .thenReturn(preparationFuture);
    recreateForkChoice(lateBlockReorgPreparationHandler);

    final SafeFuture<ChainHead> result =
        forkChoice.prepareForBlockProduction(proposalSlot, BlockProductionPerformance.NOOP);

    assertThatSafeFuture(result).isNotCompleted();
    verify(lateBlockReorgPreparationHandler)
        .onLateBlockReorgPreparation(proposerHead.getSlot(), canonicalHead.getRoot());

    final ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
        ArgumentCaptor.forClass(ForkChoiceState.class);
    verify(forkChoiceNotifier)
        .onForkChoiceUpdated(forkChoiceStateCaptor.capture(), eq(Optional.of(proposalSlot)));
    assertThat(forkChoiceStateCaptor.getValue().headBlock()).isEqualTo(proposerHeadNode);
    assertThat(forkChoiceStateCaptor.getValue().headBlockSlot()).isEqualTo(proposerHead.getSlot());

    preparationFuture.complete(null);

    final ChainHead blockProductionHead = safeJoin(result);
    assertThat(blockProductionHead.getRoot()).isEqualTo(proposerHead.getRoot());
    assertThat(blockProductionHead.getForkChoiceNode()).isEqualTo(proposerHeadNode);
  }

  private static Stream<ForkChoiceUpdatedResult> getForkChoiceUpdatedResults() {
    Set<PayloadStatus> statuses =
        Set.of(
            PayloadStatus.invalid(Optional.empty(), Optional.empty()),
            PayloadStatus.invalid(Optional.empty(), Optional.of("error")),
            PayloadStatus.invalid(Optional.of(Bytes32.random()), Optional.empty()),
            PayloadStatus.failedExecution(new RuntimeException("error")),
            PayloadStatus.ACCEPTED,
            PayloadStatus.SYNCING,
            PayloadStatus.VALID);
    return statuses.stream().map(status -> new ForkChoiceUpdatedResult(status, Optional.empty()));
  }

  private void assertHeadIsOptimistic(final SignedBlockAndState blockAndState) {
    final ChainHead optimisticHead = recentChainData.getChainHead().orElseThrow();
    assertThat(optimisticHead.getSlot()).isEqualTo(blockAndState.getSlot());
    assertThat(optimisticHead.getRoot()).isEqualTo(blockAndState.getRoot());
    assertThat(optimisticHead.isOptimistic()).isTrue();
    assertThat(recentChainData.isChainHeadOptimistic()).isTrue();
  }

  private boolean isFullyValidated(final Bytes32 root) {
    return recentChainData.getForkChoiceStrategy().orElseThrow().isFullyValidated(root);
  }

  private void assertForkChoiceUpdateNotification(
      final SignedBlockAndState blockAndState,
      final boolean optimisticHead,
      final VerificationMode mode) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final UInt64 headExecutionBlockNumber =
        forkChoiceStrategy.executionBlockNumber(blockAndState.getRoot()).orElseThrow();
    final Bytes32 headExecutionHash =
        forkChoiceStrategy.executionBlockHash(blockAndState.getRoot()).orElseThrow();
    final Bytes32 justifiedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getJustifiedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    final Bytes32 finalizedExecutionHash =
        forkChoiceStrategy
            .executionBlockHash(recentChainData.getFinalizedCheckpoint().orElseThrow().getRoot())
            .orElseThrow();
    verify(forkChoiceNotifier, mode)
        .onForkChoiceUpdated(
            new ForkChoiceState(
                ForkChoiceNode.createBase(blockAndState.getRoot()),
                blockAndState.getSlot(),
                headExecutionBlockNumber,
                headExecutionHash,
                justifiedExecutionHash,
                finalizedExecutionHash,
                optimisticHead),
            Optional.empty());
  }

  private void assertForkChoiceUpdateNotification(
      final SignedBlockAndState blockAndState, final boolean optimisticHead) {
    assertForkChoiceUpdateNotification(blockAndState, optimisticHead, times(1));
  }

  private void justifyEpoch(final ChainUpdater chainUpdater, final long epoch) {
    final UInt64 nextEpochStartSlot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch + 1))
            .max(chainUpdater.getHeadSlot().plus(1));
    // Advance chain to an epoch we can actually justify
    prepEpochForJustification(chainUpdater, nextEpochStartSlot);

    // Trigger epoch transition into next epoch.
    importBlock(chainBuilder.generateBlockAtSlot(nextEpochStartSlot));

    // Should now have justified epoch
    assertThat(recentChainData.getJustifiedCheckpoint())
        .map(Checkpoint::getEpoch)
        .contains(UInt64.valueOf(epoch));
  }

  private void prepEpochForJustification(
      final ChainUpdater chainUpdater, final UInt64 nextEpochStartSlot) {
    UInt64 headSlot = chainUpdater.getHeadSlot();
    final UInt64 targetHeadSlot = nextEpochStartSlot.minus(2);
    while (headSlot.isLessThan(targetHeadSlot)) {
      SignedBlockAndState headBlock = chainBuilder.generateBlockAtSlot(headSlot.plus(1));
      importBlock(headBlock);
      assertThat(recentChainData.getBestBlockRoot()).contains(headBlock.getRoot());
      headSlot = headBlock.getSlot();
    }

    // Add a block with enough attestations to justify the epoch.
    final BlockOptions epoch2BlockOptions = BlockOptions.create();
    final UInt64 newBlockSlot = headSlot.increment();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(newBlockSlot)
        .forEach(epoch2BlockOptions::addAttestation);
    final SignedBlockAndState epoch2Block =
        chainBuilder.generateBlockAtSlot(newBlockSlot, epoch2BlockOptions);

    importBlock(epoch2Block);
  }

  private UInt64 prepFinalizeEpoch(final long epoch) {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final UInt64 epochPlus2StartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(epoch).plus(2));

    justifyEpoch(chainUpdater, epoch);

    prepEpochForJustification(chainUpdater, epochPlus2StartSlot);

    return epochPlus2StartSlot;
  }

  private void finalizeEpoch(final long epoch) {
    final UInt64 nextBlockSlot = prepFinalizeEpoch(epoch);
    importBlock(chainBuilder.generateBlockAtSlot(nextBlockSlot));
  }

  private void doMerge() {
    doMerge(false);
  }

  private void doMerge(final boolean optimistic) {
    final SignedBlockAndState epoch4Block = generateMergeBlock();

    if (optimistic) {
      storageSystem.chainUpdater().saveOptimisticBlock(epoch4Block);
    } else {
      storageSystem.chainUpdater().saveBlock(epoch4Block);
    }
    storageSystem.chainUpdater().updateBestBlock(epoch4Block);
  }

  private SignedBlockAndState generateMergeBlock() {
    final Bytes32 terminalBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 terminalBlockParentHash = dataStructureUtil.randomBytes32();
    final PowBlock terminalBlock = new PowBlock(terminalBlockHash, terminalBlockParentHash, ZERO);
    final PowBlock terminalParentBlock =
        new PowBlock(terminalBlockParentHash, dataStructureUtil.randomBytes32(), ZERO);
    executionLayer.addPowBlock(terminalBlock);
    executionLayer.addPowBlock(terminalParentBlock);
    return chainBuilder.generateBlockAtSlot(
        storageSystem.chainUpdater().getHeadSlot().plus(1),
        BlockOptions.create().setTerminalBlockHash(terminalBlockHash));
  }

  @Test
  void onAttestation_shouldBeInvalidWhenTargetRefersToBlockAfterTargetEpochStart() {
    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(5);
    importBlock(targetBlock);

    // Attestation where the target checkpoint has a slot prior to the block it references
    final Checkpoint targetCheckpoint = new Checkpoint(ZERO, targetBlock.getRoot());
    final Attestation attestation =
        attestationSchema.create(
            attestationSchema.getAggregationBitsSchema().ofBits(5),
            new AttestationData(
                targetBlock.getSlot(),
                spec.computeEpochAtSlot(targetBlock.getSlot()),
                targetBlock.getRoot(),
                targetBlock.getState().getCurrentJustifiedCheckpoint(),
                targetCheckpoint),
            BLSSignature.empty());
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidatableAttestation.from(spec, attestation));
    assertThat(result)
        .isCompletedWithValue(
            AttestationProcessingResult.invalid(
                "LMD vote must be consistent with FFG vote target"));
  }

  @Test
  void onAttestation_shouldDeferAttestationsFromCurrentSlot() {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(UInt64.valueOf(10));
    final UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(currentSlot);
    importBlock(targetBlock);

    final Attestation attestation =
        chainBuilder.streamValidAttestationsWithTargetBlock(targetBlock).findFirst().orElseThrow();
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(ValidatableAttestation.from(spec, attestation));
    assertThat(result).isCompletedWithValue(AttestationProcessingResult.DEFER_FOR_FORK_CHOICE);

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(forkChoiceStrategy.getWeight(targetBlock.getRoot())).contains(ZERO);

    // Should apply at start of next slot.
    forkChoice.onTick(
        spec.computeTimeMillisAtSlot(currentSlot.plus(1), recentChainData.getGenesisTimeMillis()),
        Optional.empty());
    processHead(currentSlot.plus(1));

    assertThat(forkChoiceStrategy.getWeight(targetBlock.getRoot()).orElseThrow())
        .isGreaterThan(ZERO);
  }

  @Test
  void onAttestation_gloasFullVoteShouldWaitForExecutionPayload() {
    setupWithSpec(
        TestSpecFactory.createMinimalGloas(
            builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP)));
    assertThat(forkChoice.applyGenesisExecutionPayloadForGloas()).isCompleted();

    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(ONE);
    importBlock(targetBlock);

    final UInt64 attestationSlot = targetBlock.getSlot().plus(1);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(attestationSlot.plus(1));
    final ValidatableAttestation attestation =
        createPrevalidatedFullPayloadAttestation(targetBlock, attestationSlot);

    assertThat(forkChoice.onAttestation(attestation))
        .isCompletedWithValue(AttestationProcessingResult.UNKNOWN_EXECUTION_PAYLOAD);

    assertFullPayloadVoteIsPending(targetBlock);
  }

  @Test
  void onAttestation_gloasFullVoteShouldRequireReprocessingWhenPayloadArrives() {
    setupWithSpec(
        TestSpecFactory.createMinimalGloas(
            builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP)));
    assertThat(forkChoice.applyGenesisExecutionPayloadForGloas()).isCompleted();

    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(ONE);
    importBlock(targetBlock);

    final UInt64 attestationSlot = targetBlock.getSlot().plus(1);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(attestationSlot);
    final ValidatableAttestation attestation =
        createPrevalidatedFullPayloadAttestation(targetBlock, attestationSlot);

    assertThat(forkChoice.onAttestation(attestation))
        .isCompletedWithValue(AttestationProcessingResult.UNKNOWN_EXECUTION_PAYLOAD);

    importPayload(targetBlock);

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(
            forkChoiceStrategy
                .getBlockData(targetBlock.getRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL)
                .orElseThrow()
                .getWeight())
        .isEqualTo(ZERO);
  }

  /**
   * beacon-APIs requires (`should`) a second head event for the same beacon block and slot when the
   * head's payload status changes from empty to full. See
   * https://github.com/ethereum/beacon-APIs/pull/628.
   */
  @Test
  void onExecutionPayloadEnvelope_shouldUpdateHeadWhenPayloadStatusChangesFromEmptyToFull() {
    setupWithSpec(
        TestSpecFactory.createMinimalGloas(
            builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP)));
    assertThat(forkChoice.applyGenesisExecutionPayloadForGloas()).isCompleted();

    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(ONE);
    importBlock(block);

    // The payload hasn't been revealed yet, so the head is the EMPTY node of the new block.
    final List<HeadEvent> headEvents = storageSystem.chainHeadChannel().getHeadEvents();
    assertThat(headEvents).isNotEmpty();
    final HeadEvent emptyHeadEvent = headEvents.getLast();
    assertThat(emptyHeadEvent.getSlot()).isEqualTo(block.getSlot());
    assertThat(emptyHeadEvent.getBestBlockRoot()).isEqualTo(block.getRoot());
    assertThat(emptyHeadEvent.getPayloadStatus())
        .contains(ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY);
    headEvents.clear();

    importPayload(block);

    // A second head event for the same block and slot, now reporting the FULL payload status.
    assertThat(headEvents).hasSize(1);
    final HeadEvent fullHeadEvent = headEvents.getFirst();
    assertThat(fullHeadEvent.getSlot()).isEqualTo(block.getSlot());
    assertThat(fullHeadEvent.getBestBlockRoot()).isEqualTo(block.getRoot());
    assertThat(fullHeadEvent.getPayloadStatus())
        .contains(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
  }

  @Test
  void applyIndexedAttestations_gloasFullVoteShouldNotApplyWhenExecutionPayloadMissing() {
    setupWithSpec(
        TestSpecFactory.createMinimalGloas(
            builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP)));
    assertThat(forkChoice.applyGenesisExecutionPayloadForGloas()).isCompleted();

    final SignedBlockAndState targetBlock = chainBuilder.generateBlockAtSlot(ONE);
    importBlock(targetBlock);

    final UInt64 attestationSlot = targetBlock.getSlot().plus(1);
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(attestationSlot.plus(1));
    final ValidatableAttestation attestation =
        createPrevalidatedFullPayloadAttestation(targetBlock, attestationSlot);

    assertThat(forkChoice.applyIndexedAttestations(List.of(attestation)))
        .isCompletedWithValue(List.of(attestation));

    assertFullPayloadVoteIsPending(targetBlock);
  }

  private void assertFullPayloadVoteIsPending(final SignedBlockAndState targetBlock) {
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    assertThat(
            forkChoiceStrategy.getBlockData(
                targetBlock.getRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .isEmpty();
    assertThat(
            forkChoiceStrategy
                .getBlockData(targetBlock.getRoot(), ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY)
                .orElseThrow()
                .getWeight())
        .isEqualTo(ZERO);
  }

  private void importPayload(final SignedBlockAndState targetBlock) {
    final SafeFuture<ExecutionPayloadImportResult> payloadImportResult =
        forkChoice.onExecutionPayloadEnvelope(
            chainBuilder.getExecutionPayloadAtSlot(targetBlock.getSlot()).orElseThrow(),
            executionLayer);

    assertThat(payloadImportResult)
        .isCompletedWithValueMatching(ExecutionPayloadImportResult::isSuccessful);
  }

  private ValidatableAttestation createPrevalidatedFullPayloadAttestation(
      final SignedBlockAndState targetBlock, final UInt64 attestationSlot) {
    final UInt64 targetEpoch = spec.computeEpochAtSlot(attestationSlot);
    final Bytes32 targetRoot =
        recentChainData
            .getForkChoiceStrategy()
            .orElseThrow()
            .getAncestor(targetBlock.getRoot(), spec.computeStartSlotAtEpoch(targetEpoch))
            .orElse(targetBlock.getRoot());
    final AttestationData data =
        new AttestationData(
            attestationSlot,
            UInt64.ONE,
            targetBlock.getRoot(),
            recentChainData.getStore().getJustifiedCheckpoint(),
            new Checkpoint(targetEpoch, targetRoot));
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(
            spec,
            attestationSchema.create(
                attestationSchema.getAggregationBitsSchema().ofBits(16),
                data,
                BLSSignature.empty(),
                () -> attestationSchema.createEmptyCommitteeBits().orElseThrow()));
    validatableAttestation.setIndexedAttestation(
        new IndexedAttestationLight(List.of(UInt64.ZERO), data, BLSSignature.empty()));
    validatableAttestation.setValidIndexedAttestation();
    return validatableAttestation;
  }

  private UInt64 applyAttestationFromValidator(
      final UInt64 validatorIndex, final SignedBlockAndState targetBlock) {
    // Note this attestation is wildly invalid but we're going to shove it straight into fork choice
    // as pre-validated.
    final UInt64 updatedAttestationSlot = UInt64.valueOf(20);
    final ValidatableAttestation updatedVote =
        ValidatableAttestation.from(
            spec,
            attestationSchema.create(
                attestationSchema.getAggregationBitsSchema().ofBits(16),
                new AttestationData(
                    updatedAttestationSlot,
                    UInt64.ONE,
                    targetBlock.getRoot(),
                    recentChainData.getStore().getJustifiedCheckpoint(),
                    new Checkpoint(
                        spec.computeEpochAtSlot(updatedAttestationSlot), targetBlock.getRoot())),
                dataStructureUtil.randomSignature()));
    updatedVote.setIndexedAttestation(
        new IndexedAttestationLight(
            List.of(validatorIndex),
            updatedVote.getData(),
            updatedVote.getAttestation().getAggregateSignature()));

    assertThat(forkChoice.applyIndexedAttestations(List.of(updatedVote)))
        .isCompletedWithValue(List.of());
    return updatedAttestationSlot;
  }

  private void assertBlockImportedSuccessfully(
      final SafeFuture<BlockImportResult> importResult, final boolean optimistically) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = safeJoin(importResult);
    assertThat(result.isSuccessful()).describedAs(result.toString()).isTrue();
    assertThat(result.isImportedOptimistically())
        .describedAs(result.toString())
        .isEqualTo(optimistically);
  }

  private void setBlobSidecarsAvailabilityResult(
      final DataAndValidationResult<BlobSidecar> result) {
    final SafeFuture<DataAndValidationResult<BlobSidecar>> resultFuture =
        SafeFuture.completedFuture(result);
    doReturn(resultFuture).when(blobSidecarsAvailabilityChecker).getAvailabilityCheckResult();
    doReturn(resultFuture)
        .when(blobSidecarsAvailabilityChecker)
        .getAndLogAvailabilityCheckResult(any());
  }

  private SafeFuture<BlockImportResult> importBlockNoResultCheck(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    return forkChoice.onBlock(
        block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
  }

  private void importBlock(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportedSuccessfully(result, false);
  }

  private void importBlockOptimistically(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportedSuccessfully(result, true);
  }

  private UInt64 getEpochAfterMinSeedLookahead() {
    return UInt64.valueOf(spec.getGenesisSpecConfig().getMinSeedLookahead() + 2L);
  }

  private UInt64 getDependentSlotForEpoch(final UInt64 currentEpoch) {
    final int minSeedLookahead = spec.getSpecConfig(currentEpoch).getMinSeedLookahead();
    return spec.computeStartSlotAtEpoch(currentEpoch.minus(minSeedLookahead)).minus(1);
  }

  private SignedBlockAndState saveChainUntil(final ChainBuilder chain, final UInt64 slot) {
    SignedBlockAndState latestBlock = chain.getLatestBlockAndState();
    while (chain.getLatestSlot().isLessThan(slot)) {
      latestBlock = chain.generateNextBlock();
      storageSystem.chainUpdater().saveBlock(latestBlock);
    }
    return latestBlock;
  }

  private SignedBlockAndState saveDivergentNextBlock(final ChainBuilder chain) {
    final SignedBlockAndState block =
        chain.generateNextBlock(
            BlockOptions.create().setEth1Data(dataStructureUtil.randomEth1Data()));
    storageSystem.chainUpdater().saveBlock(block);
    return block;
  }

  private void applyVotesToTargetBlock(final SignedBlockAndState targetBlock) {
    for (int validatorIndex = 0; validatorIndex < 4; validatorIndex++) {
      applyAttestationFromValidator(UInt64.valueOf(validatorIndex), targetBlock);
    }
  }

  private void tickToSlot(final UInt64 slot) {
    forkChoice.onTick(
        spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis()),
        Optional.empty());
  }

  private void assertBlockImportFailure(
      final SafeFuture<BlockImportResult> importResult, final FailureReason failureReason) {
    assertThat(importResult).isCompleted();
    final BlockImportResult result = safeJoin(importResult);
    assertThat(result.getFailureReason()).isEqualTo(failureReason);
  }

  private void importBlockAndAssertFailure(
      final SignedBlockAndState block, final FailureReason failureReason) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(
            block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer);
    assertBlockImportFailure(result, failureReason);
  }

  private void processHead(final UInt64 slot) {
    assertThat(forkChoice.processHead(slot)).isCompleted();
  }

  private void recreateForkChoice(
      final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler) {
    recreateForkChoice(false, lateBlockReorgPreparationHandler);
  }

  private void recreateForkChoice(
      final boolean fastConfirmationEnabled,
      final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler) {
    final FastConfirmationTracker fastConfirmationTracker =
        fastConfirmationEnabled
            ? FastConfirmationTracker.create(
                spec,
                Optional.empty(),
                FastConfirmationEventChannel.NOOP,
                metricsSystem,
                StubTimeProvider.withTimeInMillis(0))
            : FastConfirmationTracker.NOOP;
    recreateForkChoice(fastConfirmationTracker, lateBlockReorgPreparationHandler);
  }

  private void recreateForkChoice(
      final FastConfirmationTracker fastConfirmationTracker,
      final LateBlockReorgPreparationHandler lateBlockReorgPreparationHandler) {
    forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            forkChoiceNotifier,
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            fastConfirmationTracker,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            lateBlockReorgPreparationHandler,
            debugDataDumper,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    // Started as BeaconChainController does, so the per-slot fast confirmation pipeline runs.
    assertThat(forkChoice.getFastConfirmationService().start()).isCompleted();
  }

  private IntSet ptcPositions(final int count) {
    final IntSet positions = new IntOpenHashSet();
    for (int i = 0; i < count; i++) {
      positions.add(i);
    }
    return positions;
  }

  private void setForkChoiceNotifierForkChoiceUpdatedResult(final PayloadStatus status) {
    setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(List.of(status));
  }

  private void setForkChoiceNotifierConsecutiveForkChoiceUpdatedResults(
      final List<PayloadStatus> statuses) {
    if (statuses.isEmpty()) {
      return;
    }
    Stubber stubber = null;
    for (PayloadStatus status : statuses) {
      Optional<ForkChoiceUpdatedResult> result =
          Optional.ofNullable(status)
              .map(payloadStatus -> new ForkChoiceUpdatedResult(payloadStatus, Optional.empty()));
      Answer<Void> onForkChoiceUpdatedResultAnswer = getOnForkChoiceUpdatedResultAnswer(result);
      if (stubber == null) {
        stubber = doAnswer(onForkChoiceUpdatedResultAnswer);
      } else {
        stubber.doAnswer(onForkChoiceUpdatedResultAnswer);
      }
    }
    stubber.when(forkChoiceNotifier).onForkChoiceUpdated(any(), any());
  }

  private Answer<Void> getOnForkChoiceUpdatedResultAnswer(
      final Optional<ForkChoiceUpdatedResult> result) {
    return invocation -> {
      result.ifPresent(
          forkChoiceUpdatedResult ->
              forkChoice.onForkChoiceUpdatedResult(
                  new ForkChoiceUpdatedResultNotification(
                      invocation.getArgument(0),
                      Optional.empty(),
                      false,
                      SafeFuture.completedFuture(forkChoiceUpdatedResult))));
      return null;
    };
  }
}
