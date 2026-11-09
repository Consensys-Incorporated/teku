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

package tech.pegasys.teku.reference.phase0.kzg;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCell;
import tech.pegasys.teku.kzg.KZGCellAndProof;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.kzg.KZGProof;

public class KzgRecoverCellsAndKzgProofsTestExecutor extends KzgTestExecutor {
  @Override
  public void runTest(final TestDefinition testDefinition, final KZG kzg) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final List<KZGCellAndProof> expectedKzgCells = data.getOutput();
    List<KZGCellAndProof> actualKzgCells;
    try {
      final List<Integer> cellIds = data.getInput().getCellIndices();
      final List<KZGCell> cells = data.getInput().getCells();
      if (cells.size() != cellIds.size()) {
        throw new RuntimeException("Cells doesn't match ids");
      }
      final List<KZGCellWithColumnId> cellWithIds =
          Streams.zip(cells.stream(), cellIds.stream(), KZGCellWithColumnId::fromCellAndColumn)
              .toList();
      actualKzgCells = kzg.recoverCellsAndProofs(cellWithIds);
    } catch (final RuntimeException ex) {
      actualKzgCells = null;
    }
    assertThat(actualKzgCells).isEqualTo(expectedKzgCells);
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private List<List<String>> output;

    public Input getInput() {
      return input;
    }

    public List<KZGCellAndProof> getOutput() {
      if (output == null) {
        return null;
      }
      final List<String> cellStrings = output.get(0);
      final List<String> proofStrings = output.get(1);
      if (cellStrings.size() != proofStrings.size()) {
        throw new RuntimeException("Number of cells and proofs should be the same");
      }
      return IntStream.range(0, cellStrings.size())
          .mapToObj(
              index ->
                  new KZGCellAndProof(
                      new KZGCell(Bytes.fromHexString(cellStrings.get(index))),
                      KZGProof.fromHexString(proofStrings.get(index))))
          .toList();
    }

    private static class Input {
      @JsonProperty(value = "cell_indices", required = true)
      private List<Integer> cellIndices;

      @JsonProperty(value = "cells", required = true)
      private List<String> cells;

      @JsonProperty(value = "proofs", required = true)
      private List<String> proofs;

      public List<Integer> getCellIndices() {
        return cellIndices;
      }

      public List<KZGCell> getCells() {
        return cells.stream()
            .map(cellString -> new KZGCell(Bytes.fromHexString(cellString)))
            .toList();
      }

      @SuppressWarnings("Unused")
      public List<KZGProof> getProfos() {
        return proofs.stream()
            .map(proofString -> new KZGProof(Bytes48.fromHexString(proofString)))
            .toList();
      }
    }
  }
}
