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

package tech.pegasys.teku.storage.server;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

/**
 * When async storage updates are used, the in-memory Store will be updated immediately without
 * waiting for the database write to complete. If the database write fails for some reason, we can't
 * just skip that update or the in-memory and disk stores will be out of sync.
 *
 * <p>So we keep retrying the update until it succeeds. During this time a queue of other events may
 * build up behind the failing event. That queue has a limited length until it starts blocking the
 * caller threads again, which ultimately will bring the whole node to a stop. That happens to be
 * essentially the same result we'd get if the update failed with synchronous storage - the node
 * couldn't make any progress.
 *
 * <p>The only way to get the in-memory and on-disk stores back in sync if the update never
 * succeeds, would be to restart the node at which point it will revert back to the on-disk version
 * and start again from there. If a retry fails for long enough, we give up and crash to trigger
 * this revert.
 */
public class RetryingStorageUpdateChannel implements StorageUpdateChannel {

  private static final Logger LOG = LogManager.getLogger();
  static final Duration MAX_RETRY_TIME = Duration.ofMinutes(1);
  public static final int RETRY_DELAY_MS = 500;

  private final StorageUpdateChannel delegate;
  private final TimeProvider timeProvider;
  private final long retryDelayMs;
  private final AtomicBoolean aborting = new AtomicBoolean(false);

  public RetryingStorageUpdateChannel(
      final StorageUpdateChannel delegate, final TimeProvider timeProvider) {
    this(delegate, timeProvider, RETRY_DELAY_MS);
  }

  @VisibleForTesting
  RetryingStorageUpdateChannel(
      final StorageUpdateChannel delegate,
      final TimeProvider timeProvider,
      final long retryDelayMs) {
    this.delegate = delegate;
    this.timeProvider = timeProvider;
    this.retryDelayMs = retryDelayMs;
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return retry(() -> delegate.onStorageUpdate(event));
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(
      final Collection<SignedBeaconBlock> finalizedBlocks,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot,
      final Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> blindedExecutionPayloads,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    return retry(
        () ->
            delegate.onFinalizedBlocks(
                finalizedBlocks,
                blobSidecarsBySlot,
                blindedExecutionPayloads,
                maybeEarliestBlobSidecarSlot));
  }

  @Override
  public SafeFuture<Void> onReconstructedFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return this.retry(() -> delegate.onReconstructedFinalizedState(finalizedState, blockRoot));
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return retry(() -> delegate.onWeakSubjectivityUpdate(weakSubjectivityUpdate));
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    return retry(() -> delegate.onFinalizedDepositSnapshot(depositTreeSnapshot));
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    this.retry(
            () -> {
              delegate.onChainInitialized(initialAnchor);
              return SafeFuture.COMPLETE;
            })
        .finishStackTrace();
  }

  private <O> SafeFuture<O> retry(final Supplier<SafeFuture<O>> method) {
    final UInt64 startTime = timeProvider.getTimeInMillis();
    while (!aborting.get()) {
      try {
        final SafeFuture<O> result = method.get();
        result.join();
        return result;
      } catch (final Throwable t) {
        final UInt64 failureTime = timeProvider.getTimeInMillis();
        if (failureTime.minusMinZero(startTime).isGreaterThan(MAX_RETRY_TIME.toMillis())) {
          // Don't try to process any further events as they may corrupt the database
          // or may delay shutdown while they retry until they time out.
          aborting.set(true);
          throw new FatalServiceFailureException(RetryingStorageUpdateChannel.class, t);
        }
        LOG.error("Storage update failed, retrying.", t);
        // Avoid being in a tight loop where we're going to spam logs with errors.
        pauseALittle();
      }
    }
    return SafeFuture.failedFuture(new ShuttingDownException());
  }

  private void pauseALittle() {
    try {
      Thread.sleep(retryDelayMs);
    } catch (final InterruptedException e) {
      LOG.trace("Interrupted while delaying between storage update retries", e);
    }
  }
}
