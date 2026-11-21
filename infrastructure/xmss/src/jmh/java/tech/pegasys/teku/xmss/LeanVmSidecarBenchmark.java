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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import tech.pegasys.teku.xmss.LeanVmSidecar.Claim;
import tech.pegasys.teku.xmss.LeanVmSidecar.ProofOfClaims;
import tech.pegasys.teku.xmss.LeanVmSidecar.SignedClaim;

/**
 * leanVM aggregate proofs through a {@code leanvm-sidecar} over its Unix socket.
 *
 * <pre>
 * LEANVM_SIDECAR_BINARY=/usr/local/bin/leanvm-sidecar \
 *   ./gradlew :infrastructure:xmss:jmh --args="LeanVmSidecarBenchmark"
 * LEANVM_SIDECAR_SOCKET=/run/leanvm.sock \
 *   ./gradlew :infrastructure:xmss:jmh --args="LeanVmSidecarBenchmark.prove -p signatures=64"
 * </pre>
 *
 * <p>With {@code LEANVM_SIDECAR_SOCKET} the benchmark connects to a sidecar already listening
 * there; otherwise it starts {@code LEANVM_SIDECAR_BINARY} (default {@code
 * /usr/local/bin/leanvm-sidecar}, the Docker image's) on a socket of its own, with {@code
 * LEANVM_SIDECAR_THREADS} proving threads (default 0: one per core).
 *
 * <p>Signatures are from distinct keys over one message, as in a committee's attestations. Proof
 * sizes are printed during setup.
 */
@Fork(value = 1, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
public class LeanVmSidecarBenchmark {

  private static final Bytes32 MESSAGE = Bytes32.fromHexString("0x" + "ef".repeat(32));

  private static LeanVmSidecar openSidecar(final Path socketDir) {
    final Optional<String> socket = env("LEANVM_SIDECAR_SOCKET");
    if (socket.isPresent()) {
      return LeanVmSidecar.connect(Path.of(socket.get()));
    }
    return LeanVmSidecar.start(
        Path.of(env("LEANVM_SIDECAR_BINARY").orElse("/usr/local/bin/leanvm-sidecar")),
        socketDir.resolve("leanvm.sock"),
        Integer.parseInt(env("LEANVM_SIDECAR_THREADS").orElse("0")));
  }

  private static Optional<String> env(final String name) {
    return Optional.ofNullable(System.getenv(name)).filter(value -> !value.isBlank());
  }

  /** {@code count} keys, from seeds {@code firstSeed} on, loaded in parallel. */
  private static List<XmssSigningKey> loadKeys(final int firstSeed, final int count) {
    return IntStream.range(firstSeed, firstSeed + count)
        .parallel()
        .mapToObj(
            i ->
                Xmss.loadSigningKey(
                    XmssSecretKey.fromBytes(Bytes32.leftPad(Bytes.ofUnsignedInt(i + 1L)))))
        .toList();
  }

  /** Each key signs {@code message} at {@code leaf}. */
  private static List<SignedClaim> sign(
      final List<XmssSigningKey> keys, final long leaf, final Bytes32 message) {
    return keys.parallelStream()
        .map(
            key ->
                new SignedClaim(
                    new Claim(key.getPublicKey().toBytes(), message),
                    key.sign(UInt64.valueOf(leaf), message).toBytes()))
        .toList();
  }

  private static List<Claim> claimsOf(final List<SignedClaim> signedClaims) {
    return signedClaims.stream().map(SignedClaim::claim).toList();
  }

  private static Path createSocketDir() {
    try {
      return Files.createTempDirectory("leanvm-bench");
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void deleteSocketDir(final Path socketDir) {
    try (var paths = Files.walk(socketDir)) {
      paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
    } catch (final IOException e) {
      // Best effort
    }
  }

  /** One sidecar, {@code signatures} signed claims and a proof of them. */
  @State(Scope.Benchmark)
  public static class Signed {
    @Param({"1", "16", "64", "128"})
    int signatures;

    Path socketDir;
    LeanVmSidecar sidecar;
    List<SignedClaim> signedClaims;
    List<Claim> claims;
    Bytes proof;

    @Setup(Level.Trial)
    public void setUp() {
      final List<XmssSigningKey> keys = loadKeys(0, signatures);
      try {
        signedClaims = sign(keys, 0, MESSAGE);
      } finally {
        keys.forEach(XmssSigningKey::close);
      }
      claims = claimsOf(signedClaims);
      socketDir = createSocketDir();
      sidecar = openSidecar(socketDir);
      proof = sidecar.prove(signedClaims);
      if (!sidecar.verify(claims, proof)) {
        throw new IllegalStateException("Proof of " + signatures + " signatures doesn't verify");
      }
      System.out.printf("%nProof of %d signatures: %,d bytes%n", signatures, proof.size());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      sidecar.close();
      deleteSocketDir(socketDir);
    }
  }

  /** Proving {@code signatures} signatures. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public Bytes prove(final Signed state) {
    return state.sidecar.prove(state.signedClaims);
  }

  /** Verifying a proof of {@code signatures} signatures. */
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 2, time = 2)
  @Measurement(iterations = 5, time = 2)
  public boolean verify(final Signed state) {
    return state.sidecar.verify(state.claims, state.proof);
  }

  /**
   * One sidecar and {@code children} proofs of {@value #SIGNATURES_PER_CHILD} signatures each, over
   * different messages, as when a block's attestations for several committees are merged.
   */
  @State(Scope.Benchmark)
  public static class Children {
    static final int SIGNATURES_PER_CHILD = 8;

    @Param({"2", "4", "16"})
    int children;

    Path socketDir;
    LeanVmSidecar sidecar;
    List<ProofOfClaims> proofs;

    @Setup(Level.Trial)
    public void setUp() {
      final List<XmssSigningKey> keys = loadKeys(1000, SIGNATURES_PER_CHILD);
      socketDir = createSocketDir();
      sidecar = openSidecar(socketDir);
      proofs = new ArrayList<>();
      try {
        for (int child = 0; child < children; child++) {
          final Bytes32 message = Bytes32.leftPad(Bytes.ofUnsignedInt(child + 1L));
          final List<SignedClaim> signedClaims = sign(keys, child, message);
          proofs.add(new ProofOfClaims(claimsOf(signedClaims), sidecar.prove(signedClaims)));
        }
      } finally {
        keys.forEach(XmssSigningKey::close);
      }
      final Bytes merged = sidecar.merge(proofs);
      final List<Claim> allClaims =
          proofs.stream().flatMap(proof -> proof.claims().stream()).toList();
      if (!sidecar.verify(allClaims, merged)) {
        throw new IllegalStateException("Merge of " + children + " proofs doesn't verify");
      }
      System.out.printf(
          "%nMerge of %d proofs of %d signatures: %,d bytes%n",
          children, SIGNATURES_PER_CHILD, merged.size());
    }

    @TearDown(Level.Trial)
    public void tearDown() {
      sidecar.close();
      deleteSocketDir(socketDir);
    }
  }

  /** Merging {@code children} proofs into one, verifying each child first. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public Bytes merge(final Children state) {
    return state.sidecar.merge(state.proofs);
  }
}
