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
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadHeaderSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderSchemaCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadHeaderSchemaDeneb;

public interface ExecutionPayloadHeaderSchema<T extends ExecutionPayloadHeader>
    extends SszContainerSchema<T> {

  ExecutionPayloadHeader getHeaderOfDefaultPayload();

  @Override
  T createFromBackingNode(TreeNode node);

  T createFromExecutionPayload(ExecutionPayload payload);

  /**
   * getBlindedNodeGeneralizedIndices
   *
   * @return a list of generalized indices in numeric order
   */
  LongList getBlindedNodeGeneralizedIndices();

  ExecutionPayloadHeader createExecutionPayloadHeader(
      Consumer<ExecutionPayloadHeaderBuilder> builderConsumer);

  default ExecutionPayloadHeaderSchemaBellatrix toVersionBellatrixRequired() {
    throw new UnsupportedOperationException("Not a Bellatrix schema");
  }

  default ExecutionPayloadHeaderSchemaCapella toVersionCapellaRequired() {
    throw new UnsupportedOperationException("Not a Capella schema");
  }

  default ExecutionPayloadHeaderSchemaDeneb toVersionDenebRequired() {
    throw new UnsupportedOperationException("Not a Deneb schema");
  }
}
