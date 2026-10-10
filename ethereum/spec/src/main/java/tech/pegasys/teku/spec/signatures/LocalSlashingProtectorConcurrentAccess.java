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

package tech.pegasys.teku.spec.signatures;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class LocalSlashingProtectorConcurrentAccess implements SlashingProtector {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<BLSPublicKey, LocalSlashingProtectionRecord> records =
      new ConcurrentHashMap<>();
  private final SyncDataAccessor dataAccessor;
  private final Path slashingProtectionBaseDir;
  private final boolean slashingProtectionStrictModeEnabled;

  public LocalSlashingProtectorConcurrentAccess(
      final SyncDataAccessor dataAccessor,
      final Path slashingProtectionBaseDir,
      final boolean slashingProtectionStrictModeEnabled) {
    this.dataAccessor = dataAccessor;
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
    this.slashingProtectionStrictModeEnabled = slashingProtectionStrictModeEnabled;
  }

  @Override
  public SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final Optional<LocalSlashingProtectionRecord> record =
              getSigningRecordForSigning(validator, genesisValidatorsRoot);
          if (record.isEmpty()) {
            return false;
          }
          final LocalSlashingProtectionRecord protectedRecord = record.get();
          protectedRecord.lock();
          try {
            return protectedRecord.writeSigningRecord(
                dataAccessor, protectedRecord.maySignBlock(genesisValidatorsRoot, slot));
          } finally {
            protectedRecord.unlock();
          }
        });
  }

  @Override
  public SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final Optional<LocalSlashingProtectionRecord> record =
              getSigningRecordForSigning(validator, genesisValidatorsRoot);
          if (record.isEmpty()) {
            return false;
          }
          final LocalSlashingProtectionRecord protectedRecord = record.get();
          protectedRecord.lock();
          try {
            return protectedRecord.writeSigningRecord(
                dataAccessor,
                protectedRecord.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch));
          } finally {
            protectedRecord.unlock();
          }
        });
  }

  @Override
  public Optional<ValidatorSigningRecord> getSigningRecord(final BLSPublicKey validator) {
    final Optional<LocalSlashingProtectionRecord> maybeRecord =
        Optional.ofNullable(records.get(validator));
    if (maybeRecord.isEmpty()) {
      // not loaded yet, just get from file if available, can create structure on use.
      return getValidatorSigningRecordFromFile(validator);
    }
    final LocalSlashingProtectionRecord record = maybeRecord.get();
    try {
      record.lock();
      return Optional.of(record.getSigningRecord());
    } finally {
      record.unlock();
    }
  }

  @VisibleForTesting
  Optional<LocalSlashingProtectionRecord> getSigningRecordForSigning(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot) {
    final LocalSlashingProtectionRecord record =
        records.computeIfAbsent(validator, __ -> addRecord(validator, genesisValidatorsRoot));
    if (slashingProtectionStrictModeEnabled && record.isNew()) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  private LocalSlashingProtectionRecord addRecord(
      final BLSPublicKey publicKey, final Bytes32 genesisValidatorsRoot) {
    final Path slashingProtectedPath =
        slashingProtectionBaseDir.resolve(
            publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml");
    final Optional<ValidatorSigningRecord> maybeRecord =
        getValidatorSigningRecordFromFile(publicKey);
    return new LocalSlashingProtectionRecord(
        slashingProtectedPath,
        maybeRecord.orElse(ValidatorSigningRecord.emptySigningRecord(genesisValidatorsRoot)),
        maybeRecord.isEmpty(),
        new ReentrantLock());
  }

  private Optional<ValidatorSigningRecord> getValidatorSigningRecordFromFile(
      final BLSPublicKey publicKey) {

    final Path slashingProtectedPath =
        slashingProtectionBaseDir.resolve(
            publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml");
    try {
      return dataAccessor.read(slashingProtectedPath).map(ValidatorSigningRecord::fromBytes);
    } catch (IOException e) {
      LOG.error("Failed to load validator signing record {}", publicKey, e);
      return Optional.empty();
    }
  }
}
