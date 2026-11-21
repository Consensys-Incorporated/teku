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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.cache.SoftRefIntCache;

class BeaconStateCacheRetentionTest {

  @Test
  void shouldRecreateViewCacheWithoutConsultingOriginatingState() {
    final AbstractBeaconState<?> state = mock(AbstractBeaconState.class, CALLS_REAL_METHODS);
    doReturn(1).when(state).size();
    final AtomicReference<Supplier<?>> factory = new AtomicReference<>();
    try (final var construction =
        mockConstruction(
            SoftRefIntCache.class,
            (cache, context) -> factory.set((Supplier<?>) context.arguments().getFirst()))) {
      final var cache = state.createCache();
      assertThat(cache).isSameAs(construction.constructed().getFirst());
    }

    // Creating the factory may read the state; using it after cache transfer must not.
    clearInvocations((Object) state);
    final IntCache<?> first = (IntCache<?>) factory.get().get();
    final IntCache<?> second = (IntCache<?>) factory.get().get();
    verifyNoInteractions(state);
    assertThat(second).isNotSameAs(first);
    assertThat(first.getCached(0)).isEmpty();
    assertThat(second.getCached(0)).isEmpty();
  }
}
