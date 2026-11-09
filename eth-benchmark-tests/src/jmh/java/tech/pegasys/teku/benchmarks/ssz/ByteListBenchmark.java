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

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
public class ByteListBenchmark {

  private static final long MAX_LIST_SIZE = 1L << 40; // Max Validators on Mainnet
  private static final int LIST_SIZE = 1 << 19; // ~500K, MainNet state participation flags
  private static final SszByteListSchema<?> LIST_SCHEMA = SszByteListSchema.create(MAX_LIST_SIZE);
  private static final TreeNode LIST_TREE =
      LIST_SCHEMA.fromBytes(Bytes.wrap(new byte[LIST_SIZE])).getBackingNode();

  private static final BLSPublicKey PUBLIC_KEY = BLSTestUtil.randomPublicKey(1);
  private static final DataStructureUtil DATA_STRUCTURE_UTIL =
      new DataStructureUtil(TestSpecFactory.createMainnetAltair())
          .withPubKeyGenerator(() -> PUBLIC_KEY);
  private static final BeaconStateAltair STATE =
      (BeaconStateAltair) DATA_STRUCTURE_UTIL.randomBeaconState(LIST_SIZE);
  private static final BeaconStateSchemaAltair STATE_SCHEMA =
      (BeaconStateSchemaAltair) STATE.getBeaconStateSchema();
  private static final TreeNode STATE_TREE = STATE.getBackingNode();

  @Benchmark
  public void iterateBoxed(Blackhole bh) {
    SszByteList list = LIST_SCHEMA.createFromBackingNode(LIST_TREE);
    for (SszByte sszByte : list) {
      bh.consume(sszByte);
    }
  }

  @Benchmark
  public void iterateUnboxed(Blackhole bh) {
    SszByteList list = LIST_SCHEMA.createFromBackingNode(LIST_TREE);
    List<Byte> unboxedList = list.asListUnboxed();
    for (Byte bb : unboxedList) {
      bh.consume(bb);
    }
  }

  @Benchmark
  public void serialIndexedAccess(Blackhole bh) {
    SszByteList list = LIST_SCHEMA.createFromBackingNode(LIST_TREE);
    int size = list.size();
    for (int i = 0; i < size; i++) {
      Byte bb = list.getElement(i);
      bh.consume(bb);
    }
  }

  @Benchmark
  public void randomIndexedAccess(Blackhole bh) {
    SszByteList list = LIST_SCHEMA.createFromBackingNode(LIST_TREE);
    int size = list.size();
    for (int i = 0; i < 1024; i++) {
      for (int j = 0; j < size; j += 1024) {
        Byte bb = list.getElement(i + j);
        bh.consume(bb);
      }
    }
  }

  @Benchmark
  public void iterateBeaconStateParticipationFlags(Blackhole bh) {
    BeaconStateAltair freshState = STATE_SCHEMA.createFromBackingNode(STATE_TREE);
    for (SszByte aByte : freshState.getCurrentEpochParticipation()) {
      bh.consume(aByte);
    }
  }
}
