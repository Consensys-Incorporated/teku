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

package tech.pegasys.teku.statetransition.datacolumns;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

record DataColumnSamplingTracker(
    UInt64 slot,
    Bytes32 blockRoot,
    Set<UInt64> samplingRequirement,
    Set<UInt64> missingColumns,
    AtomicBoolean rpcFetchInProgress,
    SafeFuture<List<UInt64>> completionFuture,
    AtomicBoolean fullySampled,
    AtomicBoolean blockImportOnCompletionEnabled,
    Optional<Integer> earlyCompletionRequirementCount,
    AtomicReference<Optional<SignedBeaconBlock>> block,
    long createdAtNanos) {
  private static final Logger LOG = LogManager.getLogger();

  static DataColumnSamplingTracker create(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final CustodyGroupCountManager custodyGroupCountManager,
      final Optional<Integer> completionColumnCount) {
    final Set<UInt64> samplingRequirement = custodyGroupCountManager.getSamplingColumnIndices();
    final Set<UInt64> missingColumns = ConcurrentHashMap.newKeySet(samplingRequirement.size());
    missingColumns.addAll(samplingRequirement);
    final SafeFuture<List<UInt64>> completionFuture = new SafeFuture<>();
    return new DataColumnSamplingTracker(
        slot,
        blockRoot,
        samplingRequirement,
        missingColumns,
        new AtomicBoolean(false),
        completionFuture,
        new AtomicBoolean(false),
        new AtomicBoolean(false),
        completionColumnCount,
        new AtomicReference<>(Optional.empty()),
        System.nanoTime());
  }

  public Optional<SignedBeaconBlock> getBlock() {
    return block.get();
  }

  public boolean setBlock(final SignedBeaconBlock block) {
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(slot, blockRoot);
    checkArgument(block.getSlotAndBlockRoot().equals(slotAndBlockRoot), "Wrong block");
    final Optional<SignedBeaconBlock> oldBlock = this.block.getAndSet(Optional.of(block));
    if (oldBlock.isPresent()) {
      return false;
    }
    LOG.debug("Block received for {}", slotAndBlockRoot::toLogString);
    return true;
  }

  boolean enableBlockImportOnCompletion() {
    return blockImportOnCompletionEnabled.compareAndSet(false, true);
  }

  boolean add(final DataColumnSlotAndIdentifier columnIdentifier, final RemoteOrigin origin) {
    if (!slot.equals(columnIdentifier.slot()) || !blockRoot.equals(columnIdentifier.blockRoot())) {
      return false;
    }

    LOG.trace("Adding column {} to sampling tracker", columnIdentifier);
    final boolean removed =
        missingColumns.removeIf(idx -> idx.equals(columnIdentifier.columnIndex()));
    if (!removed) {
      LOG.trace("Column {} was already marked as received, origin: {}", columnIdentifier, origin);
      return false;
    }

    if (missingColumns.isEmpty()) {
      LOG.trace(
          "Sampling complete for slot {} root {} via column {} received via {}",
          slot,
          blockRoot,
          columnIdentifier.columnIndex(),
          origin);
      completionFuture.complete(samplingRequirement.stream().toList());
      fullySampled.set(true);
      return true;
    }

    if (isCompletedEarly()) {
      completionFuture.complete(
          samplingRequirement.stream().filter(idx -> !missingColumns.contains(idx)).toList());
      LOG.trace(
          "Partial sampling complete for slot {} root {} via column {} received via {}",
          slot,
          blockRoot,
          columnIdentifier.columnIndex(),
          origin);
      return true;
    }

    LOG.trace(
        "Sampling still pending for slot {} root {}, remaining columns: {}",
        slot,
        blockRoot,
        missingColumns);

    return true;
  }

  private boolean isCompletedEarly() {
    if (earlyCompletionRequirementCount().isEmpty()) {
      // Possibility of early completion is disabled
      return false;
    }

    if (completionFuture.isDone()) {
      // Already completed
      return false;
    }

    final int alreadySampledColumnCount = samplingRequirement.size() - missingColumns().size();
    return alreadySampledColumnCount >= earlyCompletionRequirementCount.get();
  }

  List<DataColumnSlotAndIdentifier> getMissingColumnIdentifiers() {
    return missingColumns.stream()
        .map(idx -> new DataColumnSlotAndIdentifier(slot, blockRoot, idx))
        .toList();
  }
}
