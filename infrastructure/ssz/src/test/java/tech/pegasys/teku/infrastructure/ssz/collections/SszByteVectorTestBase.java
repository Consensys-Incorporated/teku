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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public interface SszByteVectorTestBase extends SszPrimitiveCollectionTestBase {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void getBytes_shouldReturnAllBytes(final SszByteVector vector) {
    Bytes bytes = vector.getBytes();

    assertThat(bytes.size()).isEqualTo(vector.size());
    for (int i = 0; i < bytes.size(); i++) {
      assertThat(bytes.get(i)).isEqualTo(vector.getElement(i));
    }
  }
}
