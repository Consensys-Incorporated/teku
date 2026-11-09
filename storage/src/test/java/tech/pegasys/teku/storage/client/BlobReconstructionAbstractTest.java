/*
 * Copyright (c) 2024 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.storage.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.spec.util.KzgUtil;

public class BlobReconstructionAbstractTest {
  protected final Spec spec = TestSpecFactory.createMinimalFulu();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected static final String VALID_BLOBS_AND_CELLS_FILENAMES = "valid_blobs_and_cells.json";

  @Test
  @Disabled
  public void regenerateValidBlobsAndCellsFile() {
    reinitializeSpecWithProductionKZG();

    final var block = dataStructureUtil.randomSignedBeaconBlock();
    final var blobs =
        List.of(dataStructureUtil.randomValidBlob(), dataStructureUtil.randomValidBlob());

    final MiscHelpersFulu miscHelpers =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());

    final var celldata =
        blobs.stream()
            .map(
                b -> {
                  final var sidecars =
                      miscHelpers.constructDataColumnSidecars(
                          block,
                          List.of(KzgUtil.computeBlobAndCellProofs(miscHelpers.getKzg(), b)));
                  return new CellData(
                      b.getBytes().toHexString(),
                      sidecars.stream()
                          .map(s -> s.getColumn().get(0).getBytes().toHexString())
                          .toList());
                })
            .toList();

    @SuppressWarnings("UnusedVariable")
    final var result = generateJson(celldata);
  }

  private void reinitializeSpecWithProductionKZG() {
    var kzg = KZG.getInstance(false);
    kzg.loadTrustedSetup(
        Eth2NetworkConfiguration.class.getResource("mainnet-trusted-setup.txt").toExternalForm(),
        0);

    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
  }

  protected BlobsAndMatrix loadBlobsAndMatrixFixture() {
    final List<CellData> cellData = loadJson();
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(
            spec.forMilestone(SpecMilestone.ELECTRA).getSchemaDefinitions());
    final List<Blob> blobs =
        cellData.stream()
            .map(
                blobAndCells ->
                    schemaDefinitionsElectra
                        .getBlobSchema()
                        .create(Bytes.fromHexString(blobAndCells.blob)))
            .toList();
    final List<List<MatrixEntry>> extendedMatrix = new ArrayList<>();
    for (int blobIndex = 0; blobIndex < cellData.size(); ++blobIndex) {
      final CellData blobCellData = cellData.get(blobIndex);
      final List<MatrixEntry> row = new ArrayList<>();
      for (int cellIndex = 0; cellIndex < blobCellData.cells.size(); ++cellIndex) {
        row.add(
            new MatrixEntry(
                schemaDefinitionsFulu.getMatrixEntrySchema(),
                schemaDefinitionsFulu
                    .getCellSchema()
                    .create(Bytes.fromHexString(blobCellData.cells.get(cellIndex))),
                KZGProof.fromBytesCompressed(Bytes48.ZERO),
                UInt64.valueOf(cellIndex),
                UInt64.valueOf(blobIndex)));
      }
      extendedMatrix.add(row);
    }

    return new BlobsAndMatrix(blobs, extendedMatrix);
  }

  protected record BlobsAndMatrix(List<Blob> blobs, List<List<MatrixEntry>> extendedMatrix) {}

  private List<CellData> loadJson() {
    final URL jsonUrl =
        BlobReconstructionAbstractTest.class.getResource(VALID_BLOBS_AND_CELLS_FILENAMES);
    try {
      final String json = Resources.toString(jsonUrl, UTF_8);
      return new ObjectMapper().readValue(json, new TypeReference<>() {});
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String generateJson(final List<CellData> data) {
    try {
      return new ObjectMapper()
          .writerWithDefaultPrettyPrinter() // Optional: makes the JSON human-readable
          .writeValueAsString(data);
    } catch (final JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class CellData {
    @JsonProperty(value = "blob", required = true)
    private String blob;

    @JsonProperty(value = "cells", required = true)
    private List<String> cells;

    public CellData(final String blob, final List<String> cells) {
      this.blob = blob;
      this.cells = cells;
    }

    public CellData() {}
  }
}
