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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

/**
 * Execution payload envelope retrieval follows the same layering as blocks: the hot store is
 * consulted first and, on a miss, {@link CombinedChainDataClient} falls back to historical data.
 * {@code Store} itself stays hot-only, matching {@code Store.retrieveSignedBlock}.
 */
class CombinedChainDataClientGloasTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);

  private final SignedExecutionPayloadEnvelope historicalEnvelope =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
  private final Bytes32 historicalBlockRoot = historicalEnvelope.getBeaconBlockRoot();
  private final AtomicInteger historicalLookups = new AtomicInteger(0);

  /**
   * Stands in for the database-backed unblinding provider: serves exactly one finalized envelope
   * and counts how often it is consulted.
   */
  private final ExecutionPayloadProvider executionPayloadProvider =
      blockRoots -> {
        historicalLookups.incrementAndGet();
        return SafeFuture.completedFuture(
            blockRoots.contains(historicalBlockRoot)
                ? Map.of(historicalBlockRoot, historicalEnvelope)
                : Map.of());
      };

  private final CombinedChainDataClient client =
      new CombinedChainDataClient(
          recentChainData, historicalChainData, spec, executionPayloadProvider);

  @Test
  public void getExecutionPayloadByBlockRoot_returnsHotEnvelopeWithoutConsultingHistorical() {
    final SignedExecutionPayloadEnvelope hotEnvelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(2);
    final Bytes32 blockRoot = hotEnvelope.getBeaconBlockRoot();
    when(recentChainData.retrieveSignedExecutionPayloadByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(hotEnvelope)));

    assertThat(client.getExecutionPayloadByBlockRoot(blockRoot))
        .isCompletedWithValue(Optional.of(hotEnvelope));
    assertThat(historicalLookups).hasValue(0);
  }

  @Test
  public void getExecutionPayloadByBlockRoot_fallsBackToHistoricalWhenNotInHotStore() {
    // Finalized block: pruned out of fork choice, so the hot store reports nothing.
    when(recentChainData.retrieveSignedExecutionPayloadByBlockRoot(historicalBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(client.getExecutionPayloadByBlockRoot(historicalBlockRoot))
        .isCompletedWithValue(Optional.of(historicalEnvelope));
    assertThat(historicalLookups).hasValue(1);
  }

  @Test
  public void getExecutionPayloadByBlockRoot_returnsEmptyWhenUnknownEverywhere() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(recentChainData.retrieveSignedExecutionPayloadByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(client.getExecutionPayloadByBlockRoot(blockRoot))
        .isCompletedWithValue(Optional.empty());
    assertThat(historicalLookups).hasValue(1);
  }
}
