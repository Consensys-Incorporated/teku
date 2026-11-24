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
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssAggregator.Claim;
import tech.pegasys.teku.xmss.XmssAggregator.ProofOfClaims;
import tech.pegasys.teku.xmss.XmssAggregator.SignedClaim;

/**
 * leanVM aggregate proofs, proven and verified inside the benchmark's process, the way the beacon
 * node uses them.
 *
 * <pre>
 * ./gradlew :infrastructure:xmss:jmh --args="LeanVmBenchmark"
 * LEANVM_THREADS=8 ./gradlew :infrastructure:xmss:jmh --args="LeanVmBenchmark.prove -p signatures=64"
 * </pre>
 *
 * <p>The prover runs with {@code LEANVM_THREADS} threads (default 0: one per core).
 *
 * <p>Signatures are from distinct keys over one message, as in a committee's attestations. Proof
 * sizes are printed during setup.
 */
@Fork(value = 1, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
public class LeanVmBenchmark {

  private static final Bytes32 MESSAGE = Bytes32.fromHexString("0x" + "ef".repeat(32));

  private static LeanVmAggregator startLeanVm() {
    return LeanVmAggregator.start(
        Integer.parseInt(
            Optional.ofNullable(System.getenv("LEANVM_THREADS"))
                .filter(value -> !value.isBlank())
                .orElse("0")));
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

  /** {@code signatures} signed claims and a proof of them. */
  @State(Scope.Benchmark)
  public static class Signed {
    @Param({"1", "16", "64", "128"})
    int signatures;

    LeanVmAggregator leanVm;
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
      leanVm = startLeanVm();
      proof = leanVm.prove(signedClaims);
      if (!leanVm.verify(claims, proof)) {
        throw new IllegalStateException("Proof of " + signatures + " signatures doesn't verify");
      }
      System.out.printf("%nProof of %d signatures: %,d bytes%n", signatures, proof.size());
    }
  }

  /** Proving {@code signatures} signatures. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public Bytes prove(final Signed state) {
    return state.leanVm.prove(state.signedClaims);
  }

  /** Verifying a proof of {@code signatures} signatures. */
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 2, time = 2)
  @Measurement(iterations = 5, time = 2)
  public boolean verify(final Signed state) {
    return state.leanVm.verify(state.claims, state.proof);
  }

  /**
   * {@code children} proofs of {@value #SIGNATURES_PER_CHILD} signatures each, over different
   * messages, as when a block's attestations for several committees are merged.
   */
  @State(Scope.Benchmark)
  public static class Children {
    static final int SIGNATURES_PER_CHILD = 8;

    @Param({"2", "4", "16"})
    int children;

    LeanVmAggregator leanVm;
    List<ProofOfClaims> proofs;

    @Setup(Level.Trial)
    public void setUp() {
      final List<XmssSigningKey> keys = loadKeys(1000, SIGNATURES_PER_CHILD);
      leanVm = startLeanVm();
      proofs = new ArrayList<>();
      try {
        for (int child = 0; child < children; child++) {
          final Bytes32 message = Bytes32.leftPad(Bytes.ofUnsignedInt(child + 1L));
          final List<SignedClaim> signedClaims = sign(keys, child, message);
          proofs.add(new ProofOfClaims(claimsOf(signedClaims), leanVm.prove(signedClaims)));
        }
      } finally {
        keys.forEach(XmssSigningKey::close);
      }
      final Bytes merged = leanVm.merge(proofs);
      final List<Claim> allClaims =
          proofs.stream().flatMap(proof -> proof.claims().stream()).toList();
      if (!leanVm.verify(allClaims, merged)) {
        throw new IllegalStateException("Merge of " + children + " proofs doesn't verify");
      }
      System.out.printf(
          "%nMerge of %d proofs of %d signatures: %,d bytes%n",
          children, SIGNATURES_PER_CHILD, merged.size());
    }
  }

  /** Merging {@code children} proofs into one, verifying each child first. */
  @Benchmark
  @BenchmarkMode(Mode.SingleShotTime)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Warmup(iterations = 1)
  @Measurement(iterations = 5)
  public Bytes merge(final Children state) {
    return state.leanVm.merge(state.proofs);
  }
}
