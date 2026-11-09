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

package tech.pegasys.teku.spec.datastructures.execution.versions.heze;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.TRANSACTIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.TRANSACTION_SCHEMA;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class InclusionListSchema
    extends ContainerSchema4<
        InclusionList, SszUInt64, SszUInt64, SszBytes32, SszList<Transaction>> {

  private final SszByteListSchema<Transaction> transactionSchema;

  public InclusionListSchema(final SchemaRegistry schemaRegistry) {
    super(
        "InclusionList",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("inclusion_list_committee_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("transactions", schemaRegistry.get(TRANSACTIONS_SCHEMA)));
    this.transactionSchema = schemaRegistry.get(TRANSACTION_SCHEMA);
  }

  @Override
  public InclusionList createFromBackingNode(final TreeNode node) {
    return new InclusionList(this, node);
  }

  public InclusionList create(
      final UInt64 slot,
      final UInt64 validatorIndex,
      final Bytes32 inclusionListCommitteeRoot,
      final List<Bytes> transactions) {
    return new InclusionList(this, slot, validatorIndex, inclusionListCommitteeRoot, transactions);
  }

  public SszByteListSchema<Transaction> getTransactionSchema() {
    return transactionSchema;
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<Transaction, ?> getTransactionsSchema() {
    return (SszListSchema<Transaction, ?>) getChildSchema(getFieldIndex("transactions"));
  }
}
