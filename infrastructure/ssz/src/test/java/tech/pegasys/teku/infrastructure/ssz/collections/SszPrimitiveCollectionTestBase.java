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

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszCollectionTestBase;
import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;

public interface SszPrimitiveCollectionTestBase extends SszCollectionTestBase {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void getElement_shouldReturnUnboxedElement(
      final SszPrimitiveCollection<ElT, SszT> collection) {
    for (int i = 0; i < collection.size(); i++) {
      assertThat(collection.getElement(i)).isEqualTo(collection.get(i).get());
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getElement_shouldThrowIndexOfBounds(final SszPrimitiveCollection<?, ?> collection) {
    assertThatThrownBy(() -> collection.getElement(-1))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> collection.getElement(collection.size()))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(
            () ->
                collection.getElement(
                    (int) Long.min(Integer.MAX_VALUE, collection.getSchema().getMaxLength())))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void asListUnboxed_shouldReturnAllElements(
      final SszPrimitiveCollection<ElT, SszT> collection) {
    List<ElT> listUnboxed = collection.asListUnboxed();
    assertThat(listUnboxed.size()).isEqualTo(collection.size());
    for (int i = 0; i < collection.size(); i++) {
      assertThat(listUnboxed.get(i)).isEqualTo(collection.getElement(i));
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void asListUnboxed_isUnmodifiable(
      final SszPrimitiveCollection<ElT, SszT> collection) {
    List<ElT> list = collection.asListUnboxed();
    ElT newElement = collection.getSchema().getElementSchema().getDefault().get();
    assertThatThrownBy(() -> list.set(0, newElement))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <ElT, SszT extends SszPrimitive<ElT>> void streamUnboxed_shouldReturnAllElements(
      final SszPrimitiveCollection<ElT, SszT> collection) {
    assertThat(collection.streamUnboxed()).containsExactlyElementsOf(collection.asListUnboxed());
  }
}
