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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class ValidatorStatusLogger implements ValidatorTimingChannel {
  private static final int VALIDATOR_KEYS_PRINT_LIMIT = 20;

  final OwnedValidators validators;
  final AtomicReference<Map<BLSPublicKey, ValidatorStatus>> latestValidatorStatuses =
      new AtomicReference<>();

  public ValidatorStatusLogger(final OwnedValidators validators) {
    this.validators = validators;
  }

  @Override
  public void onUpdatedValidatorStatuses(
      final Map<BLSPublicKey, ValidatorStatus> newValidatorStatuses,
      final boolean possibleMissingEvents) {
    final Map<BLSPublicKey, ValidatorStatus> oldValidatorStatuses =
        latestValidatorStatuses.getAndSet(newValidatorStatuses);
    // first run
    if (oldValidatorStatuses == null) {
      if (validators.getValidatorCount() < VALIDATOR_KEYS_PRINT_LIMIT) {
        printValidatorStatusesOneByOne(newValidatorStatuses);
      } else {
        printValidatorStatusSummary(newValidatorStatuses);
      }
      return;
    }

    // updates
    for (final Map.Entry<BLSPublicKey, ValidatorStatus> entry : newValidatorStatuses.entrySet()) {
      final BLSPublicKey key = entry.getKey();
      final ValidatorStatus newStatus = entry.getValue();
      final ValidatorStatus oldStatus = oldValidatorStatuses.get(key);

      // report the status of a new validator
      if (oldStatus == null) {
        STATUS_LOG.validatorStatus(key.toAbbreviatedString(), newStatus.name());
        continue;
      }
      if (oldStatus.equals(newStatus)) {
        continue;
      }
      STATUS_LOG.validatorStatusChange(
          oldStatus.name(), newStatus.name(), key.toAbbreviatedString());
    }
  }

  private void printValidatorStatusesOneByOne(
      final Map<BLSPublicKey, ValidatorStatus> validatorStatuses) {
    for (final BLSPublicKey publicKey : validators.getPublicKeys()) {
      final Optional<ValidatorStatus> maybeValidatorStatus =
          Optional.ofNullable(validatorStatuses.get(publicKey));
      maybeValidatorStatus.ifPresentOrElse(
          validatorStatus ->
              STATUS_LOG.validatorStatus(publicKey.toAbbreviatedString(), validatorStatus.name()),
          () -> STATUS_LOG.unableToRetrieveValidatorStatus(publicKey.toAbbreviatedString()));
    }
  }

  private void printValidatorStatusSummary(
      final Map<BLSPublicKey, ValidatorStatus> validatorStatuses) {
    final Map<ValidatorStatus, AtomicInteger> validatorStatusCount = new HashMap<>();
    final AtomicInteger unknownValidatorCountReference = new AtomicInteger(0);
    for (final BLSPublicKey publicKey : validators.getPublicKeys()) {
      final Optional<ValidatorStatus> maybeValidatorStatus =
          Optional.ofNullable(validatorStatuses.get(publicKey));
      maybeValidatorStatus.ifPresentOrElse(
          status -> {
            final AtomicInteger count =
                validatorStatusCount.computeIfAbsent(status, __ -> new AtomicInteger(0));
            count.incrementAndGet();
          },
          unknownValidatorCountReference::incrementAndGet);
    }

    for (final Map.Entry<ValidatorStatus, AtomicInteger> statusCount :
        validatorStatusCount.entrySet()) {
      STATUS_LOG.validatorStatusSummary(statusCount.getValue().get(), statusCount.getKey().name());
    }

    final int unknownValidatorCount = unknownValidatorCountReference.get();
    if (unknownValidatorCount > 0) {
      STATUS_LOG.unableToRetrieveValidatorStatusSummary(unknownValidatorCountReference.get());
    }
  }
}
