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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class LocalSlashingProtector implements SlashingProtector {

  private final Map<BLSPublicKey, ValidatorSigningRecord> signingRecords = new HashMap<>();
  private final Map<BLSPublicKey, Path> slashingProtectionPath = new HashMap<>();
  private final SyncDataAccessor dataAccessor;
  private final Path slashingProtectionBaseDir;

  public LocalSlashingProtector(
      final SyncDataAccessor dataAccessor, final Path slashingProtectionBaseDir) {
    this.dataAccessor = dataAccessor;
    this.slashingProtectionBaseDir = slashingProtectionBaseDir;
  }

  @Override
  public synchronized SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord =
              loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
          return handleResult(validator, signingRecord.maySignBlock(genesisValidatorsRoot, slot));
        });
  }

  @Override
  public synchronized SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch) {
    return SafeFuture.of(
        () -> {
          final ValidatorSigningRecord signingRecord =
              loadOrCreateSigningRecord(validator, genesisValidatorsRoot);
          return handleResult(
              validator,
              signingRecord.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch));
        });
  }

  private Boolean handleResult(
      final BLSPublicKey validator, final Optional<ValidatorSigningRecord> newRecord)
      throws IOException {
    if (newRecord.isEmpty()) {
      return false;
    }
    writeSigningRecord(validator, newRecord.get());
    return true;
  }

  @Override
  public Optional<ValidatorSigningRecord> getSigningRecord(final BLSPublicKey validator)
      throws IOException {
    final ValidatorSigningRecord record = signingRecords.get(validator);
    if (record != null) {
      return Optional.of(record);
    }
    final Optional<ValidatorSigningRecord> loaded =
        dataAccessor.read(validatorRecordPath(validator)).map(ValidatorSigningRecord::fromBytes);
    loaded.ifPresent(signingRecord -> signingRecords.put(validator, signingRecord));
    return loaded;
  }

  @Override
  public synchronized Optional<String> importSigningRecord(
      final BLSPublicKey validator, final Supplier<Optional<String>> recordUpdate) {
    final Optional<String> error = recordUpdate.get();
    if (error.isEmpty()) {
      // the record on disk is now ahead of the cached one, so drop the cached copy
      signingRecords.remove(validator);
    }
    return error;
  }

  private ValidatorSigningRecord loadOrCreateSigningRecord(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot) throws IOException {
    final Optional<ValidatorSigningRecord> record = getSigningRecord(validator);
    return record.orElseGet(
        () -> {
          final ValidatorSigningRecord newRecord =
              ValidatorSigningRecord.emptySigningRecord(genesisValidatorsRoot);
          signingRecords.put(validator, newRecord);
          return newRecord;
        });
  }

  private void writeSigningRecord(final BLSPublicKey validator, final ValidatorSigningRecord record)
      throws IOException {
    dataAccessor.syncedWrite(validatorRecordPath(validator), record.toBytes());
    signingRecords.put(validator, record);
  }

  private Path validatorRecordPath(final BLSPublicKey validator) {
    return slashingProtectionPath.computeIfAbsent(
        validator,
        __ ->
            slashingProtectionBaseDir.resolve(
                validator.toBytesCompressed().toUnprefixedHexString() + ".yml"));
  }
}
