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
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;
import static tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair.PARTICIPATION_FLAG_WEIGHTS;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AggregatingAttestationPoolProfilerCSV implements AggregatingAttestationPoolProfiler {
  private static final String[] PACKING_SUMMARY_HEADERS = {
    "slot", "total_pool_size", "packed_attestations", "packing_time_millis", "total_rewards_eth"
  };

  private static final String[] ATTESTATION_DETAILS_HEADERS = {
    "slot",
    "index_in_block",
    "distance",
    "root",
    "source",
    "target",
    "bits_count",
    "committee_bits_count",
    "final_reward",
    "inblock_reward",
    "attestation_data_root",
  };

  private static final String[] ATTESTATION_IMPROVEMENT_HEADERS = {
    "slot", "attestation_bits_count", "filled_up", "sorting_reward", "attestation_data_root"
  };

  private final FileWriter packingSummaryCsvWriter;
  private final FileWriter attestationDetailsCsvWriter;
  private final FileWriter attestationImprovementsCsvWriter;

  private static final long PROPOSER_REWARD_DENOMINATOR =
      WEIGHT_DENOMINATOR
          .minus(PROPOSER_WEIGHT)
          .times(WEIGHT_DENOMINATOR)
          .dividedBy(PROPOSER_WEIGHT)
          .longValue();

  public AggregatingAttestationPoolProfilerCSV(final Path outputDir) {

    try {
      createDirectory(outputDir);

      File packingSummaryFile = outputDir.resolve("packing_summary.csv").toFile();

      if (packingSummaryFile.exists()) {
        packingSummaryCsvWriter = new FileWriter(packingSummaryFile, StandardCharsets.UTF_8, true);
      } else {
        packingSummaryCsvWriter = new FileWriter(packingSummaryFile, StandardCharsets.UTF_8, false);
        packingSummaryCsvWriter.write(String.join(",", PACKING_SUMMARY_HEADERS) + "\n");
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    try {
      File attestationsDetailsFile = outputDir.resolve("attestations_details.csv").toFile();
      if (attestationsDetailsFile.exists()) {
        attestationDetailsCsvWriter =
            new FileWriter(attestationsDetailsFile, StandardCharsets.UTF_8, true);
      } else {
        attestationDetailsCsvWriter =
            new FileWriter(attestationsDetailsFile, StandardCharsets.UTF_8, false);
        attestationDetailsCsvWriter.write(String.join(",", ATTESTATION_DETAILS_HEADERS) + "\n");
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    try {
      File packingSummaryFile = outputDir.resolve("fill_up_details.csv").toFile();

      if (packingSummaryFile.exists()) {
        attestationImprovementsCsvWriter =
            new FileWriter(packingSummaryFile, StandardCharsets.UTF_8, true);
      } else {
        attestationImprovementsCsvWriter =
            new FileWriter(packingSummaryFile, StandardCharsets.UTF_8, false);
        attestationImprovementsCsvWriter.write(
            String.join(",", ATTESTATION_IMPROVEMENT_HEADERS) + "\n");
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

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

      final int aggregatingAttestationPoolSize = aggregatingAttestationPool.getSize();
      LOG.info("Pool size: {}", aggregatingAttestationPoolSize);

      var getAttestationsForBlockStart = System.nanoTime();
      var attestationPacking =
          aggregatingAttestationPool.getAttestationsForBlock(
              preState, new AttestationForkChecker(spec, preState));
      var getAttestationsForBlockEnd = System.nanoTime();
      var packingTotalTimeMillis =
          (getAttestationsForBlockEnd - getAttestationsForBlockStart) / 1_000_000;

      spec.atSlot(slot)
          .getBlockProcessor()
          .processAttestations(
              BeaconStateAltair.required(preState).createWritableCopy(),
              attestationPacking,
              BLSSignatureVerifier.SIMPLE);

      var attestationRewards =
          calculateAttestationRewards(
              attestationPacking,
              BlockProcessorAltair.required(spec.atSlot(slot).getBlockProcessor()),
              preState);

      var rewards = gweiToEth(UInt64.valueOf(attestationRewards.stream().reduce(0L, Long::sum)));

      LOG.info(
          "getAttestationsForBlock for {} produced {} attestations, rewards: {} ETH, timing: {} milliseconds",
          slot,
          attestationPacking.size(),
          rewards,
          packingTotalTimeMillis);

      try {
        packingSummaryCsvWriter.write(
            String.join(
                    ",",
                    slot.toString(),
                    String.valueOf(aggregatingAttestationPoolSize),
                    String.valueOf(attestationPacking.size()),
                    String.valueOf(packingTotalTimeMillis),
                    rewards)
                + "\n");
      } catch (IOException e) {
        LOG.warn("Failed to write to CSV", e);
      }

      var rewardsCalculator = AttestationRewardCalculator.create(spec, preState);

      IntStream.range(0, attestationPacking.size())
          .forEach(
              i -> {
                final Attestation attestation = attestationPacking.get(i);
                final AttestationData data = attestation.getData();

                var numerator = rewardsCalculator.getRewardNumeratorForAttestation(attestation);
                try {
                  attestationDetailsCsvWriter.write(
                      String.join(
                              ",",
                              slot.toString(),
                              String.valueOf(i),
                              preState.getSlot().minus(data.getSlot()).toString(),
                              data.getBeaconBlockRoot().toHexString(),
                              data.getSource().getEpoch().toString(),
                              data.getTarget().getEpoch().toString(),
                              String.valueOf(attestation.getAggregationBits().getBitCount()),
                              attestation
                                  .getCommitteeBits()
                                  .map(sszBits -> String.valueOf(sszBits.getBitCount()))
                                  .orElse("N/A"),
                              getEthRewardFromNumerator(UInt64.valueOf(numerator)),
                              gweiToEth(UInt64.valueOf(attestationRewards.get(i))),
                              data.hashTreeRoot().toString())
                          + "\n");
                } catch (IOException e) {
                  LOG.error("Failed to write to CSV", e);
                }
              });

    } catch (final Exception e) {
      LOG.error("Error occurred while profiling AggregatingAttestationPool", e);
    } finally {
      try {
        packingSummaryCsvWriter.flush();
      } catch (IOException e) {
        LOG.error("Failed to flush CSV printer", e);
      }

      try {
        attestationDetailsCsvWriter.flush();
      } catch (IOException e) {
        LOG.error("Failed to flush CSV printer", e);
      }

      try {
        attestationImprovementsCsvWriter.flush();
      } catch (IOException e) {
        LOG.error("Failed to flush CSV printer", e);
      }
    }
  }

  @Override
  public void onPreFillUp(
      final BeaconState stateAtBlockSlot,
      final PooledAttestationWithRewardInfo validatableAttestationWithSortingReward) {

    var attestation = validatableAttestationWithSortingReward.getAttestation();
    var sortingRewardNumerator = validatableAttestationWithSortingReward.getRewardNumerator();

    try {
      attestationImprovementsCsvWriter.write(
          String.join(
                  ",",
                  stateAtBlockSlot.getSlot().toString(),
                  String.valueOf(attestation.pooledAttestation().bits().getBitCount()),
                  "0", // not filled up
                  getEthRewardFromNumerator(sortingRewardNumerator),
                  attestation.data().hashTreeRoot().toString())
              + "\n");
    } catch (final IOException e) {
      LOG.error("Error printing CSV record", e);
    }
  }

  @Override
  public void onPostFillUp(
      final BeaconState stateAtBlockSlot,
      final PooledAttestationWithRewardInfo validatableAttestationWithSortingReward) {

    var attestation = validatableAttestationWithSortingReward.getAttestation();
    var sortingRewardNumerator = validatableAttestationWithSortingReward.getRewardNumerator();

    try {
      attestationImprovementsCsvWriter.write(
          String.join(
                  ",",
                  stateAtBlockSlot.getSlot().toString(),
                  String.valueOf(attestation.pooledAttestation().bits().getBitCount()),
                  "1", // filled up
                  getEthRewardFromNumerator(sortingRewardNumerator),
                  attestation.data().hashTreeRoot().toString())
              + "\n");
    } catch (final IOException e) {
      LOG.error("Error printing CSV record", e);
    }
  }

  private String getEthRewardFromNumerator(final UInt64 numerator) {
    return gweiToEth(numerator.dividedBy(PROPOSER_REWARD_DENOMINATOR));
  }

  private List<Long> calculateAttestationRewards(
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
        .map(maybeValue -> maybeValue.orElse(UInt64.ZERO))
        .map(UInt64::longValue)
        .toList();
  }

  private void createDirectory(final Path path) {
    if (!path.toFile().mkdirs()) {
      if (!path.toFile().exists()) {
        LOG.error("Unable to create directory {}", path);
      }
    }
  }

  private record AttestationRewardCalculator(
      Spec spec,
      BeaconStateAltair state,
      BeaconStateAccessorsAltair beaconStateAccessors,
      MiscHelpersAltair miscHelpers) {

    public static AttestationRewardCalculator create(final Spec spec, final BeaconState state) {
      final SpecVersion specVersion = spec.atSlot(state.getSlot());

      return new AttestationRewardCalculator(
          spec,
          BeaconStateAltair.required(state),
          BeaconStateAccessorsAltair.required(specVersion.beaconStateAccessors()),
          specVersion.miscHelpers().toVersionAltair().orElseThrow());
    }

    public long getRewardNumeratorForAttestation(final Attestation attestation) {
      final AttestationData data = attestation.getData();
      final List<Integer> participationFlagIndices =
          BeaconStateAccessorsAltair.required(beaconStateAccessors)
              .getAttestationParticipationFlagIndices(
                  state, data, state.getSlot().minusMinZero(data.getSlot()));

      final SszList<SszByte> epochParticipation;
      if (data.getTarget().getEpoch().equals(spec.getCurrentEpoch(state))) {
        epochParticipation = state.getCurrentEpochParticipation();
      } else {
        epochParticipation = state.getPreviousEpochParticipation();
      }

      UInt64 proposerRewardNumerator = UInt64.ZERO;
      final List<UInt64> attestingIndices = spec.getAttestingIndices(state, attestation);
      for (final UInt64 attestingIndex : attestingIndices) {
        final int attestingIndexInt = attestingIndex.intValue();
        for (int flagIndex = 0; flagIndex < PARTICIPATION_FLAG_WEIGHTS.size(); flagIndex++) {
          if (participationFlagIndices.contains(flagIndex)
              && !miscHelpers.hasFlag(epochParticipation.get(attestingIndexInt).get(), flagIndex)) {

            final UInt64 weight = PARTICIPATION_FLAG_WEIGHTS.get(flagIndex);

            final UInt64 reward =
                BeaconStateAccessorsAltair.required(beaconStateAccessors)
                    .getBaseReward(state, attestingIndexInt);

            proposerRewardNumerator = proposerRewardNumerator.plus(reward.times(weight));
          }
        }
      }

      return proposerRewardNumerator.longValue();
    }
  }
}
