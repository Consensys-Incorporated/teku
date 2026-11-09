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

package tech.pegasys.teku.dataproviders.generators;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class StateCache {
  private final Map<Bytes32, BeaconState> cache;
  private final Map<Bytes32, BeaconState> knownStates;

  public StateCache(final int maxCachedStates, final Map<Bytes32, BeaconState> knownStates) {
    this.cache = LimitedMap.createSynchronizedLRU(maxCachedStates);
    this.knownStates = knownStates;
  }

  boolean containsKnownState(final Bytes32 blockRoot) {
    return knownStates.containsKey(blockRoot);
  }

  @VisibleForTesting
  int countCachedStates() {
    return cache.size();
  }

  public Optional<BeaconState> get(final Bytes32 blockRoot) {
    return Optional.ofNullable(knownStates.get(blockRoot))
        .or(() -> Optional.ofNullable(cache.get(blockRoot)));
  }

  public void put(final Bytes32 blockRoot, final BeaconState state) {
    if (!knownStates.containsKey(blockRoot)) {
      cache.put(blockRoot, state);
    }
  }

  public void clear() {
    cache.clear();
  }
}
