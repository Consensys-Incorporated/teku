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

package tech.pegasys.teku.validator.client;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorIndexProvider {

  private static final Logger LOG = LogManager.getLogger();
  public static final Duration RETRY_DELAY = Duration.ofSeconds(5);
  private final OwnedValidators ownedValidators;
  private final ValidatorApiChannel validatorApiChannel;
  private final AsyncRunner asyncRunner;
  private final Map<BLSPublicKey, Integer> validatorIndicesByPublicKey = new ConcurrentHashMap<>();

  private final AtomicBoolean requestInProgress = new AtomicBoolean(false);
  private final SafeFuture<Void> firstSuccessfulRequest = new SafeFuture<>();

  public ValidatorIndexProvider(
      final OwnedValidators ownedValidators,
      final ValidatorApiChannel validatorApiChannel,
      final AsyncRunner asyncRunner) {
    this.ownedValidators = ownedValidators;
    this.validatorApiChannel = validatorApiChannel;
    this.asyncRunner = asyncRunner;
  }

  public void lookupValidators() {
    final Collection<BLSPublicKey> unknownValidators = getUnknownValidators();
    if (unknownValidators.isEmpty()) {
      return;
    }

    if (!requestInProgress.compareAndSet(false, true)) {
      return;
    }
    LOG.trace("Looking up {} unknown validators", unknownValidators.size());
    validatorApiChannel
        .getValidatorIndices(unknownValidators)
        .thenCompose(
            maybeKnownValidators ->
                maybeKnownValidators
                    .map(
                        knownValidators -> {
                          logNewValidatorIndices(knownValidators);
                          validatorIndicesByPublicKey.putAll(knownValidators);
                          firstSuccessfulRequest.complete(null);
                          return SafeFuture.COMPLETE;
                        })
                    .orElseGet(
                        () ->
                            SafeFuture.failedFuture(
                                new NodeDataUnavailableException(
                                    "Beacon node returned no validator index data"))))
        .orTimeout(30, TimeUnit.SECONDS)
        .whenComplete((result, error) -> requestInProgress.set(false))
        .finish(
            error -> {
              LOG.warn("Failed to load validator indices. Retrying.", error);
              asyncRunner.runAfterDelay(this::lookupValidators, RETRY_DELAY).finishError(LOG);
            });
  }

  private Collection<BLSPublicKey> getUnknownValidators() {
    return Sets.difference(ownedValidators.getPublicKeys(), validatorIndicesByPublicKey.keySet());
  }

  private void logNewValidatorIndices(final Map<BLSPublicKey, Integer> knownValidators) {
    if (!knownValidators.isEmpty()) {
      LOG.debug(
          "Discovered new indices for validators: {}",
          () ->
              knownValidators.entrySet().stream()
                  .map(entry -> entry.getKey().toString() + "=" + entry.getValue())
                  .collect(joining(", ")));
    }
  }

  public Optional<BLSPublicKey> getPublicKey(final Integer index) {
    return validatorIndicesByPublicKey.entrySet().stream()
        .filter(entry -> entry.getValue().equals(index))
        .findFirst()
        .map(Map.Entry::getKey);
  }

  public boolean containsPublicKey(final BLSPublicKey publicKey) {
    return validatorIndicesByPublicKey.containsKey(publicKey);
  }

  public SafeFuture<IntCollection> getValidatorIndices() {
    // Wait for at least one successful load of validator indices before attempting to read
    return firstSuccessfulRequest.thenApply(
        __ ->
            IntArrayList.toList(
                ownedValidators.getValidators().stream()
                    .map(Validator::getPublicKey)
                    .filter(validatorIndicesByPublicKey::containsKey)
                    .mapToInt(validatorIndicesByPublicKey::get)));
  }

  public SafeFuture<Map<BLSPublicKey, Integer>> getValidatorIndicesByPublicKey() {
    // Wait for at least one successful load of validator indices before attempting to read
    return firstSuccessfulRequest.thenApply(
        __ ->
            ownedValidators.getValidators().stream()
                .map(Validator::getPublicKey)
                .filter(validatorIndicesByPublicKey::containsKey)
                .collect(Collectors.toMap(Function.identity(), validatorIndicesByPublicKey::get)));
  }
}
