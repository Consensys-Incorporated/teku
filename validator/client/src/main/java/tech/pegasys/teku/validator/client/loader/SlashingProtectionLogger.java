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

package tech.pegasys.teku.validator.client.loader;

import static tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord.NEVER_SIGNED;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.Validator;

public class SlashingProtectionLogger implements ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 SAFE_PROTECTION_EPOCHS_DELTA = UInt64.valueOf(200);
  private Optional<UInt64> currentSlot = Optional.empty();
  private Optional<List<Validator>> activeValidators = Optional.empty();

  private final SlashingProtector slashingProtector;
  private final ValidatorLogger validatorLogger;
  private final Spec spec;
  private final AsyncRunner asyncRunner;

  public SlashingProtectionLogger(
      final SlashingProtector slashingProtector,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final ValidatorLogger validatorLogger) {
    this.slashingProtector = slashingProtector;
    this.validatorLogger = validatorLogger;
    this.asyncRunner = asyncRunner;
    this.spec = spec;
  }

  public synchronized void protectionSummary(final List<Validator> validators) {
    this.activeValidators = Optional.of(validators);
    tryToLog();
  }

  private void tryToLog() {
    if (currentSlot.isEmpty() || activeValidators.isEmpty()) {
      return;
    }
    final List<Validator> validators = new ArrayList<>(this.activeValidators.get());
    asyncRunner
        .runAsync(() -> logSlashingProtection(validators, this.currentSlot.get()))
        .finish(ex -> LOG.error("Failed to log validators slashing protection summary", ex));
    this.activeValidators = Optional.empty();
  }

  private void logSlashingProtection(final List<Validator> validators, final UInt64 currentSlot) {
    final List<Pair<Validator, Optional<ValidatorSigningRecord>>> validatorRecords =
        getValidatorSigningRecords(validators);
    final List<Pair<Validator, ValidatorSigningRecord>> protectedList =
        validatorRecords.stream()
            .filter(pair -> pair.getRight().isPresent())
            .map(pair -> Pair.of(pair.getLeft(), pair.getRight().get()))
            .toList();
    logLoadedProtectionValidators(protectedList);
    filterAndLogNotLoadedProtectionValidators(validatorRecords);
    Function<ValidatorSigningRecord, Boolean> outdatedSigningRecordClassifier =
        createOutdatedSigningRecordClassifier(currentSlot);
    final List<Pair<Validator, ValidatorSigningRecord>> outdatedProtectionList =
        protectedList.stream()
            .filter(pair -> outdatedSigningRecordClassifier.apply(pair.getRight()))
            .toList();
    logOutdatedProtectedValidators(outdatedProtectionList);
  }

  private void logLoadedProtectionValidators(
      final List<Pair<Validator, ValidatorSigningRecord>> validatorRecords) {
    if (validatorRecords.isEmpty()) {
      return;
    }
    validatorLogger.loadedSlashingProtection(
        validatorRecords.stream()
            .map(pair -> pair.getLeft().getPublicKey().toAbbreviatedString())
            .collect(Collectors.toSet()));
  }

  private void logOutdatedProtectedValidators(
      final List<Pair<Validator, ValidatorSigningRecord>> validatorRecords) {
    if (validatorRecords.isEmpty()) {
      return;
    }
    validatorLogger.outdatedSlashingProtection(
        validatorRecords.stream()
            .map(pair -> pair.getLeft().getPublicKey().toAbbreviatedString())
            .collect(Collectors.toSet()),
        SAFE_PROTECTION_EPOCHS_DELTA);
  }

  private void filterAndLogNotLoadedProtectionValidators(
      final List<Pair<Validator, Optional<ValidatorSigningRecord>>> validatorRecords) {
    Set<String> unprotectedValidatorSet =
        validatorRecords.stream()
            .filter(pair -> pair.getRight().isEmpty())
            .map(pair -> pair.getLeft().getPublicKey().toAbbreviatedString())
            .collect(Collectors.toSet());
    if (!unprotectedValidatorSet.isEmpty()) {
      validatorLogger.notLoadedSlashingProtection(unprotectedValidatorSet);
    }
  }

  private List<Pair<Validator, Optional<ValidatorSigningRecord>>> getValidatorSigningRecords(
      final List<Validator> validators) {
    List<Pair<Validator, Optional<ValidatorSigningRecord>>> validatorRecords = new ArrayList<>();
    for (Validator validator : validators) {
      try {
        Optional<ValidatorSigningRecord> validatorSigningRecord =
            slashingProtector.getSigningRecord(validator.getPublicKey());
        validatorRecords.add(Pair.of(validator, validatorSigningRecord));
      } catch (IOException e) {
        LOG.error("Failed to retrieve all validators slashing protection data", e);
        break;
      }
    }
    return validatorRecords;
  }

  private Function<ValidatorSigningRecord, Boolean> createOutdatedSigningRecordClassifier(
      final UInt64 currentSlot) {
    return signingRecord -> {
      final UInt64 attestationTargetEpoch = signingRecord.attestationTargetEpoch();
      return spec.computeEpochAtSlot(currentSlot)
          .minusMinZero(
              Objects.equals(attestationTargetEpoch, NEVER_SIGNED)
                  ? GENESIS_EPOCH
                  : attestationTargetEpoch)
          .isGreaterThan(SAFE_PROTECTION_EPOCHS_DELTA);
    };
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    this.currentSlot = Optional.of(slot);
    tryToLog();
  }
}
