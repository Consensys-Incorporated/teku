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
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SlashingProtector {
  SafeFuture<Boolean> maySignBlock(
      final BLSPublicKey validator, final Bytes32 genesisValidatorsRoot, final UInt64 slot);

  SafeFuture<Boolean> maySignAttestation(
      final BLSPublicKey validator,
      final Bytes32 genesisValidatorsRoot,
      final UInt64 sourceEpoch,
      final UInt64 targetEpoch);

  Optional<ValidatorSigningRecord> getSigningRecord(final BLSPublicKey validator)
      throws IOException;

  /**
   * Applies an externally sourced update to a validator's signing record, for example an EIP-3076
   * import through the keymanager API, and refreshes any record this protector has cached. The
   * update is applied while signing for that validator is blocked, so a signature in flight cannot
   * write the pre-import record back over the imported one.
   *
   * @param validator the validator whose signing record is being updated
   * @param recordUpdate updates the stored record, returning a description of any failure
   * @return the error reported by recordUpdate, or empty if the update was applied
   */
  Optional<String> importSigningRecord(
      final BLSPublicKey validator, final Supplier<Optional<String>> recordUpdate);
}
