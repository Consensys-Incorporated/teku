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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableComposite;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszPrimitiveListSchemaTest extends SszListSchemaTestBase {

  @Override
  public Stream<SszPrimitiveListSchema<?, ?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 31),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 33),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 63),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 65),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 31),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 32),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 33),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 63),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 64),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BOOLEAN_SCHEMA, 65),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 15),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 16),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 17),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10));
  }

  @Test
  void sanityTest() {
    SszPrimitiveVectorSchema<UInt64, SszUInt64, ?> schema =
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    SszPrimitiveVector<UInt64, SszUInt64> defaultVector = schema.getDefault();
    assertThat(defaultVector).hasSize(10).containsOnly(SszUInt64.of(UInt64.ZERO));
    assertThat(defaultVector.asListUnboxed()).hasSize(10).containsOnly(UInt64.ZERO);

    List<UInt64> uints =
        LongStream.range(1, 11).mapToObj(UInt64::valueOf).collect(Collectors.toList());
    SszPrimitiveVector<UInt64, SszUInt64> vector1 = schema.of(uints);
    assertThat(vector1.asListUnboxed()).hasSize(10).containsSequence(uints);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <T extends SszData> void createTreeFromElements_shouldMatchGenericPath(
      final SszPrimitiveListSchema<?, ?, ?> schema) {
    if (schema.getMaxLength() == 0) {
      return;
    }
    final List<T> elements =
        (List<T>)
            sszDataGenerator
                .withMaxListSize((int) schema.getMaxLength())
                .randomData(schema)
                .asList();
    if (elements.isEmpty()) {
      return;
    }

    // Optimized path (the override in SszPrimitiveListSchemaImpl)
    final TreeNode optimizedTree = schema.createTreeFromElements((List) elements);

    // Generic path: replicate SszCollectionSchema default implementation
    final SszMutableComposite<T> writableCopy =
        (SszMutableComposite<T>) schema.getDefault().createWritableCopy();
    int idx = 0;
    for (T element : elements) {
      writableCopy.set(idx++, element);
    }
    final TreeNode genericTree = writableCopy.commitChanges().getBackingNode();

    assertThat(optimizedTree.hashTreeRoot())
        .describedAs(
            "Hash tree root mismatch for schema %s with %d elements",
            schema.getElementSchema(), elements.size())
        .isEqualTo(genericTree.hashTreeRoot());

    SszList<T> optimizedList = (SszList<T>) schema.createFromBackingNode(optimizedTree);
    SszList<T> genericList = (SszList<T>) schema.createFromBackingNode(genericTree);
    assertThat(optimizedList.sszSerialize())
        .describedAs(
            "SSZ serialization mismatch for schema %s with %d elements",
            schema.getElementSchema(), elements.size())
        .isEqualTo(genericList.sszSerialize());
  }
}
