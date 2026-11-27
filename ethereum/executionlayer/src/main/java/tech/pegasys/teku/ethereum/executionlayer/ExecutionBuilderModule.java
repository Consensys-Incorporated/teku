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

package tech.pegasys.teku.ethereum.executionlayer;

import static tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl.Source;
import static tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil.getMessageOrSimpleName;
import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;

import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.BuilderBidOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.BuilderPayloadOrFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.FallbackData;
import tech.pegasys.teku.spec.datastructures.execution.FallbackReason;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator;
import tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorFormatter;

public class ExecutionBuilderModule {

  private static final Logger LOG = LogManager.getLogger();

  private final AtomicBoolean latestBuilderAvailability;
  private final ExecutionLayerManagerImpl executionLayerManager;
  private final BuilderBidValidator builderBidValidator;
  private final BuilderCircuitBreaker builderCircuitBreaker;
  private final Optional<BuilderClient> builderClient;
  private final EventLogger eventLogger;
  private final UInt64 builderBidCompareFactor;
  private final boolean useShouldOverrideBuilderFlag;
  private final Spec spec;

  public ExecutionBuilderModule(
      final ExecutionLayerManagerImpl executionLayerManager,
      final Spec spec,
      final BuilderBidValidator builderBidValidator,
      final BuilderCircuitBreaker builderCircuitBreaker,
      final Optional<BuilderClient> builderClient,
      final EventLogger eventLogger,
      final UInt64 builderBidCompareFactor,
      final boolean useShouldOverrideBuilderFlag) {
    this.latestBuilderAvailability = new AtomicBoolean(builderClient.isPresent());
    this.spec = spec;
    this.executionLayerManager = executionLayerManager;
    this.builderBidValidator = builderBidValidator;
    this.builderCircuitBreaker = builderCircuitBreaker;
    this.builderClient = builderClient;
    this.eventLogger = eventLogger;
    this.builderBidCompareFactor = builderBidCompareFactor;
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
  }

  private Optional<SafeFuture<BuilderBidOrFallbackData>> isBuilderFlowViable(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> localGetPayloadResponse) {
    final Optional<SignedValidatorRegistration> validatorRegistration =
        executionPayloadContext.getPayloadBuildingAttributes().validatorRegistration();

    // fallback conditions
    final FallbackReason fallbackReason;
    if (builderClient.isEmpty() && validatorRegistration.isEmpty()) {
      fallbackReason = FallbackReason.NOT_NEEDED;
    } else if (isTransitionNotFinalized(executionPayloadContext)) {
      fallbackReason = FallbackReason.TRANSITION_NOT_FINALIZED;
    } else if (isCircuitBreakerEngaged(state)) {
      fallbackReason = FallbackReason.CIRCUIT_BREAKER_ENGAGED;
    } else if (builderClient.isEmpty()) {
      fallbackReason = FallbackReason.BUILDER_NOT_CONFIGURED;
    } else if (!isBuilderAvailable()) {
      fallbackReason = FallbackReason.BUILDER_NOT_AVAILABLE;
    } else if (validatorRegistration.isEmpty()) {
      fallbackReason = FallbackReason.VALIDATOR_NOT_REGISTERED;
    } else {
      fallbackReason = null;
    }

    if (fallbackReason != null) {
      return Optional.of(
          getResultFromLocalGetPayloadResponse(localGetPayloadResponse, fallbackReason));
    }

    return Optional.empty();
  }

