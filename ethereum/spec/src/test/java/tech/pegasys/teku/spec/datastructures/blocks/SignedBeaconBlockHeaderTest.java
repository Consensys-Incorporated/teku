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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBeaconBlockHeaderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final UInt64 slot = dataStructureUtil.randomUInt64();
  private final UInt64 proposerIndex = dataStructureUtil.randomUInt64();
  private final Bytes32 previousBlockRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
  private final Bytes32 blockBodyRoot = dataStructureUtil.randomBytes32();
  private final BLSSignature signature = dataStructureUtil.randomSignature();

  private final SignedBeaconBlockHeader signedBlockHeader =
      new SignedBeaconBlockHeader(
          new BeaconBlockHeader(slot, proposerIndex, previousBlockRoot, stateRoot, blockBodyRoot),
          signature);

  @Test
  public void shouldRoundTripViaSsz() {
    final Bytes ssz = signedBlockHeader.sszSerialize();
    final SignedBeaconBlockHeader result = SignedBeaconBlockHeader.SSZ_SCHEMA.sszDeserialize(ssz);

    assertThat(result).isEqualTo(signedBlockHeader);
  }
}
