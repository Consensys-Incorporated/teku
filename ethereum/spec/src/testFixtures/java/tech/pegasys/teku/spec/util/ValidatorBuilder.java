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

package tech.pegasys.teku.spec.util;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class ValidatorBuilder {

  private final DataStructureUtil dataStructureUtil;
  private BLSPublicKey publicKey;
  private Bytes32 withdrawalCredentials;
  private UInt64 effectiveBalance;
  private boolean slashed = false;
  private UInt64 activationEligibilityEpoch = FAR_FUTURE_EPOCH;
  private UInt64 activationEpoch = FAR_FUTURE_EPOCH;
  private UInt64 exitEpoch = FAR_FUTURE_EPOCH;
  private UInt64 withdrawableEpoch = FAR_FUTURE_EPOCH;

  public ValidatorBuilder(final Spec spec, final DataStructureUtil dataStructureUtil) {
    this.dataStructureUtil = dataStructureUtil;
    this.publicKey = dataStructureUtil.randomPublicKey();
    this.withdrawalCredentials = dataStructureUtil.randomBytes32();
    this.effectiveBalance = spec.getGenesisSpec().getConfig().getMaxEffectiveBalance();
  }

  public ValidatorBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public ValidatorBuilder withdrawalCredentials(final Bytes32 withdrawalCredentials) {
    this.withdrawalCredentials = withdrawalCredentials;
    return this;
  }

  public ValidatorBuilder withRandomEth1WithdrawalCredentials() {
    this.withdrawalCredentials = dataStructureUtil.randomEth1WithdrawalCredentials();
    return this;
  }

  public ValidatorBuilder withRandomCompoundingWithdrawalCredentials() {
    this.withdrawalCredentials = dataStructureUtil.randomCompoundingWithdrawalCredentials();
    return this;
  }

  public ValidatorBuilder withRandomBlsWithdrawalCredentials() {
    this.withdrawalCredentials = dataStructureUtil.randomBlsWithdrawalCredentials();
    return this;
  }

  public ValidatorBuilder effectiveBalance(final UInt64 effectiveBalance) {
    this.effectiveBalance = effectiveBalance;
    return this;
  }

  public ValidatorBuilder slashed(final boolean slashed) {
    this.slashed = slashed;
    return this;
  }

  public ValidatorBuilder activationEligibilityEpoch(final UInt64 activationEligibilityEpoch) {
    this.activationEligibilityEpoch = activationEligibilityEpoch;
    return this;
  }

  public ValidatorBuilder activationEpoch(final UInt64 activationEpoch) {
    this.activationEpoch = activationEpoch;
    return this;
  }

  public ValidatorBuilder exitEpoch(final UInt64 exitEpoch) {
    this.exitEpoch = exitEpoch;
    return this;
  }

  public ValidatorBuilder withdrawableEpoch(final UInt64 withdrawableEpoch) {
    this.withdrawableEpoch = withdrawableEpoch;
    return this;
  }

  public Validator build() {
    return new Validator(
        publicKey,
        withdrawalCredentials,
        effectiveBalance,
        slashed,
        activationEligibilityEpoch,
        activationEpoch,
        exitEpoch,
        withdrawableEpoch);
  }
}
