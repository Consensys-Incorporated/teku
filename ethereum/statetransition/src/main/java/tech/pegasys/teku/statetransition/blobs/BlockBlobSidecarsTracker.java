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

package tech.pegasys.teku.statetransition.blobs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;

public class BlockBlobSidecarsTracker {
  private static final Logger LOG = LogManager.getLogger();

  private static final UInt64 CREATION_TIMING_IDX = UInt64.MAX_VALUE;
  private static final UInt64 BLOCK_ARRIVAL_TIMING_IDX = CREATION_TIMING_IDX.decrement();
  private static final UInt64 RPC_BLOCK_FETCH_TIMING_IDX = BLOCK_ARRIVAL_TIMING_IDX.decrement();
  private static final UInt64 RPC_BLOBS_FETCH_TIMING_IDX = RPC_BLOCK_FETCH_TIMING_IDX.decrement();
  private static final UInt64 LOCAL_EL_BLOBS_FETCH_TIMING_IDX =
      RPC_BLOBS_FETCH_TIMING_IDX.decrement();

  private final SlotAndBlockRoot slotAndBlockRoot;

  private final AtomicReference<Optional<SignedBeaconBlock>> block =
      new AtomicReference<>(Optional.empty());

  private final AtomicBoolean blockImportOnCompletionEnabled = new AtomicBoolean(false);

  private final NavigableMap<UInt64, BlobSidecar> blobSidecars = new ConcurrentSkipListMap<>();
  private final SafeFuture<Void> blobSidecarsComplete = new SafeFuture<>();

  private volatile boolean localElBlobsFetchTriggered = false;
  private volatile boolean rpcBlockFetchTriggered = false;
  private volatile boolean rpcBlobsFetchTriggered = false;

  private final Optional<Map<UInt64, Long>> maybeDebugTimings;

  /**
   * {@link BlockBlobSidecarsTracker#add} and {@link BlockBlobSidecarsTracker#setBlock} methods are
   * assumed to be called from {@link BlockBlobSidecarsTrackersPool} in a synchronized context
   *
   * <p>{@link BlockBlobSidecarsTracker#setBlock} is also called in historical sync, but a dedicated
   * tracker instance will be used, so no synchronization is required
   *
   * @param slotAndBlockRoot slot and block root to create tracker for
   */
  public BlockBlobSidecarsTracker(final SlotAndBlockRoot slotAndBlockRoot) {
    this.slotAndBlockRoot = slotAndBlockRoot;
    if (LOG.isDebugEnabled()) {
      // don't need a concurrent hashmap since we'll interact with it from synchronized BlobSidecar
      // pool methods
      final Map<UInt64, Long> debugTimings = new HashMap<>();
      debugTimings.put(CREATION_TIMING_IDX, System.currentTimeMillis());
      this.maybeDebugTimings = Optional.of(debugTimings);
    } else {
      this.maybeDebugTimings = Optional.empty();
    }
  }

  public SortedMap<UInt64, BlobSidecar> getBlobSidecars() {
    return Collections.unmodifiableSortedMap(blobSidecars);
  }

  public SafeFuture<Void> getCompletionFuture() {
    final SafeFuture<Void> newCompletionFuture = new SafeFuture<>();
    blobSidecarsComplete.propagateTo(newCompletionFuture);
    return newCompletionFuture;
  }

  public Optional<SignedBeaconBlock> getBlock() {
    return block.get();
  }

  public boolean containsBlobSidecar(final BlobIdentifier blobIdentifier) {
    return Optional.ofNullable(blobSidecars.get(blobIdentifier.getIndex()))
        .map(blobSidecar -> blobSidecar.getBlockRoot().equals(blobIdentifier.getBlockRoot()))
        .orElse(false);
  }

  public Optional<BlobSidecar> getBlobSidecar(final UInt64 index) {
    return Optional.ofNullable(blobSidecars.get(index));
  }

