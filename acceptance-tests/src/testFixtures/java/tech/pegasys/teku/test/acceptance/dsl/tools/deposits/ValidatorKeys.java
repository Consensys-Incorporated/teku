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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import static tech.pegasys.teku.spec.datastructures.util.DepositGenerator.createWithdrawalCredentials;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;

public class ValidatorKeys {
  private final BLSKeyPair validatorKey;

  private final boolean locked;
  private final Bytes32 withdrawalCredentials;
  private final Optional<BLSKeyPair> withdrawalKey;

  public ValidatorKeys(
      final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey, final boolean locked) {
    this.validatorKey = validatorKey;
    this.withdrawalCredentials = createWithdrawalCredentials(withdrawalKey.getPublicKey());
    this.withdrawalKey = Optional.of(withdrawalKey);
    this.locked = locked;
  }

  public ValidatorKeys(
      final BLSKeyPair validatorKey, final Bytes32 withdrawalCredentials, final boolean locked) {
    this.validatorKey = validatorKey;
    this.withdrawalCredentials = withdrawalCredentials;
    this.withdrawalKey = Optional.empty();
    this.locked = locked;
  }

  public BLSKeyPair getValidatorKey() {
    return validatorKey;
  }

  public Bytes32 getWithdrawalCredentials() {
    return withdrawalCredentials;
  }

  public boolean isLocked() {
    return locked;
  }

  public Optional<BLSKeyPair> getWithdrawalKey() {
    return withdrawalKey;
  }
}
