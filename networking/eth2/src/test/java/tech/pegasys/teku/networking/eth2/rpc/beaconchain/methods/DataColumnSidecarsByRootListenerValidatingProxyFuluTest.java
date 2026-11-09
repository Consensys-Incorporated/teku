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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootListenerValidatingProxyFuluTest
    extends AbstractDataColumnSidecarsByRootListenerValidatingProxyTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetFulu();
  }

  @Override
  protected List<DataColumnsByRootIdentifier> createDataColumnIdentifiers(
      final List<SignedBeaconBlock> blocks, final List<List<UInt64>> columnIndices) {
    final SchemaDefinitionsFulu schemaDefinitions =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    final DataColumnsByRootIdentifierSchema byRootIdentifierSchema =
        schemaDefinitions.getDataColumnsByRootIdentifierSchema();

    final List<DataColumnsByRootIdentifier> identifiers = new ArrayList<>();
    for (int i = 0; i < blocks.size(); i++) {
      identifiers.add(byRootIdentifierSchema.create(blocks.get(i).getRoot(), columnIndices.get(i)));
    }
    return identifiers;
  }

  @Override
  protected DataColumnSidecar createSidecarWithInvalidStructure(
      final DataColumnSidecar validSidecar) {
    final SchemaDefinitionsFulu schemaDefinitions =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    final DataColumnSidecarSchema<?> sidecarSchema = schemaDefinitions.getDataColumnSidecarSchema();

    // Create an invalid sidecar by setting empty KZG commitments
    return sidecarSchema.create(
        builder ->
            builder
                .index(validSidecar.getIndex())
                .column(validSidecar.getColumn())
                .kzgCommitments(sidecarSchema.getKzgCommitmentsSchema().of())
                .kzgProofs(validSidecar.getKzgProofs())
                .signedBlockHeader(
                    DataColumnSidecarFulu.required(validSidecar).getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    DataColumnSidecarFulu.required(validSidecar)
                        .getKzgCommitmentsInclusionProof()
                        .asListUnboxed()));
  }
}
