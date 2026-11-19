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

package tech.pegasys.teku.xmss;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * leanXMSS costs through lean-pq.
 *
 * <pre>
 * ./gradlew :infrastructure:xmss:jmh --args="XmssBenchmark"
 * ./gradlew :infrastructure:xmss:jmh --args="XmssBenchmark.signSlot -p keys=256 -p threads=8"
 * </pre>
 *
 * <p>A bottom tree covers 2^16 leaves, and a key caches its most recently used bottom trees, so
 * signing at consecutive leaves is the steady state for a validator, while the first leaf of a new
 * bottom tree is the slow case.
 */
@Fork(value = 1, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
public class XmssBenchmark {

  private static final long BOTTOM_TREE_LEAVES = 1L << 16;

  private static Bytes32 seed(final int i) {
    return Bytes32.leftPad(Bytes.ofUnsignedInt(i + 1L));
  }

  @State(Scope.Benchmark)
  public static class SingleKey {
    XmssSigningKey key;
    XmssPublicKey publicKey;
    final Bytes32 message = Bytes32.fromHexString("0x" + "ab".repeat(32));
    XmssSignature signature;
    final AtomicLong nextLeaf = new AtomicLong(1);
    final AtomicLong nextBottomTree = new AtomicLong(1);

    @Setup(Level.Trial)
    public void setUp() {
      key = Xmss.loadSigningKey(XmssSecretKey.fromBytes(seed(0)));
      publicKey = key.getPublicKey();
      // Builds the first bottom tree
      signature = key.sign(UInt64.ZERO, message);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      key.close();
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  @Warmup(iterations = 3, time = 1)
  @Measurement(iterations = 5, time = 1)
  public boolean verify(final SingleKey state) {
    return Xmss.verify(state.publicKey, UInt64.ZERO, state.message, state.signature);
  }

  /** Steady state: the next leaf, in a bottom tree the key already has. */
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 2, time = 2)
  @Measurement(iterations = 5, time = 2)
  public XmssSignature signCachedLeaf(final SingleKey state) {
    final long leaf = state.nextLeaf.getAndIncrement();
    if (leaf >= BOTTOM_TREE_LEAVES) {
      throw new IllegalStateException("Left the first bottom tree; shorten the run");
    }
    return state.key.sign(UInt64.valueOf(leaf), state.message);
  }

  /** The first leaf of a bottom tree the key has not built yet. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public XmssSignature signNewBottomTree(final SingleKey state) {
    final long leaf = state.nextBottomTree.getAndIncrement() * BOTTOM_TREE_LEAVES;
    return state.key.sign(UInt64.valueOf(leaf), state.message);
  }

  /** Loading a key from its seed, which builds its top tree. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 3)
  public XmssPublicKey loadKey() {
    try (XmssSigningKey key = Xmss.loadSigningKey(XmssSecretKey.fromBytes(seed(1)))) {
      return key.getPublicKey();
    }
  }

  /**
   * One slot of validator-client signing: every key signs once, spread over {@code threads}
   * threads. Keys are loaded and their bottom trees built during setup.
   */
  @State(Scope.Benchmark)
  public static class Slot {
    @Param({"16", "64"})
    int keys;

    @Param({"1", "4", "12"})
    int threads;

    List<XmssSigningKey> signingKeys;
    ExecutorService executor;
    final Bytes32 message = Bytes32.fromHexString("0x" + "cd".repeat(32));
    final AtomicLong nextLeaf = new AtomicLong(1);

    @Setup(Level.Trial)
    public void setUp() {
      signingKeys =
          IntStream.range(0, keys)
              .parallel()
              .mapToObj(
                  i -> {
                    final XmssSigningKey key =
                        Xmss.loadSigningKey(XmssSecretKey.fromBytes(seed(100 + i)));
                    key.sign(UInt64.ZERO, message);
                    return key;
                  })
              .toList();
      executor = Executors.newFixedThreadPool(threads);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      executor.shutdownNow();
      signingKeys.forEach(XmssSigningKey::close);
    }
  }

  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public int signSlot(final Slot state) throws Exception {
    final UInt64 leaf = UInt64.valueOf(state.nextLeaf.getAndIncrement());
    final List<Callable<XmssSignature>> tasks = new ArrayList<>(state.keys);
    for (final XmssSigningKey key : state.signingKeys) {
      tasks.add(() -> key.sign(leaf, state.message));
    }
    int signed = 0;
    for (final Future<XmssSignature> result : state.executor.invokeAll(tasks)) {
      result.get();
      signed++;
    }
    return signed;
  }
}
