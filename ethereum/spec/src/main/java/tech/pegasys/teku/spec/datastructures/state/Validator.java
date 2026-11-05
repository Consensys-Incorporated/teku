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
import tech.pegasys.teku.infrastructure.ssz.containers.Container8;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszBooleanField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszByteVectorField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUInt64Field;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class Validator
    extends Container8<
        Validator,
        SszPublicKey,
        SszBytes32,
        SszUInt64,
        SszBit,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64> {

  public static class ValidatorSchema
      extends ContainerSchema8<
          Validator,
          SszPublicKey,
          SszBytes32,
          SszUInt64,
          SszBit,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64> {

    public ValidatorSchema() {
      super(
          "Validator",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("effective_balance", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("slashed", SszPrimitiveSchemas.BIT_SCHEMA),
          namedSchema("activation_eligibility_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("activation_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("exit_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("withdrawable_epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public Validator createFromBackingNode(final TreeNode node) {
      return new Validator(this, node);
    }
  }

  public static final ValidatorSchema SSZ_SCHEMA = new ValidatorSchema();

  // Handles on the validator fields, for scans reading the serialized registry without views (see
  // SszList#forEachElementSsz). Epochs and balances read as raw uint64 bits: compare them unsigned.
  public static final SszByteVectorField<Validator> PUBKEY =
      SSZ_SCHEMA.byteVectorField("pubkey", Bytes48.SIZE);
  public static final SszByteVectorField<Validator> WITHDRAWAL_CREDENTIALS =
      SSZ_SCHEMA.byteVectorField("withdrawal_credentials", Bytes32.SIZE);
  public static final SszUInt64Field<Validator> EFFECTIVE_BALANCE =
      SSZ_SCHEMA.uint64Field("effective_balance");
  public static final SszBooleanField<Validator> SLASHED = SSZ_SCHEMA.booleanField("slashed");
  public static final SszUInt64Field<Validator> ACTIVATION_ELIGIBILITY_EPOCH =
      SSZ_SCHEMA.uint64Field("activation_eligibility_epoch");
  public static final SszUInt64Field<Validator> ACTIVATION_EPOCH =
      SSZ_SCHEMA.uint64Field("activation_epoch");
  public static final SszUInt64Field<Validator> EXIT_EPOCH = SSZ_SCHEMA.uint64Field("exit_epoch");
  public static final SszUInt64Field<Validator> WITHDRAWABLE_EPOCH =
      SSZ_SCHEMA.uint64Field("withdrawable_epoch");

  private Validator(final ValidatorSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Validator(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 effectiveBalance,
      final boolean slashed,
      final UInt64 activationEligibilityEpoch,
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 withdrawableEpoch) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(effectiveBalance),
        SszBit.of(slashed),
        SszUInt64.of(activationEligibilityEpoch),
        SszUInt64.of(activationEpoch),
        SszUInt64.of(exitEpoch),
        SszUInt64.of(withdrawableEpoch));
  }

  public Validator(
      final Bytes48 pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 effectiveBalance,
      final boolean slashed,
      final UInt64 activationEligibilityEpoch,
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 withdrawableEpoch) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(effectiveBalance),
        SszBit.of(slashed),
        SszUInt64.of(activationEligibilityEpoch),
        SszUInt64.of(activationEpoch),
        SszUInt64.of(exitEpoch),
        SszUInt64.of(withdrawableEpoch));
  }

  /**
   * Returns compressed BLS public key bytes
   *
   * <p>{@link BLSPublicKey} instance can be created with {@link
   * BLSPublicKey#fromBytesCompressed(Bytes48)} method. However this method is pretty 'expensive'
   * and the preferred way would be to use {@link Spec#getValidatorPubKey(BeaconState, UInt64)} if
   * the {@link BeaconState} instance and validator index is available
   */
  public Bytes48 getPubkeyBytes() {
    return getField0().getBytes();
  }

  public BLSPublicKey getPublicKey() {
    return getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  public UInt64 getEffectiveBalance() {
    return getField2().get();
  }

  public boolean isSlashed() {
    return getField3().get();
  }

  public UInt64 getActivationEligibilityEpoch() {
    return getField4().get();
  }

  public UInt64 getActivationEpoch() {
    return getField5().get();
  }

  public UInt64 getExitEpoch() {
    return getField6().get();
  }

  public UInt64 getWithdrawableEpoch() {
    return getField7().get();
  }

  public Validator withEffectiveBalance(final UInt64 effectiveBalance) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        effectiveBalance,
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  public Validator withSlashed(final boolean slashed) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        slashed,
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  public Validator withActivationEligibilityEpoch(final UInt64 activationEligibilityEpoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        activationEligibilityEpoch,
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  public Validator withActivationEpoch(final UInt64 activationEpoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        activationEpoch,
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  public Validator withExitEpoch(final UInt64 exitEpoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        exitEpoch,
        getWithdrawableEpoch());
  }

  public Validator withWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new Validator(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        withdrawableEpoch);
  }

  public Validator withWithdrawalCredentials(final Bytes32 withdrawalCredentials) {
    return new Validator(
        getPubkeyBytes(),
        withdrawalCredentials,
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }
}
