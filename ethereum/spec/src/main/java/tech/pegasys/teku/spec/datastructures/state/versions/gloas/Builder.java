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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.ByteUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class Builder
    extends Container6<
        Builder, SszPublicKey, SszByte, SszByteVector, SszUInt64, SszUInt64, SszUInt64> {

  public static class BuilderSchema
      extends ContainerSchema6<
          Builder, SszPublicKey, SszByte, SszByteVector, SszUInt64, SszUInt64, SszUInt64> {

    public BuilderSchema() {
      super(
          "Builder",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("version", SszPrimitiveSchemas.UINT8_SCHEMA),
          namedSchema("execution_address", SszByteVectorSchema.create(Bytes20.SIZE)),
          namedSchema("balance", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("deposit_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("withdrawable_epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public Builder createFromBackingNode(final TreeNode node) {
      return new Builder(this, node);
    }
  }

  public static final BuilderSchema SSZ_SCHEMA = new BuilderSchema();

  private Builder(final BuilderSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Builder(
      final BLSPublicKey pubkey,
      final int version,
      final Eth1Address executionAddress,
      final UInt64 balance,
      final UInt64 depositEpoch,
      final UInt64 withdrawableEpoch) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszByte.asUInt8(version),
        SszByteVector.fromBytes(executionAddress.getWrappedBytes()),
        SszUInt64.of(balance),
        SszUInt64.of(depositEpoch),
        SszUInt64.of(withdrawableEpoch));
  }

  public BLSPublicKey getPublicKey() {
    return getField0().getBLSPublicKey();
  }

  public int getVersion() {
    return ByteUtil.toUnsignedInt(getField1().get());
  }

  public Eth1Address getExecutionAddress() {
    return Eth1Address.fromBytes(getField2().getBytes());
  }

  public UInt64 getBalance() {
    return getField3().get();
  }

  public UInt64 getDepositEpoch() {
    return getField4().get();
  }

  public UInt64 getWithdrawableEpoch() {
    return getField5().get();
  }

  public Builder copyWithNewWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new Builder(
        getPublicKey(),
        getVersion(),
        getExecutionAddress(),
        getBalance(),
        getDepositEpoch(),
        withdrawableEpoch);
  }

  public Builder copyWithNewBalance(final UInt64 balance) {
    return new Builder(
        getPublicKey(),
        getVersion(),
        getExecutionAddress(),
        balance,
        getDepositEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public BuilderSchema getSchema() {
    return (BuilderSchema) super.getSchema();
  }
}
