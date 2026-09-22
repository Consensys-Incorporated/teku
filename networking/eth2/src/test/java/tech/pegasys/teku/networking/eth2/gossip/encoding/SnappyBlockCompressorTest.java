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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;

public class SnappyBlockCompressorTest {

  private static final long MAX_PAYLOAD_SIZE = Long.MAX_VALUE;

  private final SnappyCompressor compressor = new AircompressorSnappyBlockCompressor();

  @Test
  public void roundTrip() throws DecodingException {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);
    assertThat(compressed).isNotEqualTo(original);
    final Bytes uncompressed =
        compressor.uncompress(compressed, SszLengthBounds.ofBytes(0, 1000), MAX_PAYLOAD_SIZE);

    assertThat(uncompressed).isEqualTo(original);
  }

  @Test
  public void uncompress_randomData() {
    final Bytes data = Bytes.fromHexString("0x0102");

    assertThatThrownBy(
            () -> compressor.uncompress(data, SszLengthBounds.ofBytes(0, 1000), MAX_PAYLOAD_SIZE))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  void uncompress_uncompressedLengthLongerThanSszLenghtBounds() {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);
    assertThatThrownBy(
            () ->
                compressor.uncompress(compressed, SszLengthBounds.ofBytes(0, 4), MAX_PAYLOAD_SIZE))
        .isInstanceOf(DecodingException.class)
        .hasMessageContaining("not within expected bounds");
  }

  @Test
  void uncompress_uncompressedLengthShorterThanSszLengthBounds() {
    final Bytes original = Bytes.fromHexString("0x010203040506");

    final Bytes compressed = compressor.compress(original);

    assertThatThrownBy(
            () ->
                compressor.uncompress(
                    compressed, SszLengthBounds.ofBytes(100, 200), MAX_PAYLOAD_SIZE))
        .isInstanceOf(DecodingException.class)
        .hasMessageContaining("not within expected bounds");
  }

  @Test
  void uncompress_uncompressedLengthLongerThanMaxBytesLength() {
    final Bytes original = Bytes.fromHexString("0x010203040506");
    final long smallMaxBytesLength = 3;
    assertThat(smallMaxBytesLength).isLessThan(original.size());

    final Bytes compressed = compressor.compress(original);
    assertThatThrownBy(
            () ->
                compressor.uncompress(
                    compressed, SszLengthBounds.ofBytes(0, 1000), smallMaxBytesLength))
        .isInstanceOf(DecodingException.class)
        .hasMessageContaining("exceeds max length in bytes");
  }

  @Test
  void uncompress_uncompressedLengthEqualThanMaxBytesLength() throws DecodingException {
    final Bytes original = Bytes.fromHexString("0x010203040506");
    final long exactMaxBytesLength = original.size();

    final Bytes compressed = compressor.compress(original);
    assertThat(compressed).isNotEqualTo(original);
    final Bytes uncompressed =
        compressor.uncompress(compressed, SszLengthBounds.ofBytes(0, 1000), exactMaxBytesLength);

    assertThat(uncompressed).isEqualTo(original);
  }

  @Test
  void uncompress_compressedDataWithTrailingBytesIsRejected() {
    final Bytes original = Bytes.fromHexString("0x010203040506");
    final Bytes compressed = Bytes.concatenate(compressor.compress(original), Bytes.of(1));

    assertThatThrownBy(
            () ->
                compressor.uncompress(
                    compressed, SszLengthBounds.ofBytes(original.size()), MAX_PAYLOAD_SIZE))
        .isInstanceOf(DecodingException.class);
  }
}
