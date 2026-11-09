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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFutureSupplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.BlsToExecutionChangeGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;

public class BlockImporterTest {
  private final AsyncRunner asyncRunner = mock(AsyncRunner.class);
  private final Spec spec =
      TestSpecFactory.createMinimalPhase0(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(8);
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher =
      mock(ReceivedBlockEventsChannel.class);
  private final RecentChainData recentChainData =
      MemoryOnlyRecentChainData.builder().specProvider(spec).build();
  private final WeakSubjectivityValidator weakSubjectivityValidator =
      mock(WeakSubjectivityValidator.class);
  private final ForkChoiceNotifier forkChoiceNotifier = new NoopForkChoiceNotifier();
  private final MergeTransitionBlockValidator transitionBlockValidator =
      new MergeTransitionBlockValidator(spec, recentChainData);

  private final MetricsSystem metricsSystem = new StubMetricsSystem();
  private final ForkChoice forkChoice =
      new ForkChoice(
          spec,
          new InlineEventThread(),
          recentChainData,
          forkChoiceNotifier,
          transitionBlockValidator,
          metricsSystem);
  private final ChainBuilder localChainBuilder = ChainBuilder.create(spec, validatorKeys);
  private final ChainUpdater localChainUpdater =
      new ChainUpdater(recentChainData, localChainBuilder, spec);

  private final RecentChainData otherStorage = MemoryOnlyRecentChainData.create();
  private final ChainBuilder otherChainBuilder = ChainBuilder.create(spec, validatorKeys);
  private final ChainUpdater otherChainUpdater =
      new ChainUpdater(otherStorage, otherChainBuilder, spec);

  private final BlockImporter blockImporter =
      new BlockImporter(
          asyncRunner,
          spec,
          receivedBlockEventsChannelPublisher,
          recentChainData,
          forkChoice,
          weakSubjectivityValidator,
          ExecutionLayerChannel.NOOP);

  @BeforeEach
  public void setup() {
    // prepare a synchronous async runner
    doAnswer(
            invocation -> {
              final ExceptionThrowingFutureSupplier<?> task = invocation.getArgument(0);
              return SafeFuture.completedFuture(SafeFuture.of(task.get()).join());
            })
        .when(asyncRunner)
        .runAsync((ExceptionThrowingFutureSupplier<?>) any());

    otherChainUpdater.initializeGenesis(false);
    localChainUpdater.initializeGenesis(false);
    when(weakSubjectivityValidator.isBlockValid(any(), any())).thenReturn(true);
  }

  @Test
  public void importBlock_success() throws Exception {
    final SignedBlockAndState block = otherChainBuilder.generateBlockAtSlot(UInt64.ONE);
    localChainUpdater.setCurrentSlot(UInt64.ONE);

    final BlockImportResult result = blockImporter.importBlock(block.getBlock()).get();
    assertWeakSubjectivityWasChecked();
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_errorDuringWeakSubjectivityCheck() throws Exception {
    final SignedBlockAndState block = otherChainBuilder.generateBlockAtSlot(UInt64.ONE);
    localChainUpdater.setCurrentSlot(UInt64.ONE);
    doThrow(new RuntimeException("oops"))
        .when(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(any(), any());

    final BlockImportResult result = blockImporter.importBlock(block.getBlock()).get();
    assertWeakSubjectivityWasChecked();
    assertImportFailed(result, FailureReason.INTERNAL_ERROR);
  }

  @Test
  public void importBlock_alreadyInChain() throws Exception {
    final SignedBlockAndState block = otherChainBuilder.generateBlockAtSlot(UInt64.ONE);
    localChainUpdater.setCurrentSlot(UInt64.ONE);

    assertThat(blockImporter.importBlock(block.getBlock()).get().isSuccessful()).isTrue();
    BlockImportResult result = blockImporter.importBlock(block.getBlock()).get();
    assertSuccessfulResult(result);
  }

  @Test
  public void importBlock_latestFinalizedBlock() throws Exception {
    final SignedBlockAndState lastBlockAndState =
        localChainUpdater.advanceChainUntil(genesisConfig.getSlotsPerEpoch());
    localChainUpdater.updateBestBlock(lastBlockAndState);

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 bestEpoch = spec.computeEpochAtSlot(recentChainData.getHeadSlot());
    assertThat(bestEpoch).isEqualTo(SpecConfig.GENESIS_EPOCH.plus(1));
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Known blocks should report as successfully imported
    final BlockImportResult result = blockImporter.importBlock(lastBlockAndState.getBlock()).get();
    assertSuccessfulResult(result, false);
  }

  @Test
  public void importBlock_knownBlockOlderThanLatestFinalized() throws Exception {
    UInt64 startingSlot = recentChainData.getHeadSlot();
    final SignedBlockAndState blockAndState =
        localChainUpdater.advanceChainUntil(genesisConfig.getSlotsPerEpoch());
    localChainUpdater.updateBestBlock(blockAndState);

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 bestRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 bestEpoch = spec.computeEpochAtSlot(recentChainData.getHeadSlot());
    assertThat(bestEpoch).isEqualTo(SpecConfig.GENESIS_EPOCH.plus(1));
    final Checkpoint finalized = new Checkpoint(bestEpoch, bestRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Import a block prior to the latest finalized block
    final BlockImportResult result =
        blockImporter.importBlock(localChainBuilder.getBlockAtSlot(startingSlot.plus(1))).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_parentBlockFromSameSlot() throws Exception {
    // First import a valid block at slot 1
    final SignedBlockAndState blockAndState = otherChainBuilder.generateBlockAtSlot(UInt64.ONE);
    localChainUpdater.setCurrentSlot(blockAndState.getSlot());
    assertSuccessfulResult(blockImporter.importBlock(blockAndState.getBlock()).get());

    // Now create an alternate block 1 with the real block one as the parent block
    final BeaconBlock invalidAncestryUnsignedBlock =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            blockAndState.getBlock().getSlot(),
            blockAndState.getBlock().getMessage().getProposerIndex(),
            blockAndState.getBlock().getMessage().hashTreeRoot(),
            blockAndState.getBlock().getMessage().getStateRoot(),
            blockAndState.getBlock().getMessage().getBody());
    final Signer signer =
        localChainBuilder.getSigner(
            blockAndState.getBlock().getMessage().getProposerIndex().intValue());
    final SignedBeaconBlock invalidAncestryBlock =
        SignedBeaconBlock.create(
            spec,
            invalidAncestryUnsignedBlock,
            signer
                .signBlock(
                    invalidAncestryUnsignedBlock, otherStorage.getCurrentForkInfo().orElseThrow())
                .join());

    final BlockImportResult result = blockImporter.importBlock(invalidAncestryBlock).get();
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason())
        .isEqualTo(BlockImportResult.FAILED_INVALID_ANCESTRY.getFailureReason());
  }

  @Test
  public void importBlock_fromFuture() throws Exception {
    final SignedBeaconBlock block = otherChainBuilder.generateBlockAtSlot(UInt64.ONE).getBlock();

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertImportFailed(result, FailureReason.BLOCK_IS_FROM_FUTURE);
  }

  @Test
  public void importBlock_unknownParent() throws Exception {
    final SignedBlockAndState blockAndState = otherChainUpdater.advanceChainUntil(2);
    localChainUpdater.setCurrentSlot(blockAndState.getSlot());

    final BlockImportResult result = blockImporter.importBlock(blockAndState.getBlock()).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_wrongChain() throws Exception {
    final SignedBlockAndState blockAndState =
        localChainUpdater.advanceChainUntil(recentChainData.getHeadSlot().plus(3));
    localChainUpdater.updateBestBlock(blockAndState);
    UInt64 localChainHeadSlot = blockAndState.getSlot();

    // Update finalized epoch
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    final Bytes32 finalizedRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final UInt64 finalizedEpoch = UInt64.ONE;
    final Checkpoint finalized = new Checkpoint(finalizedEpoch, finalizedRoot);
    tx.setFinalizedCheckpoint(finalized, false);
    tx.commit().join();

    // Now create a new block that is not descendant from the finalized block
    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    final StateAndBlockSummary stateAndBlockSummary =
        safeJoin(otherStorage.getChainHead().orElseThrow().asStateAndBlockSummary());
    final Attestation attestation = attestationGenerator.validAttestation(stateAndBlockSummary);
    final SignedBeaconBlock block =
        otherChainBuilder
            .generateBlockAtSlot(
                localChainHeadSlot, BlockOptions.create().addAttestation(attestation))
            .getBlock();

    final BlockImportResult result = blockImporter.importBlock(block).get();
    assertImportFailed(result, FailureReason.UNKNOWN_PARENT);
  }

  @Test
  public void importBlock_invalidStateTransition() throws Exception {
    final SignedBeaconBlock block = otherChainBuilder.generateBlockAtSlot(UInt64.ONE).getBlock();
    SignedBeaconBlock newBlock =
        SignedBeaconBlock.create(
            spec, block.getMessage().withStateRoot(Bytes32.ZERO), block.getSignature());
    localChainUpdater.setCurrentSlot(block.getSlot());

    final BlockImportResult result = blockImporter.importBlock(newBlock).get();
    assertImportFailed(result, FailureReason.FAILED_STATE_TRANSITION);
  }

  @Test
  public void importBlock_weakSubjectivityFailure_wrongAncestor() throws Exception {
    final UInt64 wsEpoch = UInt64.valueOf(10);
    final UInt64 wsEpochSlot = this.spec.computeStartSlotAtEpoch(wsEpoch);
    final SignedBlockAndState wsBlock = localChainBuilder.generateBlockAtSlot(wsEpochSlot);
    final SignedBlockAndState otherBlock =
        otherChainBuilder.generateBlockAtSlot(wsEpochSlot.plus(1));

    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final Checkpoint wsCheckpoint = new Checkpoint(wsEpoch, wsBlock.getRoot());
    final WeakSubjectivityConfig wsConfig =
        WeakSubjectivityConfig.builder()
            .specProvider(spec)
            .weakSubjectivityCheckpoint(wsCheckpoint)
            .build();
    final WeakSubjectivityValidator weakSubjectivityValidator =
        WeakSubjectivityValidator.lenient(wsConfig);
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final BlockImportResult result = blockImporter.importBlock(otherBlock.getBlock()).get();
    assertImportFailed(result, FailureReason.FAILED_WEAK_SUBJECTIVITY_CHECKS);
  }

  @Test
  public void importBlock_weakSubjectivityChecksPass() throws Exception {
    final UInt64 wsEpoch = UInt64.valueOf(10);
    final UInt64 wsEpochSlot = this.spec.computeStartSlotAtEpoch(wsEpoch);
    final SignedBlockAndState wsBlock = localChainBuilder.generateBlockAtSlot(wsEpochSlot);
    final SignedBlockAndState nextBlock =
        localChainBuilder.generateBlockAtSlot(wsEpochSlot.plus(1));
    localChainUpdater.saveBlock(nextBlock);
    localChainUpdater.updateBestBlock(nextBlock);
    localChainUpdater.setCurrentSlot(wsEpochSlot.plus(1));

    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final Checkpoint wsCheckpoint = new Checkpoint(wsEpoch, wsBlock.getRoot());
    final WeakSubjectivityConfig wsConfig =
        WeakSubjectivityConfig.builder()
            .specProvider(spec)
            .weakSubjectivityCheckpoint(wsCheckpoint)
            .build();
    final WeakSubjectivityValidator weakSubjectivityValidator =
        WeakSubjectivityValidator.lenient(wsConfig);
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(wsBlock.getBlock()).get();
    assertSuccessfulResult(result);
    // Known blocks should report as successfully imported
    final BlockImportResult result2 = blockImporter.importBlock(nextBlock.getBlock()).get();
    assertSuccessfulResult(result2, false);
  }

  @Test
  public void importBlock_runWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // The current slot is far ahead of the block being imported
    final UInt64 wsPeriod = UInt64.valueOf(10);
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(wsPeriod).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final SignedBlockAndState blockToImport = storageSystem.chainBuilder().generateBlockAtSlot(1);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were run
    final CheckpointState finalizedCheckpointState =
        CheckpointState.create(
            spec,
            new Checkpoint(UInt64.ZERO, genesis.getRoot()),
            genesis.getBlock(),
            genesis.getState());
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(finalizedCheckpointState, currentSlot);
  }

  @Test
  public void importBlock_nonFinalizingChain_runWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Set current time to be several WSP's ahead of finalized checkpoint
    final UInt64 wsPeriod = UInt64.valueOf(10);
    final UInt64 wsPeriodInSlots = wsPeriod.times(genesisConfig.getSlotsPerEpoch());
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = wsPeriodInSlots.times(3).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    // Add a recent block
    final SignedBlockAndState recentBlock =
        storageSystem.chainUpdater().advanceChain(currentSlot.minus(wsPeriodInSlots).minus(1));
    storageSystem.chainUpdater().updateBestBlock(recentBlock);

    // Import block new block
    final SignedBlockAndState blockToImport =
        storageSystem.chainBuilder().generateBlockAtSlot(currentSlot);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were run
    final CheckpointState finalizedCheckpointState =
        CheckpointState.create(
            spec,
            new Checkpoint(UInt64.ZERO, genesis.getRoot()),
            genesis.getBlock(),
            genesis.getState());
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(finalizedCheckpointState, currentSlot);
  }

  @Test
  public void importBlock_nonFinalizingChain_skipWSPChecks() throws Exception {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    storageSystem.chainUpdater().initializeGenesis();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    // Set current time to be several WSP's ahead of finalized checkpoint
    final UInt64 wsPeriod = UInt64.valueOf(10);
    final UInt64 wsPeriodInSlots = wsPeriod.times(genesisConfig.getSlotsPerEpoch());
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = wsPeriodInSlots.times(3).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    // Add a recent block
    final SignedBlockAndState recentBlock =
        storageSystem.chainUpdater().advanceChain(currentSlot.minus(wsPeriodInSlots));
    storageSystem.chainUpdater().updateBestBlock(recentBlock);

    // Import block new block
    final SignedBlockAndState blockToImport =
        storageSystem.chainBuilder().generateBlockAtSlot(currentSlot);

    // Import wsBlock
    final BlockImportResult result = blockImporter.importBlock(blockToImport.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify ws period checks were skipped
    verify(weakSubjectivityValidator, never()).validateLatestFinalizedCheckpoint(any(), any());
  }

  @Test
  public void getLatestCheckpointState_initialCall() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();

    SafeFuture<CheckpointState> result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getRoot()).isEqualTo(genesis.getRoot());

    // Second call should be the same
    result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getRoot()).isEqualTo(genesis.getRoot());
  }

