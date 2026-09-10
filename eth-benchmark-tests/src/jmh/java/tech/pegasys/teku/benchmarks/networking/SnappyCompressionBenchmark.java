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

package tech.pegasys.teku.benchmarks.networking;

import io.airlift.compress.v3.Compressor;
import io.airlift.compress.v3.Decompressor;
import io.airlift.compress.v3.snappy.SnappyJavaCompressor;
import io.airlift.compress.v3.snappy.SnappyJavaDecompressor;
import io.airlift.compress.v3.snappy.SnappyNativeCompressor;
import io.airlift.compress.v3.snappy.SnappyNativeDecompressor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.compression.Snappy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.gen.BeaconBlockFixtureGenerator;
import tech.pegasys.teku.benchmarks.gen.BlockIO;
import tech.pegasys.teku.benchmarks.metric.CompressionMetrics;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Compares Snappy block-compression implementations to evaluate consolidating on a single Snappy
 * dependency for gossip:
 *
 * <ul>
 *   <li>{@code SNAPPY_JAVA} — snappy-java, JNI to native C++ (used today for gossip).
 *   <li>{@code NETTY} — Netty's pure-Java Snappy block codec (used today for RPC framing).
 *   <li>{@code AIRCOMPRESSOR_NATIVE} — aircompressor-v3 native Snappy via {@code java.lang.foreign}
 *       (FFM, not JNI).
 *   <li>{@code AIRCOMPRESSOR_JAVA} — aircompressor-v3 pure-Java Snappy.
 * </ul>
 *
 * <p>Each implementation is invoked via its raw library API so the comparison isolates the
 * compression engine; {@code byte[]} marshalling (which Netty and aircompressor require) is
 * included because gossip code speaks byte arrays.
 *
 * <p>The {@code AIRCOMPRESSOR_NATIVE} classes are direct FFM bindings with no silent pure-Java
 * fallback, so a successful run (which passes the startup cross-compatibility round-trip) proves
 * the native library actually loaded and executed on this platform.
 *
 * <p>Compression ratio matters: Snappy fixes the wire format but not the encoder heuristics, so two
 * implementations can produce different compressed sizes for the same input — a faster encoder that
 * compresses less is not actually better for gossip bandwidth. Each implementation's compressed
 * size and ratio are therefore reported alongside the timing metrics. To keep the comparison fair,
 * {@code decompress} decodes a single shared snappy-java-encoded buffer for every implementation
 * (all are wire-compatible), so decode cost is isolated from encoder-specific output shape. For
 * multi-message fixture payloads, each fixture item is compressed/decompressed independently in one
 * benchmark operation and the reported ratio aggregates all items. Synthetic {@code SYNTHETIC_*}
 * payloads exercise the encoder heuristics on compressible data, which the high-entropy SSZ message
 * payloads cannot.
 *
 * <p>Run a specific payload/impl with GC profiling:
 *
 * <pre>
 *   ./gradlew :eth-benchmark-tests:jmh --args="SnappyCompressionBenchmark -prof gc"
 * </pre>
 */
