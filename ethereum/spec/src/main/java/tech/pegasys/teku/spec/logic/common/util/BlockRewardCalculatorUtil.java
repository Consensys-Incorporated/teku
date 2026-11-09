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

package tech.pegasys.teku.spec.logic.common.util;

import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;

public class BlockRewardCalculatorUtil {
  private final Spec spec;
  private static final Logger LOG = LogManager.getLogger();

  public BlockRewardCalculatorUtil(final Spec spec) {
    this.spec = spec;
  }

  public BlockRewardData getBlockRewardData(
      final BlockContainer blockContainer, final BeaconState parentState) {
    final BeaconBlock block = blockContainer.getBlock();
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new IllegalArgumentException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final BeaconState preState = getPreState(block.getSlot(), parentState);

    final SpecVersion specVersion = spec.atSlot(preState.getSlot());
    return calculateBlockRewards(
        block, BlockProcessorAltair.required(specVersion.getBlockProcessor()), preState);
  }

  @VisibleForTesting
  long getProposerReward(final BeaconState state) {
    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);
    return participantReward
        .times(PROPOSER_WEIGHT)
        .dividedBy(WEIGHT_DENOMINATOR.minus(PROPOSER_WEIGHT))
        .longValue();
  }

  @VisibleForTesting
  BlockRewardData calculateBlockRewards(
      final BeaconBlock block,
      final BlockProcessorAltair blockProcessorAltair,
      final BeaconState preState) {
    final long proposerReward = getProposerReward(preState);
    final SyncAggregate aggregate = block.getBody().getOptionalSyncAggregate().orElseThrow();
    final UInt64 proposerIndex = block.getProposerIndex();
    final long attestationsBlockRewards =
        calculateAttestationRewards(block, blockProcessorAltair, preState);
    final long syncAggregateBlockRewards =
        calculateProposerSyncAggregateBlockRewards(proposerReward, aggregate);
    final long proposerSlashingsBlockRewards = calculateProposerSlashingsRewards(block, preState);
    final long attesterSlashingsBlockRewards = calculateAttesterSlashingsRewards(block, preState);

    return new BlockRewardData(
        proposerIndex,
        attestationsBlockRewards,
        syncAggregateBlockRewards,
        proposerSlashingsBlockRewards,
        attesterSlashingsBlockRewards);
  }

  @VisibleForTesting
  long calculateAttesterSlashingsRewards(
      final BeaconBlock beaconBlock, final BeaconState preState) {
    final SszList<AttesterSlashing> attesterSlashings =
        beaconBlock.getBody().getAttesterSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(preState.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long attesterSlashingsRewards = 0;
    for (AttesterSlashing slashing : attesterSlashings) {
      for (final UInt64 index : slashing.getIntersectingValidatorIndices()) {
        attesterSlashingsRewards =
            calculateSlashingRewards(
                specConfig, preState, index.intValue(), attesterSlashingsRewards);
      }
    }

    return attesterSlashingsRewards;
  }

  @VisibleForTesting
  long calculateProposerSyncAggregateBlockRewards(
      final long proposerReward, final SyncAggregate aggregate) {
    final SszBitvector syncCommitteeBits = aggregate.getSyncCommitteeBits();
    return proposerReward * syncCommitteeBits.getBitCount();
  }

  @VisibleForTesting
  long calculateProposerSlashingsRewards(
      final BeaconBlock beaconBlock, final BeaconState preState) {
    final SszList<ProposerSlashing> proposerSlashings =
        beaconBlock.getBody().getProposerSlashings();

    final UInt64 epoch = spec.computeEpochAtSlot(preState.getSlot());
    final SpecConfig specConfig = spec.getSpecConfig(epoch);

    long proposerSlashingsRewards = 0;
    for (ProposerSlashing slashing : proposerSlashings) {
      final int slashedIndex = slashing.getHeader1().getMessage().getProposerIndex().intValue();
      proposerSlashingsRewards =
          calculateSlashingRewards(specConfig, preState, slashedIndex, proposerSlashingsRewards);
    }

    return proposerSlashingsRewards;
  }

  private long calculateAttestationRewards(
      final BeaconBlock block,
      final BlockProcessorAltair blockProcessor,
      final BeaconState preState) {
    // In Gloas the child bid identifies whether it builds on the FULL or EMPTY parent.
    final boolean parentPayloadAvailable =
        block
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .map(
                signedBid ->
                    MiscHelpersGloas.required(spec.atSlot(block.getSlot()).miscHelpers())
                        .isBidBuildingOnFullParent(
                            BeaconStateGloas.required(preState), signedBid.getMessage()))
            .orElse(false);
    final BeaconState attestationRewardState =
        spec.getStateForAttestationRewardCalculation(preState, parentPayloadAvailable);
    final MutableBeaconStateAltair mutableBeaconStateAltair =
        BeaconStateAltair.required(attestationRewardState).createWritableCopy();
    final AbstractBlockProcessor.IndexedAttestationProvider indexedAttestationProvider =
        blockProcessor.createIndexedAttestationProvider(
            mutableBeaconStateAltair, IndexedAttestationCache.capturing());
    return block.getBody().getAttestations().stream()
        .map(
            attestation ->
                blockProcessor
                    .processAttestation(
                        mutableBeaconStateAltair, attestation, indexedAttestationProvider)
                    .proposerReward())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(UInt64::longValue)
        .reduce(0L, Long::sum);
  }

  private long calculateSlashingRewards(
      final SpecConfig specConfig,
      final BeaconState state,
      final int slashedIndex,
      final long currentRewards) {
    final Validator validator = state.getValidators().get(slashedIndex);
    final UInt64 whistleblowerReward =
        validator.getEffectiveBalance().dividedBy(specConfig.getWhistleblowerRewardQuotient());
    final UInt64 proposerReward =
        whistleblowerReward.dividedBy(specConfig.getProposerRewardQuotient());
    final UInt64 rewardsAdditions = proposerReward.plus(whistleblowerReward.minus(proposerReward));
    return currentRewards + rewardsAdditions.longValue();
  }

  private BeaconState getPreState(final UInt64 slot, final BeaconState parentState) {
    final BeaconState preState;
    try {
      preState = spec.processSlots(parentState, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.debug("Failed to fetch preState for slot {}", slot, e);
      throw new IllegalArgumentException(
          "Failed to calculate block rewards, as could not generate the pre-state of the block at slot "
              + slot,
          e);
    }
    return preState;
  }

  /** Represents the block rewards in GWei and the block proposer index */
  public record BlockRewardData(
      UInt64 proposerIndex,
      long attestations,
      long syncAggregate,
      long proposerSlashings,
      long attesterSlashings) {
    public long getTotal() {
      return attestations + syncAggregate + proposerSlashings + attesterSlashings;
    }
  }
}
