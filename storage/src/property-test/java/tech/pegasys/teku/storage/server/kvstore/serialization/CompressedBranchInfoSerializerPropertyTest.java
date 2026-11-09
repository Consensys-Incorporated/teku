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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER;

import com.google.common.primitives.Bytes;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Positive;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;

public class CompressedBranchInfoSerializerPropertyTest {
  @Property
  void roundTrip(
      @ForAll @Positive final int depth, @ForAll("bytes32array") final Bytes32[] children) {
    final TreeNodeSource.CompressedBranchInfo value =
        new TreeNodeSource.CompressedBranchInfo(depth, children);
    final byte[] serialized = COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER.serialize(value);
    final TreeNodeSource.CompressedBranchInfo deserialized =
        COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).usingDefaultComparator().isEqualTo(value);
  }

  @Provide
  public Arbitrary<Bytes32[]> bytes32array() {
    return Arbitraries.bytes()
        .list()
        .ofSize(32)
        .map(Bytes::toArray)
        .map(Bytes32::wrap)
        .array(Bytes32[].class)
        .ofMaxSize(1000);
  }
}
