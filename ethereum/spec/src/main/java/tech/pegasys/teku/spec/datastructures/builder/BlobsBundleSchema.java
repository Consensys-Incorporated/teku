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

package tech.pegasys.teku.spec.datastructures.builder;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public interface BlobsBundleSchema<T extends BlobsBundle> extends SszContainerSchema<T> {

  @Override
  T createFromBackingNode(TreeNode node);

  SszListSchema<SszKZGCommitment, ?> getCommitmentsSchema();

  SszListSchema<SszKZGProof, ?> getProofsSchema();

  SszListSchema<Blob, ?> getBlobsSchema();

  BlobsBundle create(List<KZGCommitment> commitments, List<KZGProof> proofs, List<Blob> blobs);

  BlobsBundle create(
      SszList<SszKZGCommitment> commitments, SszList<SszKZGProof> proofs, SszList<Blob> blobs);
}
