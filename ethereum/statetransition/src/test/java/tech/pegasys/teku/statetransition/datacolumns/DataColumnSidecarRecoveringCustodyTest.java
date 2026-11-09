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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand.createCustodyGroupCountManager;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

@SuppressWarnings("FutureReturnValueIgnored")
public class DataColumnSidecarRecoveringCustodyTest {

  private final Spec spec = spy(TestSpecFactory.createMinimalFulu());
  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  private final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  private final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  private final DataColumnSidecarCustody delegate = mock(DataColumnSidecarCustody.class);
  private final SpecVersion specVersion = spy(spec.forMilestone(SpecMilestone.FULU));
  private final MiscHelpersFulu miscHelpersFulu = mock(MiscHelpersFulu.class);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final BiConsumer<DataColumnSidecar, RemoteOrigin> dataColumnSidecarPublisher =
      mock(BiConsumer.class);

  private final Supplier<Stream<UInt64>> columnIndices =
      () ->
          Stream.iterate(
              UInt64.ZERO,
              i -> i.isLessThan(UInt64.valueOf(config.getNumberOfColumns())),
              UInt64::increment);
  private final UInt64 slot = UInt64.ONE;
  private final BeaconBlockBody beaconBlockBody =
      dataStructureUtil.randomBeaconBlockBodyWithCommitments(1);
  private final BeaconBlock block = dataStructureUtil.randomBeaconBlock(slot, beaconBlockBody);
  private final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.signedBlock(block);
  private DataColumnSidecarRecoveringCustodyImpl custody;

