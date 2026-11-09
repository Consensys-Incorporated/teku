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

package tech.pegasys.teku.beacon.sync.historical;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.server.ShuttingDownException;

public class ReconstructHistoricalStatesService extends Service {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient chainDataClient;
  private final Optional<String> genesisStateResource;
  private final StorageUpdateChannel storageUpdateChannel;
  private final StatusLogger statusLogger;
  private final ProgressLogger progressLogger;

  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final SafeFuture<Void> stopped = new SafeFuture<>();

  public ReconstructHistoricalStatesService(
      final StorageUpdateChannel storageUpdateChannel,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem,
      final Optional<String> genesisStateResource) {
    this(
        storageUpdateChannel,
        chainDataClient,
        spec,
        timeProvider,
        metricsSystem,
        genesisStateResource,
        STATUS_LOG);
  }

  public ReconstructHistoricalStatesService(
      final StorageUpdateChannel storageUpdateChannel,
      final CombinedChainDataClient chainDataClient,
      final Spec spec,
      final TimeProvider timeProvider,
      final MetricsSystem metricsSystem,
      final Optional<String> genesisStateResource,
      final StatusLogger statusLogger) {
    this.storageUpdateChannel = storageUpdateChannel;
    this.chainDataClient = chainDataClient;
    this.spec = spec;
    this.genesisStateResource = genesisStateResource;
    this.statusLogger = statusLogger;
    this.progressLogger = new ProgressLogger(metricsSystem, statusLogger, timeProvider);
  }

  @Override
  protected SafeFuture<?> doStart() {
    if (genesisStateResource.isEmpty()) {
      return SafeFuture.failedFuture(
          new IllegalStateException("Genesis state resource not provided"));
    }

    final String resource = genesisStateResource.get();
    final BeaconState genesisState;
    try {
      genesisState = ChainDataLoader.loadState(spec, resource);
    } catch (IOException e) {
      LOG.error("Failed to load initial state", e);
      return SafeFuture.failedFuture(
          new InvalidConfigurationException(
              "Failed to load initial state from " + resource + ": " + e.getMessage()));
    }

    return chainDataClient
        .getInitialAnchor()
        .thenAccept(
            checkpoint -> {
              if (checkpoint.isEmpty()) {
                return;
              }
              final UInt64 anchorSlot = checkpoint.get().getEpochStartSlot(spec);

              chainDataClient
                  .getLatestAvailableFinalizedState(anchorSlot.minusMinZero(1))
                  .thenComposeChecked(
                      latestState -> {
                        if (latestState.isPresent()) {
                          final BeaconState state = latestState.get();
                          return SafeFuture.completedFuture(
                              new Context(state, state.getSlot().increment(), anchorSlot));
                        }

                        final Bytes32 genesisBlockRoot =
                            BeaconBlockHeader.fromState(genesisState).getRoot();
                        return storageUpdateChannel
                            .onReconstructedFinalizedState(genesisState, genesisBlockRoot)
                            .thenApply(
                                __ ->
                                    new Context(
                                        genesisState, GENESIS_SLOT.increment(), anchorSlot));
                      })
                  .thenComposeChecked(this::applyNextBlock)
                  .finish(
                      error -> {
                        final Throwable rootCause = Throwables.getRootCause(error);
                        if (rootCause instanceof ShuttingDownException
                            || rootCause instanceof InterruptedException
                            || rootCause instanceof RejectedExecutionException) {
                          LOG.debug("Shutting down", rootCause);
                        } else {
                          statusLogger.reconstructHistoricalStatesServiceFailedProcess(error);
                        }
                      });
            });
  }

  private SafeFuture<Void> applyNextBlock(final Context context) {
    if (context.checkStopApplyBlock()) {
      statusLogger.reconstructHistoricalStatesServiceComplete();
      stopped.complete(null);
      return SafeFuture.COMPLETE;
    }

    if (shutdown.get()) {
      stopped.complete(null);
      return SafeFuture.COMPLETE;
    }

    return chainDataClient
        .getBlockAtSlotExact(context.slot)
        .thenComposeChecked(
            maybeBlock -> {
              if (maybeBlock.isEmpty()) {
                return SafeFuture.COMPLETE;
              }

              final SignedBeaconBlock block = maybeBlock.get();
              progressLogger.update(block, context.anchorSlot);
              context.currentState = spec.replayValidatedBlock(context.currentState, block);
              return storageUpdateChannel.onReconstructedFinalizedState(
                  context.currentState, block.getRoot());
            })
        .thenRun(context::incrementSlot)
        .thenCompose(__ -> applyNextBlock(context));
  }

  @Override
  protected SafeFuture<?> doStop() {
    shutdown.set(true);
    return stopped;
  }

  private static class Context {
    private BeaconState currentState;
    private UInt64 slot;
    private final UInt64 anchorSlot;

    Context(final BeaconState currentState, final UInt64 slot, final UInt64 anchorSlot) {
      this.currentState = currentState;
      this.slot = slot;
      this.anchorSlot = anchorSlot;
    }

    private boolean checkStopApplyBlock() {
      return slot.isGreaterThanOrEqualTo(anchorSlot);
    }

    private void incrementSlot() {
      slot = slot.increment();
    }
  }
}
