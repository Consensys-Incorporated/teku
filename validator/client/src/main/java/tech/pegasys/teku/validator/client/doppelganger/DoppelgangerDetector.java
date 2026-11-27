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

package tech.pegasys.teku.validator.client.doppelganger;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.NodeDataUnavailableException;

public class DoppelgangerDetector {

  private static final Logger LOG = LogManager.getLogger();
  private final StatusLogger statusLog;
  private final Duration checkDelay;
  private final int maxEpochs;
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final GenesisDataProvider genesisDataProvider;

  public DoppelgangerDetector(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final int maxEpochs) {
    this(
        STATUS_LOG,
        asyncRunner,
        validatorApiChannel,
        spec,
        timeProvider,
        genesisDataProvider,
        checkDelay,
        maxEpochs);
  }

  public DoppelgangerDetector(
      final StatusLogger statusLog,
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider,
      final Duration checkDelay,
      final int maxEpochs) {
    this.statusLog = statusLog;
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.genesisDataProvider = genesisDataProvider;
    this.checkDelay = checkDelay;
    this.maxEpochs = maxEpochs;
  }

  public SafeFuture<Map<UInt64, BLSPublicKey>> performDoppelgangerDetection(
      final Set<BLSPublicKey> pubKeys) {
    if (pubKeys.isEmpty()) {
      LOG.info("Skipping doppelganger detection: No public keys provided to check");
      return SafeFuture.completedFuture(new HashMap<>());
    }
    final DoppelgangerDetectionTask doppelgangerDetectionTask =
        new DoppelgangerDetectionTask(pubKeys);
    return doppelgangerDetectionTask.performDoppelgangerDetectionTask();
  }

  private class DoppelgangerDetectionTask {

    private volatile Cancellable doppelgangerDetectionTask;
    private final SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerCheckFinished =
        new SafeFuture<>();
    private final Set<BLSPublicKey> pubKeys;
    private Optional<UInt64> epochAtStart = Optional.empty();
    private final Map<UInt64, BLSPublicKey> detectedDoppelgangers = new HashMap<>();
    private boolean wasUnavailable;

    private DoppelgangerDetectionTask(final Set<BLSPublicKey> pubKeys) {
      this.pubKeys = pubKeys;
    }

