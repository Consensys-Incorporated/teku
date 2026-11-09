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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.VersionBasedRpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.datacolumns.MinCustodyPeriodSlotCalculator;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class StatusMessageFactory implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final AtomicReference<Optional<UInt64>> maybeEarliestAvailableSlot =
      new AtomicReference<>(Optional.empty());
  private final boolean supportsEarliestAvailableSlot;

  private final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator;

  public StatusMessageFactory(
      final Spec spec,
      final CombinedChainDataClient recentChainData,
      final MetricsSystem metricsSystem) {
    this(spec, recentChainData, MinCustodyPeriodSlotCalculator.createFromSpec(spec), metricsSystem);
  }

  @VisibleForTesting
  StatusMessageFactory(
      final Spec spec,
      final CombinedChainDataClient recentChainData,
      final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.combinedChainDataClient = recentChainData;
    this.minCustodyPeriodSlotCalculator = minCustodyPeriodSlotCalculator;
    this.supportsEarliestAvailableSlot = spec.isMilestoneSupported(SpecMilestone.FULU);

    metricsSystem.createGauge(
        TekuMetricCategory.BEACON,
        "earliest_available_slot",
        "Value representing the earliest slot where the node has data available to serve peers.",
        () -> maybeEarliestAvailableSlot.get().map(UInt64::doubleValue).orElse(0.0));
  }

  public Optional<RpcRequestBodySelector<StatusMessage>> createStatusMessage() {
    final Function<String, Optional<StatusMessage>> fn =
        (protocolId) -> {
          final int protocolVersion = BeaconChainMethodIds.extractStatusVersion(protocolId);
          final SpecMilestone milestone =
              switch (protocolVersion) {
                case 1 -> SpecMilestone.PHASE0;
                case 2 -> SpecMilestone.FULU;
                default ->
                    throw new IllegalStateException(
                        "Unexpected protocol version: " + protocolVersion);
              };
          final StatusMessageSchema<?> schema =
              spec.forMilestone(milestone).getSchemaDefinitions().getStatusMessageSchema();

          return createStatusMessage(schema);
        };

    return Optional.of(new VersionBasedRpcRequestBodySelector<>(fn));
  }

  public Optional<StatusMessage> createStatusMessage(final StatusMessageSchema<?> schema) {
    final RecentChainData recentChainData = combinedChainDataClient.getRecentChainData();
    if (recentChainData.isPreForkChoice()) {
      // We don't have chainhead information, so we can't generate an accurate status message
      return Optional.empty();
    }

    final Bytes4 forkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    final Checkpoint finalizedCheckpoint = recentChainData.getFinalizedCheckpoint().orElseThrow();
    final MinimalBeaconBlockSummary chainHead = recentChainData.getChainHead().orElseThrow();

    return Optional.of(
        schema.create(
            forkDigest,
            // Genesis finalized root is always ZERO because it's taken from the state and the
            // genesis block is calculated from the state so the state can't contain the actual
            // block root
            finalizedCheckpoint.getEpoch().isZero() ? Bytes32.ZERO : finalizedCheckpoint.getRoot(),
            finalizedCheckpoint.getEpoch(),
            chainHead.getRoot(),
            chainHead.getSlot(),
            maybeEarliestAvailableSlot
                .get()
                .or(
                    () ->
                        Optional.of(
                            spec.computeStartSlotAtEpoch(recentChainData.getFinalizedEpoch())))));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final boolean isStartOfEpoch =
        spec.computeStartSlotAtEpoch(combinedChainDataClient.getCurrentEpoch()).equals(slot);
    if (isStartOfEpoch && supportsEarliestAvailableSlot) {
      updateEarliestAvailableSlot();
    }
  }

  private void updateEarliestAvailableSlot() {
    final SafeFuture<Optional<UInt64>> earliestAvailableBlockSlotFuture =
        combinedChainDataClient.getEarliestAvailableBlockSlot();
    final SafeFuture<Optional<UInt64>> earliestDataColumnSidecarSlotFuture =
        combinedChainDataClient.getEarliestAvailableDataColumnSlot();
    final Optional<UInt64> minCustodyPeriodSlot =
        minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(
            combinedChainDataClient.getCurrentSlot());

    earliestAvailableBlockSlotFuture
        .thenCombine(
            earliestDataColumnSidecarSlotFuture,
            (blockEarliestAvailableSlot, dataColumnEarliestAvailableSlot) ->
                computeEarliestAvailableSlot(
                    blockEarliestAvailableSlot,
                    dataColumnEarliestAvailableSlot,
                    minCustodyPeriodSlot))
        .thenAccept(
            maybeSlot -> {
              maybeSlot.ifPresent(
                  slot -> LOG.debug("Updating earliest available block slot to {}", slot));
              maybeEarliestAvailableSlot.set(maybeSlot);
            })
        .finishWarn(LOG);
  }

  private Optional<UInt64> computeEarliestAvailableSlot(
      final Optional<UInt64> blockEarliestAvailableSlot,
      final Optional<UInt64> dataColumnEarliestAvailableSlot,
      final Optional<UInt64> minCustodyPeriodSlot) {
    if (blockEarliestAvailableSlot.isEmpty()
        || dataColumnEarliestAvailableSlot.isEmpty()
        || minCustodyPeriodSlot.isEmpty()) {
      return Optional.empty();
    }

    if (dataColumnEarliestAvailableSlot.get().isGreaterThan(minCustodyPeriodSlot.get())) {
      // datacolumn backfill is not yet complete, let's return the most recent slot for which we
      // have blocks and data columns
      return Optional.ofNullable(
          blockEarliestAvailableSlot.get().max(dataColumnEarliestAvailableSlot.get()));
    }

    // datacolumn complete for the entire custody period, we can just consider the earliest
    // available block slot
    return blockEarliestAvailableSlot;
  }
}
