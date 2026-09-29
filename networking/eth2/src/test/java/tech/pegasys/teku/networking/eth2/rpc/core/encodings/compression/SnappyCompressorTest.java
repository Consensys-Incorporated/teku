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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor.Decompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SnappyCompressorTest {
  // The max uncompressed bytes that will be packed into a single frame
  // See:
  // https://github.com/google/snappy/blob/251d935d5096da77c4fef26ea41b019430da5572/framing_format.txt#L104-L106
  static final int MAX_FRAME_CONTENT_SIZE = 65536;
  // The static snappy header taken from the Snappy library
  // see:
  // https://github.com/xerial/snappy-java/blob/de99182a82516c60d29813820926003b2543faf5/src/main/java/org/xerial/snappy/SnappyFramed.java#L121
  private static final Bytes SNAPPY_HEADER =
      Bytes.wrap(new byte[] {(byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59});

  private final Compressor compressor = SnappyFramedCompressor.AIRCOMPRESSOR;

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void roundTrip() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState = state.sszSerialize();

    final Bytes compressed = compressor.compress(serializedState);
    assertThat(compressed).isNotEqualTo(serializedState);

    List<List<ByteBuf>> testSlices = Utils.generateTestSlices(compressed);

    for (List<ByteBuf> testSlice : testSlices) {
      List<ByteBuf> uncompressed = new ArrayList<>();
      Decompressor decompressor = compressor.createDecompressor(serializedState.size());
      for (ByteBuf byteBuf : testSlice) {
        decompressor.decodeOneMessage(byteBuf).ifPresent(uncompressed::add);
        byteBuf.release();
      }
      decompressor.complete();
      assertThat(uncompressed).hasSize(1);
      assertThat(Bytes.wrapByteBuf(uncompressed.get(0))).isEqualTo(serializedState);

      uncompressed.get(0).release();
      assertThat(testSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void uncompress_invalidData() {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState = state.sszSerialize();

    List<List<ByteBuf>> testSlices = Utils.generateTestSlices(serializedState);

    for (List<ByteBuf> testSlice : testSlices) {
      Decompressor decompressor = compressor.createDecompressor(serializedState.size());

      boolean exceptionCaught = false;
      for (ByteBuf byteBuf : testSlice) {
        if (!exceptionCaught) {
          try {
            decompressor.decodeOneMessage(byteBuf);
          } catch (CompressionException e) {
            exceptionCaught = true;
          }
        }
        byteBuf.release();
      }

      assertThat(exceptionCaught).isTrue();
      assertThat(testSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void uncompress_seriesOfValues() throws Exception {
    final BeaconState stateA = dataStructureUtil.randomBeaconState(0);
    final BeaconState stateB = dataStructureUtil.randomBeaconState(1);
    final Bytes serializedStateA = stateA.sszSerialize();
    final Bytes serializedStateB = stateB.sszSerialize();

    final Bytes compressedA = compressor.compress(serializedStateA);
    final Bytes compressedB = compressor.compress(serializedStateB);
    final Bytes compressedSeries = Bytes.concatenate(compressedA, compressedB);

    List<List<ByteBuf>> testSlices = Utils.generateTestSlices(compressedSeries);

    for (List<ByteBuf> testSlice : testSlices) {
      List<ByteBuf> uncompressed = new ArrayList<>();
      Decompressor decompressorA = compressor.createDecompressor(serializedStateA.size());
      Decompressor decompressorB = compressor.createDecompressor(serializedStateB.size());
      for (ByteBuf byteBuf : testSlice) {
        if (uncompressed.isEmpty()) {
          decompressorA.decodeOneMessage(byteBuf).ifPresent(uncompressed::add);
        }
        if (uncompressed.size() > 0) {
          decompressorB.decodeOneMessage(byteBuf).ifPresent(uncompressed::add);
        }
        byteBuf.release();
      }
      decompressorA.complete();
      decompressorB.complete();

      assertThat(uncompressed).hasSize(2);
      assertThat(Bytes.wrapByteBuf(uncompressed.get(0))).isEqualTo(serializedStateA);
      assertThat(Bytes.wrapByteBuf(uncompressed.get(1))).isEqualTo(serializedStateB);

      uncompressed.forEach(ReferenceCounted::release);
      assertThat(testSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void uncompress_truncatedPayload() throws CompressionException {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState = state.sszSerialize();

    // Compress and deliver only part of the payload
    final int payloadSize = serializedState.size();
    final Bytes compressed = compressor.compress(serializedState.slice(1));

    Decompressor decompressor = compressor.createDecompressor(payloadSize);
    assertThat(decompressor.decodeOneMessage(Utils.toByteBuf(compressed))).isEmpty();
    assertThatThrownBy(decompressor::complete)
        .isInstanceOf(PayloadSmallerThanExpectedException.class);
  }

  @Test
  public void uncompress_maliciousBytes() {
    // The number of underlying uncompressed bytes encoded
    final int uncompressedByteCount = 4;

    // Build a set of compressed data with a snappy header, and one frame for each uncompressed byte
    final Bytes singleByte = compressor.compress(Bytes.of(0x01));
    final Bytes singleByteFrame = singleByte.slice(SNAPPY_HEADER.size());
    final Bytes[] headerAndFrames = new Bytes[uncompressedByteCount + 1];
    headerAndFrames[0] = SNAPPY_HEADER;
    for (int i = 0; i < uncompressedByteCount; i++) {
      headerAndFrames[i + 1] = singleByteFrame;
    }
    final Bytes maliciousPayload = Bytes.concatenate(headerAndFrames);

    // Check assumptions - we want to build a set of bytes with valid frames that
    // exceeds the maximum expected compressed size given the underlying data
    final int maxExpectedCompressedBytes = compressor.getMaxCompressedLength(uncompressedByteCount);
    assertThat(maliciousPayload.size()).isGreaterThan(maxExpectedCompressedBytes);

    List<List<ByteBuf>> testSlices = Utils.generateTestSlices(maliciousPayload);

    for (List<ByteBuf> testSlice : testSlices) {
      Decompressor decompressor = compressor.createDecompressor(uncompressedByteCount);

      boolean exceptionCaught = false;
      for (ByteBuf byteBuf : testSlice) {
        if (!exceptionCaught) {
          try {
            decompressor.decodeOneMessage(byteBuf);
          } catch (CompressionException e) {
            exceptionCaught = true;
          }
        }
        byteBuf.release();
      }

      assertThat(exceptionCaught).isTrue();
      assertThat(testSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void uncompress_failOnExtraHeader() {
    final Bytes singleByte = compressor.compress(Bytes.of(0x01));
    final Bytes singleByteFrame = singleByte.slice(SNAPPY_HEADER.size());
    final Bytes maliciousPayload = Bytes.concatenate(SNAPPY_HEADER, SNAPPY_HEADER, singleByteFrame);

    List<List<ByteBuf>> testSlices = Utils.generateTestSlices(maliciousPayload);

    for (List<ByteBuf> testSlice : testSlices) {
      Decompressor decompressor = compressor.createDecompressor(1);

      boolean exceptionCaught = false;
      for (ByteBuf byteBuf : testSlice) {
        if (!exceptionCaught) {
          try {
            decompressor.decodeOneMessage(byteBuf);
          } catch (CompressionException e) {
            exceptionCaught = true;
          }
        }
        byteBuf.release();
      }

      assertThat(exceptionCaught).isTrue();
      assertThat(testSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void uncompress_partialValueWhenFullFrameUnavailable() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Bytes serializedState = state.sszSerialize();

    final Bytes compressed = compressor.compress(serializedState);
    final int partialPayloadSize = MAX_FRAME_CONTENT_SIZE / 2;
    final int bytesToRead = partialPayloadSize / 2;
    // Check assumptions
    assertThat(serializedState.size()).isGreaterThan(MAX_FRAME_CONTENT_SIZE);

    ByteBuf partialPayload = Utils.toByteBuf(compressed.slice(0, partialPayloadSize));
    Decompressor decompressor = compressor.createDecompressor(bytesToRead);

    assertThatThrownBy(() -> decompressor.decodeOneMessage(partialPayload))
        .isInstanceOf(CompressionException.class);
    assertThatThrownBy(decompressor::complete).isInstanceOf(CompressionException.class);
  }
}
