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

package tech.pegasys.teku.api;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.builder.DenebBuilder;
import tech.pegasys.teku.spec.config.builder.ElectraBuilder;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.constants.ValidatorConstants;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;

public class SpecConfigData {
  private static final Logger LOG = LogManager.getLogger();
  private final SpecConfig specConfig;

  public SpecConfigData(final SpecConfig specConfig) {
    this.specConfig = specConfig;
  }

  public Map<String, Object> getConfigMap() {
    final Map<String, Object> configAttributes = new HashMap<>();
    specConfig
        .getRawConfig()
        .forEach(
            (name, value) -> {
              if (value instanceof List<?>) {
                LOG.debug("Config field {} is a list", name);
                configAttributes.put(name, value);
              } else if (value != null) {
                configAttributes.put(name, ConfigProvider.formatValue(value));
              } else {
                LOG.warn("Config field {} was set to null in runtime configuration", name);
              }
            });

    configAttributes.put("BLS_WITHDRAWAL_PREFIX", getBlsWithdrawalPrefix().toHexString());
    configAttributes.put(
        "DOMAIN_BLS_TO_EXECUTION_CHANGE", getDomainBlsToExecutionChange().toHexString());
    configAttributes.put("TARGET_AGGREGATORS_PER_COMMITTEE", getTargetAggregatorsPerCommittee());
    configAttributes.put("DOMAIN_BEACON_PROPOSER", getDomainBeaconProposer().toHexString());
    configAttributes.put("DOMAIN_BEACON_ATTESTER", getDomainBeaconAttester().toHexString());
    configAttributes.put("DOMAIN_RANDAO", getDomainRandao().toHexString());
    configAttributes.put("DOMAIN_DEPOSIT", getDomainDeposit().toHexString());
    configAttributes.put("DOMAIN_VOLUNTARY_EXIT", getDomainVoluntaryExit().toHexString());
    configAttributes.put("DOMAIN_SELECTION_PROOF", getDomainSelectionProof().toHexString());
    configAttributes.put("DOMAIN_AGGREGATE_AND_PROOF", getDomainAggregateAndProof().toHexString());
    configAttributes.put("DOMAIN_APPLICATION_BUILDER", getDomainApplicationBuilder().toHexString());
    configAttributes.put("DOMAIN_BEACON_BUILDER", getDomainBeaconBuilder().toHexString());
    configAttributes.put("DOMAIN_PTC_ATTESTER", getDomainPtcAttester().toHexString());
    addDeprecatedFields(configAttributes);

    configAttributes.put(
        "DOMAIN_PROPOSER_PREFERENCES", getDomainProposerPreferences().toHexString());
    configAttributes.put("DOMAIN_BUILDER_DEPOSIT", getDomainBuilderDeposit().toHexString());
    configAttributes.put(
        "DOMAIN_INCLUSION_LIST_COMMITTEE", getDomainInclusionListCommittee().toHexString());
    configAttributes.put("BUILDER_INDEX_FLAG", ConfigProvider.formatValue(getBuilderIndexFlag()));
    configAttributes.put(
        "BUILDER_INDEX_SELF_BUILD", ConfigProvider.formatValue(getBuilderIndexSelfBuild()));
    configAttributes.put(
        "BUILDER_PAYMENT_THRESHOLD_NUMERATOR",
        ConfigProvider.formatValue(getBuilderPaymentThresholdNumerator()));
    configAttributes.put(
        "BUILDER_PAYMENT_THRESHOLD_DENOMINATOR",
        ConfigProvider.formatValue(getBuilderPaymentThresholdDenominator()));
    configAttributes.put("BUILDER_WITHDRAWAL_PREFIX", getBuilderWithdrawalPrefix().toHexString());
    configAttributes.put(
        "PAYLOAD_BUILDER_VERSION", ConfigProvider.formatValue(getPayloadBuilderVersion()));

    getDomainSyncCommittee()
        .ifPresent(
            committee -> configAttributes.put("DOMAIN_SYNC_COMMITTEE", committee.toHexString()));
    getDomainSyncCommitteeSelectionProof()
        .ifPresent(
            proof ->
                configAttributes.put("DOMAIN_SYNC_COMMITTEE_SELECTION_PROOF", proof.toHexString()));
    getDomainContributionAndProof()
        .ifPresent(
            contribution ->
                configAttributes.put("DOMAIN_CONTRIBUTION_AND_PROOF", contribution.toHexString()));
    getTargetAggregatorsPerSyncSubcommittee()
        .ifPresent(
            targetAggregators ->
                configAttributes.put(
                    "TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE", targetAggregators));
    getSyncCommitteeSubnetCount()
        .ifPresent(subnetCount -> configAttributes.put("SYNC_COMMITTEE_SUBNET_COUNT", subnetCount));

    return configAttributes;
  }