  @Test
  public void getLatestCheckpointState_shouldPullUpdatedFinalizedCheckpoint() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());
    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final SignedBlockAndState genesis = storageSystem.chainUpdater().initializeGenesis();

    // Pull genesis checkpoint
    SafeFuture<CheckpointState> result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getRoot()).isEqualTo(genesis.getRoot());

    // Update latest finalized
    final UInt64 newFinalizedEpoch = UInt64.valueOf(2);
    final SignedBlockAndState newFinalizedBlock =
        storageSystem.chainUpdater().advanceChain(spec.computeStartSlotAtEpoch(newFinalizedEpoch));
    storageSystem.chainUpdater().finalizeEpoch(newFinalizedEpoch);

    // Second call should pull new finalized checkpoint
    result = blockImporter.getLatestCheckpointState();
    assertThat(result).isCompleted();
    assertThat(safeJoin(result).getEpoch()).isEqualTo(newFinalizedEpoch);
    assertThat(safeJoin(result).getRoot()).isEqualTo(newFinalizedBlock.getRoot());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void importBlock_validBlsToExecutionChanges() throws Exception {
    final int numOfValidators = 3;
    // Using same keys used on InMemoryStorageSystemBuilder
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numOfValidators);
    final Spec spec = TestSpecFactory.createMinimalCapella();

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .numberOfValidators(numOfValidators)
            .build();
    storageSystem.chainUpdater().initializeGenesis();

    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            storageSystem.recentChainData(),
            forkChoiceNotifier,
            transitionBlockValidator,
            storageSystem.getMetricsSystem());

    final BlockImporter blockImporter =
        new BlockImporter(
            asyncRunner,
            spec,
            receivedBlockEventsChannelPublisher,
            storageSystem.recentChainData(),
            forkChoice,
            weakSubjectivityValidator,
            ExecutionLayerChannel.NOOP);

    final VerifiedBlockOperationsListener<SignedBlsToExecutionChange> operationsListener =
        mock(VerifiedBlockOperationsListener.class);
    blockImporter.subscribeToVerifiedBlockBlsToExecutionChanges(operationsListener);

    // Set current time to be several WSP's ahead of finalized checkpoint
    final UInt64 wsPeriod = UInt64.valueOf(10);
    final UInt64 wsPeriodInSlots = wsPeriod.times(genesisConfig.getSlotsPerEpoch());
    when(weakSubjectivityValidator.getWSPeriod(any())).thenReturn(Optional.of(wsPeriod));
    final UInt64 currentSlot = wsPeriodInSlots.times(3).plus(1);
    storageSystem.chainUpdater().setCurrentSlot(currentSlot);

    final BlsToExecutionChangeGenerator blsToExecutionChangeGenerator =
        new BlsToExecutionChangeGenerator(
            spec,
            storageSystem
                .recentChainData()
                .getGenesisData()
                .orElseThrow()
                .getGenesisValidatorsRoot());

    final BLSKeyPair blsKeyPair = validatorKeys.get(0);
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        blsToExecutionChangeGenerator.createAndSign(
            UInt64.ZERO, blsKeyPair, Eth1Address.ZERO, UInt64.ZERO);

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                currentSlot.minus(wsPeriodInSlots),
                BlockOptions.create()
                    .setBlsToExecutionChanges(
                        blsToExecutionChangeGenerator.asSszList(
                            UInt64.ZERO, signedBlsToExecutionChange)));

    final BlockImportResult result =
        blockImporter.importBlock(signedBlockAndState.getBlock()).get();
    assertSuccessfulResult(result);

    // Verify that subscribers are notified of the BlsToExecutionChange in the block
    final ArgumentCaptor<SszList<SignedBlsToExecutionChange>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);
    verify(operationsListener).onOperationsFromBlock(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).hasSize(1).contains(signedBlsToExecutionChange);
  }

  private void assertSuccessfulResult(final BlockImportResult result) {
    assertSuccessfulResult(result, true);
  }

  private void assertSuccessfulResult(
      final BlockImportResult result, final boolean shouldSendNotifications) {
    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getFailureReason()).isNull();
    assertThat(result.getFailureCause()).isEmpty();
    if (shouldSendNotifications) {
      // verify notifications are sent
      verify(receivedBlockEventsChannelPublisher)
          .onBlockImported(result.getBlock(), result.isImportedOptimistically());
    }
  }

  private void assertImportFailed(
      final BlockImportResult result, final BlockImportResult.FailureReason expectedReason) {
    assertThat(result.isSuccessful()).isFalse();
    assertThat(result.getFailureReason()).isEqualTo(expectedReason);
  }

  private void assertWeakSubjectivityWasChecked() {
    SafeFuture<CheckpointState> finalizedCheckpoint =
        recentChainData.getStore().retrieveFinalizedCheckpointAndState();
    assertThat(finalizedCheckpoint).isCompleted();
    UInt64 currentSlot = recentChainData.getCurrentSlot().orElseThrow();
    verify(weakSubjectivityValidator)
        .validateLatestFinalizedCheckpoint(safeJoin(finalizedCheckpoint), currentSlot);
  }
}
