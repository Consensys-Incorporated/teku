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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.util.KzgUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;

@Fork(1)
@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10)
public class WithPrecomputeBenchmark {
  @State(Scope.Benchmark)
  public static class ExecutionPlan {
    public SidecarBenchmarkConfig config;

    @Param({"true", "false"})
    public boolean isRustEnabled;

    @Param({"true", "false"})
    public boolean isPrecompute;

    @Setup(Level.Invocation)
    public void setup() {
      config = new SidecarBenchmarkConfig(isPrecompute, isRustEnabled);
    }
  }

  @Benchmark
  public void computeExtendedMatrixAndProofs(final ExecutionPlan plan) {
    plan.config.miscHelpersFulu.computeExtendedMatrix(
        plan.config.blobs.stream()
            .map((b) -> KzgUtil.computeBlobAndCellProofs(plan.config.miscHelpersFulu.getKzg(), b))
            .toList());
  }

  @Benchmark
  public void constructDataColumnSidecars(final ExecutionPlan plan) {
    plan.config.miscHelpersFulu.constructDataColumnSidecars(
        plan.config.signedBeaconBlock.getMessage(),
        plan.config.signedBeaconBlock.asHeader(),
        plan.config.extendedMatrix);
  }

  @Benchmark
  public void reconstructDataColumnSidecars(final ExecutionPlan plan) {
    final int size = plan.config.dataColumnSidecars.size();
    final int halfSize = size / 2;
    plan.config.miscHelpersFulu.reconstructAllDataColumnSidecars(
        plan.config.dataColumnSidecars.subList(halfSize, size));
  }

  /** Similar to {@link DataColumnSidecarArchiveReconstructor} */
  @Benchmark
  public void reconstructDataColumnSidecarsFromArchive(final ExecutionPlan plan) {
    final BlobSchema blobSchema = plan.config.schemaDefinitionsFulu.getBlobSchema();
    final int size = plan.config.dataColumnSidecars.size();
    final int halfSize = size / 2;
    final List<DataColumnSidecar> sidecars = plan.config.dataColumnSidecars.subList(0, halfSize);

    final List<List<KZGProof>> kzgProofs =
        plan.config.dataColumnSidecars.subList(halfSize, size).stream()
            .map(sidecar -> sidecar.getKzgProofs().stream().map(SszKZGProof::getKZGProof).toList())
            .toList();
    final List<BlobAndCellProofs> blobAndCellProofsList = new ArrayList<>();
    for (int i = 0; i < sidecars.getFirst().getMaybeKzgCommitments().orElseThrow().size(); i++) {
      final int blobIndex = i;
      final Bytes blob =
          sidecars.stream()
              .map(sidecar -> sidecar.getColumn().get(blobIndex).getBytes())
              .reduce(Bytes.EMPTY, Bytes::concatenate);
      final List<KZGProof> blobProofs = new ArrayList<>();
      for (int j = 0; j < halfSize; j++) {
        blobProofs.add(sidecars.get(j).getKzgProofs().get(blobIndex).getKZGProof());
      }
      for (int j = 0; j < halfSize; j++) {
        blobProofs.add(kzgProofs.get(j).get(blobIndex));
      }
      final BlobAndCellProofs blobAndCellProofs =
          new BlobAndCellProofs(blobSchema.create(blob), blobProofs);
      blobAndCellProofsList.add(blobAndCellProofs);
    }

    plan.config.miscHelpersFulu.constructDataColumnSidecars(
        plan.config.signedBeaconBlock, blobAndCellProofsList);
  }
}
