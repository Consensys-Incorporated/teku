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

package tech.pegasys.teku.statetransition.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

/**
 * Tracks builder URLs which returned a bid from a builder banned by the {@link
 * ExecutionPayloadBidCircuitBreaker}. A banned URL is not requested for bids for a configured
 * number of epochs. Expired bans are cleared on every slot.
 */
public class BannedBuilderUrls implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final int banEpochs;

  private final Map<String, UInt64> bannedUntilSlotByUrl = new ConcurrentHashMap<>();

  public BannedBuilderUrls(final Spec spec, final int banEpochs) {
    this.spec = spec;
    this.banEpochs = banEpochs;
  }

  public boolean isBanned(final String url, final UInt64 slot) {
    final UInt64 bannedUntilSlot = bannedUntilSlotByUrl.get(url);
    return bannedUntilSlot != null && bannedUntilSlot.isGreaterThan(slot);
  }

  /** Bans the URL for {@code banEpochs} epochs starting from {@code slot}. */
  public void ban(final String url, final UInt64 slot) {
    if (banEpochs == 0) {
      return;
    }
    final UInt64 bannedUntilSlot = slot.plus((long) spec.getSlotsPerEpoch(slot) * banEpochs);
    bannedUntilSlotByUrl.put(url, bannedUntilSlot);
    LOG.info("Banning builder URL {} until slot {}", url, bannedUntilSlot);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    bannedUntilSlotByUrl
        .entrySet()
        .removeIf(
            entry -> {
              if (entry.getValue().isGreaterThan(slot)) {
                return false;
              }
              LOG.info("Builder URL {} has been un-banned at slot {}", entry.getKey(), slot);
              return true;
            });
  }
}
