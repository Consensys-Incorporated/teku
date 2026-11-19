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

package tech.pegasys.teku.spec.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * A validator in the registry. {@link ValidatorPhase0} is the BLS-keyed validator of every fork up
 * to PQ; {@link tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq} is keyed by a
 * leanXMSS public key and has no BLS key, so its BLS getters throw.
 */
public interface Validator extends SszContainer {

  /**
   * Returns compressed BLS public key bytes
   *
   * <p>{@link BLSPublicKey} instance can be created with {@link
   * BLSPublicKey#fromBytesCompressed(Bytes48)} method. However this method is pretty 'expensive'
   * and the preferred way would be to use {@link Spec#getValidatorPubKey(BeaconState, UInt64)} if
   * the {@link BeaconState} instance and validator index is available
   *
   * @throws UnsupportedOperationException for a PQ validator, which has no BLS key
   */
  Bytes48 getPubkeyBytes();

  /**
   * @throws UnsupportedOperationException for a PQ validator, which has no BLS key
   */
  BLSPublicKey getPublicKey();

  Bytes32 getWithdrawalCredentials();

  UInt64 getEffectiveBalance();

  boolean isSlashed();

  UInt64 getActivationEligibilityEpoch();

  UInt64 getActivationEpoch();

  UInt64 getExitEpoch();

  UInt64 getWithdrawableEpoch();

  Validator withEffectiveBalance(UInt64 effectiveBalance);

  Validator withSlashed(boolean slashed);

  Validator withActivationEligibilityEpoch(UInt64 activationEligibilityEpoch);

  Validator withActivationEpoch(UInt64 activationEpoch);

  Validator withExitEpoch(UInt64 exitEpoch);

  Validator withWithdrawableEpoch(UInt64 withdrawableEpoch);

  Validator withWithdrawalCredentials(Bytes32 withdrawalCredentials);
}
