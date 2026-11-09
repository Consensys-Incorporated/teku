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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class CompressedBranchInfoSerializerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final CompressedBranchInfoSerializer serializer = new CompressedBranchInfoSerializer();

  @Test
  void shouldRoundTripCompressedBranchInfo() {
    assertRoundTrip(
        4,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());
  }

  @Test
  void shouldRoundTripWithNoChildren() {
    assertRoundTrip(0);
  }

  @Test
  void shouldRoundTripWithMaxValueDepth() {
    assertRoundTrip(
        Integer.MAX_VALUE, dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32());
  }

  private void assertRoundTrip(final int depth, final Bytes32... children) {
    final CompressedBranchInfo input = new CompressedBranchInfo(depth, children);
    final byte[] serialized = serializer.serialize(input);
    final CompressedBranchInfo output = serializer.deserialize(serialized);
    assertThat(output).usingDefaultComparator().isEqualTo(input);
  }
}
