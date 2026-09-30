/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszProgressiveByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.sos.SszMaxLengthExceededException;

class SszProgressiveByteListSchemaTest {

  private static final SszProgressiveByteListSchema<SszByteList> SCHEMA =
      new SszProgressiveByteListSchema<>();
  private static final SszProgressiveByteListSchema<SszByteList> LIMITED_SCHEMA =
      new SszProgressiveByteListSchema<>(4);
  private static final SszProgressiveByteListSchema<SszByteList> UINT8_SCHEMA =
      new SszProgressiveByteListSchema<>(SszPrimitiveSchemas.UINT8_SCHEMA);

  @Test
  void fromBytes_shouldPreserveBytes() {
    final Bytes bytes = Bytes.fromHexString("0x010203");
    final SszByteList byteList = SCHEMA.fromBytes(bytes);

    assertThat(byteList).isInstanceOf(SszProgressiveByteListImpl.class);
    assertThat(byteList.size()).isEqualTo(3);
    assertThat(byteList.getBytes()).isEqualTo(bytes);
  }

  // 97 bytes (4 chunks) fills progressive levels 0-1; 193 bytes (7 chunks) reaches level 2
  @ParameterizedTest
  @ValueSource(ints = {97, 193})
  void fromBytes_shouldPreserveBytesAcrossMultipleChunks(final int size) {
    final Bytes bytes = Bytes.of(IntStream.range(0, size).map(i -> (i * 17) & 0xFF).toArray());
    final SszByteList byteList = SCHEMA.fromBytes(bytes);

    assertThat(byteList).isInstanceOf(SszProgressiveByteListImpl.class);
    assertThat(byteList.size()).isEqualTo(size);
    assertThat(byteList.getBytes()).isEqualTo(bytes);
    assertThat(SCHEMA.sszDeserialize(byteList.sszSerialize()).hashTreeRoot())
        .isEqualTo(byteList.hashTreeRoot());
  }

  @Test
  void sszRoundTrip_shouldPreserveBytesAndRoot() {
    final Bytes bytes = Bytes.fromHexString("0x010203");
    final SszByteList byteList = SCHEMA.fromBytes(bytes);

    final Bytes ssz = byteList.sszSerialize();
    final SszByteList deserialized = SCHEMA.sszDeserialize(ssz);

    assertThat(deserialized.getBytes()).isEqualTo(bytes);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(byteList.hashTreeRoot());
  }

  @Test
  void emptyList_roundTrips() {
    final SszByteList empty = SCHEMA.fromBytes(Bytes.EMPTY);
    assertThat(empty.size()).isZero();
    assertThat(empty.getBytes()).isEqualTo(Bytes.EMPTY);
    assertThat(SCHEMA.sszDeserialize(empty.sszSerialize()).size()).isZero();
  }

  @Test
  void jsonSerialization_shouldUseHexString() throws JsonProcessingException {
    final SszByteList byteList = SCHEMA.fromBytes(Bytes.fromHexString("0x010203"));

    assertThat(JsonUtil.serialize(byteList, SCHEMA.getJsonTypeDefinition()))
        .isEqualTo("\"0x010203\"");
  }

  @Test
  void jsonDeserialization_shouldReadHexString() throws JsonProcessingException {
    final SszByteList byteList = JsonUtil.parse("\"0x010203\"", SCHEMA.getJsonTypeDefinition());

    assertThat(byteList.getBytes()).isEqualTo(Bytes.fromHexString("0x010203"));
  }

  @Test
  void uint8JsonSerialization_shouldUseUint8Array() throws JsonProcessingException {
    final SszByteList byteList =
        UINT8_SCHEMA.createFromElements(
            List.of(SszByte.asUInt8(1), SszByte.asUInt8(2), SszByte.asUInt8(3)));

    assertThat(JsonUtil.serialize(byteList, UINT8_SCHEMA.getJsonTypeDefinition()))
        .isEqualTo("[\"1\",\"2\",\"3\"]");
  }

  @Test
  void uint8JsonDeserialization_shouldReadUint8Array() throws JsonProcessingException {
    final SszByteList byteList =
        JsonUtil.parse("[\"1\",\"2\",\"3\"]", UINT8_SCHEMA.getJsonTypeDefinition());

    assertThat(byteList.getBytes()).isEqualTo(Bytes.fromHexString("0x010203"));
    assertThat(byteList.get(0).getSchema()).isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
  }

