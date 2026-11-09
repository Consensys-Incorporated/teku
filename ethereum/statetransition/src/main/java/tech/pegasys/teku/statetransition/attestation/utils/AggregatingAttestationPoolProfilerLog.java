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

package tech.pegasys.teku.statetransition.attestation.utils;

import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AggregatingAttestationPoolProfilerLog implements AggregatingAttestationPoolProfiler {
  private static final Logger LOG = LogManager.getLogger();

  @Override
  public void execute(
      final Spec spec,
      final UInt64 slot,
      final RecentChainData recentChainData,
      final AggregatingAttestationPool aggregatingAttestationPool) {
    final Optional<SafeFuture<BeaconState>> headState = recentChainData.getBestState();
    if (headState.isEmpty()) {
      return;
    }

    try {
      var preState = spec.processSlots(headState.get().join(), slot);

      LOG.info("Pool size: {}", aggregatingAttestationPool.getSize());
      var getAttestationsForBlockStart = System.nanoTime();
      var attestationPacking =
          aggregatingAttestationPool.getAttestationsForBlock(
              preState, new AttestationForkChecker(spec, preState));
      var getAttestationsForBlockEnd = System.nanoTime();

      spec.atSlot(slot)
          .getBlockProcessor()
          .processAttestations(
              BeaconStateAltair.required(preState).createWritableCopy(),
              attestationPacking,
              BLSSignatureVerifier.SIMPLE);

      var rewards =
          gweiToEth(
              UInt64.valueOf(
                  calculateAttestationRewards(
                      attestationPacking,
                      BlockProcessorAltair.required(spec.atSlot(slot).getBlockProcessor()),
                      preState)));

      LOG.info(
          "getAttestationsForBlock for {} produced {} attestations, rewards: {}ETH, timing: {} milliseconds",
          slot,
          attestationPacking.size(),
          rewards,
          (getAttestationsForBlockEnd - getAttestationsForBlockStart) / 1_000_000);

      IntStream.range(0, attestationPacking.size())
          .forEach(
              i -> {
                final Attestation attestation = attestationPacking.get(i);
                LOG.info(
                    "attestation {}:  bits: {}, committee bits: {}, {}",
                    i,
                    attestation.getAggregationBits().getBitCount(),
                    attestation
                        .getCommitteeBits()
                        .map(sszBits -> String.valueOf(sszBits.getBitCount()))
                        .orElse("N/A"),
                    attestation.getData());
              });

    } catch (final Exception e) {
      LOG.error("Error occurred while profiling AggregatingAttestationPool", e);
    }
  }

  @Override
  public void onPreFillUp(
      final BeaconState stateAtBlockSlot, final PooledAttestationWithRewardInfo attestation) {
    LOG.info(
        "Pre-fill up: attestationDataHash: {}, bits: {}",
        attestation.getAttestation().data().hashTreeRoot(),
        attestation.getAttestation().pooledAttestation().bits().getBitCount());
  }

  @Override
  public void onPostFillUp(
      final BeaconState stateAtBlockSlot, final PooledAttestationWithRewardInfo attestation) {
    LOG.info(
        "Post-fill up: attestationDataHash: {}, bits: {}",
        attestation.getAttestation().data().hashTreeRoot(),
        attestation.getAttestation().pooledAttestation().bits().getBitCount());
  }

  private long calculateAttestationRewards(
      final SszList<Attestation> attestations,
      final BlockProcessorAltair blockProcessor,
      final BeaconState preState) {
    final List<Optional<UInt64>> rewards = new ArrayList<>();
    final MutableBeaconStateAltair mutableBeaconStateAltair =
        BeaconStateAltair.required(preState).createWritableCopy();
    final AbstractBlockProcessor.IndexedAttestationProvider indexedAttestationProvider =
        blockProcessor.createIndexedAttestationProvider(
            mutableBeaconStateAltair, IndexedAttestationCache.capturing());
    attestations.forEach(
        attestation ->
            rewards.add(
                blockProcessor
                    .processAttestation(
                        mutableBeaconStateAltair, attestation, indexedAttestationProvider)
                    .proposerReward()));

    return rewards.stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(UInt64::longValue)
        .reduce(0L, Long::sum);
  }
}
