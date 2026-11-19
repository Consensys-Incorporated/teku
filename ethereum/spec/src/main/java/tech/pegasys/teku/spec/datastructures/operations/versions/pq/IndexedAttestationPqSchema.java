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

package tech.pegasys.teku.spec.datastructures.operations.versions.pq;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTING_INDICES_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/** {@code IndexedAttestation} in PQ: a progressive container whose signature became a proof. */
public class IndexedAttestationPqSchema
    extends ContainerSchema3<IndexedAttestationPq, SszUInt64List, AttestationData, SszByteList> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(3);

  public IndexedAttestationPqSchema(
      final String containerName,
      final SchemaRegistry schemaRegistry,
      final SszByteListSchema<SszByteList> attestationProofSchema) {
    super(
        containerName,
        ACTIVE_FIELDS,
        namedSchema("attesting_indices", schemaRegistry.get(ATTESTING_INDICES_SCHEMA)),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("proof", attestationProofSchema));
  }

  public IndexedAttestationPq create(
      final SszUInt64List attestingIndices, final AttestationData data, final Bytes proof) {
    return new IndexedAttestationPq(
        this, attestingIndices, data, getAttestationProofSchema().fromBytes(proof));
  }

  public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<?>) getFieldSchema0();
  }

  public SszByteListSchema<SszByteList> getAttestationProofSchema() {
    return (SszByteListSchema<SszByteList>) getFieldSchema2();
  }

  @Override
  public IndexedAttestationPq createFromBackingNode(final TreeNode node) {
    return new IndexedAttestationPq(this, node);
  }
}
