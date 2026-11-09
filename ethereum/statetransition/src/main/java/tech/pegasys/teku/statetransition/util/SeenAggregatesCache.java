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

package tech.pegasys.teku.statetransition.util;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitSet;

public class SeenAggregatesCache<KeyT> {

  private final Map<KeyT, Set<SszBitSet>> seenAggregationBitsByDataRoot;

  public SeenAggregatesCache(final int rootCacheSize) {
    this.seenAggregationBitsByDataRoot = LimitedMap.createSynchronizedLRU(rootCacheSize);
  }

  public boolean add(final KeyT root, final SszBitSet aggregationBits) {
    final Set<SszBitSet> seenBitlists =
        seenAggregationBitsByDataRoot.computeIfAbsent(
            root, key -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    if (isAlreadySeen(seenBitlists, aggregationBits)) {
      return false;
    }
    return seenBitlists.add(aggregationBits);
  }

  public boolean isAlreadySeen(final KeyT root, final SszBitSet aggregationBits) {
    final Set<SszBitSet> seenAggregates =
        seenAggregationBitsByDataRoot.getOrDefault(root, Collections.emptySet());
    return isAlreadySeen(seenAggregates, aggregationBits);
  }

  private boolean isAlreadySeen(
      final Set<SszBitSet> seenAggregates, final SszBitSet aggregationBits) {
    return seenAggregates.stream().anyMatch(seen -> seen.isSuperSetOf(aggregationBits));
  }
}
