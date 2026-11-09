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

package tech.pegasys.teku.api;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;

public class RewardCalculator {
  private final Spec spec;
  private final BlockRewardCalculatorUtil blockRewardCalculatorUtil;

  public RewardCalculator(
      final Spec spec, final BlockRewardCalculatorUtil blockRewardCalculatorUtil) {
    this.spec = spec;
    this.blockRewardCalculatorUtil = blockRewardCalculatorUtil;
  }

  public ObjectAndMetaData<BlockRewardData> getBlockRewardDataAndMetaData(
      final BlockAndMetaData blockAndMetaData, final BeaconState parentState) {
    return blockAndMetaData.map(
        __ -> getBlockRewardData(blockAndMetaData.getData().getMessage(), parentState));
  }

  public BlockRewardData getBlockRewardData(
      final BlockContainer blockContainer, final BeaconState parentState) {
    try {
      return BlockRewardData.fromInternal(
          blockRewardCalculatorUtil.getBlockRewardData(blockContainer, parentState));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  @VisibleForTesting
  Map<Integer, Integer> getCommitteeIndices(
      final List<BLSPublicKey> committeeKeys,
      final Set<String> validators,
      final BeaconState state) {
    if (validators.isEmpty()) {
      final List<Integer> result =
          committeeKeys.stream()
              .flatMap(pubkey -> spec.getValidatorIndex(state, pubkey).stream())
              .toList();

      return IntStream.range(0, result.size())
          .boxed()
          .collect(Collectors.<Integer, Integer, Integer>toMap(Function.identity(), result::get));
    }

    checkValidatorsList(committeeKeys, state.getValidators().size(), validators);

    final Map<Integer, Integer> output = new HashMap<>();
    for (int i = 0; i < committeeKeys.size(); i++) {
      final BLSPublicKey key = committeeKeys.get(i);
      final Optional<Integer> validatorIndex = spec.getValidatorIndex(state, key);
      if (validatorIndex.isPresent()
          && (validators.contains(key.toHexString())
              || validators.contains(validatorIndex.get().toString()))) {
        output.put(i, validatorIndex.get());
      }
    }

    return output;
  }

  public SyncCommitteeRewardData getSyncCommitteeRewardData(
      final Set<String> validators,
      final BlockAndMetaData blockAndMetadata,
      final BeaconState state) {
    final BeaconBlock block = blockAndMetadata.getData().getMessage();
    if (!spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      throw new BadRequestException(
          "Slot "
              + block.getSlot()
              + " is pre altair, and no sync committee information is available");
    }

    final UInt64 epoch = spec.computeEpochAtSlot(block.getSlot());
    final SyncCommittee committee =
        spec.getSyncCommitteeUtil(block.getSlot()).orElseThrow().getSyncCommittee(state, epoch);
    final List<BLSPublicKey> committeeKeys =
        committee.getPubkeys().stream().map(SszPublicKey::getBLSPublicKey).toList();
    final Map<Integer, Integer> committeeIndices =
        getCommitteeIndices(committeeKeys, validators, state);
    final UInt64 participantReward = spec.getSyncCommitteeParticipantReward(state);

    final SyncCommitteeRewardData rewardData =
        new SyncCommitteeRewardData(
            blockAndMetadata.isExecutionOptimistic(), blockAndMetadata.isFinalized());
    return calculateSyncCommitteeRewards(
        committeeIndices,
        participantReward,
        block.getBody().getOptionalSyncAggregate(),
        rewardData);
  }

  @VisibleForTesting
  SyncCommitteeRewardData calculateSyncCommitteeRewards(
      final Map<Integer, Integer> committeeIndices,
      final UInt64 participantReward,
      final Optional<SyncAggregate> maybeAggregate,
      final SyncCommitteeRewardData data) {
    if (maybeAggregate.isEmpty()) {
      return data;
    }

    final SyncAggregate aggregate = maybeAggregate.get();

    committeeIndices.forEach(
        (i, key) -> {
          if (aggregate.getSyncCommitteeBits().getBit(i)) {
            data.increaseReward(key, participantReward.longValue());
          } else {
            data.decreaseReward(key, participantReward.longValue());
          }
        });

    return data;
  }

  @VisibleForTesting
  void checkValidatorsList(
      final List<BLSPublicKey> committeeKeys,
      final int validatorSetSize,
      final Set<String> validators) {
    for (final String validator : validators) {
      if (validator.startsWith("0x")) {
        if (!committeeKeys.contains(BLSPublicKey.fromHexString(validator))) {
          throw new BadRequestException(
              String.format("'%s' was not found in the committee", validator));
        }
      } else {
        try {
          final int index = Integer.parseInt(validator);
          if (index < 0 || index >= validatorSetSize) {
            throw new BadRequestException(
                String.format(
                    "index '%s' is not in the expected validator index range 0 - %d",
                    validator, validatorSetSize));
          }
        } catch (NumberFormatException e) {
          throw new BadRequestException(
              String.format(
                  "'%s' was expected to be a committee index but could not be read as a number",
                  validator));
        }
      }
    }
  }
}
