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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.AttestationPq;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.block.VerifiedBlockOperationsListener;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;
import tech.pegasys.teku.xmss.XmssConstants;

/** Fork choice over a chain that runs from EIP-8483 through the PQ fork. */
class ForkChoicePqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final InlineEventThread eventThread = new InlineEventThread();
  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final BlockBroadcastValidator blockBroadcastValidator =
      mock(BlockBroadcastValidator.class);
  private final MergeTransitionBlockValidator transitionBlockValidator =
      mock(MergeTransitionBlockValidator.class);
  private final ExecutionLayerChannelStub executionLayer =
      new ExecutionLayerChannelStub(spec, false);

  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private RecentChainData recentChainData;
  private ForkChoice forkChoice;

  @BeforeEach
  void setUp() {
    // As in the spec's own tests: the chain carries stub XMSS signatures
    XmssConstants.disableXmssVerification();
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        KZG.DISABLED);
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.PRUNE)
            .specProvider(spec)
            .numberOfValidators(16)
            .build();
    chainBuilder = storageSystem.chainBuilder();
    recentChainData = storageSystem.recentChainData();
    recentChainData.initializeFromGenesis(chainBuilder.generateGenesis().getState(), UInt64.ZERO);
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
            false,
            LateBlockReorgPreparationHandler.NOOP,
            mock(DebugDataDumper.class),
            new StubMetricsSystem(),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    when(transitionBlockValidator.verifyAncestorTransitionBlock(any()))
        .thenReturn(SafeFuture.completedFuture(PayloadValidationResult.VALID));
    when(blockBroadcastValidator.getResult())
        .thenReturn(SafeFuture.completedFuture(BroadcastValidationResult.SUCCESS));
  }

  @AfterEach
  void tearDown() {
    XmssConstants.enableXmssVerification();
  }

  @Test
  void importsBlocksAndPayloadsThroughThePqFork() {
    final UInt64 lastSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH.plus(2));
    final List<SignedBlockAndState> blocks = chainBuilder.generateBlocksUpToSlot(lastSlot);

    for (final SignedBlockAndState block : blocks) {
      importBlock(block);
      importPayload(block);
    }

    final SignedBlockAndState head = blocks.getLast();
    assertThat(spec.atSlot(head.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    assertThat(forkChoice.processHead(head.getSlot())).isCompleted();
    assertThat(recentChainData.getBestBlockRoot()).contains(head.getRoot());
    // Every block built on its parent's payload, so each one's parent node is the full one,
    // across the fork too
    final ReadOnlyForkChoiceStrategy strategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    final UInt64 pqForkSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH);
    for (final SignedBlockAndState block : blocks) {
      if (block.getSlot().isGreaterThanOrEqualTo(pqForkSlot)) {
        assertThat(strategy.getParentBeaconBlockNode(ForkChoiceNode.createBase(block.getRoot())))
            .describedAs("parent of the block at slot %s", block.getSlot())
            .contains(ForkChoiceNode.createFull(block.getParentRoot()));
      }
    }
  }

  @Test
  void appliesVotesFromAttestationsInPqBlocks() {
    final UInt64 pqForkSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH);
    for (final SignedBlockAndState block :
        chainBuilder.generateBlocksUpToSlot(pqForkSlot.plus(1))) {
      importBlock(block);
      importPayload(block);
    }
    final SignedBlockAndState attestedBlock = chainBuilder.getLatestBlockAndState();
    assertThat(spec.atSlot(attestedBlock.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);

    // PQ includes attestations two slots after their own
    final UInt64 inclusionSlot = attestedBlock.getSlot().plus(2);
    final ChainBuilder.BlockOptions options = ChainBuilder.BlockOptions.create();
    chainBuilder
        .streamValidAttestationsForBlockAtSlot(inclusionSlot)
        .forEach(options::addAttestation);
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(inclusionSlot, options);
    final List<Attestation> attestations =
        block.getBlock().getMessage().getBody().getAttestations().asList();
    assertThat(attestations).isNotEmpty().allMatch(AttestationPq.class::isInstance);
    // It also includes the fork slot's attestations, which vote for the first PQ block
    assertThat(attestations)
        .anyMatch(
            attestation ->
                attestation.getData().getBeaconBlockRoot().equals(attestedBlock.getRoot()));

    importBlock(block);
    importPayload(block);
    assertThat(forkChoice.processHead(inclusionSlot)).isCompleted();

    // The block's votes count towards the block they attest to
    assertThat(
            recentChainData
                .getForkChoiceStrategy()
                .orElseThrow()
                .getWeight(attestedBlock.getRoot()))
        .hasValueSatisfying(weight -> assertThat(weight).isGreaterThan(UInt64.ZERO));
    assertThat(recentChainData.getBestBlockRoot()).contains(block.getRoot());
  }

  @Test
  @SuppressWarnings("unchecked")
  void blockImporterImportsPqBlocksWithoutVoluntaryExits() {
    final WeakSubjectivityValidator weakSubjectivityValidator =
        mock(WeakSubjectivityValidator.class);
    when(weakSubjectivityValidator.isBlockValid(any(), any())).thenReturn(true);
    final BlockImporter blockImporter =
        new BlockImporter(
            SYNC_RUNNER,
            spec,
            mock(ReceivedBlockEventsChannel.class),
            recentChainData,
            forkChoice,
            weakSubjectivityValidator,
            executionLayer);
    final VerifiedBlockOperationsListener<SignedVoluntaryExit> exitListener =
        mock(VerifiedBlockOperationsListener.class);
    blockImporter.subscribeToVerifiedBlockVoluntaryExits(exitListener);
    final List<SignedBlockAndState> blocks =
        chainBuilder.generateBlocksUpToSlot(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH).plus(1));

    for (final SignedBlockAndState block : blocks) {
      storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
      final BlockImportResult result = safeJoin(blockImporter.importBlock(block.getBlock()));
      assertThat(result.isSuccessful())
          .describedAs("block at slot %s: %s", block.getSlot(), result.getFailureReason())
          .isTrue();
      importPayload(block);
    }

    assertThat(spec.atSlot(blocks.getLast().getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    // Only pre-PQ blocks have exits to deliver
    verify(exitListener, times(blocks.size() - 2)).onOperationsFromBlock(any());
  }

  private void importBlock(final SignedBlockAndState block) {
    storageSystem.chainUpdater().advanceCurrentSlotToAtLeast(block.getSlot());
    final BlockImportResult result =
        safeJoin(
            forkChoice.onBlock(
                block.getBlock(), Optional.empty(), blockBroadcastValidator, executionLayer));
    assertThat(result.isSuccessful())
        .describedAs("block at slot %s: %s", block.getSlot(), result.getFailureReason())
        .isTrue();
  }

  private void importPayload(final SignedBlockAndState block) {
    final Optional<SignedExecutionPayloadEnvelope> envelope =
        chainBuilder.getExecutionPayload(block.getRoot());
    if (envelope.isEmpty()) {
      return;
    }
    final ExecutionPayloadImportResult result =
        safeJoin(forkChoice.onExecutionPayloadEnvelope(envelope.get(), executionLayer));
    assertThat(result.isSuccessful())
        .describedAs("payload at slot %s: %s", block.getSlot(), result)
        .isTrue();
  }
}
