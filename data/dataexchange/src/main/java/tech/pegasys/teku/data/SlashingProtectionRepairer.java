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

package tech.pegasys.teku.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.PublicKeyException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SlashingProtectionRepairer {
  private final List<SigningHistory> signingHistoryList = new ArrayList<>();
  private final Set<String> invalidRecords = new HashSet<>();
  private final SyncDataAccessor syncDataAccessor;
  private final List<String> errorList = new ArrayList<>();
  private final List<BLSPublicKey> keysWithoutRecords = new ArrayList<>();
  private final SubCommandLogger log;
  private Path slashingProtectionPath;
  private final boolean updateAllEnabled;

  public SlashingProtectionRepairer(
      final SubCommandLogger log, final boolean updateAllEnabled, final Path path) {
    this.log = log;
    this.updateAllEnabled = updateAllEnabled;
    this.syncDataAccessor = SyncDataAccessor.create(path);
  }

  public static SlashingProtectionRepairer create(
      final SubCommandLogger subCommandLog,
      final Path slashProtectionPath,
      final boolean updateAllEnabled) {
    return create(subCommandLog, slashProtectionPath, updateAllEnabled, List.of());
  }

  /**
   * Reads the slashing protection records, and finds known validator keys that have no record.
   *
   * <p>A key without a record file is treated by the validator client as never having signed, so it
   * is unprotected. When update all is enabled, a record is created for each of these keys.
   * Otherwise they are only reported, because a key that has not signed yet has no file either.
   *
   * @param knownValidatorKeys the validator keys to check for missing records
   */
  public static SlashingProtectionRepairer create(
      final SubCommandLogger subCommandLog,
      final Path slashProtectionPath,
      final boolean updateAllEnabled,
      final Collection<BLSPublicKey> knownValidatorKeys) {
    final SlashingProtectionRepairer repairer =
        new SlashingProtectionRepairer(subCommandLog, updateAllEnabled, slashProtectionPath);
    repairer.initialise(slashProtectionPath);
    repairer.findKeysWithoutRecords(knownValidatorKeys);
    return repairer;
  }

  private void initialise(final Path slashProtectionPath) {
    this.slashingProtectionPath = slashProtectionPath;
    final File[] slashingProtectionRecords = slashProtectionPath.toFile().listFiles();
    if (slashingProtectionRecords == null) {
      return;
    }
    Arrays.stream(slashingProtectionRecords)
        .filter(file -> file.isFile() && file.getName().endsWith(".yml"))
        .forEach(this::readSlashProtectionFile);
  }

  private void findKeysWithoutRecords(final Collection<BLSPublicKey> knownValidatorKeys) {
    knownValidatorKeys.stream()
        .distinct()
        .filter(pubkey -> !Files.exists(recordPath(toDisplayString(pubkey))))
        .forEach(keysWithoutRecords::add);
    if (keysWithoutRecords.isEmpty()) {
      return;
    }
    keysWithoutRecords.forEach(
        pubkey -> log.display(toDisplayString(pubkey) + ": no slashing protection record"));
    final boolean single = keysWithoutRecords.size() == 1;
    log.display(
        keysWithoutRecords.size()
            + " validator "
            + (single ? "key has" : "keys have")
            + " no slashing protection record, so the validator client will treat"
            + (single ? " it" : " them")
            + " as never having signed. "
            + (updateAllEnabled
                ? "--update-all-enabled will create the missing "
                    + (single ? "record." : "records.")
                : "--update-all-enabled would create the missing "
                    + (single ? "record." : "records.")));
  }

  private void readSlashProtectionFile(final File file) {
    final String filePrefix =
        file.getName().substring(0, file.getName().length() - ".yml".length());
    try {
      final BLSPublicKey pubkey = parsePublicKey(filePrefix);

      final Optional<ValidatorSigningRecord> maybeRecord =
          syncDataAccessor.read(file.toPath()).map(ValidatorSigningRecord::fromBytes);
      if (maybeRecord.isEmpty() && invalidRecords.add(filePrefix)) {
        log.display(filePrefix + ": Empty slashing protection record");
        return;
      }

      if (updateAllEnabled) {
        log.display(filePrefix + ": looks valid");
      }
      final ValidatorSigningRecord validatorSigningRecord = maybeRecord.get();
      signingHistoryList.add(SigningHistory.createSigningHistory(pubkey, validatorSigningRecord));

    } catch (PublicKeyException e) {
      log.display(" --- " + file.getName() + " - invalid public key - ignoring file");
    } catch (Exception e) {
      if (invalidRecords.add(filePrefix)) {
        log.display(filePrefix + ": Incomplete or invalid slashing protection data");
      }
    }
  }

  static BLSPublicKey parsePublicKey(final String value) {
    try {
      return BLSPublicKey.fromHexString(value);
    } catch (IllegalArgumentException e) {
      throw new PublicKeyException(
          String.format("Public key %s is invalid: %s", value, e.getMessage()), e);
    }
  }

  public void updateRecords(final UInt64 slot, final UInt64 epoch) {
    if (updateAllEnabled) {
      keysWithoutRecords.forEach(
          pubkey ->
              writeValidatorSigningRecord(
                  updateSigningRecord(slot, epoch, Optional.empty()), toDisplayString(pubkey)));
    }
    invalidRecords.forEach(
        pubkey ->
            writeValidatorSigningRecord(
                new ValidatorSigningRecord(Optional.empty(), slot, epoch, epoch), pubkey));
    if (updateAllEnabled) {
      signingHistoryList.forEach(
          (historyRecord) -> updateValidatorSigningRecord(slot, epoch, historyRecord));
    }
    displayUpdateErrors();
  }

  private void updateValidatorSigningRecord(
      final UInt64 slot, final UInt64 epoch, final SigningHistory historyRecord) {
    final ValidatorSigningRecord currentRecord =
        historyRecord.toValidatorSigningRecord(Optional.empty(), null);
    final ValidatorSigningRecord updatedRecord =
        updateSigningRecord(slot, epoch, Optional.of(currentRecord));
    if (!currentRecord.equals(updatedRecord)) {
      writeValidatorSigningRecord(updatedRecord, toDisplayString(historyRecord.pubkey()));
    }
  }

  private void writeValidatorSigningRecord(
      final ValidatorSigningRecord updatedRecord, final String pubkey) {
    log.display(pubkey + ": updating");
    final Path outputFile = recordPath(pubkey);
    try {
      syncDataAccessor.syncedWrite(outputFile, updatedRecord.toBytes());
    } catch (IOException e) {
      errorList.add(pubkey + ": update failed, " + e.getMessage());
    }
  }

  private Path recordPath(final String pubkey) {
    return slashingProtectionPath.resolve(pubkey + ".yml");
  }

  private String toDisplayString(final BLSPublicKey pubkey) {
    return pubkey.toBytesCompressed().toUnprefixedHexString().toLowerCase(Locale.ROOT);
  }

  private void displayUpdateErrors() {
    if (errorList.isEmpty()) {
      return;
    }

    log.display("");
    log.display("The following errors were encountered:");
    for (String err : errorList) {
      log.display(err);
    }
  }

  public boolean hasUpdates() {
    if (getRecordsToCreateCount() > 0) {
      return true;
    }
    if (updateAllEnabled) {
      return signingHistoryList.size() + invalidRecords.size() > 0;
    }
    return !invalidRecords.isEmpty();
  }

  /**
   * @return the number of record files that {@link #updateRecords} will create
   */
  public int getRecordsToCreateCount() {
    return updateAllEnabled ? keysWithoutRecords.size() : 0;
  }

  /**
   * @return the confirmation line stating how many record files will be created
   */
  public static String describeRecordsToCreate(final int count) {
    return count
        + " new slashing protection "
        + (count == 1 ? "record" : "records")
        + " will be created for validator keys that don't have one.";
  }

  static ValidatorSigningRecord updateSigningRecord(
      final UInt64 blockSlot,
      final UInt64 attestationEpoch,
      final Optional<ValidatorSigningRecord> maybeRecord) {
    final UInt64 sourceEpoch =
        maybeRecord
            .map(ValidatorSigningRecord::attestationSourceEpoch)
            .orElse(attestationEpoch)
            .max(attestationEpoch);
    final UInt64 targetEpoch =
        maybeRecord
            .map(ValidatorSigningRecord::attestationTargetEpoch)
            .orElse(attestationEpoch)
            .max(attestationEpoch);
    final UInt64 slot =
        maybeRecord.map(ValidatorSigningRecord::blockSlot).orElse(blockSlot).max(blockSlot);
    final Optional<Bytes32> maybeGenesisRoot =
        maybeRecord.isPresent() ? maybeRecord.get().genesisValidatorsRoot() : Optional.empty();
    return new ValidatorSigningRecord(maybeGenesisRoot, slot, sourceEpoch, targetEpoch);
  }
}
