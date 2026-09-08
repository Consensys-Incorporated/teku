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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorRegistrator implements ValidatorTimingChannel {

  static final BiFunction<Validator, ProposerConfigPropertiesProvider, BLSPublicKey>
      VALIDATOR_BUILDER_PUBLIC_KEY =
          (validator, propertiesProvider) ->
              propertiesProvider
                  .getBuilderRegistrationPublicKeyOverride(validator.getPublicKey())
                  .orElse(validator.getPublicKey());

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

  private final Map<BLSPublicKey, SignedValidatorRegistration> cachedValidatorRegistrations =
      Maps.newConcurrentMap();

  private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);
  private final AtomicReference<UInt64> currentEpoch = new AtomicReference<>();
  private final AtomicReference<UInt64> lastSuccessfulRunEpoch = new AtomicReference<>();
  // used to disable this class post-Gloas
  private final AtomicBoolean disabled = new AtomicBoolean(false);

  private final Spec spec;
  private final OwnedValidators ownedValidators;
  private final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider;
  private final SignedValidatorRegistrationFactory signedValidatorRegistrationFactory;
  private final ValidatorApiChannel validatorApiChannel;
  private final int batchSize;
  private final AsyncRunner asyncRunner;

  public ValidatorRegistrator(
      final Spec spec,
      final OwnedValidators ownedValidators,
      final ProposerConfigPropertiesProvider proposerConfigPropertiesProvider,
      final SignedValidatorRegistrationFactory signedValidatorRegistrationFactory,
      final ValidatorApiChannel validatorApiChannel,
      final int batchSize,
      final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.ownedValidators = ownedValidators;
    this.proposerConfigPropertiesProvider = proposerConfigPropertiesProvider;
    this.signedValidatorRegistrationFactory = signedValidatorRegistrationFactory;
    this.validatorApiChannel = validatorApiChannel;
    this.batchSize = batchSize;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    if (disabled.get()) {
      return;
    }
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    currentEpoch.set(epoch);
    // signed proposer preferences supersedes validator registrations
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      disabled.set(true);
    }
  }

  /**
   * When possible missing events are detected, it may mean changing of BN which requires VC to run
   * registrations again. This event is handled by possibleMissingEvents flag in {@link
   * #onUpdatedValidatorStatuses(Map, boolean)}
   */
  @Override
  public void onPossibleMissedEvents() {}

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {
    if (disabled.get()) {
      return;
    }
    if (!registrationInProgress.compareAndSet(false, true)) {
      LOG.warn(
          "Validator registration(s) is still in progress. Will skip sending registration(s).");
      return;
    }

    proposerConfigPropertiesProvider
        .refresh()
        .thenCompose(
            __ -> {
              if (!isReadyToRegister()) {
                return SafeFuture.COMPLETE;
              }
              final List<Validator> validators =
                  getValidatorsRequiringRegistration(newValidatorStatuses);
              if (validators.isEmpty()) {
                LOG.debug("No validator registrations are required to be sent");
                return SafeFuture.COMPLETE;
              }
              if (registrationNeedsToBeRun(possibleMissingEvents)) {
                return registerValidators(validators, true);
              } else {
                final List<Validator> newValidators =
                    validators.stream()
                        .filter(
                            validator ->
                                !cachedValidatorRegistrations.containsKey(validator.getPublicKey()))
                        .toList();
                if (newValidators.isEmpty()) {
                  return SafeFuture.COMPLETE;
                }
                return registerValidators(newValidators, false);
              }
            })
        .finish(
            __ -> registrationInProgress.set(false),
            error -> {
              VALIDATOR_LOGGER.registeringValidatorsFailed(error);
              LOG.info("Will retry to register validators in {} seconds", RETRY_DELAY.toSeconds());
              asyncRunner.runAfterDelay(
                  () -> {
                    registrationInProgress.set(false);
                    onUpdatedValidatorStatuses(newValidatorStatuses, possibleMissingEvents);
                  },
                  RETRY_DELAY);
            });
  }

  public int getNumberOfCachedRegistrations() {
    return cachedValidatorRegistrations.size();
  }

  private boolean isReadyToRegister() {
    // Paranoid check
    if (currentEpoch.get() == null) {
      LOG.warn("Current epoch is not yet set, validator registrations delayed");
      return false;
    }
    if (proposerConfigPropertiesProvider.isReadyToProvideProperties()) {
      return true;
    }
    LOG.debug("Not ready to register validator(s).");
    return false;
  }

  private List<Validator> getValidatorsRequiringRegistration(
      final Map<BLSPublicKey, ValidatorStatus> validatorStatuses) {

    return ownedValidators.getValidators().stream()
        .filter(
            validator -> {
              // filtering out validators which don't have builder flow enabled
              if (!proposerConfigPropertiesProvider.isBuilderEnabled(validator.getPublicKey())) {
                return false;
              }
              // filtering out exited validators
              return Optional.ofNullable(
                      validatorStatuses.get(
                          VALIDATOR_BUILDER_PUBLIC_KEY.apply(
                              validator, proposerConfigPropertiesProvider)))
                  .map(status -> !status.hasExited())
                  .orElse(false);
            })
        .toList();
  }

  private boolean registrationNeedsToBeRun(final boolean possibleMissingEvents) {
    if (lastSuccessfulRunEpoch.get() == null || possibleMissingEvents) {
      return true;
    }

    return currentEpoch
        .get()
        .minus(lastSuccessfulRunEpoch.get())
        .isGreaterThanOrEqualTo(Constants.EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION);
  }

  private SafeFuture<Void> registerValidators(
      final List<Validator> validators, final boolean updateLastSuccessfulRunEpoch) {

    return processInBatches(validators)
        .whenSuccess(
            () -> {
              if (updateLastSuccessfulRunEpoch) {
                lastSuccessfulRunEpoch.set(currentEpoch.get());
              }
            })
        .alwaysRun(() -> cleanupCache(ownedValidators.getValidators()));
  }

  private SafeFuture<Void> processInBatches(final List<Validator> validators) {
    final List<List<Validator>> batchedValidators = Lists.partition(validators, batchSize);

    LOG.debug(
        "Going to prepare and send {} validator registration(s) to the Beacon Node in {} batch(es)",
        validators.size(),
        batchedValidators.size());

    final Iterator<List<Validator>> batchedValidatorsIterator = batchedValidators.iterator();

    final AtomicInteger batchCounter = new AtomicInteger(0);
    final AtomicInteger successfullySentRegistrations = new AtomicInteger(0);

    return SafeFuture.asyncDoWhile(
            () -> {
              if (!batchedValidatorsIterator.hasNext()) {
                return SafeFuture.completedFuture(false);
              }
              final List<Validator> batch = batchedValidatorsIterator.next();
              final int currentBatch = batchCounter.incrementAndGet();
              LOG.debug(
                  "Starting to process validators registration batch {}/{}",
                  currentBatch,
                  batchedValidators.size());
              return createValidatorRegistrations(batch)
                  .thenCompose(this::sendValidatorRegistrations)
                  .thenApply(
                      size -> {
                        successfullySentRegistrations.updateAndGet(count -> count + size);
                        LOG.debug(
                            "Batch {}/{}: {} validator(s) registrations were sent to the Beacon Node out of {} validators.",
                            currentBatch,
                            batchedValidators.size(),
                            size,
                            batch.size());
                        return true;
                      });
            })
        .alwaysRun(
            () ->
                VALIDATOR_LOGGER.validatorRegistrationsSentToTheBuilderNetwork(
                    successfullySentRegistrations.get(), validators.size()));
  }

  private SafeFuture<List<SignedValidatorRegistration>> createValidatorRegistrations(
      final List<Validator> validators) {
    final Stream<SafeFuture<SignedValidatorRegistration>> validatorRegistrationsFutures =
        validators.stream()
            .map(
                validator ->
                    signedValidatorRegistrationFactory
                        .createSignedValidatorRegistration(
                            validator,
                            Optional.ofNullable(
                                cachedValidatorRegistrations.get(validator.getPublicKey())),
                            throwable -> {
                              final String errorMessage =
                                  String.format(
                                      "Exception while creating a validator registration for %s. Creation will be attempted again next epoch.",
                                      validator.getPublicKey());
                              LOG.warn(errorMessage, throwable);
                            })
                        .thenPeek(
                            registration ->
                                cachedValidatorRegistrations.put(
                                    validator.getPublicKey(), registration)));
    return SafeFuture.collectAllSuccessful(validatorRegistrationsFutures);
  }

  private SafeFuture<Integer> sendValidatorRegistrations(
      final List<SignedValidatorRegistration> validatorRegistrations) {
    final SszList<SignedValidatorRegistration> sszValidatorRegistrations =
        SszUtils.toSszList(
            ApiSchemas.SIGNED_VALIDATOR_REGISTRATIONS_SCHEMA, validatorRegistrations);
    return validatorApiChannel
        .registerValidators(sszValidatorRegistrations)
        .thenApply(__ -> validatorRegistrations.size());
  }

  private void cleanupCache(final List<Validator> managedValidators) {
    if (cachedValidatorRegistrations.isEmpty()
        || cachedValidatorRegistrations.size() == managedValidators.size()) {
      return;
    }

    final Set<BLSPublicKey> managedValidatorsPublicKeys =
        managedValidators.stream()
            .map(Validator::getPublicKey)
            .collect(Collectors.toCollection(HashSet::new));

    cachedValidatorRegistrations
        .keySet()
        .removeIf(
            cachedPublicKey -> {
              final boolean requiresRemoving =
                  !managedValidatorsPublicKeys.contains(cachedPublicKey);
              if (requiresRemoving) {
                LOG.debug(
                    "Removing cached registration for {} because validator is no longer owned.",
                    cachedPublicKey);
              }
              return requiresRemoving;
            });
  }
}
