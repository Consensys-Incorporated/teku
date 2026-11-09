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

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszPrimitiveTest implements SszDataTestBase {

  @Override
  public Stream<? extends SszData> sszData() {
    return Stream.of(
        SszBit.of(true),
        SszBit.of(false),
        SszByte.of(0),
        SszByte.of(1),
        SszByte.of(255),
        SszByte.of(127),
        SszByte.of(128),
        SszBoolean.of(true),
        SszBoolean.of(false),
        SszUInt64.of(UInt64.ZERO),
        SszUInt64.of(UInt64.ONE),
        SszUInt64.of(UInt64.fromLongBits(Long.MAX_VALUE)),
        SszUInt64.of(UInt64.fromLongBits(Long.MIN_VALUE)),
        SszUInt64.of(UInt64.fromLongBits(-1)),
        SszUInt256.of(UInt256.ZERO),
        SszUInt256.of(UInt256.ONE),
        SszUInt256.of(UInt256.MAX_VALUE),
        SszUInt256.of(UInt256.MIN_VALUE),
        SszBytes4.of(Bytes4.fromHexString("0x00000000")),
        SszBytes4.of(Bytes4.fromHexString("0x12345678")),
        SszBytes4.of(Bytes4.fromHexString("0xFFFFFFFF")),
        SszBytes32.of(Bytes32.ZERO),
        SszBytes32.of(
            Bytes32.fromHexString(
                "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF")),
        SszBytes32.of(
            Bytes32.fromHexString(
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")));
  }

  @SuppressWarnings("unchecked")
  @MethodSource("sszDataArguments")
  @ParameterizedTest
  <V, S extends SszPrimitive<V>> void get_roundtrip(final S data) {
    final V rawVal = data.get();
    final SszPrimitiveSchema<V, S> schema = (SszPrimitiveSchema<V, S>) data.getSchema();
    final S data1 = schema.boxed(rawVal);

    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(data);

    final V rawVal1 = data1.get();

    Assertions.assertThat(rawVal1).isEqualTo(rawVal);
  }
}
