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

import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class SszCheckpointBenchmark extends SszAbstractContainerBenchmark<Checkpoint> {

  private static final Bytes32 ROOT = Bytes32.random();

  @Override
  protected Checkpoint createContainer() {
    return new Checkpoint(UInt64.valueOf(0x112233), ROOT);
  }

  @Override
  protected SszSchema<Checkpoint> getContainerType() {
    return Checkpoint.SSZ_SCHEMA;
  }

  @Override
  protected void iterateData(Checkpoint pa, Blackhole bh) {
    SszBenchUtil.iterateData(pa, bh);
  }
}
