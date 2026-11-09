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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class VoluntaryExitTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final UInt64 epoch = dataStructureUtil.randomUInt64();
  private final UInt64 validatorIndex = dataStructureUtil.randomUInt64();

  private final VoluntaryExit voluntaryExit = new VoluntaryExit(epoch, validatorIndex);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    VoluntaryExit testVoluntaryExit = voluntaryExit;

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    VoluntaryExit testVoluntaryExit = new VoluntaryExit(epoch, validatorIndex);

    assertEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenEpochsAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch.plus(dataStructureUtil.randomUInt64()), validatorIndex);

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void equalsReturnsFalseWhenValidatorIndicesAreDifferent() {
    VoluntaryExit testVoluntaryExit =
        new VoluntaryExit(epoch, validatorIndex.plus(dataStructureUtil.randomUInt64()));

    assertNotEquals(voluntaryExit, testVoluntaryExit);
  }

  @Test
  void roundtripSSZ() {
    Bytes sszVoluntaryExitBytes = voluntaryExit.sszSerialize();
    assertEquals(voluntaryExit, VoluntaryExit.SSZ_SCHEMA.sszDeserialize(sszVoluntaryExitBytes));
  }
}
