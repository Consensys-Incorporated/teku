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

package tech.pegasys.teku.services.beaconchain;

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteOrder;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledSuppliedMetric;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.analysis.ValidatorStats.CorrectAndLiveValidators;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;

public class BeaconChainMetrics implements SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final long NOT_SET = 0L;
  private final RecentChainData recentChainData;
  private final NodeSlot nodeSlot;
  private final Eth1DataCache eth1DataCache;

  private final SettableGauge currentActiveValidators;
  private final SettableGauge previousActiveValidators;

  private final SettableGauge currentLiveValidators;
  private final SettableGauge previousLiveValidators;

  private final SettableGauge currentCorrectValidators;
  private final SettableGauge previousCorrectValidators;

  private final SettableGauge finalizedEpoch;
  private final SettableGauge finalizedRoot;

  private final SettableGauge currentJustifiedEpoch;
  private final SettableGauge previousJustifiedEpoch;

  private final SettableGauge currentJustifiedRoot;
  private final SettableGauge previousJustifiedRoot;

  private final SettableGauge previousEpochParticipationWeight;
  private final SettableGauge previousEpochTotalWeight;

  private final Spec spec;

  public BeaconChainMetrics(
      final Spec spec,
      final RecentChainData recentChainData,
      final NodeSlot nodeSlot,
      final MetricsSystem metricsSystem,
      final Eth2P2PNetwork p2pNetwork,
      final Eth1DataCache eth1DataCache) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.nodeSlot = nodeSlot;
    this.eth1DataCache = eth1DataCache;

    metricsSystem.createGauge(
        TekuMetricCategory.BEACON,
        "epoch",
        "Latest epoch recorded by the beacon chain",
        this::getCurrentEpochValue);
    metricsSystem.createGauge(
        TekuMetricCategory.BEACON,
        "slot",
        "Latest slot recorded by the beacon chain",
        this::getCurrentSlotValue);
    metricsSystem.createGauge(
        TekuMetricCategory.BEACON,
        "head_slot",
        "Slot of the head block of the beacon chain",
        this::getHeadSlotValue);
    metricsSystem.createGauge(
        TekuMetricCategory.BEACON,
        "head_root",
        "Root of the head block of the beacon chain",
        this::getHeadRootValue);

    final LabelledSuppliedMetric peerCountMetric =
        metricsSystem.createLabelledSuppliedGauge(
            TekuMetricCategory.BEACON,
            "peer_count",
            "Tracks number of connected peers, verified to be on the same chain",
            "direction");
    peerCountMetric.labels(
        () -> p2pNetwork.streamPeers().filter(Eth2Peer::connectionInitiatedRemotely).count(),
        "inbound");
    peerCountMetric.labels(
        () -> p2pNetwork.streamPeers().filter(Eth2Peer::connectionInitiatedLocally).count(),
        "outbound");

    finalizedEpoch =
        SettableGauge.create(
            metricsSystem, TekuMetricCategory.BEACON, "finalized_epoch", "Current finalized epoch");
    finalizedRoot =
        SettableGauge.create(
            metricsSystem, TekuMetricCategory.BEACON, "finalized_root", "Current finalized root");

    currentJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_justified_epoch",
            "Current justified epoch");
    currentJustifiedRoot =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_justified_root",
            "Current justified root");

    previousJustifiedEpoch =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_justified_epoch",
            "Current previously justified epoch");
    previousJustifiedRoot =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_justified_root",
            "Current previously justified root");

    previousLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_live_validators",
            "Number of active validators that successfully included attestation on chain for previous epoch");
    currentLiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_live_validators",
            "Number of active validators that successfully included attestation on chain for current epoch");
    currentActiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_active_validators",
            "Number of active validators in the current epoch");
    previousActiveValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_active_validators",
            "Number of active validators in the previous epoch");

    currentCorrectValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "current_correct_validators",
            "Number of validators who voted for correct source and target checkpoints in the current epoch");
    previousCorrectValidators =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_correct_validators",
            "Number of validators who voted for correct source and target checkpoints in the previous epoch");

    previousEpochParticipationWeight =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_epoch_participation_weight",
            "Total effective balance of all validators who voted for correct source and target checkpoints in the previous epoch");
    previousEpochTotalWeight =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "previous_epoch_total_weight",
            "Total effective balance of all active validators in the previous epoch");

    final String version = VersionProvider.IMPLEMENTATION_VERSION.replaceAll("^v", "");
    final LabelledMetric<Counter> versionCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            VersionProvider.CLIENT_IDENTITY + "_version_total",
            "Teku version in use",
            "version");
    versionCounter.labels(version).inc();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    updateMetrics()
        .finish(
            error ->
                LOG.error(
                    "Failed to update block performance metrics because chain head state was unavailable",
                    error));
  }

  @VisibleForTesting
  SafeFuture<?> updateMetrics() {
    return recentChainData
        .getChainHead()
        .map(this::updateMetrics)
        .orElseGet(() -> SafeFuture.completedFuture(null));
  }

  private SafeFuture<?> updateMetrics(final ChainHead head) {
    return head.asStateAndBlockSummary().thenAccept(this::updateMetrics);
  }

  private void updateMetrics(final StateAndBlockSummary head) {
    final BeaconState state = head.getState();
    BeaconStateCache.getTransitionCaches(state)
        .getLatestTotalBalances()
        .ifPresent(
            totalBalances -> {
              // The TotalBalances are created at the end of the epoch so the "current" balance
              // is actually "previous" by the time we're actually updating the metrics
              previousEpochTotalWeight.set(
                  totalBalances.getCurrentEpochActiveValidators().longValue());
              previousEpochParticipationWeight.set(
                  totalBalances.getCurrentEpochSourceAttesters().longValue());
            });

    final UInt64 currentEpoch = spec.computeEpochAtSlot(head.getSlot());
    final UInt64 previousEpoch = currentEpoch.minusMinZero(1);
    final Bytes32 currentEpochCorrectTarget = getCorrectTargetRoot(head, currentEpoch);
    final Bytes32 previousEpochCorrectTarget = getCorrectTargetRoot(head, previousEpoch);

    CorrectAndLiveValidators currentEpochValidators =
        state.getValidatorStatsCurrentEpoch(currentEpochCorrectTarget);
    currentLiveValidators.set(currentEpochValidators.getNumberOfLiveValidators());
    currentCorrectValidators.set(currentEpochValidators.getNumberOfCorrectValidators());
    currentActiveValidators.set(
        spec.getActiveValidatorIndices(state, spec.getCurrentEpoch(state)).size());

    CorrectAndLiveValidators previousEpochValidators =
        state.getValidatorStatsPreviousEpoch(previousEpochCorrectTarget);
    previousLiveValidators.set(previousEpochValidators.getNumberOfLiveValidators());
    previousCorrectValidators.set(previousEpochValidators.getNumberOfCorrectValidators());
    previousActiveValidators.set(
        spec.getActiveValidatorIndices(state, spec.getPreviousEpoch(state)).size());

    final Checkpoint finalizedCheckpoint = state.getFinalizedCheckpoint();
    finalizedEpoch.set(finalizedCheckpoint.getEpoch().longValue());
    finalizedRoot.set(getLongFromRoot(finalizedCheckpoint.getRoot()));

    final Checkpoint currentJustifiedCheckpoint = state.getCurrentJustifiedCheckpoint();
    currentJustifiedEpoch.set(currentJustifiedCheckpoint.getEpoch().longValue());
    currentJustifiedRoot.set(getLongFromRoot(currentJustifiedCheckpoint.getRoot()));

    final Checkpoint previousJustifiedCheckpoint = state.getPreviousJustifiedCheckpoint();
    previousJustifiedEpoch.set(previousJustifiedCheckpoint.getEpoch().longValue());
    previousJustifiedRoot.set(getLongFromRoot(previousJustifiedCheckpoint.getRoot()));

    eth1DataCache.updateMetrics(state);
  }

  private Bytes32 getCorrectTargetRoot(
      final StateAndBlockSummary stateAndBlock, final UInt64 epoch) {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);
    return epochStartSlot.isGreaterThanOrEqualTo(stateAndBlock.getSlot())
        ? stateAndBlock.getRoot()
        : spec.getBlockRootAtSlot(stateAndBlock.getState(), epochStartSlot);
  }

  static long getLongFromRoot(final Bytes32 root) {
    return root.getLong(24, ByteOrder.LITTLE_ENDIAN);
  }

  private long getCurrentSlotValue() {
    return nodeSlot.longValue();
  }

  private long getHeadSlotValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    return recentChainData.getHeadSlot().longValue();
  }

  private long getHeadRootValue() {
    if (recentChainData.isPreGenesis()) {
      return NOT_SET;
    }
    Optional<Bytes32> maybeBlockRoot = recentChainData.getBestBlockRoot();
    return maybeBlockRoot.map(BeaconChainMetrics::getLongFromRoot).orElse(0L);
  }

  private long getCurrentEpochValue() {
    return spec.computeEpochAtSlot(nodeSlot.getValue()).longValue();
  }
}
