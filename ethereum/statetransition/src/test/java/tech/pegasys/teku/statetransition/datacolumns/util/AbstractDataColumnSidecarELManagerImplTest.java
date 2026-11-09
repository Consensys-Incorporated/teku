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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand.createCustodyGroupCountManager;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELManager;
import tech.pegasys.teku.statetransition.datacolumns.ValidDataColumnSidecarsListener;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractDataColumnSidecarELManagerImplTest {
  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;
  protected final UInt64 historicalTolerance = UInt64.valueOf(5);
  protected final MetricsSystem metricsSystem = new StubMetricsSystem();
  protected final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(ZERO);
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  protected final RecentChainData recentChainData = mock(RecentChainData.class);
  protected final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  protected final DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator =
      mock(DataColumnSidecarGossipValidator.class);
  protected final KZG kzg = mock(KZG.class);
  protected final int custodyGroupCount = 4;
  protected final int sampleGroupCount = 8;

  protected final List<KZGCell> kzgCells =
      IntStream.range(0, 128).mapToObj(__ -> new KZGCell(Bytes.random(2048))).toList();

  @SuppressWarnings("unchecked")
  protected final BiConsumer<List<DataColumnSidecar>, RemoteOrigin> dataColumnSidecarPublisher =
      mock(BiConsumer.class);

  protected final ValidDataColumnSidecarsListener validDataColumnSidecarsListener =
      mock(ValidDataColumnSidecarsListener.class);

  protected static final Duration EL_BLOBS_FETCHING_DELAY = Duration.ofMillis(500);
  protected static final int EL_BLOBS_FETCHING_MAX_RETRIES = 3;

  protected CustodyGroupCountManager custodyGroupCountManager;
  protected DataColumnSidecarELManager dataColumnSidecarELManager;
  protected UInt64 currentSlot;

  protected abstract Spec createSpec();

  @BeforeEach
  public void setup() {
    spec = createSpec();
    dataStructureUtil = new DataStructureUtil(spec);
    custodyGroupCountManager = createCustodyGroupCountManager(custodyGroupCount, sampleGroupCount);
    dataColumnSidecarELManager =
        new PoolFactory(metricsSystem)
            .createDataColumnSidecarELManager(
                spec,
                asyncRunner,
                recentChainData,
                executionLayer,
                dataColumnSidecarPublisher,
                dataColumnSidecarGossipValidator,
                custodyGroupCountManager,
                metricsSystem,
                timeProvider);

    currentSlot = historicalTolerance.times(2);

    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(executionLayer.engineGetBlobAndProofs(any(), eq(currentSlot)))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    when(kzg.computeCells(any())).thenReturn(kzgCells);
    dataColumnSidecarELManager.onSyncingStatusChanged(true);
    dataColumnSidecarELManager.subscribeToRecoveredColumnSidecar(validDataColumnSidecarsListener);
    setSlot(currentSlot);
  }

  protected void setSlot(final UInt64 slot) {
    currentSlot = slot;
    dataColumnSidecarELManager.onSlot(slot);
    when(recentChainData.computeTimeAtSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  public void onNewBlock_ignoresLocal() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewBlock(block, Optional.of(RemoteOrigin.LOCAL_PROPOSAL));
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void shouldNotPublish_whenNotCurrentSlot() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELManager.onSlot(currentSlot.minus(1));
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);
    verifyNoInteractions(validDataColumnSidecarsListener);
  }
}
