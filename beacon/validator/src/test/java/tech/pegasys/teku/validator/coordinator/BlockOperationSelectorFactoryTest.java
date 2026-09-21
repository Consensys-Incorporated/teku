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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.validator.coordinator.BlockProductionTestUtil.blockProductionContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.capella.operations.validation.BlsToExecutionChangesValidator.BlsToExecutionChangeInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.AttesterSlashingValidator.AttesterSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.ProposerSlashingValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.SimpleOperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.validator.api.ClientGraffitiAppendFormat;

class BlockOperationSelectorFactoryTest {
  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);

  @SuppressWarnings("unchecked")
  private final OperationValidator<AttesterSlashing> attesterSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<ProposerSlashing> proposerSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedVoluntaryExit> voluntaryExitValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedBlsToExecutionChange> blsToExecutionChangeValidator =
      mock(OperationValidator.class);

  private final SignedContributionAndProofValidator contributionValidator =
      mock(SignedContributionAndProofValidator.class);

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool =
      new SimpleOperationPool<>(
          "attester_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
          attesterSlashingValidator,
          Comparator.<AttesterSlashing>comparingInt(
                  slashing -> slashing.getIntersectingValidatorIndices().size())
              .reversed());
  private final OperationPool<ProposerSlashing> proposerSlashingPool =
      new SimpleOperationPool<>(
          "proposer_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
          proposerSlashingValidator);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool =
      new SimpleOperationPool<>(
          "voluntary_exit",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
          voluntaryExitValidator);

  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      new SimpleOperationPool<>(
          "bls_to_execution_Change",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(
              s -> s.toVersionCapella().orElseThrow().getBlsToExecutionChangesSchema()),
          blsToExecutionChangeValidator);

  private final SyncCommitteeContributionPool contributionPool =
      new SyncCommitteeContributionPool(spec, contributionValidator);

  private final PayloadAttestationPool payloadAttestationPool = mock(PayloadAttestationPool.class);

  private final DepositProvider depositProvider = mock(DepositProvider.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final Bytes32 defaultGraffiti = dataStructureUtil.randomBytes32();
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
  private final BLSSignature randaoReveal = dataStructureUtil.randomSignature();

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ExecutionLayerBlockProductionManager executionLayer =
      mock(ExecutionLayerBlockProductionManager.class);
  private final ExecutionPayloadBidManager executionPayloadBidManager =
      mock(ExecutionPayloadBidManager.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);

  private final CapturingBeaconBlockBodyBuilder bodyBuilder =
      new CapturingBeaconBlockBodyBuilder(false, false);

  private final GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(ClientGraffitiAppendFormat.DISABLED);

  private final BlockOperationSelectorFactory factory = createFactory(spec);

  private BlockOperationSelectorFactory createFactory(final Spec factorySpec) {
    return new BlockOperationSelectorFactory(
        factorySpec,
        attestationPool,
        attesterSlashingPool,
        proposerSlashingPool,
        voluntaryExitPool,
        blsToExecutionChangePool,
        contributionPool,
        payloadAttestationPool,
        depositProvider,
        eth1DataCache,
        graffitiBuilder,
        forkChoiceNotifier,
        executionLayer,
        executionPayloadBidManager,
        executionPayloadManager,
        metricsSystem,
        timeProvider);
  }

  private ExecutionPayloadContext executionPayloadContext;

  @BeforeEach
  void setUp() {
    when(attestationPool.getAttestationsForBlock(any(), any()))
        .thenReturn(
            beaconBlockSchemaSupplier.apply(UInt64.ZERO).getAttestationsSchema().getDefault());
    when(contributionValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(attesterSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(proposerSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(voluntaryExitValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(blsToExecutionChangeValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    this.executionPayloadContext = dataStructureUtil.randomPayloadExecutionContext(false);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
  }

  @Test
  void shouldApplyFullParentAvailabilityWhenSelectingGloasAttestations() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(gloasSpec.getGenesisSchemaDefinitions());
    final UInt64 parentSlot = UInt64.valueOf(8);
    final UInt64 blockSlot = parentSlot.plus(2);
    final int parentSlotIndex =
        parentSlot.mod(gloasSpec.getSlotsPerHistoricalRoot(parentSlot)).intValue();
    final BeaconStateGloas blockSlotState =
        BeaconStateGloas.required(
            gloasData
                .randomBeaconState(blockSlot)
                .updated(
                    mutableState -> {
                      final MutableBeaconStateGloas stateGloas =
                          MutableBeaconStateGloas.required(mutableState);
                      stateGloas.setLatestBlockHeader(
                          gloasData.randomBeaconBlockHeader(parentSlot, UInt64.ZERO));
                      stateGloas.setExecutionPayloadAvailability(
                          schemaDefinitions.getExecutionPayloadAvailabilitySchema().getDefault());
                    }));
    when(attestationPool.getAttestationsForBlock(any(), any()))
        .thenReturn(
            schemaDefinitions.getBeaconBlockBodySchema().getAttestationsSchema().getDefault());
    final CapturingBeaconBlockBodyBuilder gloasBodyBuilder =
        new CapturingBeaconBlockBodyBuilder(false) {
          @Override
          public Boolean supportsSyncAggregate() {
            return false;
          }

          @Override
          public Boolean supportsExecutionPayload() {
            return false;
          }

          @Override
          public Boolean supportsBlsToExecutionChanges() {
            return false;
          }
        };
    final BlockProductionContext context =
        new BlockProductionContext(
            blockSlot,
            blockSlotState,
            ForkChoiceNode.createFull(parentRoot),
            blockSlotState.getLatestBlockHash(),
            randaoReveal,
            Optional.empty(),
            false,
            Optional.empty(),
            BlockProductionPerformance.NOOP);

    safeJoin(createFactory(gloasSpec).createSelector(context).apply(gloasBodyBuilder));

    final ArgumentCaptor<BeaconState> stateCaptor = ArgumentCaptor.forClass(BeaconState.class);
    verify(attestationPool).getAttestationsForBlock(stateCaptor.capture(), any());
    final BeaconStateGloas attestationRewardState =
        BeaconStateGloas.required(stateCaptor.getValue());
    assertThat(attestationRewardState).isNotSameAs(blockSlotState);
    assertThat(attestationRewardState.getExecutionPayloadAvailability().getBit(parentSlotIndex))
        .isTrue();
    assertThat(blockSlotState.getExecutionPayloadAvailability().getBit(parentSlotIndex)).isFalse();
  }

  @Test
  void shouldNotSelectOperationsWhenNoneAreAvailable() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).isEmpty();
    assertThat(bodyBuilder.voluntaryExits).isEmpty();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeBits().getBitCount()).isZero();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeSignature().getSignature().isInfinity())
        .isTrue();
    assertThat(bodyBuilder.blsToExecutionChanges).isEmpty();
  }

  @Test
  void shouldNotSelectVoluntaryExitWhenValidatorHasPendingWithdrawal() {
    final UInt64 slot = UInt64.ONE;
    final MutableBeaconStateElectra blockSlotState =
        dataStructureUtil.randomBeaconState(slot).toVersionElectra().get().createWritableCopy();
    blockSlotState
        .getPendingPartialWithdrawals()
        .append(dataStructureUtil.randomPendingPartialWithdrawal(1));
    final SignedVoluntaryExit voluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(1));
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    addToPool(voluntaryExitPool, voluntaryExit);
    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    randaoReveal,
                    Optional.of(defaultGraffiti),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));
    assertThat(bodyBuilder.voluntaryExits).isEmpty();
  }

  @Test
  void shouldNotSelectVoluntaryExitForValidatorWithParentWithdrawalRequest() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final int validatorIndex = 1;
    final SignedVoluntaryExit voluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(validatorIndex));
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    addToPool(voluntaryExitPool, voluntaryExit);
    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    // Build parent execution requests containing a withdrawal request for validator 1
    final WithdrawalRequest withdrawalRequest =
        dataStructureUtil.withdrawalRequest(
            Bytes20.ZERO,
            blockSlotState.getValidators().get(validatorIndex).getPublicKey(),
            UInt64.ZERO);
    final ExecutionRequests parentReqs =
        SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionRequestsSchema()
            .createBuilder()
            .withdrawals(List.of(withdrawalRequest))
            .build();

    final CapturingBeaconBlockBodyBuilder gloasBodyBuilder =
        new CapturingBeaconBlockBodyBuilder(false, false, true);
    when(executionPayloadManager.getParentExecutionRequestsForBlock(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(parentReqs));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    randaoReveal,
                    Optional.of(defaultGraffiti),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(gloasBodyBuilder));

    assertThat(gloasBodyBuilder.voluntaryExits).isEmpty();
  }

  @Test
  void shouldIncludeValidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    final ProposerSlashing proposerSlashing = dataStructureUtil.randomProposerSlashing();
    final AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    addToPool(voluntaryExitPool, voluntaryExit);
    addToPool(proposerSlashingPool, proposerSlashing);
    addToPool(attesterSlashingPool, attesterSlashing);
    assertThat(contributionPool.addLocal(contribution)).isCompletedWithValue(ACCEPT);
    addToPool(blsToExecutionChangePool, blsToExecutionChange);

    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    randaoReveal,
                    Optional.of(defaultGraffiti),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).containsOnly(proposerSlashing);
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing);
    assertThat(bodyBuilder.voluntaryExits).containsOnly(voluntaryExit);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
    assertThat(bodyBuilder.blsToExecutionChanges).containsOnly(blsToExecutionChange);
  }

  private <T extends SszData> void addToPool(final OperationPool<T> pool, final T operation) {
    assertThat(pool.addRemote(operation, Optional.empty())).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldNotIncludeInvalidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit1 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(60));
    final SignedVoluntaryExit voluntaryExit2 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(65));
    final SignedVoluntaryExit voluntaryExit3 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final SignedVoluntaryExit voluntaryExit4 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final ProposerSlashing proposerSlashing1 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(60));
    final ProposerSlashing proposerSlashing2 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(61));
    final AttesterSlashing attesterSlashing1 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(60), UInt64.valueOf(62));
    final AttesterSlashing attesterSlashing2 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(63), UInt64.valueOf(64));
    final AttesterSlashing attesterSlashing3 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(62));
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    final SignedBlsToExecutionChange blsToExecutionChange1 =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final SignedBlsToExecutionChange blsToExecutionChange2 =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    addToPool(voluntaryExitPool, voluntaryExit1);
    addToPool(voluntaryExitPool, voluntaryExit2);
    addToPool(voluntaryExitPool, voluntaryExit3);
    addToPool(voluntaryExitPool, voluntaryExit4);
    addToPool(proposerSlashingPool, proposerSlashing1);
    addToPool(proposerSlashingPool, proposerSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing1);
    addToPool(attesterSlashingPool, attesterSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing3);
    assertThat(contributionPool.addRemote(contribution, Optional.empty()))
        .isCompletedWithValue(ACCEPT);
    addToPool(blsToExecutionChangePool, blsToExecutionChange1);
    addToPool(blsToExecutionChangePool, blsToExecutionChange2);

    when(proposerSlashingValidator.validateForBlockInclusion(blockSlotState, proposerSlashing2))
        .thenReturn(Optional.of(ProposerSlashingInvalidReason.INVALID_SIGNATURE));
    when(voluntaryExitValidator.validateForBlockInclusion(blockSlotState, voluntaryExit2))
        .thenReturn(Optional.of(ExitInvalidReason.invalidSignature()));
    when(attesterSlashingValidator.validateForBlockInclusion(blockSlotState, attesterSlashing2))
        .thenReturn(Optional.of(AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE));
    when(blsToExecutionChangeValidator.validateForBlockInclusion(
            blockSlotState, blsToExecutionChange2))
        .thenReturn(Optional.of(BlsToExecutionChangeInvalidReason.invalidValidatorIndex()));

    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();
    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    randaoReveal,
                    Optional.of(defaultGraffiti),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing1);
    // Both exit 3 or 4 are valid, but you can't include two exits for the same validator
    assertThat(bodyBuilder.voluntaryExits).hasSize(1).containsAnyOf(voluntaryExit3, voluntaryExit4);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
    assertThat(bodyBuilder.blsToExecutionChanges).containsOnly(blsToExecutionChange1);
  }

  @Test
  void shouldIncludeNonDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderIfBlindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    final ExecutionPayloadContext executionPayloadContextWithValidatorRegistration =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(executionPayloadContextWithValidatorRegistration)));

    prepareBlockProductionWithPayloadHeader(
        randomExecutionPayloadHeader,
        executionPayloadContextWithValidatorRegistration,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayloadHeader).isEqualTo(randomExecutionPayloadHeader);
  }

  @Test
  void shouldIncludeExecutionPayloadIfUnblindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadIfRequestedBlindedIsEmpty() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    prepareBlockProductionWithPayload(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        Optional.of(blockExecutionValue));

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadIfRequestedBlindedIsEmptyAndBuilderFlowFallsBack() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    this.executionPayloadContext = dataStructureUtil.randomPayloadExecutionContext(false, true);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));

    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();
    prepareBlindedBlockProductionWithFallBack(
        randomExecutionPayload, executionPayloadContext, blockSlotState, blockExecutionValue);

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void
      shouldIncludeExecutionPayloadAndCommitmentsIfRequestedBlindedIsEmptyAndBuilderFlowFallsBack() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();

    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    final CapturingBeaconBlockBodyBuilder bodyBuilder = new CapturingBeaconBlockBodyBuilder(true);

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    prepareBlindedBlockAndBlobsProductionWithFallBack(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        blobsBundle,
        blockExecutionValue);

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(BeaconStateCache.getSlotCaches(blockSlotState).getBlockExecutionValue())
        .isEqualByComparingTo(blockExecutionValue);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
    assertThat(bodyBuilder.blobKzgCommitments)
        .map(SszKZGCommitment::getKZGCommitment)
        .hasSameElementsAs(blobsBundle.getCommitments());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldUnblindSignedBlindedBeaconBlock(final boolean useLocalFallback) {
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final SignedBeaconBlock blindedSignedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final CapturingBeaconBlockUnblinder blockUnblinder =
        new CapturingBeaconBlockUnblinder(spec.getGenesisSchemaDefinitions(), blindedSignedBlock);

    final BuilderPayloadOrFallbackData builderPayloadOrFallbackData;
    if (useLocalFallback) {
      final FallbackData fallbackData =
          new FallbackData(
              new GetPayloadResponse(randomExecutionPayload), FallbackReason.BUILDER_ERROR);
      builderPayloadOrFallbackData = BuilderPayloadOrFallbackData.create(fallbackData);
    } else {
      builderPayloadOrFallbackData = BuilderPayloadOrFallbackData.create(randomExecutionPayload);
    }
    when(executionLayer.getUnblindedPayload(blindedSignedBlock, BlockPublishingPerformance.NOOP))
        .thenReturn(SafeFuture.completedFuture(builderPayloadOrFallbackData));

    factory.createBlockUnblinderSelector(BlockPublishingPerformance.NOOP).accept(blockUnblinder);

    assertThat(blockUnblinder.executionPayload).isCompletedWithValue(randomExecutionPayload);
  }

  @Test
  void shouldThrowWhenExecutionPayloadContextNotProvided() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThatSafeFuture(
            factory
                .createSelector(
                    blockProductionContext(
                        parentRoot,
                        blockSlotState,
                        dataStructureUtil.randomSignature(),
                        Optional.empty(),
                        Optional.empty(),
                        BlockProductionPerformance.NOOP))
                .apply(bodyBuilder))
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessage(
            "ExecutionPayloadContext is not provided for production of post-merge block at slot 1");
  }

  @Test
  void shouldGetExecutionRequestsForLocallyProducedBlocks() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    final ProposerSlashing proposerSlashing = dataStructureUtil.randomProposerSlashing();
    final AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    final SignedBlsToExecutionChange blsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    addToPool(voluntaryExitPool, voluntaryExit);
    addToPool(proposerSlashingPool, proposerSlashing);
    addToPool(attesterSlashingPool, attesterSlashing);
    assertThat(contributionPool.addLocal(contribution)).isCompletedWithValue(ACCEPT);
    addToPool(blsToExecutionChangePool, blsToExecutionChange);

    final CapturingBeaconBlockBodyBuilder bodyBuilder =
        new CapturingBeaconBlockBodyBuilder(true, true);

    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    final ExecutionRequests expectedExecutionRequests =
        dataStructureUtil.randomExecutionRequests(slot);

    prepareBlockWithBlobsAndExecutionRequestsProduction(
        randomExecutionPayload,
        executionPayloadContext,
        blockSlotState,
        dataStructureUtil.randomBlobsBundle(),
        expectedExecutionRequests,
        blockExecutionValue);

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    randaoReveal,
                    Optional.of(defaultGraffiti),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).containsOnly(proposerSlashing);
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing);
    assertThat(bodyBuilder.voluntaryExits).containsOnly(voluntaryExit);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
    assertThat(bodyBuilder.blsToExecutionChanges).containsOnly(blsToExecutionChange);
    assertThat(bodyBuilder.executionRequests).isEqualTo(expectedExecutionRequests);
  }

  @Test
  void shouldIncludeExecutionRequestsInBlindedBlock() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionRequests executionRequests = dataStructureUtil.randomExecutionRequests(slot);

    final ExecutionPayloadContext executionPayloadContextWithValidatorRegistration =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(executionPayloadContextWithValidatorRegistration)));

    prepareBlindedBlockWithBlobsAndExecutionRequestsProduction(
        dataStructureUtil.randomExecutionPayloadHeader(),
        executionPayloadContextWithValidatorRegistration,
        blockSlotState,
        dataStructureUtil.randomBlobKzgCommitments(),
        executionRequests,
        dataStructureUtil.randomUInt256());

    final CapturingBeaconBlockBodyBuilder bodyBuilder =
        new CapturingBeaconBlockBodyBuilder(true, true);

    safeJoin(
        factory
            .createSelector(
                blockProductionContext(
                    parentRoot,
                    blockSlotState,
                    dataStructureUtil.randomSignature(),
                    Optional.empty(),
                    Optional.empty(),
                    BlockProductionPerformance.NOOP))
            .apply(bodyBuilder));

    assertThat(bodyBuilder.executionRequests).isEqualTo(executionRequests);
  }

  private void prepareBlockProductionWithPayload(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final Optional<UInt256> executionPayloadValue) {
    final GetPayloadResponse getPayloadResponse =
        executionPayloadValue
            .map(value -> new GetPayloadResponse(executionPayload, value))
            .orElse(new GetPayloadResponse(executionPayload));
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            false,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForLocalFlow(
                executionPayloadContext, SafeFuture.completedFuture(getPayloadResponse)));
  }

  private void prepareBlockProductionWithPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final Optional<UInt256> executionPayloadValue) {
    final BuilderBid builderBid =
        dataStructureUtil.randomBuilderBid(
            builder -> {
              builder.header(executionPayloadHeader);
              executionPayloadValue.ifPresent(builder::value);
            });
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForBuilderFlow(
                executionPayloadContext,
                SafeFuture.completedFuture(BuilderBidOrFallbackData.create(builderBid))));
  }

  private void prepareBlindedBlockProductionWithFallBack(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final UInt256 executionPayloadValue) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            new FallbackData(
                new GetPayloadResponse(executionPayload, executionPayloadValue),
                FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE));
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForBuilderFlow(
                executionPayloadContext, SafeFuture.completedFuture(builderBidOrFallbackData)));
  }

  private void prepareBlockWithBlobsAndExecutionRequestsProduction(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final BlobsBundle blobsBundle,
      final ExecutionRequests executionRequests,
      final UInt256 executionPayloadValue) {
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            false,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForLocalFlow(
                executionPayloadContext,
                SafeFuture.completedFuture(
                    new GetPayloadResponse(
                        executionPayload,
                        executionPayloadValue,
                        blobsBundle,
                        false,
                        executionRequests))));
  }

  private void prepareBlindedBlockWithBlobsAndExecutionRequestsProduction(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final ExecutionRequests executionRequests,
      final UInt256 executionPayloadValue) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            dataStructureUtil.randomBuilderBid(
                builder -> {
                  builder.header(executionPayloadHeader);
                  builder.blobKzgCommitments(blobKzgCommitments);
                  builder.executionRequests(executionRequests);
                  builder.value(executionPayloadValue);
                }));
    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForBuilderFlow(
                executionPayloadContext, SafeFuture.completedFuture(builderBidOrFallbackData)));
  }

  private void prepareBlindedBlockAndBlobsProductionWithFallBack(
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final BlobsBundle blobsBundle,
      final UInt256 executionPayloadValue) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            new FallbackData(
                new GetPayloadResponse(executionPayload, executionPayloadValue, blobsBundle, false),
                FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE));

    when(executionLayer.initiateBlockProduction(
            executionPayloadContext,
            blockSlotState,
            true,
            Optional.empty(),
            BlockProductionPerformance.NOOP))
        .thenReturn(
            ExecutionPayloadResult.createForBuilderFlow(
                executionPayloadContext, SafeFuture.completedFuture(builderBidOrFallbackData)));
  }

  static class CapturingBeaconBlockBodyBuilder implements BeaconBlockBodyBuilder {

    private final boolean supportsKzgCommitments;
    private final boolean supportExecutionRequests;
    private final boolean supportsParentExecutionRequests;

    protected BLSSignature randaoReveal;
    protected Bytes32 graffiti;
    protected SszList<ProposerSlashing> proposerSlashings;
    protected SszList<AttesterSlashing> attesterSlashings;
    protected SszList<SignedVoluntaryExit> voluntaryExits;
    protected SszList<SignedBlsToExecutionChange> blsToExecutionChanges;
    protected SyncAggregate syncAggregate;
    protected ExecutionPayload executionPayload;
    protected ExecutionPayloadHeader executionPayloadHeader;
    protected SszList<SszKZGCommitment> blobKzgCommitments;
    protected ExecutionRequests executionRequests;

    public CapturingBeaconBlockBodyBuilder(final boolean supportsKzgCommitments) {
      this.supportsKzgCommitments = supportsKzgCommitments;
      this.supportExecutionRequests = false;
      this.supportsParentExecutionRequests = false;
    }

    public CapturingBeaconBlockBodyBuilder(
        final boolean supportsKzgCommitments, final boolean supportExecutionRequests) {
      this.supportsKzgCommitments = supportsKzgCommitments;
      this.supportExecutionRequests = supportExecutionRequests;
      this.supportsParentExecutionRequests = false;
    }

    public CapturingBeaconBlockBodyBuilder(
        final boolean supportsKzgCommitments,
        final boolean supportExecutionRequests,
        final boolean supportsParentExecutionRequests) {
      this.supportsKzgCommitments = supportsKzgCommitments;
      this.supportExecutionRequests = supportExecutionRequests;
      this.supportsParentExecutionRequests = supportsParentExecutionRequests;
    }

    @Override
    public BeaconBlockBodyBuilder randaoReveal(final BLSSignature randaoReveal) {
      this.randaoReveal = randaoReveal;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder eth1Data(final Eth1Data eth1Data) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder graffiti(final Bytes32 graffiti) {
      this.graffiti = graffiti;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attestations(final SszList<Attestation> attestations) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder proposerSlashings(
        final SszList<ProposerSlashing> proposerSlashings) {
      this.proposerSlashings = proposerSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attesterSlashings(
        final SszList<AttesterSlashing> attesterSlashings) {
      this.attesterSlashings = attesterSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder deposits(final SszList<Deposit> deposits) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder voluntaryExits(
        final SszList<SignedVoluntaryExit> voluntaryExits) {
      this.voluntaryExits = voluntaryExits;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder syncAggregate(final SyncAggregate syncAggregate) {
      this.syncAggregate = syncAggregate;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayload(final ExecutionPayload executionPayload) {
      this.executionPayload = executionPayload;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayloadHeader(
        final ExecutionPayloadHeader executionPayloadHeader) {
      this.executionPayloadHeader = executionPayloadHeader;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder blsToExecutionChanges(
        final SszList<SignedBlsToExecutionChange> blsToExecutionChanges) {
      this.blsToExecutionChanges = blsToExecutionChanges;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionRequests(final ExecutionRequests executionRequests) {
      this.executionRequests = executionRequests;
      return this;
    }

    @Override
    public Boolean supportsSyncAggregate() {
      return true;
    }

    @Override
    public Boolean supportsExecutionPayload() {
      return true;
    }

    @Override
    public Boolean supportsBlsToExecutionChanges() {
      return true;
    }

    @Override
    public Boolean supportsKzgCommitments() {
      return supportsKzgCommitments;
    }

    @Override
    public boolean supportsExecutionRequests() {
      return supportExecutionRequests;
    }

    @Override
    public Boolean supportsParentExecutionRequests() {
      return supportsParentExecutionRequests;
    }

    @Override
    public BeaconBlockBodyBuilder blobKzgCommitments(
        final SszList<SszKZGCommitment> blobKzgCommitments) {
      this.blobKzgCommitments = blobKzgCommitments;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder signedExecutionPayloadBid(
        final SignedExecutionPayloadBid signedExecutionPayloadBid) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder payloadAttestations(
        final SszList<PayloadAttestation> payloadAttestations) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder parentExecutionRequests(
        final ExecutionRequests parentExecutionRequests) {
      return this;
    }

    @Override
    public BeaconBlockBody build() {
      return null;
    }
  }

  private static class CapturingBeaconBlockUnblinder extends AbstractSignedBeaconBlockUnblinder {

    protected SafeFuture<ExecutionPayload> executionPayload;

    public CapturingBeaconBlockUnblinder(
        final SchemaDefinitions schemaDefinitions,
        final SignedBeaconBlock signedBlindedBeaconBlock) {
      super(schemaDefinitions, signedBlindedBeaconBlock);
    }

    @Override
    public void setExecutionPayloadSupplier(
        final Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
      this.executionPayload = executionPayloadSupplier.get();
    }

    @Override
    public SafeFuture<Optional<SignedBeaconBlock>> unblind() {
      return null;
    }
  }
}
