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

package tech.pegasys.teku.spec.datastructures.blobs;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public interface DataColumnSidecarSchema<T extends DataColumnSidecar>
    extends SszContainerSchema<T> {

  SszFieldName FIELD_INDEX = () -> "index";
  SszFieldName FIELD_BLOB = () -> "column";
  SszFieldName FIELD_KZG_COMMITMENTS = () -> "kzg_commitments";
  SszFieldName FIELD_KZG_PROOFS = () -> "kzg_proofs";
  SszFieldName FIELD_SIGNED_BLOCK_HEADER = () -> "signed_block_header";
  SszFieldName FIELD_KZG_COMMITMENTS_INCLUSION_PROOF = () -> "kzg_commitments_inclusion_proof";
  SszFieldName FIELD_SLOT = () -> "slot";
  SszFieldName FIELD_BEACON_BLOCK_ROOT = () -> "beacon_block_root";

  @Override
  T createFromBackingNode(TreeNode node);

  SszListSchema<SszKZGCommitment, ?> getKzgCommitmentsSchema();

  SszListSchema<SszKZGProof, ?> getKzgProofsSchema();

  DataColumnSidecar create(Consumer<DataColumnSidecarBuilder> builderConsumer);

  @SuppressWarnings("unchecked")
  default DataColumnSidecarSchema<DataColumnSidecar> toBaseSchema() {
    return (DataColumnSidecarSchema<DataColumnSidecar>) this;
  }
}
