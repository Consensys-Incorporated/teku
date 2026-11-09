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

package tech.pegasys.teku.spec.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszTestUtils;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class HistoricalBatchTest {

  private static final Spec SPEC = TestSpecFactory.createMainnetPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  private final HistoricalBatchSchema schema =
      SPEC.getGenesisSchemaDefinitions().getHistoricalBatchSchema();

  @Test
  void vectorLengthsTest() {
    final int slotsPerHistoricalRoot = SPEC.getGenesisSpecConfig().getSlotsPerHistoricalRoot();
    IntList vectorLengths = IntList.of(slotsPerHistoricalRoot, slotsPerHistoricalRoot);
    assertEquals(vectorLengths, SszTestUtils.getVectorLengths(schema));
  }

  @Test
  void roundTripViaSsz() {
    SszMutableBytes32Vector blockRoots =
        schema.getBlockRootsSchema().getDefault().createWritableCopy();
    SszMutableBytes32Vector stateRoots =
        schema.getStateRootsSchema().getDefault().createWritableCopy();
    IntStream.range(0, SPEC.getGenesisSpecConfig().getSlotsPerHistoricalRoot())
        .forEach(
            i -> {
              blockRoots.setElement(i, dataStructureUtil.randomBytes32());
              stateRoots.setElement(i, dataStructureUtil.randomBytes32());
            });
    HistoricalBatch batch = schema.create(blockRoots, stateRoots);
    Bytes serialized = batch.sszSerialize();
    HistoricalBatch result = schema.sszDeserialize(serialized);
    assertEquals(batch, result);
  }
}
