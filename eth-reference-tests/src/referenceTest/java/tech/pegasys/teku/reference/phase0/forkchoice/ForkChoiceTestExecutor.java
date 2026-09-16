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

package tech.pegasys.teku.reference.phase0.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.reference.BlsSetting.IGNORED;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Assertions;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.KzgRetriever;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.datacolumns.CurrentSlotProvider;
import tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand;
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerBasicImpl;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarRecoveringCustody;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetrieverStub;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationEventChannel;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.payloadattestation.ValidatablePayloadAttestationMessage;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor implements TestExecutor {
  private static final Logger LOG = LogManager.getLogger();
  private static final String SSZ_SNAPPY_EXTENSION = ".ssz_snappy";
  public static final ImmutableMap<String, TestExecutor> FORK_CHOICE_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("fork_choice/get_head", new ForkChoiceTestExecutor())
          .put("fork_choice/ex_ante", new ForkChoiceTestExecutor())
          .put("fork_choice/reorg", new ForkChoiceTestExecutor())
          .put("fork_choice/on_block", new ForkChoiceTestExecutor())
          .put("fork_choice/on_attestation", new ForkChoiceTestExecutor())
          .put("fork_choice/on_merge_block", IGNORE_TESTS) // TTD Logic is deprecated
          .put("fork_choice/withholding", new ForkChoiceTestExecutor())
          .put("sync/optimistic", new ForkChoiceTestExecutor())
          .put("fork_choice/get_proposer_head", new ForkChoiceTestExecutor())
          .put("fork_choice/deposit_with_reorg", new ForkChoiceTestExecutor())
          .put("fork_choice/get_parent_payload_status", new ForkChoiceTestExecutor())
          .put("fork_choice/on_execution_payload_envelope", new ForkChoiceTestExecutor())
          .put("fork_choice/on_payload_attestation_message", new ForkChoiceTestExecutor())
          .put("fork_choice/payload_data_availability", new ForkChoiceTestExecutor())
          .put("fork_choice/payload_timeliness", new ForkChoiceTestExecutor())
          .put(
              "fork_choice/should_apply_proposer_boost",
              new ForkChoiceTestExecutor(
                  // TODO this is skipped because it relies on equivocation, which is not
                  // currently implemented. See https://github.com/Consensys/teku/issues/10608
                  "should_apply_proposer_boost_withheld"))
          // Fork choice generated test types
          .put("fork_choice_compliance/block_weight_test", new ForkChoiceTestExecutor())
          .put("fork_choice_compliance/block_tree_test", new ForkChoiceTestExecutor())
          .put("fork_choice_compliance/attester_slashing_test", new ForkChoiceTestExecutor())
          .put("fork_choice_compliance/invalid_message_test", new ForkChoiceTestExecutor())
          .put("fork_choice_compliance/block_cover_test", new ForkChoiceTestExecutor())
          .put("fork_choice_compliance/shuffling_test", new ForkChoiceTestExecutor())
          .build();

  public static final ImmutableMap<String, TestExecutor> FAST_CONFIRMATION_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("fast_confirmation/basic", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/current_epoch", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/empty_slots", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/ffg", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/is_one_confirmed", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/previous_epoch", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/reconfirmation", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/restart_gu", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/revert_finality", new ForkChoiceTestExecutor(true))
          .put("fast_confirmation/variables", new ForkChoiceTestExecutor(true))
          .build();

  private final List<?> testsToSkip;
  private final boolean fastConfirmationEnabled;

  public ForkChoiceTestExecutor(final String... testsToSkip) {
    this(false, testsToSkip);
  }

  public ForkChoiceTestExecutor(
      final boolean fastConfirmationEnabled, final String... testsToSkip) {
    this.fastConfirmationEnabled = fastConfirmationEnabled;
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(new StubMetricsSystem()));

    // Load `meta.yaml` and read the BLS setting
    final ForkChoiceMetaData metaData = getMetaData(testDefinition);
    final boolean blsDisabled = metaData.getBlsSetting() == IGNORED;
    final Spec spec = testDefinition.getSpec(!blsDisabled);
    final BeaconState anchorState =
        TestDataUtils.loadStateFromSsz(testDefinition, "anchor_state" + SSZ_SNAPPY_EXTENSION);
    final SignedBeaconBlock anchorBlock = loadAnchorBlock(testDefinition);

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    final RecentChainData recentChainData = storageSystem.recentChainData();
    final AnchorPoint anchorPoint =
        AnchorPoint.fromInitialBlockAndState(
            spec, new SignedBlockAndState(anchorBlock, anchorState));
    recentChainData.initializeFromAnchorPoint(
        anchorPoint, spec.computeTimeAtSlot(anchorBlock.getSlot(), anchorState.getGenesisTime()));
    final AnchorInfo anchorInfo =
        new AnchorInfo(
            anchorBlock.getRoot(), anchorPoint.getExecutionBlockHash().orElse(Bytes32.ZERO));

    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData);
    final InlineEventThread eventThread = new InlineEventThread();
    final KZG kzg = KzgRetriever.getKzgWithLoadedTrustedSetup(spec, testDefinition.getConfigName());
    final StubBlobSidecarManager blobSidecarManager = new StubBlobSidecarManager(kzg);
    final CurrentSlotProvider currentSlotProvider =
        CurrentSlotProvider.create(spec, recentChainData.getStore());
    final DasSamplerBasicImpl dasSampler =
        new DasSamplerBasicImpl(
            spec,
            asyncRunnerFactory.create("das", 1),
            currentSlotProvider,
            RPCFetchDelayProvider.NO_DELAY,
            DataColumnSidecarRecoveringCustody.NOOP,
            new DataColumnSidecarRetrieverStub(),
            // using a const for the custody group count here, the test doesn't care
            // and fetching from the config would break when not in fulu
            DasCustodyStand.createCustodyGroupCountManager(4, 8),
            recentChainData,
            false,
            new StubMetricsSystem(),
            64,
            (block, level, origin) -> new SafeFuture<>());
    final StubDataColumnSidecarManager dataColumnSidecarManager =
        new StubDataColumnSidecarManager(spec, recentChainData, dasSampler);
    spec.reinitializeForTesting(blobSidecarManager, dataColumnSidecarManager, kzg);
    final AsyncBLSSignatureVerifier signatureVerifier =
        AsyncBLSSignatureVerifier.wrap(
            blsDisabled ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE);
    // Run the fast confirmation work synchronously so the confirmation store is up to date by the
    // time the checks step reads it.
    //
    // The tracker is driven explicitly from the checks steps (see runSteps) rather than by
    // ForkChoice.onTick, so the runner reproduces the call sequence the vectors were generated
    // with: the spec test harness calls on_fast_confirmation itself, and while it almost always
    // does so exactly once per slot right after the tick, a few vectors call it twice within a slot
    // or skip a slot entirely. Driving it from onTick would then apply a different number of
    // rotations. ForkChoice therefore gets the NOOP tracker here; its once-per-slot production
    // scheduling is covered by ForkChoiceFastConfirmationTest and FastConfirmationTrackerTest.
    final FastConfirmationTracker fastConfirmationTracker =
        fastConfirmationEnabled
            ? FastConfirmationTracker.create(
                spec,
                Optional.of(SyncAsyncRunner.SYNC_RUNNER),
                FastConfirmationEventChannel.NOOP,
                new StubMetricsSystem(),
                StubTimeProvider.withTimeInMillis(0))
            : FastConfirmationTracker.NOOP;
    fastConfirmationTracker.initialize(recentChainData.getStore());
    // Mirrors BeaconChainController#initForkChoiceStateProvider: with fast confirmation enabled the
    // FCU safe_block_hash comes from the confirmed root (get_safe_execution_block_hash), otherwise
    // the supplier is empty and the safe hash defaults to the justified block.
    final ForkChoiceStateProvider forkChoiceStateProvider =
        new ForkChoiceStateProvider(
            eventThread,
            recentChainData,
            () ->
                fastConfirmationTracker
                    .getFastConfirmationStore()
                    .map(FastConfirmationStore::confirmedRoot));
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            new NoopForkChoiceNotifier(),
            forkChoiceStateProvider,
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            // NOOP so onTick does not also run the update once per slot: the checks steps are the
            // single driver, and a double rotation per slot would desync the FCR variables.
            FastConfirmationTracker.NOOP,
            // forkChoiceLateBlockReorgEnabled is true here always because this is the reference
            // test executor
            true,
            LateBlockReorgPreparationHandler.NOOP,
            DebugDataDumper.NOOP,
            storageSystem.getMetricsSystem(),
            signatureVerifier);
    final ExecutionLayerChannelStub executionLayer = new ExecutionLayerChannelStub(spec, false);
    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
    final Set<Bytes32> blockRootsWithInvalidExecutionPayload = new HashSet<>();
    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
    final Optional<AttestationValidator> maybeAttestationValidator =
        testDefinition.getTestType().equals("fork_choice/on_attestation")
            ? Optional.of(
                new AttestationValidator(
                    spec,
                    signatureVerifier,
                    gossipValidationHelper,
                    invalidBlockRoots,
                    blockRootsWithInvalidExecutionPayload))
            : Optional.empty();

    try {
      runSteps(
          testDefinition,
          spec,
          recentChainData,
          blobSidecarManager,
          dataColumnSidecarManager,
          forkChoice,
          fastConfirmationTracker,
          forkChoiceStateProvider,
          anchorInfo,
          executionLayer,
          maybeAttestationValidator,
          gossipValidationHelper,
          invalidBlockRoots,
          blockRootsWithInvalidExecutionPayload);
    } catch (final AssertionError e) {
      final String protoArrayData =
          recentChainData.getForkChoiceStrategy().orElseThrow().getBlockData().stream()
              .map(Object::toString)
              .collect(Collectors.joining("\n"));
      throw new AssertionError(
          e.getMessage()
              + "\n-------"
              + "\nJustified checkpoint: "
              + recentChainData.getJustifiedCheckpoint().orElse(null)
              + "\nFinalized checkpoint: "
              + recentChainData.getFinalizedCheckpoint().orElse(null)
              + "\nProtoarray data:\n"
              + protoArrayData,
          e);
    }
  }

  /**
   * The anchor block is currently always a Phase 0 block because of the way the specs repo are
   * doing Altair genesis. See https://github.com/ethereum/consensus-specs/pull/2323
   *
   * @param testDefinition the test definition
   * @return the anchor block for the test
   */
  private SignedBeaconBlock loadAnchorBlock(final TestDefinition testDefinition) {
    final BeaconBlock anchorBlock =
        TestDataUtils.loadSsz(
            testDefinition,
            "anchor_block" + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec()::deserializeBeaconBlock);
    return SignedBeaconBlock.create(testDefinition.getSpec(), anchorBlock, BLSSignature.empty());
  }

  private void runSteps(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final StubBlobSidecarManager blobSidecarManager,
      final StubDataColumnSidecarManager dataColumnSidecarManager,
      final ForkChoice forkChoice,
      final FastConfirmationTracker fastConfirmationTracker,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final AnchorInfo anchorInfo,
      final ExecutionLayerChannelStub executionLayer,
      final Optional<AttestationValidator> maybeAttestationValidator,
      final GossipValidationHelper gossipValidationHelper,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload)
      throws IOException {
    final List<Map<String, Object>> steps = loadSteps(testDefinition);
    for (Map<String, Object> step : steps) {
      LOG.info("Executing step {}", step);
      if (step.containsKey("checks")) {
        applyChecks(
            recentChainData,
            forkChoice,
            fastConfirmationTracker,
            forkChoiceStateProvider,
            anchorInfo,
            step);

      } else if (step.containsKey("tick")) {
        forkChoice.onTick(secondsToMillis(getUInt64(step, "tick")), Optional.empty());
        final UInt64 currentSlot = recentChainData.getCurrentSlot().orElse(UInt64.ZERO);
        LOG.info("Current slot: {} Epoch: {}", currentSlot, spec.computeEpochAtSlot(currentSlot));
      } else if (step.containsKey("block")) {
        applyBlock(
            testDefinition,
            spec,
            recentChainData,
            blobSidecarManager,
            dataColumnSidecarManager,
            forkChoice,
            step,
            executionLayer,
            invalidBlockRoots);

      } else if (step.containsKey("attestation")) {
        applyAttestation(
            testDefinition,
            forkChoice,
            step,
            maybeAttestationValidator,
            gossipValidationHelper,
            blockRootsWithInvalidExecutionPayload);

      } else if (step.containsKey("pow_block")) {
        applyPowBlock(testDefinition, step, executionLayer);

      } else if (step.containsKey("attester_slashing")) {
        applyAttesterSlashing(testDefinition, forkChoice, step);

      } else if (step.containsKey("block_hash")) {
        applyPosBlock(step, executionLayer);

      } else if (step.containsKey("payload_attestation")
          || step.containsKey("payload_attestation_message")) {
        applyPayloadAttestation(testDefinition, spec, recentChainData, forkChoice, step);

      } else if (step.containsKey("execution_payload")) {
        applyExecutionPayloadEnvelope(
            testDefinition,
            forkChoice,
            executionLayer,
            step,
            blockRootsWithInvalidExecutionPayload);

      } else {
        throw new UnsupportedOperationException("Unsupported step: " + step);
      }
    }
  }

  private void applyPowBlock(
      final TestDefinition testDefinition,
      final Map<String, Object> step,
      final ExecutionLayerChannelStub executionLayer) {
    final String filename = (String) step.get("pow_block");
    final PowBlock block =
        TestDataUtils.loadSsz(testDefinition, filename + SSZ_SNAPPY_EXTENSION, this::parsePowBlock);
    executionLayer.addPowBlock(block);
  }

  private PowBlock parsePowBlock(final Bytes data) {
    return SSZ.decode(
        data,
        reader -> {
          final Bytes32 blockHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 parentHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          // We don't get a timestamp but as long as it's in the past that's fine
          final UInt64 timestamp = UInt64.ZERO;
          return new PowBlock(blockHash, parentHash, timestamp);
        });
  }

  private void applyPosBlock(
      final Map<String, Object> step, final ExecutionLayerChannelStub executionLayer) {
    final Bytes32 blockHash = getBytes32(step, "block_hash");
    final Map<String, Object> payloadStatus = get(step, "payload_status");
    final PayloadStatus parsePayloadStatus = parsePayloadStatus(payloadStatus);

    executionLayer.addPosBlock(blockHash, parsePayloadStatus);
  }

  private PayloadStatus parsePayloadStatus(final Map<String, Object> payloadStatus) {
    final ExecutionPayloadStatus status =
        ExecutionPayloadStatus.valueOf(get(payloadStatus, "status"));
    final Optional<Bytes32> latestValidHash =
        getOptionallyBytes32(payloadStatus, "latest_valid_hash");
    final Optional<String> validationError = getOptionally(payloadStatus, "validation_error");

    return PayloadStatus.create(status, latestValidHash, validationError);
  }

  private void applyAttestation(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step,
      final Optional<AttestationValidator> maybeAttestationValidator,
      final GossipValidationHelper gossipValidationHelper,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload) {
    final String attestationName = get(step, "attestation");
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final Attestation attestation =
        TestDataUtils.loadSsz(
            testDefinition,
            attestationName + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttestationSchema());
    final Spec spec = testDefinition.getSpec();
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestation);
    if (maybeAttestationValidator.isPresent()) {
      final SafeFuture<InternalValidationResult> validationResult =
          maybeAttestationValidator
              .get()
              .validateSingleOrAggregateAttestation(validatableAttestation);
      assertThat(validationResult).isCompleted();
      final InternalValidationResult internalValidationResult = safeJoin(validationResult);
      // Reject/ignore gossip validation results do not reach fork choice in production.
      if (internalValidationResult.isNotProcessable()) {
        assertThat(valid)
            .withFailMessage(
                "Expected attestation to be valid but gossip validation returned %s",
                internalValidationResult)
            .isFalse();
        return;
      }
      // SAVE_FOR_FUTURE normally still reaches fork choice. Re-check just the shared payload-status
      // validation to identify Gloas payload-status failures that should stop before fork choice.
      if (internalValidationResult.isSaveForFuture()) {
        final AttestationUtil attestationUtil =
            spec.atSlot(attestation.getData().getSlot()).getAttestationUtil();
        final InternalValidationResult payloadStatusValidationResult =
            gossipValidationHelper.validatePayloadStatus(
                attestationUtil, attestation.getData(), blockRootsWithInvalidExecutionPayload);
        // A payload-status failure means the fixture's attestation is invalid before fork choice.
        if (!payloadStatusValidationResult.isAccept()) {
          assertThat(valid)
              .withFailMessage(
                  "Expected attestation to be valid but payload status validation returned %s",
                  payloadStatusValidationResult)
              .isFalse();
          return;
        }
      }
    }
    final SafeFuture<AttestationProcessingResult> result =
        forkChoice.onAttestation(validatableAttestation);
    assertThat(result).isCompleted();
    final AttestationProcessingResult processingResult = safeJoin(result);
    // If a current-slot attestation is valid but deferred by fork choice (stored and applied on the
    // next tick), reference tests seem to expect it to be considered invalid, so we will not
    // consider
    // it a successful attestation
    assertThat(processingResult.isSuccessful())
        .withFailMessage("%s failed with processing result: %s", attestationName, processingResult)
        .isEqualTo(valid);
  }

  private void applyAttesterSlashing(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String slashingName = get(step, "attester_slashing");
    final AttesterSlashing attesterSlashing =
        TestDataUtils.loadSsz(
            testDefinition,
            slashingName + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttesterSlashingSchema());
    assertDoesNotThrow(
        () ->
            forkChoice.onAttesterSlashing(attesterSlashing, InternalValidationResult.ACCEPT, true));
  }

  private void applyPayloadAttestation(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String payloadAttestationName =
        ForkChoiceTestExecutor.<String>getOptionally(step, "payload_attestation")
            .orElseGet(
                () -> ForkChoiceTestExecutor.<String>get(step, "payload_attestation_message"));
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final PayloadAttestationMessage payloadAttestationMessage =
        TestDataUtils.loadSsz(
            testDefinition,
            payloadAttestationName + SSZ_SNAPPY_EXTENSION,
            SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
                .getPayloadAttestationMessageSchema());
    final ValidatablePayloadAttestationMessage validatablePayloadAttestationMessage =
        ValidatablePayloadAttestationMessage.fromNetwork(payloadAttestationMessage);
    if (valid) {
      final BeaconState state =
          safeJoin(
                  recentChainData.retrieveBlockState(
                      new SlotAndBlockRoot(
                          payloadAttestationMessage.getData().getSlot(),
                          payloadAttestationMessage.getData().getBeaconBlockRoot())))
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "State is unavailable for payload attestation slot "
                              + payloadAttestationMessage.getData().getSlot()
                              + " and block root "
                              + payloadAttestationMessage.getData().getBeaconBlockRoot()));
      validatablePayloadAttestationMessage.calculatePtcPositions(spec, state);
    }
    assertDoesNotThrow(
        () ->
            forkChoice.onPayloadAttestationMessage(
                validatablePayloadAttestationMessage,
                valid ? InternalValidationResult.ACCEPT : InternalValidationResult.IGNORE,
                true));
  }

  private void applyExecutionPayloadEnvelope(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final ExecutionLayerChannelStub executionLayer,
      final Map<String, Object> step,
      final Set<Bytes32> blockRootsWithInvalidExecutionPayload) {
    final String envelopeName = get(step, "execution_payload");
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final SignedExecutionPayloadEnvelope envelope =
        TestDataUtils.loadSsz(
            testDefinition,
            envelopeName + SSZ_SNAPPY_EXTENSION,
            SchemaDefinitionsGloas.required(testDefinition.getSpec().getGenesisSchemaDefinitions())
                .getSignedExecutionPayloadEnvelopeSchema());
    final SafeFuture<ExecutionPayloadImportResult> result =
        forkChoice.onExecutionPayloadEnvelope(envelope, executionLayer);
    assertThat(result).isCompleted();
    final ExecutionPayloadImportResult importResult = safeJoin(result);
    if (!importResult.isSuccessful() && isInvalidExecutionPayload(importResult)) {
      blockRootsWithInvalidExecutionPayload.add(envelope.getBeaconBlockRoot());
    }
    assertThat(importResult.isSuccessful())
        .withFailMessage(
            "Expected valid=%s but got isSuccessful=%s (reason: %s)",
            valid, importResult.isSuccessful(), importResult.getFailureReason())
        .isEqualTo(valid);
  }

  private void applyBlock(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final StubBlobSidecarManager blobSidecarManager,
      final StubDataColumnSidecarManager dataColumnSidecarManager,
      final ForkChoice forkChoice,
      final Map<String, Object> step,
      final ExecutionLayerChannelStub executionLayer,
      final Map<Bytes32, BlockImportResult> invalidBlockRoots) {
    final String blockName = get(step, "block");
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final SignedBeaconBlock block =
        TestDataUtils.loadSsz(
            testDefinition, blockName + SSZ_SNAPPY_EXTENSION, spec::deserializeSignedBeaconBlock);
    final List<Blob> blobs =
        getOptionally(step, "blobs")
            .map(
                blobsName ->
                    TestDataUtils.loadSsz(
                            testDefinition,
                            blobsName + SSZ_SNAPPY_EXTENSION,
                            sszBytes -> spec.deserializeBlobsInBlock(sszBytes, block.getSlot()))
                        .asList())
            .orElse(Collections.emptyList());
    @SuppressWarnings("unchecked")
    final List<KZGProof> proofs =
        getOptionally(step, "proofs")
            .map(
                proofsArray ->
                    ((List<String>) proofsArray).stream().map(KZGProof::fromHexString).toList())
            .orElse(Collections.emptyList());
    @SuppressWarnings("unchecked")
    final List<DataColumnSidecar> columns =
        getOptionally(step, "columns")
            .map(
                columnsNameArray ->
                    ((List<String>) columnsNameArray)
                        .stream()
                            .map(
                                columnsName ->
                                    TestDataUtils.loadSsz(
                                        testDefinition,
                                        columnsName + SSZ_SNAPPY_EXTENSION,
                                        sszBytes ->
                                            spec.deserializeSidecar(sszBytes, block.getSlot())))
                            .toList())
            .orElse(Collections.emptyList());
    if (spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      LOG.info("Adding {} columns to custody for block {}", columns.size(), block.getRoot());
      dataColumnSidecarManager.prepareDataColumnSidecarForBlock(block, columns);

    } else if (spec.atSlot(block.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.DENEB)) {
      LOG.info(
          "Preparing {} blobs with proofs {} for block {}", blobs.size(), proofs, block.getRoot());
      blobSidecarManager.prepareBlobsAndProofsForBlock(block, blobs, proofs);
    }
    LOG.info(
        "Importing block {} at slot {} with parent {}",
        block.getRoot(),
        block.getSlot(),
        block.getParentRoot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer);
    assertThat(result).isCompleted();
    final BlockImportResult importResult = safeJoin(result);
    if (!importResult.isSuccessful()) {
      invalidBlockRoots.put(block.getRoot(), importResult);
    }
    assertThat(importResult)
        .describedAs("Incorrect block import result for block %s", block)
        .has(new Condition<>(r -> r.isSuccessful() == valid, "isSuccessful matching " + valid));

    if (valid
        && spec.computeEpochAtSlot(block.getSlot())
            .plus(1)
            .isLessThan(recentChainData.getCurrentEpoch().orElseThrow())) {
      applyAttestationWeight(spec, recentChainData, block);
    }
  }

  private static void applyAttestationWeight(
      final Spec spec, final RecentChainData recentChainData, final SignedBeaconBlock block) {
    // Apply attestations from block that we may have skipped because they're old
    // The spec tests currently require this but it doesn't make sense to incur this cost during
    // a sync where the attestation weighting is almost certainly useless.
    final BeaconState preState =
        safeJoin(
                recentChainData.retrieveBlockState(
                    new SlotAndBlockRoot(block.getSlot(), block.getParentRoot())))
            .orElseThrow();
    final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
    final ForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getUpdatableForkChoiceStrategy().orElseThrow();
    block
        .getMessage()
        .getBody()
        .getAttestations()
        .forEach(
            attestation ->
                forkChoiceStrategy.onAttestation(
                    voteUpdater,
                    spec.atSlot(block.getSlot())
                        .getAttestationUtil()
                        .getIndexedAttestation(preState, attestation)));
    voteUpdater.commit();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> loadSteps(final TestDefinition testDefinition)
      throws IOException {
    return TestDataUtils.loadYaml(testDefinition, "steps.yaml", List.class);
  }

  private void applyChecks(
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final FastConfirmationTracker fastConfirmationTracker,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final AnchorInfo anchorInfo,
      final Map<String, Object> step) {
    assertThat(forkChoice.processHead()).isCompleted();
    final UpdatableStore store = recentChainData.getStore();
    final Map<String, Object> checks = get(step, "checks");
    // Every checks step carrying fast confirmation state was emitted by the spec test harness
    // directly after an on_fast_confirmation call, so run the handler here against the head that
    // processHead just selected.
    if (fastConfirmationTracker.isEnabled() && checks.containsKey("confirmed_root")) {
      final SafeFuture<Void> fastConfirmation =
          fastConfirmationTracker.onFastConfirmation(
              recentChainData.getCurrentSlot().orElseThrow(),
              recentChainData.getBestBlockRoot().orElseThrow());
      assertThat(fastConfirmation).isCompleted();
    }
    final List<AssertionError> failures = new ArrayList<>();
    for (String checkType : checks.keySet()) {
      try {
        switch (checkType) {
          case "genesis_time" ->
              assertThat(recentChainData.getGenesisTime()).isEqualTo(getUInt64(checks, checkType));

          case "head" -> {
            final Map<String, Object> expectedHead = get(checks, checkType);
            final UInt64 expectedSlot = UInt64.valueOf(expectedHead.get("slot").toString());
            final Bytes32 expectedRoot = Bytes32.fromHexString(expectedHead.get("root").toString());
            assertThat(recentChainData.getHeadSlot())
                .describedAs("best block slot")
                .isEqualTo(expectedSlot);
            assertThat(recentChainData.getBestBlockRoot())
                .describedAs("best block root")
                .contains(expectedRoot);
          }

          case "time" -> {
            final UInt64 expectedTime = getUInt64(checks, checkType);
            assertThat(store.getTimeSeconds()).describedAs("time").isEqualTo(expectedTime);
          }

          case "justified_checkpoint_root" -> {
            final Bytes32 expectedJustifiedRoot = getBytes32(checks, checkType);
            assertThat(store.getJustifiedCheckpoint().getRoot())
                .describedAs("justified checkpoint")
                .isEqualTo(expectedJustifiedRoot);
          }

          case "justified_checkpoint" ->
              assertCheckpoint(
                  "justified checkpoint", store.getJustifiedCheckpoint(), get(checks, checkType));

          case "best_justified_checkpoint" ->
              assertCheckpoint(
                  "best justified checkpoint",
                  store.getBestJustifiedCheckpoint(),
                  get(checks, checkType));

          case "finalized_checkpoint_root" -> {
            final Bytes32 expectedFinalizedRoot = getBytes32(checks, checkType);
            assertThat(store.getFinalizedCheckpoint().getRoot())
                .describedAs("finalized checkpoint")
                .isEqualTo(expectedFinalizedRoot);
          }

          case "finalized_checkpoint" ->
              assertCheckpoint(
                  "finalized checkpoint", store.getFinalizedCheckpoint(), get(checks, checkType));

          case "proposer_boost_root" -> {
            final Optional<Bytes32> boostedRoot = store.getProposerBoostRoot();
            final Bytes32 expectedBoostedRoot = getBytes32(checks, checkType);
            if (expectedBoostedRoot.isZero()) {
              assertThat(boostedRoot).describedAs("proposer_boost_root").isEmpty();
            } else {
              assertThat(boostedRoot)
                  .describedAs("proposer_boost_root")
                  .contains(expectedBoostedRoot);
            }
          }

          case "get_proposer_head" -> {
            final Bytes32 expectedProposerHead = getBytes32(checks, checkType);
            final ForkChoiceNode proposerHead =
                recentChainData.getProposerHead(
                    ForkChoiceNode.createBase(expectedProposerHead),
                    recentChainData.getHeadSlot().increment());
            assertThat(proposerHead.blockRoot())
                .describedAs("get_proposer_head")
                .isEqualTo(expectedProposerHead);
          }

          case "viable_for_head_roots_and_weights" -> {
            final List<Map<String, Object>> viableHeadRootsAndWeightsData = get(checks, checkType);

            final Set<HeadRootAndWeight> viableHeadRootsAndWeights =
                viableHeadRootsAndWeightsData.stream()
                    .map(
                        entry ->
                            new HeadRootAndWeight(
                                Bytes32.fromHexString((String) entry.get("root")),
                                UInt64.valueOf(entry.get("weight").toString()),
                                Optional.ofNullable((Integer) entry.get("payload_status"))
                                    .map(this::convertToPayloadStatus)))
                    .collect(Collectors.toSet());
            final Set<HeadRootAndWeight> chainHeadRootsAndWeights =
                recentChainData
                    .getForkChoiceStrategy()
                    .map(ReadOnlyForkChoiceStrategy::getChainHeads)
                    .orElse(Collections.emptyList())
                    .stream()
                    .map(
                        protoNodeData ->
                            new HeadRootAndWeight(
                                protoNodeData.getRoot(),
                                protoNodeData.getWeight(),
                                Optional.ofNullable(protoNodeData.getPayloadStatus())))
                    .collect(Collectors.toSet());

            for (HeadRootAndWeight headRootAndWeight : viableHeadRootsAndWeights) {
              boolean notPresent =
                  chainHeadRootsAndWeights.stream()
                      .noneMatch(
                          (chainHeadRootAndWeight) -> {
                            if (headRootAndWeight.root.equals(chainHeadRootAndWeight.root)
                                && headRootAndWeight.weight.equals(chainHeadRootAndWeight.weight)) {
                              // an unset payload status means we don't need to check if payload
                              // status is correct
                              if (headRootAndWeight.payloadStatus.isPresent()) {
                                return headRootAndWeight.payloadStatus.equals(
                                    chainHeadRootAndWeight.payloadStatus);
                              } else {
                                return true;
                              }
                            }
                            return false;
                          });
              Assertions.assertFalse(
                  notPresent,
                  String.format(
                      "Unable to find %s in %s", headRootAndWeight, chainHeadRootsAndWeights));
            }
          }

          case "head_payload_status" -> {
            final int expectedValue = ((Number) checks.get(checkType)).intValue();
            final ForkChoicePayloadStatus headStatus =
                recentChainData.getChainHead().map(ChainHead::getPayloadStatus).orElseThrow();
            assertThat(headStatus.getValue())
                .describedAs("head_payload_status")
                .isEqualTo(expectedValue);
          }

          case "payload_timeliness_vote" ->
              assertPayloadVote(
                  recentChainData,
                  checkType,
                  get(checks, checkType),
                  ReadOnlyForkChoiceStrategy::getPayloadTimelinessVote);

          case "payload_data_availability_vote" ->
              assertPayloadVote(
                  recentChainData,
                  checkType,
                  get(checks, checkType),
                  ReadOnlyForkChoiceStrategy::getPayloadDataAvailabilityVote);

          case "previous_epoch_observed_justified_checkpoint" ->
              assertCheckpoint(
                  checkType,
                  fastConfirmationStore(fastConfirmationTracker)
                      .previousEpochObservedJustifiedCheckpoint(),
                  get(checks, checkType));

          case "current_epoch_observed_justified_checkpoint" ->
              assertCheckpoint(
                  checkType,
                  fastConfirmationStore(fastConfirmationTracker)
                      .currentEpochObservedJustifiedCheckpoint(),
                  get(checks, checkType));

          case "previous_epoch_greatest_unrealized_checkpoint" ->
              assertCheckpoint(
                  checkType,
                  fastConfirmationStore(fastConfirmationTracker)
                      .previousEpochGreatestUnrealizedCheckpoint(),
                  get(checks, checkType));

          case "previous_slot_head" ->
              assertThat(fastConfirmationStore(fastConfirmationTracker).previousSlotHead())
                  .describedAs(checkType)
                  .isEqualTo(getBytes32(checks, checkType));

          case "current_slot_head" ->
              assertThat(fastConfirmationStore(fastConfirmationTracker).currentSlotHead())
                  .describedAs(checkType)
                  .isEqualTo(getBytes32(checks, checkType));

          case "confirmed_root" ->
              assertThat(fastConfirmationStore(fastConfirmationTracker).confirmedRoot())
                  .describedAs(checkType)
                  .isEqualTo(getBytes32(checks, checkType));

          case "safe_execution_block_hash" ->
              assertSafeExecutionBlockHash(
                  checkType,
                  store,
                  fastConfirmationStore(fastConfirmationTracker).confirmedRoot(),
                  anchorInfo,
                  forkChoiceStateProvider,
                  getBytes32(checks, checkType));

          default ->
              throw new UnsupportedOperationException("Unsupported check type: " + checkType);
        }
      } catch (final AssertionError failure) {
        failures.add(failure);
      }
    }

    if (!failures.isEmpty()) {
      final AssertionError firstError = failures.get(0);
      for (int i = 1; i < failures.size(); i++) {
        firstError.addSuppressed(failures.get(i));
      }
      throw firstError;
    }
  }

  /**
   * Asserts {@code get_safe_execution_block_hash(fcr_store)}, which the spec defines purely in
   * terms of the confirmed block's body, and then ties that value to the {@code safe_block_hash}
   * Teku actually puts in the fcU.
   *
   * <p>The two are read from different places: the spec reads the block body, while Teku's fcU
   * value comes from the confirmed block's BASE node in protoarray. For every imported block those
   * agree, because the node's execution hash is taken from the block body. They diverge only for a
   * pre-Gloas anchor, whose body holds a default, all-zero payload while its node is seeded from
   * the anchor <em>state</em>'s {@code latest_execution_payload_header} (see {@code
   * StateAndBlockSummary#getExecutionBlockHash}) and so carries the real EL genesis hash. Teku's
   * choice there is deliberate and load-bearing: protoarray uses a zero execution hash as the
   * "pre-merge" marker (see {@code ProtoArray#findOptimisticallySyncedMergeTransitionBlock}), so
   * zeroing the genesis node on a merged-at-genesis network would make the first block after
   * genesis look like the merge transition block.
   *
   * <p>So the spec value is always asserted against the vector, and the fcU value is always
   * asserted too — against the spec value in the general case, and against the anchor's seed in
   * that one divergent case. No check is skipped and no value is left unasserted.
   */
  private void assertSafeExecutionBlockHash(
      final String checkType,
      final ReadOnlyStore store,
      final Bytes32 confirmedRoot,
      final AnchorInfo anchorInfo,
      final ForkChoiceStateProvider forkChoiceStateProvider,
      final Bytes32 expected) {
    final Bytes32 specValue = confirmedBlockExecutionBlockHash(store, confirmedRoot);
    assertThat(specValue).describedAs(checkType).isEqualTo(expected);

    // Teku's fcU value is read from the confirmed block's fork choice node, which for an imported
    // block carries the same hash the spec reads out of the body. The one case where there is no
    // body payload to carry is the pre-Gloas anchor, whose block body holds a default, all-zero
    // payload while its node is seeded from the anchor state; there the fcU value is pinned to that
    // seed. Under Gloas the anchor's body does carry a bid, so it takes the normal branch. Both
    // branches assert, so a change to either source fails here rather than going unnoticed.
    final boolean isAnchorWithoutPayloadInBody =
        confirmedRoot.equals(anchorInfo.blockRoot()) && specValue.isZero();
    final Bytes32 expectedSafeBlockHash =
        isAnchorWithoutPayloadInBody ? anchorInfo.executionBlockHash() : specValue;
    assertThat(safeExecutionBlockHash(forkChoiceStateProvider))
        .describedAs(checkType + " (fcU safe_block_hash)")
        .isEqualTo(expectedSafeBlockHash);
  }

  /**
   * The anchor's block root and the execution block hash Teku seeds its fork choice node with,
   * taken from the anchor state rather than the anchor block body.
   */
  private record AnchorInfo(Bytes32 blockRoot, Bytes32 executionBlockHash) {}

  /**
   * Implements {@code get_safe_execution_block_hash} as specified: the block hash of the payload
   * carried by the confirmed block's body, which under Gloas becomes the parent block hash from its
   * execution payload bid.
   */
  private Bytes32 confirmedBlockExecutionBlockHash(
      final ReadOnlyStore store, final Bytes32 confirmedRoot) {
    final SafeFuture<Optional<BeaconBlock>> block = store.retrieveBlock(confirmedRoot);
    assertThat(block).isCompleted();
    final BeaconBlockBody body =
        safeJoin(block)
            .orElseThrow(
                () -> new IllegalStateException("Confirmed block " + confirmedRoot + " not found"))
            .getBody();
    return body.getOptionalExecutionPayload()
        .map(ExecutionPayload::getBlockHash)
        // >= Gloas
        .or(
            () ->
                body.getOptionalSignedExecutionPayloadBid()
                    .map(bid -> bid.getMessage().getParentBlockHash()))
        .orElse(Bytes32.ZERO);
  }

  private Bytes32 safeExecutionBlockHash(final ForkChoiceStateProvider forkChoiceStateProvider) {
    final SafeFuture<ForkChoiceState> forkChoiceState =
        forkChoiceStateProvider.getForkChoiceStateAsync();
    assertThat(forkChoiceState).isCompleted();
    return safeJoin(forkChoiceState).safeExecutionBlockHash();
  }

  private FastConfirmationStore fastConfirmationStore(final FastConfirmationTracker tracker) {
    return tracker
        .getFastConfirmationStore()
        .orElseThrow(() -> new IllegalStateException("Fast confirmation store is not available"));
  }

  private void assertCheckpoint(
      final String checkpointType,
      final Checkpoint actual,
      final Map<String, Object> expectedCheckpoint) {
    final Bytes32 expectedRoot = getBytes32(expectedCheckpoint, "root");
    final UInt64 expectedEpoch = getUInt64(expectedCheckpoint, "epoch");
    assertThat(actual)
        .describedAs(checkpointType)
        .isEqualTo(new Checkpoint(expectedEpoch, expectedRoot));
  }

  private void assertPayloadVote(
      final RecentChainData recentChainData,
      final String checkType,
      final Map<String, Object> expectedPayloadVote,
      final PayloadVoteGetter payloadVoteGetter) {
    final Bytes32 blockRoot = getBytes32(expectedPayloadVote, "block_root");
    final List<Boolean> expectedVotes = get(expectedPayloadVote, "votes");
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final List<Boolean> actualVotes = new ArrayList<>();
    for (int ptcPosition = 0; ptcPosition < expectedVotes.size(); ptcPosition++) {
      actualVotes.add(
          payloadVoteGetter.get(forkChoiceStrategy, blockRoot, ptcPosition).orElse(null));
    }
    assertThat(actualVotes).describedAs(checkType).isEqualTo(expectedVotes);
  }

  @FunctionalInterface
  private interface PayloadVoteGetter {
    Optional<Boolean> get(
        ReadOnlyForkChoiceStrategy forkChoiceStrategy, Bytes32 blockRoot, int ptcPosition);
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> T get(final Map<String, Object> yamlData, final String key) {
    return (T) yamlData.get(key);
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> Optional<T> getOptionally(
      final Map<String, Object> yamlData, final String key) {
    return Optional.ofNullable((T) yamlData.get(key));
  }

  private static UInt64 getUInt64(final Map<String, Object> yamlData, final String key) {
    return UInt64.valueOf(get(yamlData, key).toString());
  }

  private static Bytes32 getBytes32(final Map<String, Object> yamlData, final String key) {
    return Bytes32.fromHexString(get(yamlData, key));
  }

  private static Optional<Bytes32> getOptionallyBytes32(
      final Map<String, Object> yamlData, final String key) {
    return ForkChoiceTestExecutor.<String>getOptionally(yamlData, key).map(Bytes32::fromHexString);
  }

  private static boolean isInvalidExecutionPayload(final ExecutionPayloadImportResult result) {
    return switch (result.getFailureReason()) {
      case FAILED_VERIFICATION, FAILED_DATA_AVAILABILITY_CHECK_INVALID -> true;
      case UNKNOWN_BEACON_BLOCK_ROOT,
          FAILED_EXECUTION,
          FAILED_DATA_AVAILABILITY_CHECK_NOT_AVAILABLE,
          INTERNAL_ERROR ->
          false;
    };
  }

  private static ForkChoiceMetaData getMetaData(final TestDefinition testDefinition)
      throws IOException {
    final ForkChoiceMetaData metaData;
    final Path metaPath = testDefinition.getTestDirectory().resolve("meta.yaml");
    if (metaPath.toFile().exists()) {
      metaData = loadYaml(testDefinition, "meta.yaml", ForkChoiceMetaData.class);
    } else {
      metaData = ForkChoiceMetaData.DEFAULT;
    }

    return metaData;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class ForkChoiceMetaData {
    static final ForkChoiceMetaData DEFAULT = new ForkChoiceMetaData(0);

    private ForkChoiceMetaData(
        @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
            final int blsSetting) {
      this.blsSetting = blsSetting;
    }

    private final int blsSetting;

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }
  }

  private ForkChoicePayloadStatus convertToPayloadStatus(final int payloadStatus) {
    return Arrays.stream(ForkChoicePayloadStatus.values())
        .filter(fcps -> fcps.getValue() == payloadStatus)
        .findAny()
        .get();
  }

  private record HeadRootAndWeight(
      Bytes32 root, UInt64 weight, Optional<ForkChoicePayloadStatus> payloadStatus) {}
}
