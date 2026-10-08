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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.RecentChainData;

public class LightClientFinalityUpdateGossipValidator
    extends LightClientGossipValidator<LightClientFinalityUpdate> {
  private final LightClientUpdateStore store;

  public LightClientFinalityUpdateGossipValidator(
      final Spec spec, final RecentChainData recentChainData, final LightClientUpdateStore store) {
    super(spec, recentChainData);
    this.store = store;
  }

  @Override
  protected String updateName() {
    return "Finality update";
  }

  @Override
  protected UInt64 getSignatureSlot(final LightClientFinalityUpdate update) {
    return update.getSignatureSlot().get();
  }

  @Override
  protected Optional<LightClientFinalityUpdate> getLocalUpdate() {
    return store.getLatestFinalityUpdate();
  }

  @Override
  protected boolean isNewer(
      final LightClientFinalityUpdate update, final LightClientFinalityUpdate forwarded) {
    final UInt64 finalizedSlot = update.getFinalizedHeader().getBeacon().getSlot();
    final UInt64 forwardedFinalizedSlot = forwarded.getFinalizedHeader().getBeacon().getSlot();
    if (!finalizedSlot.equals(forwardedFinalizedSlot)) {
      return finalizedSlot.isGreaterThan(forwardedFinalizedSlot);
    }
    return LightClientUpdateStore.hasSupermajority(update.getSyncAggregate())
        && !LightClientUpdateStore.hasSupermajority(forwarded.getSyncAggregate());
  }
}
