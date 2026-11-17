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

package tech.pegasys.teku.cli.slashingprotection;

import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.BooleanSupplier;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.cli.util.SlashingProtectionCommandUtils;
import tech.pegasys.teku.data.SlashingProtectionRepairer;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.networks.StateBoostrapConfig;
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

@CommandLine.Command(
    name = "repair",
    description = "Repair slashing protection data files used by teku.",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class RepairCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Mixin(name = "Network")
  private Eth2NetworkOptions eth2NetworkOptions;

  @CommandLine.Option(
      names = {"--check-only-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      description = "Read and report potential problems, but don't update anything.",
      arity = "0..1")
  private boolean checkOnlyEnabled = false;

  @CommandLine.Option(
      names = {"--update-all-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      description = "All validator slashing protection records should be updated.",
      arity = "0..1")
  private boolean updateAllEnabled = false;

  @CommandLine.Option(
      names = {"--slot"},
      paramLabel = "<INTEGER>",
      description =
          "Update slashing protection files to contain the specified slot as a minimum."
              + "Should be a future slot, or after when the validators stopped performing duties.\n"
              + "NOTE: This can be calculated for most networks, and is generally not required.",
      arity = "1")
  private UInt64 suppliedSlot;

  private final WeakSubjectivityInitializer wsInitializer = new WeakSubjectivityInitializer();

  @Override
  public void run() {
    final Path slashProtectionPath =
        SlashingProtectionCommandUtils.getSlashingProtectionPath(dataOptions);
    SlashingProtectionCommandUtils.verifySlashingProtectionPathExists(
        SUB_COMMAND_LOG, slashProtectionPath);
    final Eth2NetworkConfiguration networkConfiguration =
        eth2NetworkOptions.getNetworkConfiguration();
    final Spec spec = networkConfiguration.getSpec();

    final Optional<AnchorPoint> initialAnchor =
        wsInitializer.loadInitialAnchorPoint(
            spec,
            getStateResourceForComputingSlot(
                Optional.ofNullable(suppliedSlot),
                networkConfiguration.getNetworkBoostrapConfig()));

    final UInt64 computedSlot = getComputedSlot(initialAnchor, spec);
    final UInt64 computedEpoch =
        spec.atSlot(computedSlot).miscHelpers().computeEpochAtSlot(computedSlot);
    final SlashingProtectionRepairer repairer =
        SlashingProtectionRepairer.create(SUB_COMMAND_LOG, slashProtectionPath, updateAllEnabled);

    final Optional<String> networkMismatch =
        initialAnchor.flatMap(
            anchor ->
                findNetworkMismatch(
                    repairer, anchor.getState().getGenesisValidatorsRoot(), getNetworkName()));
    if (initialAnchor.isEmpty()) {
      SUB_COMMAND_LOG.display(
          "NOTE: --slot was supplied, so existing records were not checked against the network.");
    } else if (!repairer.hasValidRecords()) {
      SUB_COMMAND_LOG.display("NOTE: there are no existing records to check against the network.");
    } else if (!repairer.hasRecordsWithGenesisValidatorsRoot()) {
      SUB_COMMAND_LOG.display(
          "NOTE: no existing records contain a genesis validators root, so the network could not be verified.");
    }

    final RepairOutcome outcome =
        repair(
            repairer,
            networkMismatch,
            checkOnlyEnabled,
            computedSlot,
            computedEpoch,
            () -> confirm(computedSlot, computedEpoch),
            SUB_COMMAND_LOG);
    if (outcome == RepairOutcome.NETWORK_MISMATCH || outcome == RepairOutcome.CANCELLED) {
      System.exit(1);
    }
    SUB_COMMAND_LOG.display("done");
  }

  enum RepairOutcome {
    NOTHING_TO_UPDATE,
    NETWORK_MISMATCH,
    CHECK_ONLY,
    CANCELLED,
    UPDATED
  }

  /**
   * Decides what to do with the records and applies the updates.
   *
   * <p>Nothing is written unless records need updating, the network matches, check-only is off and
   * the user confirms.
   */
  static RepairOutcome repair(
      final SlashingProtectionRepairer repairer,
      final Optional<String> networkMismatch,
      final boolean checkOnly,
      final UInt64 slot,
      final UInt64 epoch,
      final BooleanSupplier confirm,
      final SubCommandLogger log) {
    if (networkMismatch.isPresent()) {
      log.error(networkMismatch.get());
      return RepairOutcome.NETWORK_MISMATCH;
    }
    if (!repairer.hasUpdates()) {
      return RepairOutcome.NOTHING_TO_UPDATE;
    }
    if (checkOnly) {
      log.display("Updates have been skipped as --check-only-enabled was set.");
      return RepairOutcome.CHECK_ONLY;
    }
    if (!confirm.getAsBoolean()) {
      log.display("Operation cancelled.");
      return RepairOutcome.CANCELLED;
    }
    repairer.updateRecords(slot, epoch);
    return RepairOutcome.UPDATED;
  }

  /** Only an answer of "yes", in any case, confirms the repair. */
  static boolean isConfirmed(final String answer) {
    return answer.equalsIgnoreCase("yes");
  }

  /**
   * The slot used when none is supplied: the current slot plus one epoch, so validators miss at
   * most one epoch of duties.
   */
  static UInt64 computeRepairSlot(
      final Spec spec, final UInt64 genesisTime, final UInt64 currentTime) {
    final MiscHelpers miscHelpers = spec.atTime(genesisTime, currentTime).miscHelpers();
    final SpecConfig specConfig = spec.atTime(genesisTime, currentTime).getConfig();
    return miscHelpers
        .computeSlotAtTime(genesisTime, currentTime)
        .plus(specConfig.getSlotsPerEpoch());
  }

  /**
   * Picks the state used to compute the current slot. Only the genesis time and genesis validators
   * root are read from it, so the network's genesis state is as good as its initial state.
   *
   * @return empty if a slot was supplied, as no state is needed
   */
  static Optional<String> getStateResourceForComputingSlot(
      final Optional<UInt64> suppliedSlot, final StateBoostrapConfig bootstrapConfig) {
    if (suppliedSlot.isPresent()) {
      return Optional.empty();
    }
    return bootstrapConfig.getInitialState().or(bootstrapConfig::getGenesisState);
  }

  /**
   * Checks that existing records belong to the network being repaired.
   *
   * @return an error message if any record has a different genesis validators root
   */
  static Optional<String> findNetworkMismatch(
      final SlashingProtectionRepairer repairer,
      final Bytes32 genesisValidatorsRoot,
      final String networkName) {
    final List<String> mismatched =
        repairer.getPubkeysWithDifferentGenesisValidatorsRoot(genesisValidatorsRoot);
    if (mismatched.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            "%d slashing protection record(s) were written for a different network than %s"
                + " (genesis validators root %s), for example %s.%n"
                + "Check --network or --config-file. No records were changed.",
            mismatched.size(), networkName, genesisValidatorsRoot, mismatched.getFirst()));
  }

  private String getNetworkName() {
    return eth2NetworkOptions.getNetwork() == null ? "" : eth2NetworkOptions.getNetwork();
  }

  private UInt64 getComputedSlot(final Optional<AnchorPoint> initialAnchor, final Spec spec) {
    if (suppliedSlot != null) {
      displaySlotUpdateMessage(suppliedSlot, spec, "WARNING: using a supplied slot");

      return suppliedSlot;
    } else if (initialAnchor.isPresent()) {
      final UInt64 computedSlot =
          computeRepairSlot(
              spec,
              initialAnchor.get().getState().getGenesisTime(),
              SYSTEM_TIME_PROVIDER.getTimeInSeconds());
      displaySlotUpdateMessage(computedSlot, spec, "Computed " + getNetworkName() + " slot");
      return computedSlot;
    }
    SUB_COMMAND_LOG.exit(
        1,
        "Could not compute the current slot as the network has no initial or genesis state, "
            + "please supply one with --slot.");
    throw new IllegalStateException("Should not have got past System.exit.");
  }

  private void displaySlotUpdateMessage(
      final UInt64 slot, final Spec spec, final String description) {
    SUB_COMMAND_LOG.display(
        description
            + " "
            + slot
            + ", which will set attestation source/target to epoch "
            + spec.atSlot(slot).miscHelpers().computeEpochAtSlot(slot));
  }

  private boolean confirm(final UInt64 slot, final UInt64 epoch) {
    SUB_COMMAND_LOG.display("");
    SUB_COMMAND_LOG.display(
        "REMINDER! If the validator is running, you should not be updating slashing protection records.");
    if (updateAllEnabled) {
      SUB_COMMAND_LOG.display("All valid slashing protection files will also be updated.");
    }
    SUB_COMMAND_LOG.display("block slot -> " + slot);
    SUB_COMMAND_LOG.display("attestation source/target -> " + epoch);
    SUB_COMMAND_LOG.display("Are you sure you wish to continue (yes/no)? ");
    final Scanner scanner = new Scanner(System.in, Charset.defaultCharset());
    return isConfirmed(scanner.next());
  }
}
