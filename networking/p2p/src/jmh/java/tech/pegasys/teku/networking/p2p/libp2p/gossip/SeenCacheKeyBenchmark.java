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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.core.pubsub.ValidationResult;
import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.FastIdSeenCache;
import io.libp2p.pubsub.PubsubMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
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
import pubsub.pb.Rpc;

/**
 * Cost of the gossip seen-cache lookup per inbound message copy, with the fast id keyed either by a
 * tuweni {@link Bytes} (the previous key) or by {@link FastMessageId}.
 *
 * <p>The cache is the real jvm-libp2p {@link FastIdSeenCache} pre-filled with {@code size}
 * messages, which is what a node holds within the seen TTL. Each lookup uses a fresh message
 * instance, as every inbound copy is, so the key is created on every call exactly as in production.
 * {@code hit} is the duplicate path (every mesh peer forwards a copy), {@code miss} the
 * first-delivery path.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xmx6g", "-Xms6g"})
public class SeenCacheKeyBenchmark {

  private static final int LOOKUPS = 1 << 16;
  private static final int LOOKUP_MASK = LOOKUPS - 1;

  public enum KeyType {
    TUWENI_BYTES(msg -> Bytes.wrap(msg.messageSha256())),
    FAST_MESSAGE_ID(FastMessageId::of);

    private final Function<PubsubMessage, Object> fastIdFunction;

    KeyType(final Function<PubsubMessage, Object> fastIdFunction) {
      this.fastIdFunction = fastIdFunction;
    }
  }

  @Param({"TUWENI_BYTES", "FAST_MESSAGE_ID"})
  public KeyType keyType;

  @Param({"100000", "2000000"})
  public int size;

  private FastIdSeenCache<Optional<ValidationResult>> seenCache;
  private Map<Object, Object> rawMap;
  private PubsubMessage[] hits;
  private PubsubMessage[] misses;
  private int index;

  @Setup(Level.Trial)
  public void setUp(final BenchmarkParams params) {
    final Random random = new Random(1234);
    final byte[][] digests = new byte[size][];
    // fill only the structure the benchmark method probes, so the other one does not sit in the
    // live heap of every measurement
    final boolean rawMapBenchmark =
        params.getBenchmark().endsWith("rawMapHit") || params.getBenchmark().endsWith("rawMapMiss");
    seenCache = rawMapBenchmark ? null : new FastIdSeenCache<>(keyType.fastIdFunction::apply);
    rawMap = rawMapBenchmark ? new HashMap<>() : null;
    for (int i = 0; i < size; i++) {
      digests[i] = randomBytes(random, FastMessageId.DIGEST_LENGTH);
      final PubsubMessage message = new DigestOnlyMessage(digests[i], randomBytes(random, 20));
      if (rawMapBenchmark) {
        rawMap.put(keyType.fastIdFunction.apply(message), message);
      } else {
        seenCache.put(message, Optional.of(ValidationResult.Valid));
      }
    }
    hits = new PubsubMessage[LOOKUPS];
    misses = new PubsubMessage[LOOKUPS];
    for (int i = 0; i < LOOKUPS; i++) {
      // A duplicate arrives as a new instance carrying its own copy of the wire bytes.
      hits[i] =
          new DigestOnlyMessage(digests[random.nextInt(size)].clone(), randomBytes(random, 20));
      misses[i] =
          new DigestOnlyMessage(
              randomBytes(random, FastMessageId.DIGEST_LENGTH), randomBytes(random, 20));
    }
  }

  @Benchmark
  public boolean seenCacheHit() {
    return seenCache.isSeen(hits[next()]);
  }

  @Benchmark
  public boolean seenCacheMiss() {
    return seenCache.isSeen(misses[next()]);
  }

  /** The key alone: build it from a fresh message and look it up in a plain HashMap. */
  @Benchmark
  public Object rawMapHit() {
    return rawMap.get(keyType.fastIdFunction.apply(hits[next()]));
  }

  @Benchmark
  public Object rawMapMiss() {
    return rawMap.get(keyType.fastIdFunction.apply(misses[next()]));
  }

  private int next() {
    index = (index + 1) & LOOKUP_MASK;
    return index;
  }

  private static byte[] randomBytes(final Random random, final int length) {
    final byte[] bytes = new byte[length];
    random.nextBytes(bytes);
    return bytes;
  }

  /**
   * A pubsub message that only carries what the seen cache touches: the wire-bytes digest and the
   * (slow) message id. The protobuf body is never read by the cache.
   */
  private static final class DigestOnlyMessage implements PubsubMessage {
    private static final Rpc.Message EMPTY = Rpc.Message.getDefaultInstance();

    private final byte[] digest;
    private final WBytes messageId;

    DigestOnlyMessage(final byte[] digest, final byte[] messageId) {
      this.digest = digest;
      this.messageId = new WBytes(messageId);
    }

    @NotNull
    @Override
    public byte[] messageSha256() {
      return digest;
    }

    @NotNull
    @Override
    public WBytes getMessageId() {
      return messageId;
    }

    @NotNull
    @Override
    public Rpc.Message getProtobufMessage() {
      return EMPTY;
    }

    @NotNull
    @Override
    public List<String> getTopics() {
      return List.of();
    }

    @Override
    public boolean equals(final Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return messageId.hashCode();
    }
  }
}
