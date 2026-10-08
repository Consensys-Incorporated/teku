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

package tech.pegasys.teku.statetransition.validation.lightclient;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

/** Gossip validation shared by the light client finality and optimistic update topics. */
abstract class LightClientGossipValidator<T> {
  private final Spec spec;
  private final RecentChainData recentChainData;

  private Optional<T> latestForwarded = Optional.empty();

  LightClientGossipValidator(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public synchronized SafeFuture<InternalValidationResult> validate(final T update) {
    if (!isNewerThanForwarded(update)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s does not advance the previously forwarded update", updateName()));
    }

    if (!isSyncMessageDue(getSignatureSlot(update))) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s received before the sync message due time of its signature slot", updateName()));
    }

    if (!getLocalUpdate().map(update::equals).orElse(false)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s does not match the locally computed update", updateName()));
    }

    latestForwarded = Optional.of(update);
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }

  public synchronized boolean markForwarded(final T update) {
    if (!isNewerThanForwarded(update)) {
      return false;
    }
    latestForwarded = Optional.of(update);
    return true;
  }

  protected abstract String updateName();

  protected abstract UInt64 getSignatureSlot(T update);

  protected abstract Optional<T> getLocalUpdate();

  protected abstract boolean isNewer(T update, T forwarded);

  private boolean isNewerThanForwarded(final T update) {
    return latestForwarded.map(forwarded -> isNewer(update, forwarded)).orElse(true);
  }

  private boolean isSyncMessageDue(final UInt64 signatureSlot) {
    final UInt64 genesisTimeMillis = recentChainData.getGenesisTimeMillis();
    final UInt64 currentTimeMillis =
        recentChainData
            .getStore()
            .getTimeInMillis()
            .plus(spec.getNetworkingConfig().getMaximumGossipClockDisparity());
    if (signatureSlot.isGreaterThan(
        spec.getCurrentSlotFromTimeMillis(currentTimeMillis, genesisTimeMillis))) {
      return false;
    }
    return !spec.isBeforeTimeInSlot(
        signatureSlot,
        genesisTimeMillis,
        currentTimeMillis,
        spec.getSyncMessageDueMillis(signatureSlot));
  }
}
