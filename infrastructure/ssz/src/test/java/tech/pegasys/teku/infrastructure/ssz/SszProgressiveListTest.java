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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveListTest implements SszCollectionTestBase {

  private static final SszProgressiveListSchema<SszUInt64> UINT64_LIST_SCHEMA =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);

  private static SszList<SszUInt64> createUInt64List(final int count) {
    final List<SszUInt64> elements =
        IntStream.range(0, count)
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(i)))
            .collect(Collectors.toList());
    final TreeNode tree = UINT64_LIST_SCHEMA.createTreeFromElements(elements);
    return UINT64_LIST_SCHEMA.createFromBackingNode(tree);
  }

  @Override
  public Stream<SszList<SszUInt64>> sszData() {
    return Stream.of(
        createUInt64List(0),
        createUInt64List(1),
        createUInt64List(2),
        createUInt64List(3),
        createUInt64List(10),
        createUInt64List(255),
        createUInt64List(256),
        createUInt64List(257),
        createUInt64List(1000));
  }

  @Test
  void elementAccess_shouldReturnCorrectValues() {
    final SszList<SszUInt64> list = createUInt64List(5);
    for (int i = 0; i < 5; i++) {
      assertThat(list.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }
  }

  @Test
  void createWritableCopy_shouldSucceed() {
    final SszList<SszUInt64> list = createUInt64List(3);
    assertThat(list.createWritableCopy()).isNotNull();
  }

  @Test
  void isWritableSupported_shouldReturnTrue() {
    final SszList<SszUInt64> list = createUInt64List(1);
    assertThat(list.isWritableSupported()).isTrue();
  }
}