  public SafeFuture<BuilderBidOrFallbackData> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {

    final SafeFuture<GetPayloadResponse> localGetPayloadResponse =
        executionLayerManager
            .engineGetPayloadForFallback(executionPayloadContext, state.getSlot())
            .alwaysRun(blockProductionPerformance::engineGetPayload);

    final Optional<SafeFuture<BuilderBidOrFallbackData>> maybeFallback =
        isBuilderFlowViable(executionPayloadContext, state, localGetPayloadResponse);
    if (maybeFallback.isPresent()) {
      return maybeFallback
          .get()
          .thenPeek(
              builderBidOrFallbackData ->
                  builderBidOrFallbackData
                      .getFallbackData()
                      .ifPresent(this::recordAndLogFallbackToLocallyProducedExecutionData));
    }

    final UInt64 slot = state.getSlot();

    final Optional<SignedValidatorRegistration> validatorRegistration =
        executionPayloadContext.getPayloadBuildingAttributes().validatorRegistration();

    final BLSPublicKey validatorPublicKey =
        validatorRegistration.orElseThrow().getMessage().getPublicKey();

    LOG.trace(
        "calling builderGetHeader(slot={}, pubKey={}, parentHash={})",
        slot,
        validatorPublicKey,
        executionPayloadContext.getParentHash());

    // Ensures we can still propose a builder block even if getPayload fails
    final SafeFuture<Optional<GetPayloadResponse>> safeLocalGetPayloadResponse =
        localGetPayloadResponse.thenApply(Optional::of).exceptionally(__ -> Optional.empty());

    return builderClient
        .orElseThrow()
        .getHeader(slot, validatorPublicKey, executionPayloadContext.getParentHash())
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            signedBuilderBidMaybe ->
                LOG.trace(
                    "builderGetHeader(slot={}, pubKey={}, parentHash={}) -> {}",
                    slot,
                    validatorPublicKey,
                    executionPayloadContext.getParentHash(),
                    signedBuilderBidMaybe))
        .alwaysRun(blockProductionPerformance::builderGetHeader)
        .thenComposeCombined(
            safeLocalGetPayloadResponse,
            (signedBuilderBidMaybe, maybeLocalGetPayloadResponse) -> {
              if (signedBuilderBidMaybe.isEmpty()) {
                return getResultFromLocalGetPayloadResponse(
                    localGetPayloadResponse, FallbackReason.BUILDER_HEADER_NOT_AVAILABLE);
              } else {
                // Treat the shouldOverrideBuilder flag as false if local payload is unavailable
                final boolean shouldOverrideBuilder =
                    maybeLocalGetPayloadResponse
                        .map(GetPayloadResponse::getShouldOverrideBuilder)
                        .orElse(false);
                if (useShouldOverrideBuilderFlag && shouldOverrideBuilder) {
                  return getResultFromLocalGetPayloadResponse(
                      localGetPayloadResponse, FallbackReason.SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE);
                }

                final SignedBuilderBid signedBuilderBid = signedBuilderBidMaybe.get();
                // Treat the local block value as zero if local payload is unavailable
                final UInt256 localBlockValue =
                    maybeLocalGetPayloadResponse
                        .map(GetPayloadResponse::getExecutionPayloadValue)
                        .orElse(UInt256.ZERO);
                final UInt256 builderBidValue = signedBuilderBid.getMessage().getValue();

                logReceivedBuilderBid(signedBuilderBid.getMessage());

                final boolean localPayloadValueWon =
                    calculateIfLocalPayloadWinsAndLog(
                        requestedBuilderBoostFactor, localBlockValue, builderBidValue);

                if (localPayloadValueWon) {
                  return getResultFromLocalGetPayloadResponse(
                      localGetPayloadResponse, FallbackReason.LOCAL_BLOCK_VALUE_WON);
                }

                final Optional<ExecutionPayload> localExecutionPayload =
                    maybeLocalGetPayloadResponse.map(GetPayloadResponse::getExecutionPayload);
                return getResultFromSignedBuilderBid(
                    signedBuilderBidMaybe.get(),
                    state,
                    validatorRegistration.get(),
                    localExecutionPayload,
                    blockProductionPerformance);
              }
            })
        .exceptionallyCompose(
            error -> {
              LOG.error(
                  "Unable to obtain a valid bid from builder. Falling back to local execution engine.",
                  error);
              return getResultFromLocalGetPayloadResponse(
                  localGetPayloadResponse, FallbackReason.BUILDER_ERROR);
            })
        .thenPeek(
            builderBidOrFallbackData ->
                builderBidOrFallbackData
                    .getFallbackData()
                    .ifPresent(this::recordAndLogFallbackToLocallyProducedExecutionData));
  }

  private boolean calculateIfLocalPayloadWinsAndLog(
      final Optional<UInt64> requestedBuilderBoostFactor,
      final UInt256 localBlockValue,
      final UInt256 builderBidValue) {
    final UInt64 builderBoostFactor = requestedBuilderBoostFactor.orElse(builderBidCompareFactor);
    final boolean localValueWins =
        BuilderBoostFactorEvaluator.isLocalValueWinning(
            localBlockValue, builderBidValue, builderBoostFactor);

    logPayloadValueComparisonDetails(
        localValueWins,
        builderBidValue,
        localBlockValue,
        requestedBuilderBoostFactor.isPresent(),
        builderBoostFactor);

    return localValueWins;
  }

  private SafeFuture<BuilderBidOrFallbackData> getResultFromSignedBuilderBid(
      final SignedBuilderBid signedBuilderBid,
      final BeaconState state,
      final SignedValidatorRegistration validatorRegistration,
      final Optional<ExecutionPayload> localExecutionPayload,
      final BlockProductionPerformance blockProductionPerformance) {
    builderBidValidator.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, localExecutionPayload);
    blockProductionPerformance.builderBidValidated();
    final BuilderBid builderBid = signedBuilderBid.getMessage();
    return SafeFuture.completedFuture(BuilderBidOrFallbackData.create(builderBid));
  }

  public SafeFuture<Void> builderRegisterValidators(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations, final UInt64 slot) {
    LOG.trace(
        "calling builderRegisterValidator(slot={},signedValidatorRegistrations={})",
        slot,
        signedValidatorRegistrations);

    if (!isBuilderAvailable()) {
      final String reason;
      if (builderClient.isEmpty()) {
        reason = "builder not configured";
      } else {
        reason = "builder not available";
      }
      return SafeFuture.failedFuture(
          new RuntimeException("Unable to register validators: " + reason));
    }

    return builderClient
        .orElseThrow()
        .registerValidators(slot, signedValidatorRegistrations)
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            __ ->
                LOG.trace(
                    "builderRegisterValidator(slot={},signedValidatorRegistrations={}) -> success",
                    slot,
                    signedValidatorRegistrations));
  }

  public SafeFuture<BuilderPayloadOrFallbackData> builderGetPayload(
      final SignedBeaconBlock signedBeaconBlock,
      final Function<UInt64, Optional<ExecutionPayloadResult>> getPayloadResultFunction) {

    Preconditions.checkArgument(signedBeaconBlock.isBlinded(), "SignedBeaconBlock must be blind");

    final UInt64 slot = signedBeaconBlock.getSlot();

    final Optional<SafeFuture<BuilderBidOrFallbackData>> maybeProcessedSlot =
        getPayloadResultFunction
            .apply(slot)
            .flatMap(ExecutionPayloadResult::getBuilderBidOrFallbackDataFuture);

    if (maybeProcessedSlot.isEmpty()) {
      LOG.warn(
          "Blinded block seems to not be built via either builder or local EL. Trying to unblind it via builder endpoint anyway.");
      return getPayloadFromBuilder(signedBeaconBlock);
    }

    final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture =
        maybeProcessedSlot.get();

    return getPayloadFromBuilderOrFallbackData(signedBeaconBlock, builderBidOrFallbackDataFuture);
  }

  private boolean isTransitionNotFinalized(final ExecutionPayloadContext executionPayloadContext) {
    return executionPayloadContext.getForkChoiceState().finalizedExecutionBlockHash().isZero();
  }

  private boolean isCircuitBreakerEngaged(final BeaconState state) {
    try {
      return builderCircuitBreaker.isEngaged(state);
    } catch (Exception e) {
      if (ExceptionUtil.hasCause(e, InterruptedException.class)) {
        LOG.debug("Shutting down");
      } else {
        LOG.error(
            "Builder circuit breaker engagement failure at slot {}. Acting like it has been engaged.",
            state.getSlot(),
            e);
      }
      return true;
    }
  }

  private SafeFuture<BuilderBidOrFallbackData> getResultFromLocalGetPayloadResponse(
      final SafeFuture<GetPayloadResponse> localGetPayloadResponse, final FallbackReason reason) {
    return localGetPayloadResponse.thenApply(
        getPayloadResponse -> {
          final FallbackData fallbackData = new FallbackData(getPayloadResponse, reason);
          return BuilderBidOrFallbackData.create(fallbackData);
        });
  }

  private SafeFuture<BuilderPayloadOrFallbackData> getPayloadFromBuilder(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    if (spec.atSlot(signedBlindedBeaconBlock.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return getPayloadFromBuilderFulu(signedBlindedBeaconBlock);
    } else {
      return getPayloadFromBuilderPreFulu(signedBlindedBeaconBlock);
    }
  }

  private SafeFuture<BuilderPayloadOrFallbackData> getPayloadFromBuilderPreFulu(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    LOG.trace("calling builderGetPayload(signedBlindedBeaconBlock={})", signedBlindedBeaconBlock);

    return builderClient
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Unable to get payload from builder: builder endpoint not available"))
        .getPayload(signedBlindedBeaconBlock)
        .thenApply(ResponseUnwrapper::unwrapBuilderResponseOrThrow)
        .thenPeek(
            builderPayload -> {
              final ExecutionPayload executionPayload = builderPayload.getExecutionPayload();
              logReceivedBuilderExecutionPayload(executionPayload);
              builderPayload
                  .getOptionalBlobsBundle()
                  .ifPresent(this::logReceivedBuilderBlobsBundle);
              executionLayerManager.recordExecutionPayloadFallbackSource(
                  Source.BUILDER, FallbackReason.NONE);
              LOG.trace(
                  "builderGetPayload(signedBlindedBeaconBlock={}) -> {}",
                  signedBlindedBeaconBlock,
                  builderPayload);
            })
        .thenApply(BuilderPayloadOrFallbackData::create);
  }

  private SafeFuture<BuilderPayloadOrFallbackData> getPayloadFromBuilderFulu(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    LOG.trace("calling builderGetPayloadV2(signedBlindedBeaconBlock={})", signedBlindedBeaconBlock);

    return builderClient
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Unable to get payload from builder: builder endpoint not available"))
        .getPayloadV2(signedBlindedBeaconBlock)
        .whenComplete(
            (__, ex) -> {
              if (ex != null) {
                LOG.trace(
                    "builderGetPayloadV2(signedBlindedBeaconBlock={}) -> failed ({})",
                    signedBlindedBeaconBlock,
                    ex.getMessage());
              } else {
                executionLayerManager.recordExecutionPayloadFallbackSource(
                    Source.BUILDER, FallbackReason.NONE);
                LOG.trace(
                    "builderGetPayloadV2(signedBlindedBeaconBlock={}) -> success",
                    signedBlindedBeaconBlock);
              }
            })
        .thenApply(__ -> BuilderPayloadOrFallbackData.createSuccessful());
  }

  private SafeFuture<BuilderPayloadOrFallbackData> getPayloadFromBuilderOrFallbackData(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final SafeFuture<BuilderBidOrFallbackData> builderBidOrFallbackDataFuture) {
    // note: we don't do any particular consistency check here.
    // the header/payload compatibility check is done by SignedBeaconBlockUnblinder
    // the blobs bundle compatibility check is done by
    // BlockOperationSelectorFactory#createBlobSidecarsSelector
    return builderBidOrFallbackDataFuture.thenCompose(
        builderBidOrFallbackData -> {
          if (builderBidOrFallbackData.getFallbackData().isEmpty()) {
            return getPayloadFromBuilder(signedBlindedBeaconBlock);
          } else {
            final FallbackData fallbackData = builderBidOrFallbackData.getFallbackDataRequired();
            LOG.debug(
                "Using FallbackData to provide unblinded execution data (FallbackReason: {})",
                fallbackData.getReason());
            return SafeFuture.completedFuture(BuilderPayloadOrFallbackData.create(fallbackData));
          }
        });
  }

  boolean isBuilderAvailable() {
    return latestBuilderAvailability.get();
  }

  void updateBuilderAvailability(final UInt64 slot) {
    if (builderClient.isEmpty()
        || spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      return;
    }
    builderClient
        .get()
        .status()
        .finish(
            statusResponse -> {
              if (statusResponse.isFailure()) {
                markBuilderAsNotAvailable(statusResponse.errorMessage());
              } else {
                if (latestBuilderAvailability.compareAndSet(false, true)) {
                  eventLogger.builderIsAvailable();
                }
              }
            },
            throwable -> markBuilderAsNotAvailable(getMessageOrSimpleName(throwable)));
  }

  private void markBuilderAsNotAvailable(final String errorMessage) {
    latestBuilderAvailability.set(false);
    eventLogger.builderIsNotAvailable(errorMessage);
  }

  private void recordAndLogFallbackToLocallyProducedExecutionData(final FallbackData fallbackData) {
    executionLayerManager.recordExecutionPayloadFallbackSource(
        Source.BUILDER_LOCAL_EL_FALLBACK, fallbackData.getReason());
    final Level logLevel =
        fallbackData.getReason() == FallbackReason.NOT_NEEDED ? Level.DEBUG : Level.INFO;
    final ExecutionPayload executionPayload = fallbackData.getExecutionPayload();
    fallbackData
        .getBlobsBundle()
        .ifPresentOrElse(
            blobsBundle ->
                LOG.log(
                    logLevel,
                    "Falling back to locally produced execution payload and blobs bundle (Block Number {}, Block Hash = {}, Blobs = {}, Fallback Reason = {})",
                    executionPayload.getBlockNumber(),
                    executionPayload.getBlockHash(),
                    blobsBundle.getNumberOfBlobs(),
                    fallbackData.getReason()),
            () ->
                LOG.log(
                    logLevel,
                    "Falling back to locally produced execution payload (Block Number {}, Block Hash = {}, Fallback Reason = {})",
                    executionPayload.getBlockNumber(),
                    executionPayload.getBlockHash(),
                    fallbackData.getReason()));
  }

  private void logReceivedBuilderExecutionPayload(final ExecutionPayload executionPayload) {
    LOG.info(
        "Received execution payload from Builder (Block Number = {}, Block Hash = {})",
        executionPayload.getBlockNumber(),
        executionPayload.getBlockHash());
  }

  private void logReceivedBuilderBlobsBundle(final BlobsBundle blobsBundle) {
    LOG.info(
        "Received blobs bundle from Builder (Blobs count = {})", blobsBundle.getNumberOfBlobs());
  }

  private void logReceivedBuilderBid(final BuilderBid builderBid) {
    final ExecutionPayloadHeader payloadHeader = builderBid.getHeader();
    final String blobsLog =
        builderBid
            .getOptionalBlobKzgCommitments()
            .map(blobKzgCommitments -> ", Blobs count = " + blobKzgCommitments.size())
            .orElse("");
    LOG.info(
        "Received Builder Bid (Block Number = {}, Block Hash = {}, MEV Reward (ETH) = {}, Gas Limit = {}, Gas Used = {}{})",
        payloadHeader.getBlockNumber(),
        payloadHeader.getBlockHash(),
        weiToEth(builderBid.getValue()),
        payloadHeader.getGasLimit(),
        payloadHeader.getGasUsed(),
        blobsLog);
  }

  private void logPayloadValueComparisonDetails(
      final boolean localPayloadValueWon,
      final UInt256 builderBidValue,
      final UInt256 localPayloadValue,
      final boolean isRequestedBuilderBoostFactor,
      final UInt64 actualBuilderBoostFactor) {
    final String comparisonFactorSource = isRequestedBuilderBoostFactor ? "VC" : "BN";
    final String actualComparisonFactorString =
        BuilderBoostFactorFormatter.formatBuilderBoostFactor(actualBuilderBoostFactor);

    final String winningSideText;
    final String winningSideValue;
    final String losingSideText;
    final String losingSideValue;

    if (localPayloadValueWon) {
      winningSideText = "Local execution payload";
      winningSideValue = weiToEth(localPayloadValue);
      losingSideText = "builder bid";
      losingSideValue = weiToEth(builderBidValue);
    } else {
      winningSideText = "Builder bid";
      winningSideValue = weiToEth(builderBidValue);
      losingSideText = "local execution payload";
      losingSideValue = weiToEth(localPayloadValue);
    }

    LOG.info(
        "{} ({} ETH) is chosen over {} ({} ETH) - builder compare factor: {}, source: {}.",
        winningSideText,
        winningSideValue,
        losingSideText,
        losingSideValue,
        actualComparisonFactorString,
        comparisonFactorSource);
  }
}
