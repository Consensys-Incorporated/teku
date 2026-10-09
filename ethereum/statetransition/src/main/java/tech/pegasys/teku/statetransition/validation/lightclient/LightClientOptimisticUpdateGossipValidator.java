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
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.RecentChainData;

public class LightClientOptimisticUpdateGossipValidator
    extends LightClientGossipValidator<LightClientOptimisticUpdate> {
  private final LightClientUpdateStore store;

  public LightClientOptimisticUpdateGossipValidator(
      final Spec spec, final RecentChainData recentChainData, final LightClientUpdateStore store) {
    super(spec, recentChainData);
    this.store = store;
  }

  @Override
  protected String updateName() {
    return "Optimistic update";
  }

  @Override
  protected UInt64 getSignatureSlot(final LightClientOptimisticUpdate update) {
    return update.getSignatureSlot().get();
  }

  @Override
  protected Optional<LightClientOptimisticUpdate> getLocalUpdate() {
    return store.getLatestOptimisticUpdate();
  }

  @Override
  protected boolean isNewer(
      final LightClientOptimisticUpdate update, final LightClientOptimisticUpdate forwarded) {
    return update
        .getAttestedHeader()
        .getBeacon()
        .getSlot()
        .isGreaterThan(forwarded.getAttestedHeader().getBeacon().getSlot());
  }
}
