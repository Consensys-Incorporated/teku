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

package tech.pegasys.teku.statetransition.forkchoice;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarAvailabilityChecker implements AvailabilityChecker<UInt64> {
  private static final Logger LOG = LogManager.getLogger();

  private final DataAvailabilitySampler dataAvailabilitySampler;
  private final SafeFuture<DataAndValidationResult<UInt64>> validationResult = new SafeFuture<>();
  final Spec spec;
  private final RecentChainData recentChainData;
  private final SignedBeaconBlock block;
  private final Optional<SignedExecutionPayloadEnvelope> signedEnvelope;
  private static final long BATCH_SYNC_TIMEOUT_BOOST = 5L;

  public DataColumnSidecarAvailabilityChecker(
      final DataAvailabilitySampler dataAvailabilitySampler,
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block) {
    this(dataAvailabilitySampler, spec, recentChainData, block, Optional.empty());
  }

  public DataColumnSidecarAvailabilityChecker(
      final DataAvailabilitySampler dataAvailabilitySampler,
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final SignedExecutionPayloadEnvelope signedEnvelope) {
    this(dataAvailabilitySampler, spec, recentChainData, block, Optional.of(signedEnvelope));
  }

  private DataColumnSidecarAvailabilityChecker(
      final DataAvailabilitySampler dataAvailabilitySampler,
      final Spec spec,
      final RecentChainData recentChainData,
      final SignedBeaconBlock block,
      final Optional<SignedExecutionPayloadEnvelope> signedEnvelope) {
    this.dataAvailabilitySampler = dataAvailabilitySampler;
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.block = block;
    this.signedEnvelope = signedEnvelope;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    LOG.debug("Starting data availability check for slot {}", block.getSlot());
    switch (dataAvailabilitySampler.checkSamplingEligibility(block.getMessage())) {
      case NOT_REQUIRED_BEFORE_FULU -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug("Availability check for slot {} NOT_REQUIRED, Fulu not started", block.getSlot());
      }
      case NOT_REQUIRED_OLD_EPOCH -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug("Availability check for slot {} NOT_REQUIRED, epoch too old ", block.getSlot());
      }
      case NOT_REQUIRED_NO_BLOBS -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug(
            "Availability check for slot {} NOT_REQUIRED, kzg commitments empty", block.getSlot());
      }
      default -> {
        // Propagate to a local future before applying orTimeout: orTimeout mutates the future
        // in-place, and the sampler's tracker future is shared/cached, so timing it out directly
        // would permanently poison it and prevent later column arrivals from recovering it.
        final SafeFuture<List<UInt64>> localFuture = new SafeFuture<>();
        dataAvailabilitySampler
            .checkDataAvailability(block.getSlot(), block.getRoot())
            .propagateTo(localFuture);
        localFuture
            .orTimeout(calculateCompletionTimeout(spec, block.getSlot()))
            .thenApply(DataAndValidationResult::validResult)
            .exceptionallyCompose(
                error ->
                    ExceptionUtil.getCause(error, TimeoutException.class)
                        .<SafeFuture<DataAndValidationResult<UInt64>>>map(
                            timeoutException -> {
                              if (isBlockOutsideDataAvailabilityWindow()) {
                                return SafeFuture.completedFuture(
                                    DataAndValidationResult.notRequired());
                              }
                              return SafeFuture.completedFuture(
                                  DataAndValidationResult.notAvailable(timeoutException));
                            })
                        .orElseGet(
                            () -> {
                              final Throwable cause =
                                  error instanceof CompletionException && error.getCause() != null
                                      ? error.getCause()
                                      : error;
                              return SafeFuture.completedFuture(
                                  DataAndValidationResult.notAvailable(cause));
                            }))
            .propagateTo(validationResult);
        dataAvailabilitySampler.flush();
      }
    }
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<UInt64>> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public void logAvailabilityCheckResult(
      final Logger log, final DataAndValidationResult<UInt64> result) {
    signedEnvelope.ifPresentOrElse(
        envelope ->
            log.debug(
                "Data availability check for slot: {}, builder: {}, block_root: {} result: {}",
                envelope.getSlot(),
                envelope.getMessage().getBuilderIndex(),
                envelope.getBeaconBlockRoot(),
                result.toLogString()),
        () ->
            log.debug(
                "Data availability check for slot: {}, block_root: {} result: {}",
                block.getSlot(),
                block.getRoot(),
                result.toLogString()));
  }

  private boolean isBlockOutsideDataAvailabilityWindow() {
    return !spec.isAvailabilityOfDataColumnSidecarsRequiredAtSlot(
        recentChainData.getStore(), block.getSlot());
  }

  private Duration calculateCompletionTimeout(final Spec spec, final UInt64 slot) {
    if (recentChainData.getCurrentSlot().isEmpty()) {
      return Duration.ofMillis(spec.getSlotDurationMillis(slot));
    }
    final UInt64 currentSlot = recentChainData.getCurrentSlot().get();
    final UInt64 recentEpochSlot = currentSlot.minusMinZero(spec.getSlotsPerEpoch(currentSlot));
    // BatchSync downloads 125 slots simultaneously. We should increase timeout accordingly
    if (slot.isLessThan(recentEpochSlot)) {
      return Duration.ofMillis(spec.getSlotDurationMillis(slot) * BATCH_SYNC_TIMEOUT_BOOST);
    }
    return Duration.ofMillis(spec.getSlotDurationMillis(slot));
  }
}
