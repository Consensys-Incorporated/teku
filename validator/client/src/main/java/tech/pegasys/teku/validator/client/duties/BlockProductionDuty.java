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

package tech.pegasys.teku.validator.client.duties;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.BuilderConfigProvider;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;

public class BlockProductionDuty implements Duty {

  private static final Logger LOG = LogManager.getLogger();

  private final Validator validator;
  private final UInt64 slot;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final BlockContainerSigner blockContainerSigner;
  private final Spec spec;
  private final ValidatorDutyMetrics validatorDutyMetrics;
  private final ExecutionPayloadBidEventsChannel executionPayloadBidEventsChannelPublisher;
  private final BuilderConfigProvider builderConfigProvider;

  public BlockProductionDuty(
      final Validator validator,
      final UInt64 slot,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final BlockContainerSigner blockContainerSigner,
      final Spec spec,
      final ValidatorDutyMetrics validatorDutyMetrics,
      final ExecutionPayloadBidEventsChannel executionPayloadBidEventsChannelPublisher,
      final BuilderConfigProvider builderConfigProvider) {
    this.validator = validator;
    this.slot = slot;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.blockContainerSigner = blockContainerSigner;
    this.spec = spec;
    this.validatorDutyMetrics = validatorDutyMetrics;
    this.executionPayloadBidEventsChannelPublisher = executionPayloadBidEventsChannelPublisher;
    this.builderConfigProvider = builderConfigProvider;
  }

  @Override
  public DutyType getType() {
    return DutyType.BLOCK_PRODUCTION;
  }

  @Override
  public SafeFuture<DutyResult> performDuty() {
    LOG.trace("Creating block for validator {} at slot {}", validator.getPublicKey(), slot);
    return forkProvider
        .getForkInfo(slot)
        .thenCompose(this::produceBlock)
        .exceptionally(this::handleBlockProductionError);
  }

  private SafeFuture<DutyResult> produceBlock(final ForkInfo forkInfo) {
    return createRandaoReveal(forkInfo)
        .thenComposeCombined(
            builderConfigProvider.getBuilderConfig(validator, slot),
            (randaoReveal, builderConfig) ->
                validatorDutyMetrics.record(
                    () -> createUnsignedBlock(randaoReveal, builderConfig),
                    this,
                    ValidatorDutyMetricsSteps.CREATE))
        .thenCompose(this::validateBlock)
        .thenCompose(
            blockContainer ->
                validatorDutyMetrics.record(
                    () -> signBlockContainer(forkInfo, blockContainer),
                    this,
                    ValidatorDutyMetricsSteps.SIGN))
        .thenCompose(
            signedBlockContainer ->
                validatorDutyMetrics.record(
                    () -> sendBlock(signedBlockContainer, forkInfo),
                    this,
                    ValidatorDutyMetricsSteps.SEND));
  }

  private DutyResult handleBlockProductionError(final Throwable error) {
    LOG.debug(
        "Block production error for validator {} at slot {}: {}",
        validator.getPublicKey().toAbbreviatedString(),
        slot,
        error);
    return DutyResult.forError(validator.getPublicKey(), error);
  }

  private SafeFuture<BLSSignature> createRandaoReveal(final ForkInfo forkInfo) {
    return validator.getSigner().createRandaoReveal(spec.computeEpochAtSlot(slot), forkInfo);
  }

  private SafeFuture<Optional<BlockContainerAndMetaData>> createUnsignedBlock(
      final BLSSignature randaoReveal, final Optional<BuilderConfig> builderConfig) {
    return validatorApiChannel.createUnsignedBlock(
        slot, randaoReveal, validator.getGraffiti(), false, builderConfig);
  }

  private SafeFuture<BlockContainer> validateBlock(
      final Optional<BlockContainerAndMetaData> maybeBlockContainer) {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        maybeBlockContainer.orElseThrow(
            () -> new IllegalStateException("Node was not syncing but could not create block"));
    final BlockContainer unsignedBlockContainer = blockContainerAndMetaData.blockContainer();
    checkArgument(
        unsignedBlockContainer.getSlot().equals(slot),
        "Unsigned block slot (%s) does not match expected slot %s",
        unsignedBlockContainer.getSlot(),
        slot);

    if (!blockContainerAndMetaData.consensusBlockValue().isZero()) {
      LOG.info(
          "Validator client received block for slot {}, block rewards {} ETH, execution payload value {} ETH",
          slot,
          weiToEth(blockContainerAndMetaData.consensusBlockValue()),
          weiToEth(blockContainerAndMetaData.executionPayloadValue()));
    }
    return SafeFuture.completedFuture(unsignedBlockContainer);
  }