  @BeforeEach
  public void setup() {
    this.custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            dataColumnSidecarPublisher,
            createCustodyGroupCountManager(
                config.getNumberOfCustodyGroups(), config.getSamplesPerSlot()),
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider);
    doReturn(specVersion).when(spec).atSlot(any());
    doReturn(miscHelpersFulu).when(specVersion).miscHelpers();
    when(miscHelpersFulu.toVersionFulu()).thenReturn(Optional.of(miscHelpersFulu));
    when(delegate.onNewValidatedDataColumnSidecar(any(), any())).thenReturn(SafeFuture.COMPLETE);
    custody.onSyncingStatusChanged(true); // default in sync
  }

  @Test
  public void shouldNotWorkUntilFulu() {
    final Spec spec = TestSpecFactory.createMinimalElectra();
    final DataColumnSidecarRecoveringCustody custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            dataColumnSidecarPublisher,
            createCustodyGroupCountManager(0, config.getSamplesPerSlot()),
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider);

    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldNotWorkOnNonSupernode() {
    final DataColumnSidecarRecoveringCustody custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            dataColumnSidecarPublisher,
            CustodyGroupCountManager.NOOP,
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider);

    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  public void shouldWorkOnFuluSupernode() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(70)
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));

    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    verify(miscHelpersFulu).reconstructAllDataColumnSidecars(anyCollection());

    columnIndices
        .get()
        .limit(30)
        .forEach(
            i -> {
              verify(delegate)
                  .onNewValidatedDataColumnSidecar(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
              verify(dataColumnSidecarPublisher)
                  .accept(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
            });
    columnIndices
        .get()
        .skip(100)
        .forEach(
            i -> {
              verify(delegate)
                  .onNewValidatedDataColumnSidecar(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
              verify(dataColumnSidecarPublisher)
                  .accept(eq(sidecars.get(i)), eq(RemoteOrigin.RECOVERED));
            });
  }

  @Test
  public void shouldNotWorkOnFuluSupernodeForLocalProposal() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(70)
        .forEach(
            sidecar ->
                custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.LOCAL_PROPOSAL));
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    verifyNoInteractions(miscHelpersFulu);
  }

  @Test
  public void shouldNotWorkOnFuluSupernodeForLocalEL() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(70)
        .forEach(
            sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.LOCAL_EL));
    stubAsyncRunner.executeQueuedActions();
    stubAsyncRunner.executeQueuedActions();

    verifyNoInteractions(miscHelpersFulu);
  }

  @Test
  public void shouldWaitUntilTimeouted() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(70)
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));

    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(2);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(miscHelpersFulu).reconstructAllDataColumnSidecars(anyCollection());

    // post reconstructed
    verify(delegate, times(58)).onNewValidatedDataColumnSidecar(any(), eq(RemoteOrigin.RECOVERED));
    verify(dataColumnSidecarPublisher, times(58)).accept(any(), eq(RemoteOrigin.RECOVERED));
  }

  @Test
  public void shouldWaitUntilTimeoutedAndHalfOfSidecars() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(63)
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));

    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    custody.onNewValidatedDataColumnSidecar(sidecars.get(UInt64.ZERO), RemoteOrigin.RPC);

    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(miscHelpersFulu).reconstructAllDataColumnSidecars(anyCollection());

    // post reconstructed
    verify(delegate, times(64)).onNewValidatedDataColumnSidecar(any(), eq(RemoteOrigin.RECOVERED));
    verify(dataColumnSidecarPublisher, times(64)).accept(any(), eq(RemoteOrigin.RECOVERED));
  }

  @Test
  void shouldNotPublishDataColumnSidecarWhileSyncing() {
    custody.onSlot(slot);
    custody.onSyncingStatusChanged(false);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars.values().stream()
        .skip(30)
        .limit(70)
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));

    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenReturn(sidecars.values().stream().toList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(2);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verify(miscHelpersFulu).reconstructAllDataColumnSidecars(anyCollection());

    // post reconstructed
    verify(delegate, times(58)).onNewValidatedDataColumnSidecar(any(), eq(RemoteOrigin.RECOVERED));
    verify(dataColumnSidecarPublisher, never()).accept(any(), any());
  }

  @Test
  public void shouldPreserveTaskIsStartedWhenSuccess() {
    final Map<DataColumnSlotAndIdentifier, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .limit(64)
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(
                Collectors.toMap(DataColumnSlotAndIdentifier::fromDataColumn, sidecar -> sidecar));

    final DataColumnSidecarRecoveringCustodyImpl.RecoveryTask task =
        new DataColumnSidecarRecoveringCustodyImpl.RecoveryTask(
            new SlotAndBlockRoot(UInt64.ZERO, Bytes32.ZERO),
            sidecars,
            new AtomicBoolean(true),
            new AtomicBoolean(true));

    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
    custody.scheduleRecoveryTask(task);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();
    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenReturn(Collections.emptyList());
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(task.recoveryStarted()).isTrue();
  }

  @Test
  public void shouldResetTaskIsStartedOnException() {
    final DataColumnSidecarRecoveringCustodyImpl.RecoveryTask task =
        new DataColumnSidecarRecoveringCustodyImpl.RecoveryTask(
            new SlotAndBlockRoot(UInt64.ZERO, Bytes32.ZERO),
            Collections.emptyMap(),
            new AtomicBoolean(true),
            new AtomicBoolean(true));

    assertThat(stubAsyncRunner.hasDelayedActions()).isFalse();
    custody.scheduleRecoveryTask(task);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();
    when(miscHelpersFulu.reconstructAllDataColumnSidecars(anyCollection()))
        .thenThrow(new RuntimeException("Simulated exception"));
    stubAsyncRunner.executeDueActionsRepeatedly();
    assertThat(task.recoveryStarted()).isFalse();
  }

  @Test
  public void shouldNotStartWhenAllSidecarsAreAvailable() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars
        .values()
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));

    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    verifyNoInteractions(miscHelpersFulu);
    verify(dataColumnSidecarPublisher, never()).accept(any(), eq(RemoteOrigin.RECOVERED));
  }

  @Test
  public void shouldAddToCompletedSlotsWhenCompleted() {
    custody.onSlot(slot);
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

    final Map<UInt64, DataColumnSidecar> sidecars =
        columnIndices
            .get()
            .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
            .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
    sidecars
        .values()
        .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));
    verify(delegate, times(sidecars.size())).onNewValidatedDataColumnSidecar(any(), any());

    assertThat(custody.getCompletedSlots()).isEmpty();

    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    assertThat(custody.getCompletedSlots()).contains(signedBeaconBlock.getSlotAndBlockRoot());

    // doesn't pass sidecars further when it's proved as completed
    custody.onNewValidatedDataColumnSidecar(
        sidecars.values().stream().findFirst().get(), RemoteOrigin.RPC);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  public void shouldPruneRecoveryTasks() {
    // recovery tasks size target is slots per epoch, minimal is 8, so adding 9, 1st should gone
    for (UInt64 slot = UInt64.ONE;
        slot.isLessThanOrEqualTo(UInt64.valueOf(9));
        slot = slot.increment()) {
      custody.onSlot(slot);
      assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

      final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
      final Map<UInt64, DataColumnSidecar> sidecars =
          columnIndices
              .get()
              .map(i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
              .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
      sidecars
          .values()
          .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));
    }

    assertThat(custody.getCompletedSlots()).isEmpty();
    assertThat(custody.getRecoveryTasks().size()).isEqualTo(8);
    // replaced with 2nd because prune target is 8
    assertThat(
            custody.getRecoveryTasks().keySet().stream()
                .map(SlotAndBlockRoot::getSlot)
                .collect(Collectors.toSet()))
        .doesNotContain(UInt64.ONE);

    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();
    stubTimeProvider.advanceTimeBySeconds(1);
    stubAsyncRunner.executeDueActionsRepeatedly();

    assertThat(custody.getCompletedSlots().size()).isEqualTo(8);
  }

  @Test
  public void shouldPruneCompletedSlots() {
    this.custody =
        new DataColumnSidecarRecoveringCustodyImpl(
            delegate,
            stubAsyncRunner,
            spec,
            dataColumnSidecarPublisher,
            createCustodyGroupCountManager(
                config.getNumberOfCustodyGroups(), config.getSamplesPerSlot()),
            config.getNumberOfColumns(),
            config.getNumberOfCustodyGroups(),
            __ -> Duration.ofSeconds(2),
            stubMetricsSystem,
            stubTimeProvider,
            8,
            16);

    final int slotsPerEpoch = spec.getGenesisSpec().getSlotsPerEpoch();
    // completed slots size target is 16, let's run 3 * 8
    for (long j = 0; j < 3; ++j) {
      for (UInt64 slot = UInt64.ONE.plus(slotsPerEpoch * j);
          slot.isLessThanOrEqualTo(UInt64.valueOf(8).plus(slotsPerEpoch * j));
          slot = slot.increment()) {
        custody.onSlot(slot);
        assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();

        final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBeaconBlock(slot);
        final Map<UInt64, DataColumnSidecar> sidecars =
            columnIndices
                .get()
                .map(
                    i -> dataStructureUtil.randomDataColumnSidecar(signedBeaconBlock.asHeader(), i))
                .collect(Collectors.toMap(DataColumnSidecar::getIndex, sidecar -> sidecar));
        sidecars
            .values()
            .forEach(sidecar -> custody.onNewValidatedDataColumnSidecar(sidecar, RemoteOrigin.RPC));
      }

      stubAsyncRunner.executeDueActionsRepeatedly();
      stubTimeProvider.advanceTimeBySeconds(1);
      stubAsyncRunner.executeDueActionsRepeatedly();
      stubTimeProvider.advanceTimeBySeconds(1);
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
    // we didn't have onSlot (which fires prune) after last 8 recovery tasks execution
    custody.onSlot(UInt64.valueOf(3 * 8 + 1));

    assertThat(custody.getCompletedSlots().size()).isEqualTo(8 * 2 - 1);
    assertThat(custody.getCompletedSlots().getFirst().getSlot()).isEqualTo(UInt64.valueOf(10));
    assertThat(custody.getCompletedSlots().getLast().getSlot()).isEqualTo(UInt64.valueOf(8 * 3));
  }
}