@Fork(1)
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class SnappyCompressionBenchmark {

  public enum Impl {
    SNAPPY_JAVA,
    NETTY,
    AIRCOMPRESSOR_NATIVE,
    AIRCOMPRESSOR_JAVA
  }

  public enum Payload {
    ATTESTATION,
    AGGREGATE,
    SYNC_COMMITTEE_MESSAGE,
    BEACON_BLOCK,
    BEACON_BLOCKS_FIXTURE_CHUNK_32K,
    DATA_COLUMN_SIDECAR,
    DATA_COLUMN_SIDECARS_CHUNK_32K,
    // Synthetic payloads to exercise encoder heuristics on compressible data (the SSZ message
    // payloads above are high-entropy and barely compress).
    SYNTHETIC_REPEATING_64K,
    SYNTHETIC_MIXED_64K
  }

  @State(Scope.Benchmark)
  public static class Data {

    @Param({
      "ATTESTATION",
      "AGGREGATE",
      "SYNC_COMMITTEE_MESSAGE",
      "BEACON_BLOCK",
      "BEACON_BLOCKS_FIXTURE_CHUNK_32K",
      "DATA_COLUMN_SIDECAR",
      "DATA_COLUMN_SIDECARS_CHUNK_32K",
      "SYNTHETIC_REPEATING_64K",
      "SYNTHETIC_MIXED_64K"
    })
    public Payload payload;

    @Param({"SNAPPY_JAVA", "NETTY", "AIRCOMPRESSOR_NATIVE", "AIRCOMPRESSOR_JAVA"})
    public Impl impl;

    // Uncompressed payloads (input for the compress benchmark). Most payload modes contain one
    // message; fixture modes contain one entry per block/chunk.
    public List<byte[]> rawPayloads;
    // Shared reference compressed bytes (snappy-java block format), used as the decompress input
    // for EVERY implementation so decode is compared on byte-identical input (all impls are
    // wire-compatible). Isolates decoder cost from encoder-specific output shape.
    public List<byte[]> referenceCompressedPayloads;
    public long rawSize;
    public long implCompressedSize;
    public long referenceCompressedSize;
    public int measurementCount;

    @Setup(Level.Trial)
    public void setup(final BenchmarkParams benchmarkParams) throws IOException {
      final Spec spec = TestSpecFactory.createMainnetFulu();
      final DataStructureUtil data = new DataStructureUtil(1, spec);
      rawPayloads = buildPayloads(payload, data);
      referenceCompressedPayloads = snappyJavaCompressPayloads(rawPayloads);
      measurementCount = benchmarkParams.getMeasurement().getCount();

      final List<byte[]> ownCompressed = compressPayloads(impl, rawPayloads);
      rawSize = totalSize(rawPayloads);
      implCompressedSize = totalSize(ownCompressed);
      referenceCompressedSize = totalSize(referenceCompressedPayloads);
      System.out.printf(
          "RATIO|%s|%s|payloads=%d|raw=%d|compressed=%d|ratio=%.4f%n",
          payload,
          impl,
          rawPayloads.size(),
          rawSize,
          implCompressedSize,
          (double) rawSize / implCompressedSize);

      // Feasibility check (spec item 1): every implementation must produce/consume the same Snappy
      // block wire format as snappy-java, or a migration would break gossip interoperability. Fail
      // fast and loudly if not. Impl-agnostic, so run once per payload (during the SNAPPY_JAVA
      // trial).
      if (impl == Impl.SNAPPY_JAVA) {
        for (final byte[] raw : rawPayloads) {
          verifyCrossCompatibility(raw);
        }
      }
    }
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void compress(
      final Data data, final CompressionMetrics compressionMetrics, final Blackhole blackhole)
      throws IOException {
    for (final byte[] raw : data.rawPayloads) {
      blackhole.consume(compressPayload(data.impl, raw));
    }
    compressionMetrics.record(data.rawSize, data.implCompressedSize, data.measurementCount);
  }

  @Benchmark
  @Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void decompress(
      final Data data, final CompressionMetrics compressionMetrics, final Blackhole blackhole)
      throws IOException {
    for (int i = 0; i < data.rawPayloads.size(); i++) {
      blackhole.consume(
          decompressPayload(
              data.impl, data.referenceCompressedPayloads.get(i), data.rawPayloads.get(i).length));
    }
    compressionMetrics.record(data.rawSize, data.referenceCompressedSize, data.measurementCount);
  }

  private static final int CHUNK_SIZE = 32 * 1024;
  private static final int DATA_COLUMN_SIDECAR_COUNT = 16;
  private static final int SYNTHETIC_PAYLOAD_SIZE = 65536;
  private static final int REPEATING_PATTERN_SIZE = 256;
  private static final long SYNTHETIC_SEED = 5566;

  private static List<byte[]> buildPayloads(final Payload payload, final DataStructureUtil data) {
    return switch (payload) {
      case ATTESTATION -> singlePayload(data.randomAttestation().sszSerialize());
      case AGGREGATE -> singlePayload(data.randomSignedAggregateAndProof().sszSerialize());
      case SYNC_COMMITTEE_MESSAGE ->
          singlePayload(data.randomSyncCommitteeMessage().sszSerialize());
      case BEACON_BLOCK -> singlePayload(data.randomSignedBeaconBlock().sszSerialize());
      case BEACON_BLOCKS_FIXTURE_CHUNK_32K ->
          splitIntoChunks(loadResourceBeaconBlocks(), CHUNK_SIZE);
      case DATA_COLUMN_SIDECAR -> singlePayload(data.randomDataColumnSidecar().sszSerialize());
      case DATA_COLUMN_SIDECARS_CHUNK_32K ->
          splitIntoChunks(randomDataColumnSidecars(data), CHUNK_SIZE);
      case SYNTHETIC_REPEATING_64K -> List.of(syntheticRepeating(SYNTHETIC_PAYLOAD_SIZE));
      case SYNTHETIC_MIXED_64K -> List.of(syntheticMixed(SYNTHETIC_PAYLOAD_SIZE));
    };
  }

  private static List<byte[]> singlePayload(final Bytes payload) {
    return List.of(payload.toArrayUnsafe());
  }

  private static List<byte[]> loadResourceBeaconBlocks() {
    final List<byte[]> blocks =
        BlockIO.readResourceBytes(BeaconBlockFixtureGenerator.RESOURCE, Integer.MAX_VALUE);
    if (blocks.isEmpty()) {
      throw new IllegalStateException(
          "No blocks loaded from fixture " + BeaconBlockFixtureGenerator.RESOURCE);
    }
    return blocks;
  }

  private static List<byte[]> randomDataColumnSidecars(final DataStructureUtil data) {
    final List<byte[]> sidecars = new ArrayList<>(DATA_COLUMN_SIDECAR_COUNT);
    for (int i = 0; i < DATA_COLUMN_SIDECAR_COUNT; i++) {
      sidecars.add(data.randomDataColumnSidecar().sszSerialize().toArrayUnsafe());
    }
    return sidecars;
  }

  private static List<byte[]> splitIntoChunks(final List<byte[]> payloads, final int chunkSize) {
    int totalSize = 0;
    for (final byte[] payload : payloads) {
      totalSize += payload.length;
    }
    final byte[] concatenated = new byte[totalSize];
    int position = 0;
    for (final byte[] payload : payloads) {
      System.arraycopy(payload, 0, concatenated, position, payload.length);
      position += payload.length;
    }
    final List<byte[]> chunks = new ArrayList<>();
    for (int offset = 0; offset < totalSize; offset += chunkSize) {
      chunks.add(Arrays.copyOfRange(concatenated, offset, Math.min(offset + chunkSize, totalSize)));
    }
    return chunks;
  }

  private static byte[] syntheticRepeating(final int size) {
    final byte[] out = new byte[size];
    final Random random = new Random(SYNTHETIC_SEED);
    final byte[] pattern = new byte[REPEATING_PATTERN_SIZE];
    random.nextBytes(pattern);
    for (int i = 0; i < size; i++) {
      out[i] = pattern[i % pattern.length];
    }
    return out;
  }

  private static byte[] syntheticMixed(final int size) {
    final byte[] out = new byte[size];
    final Random random = new Random(SYNTHETIC_SEED);
    random.nextBytes(out);
    final int half = size / 2;
    final byte[] pattern = new byte[REPEATING_PATTERN_SIZE];
    random.nextBytes(pattern);
    for (int i = half; i < size; i++) {
      out[i] = pattern[(i - half) % pattern.length];
    }
    return out;
  }

  private static List<byte[]> compressPayloads(final Impl impl, final List<byte[]> rawPayloads)
      throws IOException {
    final List<byte[]> compressedPayloads = new ArrayList<>(rawPayloads.size());
    for (final byte[] rawPayload : rawPayloads) {
      compressedPayloads.add(compressPayload(impl, rawPayload));
    }
    return compressedPayloads;
  }

  private static List<byte[]> snappyJavaCompressPayloads(final List<byte[]> rawPayloads)
      throws IOException {
    final List<byte[]> compressedPayloads = new ArrayList<>(rawPayloads.size());
    for (final byte[] rawPayload : rawPayloads) {
      compressedPayloads.add(snappyJavaCompress(rawPayload));
    }
    return compressedPayloads;
  }

  private static byte[] compressPayload(final Impl impl, final byte[] raw) throws IOException {
    return switch (impl) {
      case SNAPPY_JAVA -> snappyJavaCompress(raw);
      case NETTY -> nettyCompress(raw);
      case AIRCOMPRESSOR_NATIVE -> aircompressorCompress(new SnappyNativeCompressor(), raw);
      case AIRCOMPRESSOR_JAVA -> aircompressorCompress(new SnappyJavaCompressor(), raw);
    };
  }

  private static byte[] decompressPayload(
      final Impl impl, final byte[] compressed, final int uncompressedLength) throws IOException {
    return switch (impl) {
      case SNAPPY_JAVA -> snappyJavaUncompress(compressed);
      case NETTY -> nettyUncompress(compressed);
      case AIRCOMPRESSOR_NATIVE ->
          aircompressorUncompress(new SnappyNativeDecompressor(), compressed, uncompressedLength);
      case AIRCOMPRESSOR_JAVA ->
          aircompressorUncompress(new SnappyJavaDecompressor(), compressed, uncompressedLength);
    };
  }

  private static long totalSize(final List<byte[]> payloads) {
    long totalSize = 0;
    for (final byte[] payload : payloads) {
      totalSize += payload.length;
    }
    return totalSize;
  }

  static byte[] snappyJavaCompress(final byte[] in) throws IOException {
    return org.xerial.snappy.Snappy.compress(in);
  }

  static byte[] snappyJavaUncompress(final byte[] in) throws IOException {
    return org.xerial.snappy.Snappy.uncompress(in);
  }

  static byte[] nettyCompress(final byte[] in) {
    final ByteBuf inBuf = Unpooled.wrappedBuffer(in);
    final ByteBuf outBuf = Unpooled.buffer(32 + in.length / 2);
    try {
      new Snappy().encode(inBuf, outBuf, inBuf.readableBytes());
      final byte[] out = new byte[outBuf.readableBytes()];
      outBuf.readBytes(out);
      return out;
    } finally {
      inBuf.release();
      outBuf.release();
    }
  }

  static byte[] nettyUncompress(final byte[] in) {
    final ByteBuf inBuf = Unpooled.wrappedBuffer(in);
    final ByteBuf outBuf = Unpooled.buffer(in.length * 4);
    try {
      new Snappy().decode(inBuf, outBuf);
      final byte[] out = new byte[outBuf.readableBytes()];
      outBuf.readBytes(out);
      return out;
    } finally {
      inBuf.release();
      outBuf.release();
    }
  }

  static byte[] aircompressorCompress(final Compressor compressor, final byte[] in) {
    final byte[] out = new byte[compressor.maxCompressedLength(in.length)];
    final int written = compressor.compress(in, 0, in.length, out, 0, out.length);
    return Arrays.copyOf(out, written);
  }

  static byte[] aircompressorUncompress(
      final Decompressor decompressor, final byte[] in, final int uncompressedLength) {
    final byte[] out = new byte[uncompressedLength];
    final int written = decompressor.decompress(in, 0, in.length, out, 0, out.length);
    return written == out.length ? out : Arrays.copyOf(out, written);
  }

  /**
   * Verifies every implementation shares the Snappy block wire format with snappy-java (the current
   * production gossip codec), in both directions. A mismatch means a migration would break gossip
   * interoperability with the network. Exercising the aircompressor-native path here also proves
   * its FFM native library loaded on this platform (there is no silent pure-Java fallback in the
   * {@code *Native*} classes).
   */
  static void verifyCrossCompatibility(final byte[] raw) throws IOException {
    final byte[] snappyJava = snappyJavaCompress(raw);

    // snappy-java must decode every implementation's compressed output.
    requireRoundTrip("snappy-java <- Netty", raw, snappyJavaUncompress(nettyCompress(raw)));
    requireRoundTrip(
        "snappy-java <- aircompressor-native",
        raw,
        snappyJavaUncompress(aircompressorCompress(new SnappyNativeCompressor(), raw)));
    requireRoundTrip(
        "snappy-java <- aircompressor-java",
        raw,
        snappyJavaUncompress(aircompressorCompress(new SnappyJavaCompressor(), raw)));

    // Every implementation must decode snappy-java's compressed output.
    requireRoundTrip("Netty <- snappy-java", raw, nettyUncompress(snappyJava));
    requireRoundTrip(
        "aircompressor-native <- snappy-java",
        raw,
        aircompressorUncompress(new SnappyNativeDecompressor(), snappyJava, raw.length));
    requireRoundTrip(
        "aircompressor-java <- snappy-java",
        raw,
        aircompressorUncompress(new SnappyJavaDecompressor(), snappyJava, raw.length));
  }

  private static void requireRoundTrip(
      final String description, final byte[] expected, final byte[] actual) {
    if (!Arrays.equals(expected, actual)) {
      throw new IllegalStateException(
          "Snappy block wire-format mismatch ("
              + description
              + "): cross-decode did not reproduce the original input");
    }
  }
}