  private SafeFuture<SignedBlockContainer> signBlockContainer(
      final ForkInfo forkInfo, final BlockContainer blockContainer) {
    return blockContainerSigner.sign(blockContainer, validator, forkInfo);
  }

  private SafeFuture<DutyResult> sendBlock(
      final SignedBlockContainer signedBlockContainer, final ForkInfo forkInfo) {
    return validatorApiChannel
        .sendSignedBlock(signedBlockContainer, BroadcastValidationLevel.GOSSIP)
        .thenApply(
            result -> {
              if (result.isPublished()) {
                final BeaconBlockBody blockBody =
                    signedBlockContainer.getSignedBlock().getMessage().getBody();
                blockBody
                    .getOptionalSignedExecutionPayloadBid()
                    .ifPresent(
                        signedBid ->
                            notifyExecutionPayloadBidEventsSubscribers(
                                signedBid, forkInfo, signedBlockContainer.getRoot()));
                return DutyResult.success(
                    signedBlockContainer.getRoot(), getBlockSummary(blockBody));
              }
              return DutyResult.forError(
                  validator.getPublicKey(),
                  new IllegalArgumentException(
                      "Block was rejected by the beacon node: "
                          + result.getRejectionReason().orElse("<reason unknown>")));
            });
  }

  @VisibleForTesting
  List<String> getBlockSummary(final BeaconBlockBody blockBody) {
    final List<String> context = new ArrayList<>();
    getBlobsSummary(blockBody).ifPresent(context::add);
    getExecutionSummary(blockBody).ifPresent(context::add);
    return context;
  }

  private Optional<String> getBlobsSummary(final BeaconBlockBody blockBody) {
    return blockBody
        .getOptionalBlobKzgCommitments()
        .map(blobKzgCommitments -> "Blobs: " + blobKzgCommitments.size());
  }

  private Optional<String> getExecutionSummary(final BeaconBlockBody blockBody) {
    return blockBody
        .getOptionalExecutionPayloadSummary()
        .map(this::getExecutionSummaryFromExecutionPayloadSummary)
        .or(
            () ->
                blockBody
                    .getOptionalSignedExecutionPayloadBid()
                    .map(signedBid -> getExecutionSummaryFromBid(signedBid.getMessage())));
  }

  private String getExecutionSummaryFromExecutionPayloadSummary(
      final ExecutionPayloadSummary summary) {
    return String.format(
        "%s (%s%%) gas, EL block: %s (%s)",
        summary.getGasUsed(),
        summary.computeGasPercentage(LOG),
        summary.getBlockHash().toUnprefixedHexString(),
        summary.getBlockNumber());
  }

  private String getExecutionSummaryFromBid(final ExecutionPayloadBid bid) {
    return String.format(
        "Builder: %s, Bid gas limit: %s, Bid EL block: %s",
        bid.getBuilderIndex(),
        bid.getGasLimit(),
        LogFormatter.formatAbbreviatedHashRoot(bid.getBlockHash()));
  }

  private void notifyExecutionPayloadBidEventsSubscribers(
      final SignedExecutionPayloadBid signedBid,
      final ForkInfo forkInfo,
      final Bytes32 beaconBlockRoot) {
    // BUILDER_INDEX_SELF_BUILD indicates a self-built bid
    if (signedBid.getMessage().getBuilderIndex().equals(BUILDER_INDEX_SELF_BUILD)) {
      executionPayloadBidEventsChannelPublisher.onSelfBuiltBidIncludedInBlock(
          validator, forkInfo, signedBid.getMessage(), beaconBlockRoot);
    }
  }

  @Override
  public String toString() {
    return "BlockProductionDuty{"
        + "validator="
        + validator
        + ", slot="
        + slot
        + ", forkProvider="
        + forkProvider
        + '}';
  }
}
