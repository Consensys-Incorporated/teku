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
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

// Intended to use only in `LocalSlashingProtectorConcurrentAccess`
class LocalSlashingProtectionRecord {
  private final Path slashingProtectedPath;
  // In the same way as the MAP in LocalSlashingProtector, signingRecord gets maintained over time
  private ValidatorSigningRecord signingRecord;
  private final boolean isNew;

  private final ReentrantLock lock;

  LocalSlashingProtectionRecord(
      final Path slashingProtectedPath,
      final ValidatorSigningRecord signingRecord,
      final boolean isNew,
      final ReentrantLock lock) {
    this.slashingProtectedPath = slashingProtectedPath;
    this.signingRecord = signingRecord;
    this.isNew = isNew;
    this.lock = lock;
  }

  @VisibleForTesting
  ReentrantLock getLock() {
    return lock;
  }

  void lock() {
    lock.lock();
  }

  void unlock() {
    lock.unlock();
  }

  ValidatorSigningRecord getSigningRecord() {
    return signingRecord;
  }

  boolean isNew() {
    return isNew;
  }

  boolean writeSigningRecord(
      final SyncDataAccessor dataAccessor, final Optional<ValidatorSigningRecord> maybeRecord)
      throws IOException {
    if (maybeRecord.isEmpty()) {
      return false;
    }
    dataAccessor.syncedWrite(slashingProtectedPath, maybeRecord.get().toBytes());
    this.signingRecord = maybeRecord.get();
    return true;
  }

  Optional<ValidatorSigningRecord> maySignBlock(
      final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    return signingRecord.maySignBlock(genesisValidatorsRoot, slot);
  }

  Optional<ValidatorSigningRecord> maySignAttestation(
      final Bytes32 genesisValidatorsRoot, final UInt64 sourceEpoch, final UInt64 targetEpoch) {
    return signingRecord.maySignAttestation(genesisValidatorsRoot, sourceEpoch, targetEpoch);
  }
}
