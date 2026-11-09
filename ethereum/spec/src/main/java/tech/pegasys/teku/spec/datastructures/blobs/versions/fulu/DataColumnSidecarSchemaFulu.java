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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeaderSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProofSchema;

public class DataColumnSidecarSchemaFulu
    extends ContainerSchema6<
        DataColumnSidecarFulu,
        SszUInt64,
        DataColumn,
        SszList<SszKZGCommitment>,
        SszList<SszKZGProof>,
        SignedBeaconBlockHeader,
        SszBytes32Vector>
    implements DataColumnSidecarSchema<DataColumnSidecarFulu> {

  public static DataColumnSidecarSchemaFulu required(final DataColumnSidecarSchema<?> schema) {
    try {
      return (DataColumnSidecarSchemaFulu) schema;
    } catch (final ClassCastException __) {
      throw new IllegalArgumentException(
          "Expected DataColumnSidecarSchemaFulu but got: " + schema.getClass().getSimpleName());
    }
  }

  public DataColumnSidecarSchemaFulu(
      final SignedBeaconBlockHeaderSchema signedBeaconBlockHeaderSchema,
      final DataColumnSchema dataColumnSchema,
      final SpecConfigFulu specConfig) {
    super(
        "DataColumnSidecarFulu",
        namedSchema(FIELD_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(FIELD_BLOB, dataColumnSchema),
        namedSchema(
            FIELD_KZG_COMMITMENTS,
            SszListSchema.create(
                SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(
            FIELD_KZG_PROOFS,
            SszListSchema.create(
                SszKZGProofSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock())),
        namedSchema(FIELD_SIGNED_BLOCK_HEADER, signedBeaconBlockHeaderSchema),
        namedSchema(
            FIELD_KZG_COMMITMENTS_INCLUSION_PROOF,
            SszBytes32VectorSchema.create(
                specConfig.getKzgCommitmentsInclusionProofDepth().intValue())));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGCommitment, ?> getKzgCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENTS));
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGProof, ?> getKzgProofsSchema() {
    return (SszListSchema<SszKZGProof, ?>) getChildSchema(getFieldIndex(FIELD_KZG_PROOFS));
  }

  public SszBytes32VectorSchema<?> getKzgCommitmentsInclusionProofSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(FIELD_KZG_COMMITMENTS_INCLUSION_PROOF));
  }

  @Override
  public DataColumnSidecar create(final Consumer<DataColumnSidecarBuilder> builderConsumer) {
    final DataColumnSidecarBuilderFulu builder = new DataColumnSidecarBuilderFulu().schema(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public DataColumnSidecarFulu createFromBackingNode(final TreeNode node) {
    return new DataColumnSidecarFulu(this, node);
  }
}
