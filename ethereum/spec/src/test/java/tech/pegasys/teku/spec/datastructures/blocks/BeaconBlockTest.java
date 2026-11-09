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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconBlockTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = dataStructureUtil.randomUInt64();
  private final UInt64 proposerIndex = dataStructureUtil.randomUInt64();
  private final Bytes32 previousRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
  private final BeaconBlockBody body = dataStructureUtil.randomBeaconBlockBody();
  private final BeaconBlockSchema blockSchema =
      spec.getGenesisSchemaDefinitions().getBeaconBlockSchema();

  private final BeaconBlock beaconBlock =
      new BeaconBlock(blockSchema, slot, proposerIndex, previousRoot, stateRoot, body);

  @Test
  void equalsReturnsTrueWhenObjectsAreSame() {
    BeaconBlock testBeaconBlock = beaconBlock;

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(blockSchema, slot, proposerIndex, previousRoot, stateRoot, body);

    assertEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(
            blockSchema, slot.plus(UInt64.ONE), proposerIndex, previousRoot, stateRoot, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenProposersAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(
            blockSchema, slot, proposerIndex.plus(UInt64.ONE), previousRoot, stateRoot, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenParentRootsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(blockSchema, slot, proposerIndex, previousRoot.not(), stateRoot, body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlock testBeaconBlock =
        new BeaconBlock(blockSchema, slot, proposerIndex, previousRoot, stateRoot.not(), body);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void equalsReturnsFalseWhenBeaconBlockBodiesAreDifferent() {
    // BeaconBlockBody is rather involved to create. Just create a random one until it is not the
    // same
    // as the original.
    BeaconBlockBody otherBody = dataStructureUtil.randomBeaconBlockBody();
    while (Objects.equals(otherBody, body)) {
      otherBody = dataStructureUtil.randomBeaconBlockBody();
    }

    BeaconBlock testBeaconBlock =
        new BeaconBlock(blockSchema, slot, proposerIndex, previousRoot, stateRoot, otherBody);

    assertNotEquals(beaconBlock, testBeaconBlock);
  }

  @Test
  void roundtripSSZ() {
    final Bytes ssz = beaconBlock.sszSerialize();
    final BeaconBlock result = blockSchema.sszDeserialize(ssz);
    assertThat(result).isEqualTo(beaconBlock);
  }
}
