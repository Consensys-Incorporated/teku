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
import tech.pegasys.teku.infrastructure.ssz.SszData;
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

public class ValidatorPhase0
    extends Container8<
        ValidatorPhase0,
        SszPublicKey,
        SszBytes32,
        SszUInt64,
        SszBit,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64>
    implements Validator {

  /**
   * One instance, {@link #SSZ_SCHEMA}, for every fork up to PQ: the getters of a {@link
   * ValidatorPhase0} read from a supernode's bytes use its field offsets and its node template. A
   * fork that changes the validator needs its own schema and view class.
   */
  public static final class ValidatorSchema
      extends ContainerSchema8<
          ValidatorPhase0,
          SszPublicKey,
          SszBytes32,
          SszUInt64,
          SszBit,
          SszUInt64,
          SszUInt64,
          SszUInt64,
          SszUInt64>
      implements SszPackedViewSchema<ValidatorPhase0> {

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
    public ValidatorPhase0 createFromBackingNode(final TreeNode node) {
      return new ValidatorPhase0(this, node);
    }

    @Override
    public ValidatorPhase0 createFromPackedBytes(final Bytes packed, final int offset) {
      return new PackedValidator(this, packed, offset);
    }
  }

  public static final ValidatorSchema SSZ_SCHEMA = new ValidatorSchema();

  private ValidatorPhase0(final ValidatorSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  /** True for a view that reads its fields from the packed bytes of the registry. */
  @VisibleForTesting
  boolean isPackedView() {
    return false;
  }

  public ValidatorPhase0(
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

  public ValidatorPhase0(
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
  @Override
  public Bytes48 getPubkeyBytes() {
    return getField0().getBytes();
  }

  @Override
  public BLSPublicKey getPublicKey() {
    return getField0().getBLSPublicKey();
  }

  @Override
  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  @Override
  public UInt64 getEffectiveBalance() {
    return getField2().get();
  }

  @Override
  public boolean isSlashed() {
    return getField3().get();
  }

  @Override
  public UInt64 getActivationEligibilityEpoch() {
    return getField4().get();
  }

  @Override
  public UInt64 getActivationEpoch() {
    return getField5().get();
  }

  @Override
  public UInt64 getExitEpoch() {
    return getField6().get();
  }

  @Override
  public UInt64 getWithdrawableEpoch() {
    return getField7().get();
  }

  @Override
  public ValidatorPhase0 withEffectiveBalance(final UInt64 effectiveBalance) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        effectiveBalance,
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPhase0 withSlashed(final boolean slashed) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        slashed,
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPhase0 withActivationEligibilityEpoch(final UInt64 activationEligibilityEpoch) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        activationEligibilityEpoch,
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPhase0 withActivationEpoch(final UInt64 activationEpoch) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        activationEpoch,
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPhase0 withExitEpoch(final UInt64 exitEpoch) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        exitEpoch,
        getWithdrawableEpoch());
  }

  @Override
  public ValidatorPhase0 withWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        getWithdrawalCredentials(),
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        withdrawableEpoch);
  }

  @Override
  public ValidatorPhase0 withWithdrawalCredentials(final Bytes32 withdrawalCredentials) {
    return new ValidatorPhase0(
        getPubkeyBytes(),
        withdrawalCredentials,
        getEffectiveBalance(),
        isSlashed(),
        getActivationEligibilityEpoch(),
        getActivationEpoch(),
        getExitEpoch(),
        getWithdrawableEpoch());
  }

  /**
   * A validator read from the content of a supernode of the registry: the getters read the packed
   * bytes, and the backing node is only created when it is asked for.
   */
  private static final class PackedValidator extends ValidatorPhase0 {
    // The byte offsets of the fields in a serialized validator
    private static final int PUBKEY_OFFSET = fieldOffset(ValidatorFields.PUBKEY);
    private static final int WITHDRAWAL_CREDENTIALS_OFFSET =
        fieldOffset(ValidatorFields.WITHDRAWAL_CREDENTIALS);
    private static final int EFFECTIVE_BALANCE_OFFSET =
        fieldOffset(ValidatorFields.EFFECTIVE_BALANCE);
    private static final int SLASHED_OFFSET = fieldOffset(ValidatorFields.SLASHED);
    private static final int ACTIVATION_ELIGIBILITY_EPOCH_OFFSET =
        fieldOffset(ValidatorFields.ACTIVATION_ELIGIBILITY_EPOCH);
    private static final int ACTIVATION_EPOCH_OFFSET =
        fieldOffset(ValidatorFields.ACTIVATION_EPOCH);
    private static final int EXIT_EPOCH_OFFSET = fieldOffset(ValidatorFields.EXIT_EPOCH);
    private static final int WITHDRAWABLE_EPOCH_OFFSET =
        fieldOffset(ValidatorFields.WITHDRAWABLE_EPOCH);

    private static final int SSZ_SIZE = SSZ_SCHEMA.getSszFixedPartSize();
    private static final SszNodeTemplate NODE_TEMPLATE = SszNodeTemplate.createFromType(SSZ_SCHEMA);

    private final Bytes packed;
    private final int packedOffset;

    private PackedValidator(
        final ValidatorSchema type, final Bytes packed, final int packedOffset) {
      // No backing node: createLazyBackingNode() creates it when it is asked for
      super(type, null);
      this.packed = packed;
      this.packedOffset = packedOffset;
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

    /**
     * The getters read the packed bytes, so the view caches no child views. Called by the super
     * constructor, before the fields of this class are set.
     */
    @Override
    protected IntCache<SszData> createCache() {
      return IntCache.noop();
    }

    @Override
    boolean isPackedView() {
      return true;
    }

    @Override
    protected TreeNode createLazyBackingNode() {
      // Copies, so a node stored outside the registry never keeps the supernode's bytes alive
      return new SszSuperNode(0, NODE_TEMPLATE, packed.slice(packedOffset, SSZ_SIZE).copy());
    }

    private UInt64 packedUInt64(final int fieldOffset) {
      return UInt64.fromLongBits(
          packed.getLong(packedOffset + fieldOffset, ByteOrder.LITTLE_ENDIAN));
    }

    // Copies, so a value that outlives the view never keeps the supernode's bytes alive
    private byte[] packedBytes(final int fieldOffset, final int length) {
      return packed.slice(packedOffset + fieldOffset, length).toArray();
    }

    @Override
    public Bytes48 getPubkeyBytes() {
      return Bytes48.wrap(packedBytes(PUBKEY_OFFSET, Bytes48.SIZE));
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return BLSPublicKey.fromBytesCompressed(getPubkeyBytes());
    }

    @Override
    public Bytes32 getWithdrawalCredentials() {
      return Bytes32.wrap(packedBytes(WITHDRAWAL_CREDENTIALS_OFFSET, Bytes32.SIZE));
    }

    @Override
    public UInt64 getEffectiveBalance() {
      return packedUInt64(EFFECTIVE_BALANCE_OFFSET);
    }

    @Override
    public boolean isSlashed() {
      return packed.get(packedOffset + SLASHED_OFFSET) != 0;
    }

    @Override
    public UInt64 getActivationEligibilityEpoch() {
      return packedUInt64(ACTIVATION_ELIGIBILITY_EPOCH_OFFSET);
    }

    @Override
    public UInt64 getActivationEpoch() {
      return packedUInt64(ACTIVATION_EPOCH_OFFSET);
    }

    @Override
    public UInt64 getExitEpoch() {
      return packedUInt64(EXIT_EPOCH_OFFSET);
    }

    @Override
    public UInt64 getWithdrawableEpoch() {
      return packedUInt64(WITHDRAWABLE_EPOCH_OFFSET);
    }
  }
}
