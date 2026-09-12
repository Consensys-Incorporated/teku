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

package tech.pegasys.teku.storage.client;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;

/**
 * Runtime storage for record_block_timeliness.
 *
 * <p>Timeliness is first recorded speculatively (unconfirmed) as soon as a block is observed,
 * before we know whether that particular import attempt will succeed. An unconfirmed recording may
 * be refreshed or discarded by later events for the same block, so a premature observation (e.g. a
 * block gossiped slightly before its slot starts) or one tied to an attempt that is ultimately
 * rejected/deferred does not permanently pin an incorrect value. Once a block is actually,
 * successfully imported, its timeliness recording is confirmed and becomes final.
 */
class BlockTimelinessTracker {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Supplier<UInt64> genesisTimeMillisSupplier;
  private final Map<Bytes32, TimelinessRecord> blockTimeliness;

  BlockTimelinessTracker(
      final Spec spec,
      final Supplier<UInt64> genesisTimeMillisSupplier,
      final Map<Bytes32, TimelinessRecord> blockTimeliness) {
    this.spec = spec;
    this.genesisTimeMillisSupplier = genesisTimeMillisSupplier;
    this.blockTimeliness = blockTimeliness;
  }

  /**
   * Records an observation of block timeliness from an arrival time (e.g. gossip receipt). As long
   * as the block hasn't yet been confirmed (see {@link #confirmBlockTimeliness}), this overwrites
   * any previous, unconfirmed observation - so a later, more accurate signal always wins over a
   * stale one left behind by a premature or unsuccessful earlier attempt.
   */
  public void setBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTimeMillis) {
    if (isConfirmed(block.getRoot())) {
      return;
    }
    if (spec.atSlot(block.getSlot())
        .getForkChoiceUtil()
        .isDataAvailabilityRequiredForTimeliness()) {
      // Timeliness will be recorded after data availability check completes
      return;
    }
    blockTimeliness.put(
        block.getRoot(),
        new TimelinessRecord(
            computeBlockTimelinessFromArrivalTime(block, arrivalTimeMillis), false));
  }

  /**
   * Records block timeliness using the time at which data availability was confirmed. Only called
   * for forks (Fulu) where DA determines timeliness rather than block body arrival.
   */
  public void setBlockTimelinessAfterDataAvailability(
      final SignedBeaconBlock block, final UInt64 dataAvailableTimeMillis) {
    if (isConfirmed(block.getRoot())) {
      return;
    }
    blockTimeliness.put(
        block.getRoot(),
        new TimelinessRecord(
            computeBlockTimelinessFromArrivalTime(block, dataAvailableTimeMillis), false));
  }

  /**
   * Discards any not-yet-confirmed timeliness recording for a block. Called whenever an import
   * attempt concludes without the block being successfully imported, so that if the block is later
   * imported through a separate attempt (e.g. retried from a pending/future block pool, or
   * re-fetched by root), that later attempt starts from a clean slate instead of being stuck with a
   * stale, possibly premature or invalid observation from the earlier attempt. A confirmed
   * recording (from a previous successful import) is never discarded.
   */
  public void invalidateUnconfirmedTimeliness(final Bytes32 root) {
    final TimelinessRecord existing = blockTimeliness.get(root);
    if (existing != null && !existing.confirmed()) {
      blockTimeliness.remove(root);
    }
  }

  /**
   * Confirms the timeliness recording for a block that has just been successfully imported. If an
   * unconfirmed observation is already present, it is promoted as the final value. Otherwise (no
   * observation was ever recorded for this block, e.g. it was imported directly via RPC with no
   * prior gossip arrival), timeliness is computed fresh using {@code fallbackTimeMillis}. Once
   * confirmed, the recording is final and will not be changed by any later call.
   */
  public void confirmBlockTimeliness(
      final SignedBeaconBlock block, final UInt64 fallbackTimeMillis) {
    final Bytes32 root = block.getRoot();
    final TimelinessRecord existing = blockTimeliness.get(root);
    if (existing == null) {
      blockTimeliness.put(
          root,
          new TimelinessRecord(
              computeBlockTimelinessFromArrivalTime(block, fallbackTimeMillis), true));
    } else if (!existing.confirmed()) {
      blockTimeliness.put(root, new TimelinessRecord(existing.timeliness(), true));
    }
  }

  private boolean isConfirmed(final Bytes32 root) {
    final TimelinessRecord existing = blockTimeliness.get(root);
    return existing != null && existing.confirmed();
  }

  BlockTimeliness computeBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTimeMillis) {
    final UInt64 genesisTimeMillis = genesisTimeMillisSupplier.get();
    final UInt64 computedSlot =
        spec.getCurrentSlot(arrivalTimeMillis.dividedBy(1000), genesisTimeMillis.dividedBy(1000));
    final Bytes32 root = block.getRoot();
    if (computedSlot.isGreaterThan(block.getMessage().getSlot())) {
      LOG.debug(
          "Block {}:{} is before computed slot {}, timeliness set to false.",
          root,
          block.getSlot(),
          computedSlot);
      return new BlockTimeliness(false, false);
    }
    final UInt64 slotStartTimeMillis =
        spec.computeTimeMillisAtSlot(computedSlot, genesisTimeMillis);
    final int millisIntoSlot = arrivalTimeMillis.minusMinZero(slotStartTimeMillis).intValue();

    final ForkChoiceUtil forkChoiceUtil = spec.atSlot(computedSlot).getForkChoiceUtil();
    final int timelinessLimit = forkChoiceUtil.getAttestationDueMillis();
    final BlockTimeliness timeliness =
        forkChoiceUtil.computeBlockTimeliness(
            block.getMessage().getSlot(), computedSlot, millisIntoSlot);
    LOG.debug(
        "Block {}:{} arrived at {} ms into slot {}, timeliness limit is {} ms. result: {}",
        root,
        block.getSlot(),
        millisIntoSlot,
        computedSlot,
        timelinessLimit,
        timeliness.isTimelyAttestation());
    return timeliness;
  }

  public Optional<BlockTimeliness> getBlockTimeliness(final Bytes32 root) {
    return Optional.ofNullable(blockTimeliness.get(root)).map(TimelinessRecord::timeliness);
  }

  public boolean isBlockLate(final Bytes32 root) {
    return ForkChoiceUtil.isHeadLate(getBlockTimeliness(root));
  }

  /**
   * @param timeliness the recorded timeliness value
   * @param confirmed whether this recording is tied to a block that has been successfully imported.
   *     Confirmed recordings are final; unconfirmed ones may still be refreshed or discarded.
   */
  record TimelinessRecord(BlockTimeliness timeliness, boolean confirmed) {}
}
