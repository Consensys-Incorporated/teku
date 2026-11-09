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

package tech.pegasys.teku.validator.coordinator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.ATTESTATION_INCLUSION_RANGE;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.ELECTRA})
public class DefaultPerformanceTrackerTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);
  private final StatusLogger log = mock(StatusLogger.class);
  private final ActiveValidatorTracker validatorTracker = mock(ActiveValidatorTracker.class);
  private final SyncCommitteePerformanceTracker syncCommitteePerformanceTracker =
      mock(SyncCommitteePerformanceTracker.class);
  private final ValidatorPerformanceMetrics validatorPerformanceMetrics =
      mock(ValidatorPerformanceMetrics.class);

  private Spec spec;
  private SpecMilestone specMilestone;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private ChainUpdater chainUpdater;
  private DataStructureUtil dataStructureUtil;
  private DefaultPerformanceTracker performanceTracker;

  @BeforeEach
  void beforeEach(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);

    chainUpdater = new ChainUpdater(storageSystem.recentChainData(), chainBuilder, spec);

    performanceTracker =
        new DefaultPerformanceTracker(
            storageSystem.combinedChainDataClient(),
            log,
            validatorPerformanceMetrics,
            ValidatorPerformanceTrackingMode.ALL,
            validatorTracker,
            syncCommitteePerformanceTracker,
            spec,
            mock(SettableGauge.class));

    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(0);
    when(syncCommitteePerformanceTracker.calculatePerformance(any()))
        .thenReturn(
            SafeFuture.completedFuture(new SyncCommitteePerformance(UInt64.ZERO, 0, 0, 0, 0)));
    chainUpdater.initializeGenesis();
    performanceTracker.start(UInt64.ZERO);
  }

  @TestTemplate
  void shouldDisplayPerfectBlockInclusion() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(10));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(1)));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(2)));
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(1).getSlotAndBlockRoot());
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(2).getSlotAndBlockRoot());
    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    BlockPerformance expectedBlockPerformance = new BlockPerformance(UInt64.ZERO, 2, 2, 2);
    verify(log).performance(expectedBlockPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayBlockInclusionWhenProducedBlockIsChainHead() {
    final UInt64 lastSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState bestBlock = chainUpdater.advanceChainUntil(2);
    chainUpdater.updateBestBlock(bestBlock);
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(bestBlock.getSlot()));
    performanceTracker.saveProducedBlock(bestBlock.getBlock().getSlotAndBlockRoot());
    performanceTracker.onSlot(lastSlot);
    BlockPerformance expectedBlockPerformance = new BlockPerformance(UInt64.ZERO, 1, 1, 1);
    verify(log).performance(expectedBlockPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayOneMissedBlock() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(10));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(1)));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(2)));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(3)));
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(1).getSlotAndBlockRoot());
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(2).getSlotAndBlockRoot());
    performanceTracker.saveProducedBlock(
        dataStructureUtil.randomSignedBeaconBlock(3).getSlotAndBlockRoot());
    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));
    BlockPerformance expectedBlockPerformance = new BlockPerformance(UInt64.ZERO, 3, 2, 3);
    verify(log).performance(expectedBlockPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayPerfectAttestationInclusion() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(2, 1);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState latestBlockAndState = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(latestBlockAndState);
    chainUpdater.updateBestBlock(latestBlockAndState);

    performanceTracker.saveProducedAttestation(attestation1);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(1);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);
    performanceTracker.onSlot(slot);

    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 1, 1, 1, 1, 1, 1, 1, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayInclusionDistanceOfMax2Min1() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(2, 1);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    Attestation attestation2 = createAttestation(4, 2);
    block2Options.addAttestation(attestation2);
    SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(4, block2Options);
    chainUpdater.saveBlock(blockAndState2);
    chainUpdater.updateBestBlock(blockAndState2);

    performanceTracker.saveProducedAttestation(attestation1);
    performanceTracker.saveProducedAttestation(attestation2);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(2);
    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);
    performanceTracker.onSlot(slot);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 2, 2, 2, 2, 1, 1.5, 2, 2);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayIncorrectTargetRoot() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder chainBuilderFork = chainBuilder.fork();
    ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork);

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(8));
    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(9, 8);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(9, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    chainUpdaterFork.advanceChain(6);
    chainUpdaterFork.advanceChainUntil(9);
    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    Attestation attestation2 = createAttestation(chainBuilderFork, 10, 9);
    block2Options.addAttestation(attestation2);
    SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(10, block2Options);
    chainUpdater.saveBlock(blockAndState2);
    chainUpdater.updateBestBlock(blockAndState2);

    performanceTracker.saveProducedAttestation(attestation1);
    performanceTracker.saveProducedAttestation(attestation2);

    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(2);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE.plus(1));
    performanceTracker.onSlot(slot);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(2);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 2, 2, 2, 1, 1, 1, 1, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldDisplayIncorrectHeadBlockRoot() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder chainBuilderFork = chainBuilder.fork();
    ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork);

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(9));
    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(10, 9);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(10, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    chainUpdaterFork.advanceChainUntil(7);
    SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(8);
    chainUpdaterFork.updateBestBlock(blockAndState);
    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    AttestationGenerator attestationGenerator =
        new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
    Attestation attestation2 =
        attestationGenerator.validAttestation(blockAndState.toUnsigned(), UInt64.valueOf(9));
    block2Options.addAttestation(attestation2);
    SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(11, block2Options);
    chainUpdater.saveBlock(blockAndState2);
    chainUpdater.updateBestBlock(blockAndState2);

    performanceTracker.saveProducedAttestation(attestation1);
    performanceTracker.saveProducedAttestation(attestation2);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(2);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE.plus(1));
    performanceTracker.onSlot(slot);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 2, 2, 2, 2, 1, 1.5, 2, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldClearOldSentObjects() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(10));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(1)));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(2)));
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(1).getSlotAndBlockRoot());
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(2).getSlotAndBlockRoot());

    performanceTracker.saveProducedAttestation(dataStructureUtil.randomAttestation(UInt64.ONE));
    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));
    assertThat(performanceTracker.producedAttestationsByEpoch).isEmpty();
    assertThat(performanceTracker.producedBlocksByEpoch).isEmpty();
    assertThat(performanceTracker.blockProductionAttemptsByEpoch).isEmpty();
  }

  @TestTemplate
  void shouldClearObjectsAfterFailure() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(10));
    final CombinedChainDataClient combinedChainDataClientMock = mock(CombinedChainDataClient.class);
    // Make the attestation performance calculation fail
    when(combinedChainDataClientMock.getBestState())
        .thenReturn(Optional.of(SafeFuture.failedFuture(new IllegalArgumentException("failure"))));
    final ChainHead chainHeadMock = mock(ChainHead.class);
    // Make the block performance calculation fail
    when(chainHeadMock.asStateAndBlockSummary())
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("failure")));
    when(combinedChainDataClientMock.getChainHead()).thenReturn(Optional.of(chainHeadMock));
    performanceTracker =
        new DefaultPerformanceTracker(
            combinedChainDataClientMock,
            log,
            validatorPerformanceMetrics,
            ValidatorPerformanceTrackingMode.ALL,
            validatorTracker,
            syncCommitteePerformanceTracker,
            spec,
            mock(SettableGauge.class));
    performanceTracker.start(UInt64.ZERO);
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(1)));
    performanceTracker.reportBlockProductionAttempt(spec.computeEpochAtSlot(UInt64.valueOf(2)));
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(1).getSlotAndBlockRoot());
    performanceTracker.saveProducedBlock(
        chainUpdater.chainBuilder.getBlockAtSlot(2).getSlotAndBlockRoot());

    performanceTracker.saveProducedAttestation(dataStructureUtil.randomAttestation(UInt64.ZERO));
    performanceTracker.saveProducedAttestation(dataStructureUtil.randomAttestation(UInt64.ONE));
    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(2)));
    assertThat(performanceTracker.producedAttestationsByEpoch).isEmpty();
    assertThat(performanceTracker.producedBlocksByEpoch).isEmpty();
    assertThat(performanceTracker.blockProductionAttemptsByEpoch).isEmpty();
  }

  @TestTemplate
  void shouldNotCountDuplicateAttestationsIncludedOnChain() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(2, 1);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    block2Options.addAttestation(attestation1);
    SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(4, block2Options);
    chainUpdater.saveBlock(blockAndState2);
    chainUpdater.updateBestBlock(blockAndState2);

    performanceTracker.saveProducedAttestation(attestation1);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(1);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);
    performanceTracker.onSlot(slot);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 1, 1, 1, 1, 1, 1, 1, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldNotSkipValidationForAttestationsWithSameDataButDifferentBitlists() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(2, 1);
    Attestation attestation2 =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(2)
            .filter(
                a ->
                    a.getData().equals(attestation1.getData())
                        && !a.getAggregationBits().equals(attestation1.getAggregationBits()))
            .findFirst()
            .orElseThrow();

    block1Options.addAttestation(attestation1);
    block1Options.addAttestation(attestation2);
    SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(blockAndState1);
    chainUpdater.updateBestBlock(blockAndState1);

    performanceTracker.saveProducedAttestation(attestation1);
    performanceTracker.saveProducedAttestation(attestation2);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(2);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);
    performanceTracker.onSlot(slot);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 2, 2, 2, 1, 1, 1, 2, 2);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldReportExpectedAttestationOnlyForTheGivenEpoch() {
    when(validatorTracker.getNumberOfValidatorsForEpoch(UInt64.valueOf(2))).thenReturn(2);
    when(validatorTracker.getNumberOfValidatorsForEpoch(UInt64.valueOf(3))).thenReturn(1);
    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE.plus(2));
    performanceTracker.onSlot(slot);
    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 2, 0, 0, 0, 0, 0, 0, 0);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  @TestTemplate
  void shouldNotReportAttestationPerformanceIfNoValidatorsInEpoch() {
    when(validatorTracker.getNumberOfValidatorsForEpoch(UInt64.valueOf(2))).thenReturn(0);
    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE.plus(2)));
    verify(log, never()).performance(anyString());
  }

  @TestTemplate
  void shouldReportSyncCommitteePerformance() {
    final UInt64 epoch = UInt64.valueOf(2);
    final SyncCommitteePerformance performance = new SyncCommitteePerformance(epoch, 10, 9, 8, 7);
    when(syncCommitteePerformanceTracker.calculatePerformance(epoch.minus(1)))
        .thenReturn(SafeFuture.completedFuture(performance));

    performanceTracker.onSlot(spec.computeStartSlotAtEpoch(epoch));
    verify(log).performance(performance.toString());
    verify(validatorPerformanceMetrics).updateSyncCommitteePerformance(performance);
  }

  @TestTemplate
  void shouldHandleErrorsWhenReportTasksFail() {
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));
    final Attestation attestation = createAttestationForParentBlockOnSlot(1);
    final UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);

    performanceTracker.saveProducedAttestation(attestation);
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenThrow(new RuntimeException());

    try (LogCaptor logCaptor = LogCaptor.forClass(DefaultPerformanceTracker.class)) {
      performanceTracker.onSlot(slot);

      // No attestation performance report on status logger because task failed
      verifyNoInteractions(log);
      assertThat(logCaptor.getErrorLogs()).hasSize(1);
    }
  }

  @TestTemplate
  void shouldAddSingleAttestation() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(SpecMilestone.ELECTRA);
    final SingleAttestation singleAttestation =
        dataStructureUtil.randomSingleAttestation(UInt64.valueOf(2), UInt64.ZERO);
    performanceTracker.saveProducedAttestation(singleAttestation);
    assertThat(performanceTracker.producedAttestationsByEpoch).hasSize(1);
  }

  @TestTemplate
  void shouldConvertSingleAttestation() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(SpecMilestone.ELECTRA);
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    ChainBuilder.BlockOptions block1Options = ChainBuilder.BlockOptions.create();
    Attestation attestation1 = createAttestation(2, 1);
    block1Options.addAttestation(attestation1);
    SignedBlockAndState latestBlockAndState = chainBuilder.generateBlockAtSlot(2, block1Options);
    chainUpdater.saveBlock(latestBlockAndState);
    chainUpdater.updateBestBlock(latestBlockAndState);

    final SingleAttestation singleAttestation =
        spec.getGenesisSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getSingleAttestationSchema()
            .create(
                UInt64.ZERO,
                UInt64.ZERO,
                attestation1.getData(),
                attestation1.getAggregateSignature());

    performanceTracker.saveProducedAttestation(singleAttestation);

    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(1);

    UInt64 slot = spec.computeStartSlotAtEpoch(ATTESTATION_INCLUSION_RANGE);
    performanceTracker.onSlot(slot);

    UInt64 attestationEpoch = spec.computeEpochAtSlot(slot).minus(ATTESTATION_INCLUSION_RANGE);
    AttestationPerformance expectedAttestationPerformance =
        new AttestationPerformance(attestationEpoch, 1, 1, 1, 1, 1, 1, 1, 1);
    verify(log).performance(expectedAttestationPerformance.toString());
  }

  /**
   * Creates an attestation voting for block on the slot provided. The attestation will be included
   * in block slot + 1.
   *
   * @param slot the slot of the block being attested
   * @return the created attestation
   */
  private Attestation createAttestationForParentBlockOnSlot(final int slot) {
    Attestation attestationForBlock1 = createAttestation(slot + 1, slot);
    ChainBuilder.BlockOptions block2Options = ChainBuilder.BlockOptions.create();
    block2Options.addAttestation(attestationForBlock1);
    SignedBlockAndState latestBlockAndState = chainBuilder.generateBlockAtSlot(2, block2Options);
    chainUpdater.saveBlock(latestBlockAndState);
    chainUpdater.updateBestBlock(latestBlockAndState);
    return attestationForBlock1;
  }

  private Attestation createAttestation(
      final ChainBuilder chainBuilder,
      final int validForBlockAtSlot,
      final int vouchingForBlockAtSlot) {
    return chainBuilder
        .streamValidAttestationsForBlockAtSlot(validForBlockAtSlot)
        .filter(
            a ->
                a.getData()
                    .getBeaconBlockRoot()
                    .equals(chainBuilder.getBlockAtSlot(vouchingForBlockAtSlot).getRoot()))
        .findFirst()
        .orElseThrow();
  }

  private Attestation createAttestation(
      final int validForBlockAtSlot, final int vouchingForBlockAtSlot) {
    return createAttestation(chainBuilder, validForBlockAtSlot, vouchingForBlockAtSlot);
  }
}
