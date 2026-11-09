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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.validator.coordinator.BlockOperationSelectorFactoryTest.CapturingBeaconBlockBodyBuilder;
import static tech.pegasys.teku.validator.coordinator.BlockProductionTestUtil.blockProductionContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundleDeneb;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
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

class BlockOperationSelectorFactoryDenebTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
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

  private final GraffitiBuilder graffitiBuilder =
      new GraffitiBuilder(ClientGraffitiAppendFormat.DISABLED);

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
  void shouldIncludeKzgCommitmentsInBlindedBlock() {
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState();

    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();

    final UInt256 blockExecutionValue = dataStructureUtil.randomUInt256();

    final SszList<SszKZGCommitment> blobKzgCommitments =
        dataStructureUtil.randomBlobKzgCommitments();

    final ExecutionPayloadContext executionPayloadContextWithValidatorRegistration =
        dataStructureUtil.randomPayloadExecutionContext(false, true);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(executionPayloadContextWithValidatorRegistration)));

    prepareBlindedBlockAndBlobsProduction(
        randomExecutionPayloadHeader,
        executionPayloadContextWithValidatorRegistration,
        blockSlotState,
        blobKzgCommitments,
        blockExecutionValue);

    final CapturingBeaconBlockBodyBuilder bodyBuilder = new CapturingBeaconBlockBodyBuilder(true);

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
    assertThat(bodyBuilder.blobKzgCommitments).hasSameElementsAs(blobKzgCommitments);
  }

  @Test
  void shouldGetBlobsBundleForLocallyProducedBlocks() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();

    final BlobsBundle expectedBlobsBundle = dataStructureUtil.randomBlobsBundle();

    // the BlobsBundle is stored in the ExecutionPayloadResult
    prepareCachedPayloadResult(
        block.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        dataStructureUtil.randomPayloadExecutionContext(false),
        expectedBlobsBundle);

    final BlobsBundle blobsBundle = safeJoin(factory.createBlobsBundleSelector().apply(block));

    assertThat(blobsBundle).isEqualTo(expectedBlobsBundle);
  }

  @Test
  void shouldGetBlobsBundleForLocallyProducedBlocksViaFallback() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock();

    final BlobsBundle expectedBlobsBundle = dataStructureUtil.randomBlobsBundle();

    // the BlobsBundle is stored in the header with fallback
    prepareCachedPayloadHeaderWithFallbackResult(
        block.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        dataStructureUtil.randomPayloadExecutionContext(false),
        expectedBlobsBundle);

    final BlobsBundle blobsBundle = safeJoin(factory.createBlobsBundleSelector().apply(block));

    assertThat(blobsBundle).isEqualTo(expectedBlobsBundle);
  }

  @Test
  void shouldCreateBlobSidecarsForBlockContents() {
    final SignedBlockContainer signedBlockContents = dataStructureUtil.randomSignedBlockContents();

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.atSlot(signedBlockContents.getSlot()).miscHelpers());

    final List<BlobSidecar> blobSidecars =
        factory.createBlobSidecarsSelector().apply(signedBlockContents);

    final SszList<Blob> expectedBlobs = signedBlockContents.getBlobs().orElseThrow();
    final SszList<SszKZGProof> expectedProofs = signedBlockContents.getKzgProofs().orElseThrow();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlockContents
            .getSignedBlock()
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(blobSidecars).hasSize(expectedBlobs.size());

    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              assertThat(blobSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(blobSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlockContents.getSignedBlock().asHeader());
              assertThat(blobSidecar.getBlob()).isEqualTo(expectedBlobs.get(index));
              assertThat(blobSidecar.getSszKZGProof()).isEqualTo(expectedProofs.get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
              // verify the merkle proof
              assertThat(miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(blobSidecar))
                  .isTrue();
            });
  }

  @Test
  void shouldFailCreatingBlobSidecarsIfBuilderBlobsBundleCommitmentsRootIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        dataStructureUtil.randomBuilderBlobsBundle(3);

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundle);

    assertThatThrownBy(() -> factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Commitments in the builder BlobsBundle don't match the commitments in the block");
  }

  @Test
  void shouldFailCreatingBlobSidecarsIfBuilderBlobsBundleProofsIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        spy(dataStructureUtil.randomBuilderBlobsBundle(commitments));
    when(blobsBundle.getBlobs()).thenReturn(dataStructureUtil.randomSszBlobs(2));

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundle);

    assertThatThrownBy(() -> factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The number of blobs in the builder BlobsBundle doesn't match the number of commitments in the block");
  }

  @Test
  void shouldFailCreatingBlobSidecarsIfBuilderBlobsBundleBlobsIsNotConsistent() {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);

    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        spy(dataStructureUtil.randomBuilderBlobsBundle(commitments));
    when(blobsBundle.getProofs()).thenReturn(dataStructureUtil.randomSszKZGProofs(2));

    prepareCachedBuilderPayload(
        signedBlindedBeaconBlock.getSlot(),
        dataStructureUtil.randomExecutionPayload(),
        blobsBundle);

    assertThatThrownBy(() -> factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The number of proofs in the builder BlobsBundle is not consistent with the number of commitments in the block");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldCreateBlobSidecarsForBlindedBlock(final boolean useLocalFallback) {
    final SszList<SszKZGCommitment> commitments = dataStructureUtil.randomBlobKzgCommitments(3);
    final SignedBeaconBlock signedBlindedBeaconBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlockWithCommitments(commitments);
    final UInt64 slot = signedBlindedBeaconBlock.getSlot();

    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        dataStructureUtil.randomBuilderBlobsBundle(commitments);

    if (useLocalFallback) {
      final BlobsBundle localFallbackBlobsBundle =
          new BlobsBundleDeneb(
              blobsBundle.getCommitments().stream()
                  .map(SszKZGCommitment::getKZGCommitment)
                  .toList(),
              blobsBundle.getProofs().stream().map(SszKZGProof::getKZGProof).toList(),
              blobsBundle.getBlobs().stream().toList());
      prepareCachedFallbackData(slot, executionPayload, localFallbackBlobsBundle);
    } else {

      prepareCachedBuilderPayload(slot, executionPayload, blobsBundle);
    }

    final List<BlobSidecar> blobSidecars =
        factory.createBlobSidecarsSelector().apply(signedBlindedBeaconBlock);

    final SszList<Blob> expectedBlobs = blobsBundle.getBlobs();
    final SszList<SszKZGProof> expectedProofs = blobsBundle.getProofs();
    final SszList<SszKZGCommitment> expectedCommitments =
        signedBlindedBeaconBlock
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.atSlot(slot).miscHelpers());

    assertThat(blobSidecars).hasSize(expectedBlobs.size());
    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              assertThat(blobSidecar.getIndex()).isEqualTo(UInt64.valueOf(index));
              assertThat(blobSidecar.getSignedBeaconBlockHeader())
                  .isEqualTo(signedBlindedBeaconBlock.asHeader());
              assertThat(blobSidecar.getBlob()).isEqualTo(expectedBlobs.get(index));
              assertThat(blobSidecar.getSszKZGProof()).isEqualTo(expectedProofs.get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
              // verify the merkle proof
              assertThat(miscHelpersDeneb.verifyBlobKzgCommitmentInclusionProof(blobSidecar))
                  .isTrue();
            });
  }

  private void prepareBlindedBlockAndBlobsProduction(
      final ExecutionPayloadHeader executionPayloadHeader,
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState blockSlotState,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final UInt256 executionPayloadValue) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            dataStructureUtil.randomBuilderBid(
                builder -> {
                  builder.header(executionPayloadHeader);
                  builder.blobKzgCommitments(blobKzgCommitments);
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

  private void prepareCachedPayloadResult(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BlobsBundle blobsBundle) {
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                ExecutionPayloadResult.createForLocalFlow(
                    executionPayloadContext,
                    SafeFuture.completedFuture(
                        new GetPayloadResponse(
                            executionPayload, UInt256.ZERO, blobsBundle, false)))));
  }

  private void prepareCachedPayloadHeaderWithFallbackResult(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final ExecutionPayloadContext executionPayloadContext,
      final BlobsBundle blobsBundle) {
    final BuilderBidOrFallbackData builderBidOrFallbackData =
        BuilderBidOrFallbackData.create(
            new FallbackData(
                new GetPayloadResponse(executionPayload, UInt256.ZERO, blobsBundle, false),
                FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE));
    when(executionLayer.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                ExecutionPayloadResult.createForBuilderFlow(
                    executionPayloadContext,
                    SafeFuture.completedFuture(builderBidOrFallbackData))));
  }

  private void prepareCachedBuilderPayload(
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle) {
    final BuilderPayload builderPayload =
        SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadAndBlobsBundleSchema()
            .create(executionPayload, blobsBundle);
    when(executionLayer.getCachedUnblindedPayload(slot))
        .thenReturn(Optional.of(BuilderPayloadOrFallbackData.create(builderPayload)));
  }

  private void prepareCachedFallbackData(
      final UInt64 slot, final ExecutionPayload executionPayload, final BlobsBundle blobsBundle) {
    when(executionLayer.getCachedUnblindedPayload(slot))
        .thenReturn(
            Optional.of(
                BuilderPayloadOrFallbackData.create(
                    new FallbackData(
                        new GetPayloadResponse(executionPayload, UInt256.ZERO, blobsBundle, false),
                        FallbackReason.BUILDER_ERROR))));
  }
}
