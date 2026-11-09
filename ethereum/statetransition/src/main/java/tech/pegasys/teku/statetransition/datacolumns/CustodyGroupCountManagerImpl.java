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

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImpl implements SlotEventsChannel, CustodyGroupCountManager {
  private static final Logger LOG = LogManager.getLogger();

  private record CustodySnapshot(
      int custodyGroupCount,
      int samplingGroupCount,
      Set<UInt64> custodyColumnIndices,
      Set<UInt64> samplingColumnIndices,
      boolean isMaxCustodyGroups) {}

  private final Spec spec;
  private final MiscHelpersFulu miscHelpersFulu;
  private final ProposersDataManager proposersDataManager;
  private final CustodyGroupCountChannel custodyGroupCountChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final UInt256 nodeId;
  private final SettableGauge custodyGroupCountGauge;

  private volatile boolean genesisInitialized = false;
  private volatile CustodySnapshot snapshot;

  public CustodyGroupCountManagerImpl(
      final Spec spec,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount,
      final UInt256 nodeId,
      final MetricsSystem metricsSystem) {
    this(
        spec,
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers()),
        proposersDataManager,
        custodyGroupCountChannel,
        combinedChainDataClient,
        initCustodyGroupCount,
        nodeId,
        metricsSystem);
  }

  @VisibleForTesting
  CustodyGroupCountManagerImpl(
      final Spec spec,
      final MiscHelpersFulu miscHelpersFulu,
      final ProposersDataManager proposersDataManager,
      final CustodyGroupCountChannel custodyGroupCountChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final int initCustodyGroupCount,
      final UInt256 nodeId,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.miscHelpersFulu = miscHelpersFulu;
    this.custodyGroupCountGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "custody_groups",
            "Total number of custody groups within a node");
    this.proposersDataManager = proposersDataManager;
    this.combinedChainDataClient = combinedChainDataClient;
    this.custodyGroupCountChannel = custodyGroupCountChannel;
    this.nodeId = nodeId;
    final Optional<Integer> maybeCustodyCount =
        combinedChainDataClient.getCustodyGroupCount().map(UInt64::intValue);
    if (maybeCustodyCount.isEmpty() || maybeCustodyCount.get() < initCustodyGroupCount) {
      updateCustodyGroupCount(initCustodyGroupCount, maybeCustodyCount);
    } else {
      LOG.info("Using custody group count {} from store", maybeCustodyCount.get());
      updateCustodyGroupCount(maybeCustodyCount.get(), maybeCustodyCount);
    }
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (snapshot.isMaxCustodyGroups()) {
      return;
    }

    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final Map<UInt64, PreparedProposerInfo> preparedValidators =
        proposersDataManager.getPreparedProposerInfo();

    if (detectGenesisInitialization(currentEpoch, preparedValidators)) {
      return;
    }

    final UInt64 startSlotAtEpoch = spec.computeStartSlotAtEpoch(currentEpoch);
    if (!startSlotAtEpoch.equals(slot) || preparedValidators.isEmpty()) {
      return;
    }

    computeAndUpdateCustodyGroupCount(preparedValidators)
        .finish(
            error ->
                LOG.error(
                    "Failed to compute custody group count for epoch {}", currentEpoch, error));
  }

  private boolean detectGenesisInitialization(
      final UInt64 currentEpoch, final Map<UInt64, PreparedProposerInfo> preparedValidators) {
    if (genesisInitialized) {
      return false;
    }

    if (!currentEpoch.isZero()) {
      genesisInitialized = true;
      return false;
    }

    if (preparedValidators.isEmpty()) {
      return false;
    }

    genesisInitialized = true;

    LOG.debug("Validators at genesis epoch detected, initializing custody group count.");
    computeAndUpdateCustodyGroupCount(preparedValidators)
        .thenAccept(
            maybeCustodyGroupCountUpdated ->
                maybeCustodyGroupCountUpdated.ifPresent(
                    integer ->
                        LOG.info(
                            "Custody group count updated to {}, because genesis validators were found.",
                            integer)))
        .finish(
            error ->
                LOG.error(
                    "Failed to compute custody group count for epoch {}", currentEpoch, error));
    return true;
  }

  @VisibleForTesting
  SafeFuture<Optional<Integer>> computeAndUpdateCustodyGroupCount(
      final Map<UInt64, PreparedProposerInfo> preparedProposerInfo) {
    final Optional<Integer> maybeCustodyGroupCount =
        combinedChainDataClient.getCustodyGroupCount().map(UInt64::intValue);
    return combinedChainDataClient
        .getBestFinalizedState()
        .thenApply(
            maybeState -> {
              if (maybeState.isEmpty()) {
                return Optional.empty();
              }

              final int computedCustody =
                  miscHelpersFulu
                      .getValidatorsCustodyRequirement(
                          maybeState.get(), preparedProposerInfo.keySet())
                      .intValue();
              final int custodyGroupCount =
                  maybeCustodyGroupCount
                      .map(integer -> Math.max(computedCustody, integer))
                      .orElse(computedCustody);
              updateCustodyGroupCount(custodyGroupCount, maybeCustodyGroupCount);

              return Optional.of(custodyGroupCount);
            });
  }

  @Override
  public int getCustodyGroupCount() {
    return snapshot.custodyGroupCount();
  }

  @Override
  public Set<UInt64> getCustodyColumnIndices() {
    return snapshot.custodyColumnIndices();
  }

  @Override
  public int getSamplingGroupCount() {
    return snapshot.samplingGroupCount();
  }

  @Override
  public Set<UInt64> getSamplingColumnIndices() {
    return snapshot.samplingColumnIndices();
  }

  private void updateCustodyGroupCount(
      final int newCustodyGroupCount, final Optional<Integer> maybeCustodyGroupCount) {
    if (maybeCustodyGroupCount.isEmpty() || maybeCustodyGroupCount.get() < newCustodyGroupCount) {
      LOG.info(
          "Persisting custody group count of {} (old value: {}).",
          newCustodyGroupCount,
          maybeCustodyGroupCount.map(Object::toString).orElse("<not set>"));
      combinedChainDataClient.updateCustodyGroupCount(newCustodyGroupCount);
    }

    final CustodySnapshot current = this.snapshot;
    if (current == null || current.custodyGroupCount() != newCustodyGroupCount) {
      final int samplingGroupCount = miscHelpersFulu.getSamplingGroupCount(newCustodyGroupCount);
      this.snapshot =
          new CustodySnapshot(
              newCustodyGroupCount,
              samplingGroupCount,
              miscHelpersFulu.computeCustodyColumnIndices(nodeId, newCustodyGroupCount),
              miscHelpersFulu.computeCustodyColumnIndices(nodeId, samplingGroupCount),
              miscHelpersFulu.isSuperNode(newCustodyGroupCount));
      custodyGroupCountChannel.onGroupCountUpdate(newCustodyGroupCount, samplingGroupCount);
      custodyGroupCountGauge.set(newCustodyGroupCount);
    }
  }
}
