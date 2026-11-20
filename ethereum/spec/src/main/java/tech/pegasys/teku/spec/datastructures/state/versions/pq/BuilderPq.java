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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.ByteUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

/** A PQ builder: the Gloas builder keyed by a 32-byte leanXMSS public key. It has no BLS key. */
public class BuilderPq
    extends Container6<
        BuilderPq, SszBytes32, SszByte, SszByteVector, SszUInt64, SszUInt64, SszUInt64>
    implements Builder {

  public static class BuilderPqSchema
      extends ContainerSchema6<
          BuilderPq, SszBytes32, SszByte, SszByteVector, SszUInt64, SszUInt64, SszUInt64> {

    private BuilderPqSchema() {
      super(
          "BuilderPq",
          namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("version", SszPrimitiveSchemas.UINT8_SCHEMA),
          namedSchema("execution_address", SszByteVectorSchema.create(Bytes20.SIZE)),
          namedSchema("balance", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("deposit_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("withdrawable_epoch", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BuilderPq createFromBackingNode(final TreeNode node) {
      return new BuilderPq(this, node);
    }
  }

  public static final BuilderPqSchema SSZ_SCHEMA = new BuilderPqSchema();

  private BuilderPq(final BuilderPqSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BuilderPq(
      final Bytes32 xmssPublicKey,
      final int version,
      final Eth1Address executionAddress,
      final UInt64 balance,
      final UInt64 depositEpoch,
      final UInt64 withdrawableEpoch) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(xmssPublicKey),
        SszByte.asUInt8(version),
        SszByteVector.fromBytes(executionAddress.getWrappedBytes()),
        SszUInt64.of(balance),
        SszUInt64.of(depositEpoch),
        SszUInt64.of(withdrawableEpoch));
  }

  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  /** A PQ builder has no BLS key. */
  @Override
  public BLSPublicKey getPublicKey() {
    throw new UnsupportedOperationException("A PQ builder has no BLS public key");
  }

  @Override
  public int getVersion() {
    return ByteUtil.toUnsignedInt(getField1().get());
  }

  @Override
  public Eth1Address getExecutionAddress() {
    return Eth1Address.fromBytes(getField2().getBytes());
  }

  @Override
  public UInt64 getBalance() {
    return getField3().get();
  }

  @Override
  public UInt64 getDepositEpoch() {
    return getField4().get();
  }

  @Override
  public UInt64 getWithdrawableEpoch() {
    return getField5().get();
  }

  @Override
  public BuilderPq copyWithNewWithdrawableEpoch(final UInt64 withdrawableEpoch) {
    return new BuilderPq(
        getXmssPublicKey(),
        getVersion(),
        getExecutionAddress(),
        getBalance(),
        getDepositEpoch(),
        withdrawableEpoch);
  }

  @Override
  public BuilderPq copyWithNewBalance(final UInt64 balance) {
    return new BuilderPq(
        getXmssPublicKey(),
        getVersion(),
        getExecutionAddress(),
        balance,
        getDepositEpoch(),
        getWithdrawableEpoch());
  }

  @Override
  public BuilderPqSchema getSchema() {
    return (BuilderPqSchema) super.getSchema();
  }
}
