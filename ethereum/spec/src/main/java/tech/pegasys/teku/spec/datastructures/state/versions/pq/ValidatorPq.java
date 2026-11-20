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

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container9;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema9;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.ValidatorFields;

/**
 * A PQ validator: keyed by a 32-byte leanXMSS public key, with the root of its VRF tree at position
 * 1. It has no BLS key.
 */
public class ValidatorPq
    extends Container9<
        ValidatorPq,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszUInt64,
        SszBit,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64>
    implements Validator {

  public static final String VRF_ROOT_FIELD_NAME = "vrf_root";

  public static class ValidatorPqSchema
      extends ContainerSchema9<
          ValidatorPq,
          SszBytes32,
          SszBytes32,
          SszBytes32,
          SszUInt64,
          SszBit,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64> {

    private ValidatorPqSchema() {
      super(
          "ValidatorPq",
          namedSchema(ValidatorFields.PUBKEY, SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(VRF_ROOT_FIELD_NAME, SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(ValidatorFields.WITHDRAWAL_CREDENTIALS, SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema(ValidatorFields.EFFECTIVE_BALANCE, SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema(ValidatorFields.SLASHED, SszPrimitiveSchemas.BIT_SCHEMA),
          namedSchema(
              ValidatorFields.ACTIVATION_ELIGIBILITY_EPOCH, SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema(ValidatorFields.ACTIVATION_EPOCH, SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema(ValidatorFields.EXIT_EPOCH, SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema(ValidatorFields.WITHDRAWABLE_EPOCH, SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public ValidatorPq createFromBackingNode(final TreeNode node) {
      return new ValidatorPq(this, node);
    }
  }

  public static final ValidatorPqSchema SSZ_SCHEMA = new ValidatorPqSchema();

  private ValidatorPq(final ValidatorPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ValidatorPq(
      final Bytes32 xmssPublicKey,
      final Bytes32 vrfRoot,
      final Bytes32 withdrawalCredentials,
      final UInt64 effectiveBalance,
      final boolean slashed,
      final UInt64 activationEligibilityEpoch,
      final UInt64 activationEpoch,
      final UInt64 exitEpoch,
      final UInt64 withdrawableEpoch) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(xmssPublicKey),
        SszBytes32.of(vrfRoot),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(effectiveBalance),
        SszBit.of(slashed),
        SszUInt64.of(activationEligibilityEpoch),
        SszUInt64.of(activationEpoch),
        SszUInt64.of(exitEpoch),
        SszUInt64.of(withdrawableEpoch));
  }

  /** The leanXMSS public key: the Merkle root followed by the public parameter. */
  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  public Bytes32 getVrfRoot() {
    return getField1().get();
  }

  /** A PQ validator has no BLS key. */
  @Override
  public Bytes48 getPubkeyBytes() {
    throw new UnsupportedOperationException("A PQ validator has no BLS public key");
  }

  /** A PQ validator has no BLS key. */
  @Override
  public BLSPublicKey getPublicKey() {
    throw new UnsupportedOperationException("A PQ validator has no BLS public key");
  }

  @Override
  public Bytes32 getWithdrawalCredentials() {
    return getField2().get();
  }

  @Override
  public UInt64 getEffectiveBalance() {
    return getField3().get();
  }

  @Override
  public boolean isSlashed() {
    return getField4().get();
  }

  @Override
  public UInt64 getActivationEligibilityEpoch() {
    return getField5().get();
  }

  @Override
  public UInt64 getActivationEpoch() {
    return getField6().get();
  }

  @Override
  public UInt64 getExitEpoch() {
    return getField7().get();
  }

  @Override
  public UInt64 getWithdrawableEpoch() {
    return getField8().get();
  }

  @Override
  public ValidatorPq withEffectiveBalance(final UInt64 effectiveBalance) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        effectiveBalance,
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPq withSlashed(final boolean slashed) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        slashed,
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPq withActivationEligibilityEpoch(final UInt64 activationEligibilityEpoch) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        activationEligibilityEpoch,
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPq withActivationEpoch(final UInt64 activationEpoch) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        activationEpoch,
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPq withExitEpoch(final UInt64 exitEpoch) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        exitEpoch,
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPq withWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        withdrawableEpoch);
  }

  @Override
  public ValidatorPq withWithdrawalCredentials(final Bytes32 withdrawalCredentials) {
    return new ValidatorPq(
        getXmssPublicKey(),
        getVrfRoot(),
        withdrawalCredentials,
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPqSchema getSchema() {
    return (ValidatorPqSchema) super.getSchema();
  }
}
