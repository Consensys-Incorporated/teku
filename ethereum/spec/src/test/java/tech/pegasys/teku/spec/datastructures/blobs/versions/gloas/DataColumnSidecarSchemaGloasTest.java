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

package tech.pegasys.teku.spec.datastructures.blobs.versions.gloas;

import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_PROOF;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

/**
 * Spec {@code compute_max_data_column_sidecar_size}: the network bound of a {@code
 * DataColumnSidecar} is the serialized size of a sidecar filled with the largest {@code
 * max_blobs_per_block} of the blob schedule.
 */
class DataColumnSidecarSchemaGloasTest {

  // index (8) + column offset (4) + kzg_proofs offset (4) + slot (8) + beacon_block_root (32)
  private static final int SIDECAR_FIXED_PART_BYTES = 8 + 4 + 4 + 8 + 32;

  @Test
  void networkSszLengthBound_shouldBeSerializedSizeOfSidecarWithScheduleMaxBlobs() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final SpecConfigGloas config = gloasConfig(spec);
    final int maxBlobs =
        Math.max(
            config.getMaxBlobsPerBlock(),
            config.getBlobSchedule().stream()
                .mapToInt(BlobScheduleEntry::maxBlobsPerBlock)
                .max()
                .orElse(0));

    assertThat(sidecarSchema(spec).getNetworkSszLengthBytesUpperBound())
        .hasValue(expectedMaxSize(maxBlobs));
  }

  @Test
  void networkSszLengthBound_shouldFollowHighestBlobScheduleEntry() {
    final int scheduleMaxBlobs = 40;
    final Spec spec =
        TestSpecFactory.createMinimalGloas(
            builder ->
                builder.fuluBuilder(
                    fuluBuilder ->
                        fuluBuilder.blobSchedule(
                            List.of(
                                new BlobScheduleEntry(UInt64.valueOf(10), 12),
                                new BlobScheduleEntry(UInt64.valueOf(20), scheduleMaxBlobs),
                                new BlobScheduleEntry(UInt64.valueOf(30), 20)))));
    assertThat(gloasConfig(spec).getMaxBlobsPerBlock()).isLessThan(scheduleMaxBlobs);

    assertThat(sidecarSchema(spec).getNetworkSszLengthBytesUpperBound())
        .hasValue(expectedMaxSize(scheduleMaxBlobs));
  }

  @Test
  void networkSszLengthBounds_shouldAcceptMaxSidecarAndRejectOneCellMore() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataColumnSidecarSchema<?> schema = sidecarSchema(spec);
    final long maxBlobs =
        schema.getNetworkSszLengthBytesUpperBound().orElseThrow() / (BYTES_PER_CELL + 1);

    assertThat(
            schema
                .getNetworkSszLengthBounds()
                .isWithinBounds(sidecarWith(spec, (int) maxBlobs).sszSerialize().size()))
        .isTrue();
    assertThat(
            schema
                .getNetworkSszLengthBounds()
                .isWithinBounds(sidecarWith(spec, (int) maxBlobs + 1).sszSerialize().size()))
        .isFalse();
  }

  private static long expectedMaxSize(final int maxBlobs) {
    return SIDECAR_FIXED_PART_BYTES + (long) maxBlobs * (BYTES_PER_CELL + BYTES_PER_PROOF);
  }

  private static SpecConfigGloas gloasConfig(final Spec spec) {
    return SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
  }

  private static SchemaDefinitionsGloas schemaDefinitions(final Spec spec) {
    return SchemaDefinitionsGloas.required(
        spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions());
  }

  private static DataColumnSidecarSchema<?> sidecarSchema(final Spec spec) {
    return schemaDefinitions(spec).getDataColumnSidecarSchema();
  }

  private static DataColumnSidecar sidecarWith(final Spec spec, final int cells) {
    final SchemaDefinitionsGloas schemaDefinitions = schemaDefinitions(spec);
    final DataColumnSidecarSchema<?> schema = schemaDefinitions.getDataColumnSidecarSchema();
    return schema.create(
        builder ->
            builder
                .index(UInt64.ZERO)
                .column(
                    schemaDefinitions
                        .getDataColumnSchema()
                        .create(
                            Collections.nCopies(
                                cells, schemaDefinitions.getCellSchema().getDefault())))
                .kzgProofs(
                    schema
                        .getKzgProofsSchema()
                        .createFromElements(
                            Collections.nCopies(
                                cells,
                                schema.getKzgProofsSchema().getElementSchema().getDefault())))
                .slot(UInt64.ZERO)
                .beaconBlockRoot(Bytes32.ZERO));
  }
}
