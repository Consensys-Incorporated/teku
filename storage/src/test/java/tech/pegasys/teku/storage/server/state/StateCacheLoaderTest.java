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

package tech.pegasys.teku.storage.server.state;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.Database;

public class StateCacheLoaderTest {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Database database = mock(Database.class);
  private final FinalizedStateCache finalizedStateCache = mock(FinalizedStateCache.class);
  private LoadingCache<UInt64, BeaconState> cache;

  @Test
  void shouldRejectRegenerateIfTooFarAway() {
    final BeaconState availableState = dataStructureUtil.randomBeaconState(UInt64.ZERO);
    when(database.getLatestAvailableFinalizedState(any())).thenReturn(Optional.of(availableState));

    final CacheBuilder<UInt64, BeaconState> cacheBuilder =
        CacheBuilder.newBuilder()
            .maximumSize(2)
            .removalListener((k) -> LOG.info(String.format("removed %s", k.getKey())));
    this.cache =
        cacheBuilder.build(new StateCacheLoader(spec, database, 1, 2, finalizedStateCache));
    try (LogCaptor logCaptor = LogCaptor.forClass(StateCacheLoader.class)) {
      assertThatThrownBy(() -> cache.get(UInt64.valueOf(4)))
          .hasCauseInstanceOf(FinalizedStateCache.StateUnavailableException.class);
      logCaptor.assertErrorLog(
          "Refusing to regenerate a state that is 4 slots from what we have stored");
    }
  }
}