  public Stream<BlobIdentifier> getMissingBlobSidecars() {
    final Optional<Integer> blockCommitmentsCount = getBlockKzgCommitmentsCount();
    checkState(blockCommitmentsCount.isPresent(), "Block must be known to call this method");

    return UInt64.range(UInt64.ZERO, UInt64.valueOf(blockCommitmentsCount.get()))
        .filter(blobIndex -> !blobSidecars.containsKey(blobIndex))
        .map(blobIndex -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), blobIndex));
  }

  public boolean add(final BlobSidecar blobSidecar) {
    checkArgument(
        blobSidecar.getBlockRoot().equals(slotAndBlockRoot.getBlockRoot()),
        "Wrong blobSidecar block root");

    if (blobSidecarsComplete.isDone()) {
      // already completed
      return false;
    }

    if (isExcessiveBlobSidecar(blobSidecar)) {
      LOG.debug(
          "Ignoring BlobSidecar {} - index is too high with respect to block commitments",
          blobSidecar::toLogString);
      return false;
    }

    boolean addedNew = blobSidecars.put(blobSidecar.getIndex(), blobSidecar) == null;

    if (addedNew) {
      LOG.debug("New BlobSidecar {}", blobSidecar::toLogString);
      maybeDebugTimings.ifPresent(
          debugTimings -> debugTimings.put(blobSidecar.getIndex(), System.currentTimeMillis()));
      checkCompletion();
    } else {
      LOG.debug(
          "Attempt to add already added BlobSidecar with index {} for {} detected.",
          blobSidecar.getIndex(),
          slotAndBlockRoot.toLogString());
    }

    return addedNew;
  }

  public int blobSidecarsCount() {
    return blobSidecars.size();
  }

  public boolean setBlock(final SignedBeaconBlock block) {
    checkArgument(block.getSlotAndBlockRoot().equals(slotAndBlockRoot), "Wrong block");
    final Optional<SignedBeaconBlock> oldBlock = this.block.getAndSet(Optional.of(block));
    if (oldBlock.isPresent()) {
      return false;
    }

    LOG.debug("Block received for {}", slotAndBlockRoot::toLogString);
    maybeDebugTimings.ifPresent(
        debugTimings -> debugTimings.put(BLOCK_ARRIVAL_TIMING_IDX, System.currentTimeMillis()));

    pruneExcessiveBlobSidecars();
    checkCompletion();

    return true;
  }

  public void enableBlockImportOnCompletion(final BlockImportChannel blockImportChannel) {
    final boolean alreadyEnabled = blockImportOnCompletionEnabled.getAndSet(true);
    if (alreadyEnabled) {
      return;
    }

    LOG.debug("Enabling block import on completion for {}", slotAndBlockRoot::toLogString);

    blobSidecarsComplete
        .thenCompose(
            __ -> {
              LOG.debug("Tracker completed: importing block {}", slotAndBlockRoot::toLogString);
              return blockImportChannel.importBlock(getBlock().orElseThrow());
            })
        .finish(
            () ->
                LOG.debug(
                    "Block {} imported upon tracker completion", slotAndBlockRoot::toLogString),
            error ->
                LOG.error(
                    "An error occurred importing block {} upon tracker completion",
                    slotAndBlockRoot.toLogString(),
                    error));
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return slotAndBlockRoot;
  }

  private void checkCompletion() {
    if (blobSidecarsComplete.isDone()) {
      return;
    }
    if (areBlobsComplete()) {
      LOG.debug("BlobSidecars for {} are completed", slotAndBlockRoot::toLogString);
      blobSidecarsComplete.complete(null);
      maybeDebugTimings.ifPresent(this::printDebugTimings);
    }
  }

  private void pruneExcessiveBlobSidecars() {
    getBlockKzgCommitmentsCount()
        .ifPresent(count -> blobSidecars.tailMap(UInt64.valueOf(count), true).clear());
  }

  private boolean isExcessiveBlobSidecar(final BlobSidecar blobSidecar) {
    return getBlockKzgCommitmentsCount()
        .map(count -> blobSidecar.getIndex().isGreaterThanOrEqualTo(count))
        .orElse(false);
  }

  public boolean isComplete() {
    return blobSidecarsComplete.isDone();
  }

  public boolean isLocalElBlobsFetchTriggered() {
    return localElBlobsFetchTriggered;
  }

  public void setLocalElBlobsFetchTriggered() {
    this.localElBlobsFetchTriggered = true;
    maybeDebugTimings.ifPresent(
        debugTimings ->
            debugTimings.put(LOCAL_EL_BLOBS_FETCH_TIMING_IDX, System.currentTimeMillis()));
  }

  public boolean isRpcBlockFetchTriggered() {
    return rpcBlockFetchTriggered;
  }

  public void setRpcBlockFetchTriggered() {
    this.rpcBlockFetchTriggered = true;
    maybeDebugTimings.ifPresent(
        debugTimings -> debugTimings.put(RPC_BLOCK_FETCH_TIMING_IDX, System.currentTimeMillis()));
  }

  public boolean isRpcBlobsFetchTriggered() {
    return rpcBlobsFetchTriggered;
  }

  public void setRpcBlobsFetchTriggered() {
    this.rpcBlobsFetchTriggered = true;
    maybeDebugTimings.ifPresent(
        debugTimings -> debugTimings.put(RPC_BLOBS_FETCH_TIMING_IDX, System.currentTimeMillis()));
  }

  private boolean areBlobsComplete() {
    return getBlockKzgCommitmentsCount().map(count -> blobSidecars.size() >= count).orElse(false);
  }

  private Optional<Integer> getBlockKzgCommitmentsCount() {
    return block
        .get()
        .map(
            b ->
                BeaconBlockBodyDeneb.required(b.getMessage().getBody())
                    .getBlobKzgCommitments()
                    .size());
  }

  /**
   * prints a debug line when tracker completes
   *
   * @param debugTimings
   */
  private void printDebugTimings(final Map<UInt64, Long> debugTimings) {
    final long completionTime = System.currentTimeMillis();
    final long creationTime = debugTimings.getOrDefault(CREATION_TIMING_IDX, 0L);
    final StringBuilder timingsReport = new StringBuilder(128);

    timingsReport
        .append("Tracker for ")
        .append(slotAndBlockRoot.toLogString())
        .append(" created at ")
        .append(creationTime)
        .append(" - ");

    timingsReport.append("Completion time ").append(completionTime - creationTime).append("ms - ");

    blobSidecars
        .keySet()
        .forEach(
            blobIndex ->
                timingsReport
                    .append("Sidecar [")
                    .append(blobIndex)
                    .append("] delay ")
                    .append(debugTimings.getOrDefault(blobIndex, 0L) - creationTime)
                    .append("ms - "));
    timingsReport
        .append("Block delay ")
        .append(debugTimings.getOrDefault(BLOCK_ARRIVAL_TIMING_IDX, 0L) - creationTime)
        .append("ms - ");

    if (debugTimings.containsKey(LOCAL_EL_BLOBS_FETCH_TIMING_IDX)) {
      timingsReport
          .append("Local EL blobs fetch delay ")
          .append(debugTimings.get(LOCAL_EL_BLOBS_FETCH_TIMING_IDX) - creationTime)
          .append("ms - ");
    } else {
      timingsReport.append("Local EL blobs fetch wasn't required - ");
    }

    if (debugTimings.containsKey(RPC_BLOCK_FETCH_TIMING_IDX)) {
      timingsReport
          .append("RPC block fetch delay ")
          .append(debugTimings.get(RPC_BLOCK_FETCH_TIMING_IDX) - creationTime)
          .append("ms - ");
    } else {
      timingsReport.append("RPC block fetch wasn't required - ");
    }

    if (debugTimings.containsKey(RPC_BLOBS_FETCH_TIMING_IDX)) {
      timingsReport
          .append("RPC blobs fetch delay ")
          .append(debugTimings.get(RPC_BLOBS_FETCH_TIMING_IDX) - creationTime)
          .append("ms");
    } else {
      timingsReport.append("RPC blobs fetch wasn't required");
    }

    LOG.debug(timingsReport.toString());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slotAndBlockRoot", slotAndBlockRoot)
        .add("isBlockPresent", block.get().isPresent())
        .add("isComplete", isComplete())
        .add("localElBlobsFetchTriggered", localElBlobsFetchTriggered)
        .add("rpcBlockFetchTriggered", rpcBlockFetchTriggered)
        .add("rpcBlobsFetchTriggered", rpcBlobsFetchTriggered)
        .add("blockImportOnCompletionEnabled", blockImportOnCompletionEnabled.get())
        .add(
            "blobSidecars",
            blobSidecars.entrySet().stream()
                .map(
                    entry ->
                        MoreObjects.toStringHelper("")
                            .add("index", entry.getKey())
                            .add("blobSidecar", entry.getValue().toLogString()))
                .toList())
        .toString();
  }
}
