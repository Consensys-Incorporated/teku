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

package tech.pegasys.teku.spec.datastructures.state.versions.pq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class PqStateContainersTest {

  private final ValidatorPq validator =
      new ValidatorPq(
          Bytes32.repeat((byte) 1),
          Bytes32.repeat((byte) 2),
          Bytes32.repeat((byte) 3),
          UInt64.valueOf(32_000_000_000L),
          false,
          UInt64.valueOf(4),
          UInt64.valueOf(5),
          UInt64.valueOf(6),
          UInt64.valueOf(7));

  private final BuilderPq builder =
      new BuilderPq(
          Bytes32.repeat((byte) 1),
          0,
          Eth1Address.fromHexString("0x8943545177806ed17b9f23f0a21ee5948ecaa776"),
          UInt64.valueOf(10),
          UInt64.valueOf(11),
          UInt64.valueOf(12));

  @Test
  void validatorHasTheSpecLayout() {
    // pubkey, vrf_root, withdrawal_credentials, effective_balance, slashed, 4 epochs
    assertThat(ValidatorPq.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(32 * 3 + 8 + 1 + 8 * 4);
    assertThat(ValidatorPq.SSZ_SCHEMA.sszDeserialize(validator.sszSerialize()))
        .isEqualTo(validator);
    assertThat(validator.getXmssPublicKey()).isEqualTo(Bytes32.repeat((byte) 1));
    assertThat(validator.getVrfRoot()).isEqualTo(Bytes32.repeat((byte) 2));
    assertThat(validator.getWithdrawalCredentials()).isEqualTo(Bytes32.repeat((byte) 3));
    assertThat(validator.getActivationEligibilityEpoch()).isEqualTo(UInt64.valueOf(4));
    assertThat(validator.getActivationEpoch()).isEqualTo(UInt64.valueOf(5));
    assertThat(validator.getExitEpoch()).isEqualTo(UInt64.valueOf(6));
    assertThat(validator.getWithdrawableEpoch()).isEqualTo(UInt64.valueOf(7));
  }

  @Test
  void validatorHasNoBlsKey() {
    assertThatThrownBy(validator::getPublicKey).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(validator::getPubkeyBytes).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void validatorCopiesChangeOnlyTheirField() {
    final ValidatorPq slashed = validator.withSlashed(true).withExitEpoch(UInt64.valueOf(9));
    assertThat(slashed.isSlashed()).isTrue();
    assertThat(slashed.getExitEpoch()).isEqualTo(UInt64.valueOf(9));
    assertThat(slashed.getXmssPublicKey()).isEqualTo(validator.getXmssPublicKey());
    assertThat(slashed.getVrfRoot()).isEqualTo(validator.getVrfRoot());
    assertThat(slashed.getWithdrawableEpoch()).isEqualTo(validator.getWithdrawableEpoch());
    assertThat(
            validator
                .withEffectiveBalance(UInt64.ONE)
                .withActivationEligibilityEpoch(UInt64.ZERO)
                .withActivationEpoch(UInt64.ZERO)
                .withWithdrawableEpoch(UInt64.ZERO)
                .withWithdrawalCredentials(Bytes32.ZERO))
        .isEqualTo(
            new ValidatorPq(
                validator.getXmssPublicKey(),
                validator.getVrfRoot(),
                Bytes32.ZERO,
                UInt64.ONE,
                false,
                UInt64.ZERO,
                UInt64.ZERO,
                validator.getExitEpoch(),
                UInt64.ZERO));
  }

  @Test
  void builderHasTheSpecLayout() {
    // pubkey, version, execution_address, balance, deposit_epoch, withdrawable_epoch
    assertThat(BuilderPq.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(32 + 1 + 20 + 8 * 3);
    assertThat(BuilderPq.SSZ_SCHEMA.sszDeserialize(builder.sszSerialize())).isEqualTo(builder);
    assertThat(builder.getXmssPublicKey()).isEqualTo(Bytes32.repeat((byte) 1));
    assertThatThrownBy(builder::getPublicKey).isInstanceOf(UnsupportedOperationException.class);
    assertThat(builder.copyWithNewBalance(UInt64.ONE).getBalance()).isEqualTo(UInt64.ONE);
    assertThat(builder.copyWithNewWithdrawableEpoch(UInt64.ONE).getWithdrawableEpoch())
        .isEqualTo(UInt64.ONE);
    assertThat(builder.copyWithNewBalance(UInt64.ONE).getXmssPublicKey())
        .isEqualTo(builder.getXmssPublicKey());
  }

  @Test
  void preregistrationsHaveTheSpecLayout() {
    final ValidatorPreregistration validatorPreregistration =
        new ValidatorPreregistration(
            Bytes32.repeat((byte) 1),
            Bytes32.repeat((byte) 2),
            Bytes32.repeat((byte) 3),
            UInt64.valueOf(4));
    assertThat(ValidatorPreregistration.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(32 * 3 + 8);
    assertThat(
            ValidatorPreregistration.SSZ_SCHEMA.sszDeserialize(
                validatorPreregistration.sszSerialize()))
        .isEqualTo(validatorPreregistration);
    assertThat(validatorPreregistration.getVrfRoot()).isEqualTo(Bytes32.repeat((byte) 3));
    assertThat(validatorPreregistration.getExpirySlot()).isEqualTo(UInt64.valueOf(4));

    final BuilderPreregistration builderPreregistration =
        new BuilderPreregistration(
            Bytes32.repeat((byte) 1), Bytes32.repeat((byte) 2), UInt64.valueOf(3));
    assertThat(BuilderPreregistration.SSZ_SCHEMA.getSszFixedPartSize()).isEqualTo(32 * 2 + 8);
    assertThat(
            BuilderPreregistration.SSZ_SCHEMA.sszDeserialize(builderPreregistration.sszSerialize()))
        .isEqualTo(builderPreregistration);
    assertThat(builderPreregistration.getWithdrawalCredentials())
        .isEqualTo(Bytes32.repeat((byte) 2));
    assertThat(builderPreregistration.getExpirySlot()).isEqualTo(UInt64.valueOf(3));
  }
}
