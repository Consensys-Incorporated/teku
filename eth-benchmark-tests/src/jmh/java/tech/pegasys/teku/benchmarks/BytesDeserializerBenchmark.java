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

package tech.pegasys.teku.benchmarks;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes4Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes8Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;

/**
 * Benchmarks the {@link com.fasterxml.jackson.databind.JsonDeserializer#deserialize(JsonParser,
 * com.fasterxml.jackson.databind.DeserializationContext)} entry-point for each custom bytes
 * deserializer, using a pre-allocated JSON byte array to minimise parser-creation overhead.
 *
 * <p>Run with:
 *
 * <pre>./gradlew :eth-benchmark-tests:jmh --args="BytesDeserializerBenchmark"</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BytesDeserializerBenchmark {

  // Byte counts matching types used in the Engine API
  private static final int LARGE_BYTES_COUNT = 2_600_000; // roughly 20 blobs
  private static final int BYTES_4_COUNT = 4;
  private static final int BYTES_8_COUNT = 8;
  private static final int BYTES_20_COUNT = 20;
  private static final int BYTES_32_COUNT = 32;
  private static final int BYTES_48_COUNT = 48;

  private JsonFactory jsonFactory;

  // Pre-serialised JSON strings as UTF-8 byte arrays, reused across benchmark invocations
  private byte[] blobJson;
  private byte[] bytes4Json;
  private byte[] bytes8Json;
  private byte[] bytes20Json;
  private byte[] bytes32Json;
  private byte[] bytes48Json;

  // Deserializer instances — shared across invocations, stateless
  private BytesDeserializer bytesDeserializer;
  private Bytes4Deserializer bytes4Deserializer;
  private Bytes8Deserializer bytes8Deserializer;
  private Bytes20Deserializer bytes20Deserializer;
  private Bytes32Deserializer bytes32Deserializer;
  private Bytes48Deserializer bytes48Deserializer;

  @Setup(Level.Trial)
  public void setup() {
    jsonFactory = new JsonFactory();

    bytesDeserializer = new BytesDeserializer();
    bytes4Deserializer = new Bytes4Deserializer();
    bytes8Deserializer = new Bytes8Deserializer();
    bytes20Deserializer = new Bytes20Deserializer();
    bytes32Deserializer = new Bytes32Deserializer();
    bytes48Deserializer = new Bytes48Deserializer();

    blobJson = hexJson(Bytes.random(LARGE_BYTES_COUNT).toHexString());
    bytes4Json = hexJson(Bytes.random(BYTES_4_COUNT).toHexString());
    bytes8Json = hexJson(Bytes.random(BYTES_8_COUNT).toHexString());
    bytes20Json = hexJson(Bytes.random(BYTES_20_COUNT).toHexString());
    bytes32Json = hexJson(Bytes.random(BYTES_32_COUNT).toHexString());
    bytes48Json = hexJson(Bytes.random(BYTES_48_COUNT).toHexString());
  }

  // ---- Benchmarks -------------------------------------------------------

  @Benchmark
  public Bytes deserializeBlob() throws IOException {
    return deserialize(bytesDeserializer, blobJson);
  }

  @Benchmark
  public Bytes4 deserializeBytes4() throws IOException {
    return deserialize(bytes4Deserializer, bytes4Json);
  }

  @Benchmark
  public Bytes8 deserializeBytes8() throws IOException {
    return deserialize(bytes8Deserializer, bytes8Json);
  }

  @Benchmark
  public Bytes20 deserializeBytes20() throws IOException {
    return deserialize(bytes20Deserializer, bytes20Json);
  }

  @Benchmark
  public Bytes32 deserializeBytes32() throws IOException {
    return deserialize(bytes32Deserializer, bytes32Json);
  }

  @Benchmark
  public Bytes48 deserializeBytes48() throws IOException {
    return deserialize(bytes48Deserializer, bytes48Json);
  }

  // ---- Helpers ----------------------------------------------------------

  /**
   * Creates a fresh {@link JsonParser} positioned at the {@code VALUE_STRING} token for each
   * invocation. Parser creation from a byte array is O(1) (pointer assignment only), so its
   * contribution to measured time is negligible relative to the hex-parsing work.
   */
  private <T> T deserialize(final JsonDeserializer<T> deserializer, final byte[] jsonBytes)
      throws IOException {
    try (JsonParser p = jsonFactory.createParser(jsonBytes)) {
      p.nextToken();
      return deserializer.deserialize(p, null);
    }
  }

  /** Wraps a hex string in JSON quotes and encodes it as ASCII bytes. */
  private static byte[] hexJson(final String hexValue) {
    return ("\"" + hexValue + "\"").getBytes(StandardCharsets.US_ASCII);
  }
}
