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

package tech.pegasys.teku.benchmarks.kzg;

import java.util.List;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.KzgUtil;

public class SidecarBenchmarkConfig {
  final KzgInstances kzgBenchmark;
  final List<SszKZGCommitment> kzgCommitments;
  final List<List<MatrixEntry>> extendedMatrix;
  final SignedBeaconBlock signedBeaconBlock;
  final List<DataColumnSidecar> dataColumnSidecars;

  final Spec spec = TestSpecFactory.createMainnetFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final PredicatesElectra predicates = new PredicatesElectra(spec.getGenesisSpecConfig());

  final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.getGenesisSchemaDefinitions());
  final SpecConfigFulu specConfigFulu = spec.getGenesisSpecConfig().toVersionFulu().orElseThrow();
  final MiscHelpersFulu miscHelpersFulu =
      new MiscHelpersFulu(specConfigFulu, predicates, schemaDefinitionsFulu);
  final List<Blob> blobs =
      IntStream.range(0, 72).mapToObj(__ -> dataStructureUtil.randomValidBlob()).toList();
  final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
      SchemaDefinitionsDeneb.required(spec.atSlot(UInt64.ONE).getSchemaDefinitions())
          .getBlobKzgCommitmentsSchema();

  SidecarBenchmarkConfig(final boolean precompute, final boolean useRustLibrary) {
    kzgBenchmark = new KzgInstances(precompute ? 9 : 0);
    kzgCommitments =
        blobs.stream()
            .map(blob -> getKzg(false).blobToKzgCommitment(blob.getBytes()))
            .map(SszKZGCommitment::new)
            .toList();
    miscHelpersFulu.setKzg(getKzg(useRustLibrary));
    List<BlobAndCellProofs> blobsAndCellProofs =
        blobs.stream()
            .map((b) -> KzgUtil.computeBlobAndCellProofs(miscHelpersFulu.getKzg(), b))
            .toList();
    extendedMatrix = miscHelpersFulu.computeExtendedMatrix(blobsAndCellProofs);
    signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(
            blobKzgCommitmentsSchema.createFromElements(kzgCommitments));
    dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            signedBeaconBlock.getMessage(), signedBeaconBlock.asHeader(), extendedMatrix);
  }

  public KZG getKzg(final boolean useRustLibrary) {
    return kzgBenchmark.getKzg(useRustLibrary);
  }
}
