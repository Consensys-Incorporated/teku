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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconBlockHeaderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final UInt64 slot = dataStructureUtil.randomUInt64();
  private final UInt64 proposerIndex = dataStructureUtil.randomUInt64();
  private final Bytes32 previousBlockRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 blockBodyRoot = dataStructureUtil.randomBytes32();

  private final BeaconBlockHeader beaconBlockHeader =
      new BeaconBlockHeader(slot, proposerIndex, previousBlockRoot, stateRoot, blockBodyRoot);

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    BeaconBlockHeader testBeaconBlockHeaderSignedData = beaconBlockHeader;

    assertEquals(beaconBlockHeader, testBeaconBlockHeaderSignedData);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(slot, proposerIndex, previousBlockRoot, stateRoot, blockBodyRoot);

    assertEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot.plus(dataStructureUtil.randomUInt64()),
            proposerIndex,
            previousBlockRoot,
            stateRoot,
            blockBodyRoot);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenProposerIndexIsDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot,
            proposerIndex.plus(dataStructureUtil.randomUInt64()),
            previousBlockRoot,
            stateRoot,
            blockBodyRoot);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenPreviousBlockRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, previousBlockRoot.not(), stateRoot, blockBodyRoot);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenStateRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, previousBlockRoot, stateRoot.not(), blockBodyRoot);

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void equalsReturnsFalseWhenBlockBodyRootsAreDifferent() {
    BeaconBlockHeader testBeaconBlockHeader =
        new BeaconBlockHeader(
            slot, proposerIndex, previousBlockRoot, stateRoot, blockBodyRoot.not());

    assertNotEquals(beaconBlockHeader, testBeaconBlockHeader);
  }

  @Test
  void roundtripSSZ() {
    BeaconBlockHeader beaconBlockHeader = dataStructureUtil.randomBeaconBlockHeader();
    Bytes beaconBlockSerialized = beaconBlockHeader.sszSerialize();
    BeaconBlockHeader newBeaconBlockHeader =
        BeaconBlockHeader.SSZ_SCHEMA.sszDeserialize(beaconBlockSerialized);
    assertEquals(beaconBlockHeader, newBeaconBlockHeader);
  }

  @Test
  void blockRootHeaderRootMatchingTests() {
    BeaconBlock block = dataStructureUtil.randomBeaconBlock(90000000);
    BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getProposerIndex(),
            block.getParentRoot(),
            block.getStateRoot(),
            block.getBody().hashTreeRoot());
    assertEquals(block.hashTreeRoot(), blockHeader.hashTreeRoot());
  }
}
