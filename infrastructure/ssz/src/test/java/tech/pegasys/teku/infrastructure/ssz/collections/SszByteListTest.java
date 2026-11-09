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
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;

/**
 * {@link SszByteList#getBytes()} must return exactly the list content for every backing-tree shape:
 * empty, partial trailing chunk, exact chunk boundaries, multi-level trees, and both the regular
 * and the progressive (EIP-7916) merkleization.
 */
class SszByteListTest {

  private static final int MAX_LENGTH = 1 << 20;

  // Sizes chosen to cross 32-byte chunk boundaries and progressive level boundaries
  // (progressive levels hold 1, 4, 16, 64, ... chunks).
  private static final int[] SIZES = {
    0, 1, 31, 32, 33, 63, 64, 65, 96, 97, 160, 161, 193, 1000, 2047, 2048, 2049, 100_000
  };

  static Stream<Arguments> schemasAndSizes() {
    final List<SszByteListSchema<?>> schemas =
        List.of(
            SszByteListSchema.create(MAX_LENGTH),
            new SszProgressiveByteListSchema<>(),
            new SszProgressiveByteListSchema<>(SszPrimitiveSchemas.UINT8_SCHEMA));
    return schemas.stream()
        .flatMap(
            schema ->
                IntStream.of(SIZES)
                    .mapToObj(
                        size -> Arguments.of(schema.getClass().getSimpleName(), schema, size)));
  }

  private static Bytes randomBytes(final int size) {
    final byte[] data = new byte[size];
    new Random(size).nextBytes(data);
    return Bytes.wrap(data);
  }

  @ParameterizedTest(name = "{0} size={2}")
  @MethodSource("schemasAndSizes")
  void getBytes_shouldReturnContentOfListBuiltFromBytes(
      final String name, final SszByteListSchema<?> schema, final int size) {
    final Bytes bytes = randomBytes(size);
    final SszByteList list = schema.sszDeserialize(bytes);

    assertThat(list.size()).isEqualTo(size);
    assertThat(list.getBytes()).isEqualTo(bytes);
  }

  @ParameterizedTest(name = "{0} size={2}")
  @MethodSource("schemasAndSizes")
  void getBytes_shouldReturnContentOfListBuiltFromElements(
      final String name, final SszByteListSchema<?> schema, final int size) {
    final Bytes bytes = randomBytes(size);
    final List<SszByte> elements =
        IntStream.range(0, size)
            .mapToObj(i -> schema.getPrimitiveElementSchema().boxed(bytes.get(i)))
            .toList();
    final SszByteList list = schema.createFromElements(elements);

    assertThat(list.size()).isEqualTo(size);
    assertThat(list.getBytes()).isEqualTo(bytes);
  }

  @ParameterizedTest(name = "{0} size={2}")
  @MethodSource("schemasAndSizes")
  void getBytes_shouldPreserveAllZeroContent(
      final String name, final SszByteListSchema<?> schema, final int size) {
    // All-zero chunks are backed by shared zero leaf nodes, so make sure their length is preserved
    final Bytes zeros = Bytes.wrap(new byte[size]);
    final SszByteList list = schema.sszDeserialize(zeros);

    assertThat(list.size()).isEqualTo(size);
    assertThat(list.getBytes()).isEqualTo(zeros);
  }
}
