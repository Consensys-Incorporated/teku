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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ValidatorTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final int seed = 100;
  private final Bytes48 pubkey = BLSTestUtil.randomPublicKey(seed).toBytesCompressed();
  private final Bytes32 withdrawalCredentials = dataStructureUtil.randomBytes32();
  private final UInt64 activationEligibilityEpoch = dataStructureUtil.randomUInt64();
  private final UInt64 activationEpoch = dataStructureUtil.randomUInt64();
  private final UInt64 exitEpoch = dataStructureUtil.randomUInt64();
  private final UInt64 withdrawalEpoch = dataStructureUtil.randomUInt64();
  private final boolean slashed = false;
  private final UInt64 effectiveBalance = dataStructureUtil.randomUInt64();

  private final Validator validator =
      new Validator(
          pubkey,
          withdrawalCredentials,
          effectiveBalance,
          slashed,
          activationEligibilityEpoch,
          activationEpoch,
          exitEpoch,
          withdrawalEpoch);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    Validator testValidator = validator;

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenPubkeysAreDifferent() {
    Bytes48 differentPublicKey = BLSTestUtil.randomPublicKey(99).toBytesCompressed();
    Validator testValidator =
        new Validator(
            differentPublicKey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(pubkey, differentPublicKey);
    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalCredentialsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials.not(),
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenActivationEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch.plus(dataStructureUtil.randomUInt64()),
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenExitEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch.plus(dataStructureUtil.randomUInt64()),
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenWithdrawalEpochsAreDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch.plus(dataStructureUtil.randomUInt64()));

    assertNotEquals(validator, testValidator);
  }

  @Test
  void equalsReturnsFalseWhenInitiatedExitIsDifferent() {
    Validator testValidator =
        new Validator(
            pubkey,
            withdrawalCredentials,
            effectiveBalance,
            !slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawalEpoch);

    assertNotEquals(validator, testValidator);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszValidatorBytes = validator.sszSerialize();
    assertEquals(validator, Validator.SSZ_SCHEMA.sszDeserialize(sszValidatorBytes));
  }
}
