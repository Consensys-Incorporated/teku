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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.config.SpecConfigPhase0;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifierImpl;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class SpecConfigBuilder {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<String, Object> rawConfig = new HashMap<>();

  // Misc
  private UInt64 eth1FollowDistance;
  private Integer maxCommitteesPerSlot;
  private Integer targetCommitteeSize;
  private Integer maxValidatorsPerCommittee;
  private Integer minPerEpochChurnLimit;
  private Integer churnLimitQuotient;
  private Integer shuffleRoundCount;
  private Integer minGenesisActiveValidatorCount;
  private UInt64 minGenesisTime;
  private UInt64 hysteresisQuotient;
  private UInt64 hysteresisDownwardMultiplier;
  private UInt64 hysteresisUpwardMultiplier;
  private Integer proportionalSlashingMultiplier;

  // Gwei values
  private UInt64 minDepositAmount;
  private UInt64 maxEffectiveBalance;
  private UInt64 ejectionBalance;
  private UInt64 effectiveBalanceIncrement;

  // Initial values
  private Bytes4 genesisForkVersion;

  // Time parameters
  private UInt64 genesisDelay;
  private Integer secondsPerSlot;

  private Integer slotDurationMs;
  private Integer slotDurationMsHeze;
  private Integer proposerReorgCutoffBps;
  private Integer attestationDueBps;
  private Integer aggregateDueBps;

  private Integer minAttestationInclusionDelay;
  private Integer slotsPerEpoch;
  private Integer minSeedLookahead;
  private Integer maxSeedLookahead;
  private UInt64 minEpochsToInactivityPenalty;
  private Integer epochsPerEth1VotingPeriod;
  private Integer slotsPerHistoricalRoot;
  private Integer minValidatorWithdrawabilityDelay;
  private UInt64 shardCommitteePeriod;

  // State list lengths
  private Integer epochsPerHistoricalVector;
  private Integer epochsPerSlashingsVector;
  private Integer historicalRootsLimit;
  private Long validatorRegistryLimit;

  // Reward and penalty quotients
  private Integer baseRewardFactor;
  private Integer whistleblowerRewardQuotient;
  private UInt64 proposerRewardQuotient;
  private UInt64 inactivityPenaltyQuotient;
  private Integer minSlashingPenaltyQuotient;

  // Max transactions per block
  private Integer maxProposerSlashings;
  private Integer maxAttesterSlashings;
  private Integer maxAttestations;
  private Integer maxDeposits;
  private Integer maxVoluntaryExits;

  // Validator
  private Integer secondsPerEth1Block;

  // Fork Choice
  // Added after Phase0 was live, so default to 0 which disables proposer score boosting.
  private Integer proposerScoreBoost = 0;

  // Deposit Contract
  private Long depositChainId;
  private Long depositNetworkId;
  private Eth1Address depositContractAddress;

  // Networking
  private Integer maxPayloadSize;
  private Integer maxRequestBlocks;
  private Integer epochsPerSubnetSubscription;
  private Integer attestationPropagationSlotRange;
  private Integer maximumGossipClockDisparity;
  private Bytes4 messageDomainInvalidSnappy;
  private Bytes4 messageDomainValidSnappy;
  private Integer subnetsPerNode;
  private Integer minEpochsForBlockRequests;
  private Integer attestationSubnetCount;
  private Integer attestationSubnetExtraBits;
  private Integer attestationSubnetPrefixBits;

  // added after Phase0, so add default values, or will be compatibility issue
  private Integer reorgMaxEpochsSinceFinalization = 2;

  private Integer reorgHeadWeightThreshold = 20;

  private Integer reorgParentWeightThreshold = 160;
  private final AltairBuilder altairBuilder = new AltairBuilder();
  private final BellatrixBuilder bellatrixBuilder = new BellatrixBuilder();
  private final CapellaBuilder capellaBuilder = new CapellaBuilder();
  private final DenebBuilder denebBuilder = new DenebBuilder();
  private final ElectraBuilder electraBuilder = new ElectraBuilder();
  private final FuluBuilder fuluBuilder = new FuluBuilder();
  private final GloasBuilder gloasBuilder = new GloasBuilder();
  private final HezeBuilder hezeBuilder = new HezeBuilder();

  // Forks
  private Bytes4 altairForkVersion;
  private Bytes4 bellatrixForkVersion;
  private Bytes4 capellaForkVersion;
  private Bytes4 denebForkVersion;
  private Bytes4 electraForkVersion;
  private Bytes4 fuluForkVersion;
  private Bytes4 gloasForkVersion;
  private Bytes4 hezeForkVersion;
  private UInt64 altairForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 bellatrixForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 capellaForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 denebForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 electraForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 fuluForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 gloasForkEpoch = FAR_FUTURE_EPOCH;
  private UInt64 hezeForkEpoch = FAR_FUTURE_EPOCH;

  private BLSSignatureVerifier blsSignatureVerifier = BLSSignatureVerifier.SIMPLE;
  private Supplier<BatchSignatureVerifier> batchSignatureVerifierSupplier =
      BatchSignatureVerifierImpl::new;

  private UInt64 maxPerEpochActivationExitChurnLimit = UInt64.valueOf(256000000000L);

  // Ephemery-specific optional field
  private UInt64 ephemeryResetPeriod;

  private final BuilderChain<SpecConfig, SpecConfigHeze> builderChain =
      BuilderChain.create(altairBuilder)
          .appendBuilder(bellatrixBuilder)
          .appendBuilder(capellaBuilder)
          .appendBuilder(denebBuilder)
          .appendBuilder(electraBuilder)
          .appendBuilder(fuluBuilder)
          .appendBuilder(gloasBuilder)
          .appendBuilder(hezeBuilder);

  public SpecConfigAndParent<SpecConfigHeze> build() {
    builderChain.addOverridableItemsToRawConfig(
        (key, value) -> {
          if (value != null) {
            rawConfig.put(key, value);
          }
        });

    if (maxPayloadSize == null && rawConfig.containsKey("GOSSIP_MAX_SIZE")) {
      try {
        // for compatibility, add this constant if its missing but we got GOSSIP_MAX_SIZE
        // both need to be able to initialize due to renamed global config constant.
        final String gossipMaxSize = (String) rawConfig.get("GOSSIP_MAX_SIZE");
        rawConfig.put("MAX_PAYLOAD_SIZE", gossipMaxSize);
        maxPayloadSize(Integer.parseInt(gossipMaxSize));
      } catch (NumberFormatException e) {
        LOG.error("Failed to parse GOSSIP_MAX_SIZE", e);
      }
    }

    // we need to make sure we have both fields set as we may only get one or the other from config
    // currently.
    if (slotDurationMs == null && secondsPerSlot != null) {
      LOG.debug("Defaulting slotDurationMs from secondsPerSlot: {}", secondsPerSlot);
      slotDurationMs = secondsToMillis(secondsPerSlot).intValue();
    } else if (secondsPerSlot == null && slotDurationMs != null) {
      LOG.debug("Defaulting secondsPerSlot from slotDurationMs: {}", slotDurationMs);
      secondsPerSlot = millisToSeconds(slotDurationMs).intValue();
    } else if (slotDurationMs != null
        && secondsPerSlot != null
        && slotDurationMs != secondsPerSlot * 1000) {
      throw new IllegalArgumentException(
          String.format(
              "The specified network configuration had both SLOT_DURATION_MS (%d) and SECONDS_PER_SLOT(%d) defined, and they were inconsistent.",
              slotDurationMs, secondsPerSlot));
    }
    // EIP-8198: the Heze slot duration defaults to the current slot duration
    if (slotDurationMsHeze == null && slotDurationMs != null) {
      slotDurationMsHeze = slotDurationMs;
    }

    // defaulting for compatibility
    if (attestationDueBps == null) {
      attestationDueBps = 3333;
      LOG.debug("Defaulting attestationDueBps to {}", attestationDueBps);
    }

    if (aggregateDueBps == null) {
      aggregateDueBps = 6667;
      LOG.debug("Defaulting aggregateDueBps to {}", aggregateDueBps);
    }

    if (proposerReorgCutoffBps == null) {
      proposerReorgCutoffBps = 1667;
      LOG.debug("Defaulting proposerReorgCutoffBps to {}", proposerReorgCutoffBps);
    }

    if (minEpochsForBlockRequests == null
        && minValidatorWithdrawabilityDelay != null
        && churnLimitQuotient != null) {
      final Integer newMinEpochsForBlockRequests = computeMinEpochsForBlockRequests();
      LOG.debug(
          "Setting minEpochsForBlockRequests to {} (was {})",
          newMinEpochsForBlockRequests,
          minEpochsForBlockRequests);
      minEpochsForBlockRequests = newMinEpochsForBlockRequests;
    }

    if (attestationSubnetCount != null && attestationSubnetExtraBits != null) {
      final Integer newAttestationSubnetPrefixBits = computeAttestationSubnetPrefixBits();
      LOG.debug(
          "Setting attestationSubnetPrefixBits to {} (was {})",
          newAttestationSubnetPrefixBits,
          attestationSubnetPrefixBits);
      attestationSubnetPrefixBits = newAttestationSubnetPrefixBits;
    }

    applyForkVersions();
    validate();
    final SpecConfigAndParent<SpecConfig> config =
        SpecConfigAndParent.of(
            new SpecConfigPhase0(
                rawConfig,
                eth1FollowDistance,
                maxCommitteesPerSlot,
                targetCommitteeSize,
                maxValidatorsPerCommittee,
                minPerEpochChurnLimit,
                churnLimitQuotient,
                shuffleRoundCount,
                minGenesisActiveValidatorCount,
                minGenesisTime,
                hysteresisQuotient,
                hysteresisDownwardMultiplier,
                hysteresisUpwardMultiplier,
                proportionalSlashingMultiplier,
                minDepositAmount,
                maxEffectiveBalance,
                ejectionBalance,
                effectiveBalanceIncrement,
                genesisForkVersion,
                genesisDelay,
                secondsPerSlot,
                minAttestationInclusionDelay,
                slotsPerEpoch,
                minSeedLookahead,
                maxSeedLookahead,
                minEpochsToInactivityPenalty,
                epochsPerEth1VotingPeriod,
                slotsPerHistoricalRoot,
                minValidatorWithdrawabilityDelay,
                shardCommitteePeriod,
                epochsPerHistoricalVector,
                epochsPerSlashingsVector,
                historicalRootsLimit,
                validatorRegistryLimit,
                baseRewardFactor,
                whistleblowerRewardQuotient,
                proposerRewardQuotient,
                inactivityPenaltyQuotient,
                minSlashingPenaltyQuotient,
                maxProposerSlashings,
                maxAttesterSlashings,
                maxAttestations,
                maxDeposits,
                maxVoluntaryExits,
                secondsPerEth1Block,
                proposerScoreBoost,
                depositChainId,
                depositNetworkId,
                depositContractAddress,
                maxPayloadSize,
                maxRequestBlocks,
                epochsPerSubnetSubscription,
                minEpochsForBlockRequests,
                attestationPropagationSlotRange,
                maximumGossipClockDisparity,
                messageDomainInvalidSnappy,
                messageDomainValidSnappy,
                subnetsPerNode,
                attestationSubnetCount,
                attestationSubnetExtraBits,
                attestationSubnetPrefixBits,
                reorgMaxEpochsSinceFinalization,
                reorgHeadWeightThreshold,
                reorgParentWeightThreshold,
                maxPerEpochActivationExitChurnLimit,
                slotDurationMs,
                slotDurationMsHeze,
                attestationDueBps,
                aggregateDueBps,
                proposerReorgCutoffBps,
                blsSignatureVerifier,
                batchSignatureVerifierSupplier,
                altairForkVersion,
                altairForkEpoch,
                bellatrixForkVersion,
                bellatrixForkEpoch,
                capellaForkVersion,
                capellaForkEpoch,
                denebForkVersion,
                denebForkEpoch,
                electraForkVersion,
                electraForkEpoch,
                fuluForkVersion,
                fuluForkEpoch,
                gloasForkVersion,
                gloasForkEpoch,
                hezeForkVersion,
                hezeForkEpoch,
                Optional.ofNullable(ephemeryResetPeriod)));

    return builderChain.build(config);
  }

  private Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("eth1FollowDistance", eth1FollowDistance);
    constants.put("maxCommitteesPerSlot", maxCommitteesPerSlot);
    constants.put("targetCommitteeSize", targetCommitteeSize);
    constants.put("maxValidatorsPerCommittee", maxValidatorsPerCommittee);
    constants.put("minPerEpochChurnLimit", minPerEpochChurnLimit);
    constants.put("churnLimitQuotient", churnLimitQuotient);
    constants.put("shuffleRoundCount", shuffleRoundCount);
    constants.put("minGenesisActiveValidatorCount", minGenesisActiveValidatorCount);
    constants.put("minGenesisTime", minGenesisTime);
    constants.put("hysteresisQuotient", hysteresisQuotient);
    constants.put("hysteresisDownwardMultiplier", hysteresisDownwardMultiplier);
    constants.put("hysteresisUpwardMultiplier", hysteresisUpwardMultiplier);
    constants.put("proportionalSlashingMultiplier", proportionalSlashingMultiplier);
    constants.put("minDepositAmount", minDepositAmount);
    constants.put("maxEffectiveBalance", maxEffectiveBalance);
    constants.put("ejectionBalance", ejectionBalance);
    constants.put("effectiveBalanceIncrement", effectiveBalanceIncrement);
    constants.put("genesisForkVersion", genesisForkVersion);
    constants.put("genesisDelay", genesisDelay);
    constants.put("secondsPerSlot", secondsPerSlot);
    constants.put("slotDurationMs", slotDurationMs);
    constants.put("slotDurationMsHeze", slotDurationMsHeze);
    constants.put("proposerReorgCutoffBps", proposerReorgCutoffBps);
    constants.put("attestationDueBps", attestationDueBps);
    constants.put("aggregateDueBps", aggregateDueBps);
    constants.put("minAttestationInclusionDelay", minAttestationInclusionDelay);
    constants.put("slotsPerEpoch", slotsPerEpoch);
    constants.put("minSeedLookahead", minSeedLookahead);
    constants.put("maxSeedLookahead", maxSeedLookahead);
    constants.put("minEpochsToInactivityPenalty", minEpochsToInactivityPenalty);
    constants.put("epochsPerEth1VotingPeriod", epochsPerEth1VotingPeriod);
    constants.put("slotsPerHistoricalRoot", slotsPerHistoricalRoot);
    constants.put("minValidatorWithdrawabilityDelay", minValidatorWithdrawabilityDelay);
    constants.put("shardCommitteePeriod", shardCommitteePeriod);
    constants.put("epochsPerHistoricalVector", epochsPerHistoricalVector);
    constants.put("epochsPerSlashingsVector", epochsPerSlashingsVector);
    constants.put("historicalRootsLimit", historicalRootsLimit);
    constants.put("validatorRegistryLimit", validatorRegistryLimit);
    constants.put("baseRewardFactor", baseRewardFactor);
    constants.put("whistleblowerRewardQuotient", whistleblowerRewardQuotient);
    constants.put("proposerRewardQuotient", proposerRewardQuotient);
    constants.put("inactivityPenaltyQuotient", inactivityPenaltyQuotient);
    constants.put("minSlashingPenaltyQuotient", minSlashingPenaltyQuotient);
    constants.put("maxProposerSlashings", maxProposerSlashings);
    constants.put("maxAttesterSlashings", maxAttesterSlashings);
    constants.put("maxAttestations", maxAttestations);
    constants.put("maxDeposits", maxDeposits);
    constants.put("maxVoluntaryExits", maxVoluntaryExits);
    constants.put("secondsPerEth1Block", secondsPerEth1Block);
    constants.put("depositChainId", depositChainId);
    constants.put("depositNetworkId", depositNetworkId);
    constants.put("depositContractAddress", depositContractAddress);

    constants.put("maxPayloadSize", maxPayloadSize);
    constants.put("maxRequestBlocks", maxRequestBlocks);
    constants.put("epochsPerSubnetSubscription", epochsPerSubnetSubscription);
    constants.put("minEpochsForBlockRequests", minEpochsForBlockRequests);
    constants.put("attestationPropagationSlotRange", attestationPropagationSlotRange);
    constants.put("maximumGossipClockDisparity", maximumGossipClockDisparity);
    constants.put("messageDomainInvalidSnappy", messageDomainInvalidSnappy);
    constants.put("messageDomainValidSnappy", messageDomainValidSnappy);
    constants.put("subnetsPerNode", subnetsPerNode);
    constants.put("attestationSubnetCount", attestationSubnetCount);
    constants.put("attestationSubnetExtraBits", attestationSubnetExtraBits);
    constants.put("attestationSubnetPrefixBits", attestationSubnetPrefixBits);
    constants.put("reorgMaxEpochsSinceFinalization", reorgMaxEpochsSinceFinalization);
    constants.put("reorgHeadWeightThreshold", reorgHeadWeightThreshold);
    constants.put("reorgParentWeightThreshold", reorgParentWeightThreshold);
    constants.put("altairForkEpoch", altairForkEpoch);
    constants.put("altairForkVersion", altairForkVersion);
    constants.put("bellatrixForkEpoch", bellatrixForkEpoch);
    constants.put("bellatrixForkVersion", bellatrixForkVersion);
    constants.put("capellaForkVersion", capellaForkVersion);
    constants.put("capellaForkEpoch", capellaForkEpoch);
    constants.put("denebForkVersion", denebForkVersion);
    constants.put("denebForkEpoch", denebForkEpoch);
    constants.put("electraForkVersion", electraForkVersion);
    constants.put("electraForkEpoch", electraForkEpoch);
    constants.put("fuluForkVersion", fuluForkVersion);
    constants.put("fuluForkEpoch", fuluForkEpoch);
    constants.put("gloasForkVersion", gloasForkVersion);
    constants.put("gloasForkEpoch", gloasForkEpoch);
    constants.put("hezeForkVersion", hezeForkVersion);
    constants.put("hezeForkEpoch", hezeForkEpoch);
    return constants;
  }

  private void applyForkVersions() {
    // update raw config if epochs and fork versions are known
    // if they're not known, they'll result in a validation error (expected)
    if (altairForkEpoch.equals(FAR_FUTURE_EPOCH) && altairForkVersion == null) {
      altairForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (bellatrixForkEpoch.equals(FAR_FUTURE_EPOCH) && bellatrixForkVersion == null) {
      bellatrixForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (capellaForkEpoch.equals(FAR_FUTURE_EPOCH) && capellaForkVersion == null) {
      capellaForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (denebForkEpoch.equals(FAR_FUTURE_EPOCH) && denebForkVersion == null) {
      denebForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (electraForkEpoch.equals(FAR_FUTURE_EPOCH) && electraForkVersion == null) {
      electraForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (fuluForkEpoch.equals(FAR_FUTURE_EPOCH) && fuluForkVersion == null) {
      fuluForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (gloasForkEpoch.equals(FAR_FUTURE_EPOCH) && gloasForkVersion == null) {
      gloasForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    if (hezeForkEpoch.equals(FAR_FUTURE_EPOCH) && hezeForkVersion == null) {
      hezeForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // ensure raw config is accurate
    rawConfig.put("ALTAIR_FORK_EPOCH", altairForkEpoch);
    rawConfig.put("BELLATRIX_FORK_EPOCH", bellatrixForkEpoch);
    rawConfig.put("CAPELLA_FORK_EPOCH", capellaForkEpoch);
    rawConfig.put("DENEB_FORK_EPOCH", denebForkEpoch);
    rawConfig.put("ELECTRA_FORK_EPOCH", electraForkEpoch);
    rawConfig.put("FULU_FORK_EPOCH", fuluForkEpoch);
    rawConfig.put("GLOAS_FORK_EPOCH", gloasForkEpoch);
    rawConfig.put("HEZE_FORK_EPOCH", hezeForkEpoch);

    rawConfig.put("ALTAIR_FORK_VERSION", altairForkVersion);
    rawConfig.put("BELLATRIX_FORK_VERSION", bellatrixForkVersion);
    rawConfig.put("CAPELLA_FORK_VERSION", capellaForkVersion);
    rawConfig.put("DENEB_FORK_VERSION", denebForkVersion);
    rawConfig.put("ELECTRA_FORK_VERSION", electraForkVersion);
    rawConfig.put("FULU_FORK_VERSION", fuluForkVersion);
    rawConfig.put("GLOAS_FORK_VERSION", gloasForkVersion);
    rawConfig.put("HEZE_FORK_VERSION", hezeForkVersion);

    // tell the fork builders their fork epoch
    altairBuilder.setForkEpoch(altairForkEpoch);
    bellatrixBuilder.setForkEpoch(bellatrixForkEpoch);
    capellaBuilder.setForkEpoch(capellaForkEpoch);
    denebBuilder.setForkEpoch(denebForkEpoch);
    electraBuilder.setForkEpoch(electraForkEpoch);
    fuluBuilder.setForkEpoch(fuluForkEpoch);
    gloasBuilder.setForkEpoch(gloasForkEpoch);
    hezeBuilder.setForkEpoch(hezeForkEpoch);
  }

  private void validate() {
    checkArgument(!rawConfig.isEmpty(), "Raw spec config must be provided");
    final List<Optional<String>> maybeErrors = new ArrayList<>();
    final Map<String, Object> constants = getValidationMap();

    constants.forEach((k, v) -> maybeErrors.add(SpecBuilderUtil.validateConstant(k, v)));

    final List<String> fieldsFailingValidation =
        maybeErrors.stream().filter(Optional::isPresent).map(Optional::get).toList();

    if (!fieldsFailingValidation.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The specified network configuration had missing or invalid values for constants %s",
              String.join(", ", fieldsFailingValidation)));
    }
    if (!hezeForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      checkArgument(
          slotDurationMsHeze > 0 && slotDurationMsHeze % 1000 == 0,
          "SLOT_DURATION_MS_HEZE (%s) must be a positive multiple of 1000",
          slotDurationMsHeze);
    }
    builderChain.validate();
  }

  public SpecConfigBuilder rawConfig(final Map<String, ?> rawConfig) {
    checkNotNull(rawConfig);
    this.rawConfig.putAll(rawConfig);
    return this;
  }

  public SpecConfigBuilder eth1FollowDistance(final UInt64 eth1FollowDistance) {
    checkNotNull(eth1FollowDistance);
    this.eth1FollowDistance = eth1FollowDistance;
    return this;
  }

  public SpecConfigBuilder maxCommitteesPerSlot(final Integer maxCommitteesPerSlot) {
    checkNotNull(maxCommitteesPerSlot);
    this.maxCommitteesPerSlot = maxCommitteesPerSlot;
    return this;
  }

  public SpecConfigBuilder targetCommitteeSize(final Integer targetCommitteeSize) {
    checkNotNull(targetCommitteeSize);
    this.targetCommitteeSize = targetCommitteeSize;
    return this;
  }

  public SpecConfigBuilder maxValidatorsPerCommittee(final Integer maxValidatorsPerCommittee) {
    checkNotNull(maxValidatorsPerCommittee);
    this.maxValidatorsPerCommittee = maxValidatorsPerCommittee;
    return this;
  }

  public SpecConfigBuilder minPerEpochChurnLimit(final Integer minPerEpochChurnLimit) {
    checkNotNull(minPerEpochChurnLimit);
    this.minPerEpochChurnLimit = minPerEpochChurnLimit;
    return this;
  }

  public SpecConfigBuilder churnLimitQuotient(final Integer churnLimitQuotient) {
    checkNotNull(churnLimitQuotient);
    this.churnLimitQuotient = churnLimitQuotient;
    return this;
  }

  public SpecConfigBuilder shuffleRoundCount(final Integer shuffleRoundCount) {
    checkNotNull(shuffleRoundCount);
    this.shuffleRoundCount = shuffleRoundCount;
    return this;
  }

  public SpecConfigBuilder minGenesisActiveValidatorCount(
      final Integer minGenesisActiveValidatorCount) {
    checkNotNull(minGenesisActiveValidatorCount);
    this.minGenesisActiveValidatorCount = minGenesisActiveValidatorCount;
    return this;
  }

  public SpecConfigBuilder minGenesisTime(final UInt64 minGenesisTime) {
    checkNotNull(minGenesisTime);
    this.minGenesisTime = minGenesisTime;
    return this;
  }

  public SpecConfigBuilder hysteresisQuotient(final UInt64 hysteresisQuotient) {
    checkNotNull(hysteresisQuotient);
    this.hysteresisQuotient = hysteresisQuotient;
    return this;
  }

  public SpecConfigBuilder hysteresisDownwardMultiplier(final UInt64 hysteresisDownwardMultiplier) {
    checkNotNull(hysteresisDownwardMultiplier);
    this.hysteresisDownwardMultiplier = hysteresisDownwardMultiplier;
    return this;
  }

  public SpecConfigBuilder hysteresisUpwardMultiplier(final UInt64 hysteresisUpwardMultiplier) {
    checkNotNull(hysteresisUpwardMultiplier);
    this.hysteresisUpwardMultiplier = hysteresisUpwardMultiplier;
    return this;
  }

  public SpecConfigBuilder proportionalSlashingMultiplier(
      final Integer proportionalSlashingMultiplier) {
    checkNotNull(proportionalSlashingMultiplier);
    this.proportionalSlashingMultiplier = proportionalSlashingMultiplier;
    return this;
  }

  public SpecConfigBuilder minDepositAmount(final UInt64 minDepositAmount) {
    checkNotNull(minDepositAmount);
    this.minDepositAmount = minDepositAmount;
    return this;
  }

  public SpecConfigBuilder maxEffectiveBalance(final UInt64 maxEffectiveBalance) {
    checkNotNull(maxEffectiveBalance);
    this.maxEffectiveBalance = maxEffectiveBalance;
    return this;
  }

  public SpecConfigBuilder ejectionBalance(final UInt64 ejectionBalance) {
    checkNotNull(ejectionBalance);
    this.ejectionBalance = ejectionBalance;
    return this;
  }

  public SpecConfigBuilder effectiveBalanceIncrement(final UInt64 effectiveBalanceIncrement) {
    checkNotNull(effectiveBalanceIncrement);
    this.effectiveBalanceIncrement = effectiveBalanceIncrement;
    return this;
  }

  public SpecConfigBuilder genesisForkVersion(final Bytes4 genesisForkVersion) {
    checkNotNull(genesisForkVersion);
    this.genesisForkVersion = genesisForkVersion;
    return this;
  }

  public SpecConfigBuilder altairForkVersion(final Bytes4 altairForkVersion) {
    checkNotNull(altairForkVersion);
    this.altairForkVersion = altairForkVersion;
    return this;
  }

  public SpecConfigBuilder altairForkEpoch(final UInt64 altairForkEpoch) {
    checkNotNull(altairForkEpoch);
    this.altairForkEpoch = altairForkEpoch;
    return this;
  }

  public SpecConfigBuilder bellatrixForkVersion(final Bytes4 bellatrixForkVersion) {
    checkNotNull(bellatrixForkVersion);
    this.bellatrixForkVersion = bellatrixForkVersion;
    return this;
  }

  public SpecConfigBuilder bellatrixForkEpoch(final UInt64 bellatrixForkEpoch) {
    checkNotNull(bellatrixForkEpoch);
    this.bellatrixForkEpoch = bellatrixForkEpoch;
    return this;
  }

  public SpecConfigBuilder capellaForkVersion(final Bytes4 capellaForkVersion) {
    checkNotNull(capellaForkVersion);
    this.capellaForkVersion = capellaForkVersion;
    return this;
  }

  public SpecConfigBuilder capellaForkEpoch(final UInt64 capellaForkEpoch) {
    checkNotNull(capellaForkEpoch);
    this.capellaForkEpoch = capellaForkEpoch;
    return this;
  }

  public SpecConfigBuilder denebForkVersion(final Bytes4 denebForkVersion) {
    checkNotNull(denebForkVersion);
    this.denebForkVersion = denebForkVersion;
    return this;
  }

  public SpecConfigBuilder denebForkEpoch(final UInt64 denebForkEpoch) {
    checkNotNull(denebForkEpoch);
    this.denebForkEpoch = denebForkEpoch;
    return this;
  }

  public SpecConfigBuilder electraForkVersion(final Bytes4 electraForkVersion) {
    checkNotNull(electraForkVersion);
    this.electraForkVersion = electraForkVersion;
    return this;
  }

  public SpecConfigBuilder electraForkEpoch(final UInt64 electraForkEpoch) {
    checkNotNull(electraForkEpoch);
    this.electraForkEpoch = electraForkEpoch;
    return this;
  }

  public SpecConfigBuilder fuluForkVersion(final Bytes4 fuluForkVersion) {
    checkNotNull(fuluForkVersion);
    this.fuluForkVersion = fuluForkVersion;
    return this;
  }

  public SpecConfigBuilder fuluForkEpoch(final UInt64 fuluForkEpoch) {
    checkNotNull(fuluForkEpoch);
    this.fuluForkEpoch = fuluForkEpoch;
    return this;
  }

  public SpecConfigBuilder gloasForkVersion(final Bytes4 gloasForkVersion) {
    checkNotNull(gloasForkVersion);
    this.gloasForkVersion = gloasForkVersion;
    return this;
  }

  public SpecConfigBuilder gloasForkEpoch(final UInt64 gloasForkEpoch) {
    checkNotNull(gloasForkEpoch);
    this.gloasForkEpoch = gloasForkEpoch;
    return this;
  }

  public SpecConfigBuilder hezeForkVersion(final Bytes4 hezeForkVersion) {
    checkNotNull(hezeForkVersion);
    this.hezeForkVersion = hezeForkVersion;
    return this;
  }

  public SpecConfigBuilder hezeForkEpoch(final UInt64 hezeForkEpoch) {
    checkNotNull(hezeForkEpoch);
    this.hezeForkEpoch = hezeForkEpoch;
    return this;
  }

  public SpecConfigBuilder genesisDelay(final UInt64 genesisDelay) {
    checkNotNull(genesisDelay);
    this.genesisDelay = genesisDelay;
    return this;
  }

  public SpecConfigBuilder secondsPerSlot(final Integer secondsPerSlot) {
    checkNotNull(secondsPerSlot);
    this.secondsPerSlot = secondsPerSlot;
    return this;
  }

  public SpecConfigBuilder slotDurationMsHeze(final Integer slotDurationMsHeze) {
    checkNotNull(slotDurationMsHeze);
    this.slotDurationMsHeze = slotDurationMsHeze;
    return this;
  }

  public SpecConfigBuilder slotDurationMs(final Integer slotDurationMs) {
    checkNotNull(slotDurationMs);
    this.slotDurationMs = slotDurationMs;
    return this;
  }

  public SpecConfigBuilder proposerReorgCutoffBps(final Integer proposerReorgCutoffBps) {
    checkNotNull(proposerReorgCutoffBps);
    this.proposerReorgCutoffBps = proposerReorgCutoffBps;
    return this;
  }

  public SpecConfigBuilder attestationDueBps(final Integer attestationDueBps) {
    checkNotNull(attestationDueBps);
    this.attestationDueBps = attestationDueBps;
    return this;
  }

  public SpecConfigBuilder aggregateDueBps(final Integer aggregateDueBps) {
    checkNotNull(aggregateDueBps);
    this.aggregateDueBps = aggregateDueBps;
    return this;
  }

  public SpecConfigBuilder minAttestationInclusionDelay(
      final Integer minAttestationInclusionDelay) {
    checkNotNull(minAttestationInclusionDelay);
    this.minAttestationInclusionDelay = minAttestationInclusionDelay;
    return this;
  }

  public SpecConfigBuilder slotsPerEpoch(final Integer slotsPerEpoch) {
    checkNotNull(slotsPerEpoch);
    this.slotsPerEpoch = slotsPerEpoch;
    return this;
  }

  public SpecConfigBuilder minSeedLookahead(final Integer minSeedLookahead) {
    checkNotNull(minSeedLookahead);
    this.minSeedLookahead = minSeedLookahead;
    return this;
  }

  public SpecConfigBuilder maxSeedLookahead(final Integer maxSeedLookahead) {
    checkNotNull(maxSeedLookahead);
    this.maxSeedLookahead = maxSeedLookahead;
    return this;
  }

  public SpecConfigBuilder minEpochsToInactivityPenalty(final UInt64 minEpochsToInactivityPenalty) {
    checkNotNull(minEpochsToInactivityPenalty);
    this.minEpochsToInactivityPenalty = minEpochsToInactivityPenalty;
    return this;
  }

  public SpecConfigBuilder epochsPerEth1VotingPeriod(final Integer epochsPerEth1VotingPeriod) {
    checkNotNull(epochsPerEth1VotingPeriod);
    this.epochsPerEth1VotingPeriod = epochsPerEth1VotingPeriod;
    return this;
  }

  public SpecConfigBuilder slotsPerHistoricalRoot(final Integer slotsPerHistoricalRoot) {
    checkNotNull(slotsPerHistoricalRoot);
    this.slotsPerHistoricalRoot = slotsPerHistoricalRoot;
    return this;
  }

  public SpecConfigBuilder minValidatorWithdrawabilityDelay(
      final Integer minValidatorWithdrawabilityDelay) {
    checkNotNull(minValidatorWithdrawabilityDelay);
    this.minValidatorWithdrawabilityDelay = minValidatorWithdrawabilityDelay;
    return this;
  }

  public SpecConfigBuilder shardCommitteePeriod(final UInt64 shardCommitteePeriod) {
    checkNotNull(shardCommitteePeriod);
    this.shardCommitteePeriod = shardCommitteePeriod;
    return this;
  }

  public SpecConfigBuilder epochsPerHistoricalVector(final Integer epochsPerHistoricalVector) {
    checkNotNull(epochsPerHistoricalVector);
    this.epochsPerHistoricalVector = epochsPerHistoricalVector;
    return this;
  }

  public SpecConfigBuilder epochsPerSlashingsVector(final Integer epochsPerSlashingsVector) {
    checkNotNull(epochsPerSlashingsVector);
    this.epochsPerSlashingsVector = epochsPerSlashingsVector;
    return this;
  }

  public SpecConfigBuilder historicalRootsLimit(final Integer historicalRootsLimit) {
    checkNotNull(historicalRootsLimit);
    this.historicalRootsLimit = historicalRootsLimit;
    return this;
  }

  public SpecConfigBuilder validatorRegistryLimit(final Long validatorRegistryLimit) {
    checkNotNull(validatorRegistryLimit);
    this.validatorRegistryLimit = validatorRegistryLimit;
    return this;
  }

  public SpecConfigBuilder baseRewardFactor(final Integer baseRewardFactor) {
    checkNotNull(baseRewardFactor);
    this.baseRewardFactor = baseRewardFactor;
    return this;
  }

  public SpecConfigBuilder whistleblowerRewardQuotient(final Integer whistleblowerRewardQuotient) {
    checkNotNull(whistleblowerRewardQuotient);
    this.whistleblowerRewardQuotient = whistleblowerRewardQuotient;
    return this;
  }

  public SpecConfigBuilder proposerRewardQuotient(final UInt64 proposerRewardQuotient) {
    checkNotNull(proposerRewardQuotient);
    this.proposerRewardQuotient = proposerRewardQuotient;
    return this;
  }

  public SpecConfigBuilder maxPerEpochActivationExitChurnLimit(
      final UInt64 maxPerEpochActivationExitChurnLimit) {
    checkNotNull(maxPerEpochActivationExitChurnLimit);
    this.maxPerEpochActivationExitChurnLimit = maxPerEpochActivationExitChurnLimit;
    return this;
  }

  public SpecConfigBuilder inactivityPenaltyQuotient(final UInt64 inactivityPenaltyQuotient) {
    checkNotNull(inactivityPenaltyQuotient);
    this.inactivityPenaltyQuotient = inactivityPenaltyQuotient;
    return this;
  }

  public SpecConfigBuilder minSlashingPenaltyQuotient(final Integer minSlashingPenaltyQuotient) {
    checkNotNull(minSlashingPenaltyQuotient);
    this.minSlashingPenaltyQuotient = minSlashingPenaltyQuotient;
    return this;
  }

  public SpecConfigBuilder maxProposerSlashings(final Integer maxProposerSlashings) {
    checkNotNull(maxProposerSlashings);
    this.maxProposerSlashings = maxProposerSlashings;
    return this;
  }

  public SpecConfigBuilder maxAttesterSlashings(final Integer maxAttesterSlashings) {
    checkNotNull(maxAttesterSlashings);
    this.maxAttesterSlashings = maxAttesterSlashings;
    return this;
  }

  public SpecConfigBuilder maxAttestations(final Integer maxAttestations) {
    checkNotNull(maxAttestations);
    this.maxAttestations = maxAttestations;
    return this;
  }

  public SpecConfigBuilder maxDeposits(final Integer maxDeposits) {
    checkNotNull(maxDeposits);
    this.maxDeposits = maxDeposits;
    return this;
  }

  public SpecConfigBuilder maxVoluntaryExits(final Integer maxVoluntaryExits) {
    checkNotNull(maxVoluntaryExits);
    this.maxVoluntaryExits = maxVoluntaryExits;
    return this;
  }

  public SpecConfigBuilder secondsPerEth1Block(final Integer secondsPerEth1Block) {
    checkNotNull(secondsPerEth1Block);
    this.secondsPerEth1Block = secondsPerEth1Block;
    return this;
  }

  public SpecConfigBuilder proposerScoreBoost(final Integer proposerScoreBoost) {
    checkNotNull(proposerScoreBoost);
    this.proposerScoreBoost = proposerScoreBoost;
    return this;
  }

  public SpecConfigBuilder depositChainId(final Long depositChainId) {
    checkNotNull(depositChainId);
    this.depositChainId = depositChainId;
    return this;
  }

  public SpecConfigBuilder depositNetworkId(final Long depositNetworkId) {
    checkNotNull(depositNetworkId);
    this.depositNetworkId = depositNetworkId;
    return this;
  }

  public SpecConfigBuilder depositContractAddress(final Eth1Address depositContractAddress) {
    checkNotNull(depositContractAddress);
    this.depositContractAddress = depositContractAddress;
    return this;
  }

  public SpecConfigBuilder maxPayloadSize(final Integer maxPayloadSize) {
    this.maxPayloadSize = maxPayloadSize;
    return this;
  }

  public SpecConfigBuilder maxRequestBlocks(final Integer maxRequestBlocks) {
    this.maxRequestBlocks = maxRequestBlocks;
    return this;
  }

  public SpecConfigBuilder epochsPerSubnetSubscription(final Integer epochsPerSubnetSubscription) {
    this.epochsPerSubnetSubscription = epochsPerSubnetSubscription;
    return this;
  }

  @Deprecated
  public SpecConfigBuilder minEpochsForBlockRequests(final Integer minEpochsForBlockRequests) {
    this.minEpochsForBlockRequests = minEpochsForBlockRequests;
    return this;
  }

  public SpecConfigBuilder attestationPropagationSlotRange(
      final Integer attestationPropagationSlotRange) {
    this.attestationPropagationSlotRange = attestationPropagationSlotRange;
    return this;
  }

  public SpecConfigBuilder maximumGossipClockDisparity(final Integer maximumGossipClockDisparity) {
    this.maximumGossipClockDisparity = maximumGossipClockDisparity;
    return this;
  }

  public SpecConfigBuilder messageDomainInvalidSnappy(final Bytes4 messageDomainInvalidSnappy) {
    this.messageDomainInvalidSnappy = messageDomainInvalidSnappy;
    return this;
  }

  public SpecConfigBuilder messageDomainValidSnappy(final Bytes4 messageDomainValidSnappy) {
    this.messageDomainValidSnappy = messageDomainValidSnappy;
    return this;
  }

  public SpecConfigBuilder subnetsPerNode(final Integer subnetsPerNode) {
    this.subnetsPerNode = subnetsPerNode;
    return this;
  }

  @Deprecated
  public SpecConfigBuilder attestationSubnetCount(final Integer attestationSubnetCount) {
    this.attestationSubnetCount = attestationSubnetCount;
    return this;
  }

  public SpecConfigBuilder attestationSubnetExtraBits(final Integer attestationSubnetExtraBits) {
    this.attestationSubnetExtraBits = attestationSubnetExtraBits;
    return this;
  }

  @Deprecated
  public SpecConfigBuilder attestationSubnetPrefixBits(final Integer attestationSubnetPrefixBits) {
    this.attestationSubnetPrefixBits = attestationSubnetPrefixBits;
    return this;
  }

  public SpecConfigBuilder reorgMaxEpochsSinceFinalization(
      final Integer reorgMaxEpochsSinceFinalization) {
    this.reorgMaxEpochsSinceFinalization = reorgMaxEpochsSinceFinalization;
    return this;
  }

  public SpecConfigBuilder reorgHeadWeightThreshold(final Integer reorgHeadWeightThreshold) {
    this.reorgHeadWeightThreshold = reorgHeadWeightThreshold;
    return this;
  }

  public SpecConfigBuilder reorgParentWeightThreshold(final Integer reorgParentWeightThreshold) {
    this.reorgParentWeightThreshold = reorgParentWeightThreshold;
    return this;
  }

  public SpecConfigBuilder ephemeryResetPeriod(final UInt64 ephemeryResetPeriod) {
    checkNotNull(ephemeryResetPeriod);
    this.ephemeryResetPeriod = ephemeryResetPeriod;
    return this;
  }

  public SpecConfigBuilder blsSignatureVerifier(final BLSSignatureVerifier blsSignatureVerifier) {
    this.blsSignatureVerifier = blsSignatureVerifier;
    return this;
  }

  public SpecConfigBuilder batchSignatureVerifierSupplier(
      final Supplier<BatchSignatureVerifier> batchSignatureVerifierSupplier) {
    this.batchSignatureVerifierSupplier = batchSignatureVerifierSupplier;
    return this;
  }

  public SpecConfigBuilder altairBuilder(final Consumer<AltairBuilder> consumer) {
    builderChain.withBuilder(AltairBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder bellatrixBuilder(final Consumer<BellatrixBuilder> consumer) {
    builderChain.withBuilder(BellatrixBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder capellaBuilder(final Consumer<CapellaBuilder> consumer) {
    builderChain.withBuilder(CapellaBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder denebBuilder(final Consumer<DenebBuilder> consumer) {
    builderChain.withBuilder(DenebBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder electraBuilder(final Consumer<ElectraBuilder> consumer) {
    builderChain.withBuilder(ElectraBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder fuluBuilder(final Consumer<FuluBuilder> consumer) {
    builderChain.withBuilder(FuluBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder gloasBuilder(final Consumer<GloasBuilder> consumer) {
    builderChain.withBuilder(GloasBuilder.class, consumer);
    return this;
  }

  public SpecConfigBuilder hezeBuilder(final Consumer<HezeBuilder> consumer) {
    builderChain.withBuilder(HezeBuilder.class, consumer);
    return this;
  }

  // compute_min_epochs_for_block_requests
  private Integer computeMinEpochsForBlockRequests() {
    return computeMinEpochsForBlockRequests(minValidatorWithdrawabilityDelay, churnLimitQuotient);
  }

  // compute_attestation_subnet_prefix_bits
  private Integer computeAttestationSubnetPrefixBits() {
    return computeAttestationSubnetPrefixBits(attestationSubnetCount, attestationSubnetExtraBits);
  }

  public static Integer computeAttestationSubnetPrefixBits(
      final Integer attestationSubnetCount, final Integer attestationSubnetExtraBits) {
    return 32
        - Integer.numberOfLeadingZeros(attestationSubnetCount - 1)
        + attestationSubnetExtraBits;
  }

  public static Integer computeMinEpochsForBlockRequests(
      final int minValidatorWithdrawabilityDelay, final int churnLimitQuotient) {
    return minValidatorWithdrawabilityDelay + churnLimitQuotient / 2;
  }
}
