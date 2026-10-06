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

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ProposerConfigPropertiesProvider {

  Optional<Eth1Address> getFeeRecipient(BLSPublicKey publicKey);

  UInt64 getGasLimit(BLSPublicKey publicKey);

  /**
   * The gas limit to use for a duty in the given epoch, which may differ from the one for the
   * current epoch when the network schedules a gas limit change (EIP-8261).
   */
  UInt64 getGasLimit(BLSPublicKey publicKey, UInt64 epoch);

  Optional<UInt64> getBuilderRegistrationTimestampOverride(BLSPublicKey publicKey);

  Optional<BLSPublicKey> getBuilderRegistrationPublicKeyOverride(BLSPublicKey publicKey);

  boolean isBuilderEnabled(BLSPublicKey publicKey);

  SafeFuture<Void> refresh();

  boolean isReadyToProvideProperties();
}
