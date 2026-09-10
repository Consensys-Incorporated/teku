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

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.COMPLETE;
import static tech.pegasys.teku.kzg.KZG.CELLS_PER_EXT_BLOB;
import static tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELManagerImpl.DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;

public class BlockOperationSelectorFactory {
  private final Spec spec;
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;
  private final SyncCommitteeContributionPool contributionPool;
  private final PayloadAttestationPool payloadAttestationPool;

  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final GraffitiBuilder graffitiBuilder;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;
  private final MetricsHistogram dataColumnSidecarComputationTimeSeconds;
  private final ExecutionPayloadBidManager executionPayloadBidManager;
  private final ExecutionPayloadManager executionPayloadManager;

  public BlockOperationSelectorFactory(
      final Spec spec,
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool,
      final SyncCommitteeContributionPool contributionPool,
      final PayloadAttestationPool payloadAttestationPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final GraffitiBuilder graffitiBuilder,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager,
      final ExecutionPayloadBidManager executionPayloadBidManager,
      final ExecutionPayloadManager executionPayloadManager,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    this.spec = spec;
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.blsToExecutionChangePool = blsToExecutionChangePool;
    this.contributionPool = contributionPool;
    this.payloadAttestationPool = payloadAttestationPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffitiBuilder = graffitiBuilder;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
    this.executionPayloadBidManager = executionPayloadBidManager;
    this.executionPayloadManager = executionPayloadManager;
    this.dataColumnSidecarComputationTimeSeconds =
        DATA_COLUMN_SIDECAR_COMPUTATION_HISTOGRAM.apply(metricsSystem, timeProvider);
  }

