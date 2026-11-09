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

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix.ExecutionPayloadBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionPayloadGloas;

public interface ExecutionPayload extends ExecutionPayloadSummary, SszContainer, BuilderPayload {

  @Override
  ExecutionPayloadSchema<?> getSchema();

  SszList<Transaction> getTransactions();

  default Optional<SszList<Withdrawal>> getOptionalWithdrawals() {
    return Optional.empty();
  }

  /**
   * getUnblindedTreeNodes
   *
   * @return a list of unblinded tree nodes in the order of their generalized indices
   */
  List<TreeNode> getUnblindedTreeNodes();

  default Optional<ExecutionPayloadBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  default Optional<ExecutionPayloadGloas> toVersionGloas() {
    return Optional.empty();
  }

  @Override
  default ExecutionPayload getExecutionPayload() {
    return this;
  }

  @Override
  default Optional<BlobsBundle> getOptionalBlobsBundle() {
    return Optional.empty();
  }
}
