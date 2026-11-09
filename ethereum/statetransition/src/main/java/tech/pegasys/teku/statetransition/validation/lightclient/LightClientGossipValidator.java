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

package tech.pegasys.teku.statetransition.validation.lightclient;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

abstract class LightClientGossipValidator<T> {
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final TimeProvider timeProvider;

  private Optional<T> latestForwarded = Optional.empty();

  LightClientGossipValidator(
      final Spec spec, final RecentChainData recentChainData, final TimeProvider timeProvider) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.timeProvider = timeProvider;
  }

  public synchronized SafeFuture<InternalValidationResult> validate(final T update) {
    if (!isNewerThanForwarded(update)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s does not advance the previously forwarded update", updateName()));
    }

    if (!getLocalUpdate().map(update::equals).orElse(false)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s does not match the locally computed update", updateName()));
    }

    if (!isSyncMessageDue(getSignatureSlot(update))) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "%s received before the sync message due time of its signature slot", updateName()));
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
    if (recentChainData.isPreGenesis()) {
      return false;
    }
    final UInt64 genesisTimeMillis = recentChainData.getGenesisTimeMillis();
    final UInt64 currentTimeMillis =
        timeProvider
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
