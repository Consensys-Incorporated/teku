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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public interface BlockContentsSchema<T extends BlockContainer> extends BlockContainerSchema<T> {

  SszFieldName FIELD_BLOCK = () -> "block";
  SszFieldName FIELD_KZG_PROOFS = () -> "kzg_proofs";
  SszFieldName FIELD_BLOBS = () -> "blobs";
  // GLOAS
  SszFieldName FIELD_EXECUTION_PAYLOAD_ENVELOPE = () -> "execution_payload_envelope";

  T create(
      BeaconBlock beaconBlock,
      List<KZGProof> kzgProofs,
      List<Blob> blobs,
      Optional<ExecutionPayloadEnvelope> executionPayloadEnvelope);

  @Override
  T createFromBackingNode(TreeNode node);

  SszListSchema<SszKZGProof, ?> getKzgProofsSchema();

  SszListSchema<Blob, ?> getBlobsSchema();
}
