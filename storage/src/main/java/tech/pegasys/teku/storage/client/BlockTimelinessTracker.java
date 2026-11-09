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

/** Runtime storage for record_block_timeliness. */
class BlockTimelinessTracker {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final Supplier<UInt64> genesisTimeMillisSupplier;
  private final Map<Bytes32, BlockTimeliness> blockTimeliness;

  BlockTimelinessTracker(
      final Spec spec,
      final Supplier<UInt64> genesisTimeMillisSupplier,
      final Map<Bytes32, BlockTimeliness> blockTimeliness) {
    this.spec = spec;
    this.genesisTimeMillisSupplier = genesisTimeMillisSupplier;
    this.blockTimeliness = blockTimeliness;
  }

  public void setBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTimeMillis) {
    if (blockTimeliness.get(block.getRoot()) != null) {
      return;
    }
    if (spec.atSlot(block.getSlot())
        .getForkChoiceUtil()
        .isDataAvailabilityRequiredForTimeliness()) {
      // Timeliness will be recorded after data availability check completes
      return;
    }
    blockTimeliness.put(
        block.getRoot(), computeBlockTimelinessFromArrivalTime(block, arrivalTimeMillis));
  }

  /**
   * Records block timeliness using the time at which data availability was confirmed. Only called
   * for forks (Fulu) where DA determines timeliness rather than block body arrival.
   */
  public void setBlockTimelinessAfterDataAvailability(
      final SignedBeaconBlock block, final UInt64 dataAvailableTimeMillis) {
    if (blockTimeliness.get(block.getRoot()) != null) {
      return;
    }
    blockTimeliness.put(
        block.getRoot(), computeBlockTimelinessFromArrivalTime(block, dataAvailableTimeMillis));
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
    return Optional.ofNullable(blockTimeliness.get(root));
  }

  public boolean isBlockLate(final Bytes32 root) {
    return ForkChoiceUtil.isHeadLate(Optional.ofNullable(blockTimeliness.get(root)));
  }
}
