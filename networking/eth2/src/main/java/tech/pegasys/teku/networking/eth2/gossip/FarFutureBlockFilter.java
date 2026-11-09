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

package tech.pegasys.teku.networking.eth2.gossip;

import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.DecodedPrefixFilter;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.block.FutureBlockPool;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

/**
 * Ignores gossiped blocks that are too far in the future to be queued for later import, before they
 * are decoded, by reading the slot from its fixed position in the SSZ bytes.
 *
 * <p>Gossip validation would ignore them anyway, but only after decoding them, and the blocks would
 * hold their share of the in-flight limit while waiting to be processed.
 */
class FarFutureBlockFilter implements DecodedPrefixFilter {
  private static final Logger LOG = LogManager.getLogger();

  // A SignedBeaconBlock starts with the 4 byte offset of its message followed by the 96 byte
  // signature, and the BeaconBlock message starts with its slot. This is the same for every
  // milestone.
  static final int MESSAGE_OFFSET = 4 + 96;
  private static final int SLOT_SIZE = 8;

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Counter ignoredCounter;

  FarFutureBlockFilter(
      final Spec spec, final RecentChainData recentChainData, final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.ignoredCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "gossip_far_future_blocks_ignored_total",
            "Total number of gossip blocks ignored before decoding because their slot is too far"
                + " in the future to queue them for import");
  }

  @Override
  public boolean shouldProcess(final Bytes decodedPrefix) {
    final Optional<UInt64> maybeSlot = getSlot(decodedPrefix);
    final UpdatableStore store = recentChainData.getStore();
    if (maybeSlot.isEmpty() || store == null) {
      // leave anything malformed to the decoder
      return true;
    }
    // same clock disparity allowance as the gossip validation's future slot check
    final UInt64 maxTimeMillis =
        store.getTimeInMillis().plus(spec.getNetworkingConfig().getMaximumGossipClockDisparity());
    final UInt64 maxCurrentSlot =
        spec.getCurrentSlotFromTimeMillis(maxTimeMillis, recentChainData.getGenesisTimeMillis());
    final UInt64 slot = maybeSlot.get();
    if (FutureBlockPool.isBeyondFutureSlotTolerance(slot, maxCurrentSlot)) {
      LOG.trace(
          "Ignoring gossip block for slot {} which is too far beyond current slot {}",
          slot,
          maxCurrentSlot);
      ignoredCounter.inc();
      return false;
    }
    return true;
  }

  static Optional<UInt64> getSlot(final Bytes signedBeaconBlockPrefix) {
    if (signedBeaconBlockPrefix.size() < MESSAGE_OFFSET + SLOT_SIZE
        || signedBeaconBlockPrefix.getInt(0, ByteOrder.LITTLE_ENDIAN) != MESSAGE_OFFSET) {
      return Optional.empty();
    }
    return Optional.of(
        UInt64.fromLongBits(
            signedBeaconBlockPrefix.getLong(MESSAGE_OFFSET, ByteOrder.LITTLE_ENDIAN)));
  }
}
