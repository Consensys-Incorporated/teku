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

import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class EventLogger {

  public static final EventLogger EVENT_LOG =
      new EventLogger(LoggingConfigurator.EVENT_LOGGER_NAME);

  private static final String EXECUTION_CLIENT_READINESS_USER_REMINDER =
      "Make sure the Execution Client is online and can respond to requests.";

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private EventLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void genesisEvent(
      final Bytes32 hashTreeRoot, final Bytes32 genesisBlockRoot, final UInt64 genesisTime) {
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    final String formattedGenesisTime =
        Instant.ofEpochSecond(genesisTime.longValue()).atZone(ZoneId.of("GMT")).format(formatter);

    final String genesisEventLog =
        String.format(
            """
                Genesis Event ***
                Genesis state root: %s
                Genesis block root: %s
                Genesis time: %s GMT
                """,
            hashTreeRoot.toHexString(), genesisBlockRoot.toHexString(), formattedGenesisTime);
    info(genesisEventLog, Color.CYAN);
  }

  public void epochEvent(
      final UInt64 currentEpoch,
      final UInt64 justifiedCheckpoint,
      final UInt64 finalizedCheckpoint,
      final Bytes32 finalizedRoot) {
    final String epochEventLog =
        String.format(
            "Epoch Event *** Epoch: %s, Justified checkpoint: %s, Finalized checkpoint: %s, Finalized root: %s",
            currentEpoch.toString(),
            justifiedCheckpoint.toString(),
            finalizedCheckpoint.toString(),
            LogFormatter.formatHashRoot(finalizedRoot));
    info(epochEventLog, Color.GREEN);
  }

  public void nodeSlotsMissed(final UInt64 oldSlot, final UInt64 newSlot) {
    final String driftEventLog =
        String.format(
            "Miss slots  *** Current slot: %s, previous slot: %s",
            newSlot.toString(), oldSlot.toString());
    info(driftEventLog, Color.WHITE);
  }

  public void syncEvent(
      final UInt64 nodeSlot,
      final UInt64 headSlot,
      final int numPeers,
      final Optional<TargetChain> maybeTargetChain) {
    final String syncEventLog =
        String.format(
            "Syncing     *** Slot: %s, Head slot: %s, Remaining slots: %s%s, Connected peers: %s",
            nodeSlot,
            headSlot,
            nodeSlot.minusMinZero(headSlot),
            maybeTargetChain
                .map(
                    targetChain ->
                        String.format(
                            ", Target chain: %s (%s) with %s peers",
                            LogFormatter.formatAbbreviatedHashRoot(targetChain.blockRoot),
                            targetChain.slot,
                            targetChain.numPeers))
                .orElse(""),
            numPeers);
    info(syncEventLog, Color.WHITE);
  }

  public record TargetChain(Bytes32 blockRoot, UInt64 slot, int numPeers) {}

  public void syncEventAwaitingEL(
      final UInt64 nodeSlot, final UInt64 headSlot, final int numPeers) {
    final String syncEventLog =
        String.format(
            "Syncing     *** Slot: %s, Head slot: %s, Waiting for execution layer sync, Connected peers: %s",
            nodeSlot, headSlot, numPeers);
    info(syncEventLog, Color.WHITE);
  }

  public void syncProgressEvent(
      final UInt64 fromSlot,
      final UInt64 toSlot,
      final int batches,
      final int downloadingSlots,
      final int downloadingBatches,
      final int readySlots,
      final int readyBatches,
      final boolean importing) {
    final String syncProgressEventLog =
        String.format(
            "Sync Info   *** Range: %s, Downloading: %s, Ready: %s, Batch import: %s",
            batches == 0
                ? "none"
                : String.format("%s - %s (%d batches)", fromSlot, toSlot, batches),
            downloadingSlots == 0
                ? "none"
                : String.format("%d slots (%d batches)", downloadingSlots, downloadingBatches),
            readySlots == 0
                ? "none"
                : String.format("%d slots (%d batches)", readySlots, readyBatches),
            importing ? "in progress" : "waiting");
    info(syncProgressEventLog, Color.GRAY);
  }

  public void syncCompleted() {
    info("Syncing completed", Color.GREEN);
  }

  public void notInSyncWithoutPeers() {
    warn(
        "No connected peers, so the chain head cannot be trusted to be current. Remaining in syncing state until a peer connects",
        Color.YELLOW);
  }

  public void syncStoppedWhileBehindHead(final long slotsBehind) {
    warn(
        String.format(
            "Syncing stopped while the chain head is still %d slots behind. Remaining in syncing state until a sync can make progress",
            slotsBehind),
        Color.YELLOW);
  }

  public void headNoLongerOptimisticWhileSyncing() {
    info("Execution Client syncing complete. Continuing to sync beacon chain blocks", Color.YELLOW);
  }

  public void headTurnedOptimisticWhileSyncing() {
    info(
        "Execution Client syncing in progress, proceeding with optimistic sync of beacon chain",
        Color.YELLOW);
  }

  public void headTurnedOptimisticWhileInSync() {
    warn(
        "Unable to execute the current chain head block payload because the Execution Client is syncing. Activating optimistic sync of the beacon chain node",
        Color.YELLOW);
  }

  public void eth1DepositDataNotAvailable(final UInt64 fromIndex, final UInt64 toIndex) {
    final String eth1DepositDataNotAvailableEventLog =
        String.format(
            "Some ETH1 deposits are not available. Missing deposits %s to %s", fromIndex, toIndex);
    warn(eth1DepositDataNotAvailableEventLog, Color.YELLOW);
  }

  public void syncCompletedWhileHeadIsOptimistic() {
    info("Beacon chain syncing complete, waiting for Execution Client", Color.YELLOW);
  }

  public void executionClientIsOnline() {
    info("Execution Client is online", Color.GREEN);
  }

  public void executionClientRequestFailed(final Throwable error, final boolean couldBeAuthError) {
    error(
        "Execution Client request failed. "
            + (couldBeAuthError
                ? "Check the same JWT secret is configured for Teku and the Execution Client."
                : EXECUTION_CLIENT_READINESS_USER_REMINDER),
        Color.RED,
        error);
  }

  public void executionClientConnectFailure() {
    error(
        "Execution Client request failed. " + EXECUTION_CLIENT_READINESS_USER_REMINDER, Color.RED);
  }

  public void executionClientRequestTimedOut() {
    warn(
        "Execution Client request timed out. " + EXECUTION_CLIENT_READINESS_USER_REMINDER,
        Color.YELLOW);
  }

  public void executionClientRecovered() {
    info("Execution Client is responding to requests again after a previous failure", Color.GREEN);
  }

  public void missingEngineApiCapabilities(final List<String> missingCapabilities) {
    warn(
        String.format(
            "Execution Client does not support required Engine API methods: %s. Make sure it is upgraded to a compatible version.",
            missingCapabilities),
        Color.YELLOW);
  }

  public void logExecutionClientVersion(final String name, final String version) {
    log.info("Execution Client version: {} {}", name, version);
  }

  public void logGraffitiWatermark(final String graffitiWatermark) {
    log.info(
        "Using graffiti watermark: \"{}\". This will be appended to any user-defined graffiti or used if none is defined. "
            + "Refer to validator graffiti options to customize.",
        graffitiWatermark);
  }

  public void builderIsNotAvailable(final String errorMessage) {
    final String builderIsNotAvailableEventLog =
        String.format(
            "The builder is not available: %s. Block production will fallback to the execution engine.",
            errorMessage);
    warn(builderIsNotAvailableEventLog, Color.YELLOW);
  }

  public void builderIsAvailable() {
    final String builderIsAvailableEventLog =
        "The builder is available. It will be used for block production.";
    info(builderIsAvailableEventLog, Color.GREEN);
  }

  public void syncStart() {
    info("Syncing started", Color.YELLOW);
  }

  public void weakSubjectivityFailedEvent(final Bytes32 blockRoot, final UInt64 slot) {
    final String weakSubjectivityFailedEventLog =
        String.format(
            "Syncing     *** Weak subjectivity check failed block: %s, slot: %s",
            LogFormatter.formatHashRoot(blockRoot), slot.toString());
    warn(weakSubjectivityFailedEventLog, Color.RED);
  }

  public void slotEvent(
      final UInt64 nodeSlot,
      final UInt64 headSlot,
      final Bytes32 bestBlockRoot,
      final UInt64 justifiedCheckpoint,
      final UInt64 finalizedCheckpoint,
      final int numPeers) {
    String blockRoot = "                                                       ... empty";
    if (nodeSlot.equals(headSlot)) {
      blockRoot = LogFormatter.formatHashRoot(bestBlockRoot);
    }
    final String slotEventLog =
        String.format(
            "Slot Event  *** Slot: %s, Block: %s, Justified: %s, Finalized: %s, Peers: %d",
            nodeSlot, blockRoot, justifiedCheckpoint, finalizedCheckpoint, numPeers);
    info(slotEventLog, Color.WHITE);
  }

  public void slotPayloadEvent(
      final UInt64 nodeSlot,
      final UInt64 headSlot,
      final Optional<Bytes32> maybeExecutionBlockHash,
      final int numPeers) {
    String executionBlockHash = "                                                       ... empty";
    if (nodeSlot.equals(headSlot) && maybeExecutionBlockHash.isPresent()) {
      executionBlockHash = LogFormatter.formatHashRoot(maybeExecutionBlockHash.get());
    }
    final String slotPayloadEventLog =
        String.format(
            "PTC Event   *** Slot: %s, Execution Block: %s, Peers: %d",
            nodeSlot, executionBlockHash, numPeers);
    info(slotPayloadEventLog, Color.GRAY);
  }

  public void reorgEvent(
      final Bytes32 previousHeadRoot,
      final UInt64 previousHeadSlot,
      final Bytes32 newHeadRoot,
      final UInt64 newHeadSlot,
      final Bytes32 commonAncestorRoot,
      final UInt64 commonAncestorSlot) {
    String reorgEventLog =
        String.format(
            "Reorg Event *** New Head: %s, Previous Head: %s, Common Ancestor: %s",
            LogFormatter.formatBlock(newHeadSlot, newHeadRoot),
            LogFormatter.formatBlock(previousHeadSlot, previousHeadRoot),
            LogFormatter.formatBlock(commonAncestorSlot, commonAncestorRoot));
    info(reorgEventLog, Color.YELLOW);
  }

  public void networkUpgradeActivated(
      final UInt64 nodeEpoch, final String upgradeName, final String banner) {
    if (banner.isEmpty()) {
      info(
          String.format(
              "Milestone   *** Epoch: %s, Activating network upgrade: %s", nodeEpoch, upgradeName),
          Color.GREEN);
    } else {
      info(
          String.format(
              "Milestone   *** Epoch: %s, Activating network upgrade: %s\n%s",
              nodeEpoch, upgradeName, banner),
          Color.GREEN);
    }
  }

  public void terminalPowBlockDetected(final Bytes32 terminalBlockHash) {
    info(
        String.format(
            "Merge       *** Terminal Block detected: %s",
            LogFormatter.formatHashRoot(terminalBlockHash)),
        Color.GREEN);
  }

  public void terminalPowBlockTtdEta(final UInt256 ttd, final Duration eta, final Instant instant) {

    final String etaString =
        eta.toMinutes() <= 1
            ? "imminent"
            : String.format(
                "%s days, %s hours and %s minutes (%s)",
                eta.toDays(),
                eta.toHours() - TimeUnit.DAYS.toHours(eta.toDays()),
                eta.toMinutes() - TimeUnit.HOURS.toMinutes(eta.toHours()),
                LocalDateTime.ofInstant(instant, TimeZone.getDefault().toZoneId())
                    .format(DateTimeFormatter.ofPattern("d MMM uuuu - HH:mm:ss")));

    log.info(String.format("TTD ETA: %s - Current Total Difficulty: %s", etaString, ttd));
  }

  public void lateBlockImport(
      final Bytes32 root,
      final UInt64 slot,
      final UInt64 proposer,
      final String timings,
      final String result) {
    final String slowBlockLog =
        String.format(
            "Late Block Import *** Block: %s Proposer: %s Result: %s Timings: %s",
            LogFormatter.formatBlock(slot, root), proposer, result, timings);
    warn(slowBlockLog, Color.YELLOW);
  }

  public void slowTickEvent(
      final UInt64 tickTime, final UInt64 totalProcessingDuration, final String timings) {
    final String slowTickLog =
        String.format(
            "Slow Tick Event   *** Time: %s %s total: %sms",
            tickTime, timings, totalProcessingDuration);
    warn(slowTickLog, Color.YELLOW);
  }

  public void slowBlockProductionEvent(
      final UInt64 slot, final UInt64 totalProcessingDuration, final String timings) {
    final String slowBlockProductionLog =
        String.format(
            "Slow Block Production *** Slot: %s %s total: %sms",
            slot, timings, totalProcessingDuration);
    warn(slowBlockProductionLog, Color.YELLOW);
  }

  public void slowBlockPublishingEvent(
      final UInt64 slot, final UInt64 totalProcessingDuration, final String timings) {
    final String slowBlockPublishingLog =
        String.format(
            "Slow Block Publishing *** Slot: %s %s total: %sms",
            slot, timings, totalProcessingDuration);
    warn(slowBlockPublishingLog, Color.YELLOW);
  }

  public void executionLayerStubEnabled() {
    error(
        "Execution Layer Stub has been enabled! This is UNSAFE! You WILL fail to produce blocks and may follow an invalid chain.",
        Color.RED);
  }

  public void depositContractLogsSyncingDisabled() {
    warn(
        "Deposit contract logs syncing from the Execution Client has been disabled! You WILL not be able to produce blocks.",
        Color.YELLOW);
  }

  public void builderBidNotHonouringGasLimit(
      final UInt64 parentGasLimit,
      final UInt64 proposedGasLimit,
      final UInt64 expectedGasLimit,
      final UInt64 preferredGasLimit) {
    String reorgEventLog =
        String.format(
            "Builder proposed a bid not honouring the validator gas limit preference. Parent: %s - Proposed: %s - Expected %s - Target: %s",
            parentGasLimit, proposedGasLimit, expectedGasLimit, preferredGasLimit);
    warn(reorgEventLog, Color.YELLOW);
  }

  private void info(final String message, final Color color) {
    log.info(print(message, color));
  }

  private void warn(final String message, final Color color) {
    log.warn(print(message, color));
  }

  private void error(final String message, final Color color, final Throwable error) {
    log.error(print(message, color), error);
  }

  private void error(final String message, final Color color) {
    log.error(print(message, color));
  }
}
