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
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.generator.AggregateGenerator;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PendingAttestationPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService;
import tech.pegasys.teku.statetransition.validation.signatures.SimpleSignatureVerificationService;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class AttestationManagerIntegrationTest {

  public static final UInt64 COMMITTEE_INDEX = UInt64.ZERO;
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.ONE);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create()
          .specProvider(spec)
          .numberOfValidators(spec.getSlotsPerEpoch(UInt64.ZERO))
          .build();
  private final RecentChainData recentChainData = storageSystem.recentChainData();

  private final AggregateGenerator generator =
      new AggregateGenerator(spec, storageSystem.chainBuilder().getValidatorKeys());

  private final AggregatingAttestationPool attestationPool =
      new AggregatingAttestationPoolV2(
          spec,
          recentChainData,
          new NoOpMetricsSystem(),
          DEFAULT_MAXIMUM_ATTESTATION_COUNT,
          AggregatingAttestationPoolProfiler.NOOP,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE);
  private final PendingAttestationPool pendingAttestationPool =
      new PoolFactory(storageSystem.getMetricsSystem())
          .createPendingAttestationPool(spec, DEFAULT_MAX_QUEUE_PENDING_ATTESTATIONS);
  private final MergeTransitionBlockValidator transitionBlockValidator =
      new MergeTransitionBlockValidator(spec, recentChainData);
  private final ForkChoice forkChoice =
      new ForkChoice(
          spec,
          new InlineEventThread(),
          recentChainData,
          new NoopForkChoiceNotifier(),
          transitionBlockValidator,
          storageSystem.getMetricsSystem());
  private final FutureItems<ValidatableAttestation> futureAttestations =
      FutureItems.create(
          ValidatableAttestation::getEarliestSlotForForkChoiceProcessing,
          mock(SettableLabelledGauge.class),
          "attestations");
  private final SignatureVerificationService signatureVerificationService =
      new SimpleSignatureVerificationService();
  private final GossipValidationHelper gossipValidationHelper =
      new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
  private final AttestationValidator attestationValidator =
      new AttestationValidator(
          spec,
          signatureVerificationService,
          gossipValidationHelper,
          new ConcurrentHashMap<>(),
          Set.of());
  private final ActiveValidatorChannel activeValidatorChannel = mock(ActiveValidatorChannel.class);

  private final AttestationManager attestationManager =
      new AttestationManager(
          forkChoice,
          pendingAttestationPool,
          futureAttestations,
          attestationPool,
          attestationValidator,
          new AggregateAttestationValidator(
              spec, attestationValidator, signatureVerificationService),
          signatureVerificationService,
          activeValidatorChannel);

  // Version of forks with same fork version for previous and current
  // Guarantees that's the version used for signing regardless of slot
  private final Fork altairFork = createForkWithVersion(spec.fork(UInt64.ONE).getCurrentVersion());
  private final Fork phase0Fork = createForkWithVersion(spec.fork(UInt64.ZERO).getCurrentVersion());

  @BeforeEach
  public void setup() {
    storageSystem.chainUpdater().initializeGenesis();
    assertThat(attestationManager.start()).isCompleted();
  }

  private Fork createForkWithVersion(final Bytes4 version) {
    return new Fork(version, version, UInt64.ZERO);
  }

  @AfterEach
  public void cleanup() {
    assertThat(attestationManager.stop()).isCompleted();
  }

  @Test
  void shouldAcceptAttestationsBeforeForkWithOriginalForkId() {
    final UInt64 attestationSlot = UInt64.ONE;

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidatableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, phase0Fork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation, Optional.empty());
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldRejectAttestationsBeforeForkWithNewForkId() {
    final UInt64 attestationSlot = UInt64.ONE;

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidatableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation, Optional.empty());
    assertThat(result)
        .isCompletedWithValueMatching(InternalValidationResult::isReject, "is rejected");
  }

  @Test
  void shouldRejectAttestationsAfterForkWithOldForkId() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidatableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, phase0Fork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation, Optional.empty());
    assertThat(result)
        .isCompletedWithValueMatching(InternalValidationResult::isReject, "is rejected");
  }

  @Test
  void shouldAcceptAttestationsAfterForkWithNewForkId_emptySlots() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidatableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation, Optional.empty());
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldAcceptAttestationAggregatesAfterForkWithNewForkId_emptySlots() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedAggregateAndProof aggregate =
        generator.validAggregateAndProof(storageSystem.getChainHead(), attestationSlot);
    ValidatableAttestation attestation =
        ValidatableAttestation.aggregateFromValidator(spec, aggregate);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAggregate(attestation, Optional.empty());

    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  @Test
  void shouldAcceptAttestationsAfterForkWithNewForkId_filledSlots() {
    final UInt64 attestationSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final SignedBlockAndState chainHead =
        storageSystem.chainUpdater().advanceChainUntil(attestationSlot);
    storageSystem.chainUpdater().updateBestBlock(chainHead);

    // Fork choice only runs attestations one slot after they're sent.
    storageSystem.chainUpdater().setCurrentSlot(attestationSlot.plus(1));

    final SignedBlockAndState targetBlockAndState =
        storageSystem.chainBuilder().getLatestBlockAndStateAtSlot(attestationSlot);
    final ValidatableAttestation attestation =
        createAttestation(attestationSlot, targetBlockAndState, altairFork);

    final SafeFuture<InternalValidationResult> result =
        attestationManager.addAttestation(attestation, Optional.empty());
    assertThat(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
  }

  private ValidatableAttestation createAttestation(
      final UInt64 attestationSlot,
      final SignedBlockAndState targetBlockAndState,
      final Fork fork) {
    final int validatorCommitteePosition = 0;
    final IntList committee =
        spec.getBeaconCommittee(targetBlockAndState.getState(), attestationSlot, COMMITTEE_INDEX);
    final int validatorId = committee.getInt(validatorCommitteePosition);

    final AttestationData attestationData =
        spec.getGenericAttestationData(
            attestationSlot, targetBlockAndState.getState(), targetBlockAndState, COMMITTEE_INDEX);

    final ForkInfo forkInfo =
        new ForkInfo(fork, targetBlockAndState.getState().getGenesisValidatorsRoot());
    final BLSSignature signature =
        storageSystem
            .chainBuilder()
            .sign(validatorId, signer -> signer.signAttestationData(attestationData, forkInfo));

    final AttestationSchema<?> attestationSchema =
        spec.atSlot(attestationSlot).getSchemaDefinitions().getAttestationSchema();
    SszBitlist aggregationBits =
        attestationSchema
            .getAggregationBitsSchema()
            .ofBits(committee.size(), validatorCommitteePosition);
    final Attestation attestation =
        attestationSchema.create(aggregationBits, attestationData, signature);
    return ValidatableAttestation.fromNetwork(
        spec,
        attestation,
        spec.computeSubnetForAttestation(targetBlockAndState.getState(), attestation));
  }
}
