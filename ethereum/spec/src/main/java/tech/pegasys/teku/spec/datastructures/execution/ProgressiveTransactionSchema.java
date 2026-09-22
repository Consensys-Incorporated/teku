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

package tech.pegasys.teku.spec.datastructures.execution;

import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;

/**
 * Progressive (EIP-7916) variant of {@link TransactionSchema} that preserves the {@link
 * Transaction} subtype while using progressive merkleization. The spec {@code LIMIT} ({@code
 * MAX_BYTES_PER_TRANSACTION}) is enforced by the schema on construction and deserialization.
 */
public class ProgressiveTransactionSchema extends SszProgressiveByteListSchema<Transaction> {

  public ProgressiveTransactionSchema(final SpecConfigBellatrix specConfig) {
    super(
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SszSchemaHints.none(),
        specConfig.getMaxBytesPerTransaction());
  }

  @Override
  public Transaction createFromBackingNode(final TreeNode node) {
    return new Transaction(this, node);
  }
}
