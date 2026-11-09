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

package tech.pegasys.teku.infrastructure.ssz.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.ContainerReadImpl;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.WritableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;

public class AbstractSszCompositeTest {

  @Test
  public void getUsesCachedLookupAndStoresMissWithoutCallback() {
    final TrackingIntCache cache = new TrackingIntCache();
    final ContainerReadImpl container =
        new ContainerReadImpl(
            WritableContainer.SSZ_SCHEMA, WritableContainer.SSZ_SCHEMA.getDefaultTree(), cache);

    final SszData child = container.get(0);

    assertThat(child).isNotNull();
    assertThat(cache.getIntCalls).isZero();
    assertThat(cache.cachedIntLookups).containsExactly(0);
    assertThat(cache.values).containsOnlyKeys(0);

    assertThat(container.get(0)).isSameAs(child);
    assertThat(cache.getIntCalls).isZero();
    assertThat(cache.cachedIntLookups).containsExactly(0, 0);
  }

  private static class TrackingIntCache implements IntCache<SszData> {
    private final Map<Integer, SszData> values = new HashMap<>();
    private final List<Integer> cachedIntLookups = new ArrayList<>();
    private int getIntCalls = 0;

    @Override
    public SszData getInt(final int key, final IntFunction<SszData> fallback) {
      getIntCalls++;
      throw new AssertionError("getInt allocates a callback for AbstractSszComposite.get");
    }

    @Override
    public SszData getCachedInt(final int key) {
      cachedIntLookups.add(key);
      return values.get(key);
    }

    @Override
    public Optional<SszData> getCached(final Integer key) {
      return Optional.ofNullable(values.get(key));
    }

    @Override
    public IntCache<SszData> copy() {
      final TrackingIntCache copy = new TrackingIntCache();
      copy.values.putAll(values);
      return copy;
    }

    @Override
    public void invalidateInt(final int key) {
      values.remove(key);
    }

    @Override
    public void invalidateWithNewValueInt(final int key, final SszData newValue) {
      values.put(key, newValue);
    }

    @Override
    public void clear() {
      values.clear();
    }
  }
}
