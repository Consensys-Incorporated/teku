/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.ethereum.pow.api;

import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositTreeSnapshot
    extends Container5<
        DepositTreeSnapshot, SszList<SszBytes32>, SszBytes32, SszUInt64, SszBytes32, SszUInt64> {

  static class DepositTreeSnapshotSchema
      extends ContainerSchema5<
          DepositTreeSnapshot, SszList<SszBytes32>, SszBytes32, SszUInt64, SszBytes32, SszUInt64> {

    private DepositTreeSnapshotSchema() {
      super(
          "DepositTreeSnapshot",
          namedSchema(
              "finalized",
              SszListSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, DEPOSIT_CONTRACT_TREE_DEPTH)),
          namedSchema("deposit_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("deposit_count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("execution_block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("execution_block_height", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public DepositTreeSnapshot createFromBackingNode(final TreeNode node) {
      return new DepositTreeSnapshot(this, node);
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<SszBytes32, ? extends SszList<SszBytes32>> getFinalizedSchema() {
      return (SszListSchema<SszBytes32, ? extends SszList<SszBytes32>>) getChildSchema(0);
    }
  }

  public static final DepositTreeSnapshotSchema SSZ_SCHEMA = new DepositTreeSnapshotSchema();

  public static DeserializableTypeDefinition<DepositTreeSnapshot> getJsonTypeDefinition() {
    return SSZ_SCHEMA.getJsonTypeDefinition();
  }

  public static DepositTreeSnapshot fromBytes(final Bytes bytes) {
    return SSZ_SCHEMA.sszDeserialize(bytes);
  }

  public DepositTreeSnapshot(
      final List<Bytes32> finalized,
      final Bytes32 depositRoot,
      final long depositCount,
      final Bytes32 executionBlockHash,
      final UInt64 executionBlockHeight) {
    super(
        SSZ_SCHEMA,
        finalized.stream().map(SszBytes32::of).collect(SSZ_SCHEMA.getFinalizedSchema().collector()),
        SszBytes32.of(depositRoot),
        SszUInt64.of(UInt64.valueOf(depositCount)),
        SszBytes32.of(executionBlockHash),
        SszUInt64.of(executionBlockHeight));
  }

  private DepositTreeSnapshot(final DepositTreeSnapshotSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public List<Bytes32> getFinalized() {
    final SszList<SszBytes32> value = getAny(0);
    return value.asList().stream().map(SszBytes32::get).toList();
  }

  public Bytes32 getDepositRoot() {
    final SszBytes32 value = getAny(1);
    return value.get();
  }

  public long getDepositCount() {
    final SszUInt64 value = getAny(2);
    return value.longValue();
  }

  public Bytes32 getExecutionBlockHash() {
    final SszBytes32 value = getAny(3);
    return value.get();
  }

  public UInt64 getExecutionBlockHeight() {
    final SszUInt64 value = getAny(4);
    return value.get();
  }
}
