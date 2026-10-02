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

package tech.pegasys.teku.infrastructure.logging;

import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatBlock;

import com.google.common.base.Strings;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorLogger {
  private static final int VALIDATOR_KEY_LIMIT = 20;
  public static final ValidatorLogger VALIDATOR_LOGGER =
      new ValidatorLogger(LoggingConfigurator.VALIDATOR_LOGGER_NAME);
  public static final int LONGEST_TYPE_LENGTH = "payload_attestation".length();
  private static final String PREFIX = "Validator   *** ";
  private static final String BUILDER_PREFIX = "Builder     *** ";

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private ValidatorLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void connectedToBeaconNodeEventStream() {
    log.info(
        ColorConsolePrinter.print(
            String.format("%sSuccessfully connected to beacon node event stream", PREFIX),
            Color.GREEN));
  }

  public void noHeadEventsReceivedFromBeaconNodeEventStream(final String beaconNodeEndpoint) {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sNo head events were received from the beacon node event stream before it failed: %s. "
                    + "Duties will be driven by the slot timer alone",
                PREFIX, beaconNodeEndpoint),
            Color.YELLOW));
  }

  public void beaconNodeEventStreamConnectionError() {
    log.debug(
        ColorConsolePrinter.print(
            String.format("%sError while connecting to beacon node event stream", PREFIX),
            Color.GRAY));
  }

  public void switchingToFailoverBeaconNodeForEventStreaming(final URI eventStreamEndpoint) {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sSwitching to failover beacon node for event streaming: %s",
                PREFIX, eventStreamEndpoint),
            Color.GREEN));
  }

  public void switchingBackToPrimaryBeaconNodeForEventStreaming() {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sSwitching back to the primary beacon node for event streaming", PREFIX),
            Color.GREEN));
  }

  public void noFailoverBeaconNodesAvailableForEventStreaming() {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sThere are no beacon nodes from the configured ones that are ready to be used as an event stream failover",
                PREFIX),
            Color.YELLOW));
  }

  public void primaryBeaconNodeNotReady() {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sThe primary beacon node is NOT ready to accept requests. Future requests will use one of the configured failover beacon nodes until the primary one is ready again.",
                PREFIX),
            Color.YELLOW));
  }

  public void primaryBeaconNodeIsBackAndReady() {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sThe primary beacon node is back and ready to accept requests now", PREFIX),
            Color.GREEN));
  }

  public void dutyCompleted(
      final String producedType,
      final UInt64 slot,
      final int successCount,
      final Set<Bytes32> blockRoots,
      final Optional<String> context) {
    final String paddedType = Strings.padEnd(producedType, LONGEST_TYPE_LENGTH, ' ');
    logDuty(paddedType, slot, successCount, blockRoots, context);
  }

  public void dutySkippedWhileSyncing(
      final String producedType, final UInt64 slot, final int skippedCount) {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sSkipped producing %s while node is syncing  Count: %s, Slot: %s",
                PREFIX, producedType, skippedCount, slot),
            Color.YELLOW));
  }

  public void dutyFailed(
      final String producedType,
      final UInt64 slot,
      final Set<String> maybeKey,
      final Throwable error) {
    final String errorString =
        String.format(
            "%sFailed to produce %s  Slot: %s Validator: %s",
            PREFIX, producedType, slot, formatValidators(maybeKey));
    final String consoleMessage = ColorConsolePrinter.print(errorString, Color.RED);
    if (log.isTraceEnabled() || producedType.equals("block")) {
      log.error(consoleMessage, error);
    } else {
      log.error(consoleMessage);
    }
  }

  public void signerNoLongerActive(
      final String producedType, final UInt64 slot, final Set<String> maybeKey) {
    final String errorString =
        String.format(
            "%sValidator removed, skipping previously scheduled %s production. Slot: %s Validator: %s",
            PREFIX, producedType, slot, formatValidators(maybeKey));
    log.info(ColorConsolePrinter.print(errorString, Color.YELLOW));
  }

  private String formatValidators(final Set<String> keys) {
    if (keys.isEmpty()) {
      return "";
    }
    final String suffix = keys.size() > VALIDATOR_KEY_LIMIT ? "… (" + keys.size() + " total)" : "";
    return keys.stream().limit(VALIDATOR_KEY_LIMIT).collect(Collectors.joining(", ", "", suffix));
  }

  private void logDuty(
      final String type,
      final UInt64 slot,
      final int count,
      final Set<Bytes32> roots,
      final Optional<String> context) {
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sPublished %s  Count: %s, Slot: %s, Root: %s%s",
                PREFIX,
                type,
                count,
                slot,
                formatBlockRoots(roots),
                context.map(s -> ", " + s).orElse("")),
            type.startsWith("block") ? Color.CYAN : Color.BLUE));
  }

  private String formatBlockRoots(final Set<Bytes32> blockRoots) {
    return blockRoots.stream().map(LogFormatter::formatHashRoot).collect(Collectors.joining(", "));
  }

  public void aggregationSkipped(final UInt64 slot, final UInt64 committeeIndex) {
    log.warn(
        ColorConsolePrinter.print(
            PREFIX
                + "Skipped aggregation for committee "
                + committeeIndex
                + " at slot "
                + slot
                + " because there was nothing to aggregate",
            Color.YELLOW));
  }

  public void syncCommitteeAggregationSkipped(final UInt64 slot) {
    log.warn(
        ColorConsolePrinter.print(
            PREFIX
                + "Skipped aggregation at slot "
                + slot
                + " because there was nothing to aggregate",
            Color.YELLOW));
  }

  public void producedInvalidAttestation(final UInt64 slot, final String reason) {
    log.error(
        ColorConsolePrinter.print(
            PREFIX
                + "Produced invalid attestation for slot "
                + slot
                + ". Invalid reason: "
                + reason,
            Color.RED));
  }

  public void producedInvalidAggregate(final UInt64 slot, final String reason) {
    log.error(
        ColorConsolePrinter.print(
            PREFIX + "Produced invalid aggregate for slot " + slot + ": " + reason, Color.RED));
  }

  public void preparedBeaconProposersExpiration(
      final UInt64 slot, final int numberOfExpiredProposers) {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sInformation about %s prepared beacon proposer(s) expired at slot %s",
                PREFIX, numberOfExpiredProposers, slot),
            Color.YELLOW));
  }

  public void validatorRegistrationsExpiration(
      final UInt64 slot, final int numberOfExpiredRegistrations) {
    log.warn(
        ColorConsolePrinter.print(
            String.format(
                "%sInformation about %s validator registration(s) expired at slot %s",
                PREFIX, numberOfExpiredRegistrations, slot),
            Color.YELLOW));
  }

  public void beaconProposerPreparationFailed(final Throwable error) {
    final String errorString = String.format("%sFailed to send proposers to Beacon Node", PREFIX);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void proposerPreferencesPublicationFailed(final UInt64 epoch, final Throwable error) {
    final String errorString =
        String.format("%sFailed to publish proposer preferences for epoch %s", PREFIX, epoch);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void builderPreferencesPublicationFailed(final UInt64 epoch, final Throwable error) {
    final String errorString =
        String.format("%sFailed to publish builder preferences for epoch %s", PREFIX, epoch);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void validatorRegistrationsSentToTheBuilderNetwork(
      final int successfullySentRegistrations, final int totalRegistrations) {
    final String infoString =
        String.format(
            "%s%s out of %s validator registration(s) were successfully sent to the builder network via the Beacon Node.",
            PREFIX, successfullySentRegistrations, totalRegistrations);
    log.info(ColorConsolePrinter.print(infoString, Color.GREEN));
  }

  public void registeringValidatorsFailed(final Throwable error) {
    final String errorString =
        String.format(
            "%sFailed to send validator registrations to the builder network via the Beacon Node",
            PREFIX);
    log.error(ColorConsolePrinter.print(errorString, Color.RED), error);
  }

  public void executionPayloadPreparedUsingBeaconDefaultFeeRecipient(final UInt64 slot) {
    log.warn(
        ColorConsolePrinter.print(
            "Beacon Node has been requested to produce a block for slot "
                + slot
                + " but proposer hasn't been prepared by any Validator Client. "
                + "Using Beacon Node's default fee recipient.",
            Color.YELLOW));
  }

  public void executionPayloadPreparedUsingBurnAddressForFeeRecipient(final UInt64 slot) {
    log.error(
        ColorConsolePrinter.print(
            "Producing block at slot "
                + slot
                + ", no proposer was prepared, and no default fee recipient defined, inclusion fees will be lost",
            Color.RED));
  }

  public void proposedBlockImportFailed(
      final String failureReason,
      final UInt64 slot,
      final Bytes32 root,
      final Optional<Throwable> error) {
    log.error(
        ColorConsolePrinter.print(
            "Failed to import proposed block due to "
                + failureReason
                + ": "
                + formatBlock(slot, root),
            Color.RED),
        error.orElse(null));
  }

  public void loadedSlashingProtection(final Set<String> maybeKey) {
    final String infoString =
        String.format(
            "%sSlashing protection loaded for validators: %s", PREFIX, formatValidators(maybeKey));
    log.info(ColorConsolePrinter.print(infoString, Color.GREEN));
  }

  public void notLoadedSlashingProtection(final Set<String> maybeKey) {
    final String infoString =
        String.format(
            "%sSlashing protection not loaded for validators: %s",
            PREFIX, formatValidators(maybeKey));
    log.warn(ColorConsolePrinter.print(infoString, Color.YELLOW));
  }

  public void outdatedSlashingProtection(final Set<String> maybeKey, final UInt64 deltaEpochs) {
    final String infoString =
        String.format(
            "%sSlashing protection last updated more than %s epochs ago for validators: %s",
            PREFIX, deltaEpochs, formatValidators(maybeKey));
    log.warn(ColorConsolePrinter.print(infoString, Color.YELLOW));
  }

  public void logExecutionPayloadDuty(
      final UInt64 slot, final UInt64 builderIndex, final Bytes32 blockRoot, final String suffix) {
    final String paddedType = Strings.padEnd("execution_payload", LONGEST_TYPE_LENGTH, ' ');
    log.info(
        ColorConsolePrinter.print(
            String.format(
                "%sPublished %s  Slot: %s, Builder: %s, Block Root: %s, %s",
                BUILDER_PREFIX,
                paddedType,
                slot,
                builderIndex,
                LogFormatter.formatHashRoot(blockRoot),
                suffix),
            ColorConsolePrinter.Color.PURPLE));
  }

  public void executionPayloadDutyFailed(
      final UInt64 slot, final UInt64 builderIndex, final Throwable error) {
    final String errorString =
        String.format(
            "%sFailed to produce execution_payload  Slot: %s Builder: %s",
            BUILDER_PREFIX, slot, builderIndex);
    log.error(ColorConsolePrinter.print(errorString, ColorConsolePrinter.Color.RED), error);
  }
}
