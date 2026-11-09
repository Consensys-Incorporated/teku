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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BuilderIndexCacheTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());
  final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
  final BLSPublicKey missingPublicKey = dataStructureUtil.randomPublicKey();

  @SuppressWarnings("unchecked")
  final Cache<BLSPublicKey, Integer> cache = mock(Cache.class);

  @Test
  public void shouldNotScanStateIfAlreadyHaveBuilders() {
    final BuilderIndexCache builderIndexCache =
        new BuilderIndexCache(cache, state.getBuilders().size());

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = builderIndexCache.getBuilderIndex(state, missingPublicKey);

    verify(cache).getCached(missingPublicKey);
    verify(cache, never()).invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldScanNewBuildersInSuppliedState() {
    final int initialLastCachedIndex = state.getBuilders().size() - 3;
    final BuilderIndexCache builderIndexCache =
        new BuilderIndexCache(cache, initialLastCachedIndex);

    when(cache.getCached(missingPublicKey)).thenReturn(Optional.empty());
    final Optional<Integer> index = builderIndexCache.getBuilderIndex(state, missingPublicKey);
    verify(cache).getCached(missingPublicKey);
    verify(cache, times(state.getBuilders().size() - initialLastCachedIndex - 1))
        .invalidateWithNewValue(any(), any());
    assertThat(index).isEmpty();
  }

  @Test
  public void shouldGetAllBuilderKeysCachedIfMissingKeyPassed() {
    final BuilderIndexCache builderIndexCache = new BuilderIndexCache();
    final Optional<Integer> index = builderIndexCache.getBuilderIndex(state, missingPublicKey);
    assertThat(index).isEmpty();
    assertThat(builderIndexCache.getBuilderIndices().size()).isEqualTo(state.getBuilders().size());
  }

  @Test
  public void shouldPopulateCacheItemsFromState() {
    final BuilderIndexCache builderIndexCache = new BuilderIndexCache();
    final int targetIndex = state.getBuilders().size() - 1;
    final BLSPublicKey foundKey = state.getBuilders().get(targetIndex).getPublicKey();

    final Optional<Integer> index = builderIndexCache.getBuilderIndex(state, foundKey);
    assertThat(index).contains(targetIndex);
    assertThat(builderIndexCache.getLastCachedIndex()).isEqualTo(targetIndex);
    assertThat(builderIndexCache.getBuilderIndices().size()).isEqualTo(targetIndex + 1);
  }

  @Test
  public void shouldFilterItemsBeyondStateIndex() {
    final BuilderIndexCache builderIndexCache = new BuilderIndexCache();
    builderIndexCache.invalidateWithNewValue(missingPublicKey, 100);
    final Optional<Integer> index = builderIndexCache.getBuilderIndex(state, missingPublicKey);

    assertThat(index).isEmpty();
    // state didn't get scanned, because we had the index but it was out of bounds
    assertThat(builderIndexCache.getLastCachedIndex()).isEqualTo(-1);
    assertThat(builderIndexCache.getBuilderIndices().size()).isEqualTo(1);
  }

  @Test
  public void shouldInvalidateMapping() {
    final BuilderIndexCache builderIndexCache = new BuilderIndexCache();
    final BLSPublicKey existingKey = state.getBuilders().get(0).getPublicKey();

    // populate the cache
    assertThat(builderIndexCache.getBuilderIndex(state, existingKey)).contains(0);
    assertThat(builderIndexCache.getBuilderIndices().getCached(existingKey)).contains(0);

    // invalidate the mapping (simulates builder reassignment)
    builderIndexCache.invalidate(existingKey);
    assertThat(builderIndexCache.getBuilderIndices().getCached(existingKey)).isEmpty();
  }
}
