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

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

public class TerminalPowBlockMonitor {
  private static final Logger LOG = LogManager.getLogger();

  private final EventLogger eventLogger;
  private final ExecutionLayerChannel executionLayer;
  private final AsyncRunner asyncRunner;
  private Optional<Cancellable> timer = Optional.empty();
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ForkChoiceNotifier forkChoiceNotifier;

  private Duration pollingPeriod;

  private Optional<Bytes32> blockHashTracking = Optional.empty();
  private Optional<Bytes32> foundTerminalBlockHash = Optional.empty();
  private SpecConfigBellatrix specConfigBellatrix;
  private boolean isBellatrixActive = false;
  private boolean inSync = true;

  public TerminalPowBlockMonitor(
      final ExecutionLayerChannel executionLayer,
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoiceNotifier forkChoiceNotifier,
      final AsyncRunner asyncRunner,
      final EventLogger eventLogger) {
    this.executionLayer = executionLayer;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.eventLogger = eventLogger;
  }

  public synchronized void start() {
    if (timer.isPresent()) {
      return;
    }

    Optional<SpecConfigBellatrix> maybeSpecConfigBellatrix = getSpecConfigBellatrix();
    if (maybeSpecConfigBellatrix.isEmpty()) {
      LOG.error("Bellatrix spec config not found. Monitor will shutdown.");
      stop();
      return;
    }
    specConfigBellatrix = maybeSpecConfigBellatrix.get();

    final boolean isMergeTransitionComplete =
        isMergeTransitionComplete(recentChainData.getChainHead());
    if (!isMergeTransitionComplete && specConfigBellatrix.getTerminalBlockHash().isZero()) {
      throw new InvalidConfigurationException(
          "Bellatrix transition by terminal total difficulty is no more supported");
    }

    pollingPeriod = Duration.ofSeconds(spec.getGenesisSpec().getConfig().getSecondsPerEth1Block());
    timer =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::monitor,
                pollingPeriod,
                (error) -> LOG.error("An error occurred while executing the monitor task", error)));
    LOG.debug(
        "Monitor has started. Waiting BELLATRIX fork activation. Polling every {}", pollingPeriod);
  }

  public synchronized void stop() {
    if (timer.isEmpty()) {
      return;
    }
    timer.get().cancel();
    timer = Optional.empty();
    LOG.debug("TTD monitoring has stopped.");
  }

  public synchronized boolean isRunning() {
    return timer.isPresent();
  }

  public synchronized void onNodeSyncStateChanged(final boolean inSync) {
    this.inSync = inSync;
  }

  private synchronized void monitor() {
    if (!isBellatrixActive) {
      initMergeState();
      if (!isBellatrixActive) {
        LOG.trace("Monitor is sill inactive. BELLATRIX fork not yet activated.");
        return;
      }
    }

    if (isMergeTransitionComplete(recentChainData.getChainHead())) {
      LOG.debug("MERGE is completed.");
      stop();
      return;
    }

    if (!inSync) {
      LOG.debug("Node is syncing, skipping check.");
      return;
    }

    checkTerminalBlockByBlockHash(blockHashTracking.orElseThrow());
  }

  private Boolean isMergeTransitionComplete(final Optional<ChainHead> chainHead) {
    return chainHead.map(head -> !head.getExecutionBlockHash().isZero()).orElse(false);
  }

  private void initMergeState() {
    Optional<UInt64> maybeEpoch = recentChainData.getCurrentEpoch();
    if (maybeEpoch.isEmpty()) {
      return;
    }

    if (specConfigBellatrix.getBellatrixForkEpoch().isGreaterThan(maybeEpoch.get())) {
      LOG.trace("Bellatrix not yet activated");
      return;
    }

    final Optional<ChainHead> chainHead = recentChainData.getChainHead();
    if (chainHead.isEmpty()) {
      LOG.trace("Beacon state not yet available");
      return;
    }

    if (isMergeTransitionComplete(chainHead)) {
      LOG.debug("MERGE is completed. Stopping.");
      stop();
      return;
    }

    blockHashTracking = Optional.of(specConfigBellatrix.getTerminalBlockHash());
    LOG.debug(
        "Enabling tracking by Block Hash {} and Activation Epoch {}",
        specConfigBellatrix.getTerminalBlockHash(),
        specConfigBellatrix.getTerminalBlockHashActivationEpoch());

    isBellatrixActive = true;
  }

  private void checkTerminalBlockByBlockHash(final Bytes32 blockHashTracking) {
    final UInt64 slot = recentChainData.getCurrentSlot().orElseThrow();
    final SpecVersion specVersion = spec.atSlot(slot);

    final boolean isActivationEpochReached =
        specVersion
            .miscHelpers()
            .computeEpochAtSlot(slot)
            .isGreaterThanOrEqualTo(specConfigBellatrix.getTerminalBlockHashActivationEpoch());

    if (isActivationEpochReached) {
      executionLayer
          .eth1GetPowBlock(blockHashTracking)
          .thenAccept(
              maybePowBlock ->
                  maybePowBlock
                      .map(PowBlock::getBlockHash)
                      .map(blockHashTracking::equals)
                      .ifPresent(
                          found -> {
                            if (!found) {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block not found.");
                              return;
                            }

                            if (notYetFound(blockHashTracking)) {
                              LOG.trace("checkTerminalBlockByBlockHash: Terminal Block found!");
                              onTerminalPowBlockFound(blockHashTracking);
                            }
                          }))
          .finish(
              error -> LOG.error("Unexpected error while searching Terminal Block by Hash", error));
    }
  }

  private void onTerminalPowBlockFound(final Bytes32 blockHash) {
    foundTerminalBlockHash = Optional.of(blockHash);
    forkChoiceNotifier.onTerminalBlockReached(blockHash);
    eventLogger.terminalPowBlockDetected(blockHash);
  }

  private boolean notYetFound(final Bytes32 blockHash) {
    return !foundTerminalBlockHash.map(blockHash::equals).orElse(false);
  }

  private Optional<SpecConfigBellatrix> getSpecConfigBellatrix() {
    final SpecVersion bellatrixMilestone = spec.forMilestone(SpecMilestone.BELLATRIX);
    if (bellatrixMilestone == null) {
      return Optional.empty();
    }
    return bellatrixMilestone.getConfig().toVersionBellatrix();
  }
}
