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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.StringValueTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.ValidatorPhase0;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;

/**
 * The JSON shape of a validator in the state, for BLS-keyed and PQ validators alike. They differ
 * only in their key: a BLS validator has a 48-byte {@code pubkey}, while a PQ validator has a
 * 32-byte XMSS {@code pubkey} and a {@code vrf_root}, which is how a parsed validator is told
 * apart.
 */
public class ValidatorTypeDefinition {

  public static final StringValueTypeDefinition<Bytes> VALIDATOR_PUBKEY_TYPE =
      DeserializableTypeDefinition.string(Bytes.class)
          .formatter(Bytes::toHexString)
          .parser(Bytes::fromHexString)
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1a"
                  + "f526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "The validator's public key: a 48-byte BLS public key, or from the PQ fork a 32-byte "
                  + "XMSS public key. Hex encoded with 0x prefix, case insensitive.")
          .format("byte")
          .build();

  public static final DeserializableTypeDefinition<Validator> VALIDATOR_TYPE =
      DeserializableTypeDefinition.object(Validator.class, Builder.class)
          .name("StateValidator")
          .initializer(Builder::new)
          .finisher(Builder::build)
          .withField(
              "pubkey", VALIDATOR_PUBKEY_TYPE, Validator::getSigningKeyBytes, Builder::pubkey)
          .withOptionalField(
              ValidatorPq.VRF_ROOT_FIELD_NAME,
              BYTES32_TYPE,
              ValidatorTypeDefinition::getVrfRoot,
              Builder::vrfRoot)
          .withField(
              "withdrawal_credentials",
              BYTES32_TYPE,
              Validator::getWithdrawalCredentials,
              Builder::withdrawalCredentials)
          .withField(
              "effective_balance",
              UINT64_TYPE,
              Validator::getEffectiveBalance,
              Builder::effectiveBalance)
          .withField("slashed", BOOLEAN_TYPE, Validator::isSlashed, Builder::slashed)
          .withField(
              "activation_eligibility_epoch",
              UINT64_TYPE,
              Validator::getActivationEligibilityEpoch,
              Builder::activationEligibilityEpoch)
          .withField(
              "activation_epoch",
              UINT64_TYPE,
              Validator::getActivationEpoch,
              Builder::activationEpoch)
          .withField("exit_epoch", UINT64_TYPE, Validator::getExitEpoch, Builder::exitEpoch)
          .withField(
              "withdrawable_epoch",
              UINT64_TYPE,
              Validator::getWithdrawableEpoch,
              Builder::withdrawableEpoch)
          .build();

  private static Optional<Bytes32> getVrfRoot(final Validator validator) {
    return validator instanceof ValidatorPq validatorPq
        ? Optional.of(validatorPq.getVrfRoot())
        : Optional.empty();
  }

  static class Builder {
    private Bytes pubkey;
    private Optional<Bytes32> vrfRoot = Optional.empty();
    private Bytes32 withdrawalCredentials;
    private UInt64 effectiveBalance;
    private boolean slashed;
    private UInt64 activationEligibilityEpoch;
    private UInt64 activationEpoch;
    private UInt64 exitEpoch;
    private UInt64 withdrawableEpoch;

    Builder pubkey(final Bytes pubkey) {
      this.pubkey = pubkey;
      return this;
    }

    Builder vrfRoot(final Optional<Bytes32> vrfRoot) {
      this.vrfRoot = vrfRoot;
      return this;
    }

    Builder withdrawalCredentials(final Bytes32 withdrawalCredentials) {
      this.withdrawalCredentials = withdrawalCredentials;
      return this;
    }

    Builder effectiveBalance(final UInt64 effectiveBalance) {
      this.effectiveBalance = effectiveBalance;
      return this;
    }

    Builder slashed(final boolean slashed) {
      this.slashed = slashed;
      return this;
    }

    Builder activationEligibilityEpoch(final UInt64 activationEligibilityEpoch) {
      this.activationEligibilityEpoch = activationEligibilityEpoch;
      return this;
    }

    Builder activationEpoch(final UInt64 activationEpoch) {
      this.activationEpoch = activationEpoch;
      return this;
    }

    Builder exitEpoch(final UInt64 exitEpoch) {
      this.exitEpoch = exitEpoch;
      return this;
    }

    Builder withdrawableEpoch(final UInt64 withdrawableEpoch) {
      this.withdrawableEpoch = withdrawableEpoch;
      return this;
    }

    Validator build() {
      if (vrfRoot.isPresent()) {
        checkArgument(
            pubkey.size() == Bytes32.SIZE, "A PQ validator's XMSS public key must be 32 bytes");
        return new ValidatorPq(
            Bytes32.wrap(pubkey),
            vrfRoot.get(),
            withdrawalCredentials,
            effectiveBalance,
            slashed,
            activationEligibilityEpoch,
            activationEpoch,
            exitEpoch,
            withdrawableEpoch);
      }
      checkArgument(pubkey.size() == Bytes48.SIZE, "A validator's BLS public key must be 48 bytes");
      return new ValidatorPhase0(
          Bytes48.wrap(pubkey),
          withdrawalCredentials,
          effectiveBalance,
          slashed,
          activationEligibilityEpoch,
          activationEpoch,
          exitEpoch,
          withdrawableEpoch);
    }
  }
}
