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

package tech.pegasys.teku.spec.datastructures.operations.versions.gloas;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveUInt64ListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class AttestationGloasSchemaTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void aggregationBitsSchemaIsProgressive() {
    assertThat(schemaDefinitions.getAttestationSchema().getAggregationBitsSchema())
        .isInstanceOf(SszProgressiveBitlistSchema.class);
  }

  @Test
  void attestingIndicesSchemaIsProgressive() {
    assertThat(schemaDefinitions.getIndexedAttestationSchema().getAttestingIndicesSchema())
        .isInstanceOf(SszProgressiveUInt64ListSchema.class);
  }

  @Test
  void networkSchemasHaveFiniteRawBounds() {
    // aggregation bits and attesting indices declare their limit, so the containers are bounded
    assertThat(schemaDefinitions.getAttestationSchema().getSszLengthBounds().isUnbounded())
        .isFalse();
    assertThat(schemaDefinitions.getIndexedAttestationSchema().getSszLengthBounds().isUnbounded())
        .isFalse();
    assertThat(schemaDefinitions.getAttesterSlashingSchema().getSszLengthBounds().isUnbounded())
        .isFalse();
  }

  @Test
  void attestationSszRoundTrip() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final Bytes ssz = attestation.sszSerialize();
    final Attestation deserialized = schemaDefinitions.getAttestationSchema().sszDeserialize(ssz);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(attestation.hashTreeRoot());
  }

  @Test
  void attesterSlashingSszRoundTrip() {
    final AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    final Bytes ssz = attesterSlashing.sszSerialize();
    final AttesterSlashing deserialized =
        schemaDefinitions.getAttesterSlashingSchema().sszDeserialize(ssz);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(attesterSlashing.hashTreeRoot());
  }
}
