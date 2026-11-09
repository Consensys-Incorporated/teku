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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.custom.SszCustomBytes48Schema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;

public class SszByteVectorSchemaTest extends SszVectorSchemaTestBase {

  private final Random random = new Random(1);

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return Stream.of(
        SszByteVectorSchema.create(1),
        SszByteVectorSchema.create(31),
        SszByteVectorSchema.create(32),
        SszByteVectorSchema.create(33),
        SszByteVectorSchema.create(63),
        SszByteVectorSchema.create(64),
        SszByteVectorSchema.create(65),
        SszCustomBytes48Schema.INSTANCE);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <T extends SszByteVector> void fromBytes_shouldCreateCorrectClassInstance(
      final SszByteVectorSchema<T> schema) {
    List<Bytes> byteList =
        Stream.generate(() -> Bytes.of(random.nextInt(256))).limit(schema.getLength()).toList();
    Bytes bytes = Bytes.wrap(byteList);
    T vector = schema.fromBytes(bytes);
    assertThat(vector.getBytes()).isEqualTo(bytes);
    assertThat(vector.getClass()).isEqualTo(schema.getDefault().getClass());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <T extends SszByteVector> void fromBytes_shouldThrowWhenWrongSize(
      final SszByteVectorSchema<T> schema) {
    List<Bytes> byteList =
        Stream.generate(() -> Bytes.of(random.nextInt(256))).limit(schema.getLength()).toList();
    Bytes correctBytes = Bytes.wrap(byteList);

    assertThatThrownBy(() -> schema.fromBytes(Bytes.wrap(correctBytes, Bytes.of(111))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> schema.fromBytes(correctBytes.slice(0, correctBytes.size() - 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
