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

package tech.pegasys.teku.benchmarks.ssz;

import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DataColumnSidecarDeserializationBenchmark {

  private static final UInt64 SLOT = UInt64.ONE;

  @Param({"1", "72"})
  public int blobCount;

  private DataColumnSidecarSchema<DataColumnSidecar> schema;
  private Bytes serializedSidecar;

  @Setup(Level.Trial)
  public void setup() {
    final Spec spec = TestSpecFactory.createMainnetFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(1, spec);
    schema =
        SchemaDefinitionsFulu.required(spec.atSlot(SLOT).getSchemaDefinitions())
            .getDataColumnSidecarSchema()
            .toBaseSchema();

    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader(SLOT);
    final DataColumn dataColumn = dataStructureUtil.randomDataColumn(SLOT, blobCount);
    final DataColumnSidecar sidecar =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(SLOT)
            .index(UInt64.ZERO)
            .signedBeaconBlockHeader(signedBeaconBlockHeader)
            .kzgCommitments(dataStructureUtil.randomKZGCommitments(blobCount))
            .kzgProofs(dataStructureUtil.randomKZGProofs(blobCount))
            .dataColumn(dataColumn)
            .build();

    serializedSidecar = sidecar.sszSerialize();
  }

  @Benchmark
  public DataColumnSidecar deserializeDataColumnSidecar() {
    return schema.sszDeserialize(serializedSidecar);
  }
}
