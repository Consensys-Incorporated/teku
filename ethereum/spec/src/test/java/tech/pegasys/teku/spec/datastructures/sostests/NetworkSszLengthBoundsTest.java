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

package tech.pegasys.teku.spec.datastructures.sostests;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

class NetworkSszLengthBoundsTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  @Test
  void gloasTopLevelNetworkSchemasApplyConfiguredNetworkBounds() {
    assertNetworkBound(
        schemaDefinitions.getSignedBeaconBlockSchema(),
        spec.getNetworkingConfig().getMaxPayloadSize());
    assertNetworkBound(schemaDefinitions.getSignedAggregateAndProofSchema(), 16_829);
    assertNetworkBound(schemaDefinitions.getAttesterSlashingSchema(), 2_097_616);
    // compute_max_data_column_sidecar_size for the minimal blob schedule (9 blobs):
    // 56 fixed bytes + 9 * (2048 cell + 48 proof)
    assertNetworkBound(schemaDefinitions.getDataColumnSidecarSchema(), 18_920);
    assertNetworkBound(schemaDefinitions.getSignedExecutionPayloadBidSchema(), 196_932);
  }

  @Test
  void gloasSchemasWithoutNetworkOverridesKeepRawBounds() {
    final SszType attestationSchema = schemaDefinitions.getAttestationSchema();

    assertThat(attestationSchema.getNetworkSszLengthBytesUpperBound().isEmpty()).isTrue();
    assertThat(attestationSchema.getNetworkSszLengthBounds())
        .isEqualTo(attestationSchema.getSszLengthBounds());
  }

  private void assertNetworkBound(final SszType schema, final long expectedMaxBytes) {
    assertThat(schema.getSszLengthBounds().isUnbounded()).isTrue();
    assertThat(schema.getNetworkSszLengthBytesUpperBound().orElseThrow())
        .isEqualTo(expectedMaxBytes);
    assertThat(schema.getNetworkSszLengthBounds().getMinBytes())
        .isEqualTo(schema.getSszLengthBounds().getMinBytes());
    assertThat(schema.getNetworkSszLengthBounds().getMaxBytes()).isEqualTo(expectedMaxBytes);
  }
}