  @Test
  void getSszLengthBounds_shouldBeUnbounded() {
    assertThat(SCHEMA.getSszLengthBounds().isUnbounded()).isTrue();
  }

  @Test
  void mutationIsSupported() {
    final SszByteList byteList = SCHEMA.fromBytes(Bytes.fromHexString("0x010203"));
    assertThat(byteList.isWritableSupported()).isTrue();
  }

  @Test
  void writableCopy_canSetElement() {
    final SszByteList byteList = SCHEMA.fromBytes(Bytes.fromHexString("0x010203"));
    final SszMutablePrimitiveList<Byte, SszByte> mutable = byteList.createWritableCopy();
    mutable.set(1, SszByte.of((byte) 0xFF));
    final SszByteList updated = (SszByteList) mutable.commitChanges();

    assertThat(updated.getBytes()).isEqualTo(Bytes.fromHexString("0x01FF03"));
    // mutated list still round-trips
    assertThat(SCHEMA.sszDeserialize(updated.sszSerialize()).getBytes())
        .isEqualTo(Bytes.fromHexString("0x01FF03"));
  }

  @Test
  void uint8WritableCopy_canSetByteSchemaElement() {
    final SszByteList byteList = UINT8_SCHEMA.fromBytes(Bytes.fromHexString("0x010203"));
    final SszMutablePrimitiveList<Byte, SszByte> mutable = byteList.createWritableCopy();
    mutable.set(1, SszByte.of((byte) 0xFF));
    final SszByteList updated = (SszByteList) mutable.commitChanges();

    assertThat(updated.getBytes()).isEqualTo(Bytes.fromHexString("0x01FF03"));
    assertThat(updated.get(1).getSchema()).isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
    assertThat(UINT8_SCHEMA.sszDeserialize(updated.sszSerialize()).get(1).getSchema())
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
  }

  @Test
  void writableCopy_canAppendElement() {
    final SszByteList byteList = SCHEMA.fromBytes(Bytes.fromHexString("0x0102"));
    final SszMutablePrimitiveList<Byte, SszByte> mutable = byteList.createWritableCopy();
    mutable.appendElement((byte) 0x03);
    final SszByteList updated = (SszByteList) mutable.commitChanges();

    assertThat(updated.getBytes()).isEqualTo(Bytes.fromHexString("0x010203"));
    assertThat(updated.size()).isEqualTo(3);
  }

  @Test
  void maxLength_shouldBeEnforcedOnDeserialization() {
    assertThat(LIMITED_SCHEMA.getMaxLength()).isEqualTo(4);
    assertThat(LIMITED_SCHEMA.fromBytes(Bytes.fromHexString("0x01020304")).size()).isEqualTo(4);
    assertThatThrownBy(() -> LIMITED_SCHEMA.fromBytes(Bytes.fromHexString("0x0102030405")))
        .isInstanceOf(SszMaxLengthExceededException.class)
        .hasMessage("List length 5 exceeds max length 4");
    assertThatThrownBy(() -> LIMITED_SCHEMA.sszDeserialize(Bytes.fromHexString("0x0102030405")))
        .isInstanceOf(SszMaxLengthExceededException.class);
    assertThatThrownBy(
            () -> JsonUtil.parse("\"0x0102030405\"", LIMITED_SCHEMA.getJsonTypeDefinition()))
        .hasRootCauseInstanceOf(SszMaxLengthExceededException.class);
  }

  @Test
  void maxLength_shouldBeEnforcedOnMutation() {
    final SszByteList byteList = LIMITED_SCHEMA.fromBytes(Bytes.fromHexString("0x01020304"));
    final SszMutablePrimitiveList<Byte, SszByte> mutable = byteList.createWritableCopy();
    assertThatThrownBy(() -> mutable.appendElement((byte) 0x05))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void maxLength_shouldMakeSszLengthBoundsFinite() {
    assertThat(LIMITED_SCHEMA.getSszLengthBounds().getMaxBytes()).isEqualTo(4);
    assertThat(LIMITED_SCHEMA).isNotEqualTo(SCHEMA);
  }
}
