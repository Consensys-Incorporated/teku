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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_ASYNC_P2P_MAX_THREADS;
import static tech.pegasys.teku.spec.constants.NetworkConstants.DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.Bytes32Converter;
import tech.pegasys.teku.cli.converter.OptionalIntConverter;
import tech.pegasys.teku.cli.converter.OptionalLongConverter;
import tech.pegasys.teku.cli.converter.UInt256Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;

public class Eth2NetworkOptions {

  @Option(
      names = {"-n", "--network"},
      paramLabel = "<NETWORK>",
      description = "Represents which network to use.",
      arity = "1")
  private String network = "mainnet";

  @Option(
      names = {"--initial-state"},
      paramLabel = "<STRING>",
      description =
          "The initial state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint "
              + "state.",
      arity = "1")
  private String initialState;

  @Option(
      names = {"--ignore-weak-subjectivity-period-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Allows syncing outside of the weak subjectivity period.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS)
  private boolean ignoreWeakSubjectivityPeriodEnabled = false;

  @Option(
      names = {"--genesis-state"},
      paramLabel = "<STRING>",
      description =
          "The genesis state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint "
              + "state.",
      arity = "1")
  private String genesisState;

  @Option(
      names = {"--checkpoint-sync-url"},
      paramLabel = "<STRING>",
      description = "The Checkpointz server that will be used to bootstrap this node.",
      arity = "1")
  private String checkpointSyncUrl;

  @Option(
      names = {"--eth1-deposit-contract-address"},
      paramLabel = "<ADDRESS>",
      description =
          "Contract address for the deposit contract. Only required when creating a custom network.",
      arity = "1")
  private String eth1DepositContractAddress = null; // Depends on network configuration

  @Option(
      names = {"--Xtrusted-setup"},
      hidden = true,
      paramLabel = "<STRING>",
      description =
          "The trusted setup which is needed for KZG commitments. Only required when creating a custom network. This "
              + "value should be a file or URL pointing to a trusted setup.",
      arity = "1")
  private String trustedSetup = null; // Depends on network configuration

  @Option(
      names = {"--Xrust-kzg-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "Use Rust KZG library LibPeerDASKZG with fallback to CKZG4844 for EIP-4844 methods",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean rustKzgEnabled = Eth2NetworkConfiguration.DEFAULT_RUST_KZG_ENABLED;

  @Option(
      names = {"--Xkzg-precompute"},
      paramLabel = "<INT>",
      description =
          "Configure KZG precompute value for PeerDAS performance optimization. Valid values range from 0 to 15. "
              + "Higher values improve performance but use more memory. See the following for more information: "
              + "https://github.com/ethereum/c-kzg-4844/blob/main/README.md#precompute",
      arity = "1",
      converter = OptionalIntConverter.class,
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private OptionalInt kzgPrecompute = OptionalInt.empty();

  @Option(
      names = {"--Xdata-column-sidecar-recovery-max-delay"},
      paramLabel = "<MILLISECONDS>",
      description =
          "Maximum delay in milliseconds for a supernode to begin data column sidecar recovery.",
      arity = "1",
      converter = OptionalLongConverter.class,
      hidden = true)
  private OptionalLong dataColumnSidecarRecoveryMaxDelayMillis = OptionalLong.empty();

  @Option(
      names = {"--Xlight-client-server-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Generate and serve light client updates",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean lightClientServerEnabled =
      Eth2NetworkConfiguration.DEFAULT_LIGHT_CLIENT_SERVER_ENABLED;

  @Option(
      names = {"--Xfork-choice-late-block-reorg-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Allow late blocks to be reorged out if they meet the requirements.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean forkChoiceLateBlockReorgEnabled =
      Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

  @Option(
      names = {"--Xfast-confirmation-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable the experimental fast confirmation rule.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean fastConfirmationEnabled =
      Eth2NetworkConfiguration.DEFAULT_FAST_CONFIRMATION_ENABLED;

  @Option(
      names = {"--Xprepare-block-production-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable block production to be prepared in advance.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean prepareBlockProductionEnabled =
      Eth2NetworkConfiguration.DEFAULT_PREPARE_BLOCK_PRODUCTION_ENABLED;

  @Option(
      names = {"--Xfork-choice-updated-always-send-payload-attributes"},
      paramLabel = "<BOOLEAN>",
      description =
          "Calculate and send payload attributes on every forkChoiceUpdated regardless if a connected validator is "
              + "due to be a block proposer or not.",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean forkChoiceUpdatedAlwaysSendPayloadAttributes =
      Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_UPDATED_ALWAYS_SEND_PAYLOAD_ATTRIBUTES;

  @Option(
      names = {"--Xfork-choice-attestation-wait-limit"},
      paramLabel = "<MILLISECONDS>",
      description = "If fork choice isn't complete when attestations are due (Defaults to 1500ms).",
      arity = "1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private int attestationWaitlimitMillis =
      Eth2NetworkConfiguration.DEFAULT_ATTESTATION_WAIT_TIMEOUT_MILLIS;

  @Option(
      names = {"--Xnetwork-altair-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Altair fork activation epoch.",
      arity = "1")
  private UInt64 altairForkEpoch;

  @Option(
      names = {"--Xnetwork-bellatrix-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Bellatrix fork activation epoch.",
      arity = "1")
  private UInt64 bellatrixForkEpoch;

  @Option(
      names = {"--Xnetwork-capella-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the capella fork activation epoch.",
      arity = "1")
  private UInt64 capellaForkEpoch;

  @Option(
      names = {"--Xnetwork-deneb-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the deneb fork activation epoch.",
      arity = "1")
  private UInt64 denebForkEpoch;

  @Option(
      names = {"--Xnetwork-electra-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the electra fork activation epoch.",
      arity = "1")
  private UInt64 electraForkEpoch;

  @Option(
      names = {"--Xnetwork-fulu-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Fulu fork activation epoch.",
      arity = "1")
  private UInt64 fuluForkEpoch;

  @Option(
      names = {"--Xnetwork-gloas-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Gloas fork activation epoch.",
      arity = "1")
  private UInt64 gloasForkEpoch;

  @Option(
      names = {"--Xnetwork-heze-fork-epoch"},
      hidden = true,
      paramLabel = "<epoch>",
      description = "Override the Heze fork activation epoch.",
      arity = "1")
  private UInt64 hezeForkEpoch;

  @Option(
      names = {"--Xnetwork-total-terminal-difficulty-override"},
      hidden = true,
      paramLabel = "<uint256>",
      description = "Override total terminal difficulty for The Merge",
      arity = "1",
      converter = UInt256Converter.class)
  private UInt256 totalTerminalDifficultyOverride;

  @Option(
      names = {"--Xnetwork-terminal-block-hash-override"},
      hidden = true,
      paramLabel = "<Bytes32 hex>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with "
              + "--Xnetwork-bellatrix-terminal-block-hash-epoch-override",
      arity = "1",
      converter = Bytes32Converter.class)
  private Bytes32 terminalBlockHashOverride;

  @Option(
      names = {"--Xnetwork-terminal-block-hash-epoch-override"},
      hidden = true,
      paramLabel = "<epoch>",
      description =
          "Override terminal block hash for The Merge. To be used in conjunction with "
              + "--Xnetwork-bellatrix-terminal-block-hash-override",
      arity = "1")
  private UInt64 terminalBlockHashEpochOverride;

  @Option(
      names = {"--Xnetwork-safe-slots-to-import-optimistically"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description =
          "Override the the number of slots that must pass before it is considered safe to optimistically import a "
              + "block",
      arity = "1")
  private Integer safeSlotsToImportOptimistically = DEFAULT_SAFE_SLOTS_TO_IMPORT_OPTIMISTICALLY;

  @Option(
      names = {"--Xnetwork-async-p2p-max-threads"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the number of threads available to the p2p async runner",
      arity = "1")
  private Integer asyncP2pMaxThreads = DEFAULT_ASYNC_P2P_MAX_THREADS;

  @Option(
      names = {"--Xnetwork-async-p2p-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size of the p2p async runner",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt asyncP2pMaxQueue = OptionalInt.empty();

  @Option(
      names = {"--Xnetwork-pending-attestations-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size for pending attestations",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt pendingAttestationsMaxQueue = OptionalInt.empty();

  @Option(
      names = {"--Xnetwork-async-beaconchain-max-threads"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the number of threads available to the beaconchain async runner",
      arity = "1")
  private Integer asyncBeaconChainMaxThreads = DEFAULT_ASYNC_BEACON_CHAIN_MAX_THREADS;

  @Option(
      names = {"--Xnetwork-async-beaconchain-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size of the beaconchain queue",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt asyncBeaconChainMaxQueue = OptionalInt.empty();

  @Option(
      names = {"--Xstartup-target-peer-count"},
      paramLabel = "<NUMBER>",
      description = "Number of peers to wait for before considering the node in sync.",
      hidden = true)
  private Integer startupTargetPeerCount;

  @Option(
      names = {"--Xstartup-timeout-seconds"},
      paramLabel = "<NUMBER>",
      description =
          "Timeout in seconds to allow the node to be in sync even if startup target peer count has not yet been "
              + "reached.",
      hidden = true)
  private Integer startupTimeoutSeconds;

  @Option(
      names = {"--Xeth1-deposit-contract-deploy-block-override"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override deposit contract block number.",
      arity = "1")
  private Long eth1DepositContractDeployBlockOverride;

  @Option(
      names = {"--Xstartup-strict-config-loader-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Strict config loading will fail if a required parameter is not present in a passed in file, otherwise defaults will be used.",
      arity = "0..1",
      hidden = true,
      fallbackValue = "true")
  private boolean strictConfigLoadingEnabled = NetworkConfig.DEFAULT_STRICT_CONFIG_LOADING_ENABLED;

  @CommandLine.Option(
      names = {"--Xepochs-store-blobs"},
      hidden = true,
      paramLabel = "<STRING>",
      description =
          "Sets the number of epochs blob sidecars are stored and requested during the sync. Use MAX to store all "
              + "blob sidecars. The value cannot be set to be lower than the spec's "
              + "MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS.",
      fallbackValue = "",
      showDefaultValue = Visibility.ALWAYS,
      arity = "0..1")
  private String epochsStoreBlobs;

  @Option(
      names = {"--Xaggregating-attestation-pool-profiling-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable the profiler for the aggregating attestation pool",
      arity = "0..1",
      fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private boolean aggregatingAttestationPoolProfilingEnabled =
      Eth2NetworkConfiguration.DEFAULT_AGGREGATING_ATTESTATION_POOL_PROFILING_ENABLED;

  @Option(
      names = {"--Xaggregating-attestation-pool-v2-block-aggregation-time-limit"},
      paramLabel = "<NUMBER>",
      description = "Maximum time to spend packing attestations when producing a block.",
      arity = "1",
      hidden = true)
  private int aggregatingAttestationPoolV2BlockAggregationTimeLimit =
      Eth2NetworkConfiguration
          .DEFAULT_AGGREGATING_ATTESTATION_POOL_V2_BLOCK_AGGREGATION_TIME_LIMIT_MILLIS;

  @Option(
      names = {"--Xaggregating-attestation-pool-v2-total-block-aggregation-time-limit"},
      paramLabel = "<NUMBER>",
      description =
          "Maximum time to spend packing and improving attestations when producing a block.",
      arity = "1",
      hidden = true)
  private int aggregatingAttestationPoolV2TotalBlockAggregationTimeLimit =
      Eth2NetworkConfiguration
          .DEFAULT_AGGREGATING_ATTESTATION_POOL_V2_TOTAL_BLOCK_AGGREGATION_TIME_LIMIT_MILLIS;

  @Option(
      names = {"--Xdata-column-sidecar-extension-retention-epochs"},
      paramLabel = "<NUMBER>",
      description =
          "Configures period, when all sidecars are kept on supernode. When it's over, half of the older "
              + "finalized sidecars outside the boundary of this period will be pruned, decreasing "
              + "occupied storage, but slowing down ReqResp responses. It will not affect Beacon-API speed.",
      arity = "1",
      hidden = true)
  private int dataColumnSidecarExtensionRetentionEpochs =
      Eth2NetworkConfiguration.DEFAULT_DATA_COLUMN_SIDECAR_EXTENSION_RETENTION_EPOCHS;

  @Option(
      names = {"--Xnetwork-pending-payload-attestations-max-queue"},
      hidden = true,
      paramLabel = "<NUMBER>",
      description = "Override the queue size for pending payload attestations",
      converter = OptionalIntConverter.class,
      arity = "1")
  private OptionalInt pendingPayloadAttestationsMaxQueue = OptionalInt.empty();

  public Eth2NetworkConfiguration getNetworkConfiguration() {
    return createEth2NetworkConfig(builder -> {});
  }

  public void configure(final TekuConfiguration.Builder builder) {
    builder.eth2NetworkConfig(this::configureEth2Network);
  }

  private Eth2NetworkConfiguration createEth2NetworkConfig(
      final Consumer<Eth2NetworkConfiguration.Builder> modifier) {
    Eth2NetworkConfiguration.Builder builder = Eth2NetworkConfiguration.builder();
    configureEth2Network(builder);
    modifier.accept(builder);
    return builder.build();
  }

  private void configureEth2Network(final Eth2NetworkConfiguration.Builder builder) {
    if (network.equals("goerli")) {
      throw new InvalidConfigurationException(
          "Goerli support has been removed. Please choose another network (see https://docs.teku.consensys"
              + ".io/get-started/connect).");
    }

    if (initialState != null && checkpointSyncUrl != null) {
      throw new InvalidConfigurationException(
          "Both --initial-state and --checkpoint-sync-url are provided. Please specify only one.");
    }

    builder.applyNetworkDefaults(network);
    builder.strictConfigLoadingEnabled(strictConfigLoadingEnabled);
    if (startupTargetPeerCount != null) {
      builder.startupTargetPeerCount(startupTargetPeerCount);
    }
    if (startupTimeoutSeconds != null) {
      builder.startupTimeoutSeconds(startupTimeoutSeconds);
    }
    if (eth1DepositContractAddress != null) {
      builder.eth1DepositContractAddress(eth1DepositContractAddress);
    }
    if (StringUtils.isNotBlank(checkpointSyncUrl)) {
      builder.checkpointSyncUrl(checkpointSyncUrl);
    }
    if (StringUtils.isNotBlank(initialState)) {
      builder.customInitialState(initialState);
    }
    if (StringUtils.isNotBlank(genesisState)) {
      builder.customGenesisState(genesisState);
    }
    if (altairForkEpoch != null) {
      builder.altairForkEpoch(altairForkEpoch);
    }
    if (bellatrixForkEpoch != null) {
      builder.bellatrixForkEpoch(bellatrixForkEpoch);
      if (bellatrixForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
      }
    }
    if (capellaForkEpoch != null) {
      builder.capellaForkEpoch(capellaForkEpoch);
      if (capellaForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
      }
    }
    if (denebForkEpoch != null) {
      builder.denebForkEpoch(denebForkEpoch);
      if (denebForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
        implicitEpochDefault(capellaForkEpoch, builder::capellaForkEpoch);
      }
    }
    if (electraForkEpoch != null) {
      builder.electraForkEpoch(electraForkEpoch);
      if (electraForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
        implicitEpochDefault(capellaForkEpoch, builder::capellaForkEpoch);
        implicitEpochDefault(denebForkEpoch, builder::denebForkEpoch);
      }
    }
    if (fuluForkEpoch != null) {
      builder.fuluForkEpoch(fuluForkEpoch);
      if (fuluForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
        implicitEpochDefault(capellaForkEpoch, builder::capellaForkEpoch);
        implicitEpochDefault(denebForkEpoch, builder::denebForkEpoch);
        implicitEpochDefault(electraForkEpoch, builder::electraForkEpoch);
      }
    }
    if (gloasForkEpoch != null) {
      builder.gloasForkEpoch(gloasForkEpoch);
      if (gloasForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
        implicitEpochDefault(capellaForkEpoch, builder::capellaForkEpoch);
        implicitEpochDefault(denebForkEpoch, builder::denebForkEpoch);
        implicitEpochDefault(electraForkEpoch, builder::electraForkEpoch);
        implicitEpochDefault(fuluForkEpoch, builder::fuluForkEpoch);
      }
    }
    if (hezeForkEpoch != null) {
      builder.hezeForkEpoch(hezeForkEpoch);
      if (hezeForkEpoch.isZero()) {
        implicitEpochDefault(altairForkEpoch, builder::altairForkEpoch);
        implicitEpochDefault(bellatrixForkEpoch, builder::bellatrixForkEpoch);
        implicitEpochDefault(capellaForkEpoch, builder::capellaForkEpoch);
        implicitEpochDefault(denebForkEpoch, builder::denebForkEpoch);
        implicitEpochDefault(electraForkEpoch, builder::electraForkEpoch);
        implicitEpochDefault(fuluForkEpoch, builder::fuluForkEpoch);
        implicitEpochDefault(gloasForkEpoch, builder::gloasForkEpoch);
      }
    }
    if (totalTerminalDifficultyOverride != null) {
      builder.totalTerminalDifficultyOverride(totalTerminalDifficultyOverride);
    }
    if (terminalBlockHashOverride != null) {
      builder.terminalBlockHashOverride(terminalBlockHashOverride);
    }
    if (terminalBlockHashEpochOverride != null) {
      builder.terminalBlockHashEpochOverride(terminalBlockHashEpochOverride);
    }
    if (trustedSetup != null) {
      builder.trustedSetup(trustedSetup);
    }
    if (eth1DepositContractDeployBlockOverride != null) {
      builder.eth1DepositContractDeployBlock(eth1DepositContractDeployBlockOverride);
    }
    builder
        .ignoreWeakSubjectivityPeriodEnabled(ignoreWeakSubjectivityPeriodEnabled)
        .safeSlotsToImportOptimistically(safeSlotsToImportOptimistically)
        .asyncP2pMaxThreads(asyncP2pMaxThreads)
        .asyncBeaconChainMaxThreads(asyncBeaconChainMaxThreads)
        .forkChoiceLateBlockReorgEnabled(forkChoiceLateBlockReorgEnabled)
        .lightClientServerEnabled(lightClientServerEnabled)
        .fastConfirmationEnabled(fastConfirmationEnabled)
        .prepareBlockProductionEnabled(prepareBlockProductionEnabled)
        .aggregatingAttestationPoolProfilingEnabled(aggregatingAttestationPoolProfilingEnabled)
        .aggregatingAttestationPoolV2BlockAggregationTimeLimit(
            aggregatingAttestationPoolV2BlockAggregationTimeLimit)
        .aggregatingAttestationPoolV2TotalBlockAggregationTimeLimit(
            aggregatingAttestationPoolV2TotalBlockAggregationTimeLimit)
        .epochsStoreBlobs(epochsStoreBlobs)
        .attestationWaitLimitMillis(attestationWaitlimitMillis)
        .forkChoiceUpdatedAlwaysSendPayloadAttributes(forkChoiceUpdatedAlwaysSendPayloadAttributes)
        .rustKzgEnabled(rustKzgEnabled)
        .dataColumnSidecarExtensionRetentionEpochs(dataColumnSidecarExtensionRetentionEpochs);
    kzgPrecompute.ifPresent(builder::kzgPrecompute);
    dataColumnSidecarRecoveryMaxDelayMillis.ifPresent(
        builder::dataColumnSidecarRecoveryMaxDelayMillis);
    asyncP2pMaxQueue.ifPresent(builder::asyncP2pMaxQueue);
    pendingAttestationsMaxQueue.ifPresent(builder::pendingAttestationsMaxQueue);
    asyncBeaconChainMaxQueue.ifPresent(builder::asyncBeaconChainMaxQueue);
    pendingPayloadAttestationsMaxQueue.ifPresent(builder::pendingPayloadAttestationsMaxQueue);
  }

  private void implicitEpochDefault(
      final UInt64 forkEpoch, final Consumer<UInt64> forkDefaultConsumer) {
    if (forkEpoch == null) {
      forkDefaultConsumer.accept(ZERO);
    }
  }

  public String getNetwork() {
    return network;
  }
}
