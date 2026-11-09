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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

class ForkProviderTest {
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.valueOf(2));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);

  private final Bytes32 genesisValidatorsRoot = dataStructureUtil.randomBytes32();

  private final ForkProvider forkProvider = new ForkProvider(spec, genesisDataProvider);

  @BeforeEach
  void setUp() {
    when(genesisDataProvider.getGenesisValidatorsRoot())
        .thenReturn(SafeFuture.completedFuture(genesisValidatorsRoot));
  }

  @Test
  void shouldLookupCorrectFork() {
    final ForkInfo phase0 =
        new ForkInfo(spec.getForkSchedule().getFork(UInt64.ZERO), genesisValidatorsRoot);
    final ForkInfo altair =
        new ForkInfo(spec.getForkSchedule().getFork(UInt64.valueOf(2)), genesisValidatorsRoot);

    // Should convert slot to epoch when retrieving fork.
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(0))).isCompletedWithValue(phase0);
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(1))).isCompletedWithValue(phase0);
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(2))).isCompletedWithValue(phase0);
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(15))).isCompletedWithValue(phase0);

    assertThat(forkProvider.getForkInfo(UInt64.valueOf(16))).isCompletedWithValue(altair);
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(18))).isCompletedWithValue(altair);
    // This is the last fork so should continue indefinitely
    assertThat(forkProvider.getForkInfo(UInt64.valueOf(24982))).isCompletedWithValue(altair);
  }
}
