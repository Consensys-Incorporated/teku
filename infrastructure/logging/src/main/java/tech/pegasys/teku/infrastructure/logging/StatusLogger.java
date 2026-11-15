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

package tech.pegasys.teku.infrastructure.logging;

import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StatusLogger {

  public static final StatusLogger STATUS_LOG =
      new StatusLogger(LoggingConfigurator.STATUS_LOGGER_NAME);

  private static final int VALIDATOR_KEY_LIMIT = 20;

  @SuppressWarnings("PrivateStaticFinalLoggers")
  final Logger log;

  private StatusLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void onStartup(final String version) {
    log.info("Teku version: {}", version);
    log.info(
        "This software is licensed under the Apache License, Version 2.0 (the \"License\"); "
            + "you may not use this software except in compliance with the License. "
            + "You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0");
  }

  public void startupConfigurations(final StartupLogConfig config) {
    config.getReport().forEach(log::info);
  }

  public void warnForkEpochChanged(final String milestoneName, final UInt64 newEpoch) {
    log.warn(
        print(
            milestoneName + " configuration has been overridden to activate at epoch " + newEpoch,
            Color.YELLOW));
  }

  public void warnBellatrixParameterChanged(final String parameterName, final String newValue) {
    log.warn(
        print(
            "Bellatrix parameter " + parameterName + " has been overridden to " + newValue,
            Color.YELLOW));
  }

  public void warnDenebEpochsStoreBlobsParameterSet(
      final String epochsStoreBlobs,
      final boolean isOverridden,
      final String specValue,
      final Integer maxEpochsStoreBlobs) {
    if (isOverridden) {
      final boolean isMax = String.valueOf(maxEpochsStoreBlobs).equals(epochsStoreBlobs);
      log.warn(
          print(
              "Xepochs-store-blobs has been set to "
                  + (isMax ? "MAX" : epochsStoreBlobs)
                  + ". It overrides the standard number of epochs blob sidecars are stored and requested during the sync.",
              Color.YELLOW));
    } else {
      log.warn(
          print(
              "Ignoring Xepochs-store-blobs value "
                  + epochsStoreBlobs
                  + " since it is less than MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS = "
                  + specValue,
              Color.YELLOW));
    }
  }

  public void warnMissingProposerDefaultFeeRecipientWithRestAPIEnabled() {
    log.warn(
        print(
            "Rest API is enabled but no default fee recipient has been configured via the validators-proposer-default-fee-recipient option! "
                + "It is strongly recommended to configure it to avoid possible block production failures in case the node has not been prepared for potential proposers by the Validator Client.",
            Color.RED));
  }

  public void warnMissingProposerDefaultFeeRecipientWithPreparedBeaconProposerBeingCalled() {
    log.warn(
        print(
            "Remote Validator Client detected and no default fee recipient has been configured via the validators-proposer-default-fee-recipient option! "
                + "It is strongly recommended to configure it to avoid possible block production failures in case the node has not been prepared for potential proposers by the Validator Client.",
            Color.RED));
  }

  public void fatalError(final String description, final Throwable cause) {
    log.fatal("Exiting due to fatal error in {}", description, cause);
  }

  public void specificationFailure(final String description, final Throwable cause) {
    log.warn("Spec failed for {}: {}", description, cause, cause);
  }

  public void failedToLoadValidatorKey(final String message) {
    log.fatal("Failed to load keystore, error {}", message);
  }

  public void eth1DepositEventsFailure(final Throwable cause) {
    log.fatal(
        "PLEASE CHECK YOUR ETH1 NODE | Encountered a problem retrieving deposit events from eth1 endpoint: {}",
        cause.getMessage());
  }

  public void eth1FetchDepositsRequiresSmallerRange(final int batchSize) {
    log.warn(
        "Request for eth1 deposit logs from {} blocks failed. Retrying with a smaller block range.",
        batchSize);
  }

  public void eth1PollingHasBeenDisabled() {
    log.info("Eth1 polling has been disabled");
  }

  public void unexpectedFailure(final String description, final Throwable cause) {
    log.error("PLEASE FIX OR REPORT | Unexpected exception thrown for {}", description, cause);
  }

  public void listeningForLibP2P(final List<String> addresses) {
    log.info("Listening for connections on: {}", String.join(",", addresses));
  }

  public void listeningForDiscv5PreGenesis(final String enr) {
    log.info("PreGenesis Local ENR: {}", enr);
  }

  public void listeningForDiscv5(final String enr) {
    log.info("Local ENR: {}", enr);
  }

  public void validatorDepositEncryptedKeystoreWriterFailure(
      final String message, final Path file, final String cause) {
    log.error(message, file.toString(), cause);
  }

  public void loadingValidators(final int validatorCount) {
    log.info("Loading {} validator keys...", validatorCount);
  }

  public void atLoadedValidatorNumber(
      final int loadedValidatorCount, final int totalValidatorCount) {
    log.info("Loaded validator key {} of {}.", loadedValidatorCount, totalValidatorCount);
  }

  public void validatorsInitialised(final List<String> validators) {
    if (validators.size() > 100) {
      log.info("Loaded {} validators", validators.size());
      log.debug("validators: {}", () -> String.join(", ", validators));
    } else {
      log.info("Loaded {} Validators: {}", validators::size, () -> String.join(", ", validators));
    }
  }

  public void doppelgangerDetectionStart(final Set<String> publicKeys) {
    log.info("Starting doppelganger detection for public keys: {}", String.join(", ", publicKeys));
  }

  public void doppelgangerDetectionUnavailable(final Set<String> publicKeys, final String reason) {
    log.error(
        "Doppelganger detection is temporarily unavailable for public keys {}. "
            + "Duties for these public keys remain disabled while Teku retries. "
            + "Reason: {}",
        String.join(", ", publicKeys),
        reason);
  }

  public void doppelgangerDetectionResumed(final Set<String> publicKeys) {
    log.info(
        "Beacon node is available. Resuming doppelganger detection for public keys {}.",
        String.join(", ", publicKeys));
  }

  public void doppelgangerDetectionEnd(
      final Set<String> publicKeys, final Map<UInt64, String> doppelgangersInfo) {
    log.info(
        "Doppelganger detection check finished. Stopping doppelganger detection for public keys {}.",
        String.join(", ", publicKeys));
    if (doppelgangersInfo.isEmpty()) {
      log.info("No validators doppelganger detected.");
    } else {
      validatorsDoppelgangersDetected(doppelgangersInfo);
    }
  }

  public void doppelgangerCheck(final UInt64 epoch, final Set<String> publicKeys) {
    log.info(
        "Performing doppelganger check at epoch {} for public keys {} ",
        epoch,
        formatValidatorKeys(publicKeys));
  }

  public void validatorsDoppelgangersDetected(final Map<UInt64, String> doppelgangersInfo) {
    String doppelgangersLogInfo =
        doppelgangersInfo.entrySet().stream()
            .map(
                doppelgangerInfo ->
                    StringUtils.isBlank(doppelgangerInfo.getValue())
                        ? String.format("Index: %s", doppelgangerInfo.getKey())
                        : String.format(
                            "Index: %s, Public key: %s",
                            doppelgangerInfo.getKey(), doppelgangerInfo.getValue()))
            .collect(Collectors.joining("\n", "\n", "\n"));
    log.fatal(
        "Detected {} validators doppelganger: {}", doppelgangersInfo.size(), doppelgangersLogInfo);
  }

  public void doppelgangerDetectionAlert(final Set<String> doppelgangerPublicKeys) {
    log.error(
        "Detected {} active validators doppelganger. The following keys have been ignored: {}",
        doppelgangerPublicKeys.size(),
        String.join(", ", doppelgangerPublicKeys));
  }

  public void exitOnDoppelgangerDetected(final String keys) {
    log.fatal("Validator doppelganger detected. Public keys: {}. Shutting down...", keys);
  }

  public void exitOnNoValidatorKeys() {
    log.fatal(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is true. Shutting down...");
  }

  public void validatorSlashedAlert(final Set<String> slashedValidatorPublicKeys) {
    log.fatal(
        "Validator slashing detection is enabled and validator(s) with public key(s) {} detected as slashed. "
            + "Shutting down...",
        String.join(", ", slashedValidatorPublicKeys));
  }

  public void beginInitializingChainData() {
    log.info("Initializing storage");
  }

  public void reconstructedHistoricalBlocks(
      final UInt64 numberRecorded, final UInt64 totalToRecord) {
    log.info(
        "ReconstructHistoricalStatesService recorded {} of {} historical blocks",
        numberRecorded,
        totalToRecord);
  }

  public void failedToStartValidatorClient(final String message) {
    log.fatal(
        "An error was encountered during validator client service start up. Error: {}", message);
    log.fatal("Please check the logs for details.");
  }

  public void fatalErrorInitialisingStorage(final Throwable err) {
    log.debug("Failed to initialize storage", err);
    log.fatal(
        "Failed to initialize storage. "
            + "Check the existing database matches the current network configuration. "
            + "Set log level to debug for more information.");
  }

  public void finishInitializingChainData() {
    log.info("Storage initialization complete");
  }

  public void emptyChainData() {
    log.info("Empty storage. Initialization complete.");
  }

  public void recordedFinalizedBlocks(final int numberRecorded, final int totalToRecord) {
    log.info("Recorded {} of {} finalized blocks", numberRecorded, totalToRecord);
  }

  public void generatingMockStartGenesis(final long genesisTime, final int size) {
    log.info(
        "Starting with mocked start interoperability mode with genesis time {} and {} validators",
        () -> DateFormatUtils.format(genesisTime * 1000, "yyyy-MM-dd hh:mm:ss"),
        () -> size);
  }

  public void timeUntilGenesis(final long timeToGenesis, final int peerCount) {
    log.info(
        "{} until genesis time is reached. Peers: {}",
        () -> DurationFormatUtils.formatDurationWords(timeToGenesis * 1000, true, true),
        () -> peerCount);
  }

  public void loadingInitialStateResource(final String wsBlockResource) {
    log.info("Loading initial state from {}", wsBlockResource);
  }

  public void loadedInitialStateResource(
      final Bytes32 stateRoot,
      final Bytes32 blockRoot,
      final UInt64 blockSlot,
      final UInt64 epoch,
      final UInt64 epochStartSlot) {
    if (blockSlot.isGreaterThan(0)) {
      log.info(
          "Loaded initial state at epoch {} (state root = {}, block root = {}, block slot = {}).  Please ensure that the supplied initial state corresponds to the latest finalized block as of the start of epoch {} (slot {}).",
          epoch,
          stateRoot,
          blockRoot,
          blockSlot,
          epoch,
          epochStartSlot);
    } else {
      log.info(
          "Loaded initial state at epoch {} (state root = {}, block root = {}, block slot = {}).",
          epoch,
          stateRoot,
          blockRoot,
          blockSlot);
    }
  }

  public void warningUnexpectedInitialState(
      final UInt64 epoch, final UInt64 stateSlot, final UInt64 blockSlot) {
    log.warn(
        "Starting from state at slot {}, with block slot {}, which is not an anchor state. "
            + "Ensure that slots from {} - {} are empty. At epoch {}.",
        stateSlot,
        blockSlot,
        stateSlot,
        blockSlot,
        epoch);
  }

  public void warnInitialStateIgnored() {
    log.warn("Not loading specified initial state as chain data already exists.");
  }

  public void warnFailedToLoadInitialState(final String message) {
    log.warn(message);
  }

  public void warnOnInitialStateWithSkippedSlots(
      final Level level,
      final UInt64 anchorBlockSlot,
      final UInt64 anchorEpoch,
      final UInt64 anchorEpochStartSlot) {
    final UInt64 slotsBetweenBlockAndEpochStart =
        anchorEpochStartSlot.minusMinZero(anchorBlockSlot);
    if (slotsBetweenBlockAndEpochStart.equals(UInt64.ZERO)) {
      return;
    }

    final String msg =
        String.format(
            "The provided initial state is %s slots prior to the start of epoch %s. Please ensure that slots %s - %s (inclusive) are empty.",
            slotsBetweenBlockAndEpochStart,
            anchorEpoch,
            anchorBlockSlot.plus(1),
            anchorEpochStartSlot);
    logWithColorIfLevelGreaterThanInfo(level, msg, ColorConsolePrinter.Color.YELLOW);
  }

  public void loadingGenesisFromEth1Chain() {
    log.info("No genesis state available. Loading deposits from ETH1 chain");
  }

  public void genesisValidatorsActivated(
      final int activeValidatorCount, final int requiredValidatorCount) {
    log.info(
        "Activated {} of {} validators required for genesis ({}%)",
        activeValidatorCount,
        requiredValidatorCount,
        activeValidatorCount * 100 / requiredValidatorCount);
  }

  public void minGenesisTimeReached() {
    log.info("ETH1 block satisfying minimum genesis time found");
  }

  public void beaconDataPathSet(final Path dataPath) {
    log.info("Storing beacon chain data in: {}", dataPath.toAbsolutePath());
  }

  public void validatorDataPathSet(final Path dataPath) {
    log.info("Storing validator data in: {}", dataPath.toAbsolutePath());
  }

  public void eth1ServiceDown(final UInt64 interval) {
    log.warn("Eth1 service down or still syncing for {}s, retrying", interval);
  }

  public void reconstructHistoricalStatesServiceFailedStartup(final Throwable throwable) {
    log.error("ReconstructHistoricalStatesService unable to start", throwable);
  }

  public void reconstructHistoricalStatesServiceFailedProcess(final Throwable throwable) {
    log.error("ReconstructHistoricalStatesService failed constructing states", throwable);
  }

  public void reconstructHistoricalStatesServiceComplete() {
    log.info("ReconstructHistoricalStatesService reconstruction complete");
  }

  public void eth1AtHead(final BigInteger headBlockNumber) {
    log.info("Loading deposits up to Eth1 block {}", headBlockNumber);
  }

  public void usingGeneratedP2pPrivateKey(final String key, final boolean justGenerated) {
    if (justGenerated) {
      log.info("Generated new p2p private key and storing in: " + key);
    } else {
      log.info("Loading generated p2p private key from: " + key);
    }
  }

  public void performance(final String performance) {
    log.info(performance);
  }

  public void eth1DepositChainIdMismatch(
      final long expectedChainId, final long eth1ChainId, final String endpointId) {
    log.log(
        Level.ERROR,
        "PLEASE CHECK YOUR ETH1 NODE (endpoint {})| Wrong Eth1 chain id (expected={}, actual={})",
        endpointId,
        expectedChainId,
        eth1ChainId);
  }

  public void externalSignerStatus(final URL externalSignerUrl, final boolean isReachable) {
    if (isReachable) {
      log.info("External signer is reachable at {}", externalSignerUrl);
    } else {
      log.error(
          ColorConsolePrinter.print(
              "External signer is currently not reachable at " + externalSignerUrl,
              ColorConsolePrinter.Color.RED));
    }
  }

  public void unableToRetrieveValidatorStatusesFromBeaconNode() {
    log.error("Unable to retrieve validator statuses from BeaconNode.");
  }

  public void validatorStatus(final String publicKey, final String validatorStatus) {
    log.info("Validator {} status is {}.", publicKey, validatorStatus);
  }

  public void unableToRetrieveValidatorStatus(final String publicKey) {
    log.warn("Unable to retrieve status for validator {}.", publicKey);
  }

  public void unableToRetrieveValidatorStatusSummary(final int n) {
    log.warn("Unable to retrieve status for {} validators.", n);
  }

  public void validatorStatusSummary(final int n, final String validatorStatus) {
    log.info("{} validators are in {} state.", n, validatorStatus);
  }

  public void validatorStatusChange(
      final String oldStatus, final String newStatus, final String publicKey) {
    log.warn("Validator {} has changed status from {} to {}.", publicKey, oldStatus, newStatus);
  }

  public void eth1MinGenesisNotFound(final Throwable error) {
    log.error(
        "Failed to retrieve min genesis block. "
            + "Check that your eth1 node is fully synced. "
            + "Will retry in 1 minute.",
        error);
  }

  public void unknownLatestValidHash(final Bytes32 latestValidHash) {
    log.error(
        print(
            "Could not find latest valid execution payload hash ("
                + latestValidHash
                + ") in the non-finalized chain. Optimistic sync may have finalized an invalid transition block.",
            Color.RED));
  }

  public void warnIgnoringWeakSubjectivityPeriod() {
    log.warn(
        print(
            "Ignoring weak subjectivity period check (--ignore-weak-subjectivity-period-enabled). Syncing "
                + "from outside of the weak subjectivity period is considered UNSAFE.",
            Color.YELLOW));
  }

  public void warnUsageOfImplicitPruneDataStorageMode() {
    log.warn(
        print(
            "Prune mode being used as default without a explicit --data-storage-mode option. This will NOT be "
                + "supported in future Teku versions. Please add --data-storage-mode=prune to your CLI arguments"
                + " or config file if you want to keep using PRUNE.",
            Color.YELLOW));
  }

  private void logWithColorIfLevelGreaterThanInfo(
      final Level level, final String msg, final ColorConsolePrinter.Color color) {
    final boolean useColor = level.compareTo(Level.INFO) < 0;
    log.log(level, useColor ? print(msg, color) : msg);
  }

  private String formatValidatorKeys(final Set<String> keys) {
    if (keys.isEmpty()) {
      return "";
    }
    final String suffix = keys.size() > VALIDATOR_KEY_LIMIT ? "… (" + keys.size() + " total)" : "";
    return keys.stream().limit(VALIDATOR_KEY_LIMIT).collect(Collectors.joining(", ", "", suffix));
  }
}
