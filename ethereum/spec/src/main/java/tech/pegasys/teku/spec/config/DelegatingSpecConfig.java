/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.config;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;

public class DelegatingSpecConfig implements SpecConfig {
  protected final SpecConfig specConfig;

  public DelegatingSpecConfig(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  @Override
  public Map<String, Object> getRawConfig() {
    return specConfig.getRawConfig();
  }

  @Override
  public UInt64 getBaseRewardsPerEpoch() {
    return specConfig.getBaseRewardsPerEpoch();
  }

  @Override
  public int getDepositContractTreeDepth() {
    return specConfig.getDepositContractTreeDepth();
  }

  @Override
  public int getJustificationBitsLength() {
    return specConfig.getJustificationBitsLength();
  }

  @Override
  public UInt64 getEth1FollowDistance() {
    return specConfig.getEth1FollowDistance();
  }

  @Override
  public int getMaxCommitteesPerSlot() {
    return specConfig.getMaxCommitteesPerSlot();
  }

  @Override
  public int getTargetCommitteeSize() {
    return specConfig.getTargetCommitteeSize();
  }

  @Override
  public int getMaxValidatorsPerCommittee() {
    return specConfig.getMaxValidatorsPerCommittee();
  }

  @Override
  public int getMinPerEpochChurnLimit() {
    return specConfig.getMinPerEpochChurnLimit();
  }

  @Override
  public UInt64 getMaxPerEpochActivationExitChurnLimit() {
    return specConfig.getMaxPerEpochActivationExitChurnLimit();
  }

  @Override
  public int getChurnLimitQuotient() {
    return specConfig.getChurnLimitQuotient();
  }

  @Override
  public int getShuffleRoundCount() {
    return specConfig.getShuffleRoundCount();
  }

  @Override
  public int getMinGenesisActiveValidatorCount() {
    return specConfig.getMinGenesisActiveValidatorCount();
  }

  @Override
  public UInt64 getMinGenesisTime() {
    return specConfig.getMinGenesisTime();
  }

  @Override
  public UInt64 getHysteresisQuotient() {
    return specConfig.getHysteresisQuotient();
  }

  @Override
  public UInt64 getHysteresisDownwardMultiplier() {
    return specConfig.getHysteresisDownwardMultiplier();
  }

  @Override
  public UInt64 getHysteresisUpwardMultiplier() {
    return specConfig.getHysteresisUpwardMultiplier();
  }

  @Override
  public int getProportionalSlashingMultiplier() {
    return specConfig.getProportionalSlashingMultiplier();
  }

  @Override
  public UInt64 getMinDepositAmount() {
    return specConfig.getMinDepositAmount();
  }

  @Override
  public UInt64 getMaxEffectiveBalance() {
    return specConfig.getMaxEffectiveBalance();
  }

  @Override
  public UInt64 getEjectionBalance() {
    return specConfig.getEjectionBalance();
  }

  @Override
  public UInt64 getEffectiveBalanceIncrement() {
    return specConfig.getEffectiveBalanceIncrement();
  }

  @Override
  public Bytes4 getGenesisForkVersion() {
    return specConfig.getGenesisForkVersion();
  }

  @Override
  public Bytes getBlsWithdrawalPrefix() {
    return specConfig.getBlsWithdrawalPrefix();
  }

  @Override
  public UInt64 getGenesisDelay() {
    return specConfig.getGenesisDelay();
  }

  @Override
  public Bytes4 getAltairForkVersion() {
    return specConfig.getAltairForkVersion();
  }

  @Override
  public UInt64 getAltairForkEpoch() {
    return specConfig.getAltairForkEpoch();
  }

  @Override
  public Bytes4 getBellatrixForkVersion() {
    return specConfig.getBellatrixForkVersion();
  }

  @Override
  public UInt64 getBellatrixForkEpoch() {
    return specConfig.getBellatrixForkEpoch();
  }

  @Override
  public Bytes4 getCapellaForkVersion() {
    return specConfig.getCapellaForkVersion();
  }

  @Override
  public UInt64 getCapellaForkEpoch() {
    return specConfig.getCapellaForkEpoch();
  }

  @Override
  public Bytes4 getDenebForkVersion() {
    return specConfig.getDenebForkVersion();
  }

  @Override
  public UInt64 getDenebForkEpoch() {
    return specConfig.getDenebForkEpoch();
  }

  @Override
  public Bytes4 getElectraForkVersion() {
    return specConfig.getElectraForkVersion();
  }

  @Override
  public UInt64 getElectraForkEpoch() {
    return specConfig.getElectraForkEpoch();
  }

  @Override
  public Bytes4 getFuluForkVersion() {
    return specConfig.getFuluForkVersion();
  }

  @Override
  public UInt64 getFuluForkEpoch() {
    return specConfig.getFuluForkEpoch();
  }

  @Override
  public Bytes4 getGloasForkVersion() {
    return specConfig.getGloasForkVersion();
  }

  @Override
  public UInt64 getGloasForkEpoch() {
    return specConfig.getGloasForkEpoch();
  }

  @Override
  public Bytes4 getHezeForkVersion() {
    return specConfig.getHezeForkVersion();
  }

  @Override
  public UInt64 getHezeForkEpoch() {
    return specConfig.getHezeForkEpoch();
  }

  @Override
  public int getSecondsPerSlot() {
    return specConfig.getSecondsPerSlot();
  }

  @Override
  public int getProposerReorgCutoffBps() {
    return specConfig.getProposerReorgCutoffBps();
  }

  @Override
  public int getAttestationDueBps() {
    return specConfig.getAttestationDueBps();
  }

  @Override
  public int getAggregateDueBps() {
    return specConfig.getAggregateDueBps();
  }

  @Override
  public int getSlotDurationMillis() {
    return specConfig.getSlotDurationMillis();
  }

  @Override
  public int getSlotDurationMsHeze() {
    return specConfig.getSlotDurationMsHeze();
  }

  @Override
  public int getMinAttestationInclusionDelay() {
    return specConfig.getMinAttestationInclusionDelay();
  }

  @Override
  public int getSlotsPerEpoch() {
    return specConfig.getSlotsPerEpoch();
  }

  @Override
  public long getSquareRootSlotsPerEpoch() {
    return specConfig.getSquareRootSlotsPerEpoch();
  }

  @Override
  public int getMinSeedLookahead() {
    return specConfig.getMinSeedLookahead();
  }

  @Override
  public int getMaxSeedLookahead() {
    return specConfig.getMaxSeedLookahead();
  }

  @Override
  public UInt64 getMinEpochsToInactivityPenalty() {
    return specConfig.getMinEpochsToInactivityPenalty();
  }

  @Override
  public int getEpochsPerEth1VotingPeriod() {
    return specConfig.getEpochsPerEth1VotingPeriod();
  }

  @Override
  public int getSlotsPerHistoricalRoot() {
    return specConfig.getSlotsPerHistoricalRoot();
  }

  @Override
  public int getMinValidatorWithdrawabilityDelay() {
    return specConfig.getMinValidatorWithdrawabilityDelay();
  }

  @Override
  public UInt64 getShardCommitteePeriod() {
    return specConfig.getShardCommitteePeriod();
  }

  @Override
  public int getEpochsPerHistoricalVector() {
    return specConfig.getEpochsPerHistoricalVector();
  }

  @Override
  public int getEpochsPerSlashingsVector() {
    return specConfig.getEpochsPerSlashingsVector();
  }

  @Override
  public int getHistoricalRootsLimit() {
    return specConfig.getHistoricalRootsLimit();
  }

  @Override
  public long getValidatorRegistryLimit() {
    return specConfig.getValidatorRegistryLimit();
  }

  @Override
  public int getBaseRewardFactor() {
    return specConfig.getBaseRewardFactor();
  }

  @Override
  public int getWhistleblowerRewardQuotient() {
    return specConfig.getWhistleblowerRewardQuotient();
  }

  @Override
  public UInt64 getProposerRewardQuotient() {
    return specConfig.getProposerRewardQuotient();
  }

  @Override
  public UInt64 getInactivityPenaltyQuotient() {
    return specConfig.getInactivityPenaltyQuotient();
  }

  @Override
  public int getMinSlashingPenaltyQuotient() {
    return specConfig.getMinSlashingPenaltyQuotient();
  }

  @Override
  public int getMaxProposerSlashings() {
    return specConfig.getMaxProposerSlashings();
  }

  @Override
  public int getMaxAttesterSlashings() {
    return specConfig.getMaxAttesterSlashings();
  }

  @Override
  public int getMaxAttestations() {
    return specConfig.getMaxAttestations();
  }

  @Override
  public int getMaxDeposits() {
    return specConfig.getMaxDeposits();
  }

  @Override
  public int getMaxVoluntaryExits() {
    return specConfig.getMaxVoluntaryExits();
  }

  @Override
  public int getSecondsPerEth1Block() {
    return specConfig.getSecondsPerEth1Block();
  }

  @Override
  public int getReorgMaxEpochsSinceFinalization() {
    return specConfig.getReorgMaxEpochsSinceFinalization();
  }

  @Override
  public int getReorgHeadWeightThreshold() {
    return specConfig.getReorgHeadWeightThreshold();
  }

  @Override
  public int getReorgParentWeightThreshold() {
    return specConfig.getReorgParentWeightThreshold();
  }

  @Override
  public long getDepositChainId() {
    return specConfig.getDepositChainId();
  }

  @Override
  public long getDepositNetworkId() {
    return specConfig.getDepositNetworkId();
  }

  @Override
  public Eth1Address getDepositContractAddress() {
    return specConfig.getDepositContractAddress();
  }

  @Override
  public int getMaxPayloadSize() {
    return specConfig.getMaxPayloadSize();
  }

  @Override
  public int getAttestationPropagationSlotRange() {
    return specConfig.getAttestationPropagationSlotRange();
  }

  @Override
  public int getMaximumGossipClockDisparity() {
    return specConfig.getMaximumGossipClockDisparity();
  }

  @Override
  public Bytes4 getMessageDomainInvalidSnappy() {
    return specConfig.getMessageDomainInvalidSnappy();
  }

  @Override
  public Bytes4 getMessageDomainValidSnappy() {
    return specConfig.getMessageDomainValidSnappy();
  }

  @Override
  public int getMaxRequestBlocks() {
    return specConfig.getMaxRequestBlocks();
  }

  @Override
  public int getEpochsPerSubnetSubscription() {
    return specConfig.getEpochsPerSubnetSubscription();
  }

  @Override
  public int getMinEpochsForBlockRequests() {
    return specConfig.getMinEpochsForBlockRequests();
  }

  @Override
  public int getSubnetsPerNode() {
    return specConfig.getSubnetsPerNode();
  }

  @Override
  public int getAttestationSubnetCount() {
    return specConfig.getAttestationSubnetCount();
  }

  @Override
  public int getAttestationSubnetExtraBits() {
    return specConfig.getAttestationSubnetExtraBits();
  }

  @Override
  public int getAttestationSubnetPrefixBits() {
    return specConfig.getAttestationSubnetPrefixBits();
  }

  @Override
  public int getProposerScoreBoost() {
    return specConfig.getProposerScoreBoost();
  }

  @Override
  public Optional<UInt64> getEphemeryResetPeriod() {
    return specConfig.getEphemeryResetPeriod();
  }

  @Override
  public SpecMilestone getMilestone() {
    return specConfig.getMilestone();
  }

  @Override
  public BLSSignatureVerifier getBLSSignatureVerifier() {
    return specConfig.getBLSSignatureVerifier();
  }

  @Override
  public BatchSignatureVerifier createBatchSignatureVerifier() {
    return specConfig.createBatchSignatureVerifier();
  }
}