  private void addDeprecatedFields(final Map<String, Object> configAttributes) {
    // #10499 - clean post gloas fork
    if (configAttributes.get("SECONDS_PER_SLOT") == null) {
      configAttributes.put(
          "SECONDS_PER_SLOT",
          ConfigProvider.formatValue(specConfig.getSlotDurationMillis() / 1000));
    }
    // #10499 - clean post gloas fork
    if (configAttributes.get("MIN_EPOCHS_FOR_BLOCK_REQUESTS") == null) {
      configAttributes.put(
          "MIN_EPOCHS_FOR_BLOCK_REQUESTS",
          ConfigProvider.formatValue(
              SpecConfigBuilder.computeMinEpochsForBlockRequests(
                  specConfig.getMinValidatorWithdrawabilityDelay(),
                  specConfig.getChurnLimitQuotient())));
    }
    // #10499 - clean post gloas fork
    if (configAttributes.get("ATTESTATION_SUBNET_PREFIX_BITS") == null) {
      configAttributes.put(
          "ATTESTATION_SUBNET_PREFIX_BITS",
          ConfigProvider.formatValue(
              SpecConfigBuilder.computeAttestationSubnetPrefixBits(
                  specConfig.getAttestationSubnetCount(),
                  specConfig.getAttestationSubnetExtraBits())));
    }
    // #10499 - clean post gloas fork
    if (specConfig.getDenebForkEpoch().isLessThan(FAR_FUTURE_EPOCH)) {
      if (configAttributes.get("MAX_REQUEST_BLOB_SIDECARS") == null) {
        final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(specConfig);
        configAttributes.put(
            "MAX_REQUEST_BLOB_SIDECARS",
            ConfigProvider.formatValue(
                DenebBuilder.computeMaxRequestBlobSidecars(
                    specConfigDeneb.getMaxRequestBlocksDeneb(),
                    specConfigDeneb.getDenebMaxBlobsPerBlock())));
      }
    }
    // #10499 - clean post gloas fork
    if (specConfig.getElectraForkEpoch().isLessThan(FAR_FUTURE_EPOCH)) {
      if (configAttributes.get("MAX_REQUEST_BLOB_SIDECARS_ELECTRA") == null) {
        final SpecConfigElectra specConfigElectra = SpecConfigElectra.required(specConfig);
        configAttributes.put(
            "MAX_REQUEST_BLOB_SIDECARS_ELECTRA",
            ConfigProvider.formatValue(
                ElectraBuilder.computeMaxRequestBlobSidecars(
                    specConfigElectra.getMaxRequestBlocksDeneb(),
                    specConfigElectra.getMaxBlobsPerBlock())));
      }
    }
    // #10499 - clean post gloas fork
    if (specConfig.getFuluForkEpoch().isLessThan(FAR_FUTURE_EPOCH)) {
      if (configAttributes.get("MAX_REQUEST_DATA_COLUMN_SIDECARS") == null) {
        final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(specConfig);
        configAttributes.put(
            "MAX_REQUEST_DATA_COLUMN_SIDECARS",
            ConfigProvider.formatValue(specConfigFulu.getMaxRequestDataColumnSidecars()));
      }
    }
  }

