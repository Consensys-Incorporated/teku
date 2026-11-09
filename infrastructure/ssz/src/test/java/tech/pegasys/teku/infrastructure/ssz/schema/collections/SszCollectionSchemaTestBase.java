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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchemaTestBase;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchemaTest;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchemaTest;

public abstract class SszCollectionSchemaTestBase extends SszCompositeSchemaTestBase {

  final RandomSszDataGenerator sszDataGenerator = new RandomSszDataGenerator();

  static Stream<SszSchema<?>> complexElementSchemas() {
    return Stream.of(
            SszContainerSchemaTest.testContainerSchemas(),
            SszUnionSchemaTest.testUnionSchemas(),
            Stream.of(
                SszBitvectorSchema.create(1),
                SszBitvectorSchema.create(8),
                SszBitvectorSchema.create(9),
                SszBitlistSchema.create(0),
                SszBitlistSchema.create(1),
                SszBitlistSchema.create(7),
                SszBitlistSchema.create(8),
                SszBitlistSchema.create(9),
                SszByteVectorSchema.create(3),
                SszBytes32VectorSchema.create(3),
                SszUInt64ListSchema.create(3)))
        .flatMap(Function.identity());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <T extends SszData> void createFromElements_shouldCreateCorrectListOfTheSameClass(
      final SszCollectionSchema<T, ? extends SszCollection<T>> schema) {
    List<T> elements = sszDataGenerator.randomData(schema).asList();
    SszCollection<T> collection = schema.createFromElements(elements);
    assertThat(collection).containsExactlyElementsOf(elements);
    assertThat(collection.getClass()).isEqualTo(schema.getDefault().getClass());
  }
}
