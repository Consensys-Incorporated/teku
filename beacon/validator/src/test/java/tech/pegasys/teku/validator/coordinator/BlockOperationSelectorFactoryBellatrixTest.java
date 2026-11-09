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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactoryTest.CapturingBeaconBlockBodyBuilder;
import static tech.pegasys.teku.validator.coordinator.BlockProductionTestUtil.blockProductionContext;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
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

class BlockOperationSelectorFactoryBellatrixTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
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
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ExecutionLayerBlockProductionManager executionLayer =
      mock(ExecutionLayerBlockProductionManager.class);
  private final ExecutionPayloadBidManager executionPayloadBidManager =
      mock(ExecutionPayloadBidManager.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);

  private final CapturingBeaconBlockBodyBuilder bodyBuilder =
      new CapturingBeaconBlockBodyBuilder(false, false) {
        @Override
        public Boolean supportsBlsToExecutionChanges() {
          return false;
        }
      };

  private final GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(ClientGraffitiAppendFormat.DISABLED);

  private final ExecutionPayload defaultExecutionPayload =
      SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
          .getExecutionPayloadSchema()
          .getDefault();

  private final BlockOperationSelectorFactory factory =
      new BlockOperationSelectorFactory(
          spec,
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
  void shouldIncludeDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconStatePreMerge(slot);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

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
    assertThat(bodyBuilder.executionPayload).isEqualTo(defaultExecutionPayload);
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
}
