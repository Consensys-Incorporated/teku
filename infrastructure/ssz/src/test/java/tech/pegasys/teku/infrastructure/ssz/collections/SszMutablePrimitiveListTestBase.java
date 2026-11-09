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

package tech.pegasys.teku.infrastructure.ssz.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.infrastructure.ssz.SszListTestBase;
import tech.pegasys.teku.infrastructure.ssz.SszMutableCollectionTestBase;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;

public interface SszMutablePrimitiveListTestBase
    extends SszListTestBase, SszMutableCollectionTestBase {

  RandomSszDataGenerator GENERATOR = new RandomSszDataGenerator();

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void append_extendsExtendableCollection(
      final SszMutablePrimitiveList<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();
      ElT newElement = collection.getPrimitiveElementSchema().getDefault().get();
      collection.appendElement(newElement);

      assertThat(collection.size()).isEqualTo(origSize + 1);
      assertThat(collection.getElement(origSize)).isEqualTo(newElement);

      SszPrimitiveCollection<ElT, SszT> immCollection = collection.commitChanges();

      assertThat(immCollection.size()).isEqualTo(origSize + 1);
      assertThat(immCollection.getElement(origSize)).isEqualTo(newElement);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void appendAllElements_extendsExtendableCollection(
      final SszMutablePrimitiveList<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();

      int appendListSize = Integer.min(3, (int) (collection.getSchema().getMaxLength() - origSize));
      List<ElT> appendList =
          Stream.generate(() -> GENERATOR.randomData(collection.getPrimitiveElementSchema()))
              .limit(appendListSize)
              .map(SszPrimitive::get)
              .collect(Collectors.toList());
      collection.appendAllElements(appendList);

      assertThat(collection.size()).isEqualTo(origSize + appendListSize);
      for (int i = 0; i < appendListSize; i++) {
        assertThat(collection.getElement(origSize + i)).isEqualTo(appendList.get(i));
      }
    }
  }
}
