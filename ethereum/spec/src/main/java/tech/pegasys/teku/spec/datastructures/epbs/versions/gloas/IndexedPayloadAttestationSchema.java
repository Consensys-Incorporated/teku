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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PAYLOAD_ATTESTATION_DATA_SCHEMA;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class IndexedPayloadAttestationSchema
    extends ContainerSchema3<
        IndexedPayloadAttestation, SszUInt64List, PayloadAttestationData, SszSignature> {

  private static final SszFieldName ATTESTING_INDICES = () -> "attesting_indices";
  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(3);

  public IndexedPayloadAttestationSchema(
      final SpecConfigGloas specConfig, final SchemaRegistry schemaRegistry) {
    super(
        "IndexedPayloadAttestation",
        ACTIVE_FIELDS,
        namedSchema(
            ATTESTING_INDICES,
            SszUInt64ListSchema.create(specConfig.getPayloadTimelinessCommitteeSize())),
        namedSchema("data", schemaRegistry.get(PAYLOAD_ATTESTATION_DATA_SCHEMA)),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public IndexedPayloadAttestation create(
      final SszUInt64List attestingIndices,
      final PayloadAttestationData data,
      final BLSSignature signature) {
    return new IndexedPayloadAttestation(this, attestingIndices, data, signature);
  }

  @Override
  public IndexedPayloadAttestation createFromBackingNode(final TreeNode node) {
    return new IndexedPayloadAttestation(this, node);
  }

  public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<?>) getChildSchema(getFieldIndex(ATTESTING_INDICES));
  }
}
