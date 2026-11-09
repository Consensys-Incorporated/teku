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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszMutableCollectionTestBase;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;

public interface SszMutablePrimitiveCollectionTestBase
    extends SszPrimitiveCollectionTestBase, SszMutableCollectionTestBase {

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void setElement_throwsIndexOutOfBounds(
      final SszMutablePrimitiveCollection<ElT, SszT> collection) {
    assertThatThrownBy(
            () ->
                collection.setElement(
                    collection.size() + 1,
                    collection.getPrimitiveElementSchema().getDefault().get()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void setElement_extendsExtendableCollection(
      final SszMutablePrimitiveCollection<ElT, SszT> collection) {
    if (collection.size() < collection.getSchema().getMaxLength()) {
      // collection is extendable (List effectively)
      int origSize = collection.size();
      ElT newElement = collection.getPrimitiveElementSchema().getDefault().get();
      collection.setElement(collection.size(), newElement);

      assertThat(collection.size()).isEqualTo(origSize + 1);
      assertThat(collection.get(origSize)).isEqualTo(newElement);

      SszPrimitiveCollection<ElT, SszT> immCollection = collection.commitChanges();

      assertThat(immCollection.size()).isEqualTo(origSize + 1);
      assertThat(immCollection.get(origSize)).isEqualTo(newElement);
    }
  }
}
