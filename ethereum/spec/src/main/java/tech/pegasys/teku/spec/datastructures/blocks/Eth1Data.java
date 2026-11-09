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

package tech.pegasys.teku.spec.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1Data extends Container3<Eth1Data, SszBytes32, SszUInt64, SszBytes32> {

  public static class Eth1DataSchema
      extends ContainerSchema3<Eth1Data, SszBytes32, SszUInt64, SszBytes32> {

    public Eth1DataSchema() {
      super(
          "Eth1Data",
          namedSchema("deposit_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("deposit_count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public Eth1Data createFromBackingNode(final TreeNode node) {
      return new Eth1Data(this, node);
    }
  }

  public static final Eth1DataSchema SSZ_SCHEMA = new Eth1DataSchema();

  /**
   * The output of `get_deposit_root` from the deposit contract prior to any deposits being made.
   */
  public static final Bytes32 EMPTY_DEPOSIT_ROOT =
      Bytes32.fromHexString("0xd70a234731285c6804c2a4f56711ddb8c82c99740f207854891028af34e27e5e");

  private Eth1Data(final Eth1DataSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Eth1Data(final Bytes32 depositRoot, final UInt64 depositCount, final Bytes32 blockHash) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(depositRoot),
        SszUInt64.of(depositCount),
        SszBytes32.of(blockHash));
  }

  public Eth1Data() {
    super(SSZ_SCHEMA);
  }

  public Eth1Data withBlockHash(final Bytes32 blockHash) {
    return new Eth1Data(getDepositRoot(), getDepositCount(), blockHash);
  }

  /**
   * @return the deposit_root
   */
  public Bytes32 getDepositRoot() {
    return getField0().get();
  }

  public UInt64 getDepositCount() {
    return getField1().get();
  }

  /**
   * @return the block_hash
   */
  public Bytes32 getBlockHash() {
    return getField2().get();
  }

  @Override
  public Eth1DataSchema getSchema() {
    return (Eth1DataSchema) super.getSchema();
  }
}
