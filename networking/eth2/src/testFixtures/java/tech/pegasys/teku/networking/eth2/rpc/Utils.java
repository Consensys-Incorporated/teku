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

package tech.pegasys.teku.networking.eth2.rpc;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

import com.google.common.collect.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;

public class Utils {

  /**
   * Given the list of a stream byte chunks (which are normally prefixes and payloads) creates a set
   * of test {@code ByteBuf}'s which are different combinations of slicing and sticking of the
   * original chunks. Zero-length {@code ByteBuf}s are also mixed up to the resulting lists
   */
  public static List<List<ByteBuf>> generateTestSlices(final Bytes... chunks) {
    int totalLen = Arrays.stream(chunks).mapToInt(Bytes::size).sum();
    List<List<ByteBuf>> splits =
        List.of(
            List.of(toByteBuf(chunks)),
            splitIntoEvenChunks(1, chunks).stream()
                .map(Utils::toByteBuf)
                .collect(Collectors.toList()),
            splitIntoEvenChunks(2, chunks).stream()
                .map(Utils::toByteBuf)
                .collect(Collectors.toList()),
            Arrays.stream(chunks).map(Utils::toByteBuf).collect(Collectors.toList()),
            Arrays.stream(chunks)
                .map(Utils::toByteBuf)
                .flatMap(b -> Utils.slice(b, 1).stream())
                .collect(Collectors.toList()),
            Arrays.stream(chunks)
                .map(Utils::toByteBuf)
                .flatMap(b -> Utils.slice(b, 2).stream())
                .collect(Collectors.toList()),
            Arrays.stream(chunks)
                .map(Utils::toByteBuf)
                .flatMap(b -> Utils.slice(b, 1, 2).stream())
                .collect(Collectors.toList()),
            Arrays.stream(chunks)
                .map(Utils::toByteBuf)
                .flatMap(b -> Utils.slice(b, -1).stream())
                .collect(Collectors.toList()),
            Arrays.stream(chunks)
                .map(Utils::toByteBuf)
                .flatMap(b -> Utils.slice(b, -2).stream())
                .collect(Collectors.toList()),
            shiftedSlices(1, chunks),
            shiftedSlices(2, chunks),
            shiftedSlices(-1, chunks),
            shiftedSlices(-2, chunks),
            Utils.slice(toByteBuf(chunks), totalLen / 3, 2 * totalLen / 3));

    List<List<ByteBuf>> ret =
        Stream.concat(splits.stream(), addZeroLenBuffers(splits).stream())
            .collect(Collectors.toList());

    return ret;
  }

  /**
   * Combine chunks, then split into even-sized chunks
   *
   * @param chunkSize The target chunk size
   * @param chunks The original chunks
   * @return
   */
  private static List<Bytes> splitIntoEvenChunks(final int chunkSize, final Bytes... chunks) {
    final Bytes allBytes = Bytes.concatenate(chunks);
    final List<Bytes> evenChunks = new ArrayList<>();
    for (int i = 0; i < allBytes.size(); i += chunkSize) {
      final int size = Math.min(chunkSize, allBytes.size() - i);
      evenChunks.add(allBytes.slice(i, size));
    }
    return evenChunks;
  }

  private static List<List<ByteBuf>> addZeroLenBuffers(final List<List<ByteBuf>> bufSets) {
    return bufSets.stream()
        .map(
            set ->
                set.stream()
                    .flatMap(b -> Stream.of(emptyBuf(), b.copy(), emptyBuf()))
                    .collect(Collectors.toList()))
        .collect(Collectors.toList());
  }

  private static List<ByteBuf> shiftedSlices(final int shift, final Bytes... chunks) {
    AtomicInteger sum = new AtomicInteger(0);
    IntStream pos =
        IntStream.concat(
            IntStream.of(0), Arrays.stream(chunks).mapToInt(Bytes::size).map(sum::addAndGet));
    if (shift > 0) {
      pos = pos.limit(chunks.length);
    } else {
      pos = pos.skip(1);
    }
    pos = pos.map(p -> p + shift).map(p -> Math.max(p, 0));
    return slice(toByteBuf(chunks), pos.toArray());
  }

  private static List<ByteBuf> slice(final ByteBuf src, final int... pos) {
    int[] pos1 =
        Arrays.stream(pos)
            .map(i -> i >= 0 ? i : src.readableBytes() + i)
            .map(i -> max(0, min(i, src.readableBytes())))
            .toArray();
    return Streams.zip(
            IntStream.concat(IntStream.of(0), Arrays.stream(pos1)).boxed(),
            IntStream.concat(Arrays.stream(pos1), IntStream.of(src.readableBytes())).boxed(),
            IntRange::ofIndices)
        .map(il -> src.slice(il.getStartIndex(), il.getLength()).copy())
        // check that buffer with non-zero offset works well
        .map(bb -> Unpooled.wrappedBuffer(Unpooled.wrappedBuffer(new byte[4]), bb).readerIndex(4))
        .collect(Collectors.toList());
  }

  public static ByteBuf emptyBuf() {
    // to avoid ByteBuf.EMPTY which always has reference count > 0
    return Unpooled.wrappedBuffer(new byte[1]).readerIndex(1);
  }

  public static ByteBuf toByteBuf(final Bytes... bytes) {
    return Unpooled.wrappedBuffer(Bytes.concatenate(bytes).toArray());
  }
}