  public Function<BeaconBlockBodyBuilder, SafeFuture<Void>> createSelector(
      final BlockProductionContext blockProductionContext) {

    final BeaconState blockSlotState = blockProductionContext.blockSlotState();
    final Bytes32 parentRoot = blockProductionContext.parentRoot();
    final SpecConfig specConfig = spec.atSlot(blockSlotState.getSlot()).getConfig();

    return bodyBuilder -> {
      blockProductionContext.blockProductionPerformance().beaconBlockBodyPreparationStarted();

      final SchemaDefinitions schemaDefinitions =
          spec.atSlot(blockSlotState.getSlot()).getSchemaDefinitions();

      final SafeFuture<Void> setExecutionData;

      // In `setExecutionData` the following fields are set:
      // Post-Bellatrix: Execution Payload / Execution Payload Header
      // Post-Deneb: KZG Commitments
      // Post-Gloas: this section is skipped
      if (bodyBuilder.supportsExecutionPayload()) {
        setExecutionData =
            forkChoiceNotifier
                .getPayloadId(
                    blockProductionContext.parentForkChoiceNode(), blockSlotState.getSlot())
                .thenCompose(
                    executionPayloadContext ->
                        setExecutionData(
                            executionPayloadContext,
                            bodyBuilder,
                            SchemaDefinitionsBellatrix.required(schemaDefinitions),
                            blockProductionContext));
      } else {
        setExecutionData = COMPLETE;
      }

      final SafeFuture<Void> setExecutionPayloadBid;

      // Post-Gloas: Signed Execution Payload Bid
      if (bodyBuilder.supportsSignedExecutionPayloadBid()) {
        setExecutionPayloadBid =
            forkChoiceNotifier
                .getPayloadId(
                    blockProductionContext.parentForkChoiceNode(), blockSlotState.getSlot())
                .thenCompose(
                    executionPayloadContext ->
                        setExecutionPayloadBid(
                            parentRoot,
                            executionPayloadContext,
                            bodyBuilder,
                            blockProductionContext));

      } else {
        setExecutionPayloadBid = COMPLETE;
      }

      final Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockSlotState);

      final BeaconState attestationRewardState =
          spec.getStateForAttestationRewardCalculation(
              blockSlotState,
              blockProductionContext.parentPayloadStatus()
                  == ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL);
      final SszList<Attestation> attestations =
          attestationPool.getAttestationsForBlock(
              attestationRewardState, new AttestationForkChecker(spec, blockSlotState));
      blockProductionContext.blockProductionPerformance().getAttestationsForBlock();

      // Collect slashings to include
      final Set<UInt64> exitedValidators = new HashSet<>();
      final SszList<AttesterSlashing> attesterSlashings =
          attesterSlashingPool.getItemsForBlock(
              blockSlotState,
              getMaxAttesterSlashings(specConfig),
              slashing -> !exitedValidators.containsAll(slashing.getIntersectingValidatorIndices()),
              slashing -> exitedValidators.addAll(slashing.getIntersectingValidatorIndices()));

      final SszList<ProposerSlashing> proposerSlashings =
          proposerSlashingPool.getItemsForBlock(
              blockSlotState,
              specConfig.getMaxProposerSlashings(),
              slashing ->
                  !exitedValidators.contains(slashing.getHeader1().getMessage().getProposerIndex()),
              slashing ->
                  exitedValidators.add(slashing.getHeader1().getMessage().getProposerIndex()));

      final SafeFuture<Void> setVoluntaryExitsAndParentExecutionRequests;

      // In Gloas, parent withdrawal request targeting a validator can invalidate a voluntary exit
      // for this validator in the same block.
      if (bodyBuilder.supportsParentExecutionRequests()) {
        setVoluntaryExitsAndParentExecutionRequests =
            executionPayloadManager
                .getParentExecutionRequestsForBlock(
                    blockSlotState.getSlot(),
                    parentRoot,
                    blockProductionContext.parentPayloadStatus())
                .thenAccept(
                    parentExecutionRequests -> {
                      final Set<UInt64> validatorsWithParentWithdrawalRequests = new HashSet<>();
                      for (final WithdrawalRequest withdrawalRequest :
                          parentExecutionRequests.getWithdrawals()) {
                        spec.getValidatorIndex(
                                blockSlotState, withdrawalRequest.getValidatorPubkey())
                            .ifPresent(
                                idx ->
                                    validatorsWithParentWithdrawalRequests.add(
                                        UInt64.valueOf(idx)));
                      }
                      final SszList<SignedVoluntaryExit> voluntaryExits =
                          getVoluntaryExitsForBlock(
                              blockSlotState,
                              specConfig.getMaxVoluntaryExits(),
                              exitedValidators,
                              validatorsWithParentWithdrawalRequests);
                      bodyBuilder.voluntaryExits(voluntaryExits);
                      // Post-Gloas: Parent Execution Requests
                      bodyBuilder.parentExecutionRequests(parentExecutionRequests);
                    });
      } else {
        final SszList<SignedVoluntaryExit> voluntaryExits =
            getVoluntaryExitsForBlock(
                blockSlotState,
                specConfig.getMaxVoluntaryExits(),
                exitedValidators,
                new HashSet<>());
        bodyBuilder.voluntaryExits(voluntaryExits);
        setVoluntaryExitsAndParentExecutionRequests = COMPLETE;
      }

      bodyBuilder
          .randaoReveal(blockProductionContext.randaoReveal())
          .eth1Data(eth1Data)
          .graffiti(graffitiBuilder.buildGraffiti(blockProductionContext.graffiti()))
          .attestations(attestations)
          .proposerSlashings(proposerSlashings)
          .attesterSlashings(attesterSlashings)
          .deposits(depositProvider.getDeposits(blockSlotState, eth1Data));

      // Optional fields introduced in later forks

      // Post-Altair: Sync aggregate
      if (bodyBuilder.supportsSyncAggregate()) {
        bodyBuilder.syncAggregate(
            contributionPool.createSyncAggregateForBlock(blockSlotState.getSlot(), parentRoot));
      }

      // Post-Capella: BLS to Execution changes
      if (bodyBuilder.supportsBlsToExecutionChanges()) {
        bodyBuilder.blsToExecutionChanges(
            blsToExecutionChangePool.getItemsForBlock(
                blockSlotState,
                SpecConfigCapella.required(specConfig).getMaxBlsToExecutionChanges()));
      }

      // Post-Gloas: Payload Attestations
      if (bodyBuilder.supportsPayloadAttestations()) {
        bodyBuilder.payloadAttestations(
            payloadAttestationPool.getPayloadAttestationsForBlock(blockSlotState, parentRoot));
      }

      return SafeFuture.allOfFailFast(
              setExecutionData, setExecutionPayloadBid, setVoluntaryExitsAndParentExecutionRequests)
          .thenPeek(
              __ -> blockProductionContext.blockProductionPerformance().beaconBlockBodyPrepared());
    };
  }

  private SszList<SignedVoluntaryExit> getVoluntaryExitsForBlock(
      final BeaconState blockSlotState,
      final int maxVoluntaryExits,
      final Set<UInt64> exitedValidators,
      final Set<UInt64> validatorsWithParentWithdrawalRequests) {
    return voluntaryExitPool.getItemsForBlock(
        blockSlotState,
        maxVoluntaryExits,
        exit ->
            voluntaryExitPredicate(
                blockSlotState, exitedValidators, exit, validatorsWithParentWithdrawalRequests),
        exit -> exitedValidators.add(exit.getMessage().getValidatorIndex()));
  }

  private static int getMaxAttesterSlashings(final SpecConfig specConfig) {
    return specConfig
        .toVersionElectra()
        .map(SpecConfigElectra::getMaxAttesterSlashingsElectra)
        .orElseGet(specConfig::getMaxAttesterSlashings);
  }

  private boolean voluntaryExitPredicate(
      final BeaconState blockSlotState,
      final Set<UInt64> exitedValidators,
      final SignedVoluntaryExit exit,
      final Set<UInt64> validatorsWithParentWithdrawalRequests) {
    final UInt64 validatorIndex = exit.getMessage().getValidatorIndex();
    if (exitedValidators.contains(validatorIndex)) {
      return false;
    }
    // In Gloas, a withdrawal request for this validator would call initiate_validator_exit or add a
    // pending partial withdrawal, either of which would invalidate this voluntary exit.
    if (validatorsWithParentWithdrawalRequests.contains(validatorIndex)) {
      return false;
    }
    // if there is a pending withdrawal, the exit is not valid for inclusion in a block.
    return blockSlotState
        .toVersionElectra()
        .map(
            beaconStateElectra ->
                beaconStateElectra.getPendingPartialWithdrawals().stream()
                    .map(PendingPartialWithdrawal::getValidatorIndex)
                    .noneMatch(index -> index.equals(validatorIndex)))
        .orElse(true);
  }

  private SafeFuture<Void> setExecutionData(
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitionsBellatrix schemaDefinitions,
      final BlockProductionContext blockProductionContext) {
    final BeaconState blockSlotState = blockProductionContext.blockSlotState();

    if (spec.isMergeTransitionComplete(blockSlotState) && executionPayloadContext.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "ExecutionPayloadContext is not provided for production of post-merge block at slot %s",
              blockSlotState.getSlot()));
    }

    // pre-Merge Execution Payload / Execution Payload Header
    if (executionPayloadContext.isEmpty()) {
      bodyBuilder.executionPayload(schemaDefinitions.getExecutionPayloadSchema().getDefault());
      return COMPLETE;
    }

    // We should run Builder flow (blinded) only if we have a validator registration
    final boolean shouldTryBuilderFlow =
        executionPayloadContext
            .map(ExecutionPayloadContext::isValidatorRegistrationPresent)
            .orElse(false);

    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager.initiateBlockProduction(
            executionPayloadContext.orElseThrow(),
            blockSlotState,
            shouldTryBuilderFlow,
            blockProductionContext.builderConfig().map(BuilderConfig::getBuilderBoostFactor),
            blockProductionContext.blockProductionPerformance());

    return SafeFuture.allOf(
        cacheExecutionPayloadValue(executionPayloadResult, blockSlotState),
        setPayloadOrPayloadHeader(bodyBuilder, executionPayloadResult),
        setKzgCommitments(bodyBuilder, schemaDefinitions, executionPayloadResult),
        setExecutionRequests(bodyBuilder, executionPayloadResult));
  }

  private SafeFuture<Void> cacheExecutionPayloadValue(
      final ExecutionPayloadResult executionPayloadResult, final BeaconState blockSlotState) {
    return executionPayloadResult
        .getExecutionPayloadValueFuture()
        .thenAccept(
            blockExecutionValue ->
                BeaconStateCache.getSlotCaches(blockSlotState)
                    .setBlockExecutionValue(blockExecutionValue));
  }

  private SafeFuture<Void> setPayloadOrPayloadHeader(
      final BeaconBlockBodyBuilder bodyBuilder,
      final ExecutionPayloadResult executionPayloadResult) {

    if (executionPayloadResult.isFromLocalFlow()) {
      // local, non-blinded flow
      return executionPayloadResult
          .getExecutionPayloadFutureFromLocalFlow()
          .orElseThrow()
          .thenAccept(bodyBuilder::executionPayload);
    }
    // builder, blinded flow
    return executionPayloadResult
        .getBuilderBidOrFallbackDataFuture()
        .orElseThrow()
        .thenAccept(
            builderBidOrFallbackData -> {
              // we should try to return unblinded content only if no explicit "blindness" has been
              // requested and builder flow fallbacks to local
              if (builderBidOrFallbackData.getFallbackData().isPresent()) {
                bodyBuilder.executionPayload(
                    builderBidOrFallbackData.getFallbackDataRequired().getExecutionPayload());
              } else {
                final ExecutionPayloadHeader executionPayloadHeader =
                    builderBidOrFallbackData
                        .getBuilderBid()
                        // from the builder bid
                        .map(BuilderBid::getHeader)
                        // from the local fallback
                        .orElseThrow();
                bodyBuilder.executionPayloadHeader(executionPayloadHeader);
              }
            });
  }

  private SafeFuture<Void> setKzgCommitments(
      final BeaconBlockBodyBuilder bodyBuilder,
      final SchemaDefinitions schemaDefinitions,
      final ExecutionPayloadResult executionPayloadResult) {
    if (!bodyBuilder.supportsKzgCommitments()) {
      return COMPLETE;
    }
    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobKzgCommitmentsSchema();
    final SafeFuture<SszList<SszKZGCommitment>> blobKzgCommitments;
    if (executionPayloadResult.isFromLocalFlow()) {
      // local, non-blinded flow
      blobKzgCommitments =
          executionPayloadResult
              .getBlobsBundleFutureFromLocalFlow()
              .orElseThrow()
              .thenApply(Optional::orElseThrow)
              .thenApply(blobKzgCommitmentsSchema::createFromBlobsBundle);
    } else {
      // builder, blinded flow
      blobKzgCommitments =
          executionPayloadResult
              .getBuilderBidOrFallbackDataFuture()
              .orElseThrow()
              .thenApply(
                  builderBidOrFallbackData ->
                      getBlobKzgCommitmentsFromBuilderFlow(
                          builderBidOrFallbackData, blobKzgCommitmentsSchema));
    }

    return blobKzgCommitments.thenAccept(bodyBuilder::blobKzgCommitments);
  }

  private SszList<SszKZGCommitment> getBlobKzgCommitmentsFromBuilderFlow(
      final BuilderBidOrFallbackData builderBidOrFallbackData,
      final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema) {
    return builderBidOrFallbackData
        .getBuilderBid()
        // from the builder bid
        .map(BuilderBid::getOptionalBlobKzgCommitments)
        // from the local fallback
        .orElseGet(
            () ->
                builderBidOrFallbackData
                    .getFallbackDataRequired()
                    .getBlobsBundle()
                    .map(blobKzgCommitmentsSchema::createFromBlobsBundle))
        .orElseThrow();
  }

  private SafeFuture<Void> setExecutionRequests(
      final BeaconBlockBodyBuilder bodyBuilder,
      final ExecutionPayloadResult executionPayloadResult) {
    if (!bodyBuilder.supportsExecutionRequests()) {
      return COMPLETE;
    }
    final SafeFuture<ExecutionRequests> executionRequests;
    if (executionPayloadResult.isFromLocalFlow()) {
      // local, non-blinded flow
      executionRequests =
          executionPayloadResult
              .getExecutionRequestsFutureFromLocalFlow()
              .orElseThrow()
              .thenApply(Optional::orElseThrow);
    } else {
      // builder, blinded flow
      executionRequests =
          executionPayloadResult
              .getBuilderBidOrFallbackDataFuture()
              .orElseThrow()
              .thenApply(this::getExecutionRequestsFromBuilderFlow);
    }

    return executionRequests.thenAccept(bodyBuilder::executionRequests);
  }

  private ExecutionRequests getExecutionRequestsFromBuilderFlow(
      final BuilderBidOrFallbackData builderBidOrFallbackData) {
    return builderBidOrFallbackData
        .getBuilderBid()
        // from the builder bid
        .flatMap(BuilderBid::getOptionalExecutionRequests)
        // from the local fallback
        .or(() -> builderBidOrFallbackData.getFallbackDataRequired().getExecutionRequests())
        .orElseThrow();
  }

  private SafeFuture<Void> setExecutionPayloadBid(
      final Bytes32 parentRoot,
      final Optional<ExecutionPayloadContext> executionPayloadContext,
      final BeaconBlockBodyBuilder bodyBuilder,
      final BlockProductionContext blockProductionContext) {
    final BeaconState blockSlotState = blockProductionContext.blockSlotState();
    checkState(
        executionPayloadContext.isPresent(),
        "ExecutionPayloadContext is not provided for production of block at slot %s",
        blockSlotState.getSlot());
    final ExecutionPayloadResult executionPayloadResult =
        executionLayerBlockProductionManager.initiateBlockProduction(
            executionPayloadContext.get(),
            blockSlotState,
            // builder flow (mev-boost) is not used in Gloas
            false,
            Optional.empty(),
            blockProductionContext.blockProductionPerformance());
    // BuilderConfig is expected post-Gloas and is passed from the VC
    final BuilderConfig builderConfig =
        blockProductionContext
            .builderConfig()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "BuilderConfig is missing for production of block at slot "
                            + blockSlotState.getSlot()));
    return executionPayloadBidManager
        .getBidForBlock(
            parentRoot,
            blockProductionContext.parentExecutionBlockHash(),
            blockSlotState,
            executionPayloadResult.getPayloadResponseFutureFromLocalFlowRequired(),
            builderConfig,
            blockProductionContext.blockProductionPerformance())
        .thenAccept(
            bidForBlock -> {
              bodyBuilder.signedExecutionPayloadBid(bidForBlock.bid());
              // cache execution payload value and builder url
              final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(blockSlotState);
              slotCaches.setBlockExecutionValue(bidForBlock.valueInWei());
              bidForBlock.builderUrl().ifPresent(slotCaches::setBuilderUrl);
            });
  }

  public Consumer<SignedBeaconBlockUnblinder> createBlockUnblinderSelector(
      final BlockPublishingPerformance blockPublishingPerformance) {
    return bodyUnblinder -> {
      final SignedBeaconBlock signedBlindedBlock = bodyUnblinder.getSignedBlindedBeaconBlock();

      final BeaconBlock block = signedBlindedBlock.getMessage();

      if (bodyUnblinder.isVersionFulu()) {
        bodyUnblinder.setCompletionSupplier(
            () ->
                executionLayerBlockProductionManager
                    .getUnblindedPayload(signedBlindedBlock, blockPublishingPerformance)
                    .thenApplyChecked(
                        builderPayloadOrFallbackData -> {
                          checkState(builderPayloadOrFallbackData.isEmptySuccessful());
                          return null;
                        }));
        return;
      }

      if (block
          .getBody()
          .getOptionalExecutionPayloadHeader()
          .orElseThrow()
          .isHeaderOfDefaultPayload()) {
        // Terminal block not reached, provide default payload
        bodyUnblinder.setExecutionPayloadSupplier(
            () ->
                SafeFuture.completedFuture(
                    SchemaDefinitionsBellatrix.required(
                            spec.atSlot(block.getSlot()).getSchemaDefinitions())
                        .getExecutionPayloadSchema()
                        .getDefault()));
      } else {
        bodyUnblinder.setExecutionPayloadSupplier(
            () ->
                executionLayerBlockProductionManager
                    .getUnblindedPayload(signedBlindedBlock, blockPublishingPerformance)
                    .thenApply(this::getExecutionPayloadFromBuilderFlow));
      }
    };
  }

  private ExecutionPayload getExecutionPayloadFromBuilderFlow(
      final BuilderPayloadOrFallbackData builderPayloadOrFallbackData) {
    return builderPayloadOrFallbackData
        .getBuilderPayload()
        // from the builder payload
        .map(BuilderPayload::getExecutionPayload)
        // from the local fallback
        .orElseGet(
            () -> builderPayloadOrFallbackData.getFallbackDataRequired().getExecutionPayload());
  }

  public Function<BeaconBlock, SafeFuture<BlobsBundle>> createBlobsBundleSelector() {
    return block -> {
      final UInt64 slot = block.getSlot();
      final ExecutionPayloadResult executionPayloadResult = getCachedPayloadResultRequired(slot);

      if (executionPayloadResult.isFromLocalFlow()) {
        // we performed a non-blinded flow, so the bundle must be in
        // getBlobsBundleFutureFromNonBlindedFlow
        return executionPayloadResult
            .getBlobsBundleFutureFromLocalFlow()
            .orElseThrow()
            .thenApply(Optional::orElseThrow);
      } else {
        // we performed a blinded flow, so the bundle must be in the FallbackData in
        // getBuilderBidOrFallbackDataFuture
        return executionPayloadResult
            .getBuilderBidOrFallbackDataFuture()
            .orElseThrow()
            .thenApply(
                builderBidOrFallbackData ->
                    builderBidOrFallbackData.getFallbackDataRequired().getBlobsBundle())
            .thenApply(Optional::orElseThrow);
      }
    };
  }

  public ExecutionPayloadResult getCachedPayloadResultRequired(final UInt64 slot) {
    return executionLayerBlockProductionManager
        .getCachedPayloadResult(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "ExecutionPayloadResult hasn't been cached for slot " + slot));
  }

  public Function<SignedBlockContainer, List<BlobSidecar>> createBlobSidecarsSelector() {
    return blockContainer -> {
      final Optional<BlobsAndProofs> maybeBlobsAndProofs =
          getBlobsAndProofs(
              blockContainer,
              (numberOfCommitmentsInBlock, blobsBundle) ->
                  numberOfCommitmentsInBlock == blobsBundle.getProofs().size());
      // Builder is responsible for publishing the blob sidecars to the network
      if (maybeBlobsAndProofs.isEmpty()) {
        return Collections.emptyList();
      }
      final BlobsAndProofs blobsAndProofs = maybeBlobsAndProofs.get();

      final MiscHelpersDeneb miscHelpersDeneb =
          MiscHelpersDeneb.required(spec.atSlot(blockContainer.getSlot()).miscHelpers());

      return IntStream.range(0, blobsAndProofs.blobs.size())
          .mapToObj(
              index ->
                  miscHelpersDeneb.constructBlobSidecar(
                      blockContainer.getSignedBlock(),
                      UInt64.valueOf(index),
                      blobsAndProofs.blobs.get(index),
                      blobsAndProofs.proofs.get(index)))
          .toList();
    };
  }

  public Function<SignedBlockContainer, List<DataColumnSidecar>>
      createDataColumnSidecarsSelector() {
    return blockContainer -> {
      final Optional<BlobsAndProofs> maybeBlobsAndProofs =
          getBlobsAndProofs(
              blockContainer,
              (numberOfCommitmentsInBlock, blobsBundle) ->
                  numberOfCommitmentsInBlock * CELLS_PER_EXT_BLOB
                      == blobsBundle.getProofs().size());
      // Builder is responsible for publishing the data column sidecars to the network
      if (maybeBlobsAndProofs.isEmpty()) {
        return Collections.emptyList();
      }
      final BlobsAndProofs blobsAndProofs = maybeBlobsAndProofs.get();

      final MiscHelpersFulu miscHelpersFulu =
          MiscHelpersFulu.required(spec.atSlot(blockContainer.getSlot()).miscHelpers());
      final SpecConfigFulu specConfigFulu =
          SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());

      final List<BlobAndCellProofs> blobAndCellProofsList =
          IntStream.range(0, blobsAndProofs.blobs.size())
              .mapToObj(
                  index ->
                      new BlobAndCellProofs(
                          blobsAndProofs.blobs.get(index),
                          blobsAndProofs.proofs.stream()
                              .skip((long) index * specConfigFulu.getNumberOfColumns())
                              .limit(specConfigFulu.getNumberOfColumns())
                              .map(SszKZGProof::getKZGProof)
                              .toList()))
              .toList();

      try (MetricsHistogram.Timer ignored = dataColumnSidecarComputationTimeSeconds.startTimer()) {
        return miscHelpersFulu.constructDataColumnSidecars(
            blockContainer.getSignedBlock(), blobAndCellProofsList);
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    };
  }

  private record BlobsAndProofs(SszList<Blob> blobs, SszList<SszKZGProof> proofs) {}

  @FunctionalInterface
  private interface BuilderBlobsBundleProofsVerifier {
    boolean test(
        final int numberOfCommitmentsInBlock,
        final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle);
  }

  private Optional<BlobsAndProofs> getBlobsAndProofs(
      final SignedBlockContainer blockContainer,
      final BuilderBlobsBundleProofsVerifier builderBlobsBundleProofsVerifier) {
    final SszList<Blob> blobs;
    final SszList<SszKZGProof> proofs;

    if (blockContainer.isBlinded()) {
      final UInt64 slot = blockContainer.getSlot();
      // need to use the builder BlobsBundle or the local fallback for the blinded flow, because
      // the blobs and the proofs wouldn't be part of the BlockContainer.
      final BuilderPayloadOrFallbackData builderPayloadOrFallbackData =
          executionLayerBlockProductionManager
              .getCachedUnblindedPayload(slot)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "BuilderPayloadOrFallbackData hasn't been cached for slot " + slot));

      // Builder is responsible for publishing the blobs to the network
      if (builderPayloadOrFallbackData.isEmptySuccessful()) {
        return Optional.empty();
      }

      final Optional<BuilderPayload> maybeBuilderPayload =
          builderPayloadOrFallbackData.getBuilderPayload();

      if (maybeBuilderPayload.isPresent()) {
        // from the builder payload
        final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
            maybeBuilderPayload.get().getOptionalBlobsBundle().orElseThrow();
        // consistency checks because the BlobsBundle comes from an external source (a builder)
        verifyBuilderBlobsBundle(
            blobsBundle, blockContainer.getSignedBlock(), builderBlobsBundleProofsVerifier);
        blobs = blobsBundle.getBlobs();
        proofs = blobsBundle.getProofs();
      } else {
        // from the local fallback
        final BlobsBundle blobsBundle =
            builderPayloadOrFallbackData.getFallbackDataRequired().getBlobsBundle().orElseThrow();
        final BlockContentsSchema<?> blockContentsSchema =
            SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
                .getBlockContentsSchema();
        blobs = blockContentsSchema.getBlobsSchema().createFromElements(blobsBundle.getBlobs());
        proofs =
            blockContentsSchema
                .getKzgProofsSchema()
                .createFromElements(
                    blobsBundle.getProofs().stream().map(SszKZGProof::new).toList());
      }
    } else {
      blobs = blockContainer.getBlobs().orElseThrow();
      proofs = blockContainer.getKzgProofs().orElseThrow();
    }
    return Optional.of(new BlobsAndProofs(blobs, proofs));
  }

  private void verifyBuilderBlobsBundle(
      final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle,
      final SignedBeaconBlock block,
      final BuilderBlobsBundleProofsVerifier builderBlobsBundleProofsVerifier) {
    final SszList<SszKZGCommitment> blockCommitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
    checkState(
        blobsBundle.getCommitments().hashTreeRoot().equals(blockCommitments.hashTreeRoot()),
        "Commitments in the builder BlobsBundle don't match the commitments in the block");
    checkState(
        builderBlobsBundleProofsVerifier.test(blockCommitments.size(), blobsBundle),
        "The number of proofs in the builder BlobsBundle is not consistent with the number of commitments in the block");
    checkState(
        blockCommitments.size() == blobsBundle.getBlobs().size(),
        "The number of blobs in the builder BlobsBundle doesn't match the number of commitments in the block");
  }
}