  private Bytes getBlsWithdrawalPrefix() {
    return specConfig.getBlsWithdrawalPrefix();
  }

  private String getTargetAggregatorsPerCommittee() {
    return Integer.toString(ValidatorConstants.TARGET_AGGREGATORS_PER_COMMITTEE);
  }

  private Bytes4 getDomainBeaconProposer() {
    return Domain.BEACON_PROPOSER;
  }

  private Bytes4 getDomainBeaconAttester() {
    return Domain.BEACON_ATTESTER;
  }

  private Bytes4 getDomainRandao() {
    return Domain.RANDAO;
  }

  private Bytes4 getDomainDeposit() {
    return Domain.DEPOSIT;
  }

  private Bytes4 getDomainVoluntaryExit() {
    return Domain.VOLUNTARY_EXIT;
  }

  private Bytes4 getDomainSelectionProof() {
    return Domain.SELECTION_PROOF;
  }

  private Bytes4 getDomainAggregateAndProof() {
    return Domain.AGGREGATE_AND_PROOF;
  }

  public Bytes4 getDomainApplicationBuilder() {
    return Domain.APPLICATION_BUILDER;
  }

  public Bytes4 getDomainBlsToExecutionChange() {
    return Domain.BLS_TO_EXECUTION_CHANGE;
  }

  public Bytes4 getDomainBeaconBuilder() {
    return Domain.BEACON_BUILDER;
  }

  public Bytes4 getDomainPtcAttester() {
    return Domain.PTC_ATTESTER;
  }

  public Bytes4 getDomainProposerPreferences() {
    return Domain.PROPOSER_PREFERENCES;
  }

  public Bytes4 getDomainBuilderDeposit() {
    return Domain.BUILDER_DEPOSIT;
  }

  public Bytes4 getDomainInclusionListCommittee() {
    return Domain.INCLUSION_LIST_COMMITTEE;
  }

  private UInt64 getBuilderIndexFlag() {
    return SpecConfigGloas.BUILDER_INDEX_FLAG;
  }

  private UInt64 getBuilderIndexSelfBuild() {
    return SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
  }

  private UInt64 getBuilderPaymentThresholdNumerator() {
    return SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_NUMERATOR;
  }

  private UInt64 getBuilderPaymentThresholdDenominator() {
    return SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_DENOMINATOR;
  }

  private Bytes getBuilderWithdrawalPrefix() {
    return WithdrawalPrefixes.BUILDER_WITHDRAWAL_PREFIX;
  }

  private int getPayloadBuilderVersion() {
    return SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
  }

  private Optional<Bytes4> getDomainSyncCommittee() {
    return getLegacyAltairConstant(Domain.SYNC_COMMITTEE);
  }

  private Optional<Bytes4> getDomainSyncCommitteeSelectionProof() {
    return getLegacyAltairConstant(Domain.SYNC_COMMITTEE_SELECTION_PROOF);
  }

  private Optional<Bytes4> getDomainContributionAndProof() {
    return getLegacyAltairConstant(Domain.CONTRIBUTION_AND_PROOF);
  }

  private Optional<String> getTargetAggregatorsPerSyncSubcommittee() {
    return getLegacyAltairConstant(
        Integer.toString(ValidatorConstants.TARGET_AGGREGATORS_PER_SYNC_SUBCOMMITTEE));
  }

  private Optional<String> getSyncCommitteeSubnetCount() {
    return getLegacyAltairConstant(Integer.toString(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT));
  }

  private <T> Optional<T> getLegacyAltairConstant(final T value) {
    return specConfig.toVersionAltair().isPresent() ? Optional.of(value) : Optional.empty();
  }
}
