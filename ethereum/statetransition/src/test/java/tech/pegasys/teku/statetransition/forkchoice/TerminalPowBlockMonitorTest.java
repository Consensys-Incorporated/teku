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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.builder.BellatrixBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class TerminalPowBlockMonitorTest {
  private static final UInt64 BELLATRIX_FORK_EPOCH = UInt64.ONE;
  private static final UInt256 TTD = UInt256.valueOf(10_000_000);
  private static final Bytes32 TERMINAL_BLOCK_HASH = Bytes32.random();
  private static final UInt64 TERMINAL_BLOCK_EPOCH = UInt64.valueOf(2);
  private static final UInt64 TIME_IN_PAST = UInt64.valueOf(123);

  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final EventLogger eventLogger = Mockito.mock(EventLogger.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private TerminalPowBlockMonitor terminalPowBlockMonitor;

  private void setUpTerminalBlockHashConfig() {
    setUpCommon(
        bellatrixBuilder ->
            bellatrixBuilder
                .terminalBlockHash(TERMINAL_BLOCK_HASH)
                .terminalBlockHashActivationEpoch(TERMINAL_BLOCK_EPOCH));
  }

  private void setUpTTDConfig() {
    setUpCommon(bellatrixBuilder -> bellatrixBuilder.terminalTotalDifficulty(TTD));
  }

  private void setUpCommon(final Consumer<BellatrixBuilder> bellatrixBuilder) {
    spec =
        TestSpecFactory.createBellatrix(
            SpecConfigLoader.loadConfig(
                "minimal",
                phase0Builder ->
                    phase0Builder
                        .blsSignatureVerifier(BLSSignatureVerifier.NOOP)
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(BELLATRIX_FORK_EPOCH)
                        .bellatrixBuilder(bellatrixBuilder)));
    dataStructureUtil = new DataStructureUtil(spec);
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();

    terminalPowBlockMonitor =
        new TerminalPowBlockMonitor(
            executionLayer, spec, recentChainData, forkChoiceNotifier, asyncRunner, eventLogger);

    terminalPowBlockMonitor.onNodeSyncStateChanged(true);
  }

  private void goToSlot(final UInt64 slot) {
    storageSystem
        .chainUpdater()
        .updateBestBlock(storageSystem.chainUpdater().advanceChainUntil(slot));
  }

  private void doMerge(final Bytes32 terminalBlockHash) {
    SignedBlockAndState newBlockWithExecutionPayloadAtopTerminalBlock =
        storageSystem
            .chainUpdater()
            .chainBuilder
            .generateBlockAtSlot(
                recentChainData.getHeadSlot().plus(1),
                ChainBuilder.BlockOptions.create().setTerminalBlockHash(terminalBlockHash));

    storageSystem.chainUpdater().updateBestBlock(newBlockWithExecutionPayloadAtopTerminalBlock);
  }

  @Test
  public void shouldThrowIfTTDOnlyConfigured() {
    setUpTTDConfig();

    assertThatThrownBy(() -> terminalPowBlockMonitor.start())
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining(
            "Bellatrix transition by terminal total difficulty is no more supported");
    verifyNoMoreInteractions(executionLayer);
  }

  @Test
  public void shouldHandleLatestPowBlockBeingNull() {
    setUpTerminalBlockHashConfig();

    terminalPowBlockMonitor.start();
    try (LogCaptor logCaptor = LogCaptor.forClass(TerminalPowBlockMonitor.class)) {
      // AT BELLATRIX FORK, TTD reached but block is null (EL not in sync or doing something weird)
      goToSlot(BELLATRIX_FORK_EPOCH.times(spec.getGenesisSpecConfig().getSlotsPerEpoch()));
      when(executionLayer.eth1GetPowChainHead()).thenReturn(completedFuture(null));

      asyncRunner.executeQueuedActions();
      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
  }

  @Test
  void shouldPerformTerminalBlockDetectionByTerminalBlockHash() {
    setUpTerminalBlockHashConfig();

    terminalPowBlockMonitor.start();

    // NOT YET BELLATRIX FORK - should not notify
    goToSlot(UInt64.ONE);

    assertThat(terminalPowBlockMonitor.isRunning()).isTrue();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();

    verify(executionLayer, times(0)).eth1GetPowBlock(any());
    verify(forkChoiceNotifier, times(0)).onTerminalBlockReached(any());

    // AT BELLATRIX FORK, Terminal Bloch Epoch not reached - should not notify
    goToSlot(BELLATRIX_FORK_EPOCH.times(spec.getGenesisSpecConfig().getSlotsPerEpoch()));

    asyncRunner.executeQueuedActions();

    verify(executionLayer, times(0)).eth1GetPowBlock(any());
    verify(forkChoiceNotifier, times(0)).onTerminalBlockReached(any());

    // AT Terminal Bloch Epoch, Terminal Block Hash not found - should not notify
    goToSlot(TERMINAL_BLOCK_EPOCH.times(spec.getGenesisSpecConfig().getSlotsPerEpoch()));
    when(executionLayer.eth1GetPowBlock(TERMINAL_BLOCK_HASH))
        .thenReturn(completedFuture(Optional.empty()));

    asyncRunner.executeQueuedActions();

    verify(executionLayer, times(1)).eth1GetPowBlock(TERMINAL_BLOCK_HASH);
    verify(forkChoiceNotifier, times(0)).onTerminalBlockReached(any());

    // AT Terminal Bloch Epoch, Terminal Block Hash found - should notify
    when(executionLayer.eth1GetPowBlock(TERMINAL_BLOCK_HASH))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new PowBlock(
                        TERMINAL_BLOCK_HASH, dataStructureUtil.randomBytes32(), TIME_IN_PAST))));

    asyncRunner.executeQueuedActions();

    verify(eventLogger).terminalPowBlockDetected(TERMINAL_BLOCK_HASH);
    verify(executionLayer, times(2)).eth1GetPowBlock(TERMINAL_BLOCK_HASH);
    verify(forkChoiceNotifier, times(1)).onTerminalBlockReached(TERMINAL_BLOCK_HASH);

    // MERGE Completed - should stop
    doMerge(TERMINAL_BLOCK_HASH);
    asyncRunner.executeQueuedActions();
    assertThat(terminalPowBlockMonitor.isRunning()).isFalse();

    // final check
    verifyNoMoreInteractions(executionLayer);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  void shouldImmediatelyStopWhenMergeCompleted() {
    setUpTerminalBlockHashConfig();
    goToSlot(BELLATRIX_FORK_EPOCH.times(spec.getGenesisSpecConfig().getSlotsPerEpoch()));
    doMerge(TERMINAL_BLOCK_HASH);

    terminalPowBlockMonitor.start();

    asyncRunner.executeQueuedActions();

    assertThat(terminalPowBlockMonitor.isRunning()).isFalse();
  }

  @Test
  void shouldNotPerformCheckIfSyncing() {
    setUpTerminalBlockHashConfig();

    terminalPowBlockMonitor.start();

    terminalPowBlockMonitor.onNodeSyncStateChanged(false);

    goToSlot(BELLATRIX_FORK_EPOCH.times(spec.getGenesisSpecConfig().getSlotsPerEpoch()));

    asyncRunner.executeQueuedActions();

    verifyNoMoreInteractions(executionLayer);
  }
}
