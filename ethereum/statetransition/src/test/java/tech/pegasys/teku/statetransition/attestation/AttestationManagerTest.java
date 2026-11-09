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

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.DEFER_FOR_FORK_CHOICE;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.SAVED_FOR_FUTURE;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.SUCCESSFUL;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.UNKNOWN_BLOCK;
import static tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult.UNKNOWN_EXECUTION_PAYLOAD;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ProcessedAttestationListener;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorCache;

class AttestationManagerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationSchema<?> attestationSchema =
      spec.getGenesisSchemaDefinitions().getAttestationSchema();
  private final SignedAggregateAndProofSchema aggregateSchema =
      spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema();

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final PoolFactory poolFactory = new PoolFactory(metricsSystem);
  private final PendingAttestationPool pendingAttestationPool =
      poolFactory.createPendingAttestationPool(spec, DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS);
  private final PendingPool<ValidatableAttestation> pendingAttestations =
      pendingAttestationPool.getAttestationsWaitingForBlock();
  private final FutureItems<ValidatableAttestation> futureAttestations =
      FutureItems.create(
          ValidatableAttestation::getEarliestSlotForForkChoiceProcessing,
          mock(SettableLabelledGauge.class),
          "attestations");
  private final SignatureVerificationService signatureVerificationService =
      mock(SignatureVerificationService.class);
  private final ActiveValidatorCache activeValidatorCache = mock(ActiveValidatorCache.class);

  private final AttestationValidator attestationValidator = mock(AttestationValidator.class);
  private final AggregateAttestationValidator aggregateValidator =
      mock(AggregateAttestationValidator.class);
  private final AttestationManager attestationManager =
      new AttestationManager(
          forkChoice,
          pendingAttestationPool,
          futureAttestations,
          attestationPool,
          attestationValidator,
          aggregateValidator,
          signatureVerificationService,
          activeValidatorCache);

  @BeforeEach
  public void setup() {
    when(signatureVerificationService.start()).thenReturn(SafeFuture.completedFuture(null));
    when(signatureVerificationService.stop()).thenReturn(SafeFuture.completedFuture(null));
    when(forkChoice.applyIndexedAttestations(any())).thenReturn(completedFuture(List.of()));
    assertThat(attestationManager.start()).isCompleted();
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  private void onSlot(final UInt64 slot) {
    pendingAttestationPool.onSlot(slot);
    attestationManager.onSlot(slot);
  }

  @Test
  public void shouldProcessAttestationsThatAreReadyImmediately() {
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SUCCESSFUL));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(attestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
  }

  @Test
  public void shouldProcessAggregatesThatAreReadyImmediately() {
    final ProcessedAttestationListener subscriber = mock(ProcessedAttestationListener.class);
    attestationManager.subscribeToAttestationsToSend(subscriber);
    final ValidatableAttestation aggregate =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SUCCESSFUL));
    when(aggregateValidator.validate(aggregate)).thenReturn(completedFuture(ACCEPT));
    assertThat(attestationManager.onAttestation(aggregate)).isCompleted();

    verify(forkChoice).onAttestation(aggregate);
    verify(attestationPool).add(aggregate);
    assertThat(futureAttestations.size()).isEqualTo(0);
    assertThat(pendingAttestations.size()).isEqualTo(0);
    verify(subscriber).accept(aggregate);
  }

  @Test
  public void shouldAddAttestationsThatHaveNotYetReachedTargetSlotToFutureItemsAndPool() {
    final int futureSlot = 100;
    final UInt64 currentSlot = UInt64.valueOf(futureSlot).minus(1);
    onSlot(currentSlot);

    ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, attestationFromSlot(futureSlot));
    IndexedAttestationLight randomIndexedAttestation =
        IndexedAttestationLight.fromSsz(
            dataStructureUtil.randomIndexedAttestation(
                UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SAVED_FOR_FUTURE));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(attestation);
    attestation.setIndexedAttestation(randomIndexedAttestation);
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.contains(attestation)).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(0);

    // Shouldn't try to process the attestation until after it's slot.
    onSlot(UInt64.valueOf(100));
    assertThat(futureAttestations.size()).isEqualTo(1);
    verify(forkChoice, never()).applyIndexedAttestations(any());

    onSlot(UInt64.valueOf(101));
    verify(forkChoice).applyIndexedAttestations(List.of(attestation));
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldPerformGossipValidationWhenProcessingFutureAggregates() {
    final ProcessedAttestationListener subscriber = mock(ProcessedAttestationListener.class);
    attestationManager.subscribeToAttestationsToSend(subscriber);
    final int futureSlot = 100;
    final UInt64 currentSlot = UInt64.valueOf(futureSlot).minus(1);
    onSlot(currentSlot);

    ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(
            spec, aggregateFromSlot(futureSlot, dataStructureUtil.randomBytes32()));
    IndexedAttestationLight randomIndexedAttestation =
        IndexedAttestationLight.fromSsz(
            dataStructureUtil.randomIndexedAttestation(
                UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SAVED_FOR_FUTURE));
    when(aggregateValidator.validate(attestation))
        .thenReturn(completedFuture(InternalValidationResult.IGNORE));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(attestation);
    attestation.setIndexedAttestation(randomIndexedAttestation);
    assertThat(futureAttestations.contains(attestation)).isTrue();

    onSlot(UInt64.valueOf(101));
    verify(aggregateValidator).validate(attestation);
    // Should apply because the attestation is valid
    verify(forkChoice).applyIndexedAttestations(List.of(attestation));
    assertThat(futureAttestations.size()).isZero();

    // But should not send to gossip because it was ignored
    verify(subscriber, never()).accept(attestation);
  }

  @Test
  public void shouldParkFutureFullPayloadAttestationWhenPayloadIsStillMissing() {
    final ProcessedAttestationListener subscriber = mock(ProcessedAttestationListener.class);
    attestationManager.subscribeToAttestationsToSend(subscriber);
    final int futureSlot = 100;
    final UInt64 currentSlot = UInt64.valueOf(futureSlot).minus(1);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    onSlot(currentSlot);

    final ValidatableAttestation attestation =
        ValidatableAttestation.fromValidator(
            spec, fullPayloadAttestationFromSlot(futureSlot, blockRoot));
    final IndexedAttestationLight randomIndexedAttestation =
        IndexedAttestationLight.fromSsz(
            dataStructureUtil.randomIndexedAttestation(
                UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(SAVED_FOR_FUTURE));
    when(forkChoice.applyIndexedAttestations(List.of(attestation)))
        .thenReturn(completedFuture(List.of(attestation)));

    assertThat(attestationManager.onAttestation(attestation)).isCompleted();
    attestation.setIndexedAttestation(randomIndexedAttestation);

    onSlot(UInt64.valueOf(101));

    verify(forkChoice).applyIndexedAttestations(List.of(attestation));
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(pendingAttestationPool.contains(attestation)).isTrue();
    verify(subscriber, never()).accept(attestation);
    assertThat(attestation.isGossiped()).isFalse();
  }

  @Test
  public void shouldNotAddDeferredAttestationsToFuturePool() {
    IndexedAttestationLight randomIndexedAttestation =
        IndexedAttestationLight.fromSsz(
            dataStructureUtil.randomIndexedAttestation(
                UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
    final UInt64 attestationSlot = randomIndexedAttestation.data().getSlot();
    final UInt64 currentSlot = attestationSlot.minus(1);
    onSlot(currentSlot);

    ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, attestationFromSlot(attestationSlot.longValue()));
    attestation.setIndexedAttestation(randomIndexedAttestation);
    when(forkChoice.onAttestation(any())).thenReturn(completedFuture(DEFER_FOR_FORK_CHOICE));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(any());
    verify(attestationPool).add(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
  }

  @Test
  public void shouldDeferProcessingForAttestationsThatAreMissingBlockDependencies() {
    final int slot = 1024;
    onSlot(UInt64.valueOf(slot));
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    final Bytes32 requiredBlockRoot = block.getMessage().hashTreeRoot();
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, attestationFromSlot(slot, requiredBlockRoot));
    attestation.setAcceptedAsGossip();
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(UNKNOWN_BLOCK))
        .thenReturn(completedFuture(SUCCESSFUL));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.contains(attestation)).isTrue();
    assertThat(pendingAttestations.size()).isEqualTo(1);

    // Slots progressing shouldn't cause the attestation to be processed
    onSlot(UInt64.valueOf(100));
    verifyNoMoreInteractions(forkChoice);

    // Importing a different block shouldn't cause the attestation to be processed
    attestationManager.onBlockImported(dataStructureUtil.randomSignedBeaconBlock(2), false);
    verifyNoMoreInteractions(forkChoice);

    attestationManager.onBlockImported(block, false);
    verify(forkChoice, times(2)).onAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    verify(attestationPool).add(attestation);
  }

  @Test
  public void shouldParkAttestationsThatAreMissingFullPayloadDependencies() {
    final ProcessedAttestationListener subscriber = mock(ProcessedAttestationListener.class);
    attestationManager.subscribeToAttestationsToSend(subscriber);
    final SignedExecutionPayloadEnvelope executionPayload =
        mock(SignedExecutionPayloadEnvelope.class);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final ValidatableAttestation attestation =
        ValidatableAttestation.fromValidator(spec, fullPayloadAttestationFromSlot(12, blockRoot));
    when(executionPayload.getBeaconBlockRoot()).thenReturn(blockRoot);
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(UNKNOWN_EXECUTION_PAYLOAD))
        .thenReturn(completedFuture(SUCCESSFUL));
    onSlot(UInt64.valueOf(12));

    assertThat(attestationManager.onAttestation(attestation))
        .isCompletedWithValue(UNKNOWN_EXECUTION_PAYLOAD);

    verify(forkChoice).onAttestation(attestation);
    assertThat(futureAttestations.size()).isZero();
    assertThat(pendingAttestations.size()).isZero();
    assertThat(pendingAttestationPool.contains(attestation)).isTrue();
    verifyNoInteractions(attestationPool);
    verify(subscriber, never()).accept(attestation);
    assertThat(attestation.isGossiped()).isFalse();

    attestationManager.onExecutionPayloadImported(executionPayload, false);

    verify(forkChoice, times(2)).onAttestation(attestation);
    verify(attestationPool).add(attestation);
    verify(subscriber).accept(attestation);
    assertThat(attestation.isGossiped()).isTrue();
  }

  @Test
  public void shouldValidateAggregatesBeforeGossipingAfterBlockIsReceived() {
    final ProcessedAttestationListener subscriber = mock(ProcessedAttestationListener.class);
    attestationManager.subscribeToAttestationsToSend(subscriber);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);
    final Bytes32 requiredBlockRoot = block.getMessage().hashTreeRoot();
    final ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(
            spec, aggregateFromSlot(1, requiredBlockRoot));
    when(aggregateValidator.validate(attestation))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(UNKNOWN_BLOCK))
        .thenReturn(completedFuture(SUCCESSFUL));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    assertThat(pendingAttestations.contains(attestation)).isTrue();

    attestationManager.onBlockImported(block, false);
    verify(forkChoice, times(2)).onAttestation(attestation);
    // Validate for gossip, but it is ignored so shouldn't send to gossip
    verify(aggregateValidator).validate(attestation);
    verify(subscriber, never()).accept(attestation);
  }

  @Test
  public void shouldNotPublishProcessedAttestationEventWhenAttestationIsInvalid() {
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(AttestationProcessingResult.invalid("Didn't like it")));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();

    verify(forkChoice).onAttestation(attestation);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldNotPublishProcessedAggregationEventWhenAttestationIsInvalid() {
    final ValidatableAttestation aggregateAndProof =
        ValidatableAttestation.aggregateFromValidator(
            spec, dataStructureUtil.randomSignedAggregateAndProof());
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(AttestationProcessingResult.invalid("Don't wanna")));
    assertThat(attestationManager.onAttestation(aggregateAndProof)).isCompleted();

    verify(forkChoice).onAttestation(aggregateAndProof);
    assertThat(pendingAttestations.size()).isZero();
    assertThat(futureAttestations.size()).isZero();
    verifyNoInteractions(attestationPool);
  }

  @Test
  public void shouldOnlyTraceReceivedAttestationFailingBecauseSignatureVerificationQueueIsFull() {
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(attestationValidator.validate(attestation)).thenReturn(completedFuture(ACCEPT));
    when(forkChoice.onAttestation(any()))
        .thenReturn(SafeFuture.failedFuture(new ServiceCapacityExceededException("queue is full")));

    try (final LogCaptor logCaptor = LogCaptor.forClass(AttestationManager.class, Level.TRACE)) {
      assertThat(attestationManager.addAttestation(attestation, Optional.empty()))
          .isCompletedWithValue(ACCEPT);

      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anyMatch(log -> log.startsWith("Failed to process received attestation."));
    }
  }

  @Test
  public void shouldLogErrorWhenReceivedAttestationFailsUnexpectedly() {
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, dataStructureUtil.randomAttestation());
    when(attestationValidator.validate(attestation)).thenReturn(completedFuture(ACCEPT));
    when(forkChoice.onAttestation(any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("unexpected")));

    try (final LogCaptor logCaptor = LogCaptor.forClass(AttestationManager.class)) {
      assertThat(attestationManager.addAttestation(attestation, Optional.empty()))
          .isCompletedWithValue(ACCEPT);

      assertThat(logCaptor.getErrorLogs())
          .containsExactly("Failed to process received attestation.");
    }
  }

  @Test
  public void shouldOnlyTracePendingAttestationFailingBecauseSignatureVerificationQueueIsFull() {
    final int slot = 1024;
    onSlot(UInt64.valueOf(slot));
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
    final Bytes32 requiredBlockRoot = block.getMessage().hashTreeRoot();
    final ValidatableAttestation attestation =
        ValidatableAttestation.from(spec, attestationFromSlot(slot, requiredBlockRoot));
    when(forkChoice.onAttestation(any()))
        .thenReturn(completedFuture(UNKNOWN_BLOCK))
        .thenReturn(SafeFuture.failedFuture(new ServiceCapacityExceededException("queue is full")));
    assertThat(attestationManager.onAttestation(attestation)).isCompleted();
    assertThat(pendingAttestations.contains(attestation)).isTrue();

    try (final LogCaptor logCaptor = LogCaptor.forClass(AttestationManager.class, Level.TRACE)) {
      // Importing the block replays the pending attestation, which now fails on the full queue
      attestationManager.onBlockImported(block, false);

      verify(forkChoice, times(2)).onAttestation(attestation);
      assertThat(logCaptor.getErrorLogs()).isEmpty();
      assertThat(logCaptor.getTraceLogs())
          .anyMatch(
              log ->
                  log.startsWith(
                      "Failed to process pending attestation dependent on " + requiredBlockRoot));
    }
  }

  private Attestation attestationFromSlot(final long slot) {
    return attestationFromSlot(slot, Bytes32.ZERO);
  }

  private Attestation attestationFromSlot(final long slot, final Bytes32 targetRoot) {
    return attestationSchema.create(
        attestationSchema.getAggregationBitsSchema().ofBits(1),
        new AttestationData(
            UInt64.valueOf(slot),
            UInt64.ZERO,
            Bytes32.ZERO,
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
            new Checkpoint(UInt64.ZERO, targetRoot)),
        BLSSignature.empty());
  }

  private Attestation fullPayloadAttestationFromSlot(final long slot, final Bytes32 blockRoot) {
    return attestationSchema.create(
        attestationSchema.getAggregationBitsSchema().ofBits(1, 0),
        new AttestationData(
            UInt64.valueOf(slot),
            UInt64.ONE,
            blockRoot,
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO),
            new Checkpoint(UInt64.ZERO, Bytes32.ZERO)),
        BLSSignature.empty());
  }

  private SignedAggregateAndProof aggregateFromSlot(final long slot, final Bytes32 targetRoot) {
    final Attestation attestation = attestationFromSlot(slot, targetRoot);
    return aggregateSchema.create(
        aggregateSchema
            .getAggregateAndProofSchema()
            .create(UInt64.valueOf(123), attestation, BLSSignature.empty()),
        BLSSignature.empty());
  }
}
