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

import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.containers.Container8;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema8;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPackedViewSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.SszNodeTemplate;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
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

  /**
   * One instance, {@link #SSZ_SCHEMA}, for every fork: the getters of a {@link Validator} read from
   * a supernode's bytes use its field offsets and its node template. A fork that changes the
   * validator needs its own schema and view class.
   */
  public static final class ValidatorSchema
      extends ContainerSchema8<
          Validator,
          SszPublicKey,
          SszBytes32,
          SszUInt64,
          SszBit,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64>
      implements SszPackedViewSchema<Validator> {

    private ValidatorSchema() {
      super(
          "Validator",
          namedSchema(ValidatorFields.PUBKEY, SszPublicKeySchema.INSTANCE),
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
    public Validator createFromBackingNode(final TreeNode node) {
      return new Validator(this, node);
    }

    @Override
    public Validator createFromPackedBytes(final Bytes packed, final int offset) {
      return new Validator(this, packed, offset);
    }
  }

  public static final ValidatorSchema SSZ_SCHEMA = new ValidatorSchema();

  // The byte offsets of the fields in a serialized validator, read by the views created from the
  // packed bytes of the registry
  private static final int PUBKEY_OFFSET = fieldOffset(ValidatorFields.PUBKEY);
  private static final int WITHDRAWAL_CREDENTIALS_OFFSET =
      fieldOffset(ValidatorFields.WITHDRAWAL_CREDENTIALS);
  private static final int EFFECTIVE_BALANCE_OFFSET =
      fieldOffset(ValidatorFields.EFFECTIVE_BALANCE);
  private static final int SLASHED_OFFSET = fieldOffset(ValidatorFields.SLASHED);
  private static final int ACTIVATION_ELIGIBILITY_EPOCH_OFFSET =
      fieldOffset(ValidatorFields.ACTIVATION_ELIGIBILITY_EPOCH);
  private static final int ACTIVATION_EPOCH_OFFSET = fieldOffset(ValidatorFields.ACTIVATION_EPOCH);
  private static final int EXIT_EPOCH_OFFSET = fieldOffset(ValidatorFields.EXIT_EPOCH);
  private static final int WITHDRAWABLE_EPOCH_OFFSET =
      fieldOffset(ValidatorFields.WITHDRAWABLE_EPOCH);

  private static final int SSZ_SIZE = SSZ_SCHEMA.getSszFixedPartSize();
  private static final SszNodeTemplate NODE_TEMPLATE = SszNodeTemplate.createFromType(SSZ_SCHEMA);

  // Set on a view created from the content of a supernode of the registry: the fields are read
  // from these bytes and the backing node is only created when it is asked for
  private final Bytes packed;
  private final int packedOffset;

  private Validator(final ValidatorSchema type, final TreeNode backingNode) {
    super(type, backingNode);
    this.packed = null;
    this.packedOffset = 0;
  }

  private Validator(final ValidatorSchema type, final Bytes packed, final int packedOffset) {
    // No child views are cached: the getters read the bytes
    super(type, IntCache.noop());
    this.packed = packed;
    this.packedOffset = packedOffset;
  }

  /** True for a view that reads its fields from the packed bytes of the registry. */
  @VisibleForTesting
  boolean isPackedView() {
    return packed != null;
  }

  @Override
  protected TreeNode createLazyBackingNode() {
    return new SszSuperNode(0, NODE_TEMPLATE, packed.slice(packedOffset, SSZ_SIZE));
  }

  // The sum of the fixed sizes of the fields before it
  private static int fieldOffset(final ValidatorFields field) {
    final int fieldIndex = SSZ_SCHEMA.getFieldIndex(field);
    int offset = 0;
    for (int i = 0; i < fieldIndex; i++) {
      offset += SSZ_SCHEMA.getChildSchema(i).getSszFixedPartSize();
    }
    return offset;
  }

  private UInt64 packedUInt64(final int fieldOffset) {
    return UInt64.fromLongBits(packed.getLong(packedOffset + fieldOffset, ByteOrder.LITTLE_ENDIAN));
  }

  // Copies, so a value that outlives the view never keeps the supernode's bytes alive
  private byte[] packedBytes(final int fieldOffset, final int length) {
    return packed.slice(packedOffset + fieldOffset, length).toArray();
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
    this.packed = null;
    this.packedOffset = 0;
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
    this.packed = null;
    this.packedOffset = 0;
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
    return packed != null
        ? Bytes48.wrap(packedBytes(PUBKEY_OFFSET, Bytes48.SIZE))
        : getField0().getBytes();
  }

  public BLSPublicKey getPublicKey() {
    return packed != null
        ? BLSPublicKey.fromBytesCompressed(getPubkeyBytes())
        : getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawalCredentials() {
    return packed != null
        ? Bytes32.wrap(packedBytes(WITHDRAWAL_CREDENTIALS_OFFSET, Bytes32.SIZE))
        : getField1().get();
  }

  public UInt64 getEffectiveBalance() {
    return packed != null ? packedUInt64(EFFECTIVE_BALANCE_OFFSET) : getField2().get();
  }

  public boolean isSlashed() {
    return packed != null ? packed.get(packedOffset + SLASHED_OFFSET) != 0 : getField3().get();
  }

  public UInt64 getActivationEligibilityEpoch() {
    return packed != null ? packedUInt64(ACTIVATION_ELIGIBILITY_EPOCH_OFFSET) : getField4().get();
  }

  public UInt64 getActivationEpoch() {
    return packed != null ? packedUInt64(ACTIVATION_EPOCH_OFFSET) : getField5().get();
  }

  public UInt64 getExitEpoch() {
    return packed != null ? packedUInt64(EXIT_EPOCH_OFFSET) : getField6().get();
  }

  public UInt64 getWithdrawableEpoch() {
    return packed != null ? packedUInt64(WITHDRAWABLE_EPOCH_OFFSET) : getField7().get();
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
