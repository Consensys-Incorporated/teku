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

package tech.pegasys.teku.spec.datastructures.execution;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadSchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.WithdrawalSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadSchemaGloas;

public interface ExecutionPayloadSchema<T extends ExecutionPayload>
    extends SszContainerSchema<T>, BuilderPayloadSchema<T> {

  @Override
  T createFromBackingNode(TreeNode node);

  SszByteListSchema<Transaction> getTransactionSchema();

  SszListSchema<Withdrawal, ? extends SszList<Withdrawal>> getWithdrawalsSchemaRequired();

  WithdrawalSchema getWithdrawalSchemaRequired();

  LongList getBlindedNodeGeneralizedIndices();

  ExecutionPayload createExecutionPayload(Consumer<ExecutionPayloadBuilder> builderConsumer);

  default ExecutionPayloadSchemaBellatrix toVersionBellatrixRequired() {
    throw new UnsupportedOperationException("Not a Bellatrix schema");
  }

  default ExecutionPayloadSchemaCapella toVersionCapellaRequired() {
    throw new UnsupportedOperationException("Not a Capella schema");
  }

  default ExecutionPayloadSchemaDeneb toVersionDenebRequired() {
    throw new UnsupportedOperationException("Not a Deneb schema");
  }

  default ExecutionPayloadSchemaGloas toVersionGloasRequired() {
    throw new UnsupportedOperationException("Not a Gloas schema");
  }
}
