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

import java.util.Collections;
import java.util.OptionalLong;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class DataColumnSidecarSchemaGloas
    extends ContainerSchema5<
        DataColumnSidecarGloas, SszUInt64, DataColumn, SszList<SszKZGProof>, SszUInt64, SszBytes32>
    implements DataColumnSidecarSchema<DataColumnSidecarGloas> {

  private final OptionalLong networkSszLengthBytesUpperBound;

  public DataColumnSidecarSchemaGloas(
      final DataColumnSchema dataColumnSchema, final SpecConfigFulu specConfig) {
    super(
        "DataColumnSidecarGloas",
        namedSchema(FIELD_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BLOB, dataColumnSchema),
        namedSchema(FIELD_KZG_PROOFS, SszProgressiveListSchema.create(SszKZGProofSchema.INSTANCE)),
        namedSchema(FIELD_SLOT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BEACON_BLOCK_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));
    this.networkSszLengthBytesUpperBound =
        OptionalLong.of(computeMaxDataColumnSidecarSize(dataColumnSchema, specConfig));
    validateNetworkSszLengthBytesUpperBound();
  }

  /**
   * Spec {@code compute_max_data_column_sidecar_size}: the serialized size of a sidecar holding the
   * largest {@code max_blobs_per_block} of the blob schedule, regardless of whether that is the
   * current value. It bounds the sidecar on gossip and RPC in place of the type-level SSZ bound,
   * which is far larger since the lists are only limited by {@code MAX_BLOB_COMMITMENTS_PER_BLOCK}.
   */
  private long computeMaxDataColumnSidecarSize(
      final DataColumnSchema dataColumnSchema, final SpecConfigFulu specConfig) {
    final int maxBlobs =
        specConfig.getBlobSchedule().stream()
            .mapToInt(BlobScheduleEntry::maxBlobsPerBlock)
            .reduce(specConfig.getMaxBlobsPerBlock(), Math::max);
    final DataColumnSidecar sidecar =
        create(
            builder ->
                builder
                    .index(UInt64.ZERO)
                    .column(
                        dataColumnSchema.create(
                            Collections.nCopies(
                                maxBlobs, dataColumnSchema.getElementSchema().getDefault())))
                    .kzgProofs(
                        getKzgProofsSchema()
                            .createFromElements(
                                Collections.nCopies(
                                    maxBlobs, SszKZGProofSchema.INSTANCE.getDefault())))
                    .slot(UInt64.ZERO)
                    .beaconBlockRoot(Bytes32.ZERO));
    return sidecar.sszSerialize().size();
  }

  @Override
  public OptionalLong getNetworkSszLengthBytesUpperBound() {
    return networkSszLengthBytesUpperBound;
  }

  @Override
  public SszListSchema<SszKZGCommitment, ?> getKzgCommitmentsSchema() {
    throw new UnsupportedOperationException("blob_kzg_commitments field was removed in Gloas");
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  @Override
  public DataColumnSidecar create(final Consumer<DataColumnSidecarBuilder> builderConsumer) {
    final DataColumnSidecarBuilderGloas builder = new DataColumnSidecarBuilderGloas().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public DataColumnSidecarGloas createFromBackingNode(final TreeNode node) {
    return new DataColumnSidecarGloas(this, node);
  }
}