    private synchronized SafeFuture<Map<UInt64, BLSPublicKey>> performDoppelgangerDetectionTask() {
      statusLog.doppelgangerDetectionStart(
          mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
      doppelgangerDetectionTask =
          asyncRunner.runWithFixedDelay(
              this::performDoppelgangerCheck,
              checkDelay,
              checkDelay,
              throwable ->
                  LOG.error(
                      "Error while checking validators doppelgangers for keys {}. The check could not be performed correctly.",
                      mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")),
                      throwable));
      return doppelgangerCheckFinished;
    }

    private SafeFuture<Void> performDoppelgangerCheck() {
      return getCurrentEpoch()
          .exceptionallyCompose(
              throwable ->
                  unavailable(
                      "Unable to get genesis time to calculate the current epoch: "
                          + extractErrorMessage(throwable)))
          .thenCompose(
              currentEpoch -> checkRequiredEpochs(currentEpoch).thenApply(ignored -> currentEpoch))
          .handleComposed(
              (currentEpoch, throwable) ->
                  throwable == null
                      ? handleSuccessfulCheck(currentEpoch)
                      : handleUnavailableCheck(extractErrorMessage(throwable)));
    }

    private SafeFuture<UInt64> getCurrentEpoch() {
      return genesisDataProvider
          .getGenesisTime()
          .orTimeout(asyncRunner, checkDelay)
          .thenApply(
              genesisTime ->
                  spec.computeEpochAtSlot(
                      spec.getCurrentSlot(timeProvider.getTimeInSeconds(), genesisTime)));
    }

    private SafeFuture<Void> checkRequiredEpochs(final UInt64 currentEpoch) {
      if (epochAtStart.isEmpty()) {
        epochAtStart = Optional.of(currentEpoch);
        return checkPreviousAndCurrentEpochs(currentEpoch);
      }
      if (!maxEpochsReached(currentEpoch)) {
        return checkDoppelgangersAtEpoch(currentEpoch);
      }
      return checkPreviousAndCurrentEpochs(currentEpoch);
    }

    private SafeFuture<Void> checkPreviousAndCurrentEpochs(final UInt64 currentEpoch) {
      if (currentEpoch.isZero()) {
        return checkDoppelgangersAtEpoch(currentEpoch);
      }
      return checkDoppelgangersAtEpoch(currentEpoch.minus(UInt64.ONE))
          .thenCompose(
              ignored ->
                  allKeysAreActive()
                      ? SafeFuture.COMPLETE
                      : checkDoppelgangersAtEpoch(currentEpoch));
    }

    private SafeFuture<Void> handleSuccessfulCheck(final UInt64 currentEpoch) {
      markAvailable();
      if (!allKeysAreActive() && !maxEpochsReached(currentEpoch)) {
        return SafeFuture.COMPLETE;
      }
      statusLog.doppelgangerDetectionEnd(
          mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()),
          detectedDoppelgangers.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString())));
      return completeDoppelgangerDetection();
    }

    private SafeFuture<Void> handleUnavailableCheck(final String reason) {
      markUnavailable(reason);
      return SafeFuture.COMPLETE;
    }

    private void markAvailable() {
      if (wasUnavailable) {
        statusLog.doppelgangerDetectionResumed(
            mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
      }
      wasUnavailable = false;
    }

    private void markUnavailable(final String reason) {
      if (!wasUnavailable) {
        statusLog.doppelgangerDetectionUnavailable(
            mapToAbbreviatedKeys(pubKeys).collect(Collectors.toSet()), reason);
      }
      epochAtStart = Optional.empty();
      wasUnavailable = true;
    }

    private synchronized SafeFuture<Void> completeDoppelgangerDetection() {
      if (!doppelgangerCheckFinished.complete(detectedDoppelgangers)) {
        throw new IllegalStateException("Doppelganger detection is already stopped");
      }
      return cancelScheduledTask();
    }

    private SafeFuture<Void> cancelScheduledTask() {
      return SafeFuture.fromRunnable(
          () -> Optional.ofNullable(doppelgangerDetectionTask).ifPresent(Cancellable::cancel));
    }

    private SafeFuture<Void> checkDoppelgangersAtEpoch(final UInt64 epoch) {
      final Set<BLSPublicKey> inactivePubKeys =
          pubKeys.stream()
              .filter(pubKey -> !detectedDoppelgangers.containsValue(pubKey))
              .collect(Collectors.toSet());
      statusLog.doppelgangerCheck(
          epoch, mapToAbbreviatedKeys(inactivePubKeys).collect(Collectors.toSet()));

      return validatorApiChannel
          .getValidatorIndices(inactivePubKeys)
          .orTimeout(asyncRunner, checkDelay)
          .exceptionallyCompose(
              throwable ->
                  unavailable("Unable to get validator indices: " + extractErrorMessage(throwable)))
          .thenCompose(
              maybeValidatorIndices ->
                  maybeValidatorIndices
                      .map(
                          validatorIndices ->
                              checkValidatorsLivenessAtEpoch(
                                  epoch,
                                  inactivePubKeys,
                                  validatorIndices.entrySet().stream()
                                      .collect(
                                          Collectors.toMap(
                                              Map.Entry::getKey,
                                              entry -> UInt64.valueOf(entry.getValue())))))
                      .orElseGet(
                          () -> unavailable("Beacon node returned no validator index data")));
    }

    private SafeFuture<Void> checkValidatorsLivenessAtEpoch(
        final UInt64 epoch,
        final Set<BLSPublicKey> pubKeys,
        final Map<BLSPublicKey, UInt64> validatorIndicesByPubKey) {
      if (validatorIndicesByPubKey.isEmpty()) {
        LOG.info(
            "Skipping validators doppelgangers check for public keys {}. No associated indices found. Public keys are inactive",
            mapToAbbreviatedKeys(pubKeys).collect(Collectors.joining(", ")));
        return SafeFuture.COMPLETE;
      }

      logMissingIndices(pubKeys, validatorIndicesByPubKey);
      return validatorApiChannel
          .getValidatorsLiveness(new ArrayList<>(validatorIndicesByPubKey.values()), epoch)
          .orTimeout(asyncRunner, checkDelay)
          .exceptionallyCompose(
              throwable ->
                  unavailable(
                      "Unable to get validator liveness: " + extractErrorMessage(throwable)))
          .thenApply(
              maybeValidatorLiveness -> {
                final List<ValidatorLivenessAtEpoch> validatorLiveness =
                    maybeValidatorLiveness.orElseThrow(
                        () ->
                            new NodeDataUnavailableException(
                                "Beacon node returned no validator liveness data"));
                checkValidatorDoppelgangers(validatorLiveness, validatorIndicesByPubKey);
                final Set<UInt64> missingValidatorIndices =
                    new HashSet<>(validatorIndicesByPubKey.values());
                validatorLiveness.forEach(
                    liveness -> missingValidatorIndices.remove(liveness.index()));
                if (!missingValidatorIndices.isEmpty()) {
                  throw new NodeDataUnavailableException(
                      "Beacon node liveness response omitted validator indices "
                          + missingValidatorIndices);
                }
                return null;
              });
    }

    private <T> SafeFuture<T> unavailable(final String reason) {
      return SafeFuture.failedFuture(new NodeDataUnavailableException(reason));
    }

    private boolean maxEpochsReached(final UInt64 epoch) {
      return epochAtStart
          .map(startEpoch -> epoch.minus(startEpoch).isGreaterThanOrEqualTo(maxEpochs))
          .orElse(false);
    }

    private boolean allKeysAreActive() {
      return detectedDoppelgangers.values().containsAll(pubKeys);
    }

    private void logMissingIndices(
        final Set<BLSPublicKey> pubKeys, final Map<BLSPublicKey, UInt64> validatorIndicesByPubKey) {
      final Set<BLSPublicKey> publicKeysWithoutIndices =
          pubKeys.stream()
              .filter(publicKey -> !validatorIndicesByPubKey.containsKey(publicKey))
              .collect(Collectors.toSet());

      if (!publicKeysWithoutIndices.isEmpty()) {
        LOG.info(
            "Skipping doppelganger check for public keys {}. No associated indices found. Public keys are inactive",
            publicKeysWithoutIndices.stream()
                .map(BLSPublicKey::toAbbreviatedString)
                .collect(Collectors.joining(", ")));
      }
    }

    private void checkValidatorDoppelgangers(
        final List<ValidatorLivenessAtEpoch> validatorLiveness,
        final Map<BLSPublicKey, UInt64> validatorsIndicesByPubKey) {
      final List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> doppelgangers =
          filterLiveValidators(validatorLiveness, validatorsIndicesByPubKey);
      if (doppelgangers.isEmpty()) {
        return;
      }
      LOG.fatal("Validator doppelganger detected...");
      statusLog.validatorsDoppelgangersDetected(
          mapLivenessAtEpochToIndicesByPubKeyStrings(doppelgangers));
      doppelgangers.forEach(
          doppelganger ->
              detectedDoppelgangers.putIfAbsent(
                  doppelganger.getRight().index(), doppelganger.getLeft()));
    }

    private Map<UInt64, String> mapLivenessAtEpochToIndicesByPubKeyStrings(
        final List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> doppelgangers) {
      return doppelgangers.stream()
          .collect(
              Collectors.toMap(
                  entry -> entry.getRight().index(), entry -> entry.getLeft().toString()));
    }

    private Stream<String> mapToAbbreviatedKeys(final Set<BLSPublicKey> pubKeys) {
      return pubKeys.stream().map(BLSPublicKey::toAbbreviatedString);
    }

    private List<Pair<BLSPublicKey, ValidatorLivenessAtEpoch>> filterLiveValidators(
        final List<ValidatorLivenessAtEpoch> validatorLiveness,
        final Map<BLSPublicKey, UInt64> validatorPubKeysByIndices) {
      return validatorLiveness.stream()
          .filter(
              validatorLivenessAtEpoch ->
                  validatorPubKeysByIndices.containsValue(validatorLivenessAtEpoch.index())
                      && validatorLivenessAtEpoch.isLive())
          .map(
              validatorLivenessAtEpoch ->
                  Pair.of(
                      validatorPubKeysByIndices.entrySet().stream()
                          .filter(
                              entry -> entry.getValue().equals(validatorLivenessAtEpoch.index()))
                          .findFirst()
                          .orElseThrow()
                          .getKey(),
                      validatorLivenessAtEpoch))
          .toList();
    }

    private String extractErrorMessage(final Throwable throwable) {
      return ExceptionUtil.hasCause(throwable, TimeoutException.class)
          ? "Request timeout"
          : ExceptionUtil.getRootCauseMessage(throwable);
    }
  }
}
