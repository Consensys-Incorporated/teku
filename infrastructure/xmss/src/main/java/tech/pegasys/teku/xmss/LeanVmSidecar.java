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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.leanpq.Aggregate;
import org.ethereum.leanpq.LeanPqException;
import org.ethereum.leanpq.Sidecar;
import org.ethereum.leanpq.SignatureClaim;
import org.ethereum.leanpq.SignedMessage;

/**
 * Aggregate proofs of leanXMSS signatures from a leanVM sidecar ({@code leanvm-sidecar}), the
 * separate process that proves and verifies them. PQ devnet clients use these proofs where the
 * specification has its concatenation stand-in.
 *
 * <p>A proof attests a set of claims, each a public key and the 32-byte message it signed. The leaf
 * index a signature was made at is not part of a claim (leanEthereum/leanVM#787). Order and
 * repetition don't matter: a proof is of the set.
 *
 * <p>Proofs only verify with a sidecar built from a compatible leanVM revision, and a mismatch
 * shows only as proofs that fail to verify, so every node of a network must run the same revision.
 *
 * <p>Proving takes seconds and verifying milliseconds, and a connection serves one call at a time,
 * so proving and verifying use separate connections. Every method blocks.
 */
public final class LeanVmSidecar implements XmssAggregator, AutoCloseable {

  private static final Logger LOG = LogManager.getLogger();

  /** The most child proofs one aggregation takes. */
  public static final int MAX_CHILDREN_PER_AGGREGATE = 16;

  // "the aggregate does not prove the given claims" (lean_pq::Error::ClaimsMismatch), and refusals
  // of the request itself, e.g. a proof too large for the sidecar: both mean the proof is no good.
  private static final List<String> INVALID_PROOF_MESSAGES =
      List.of("does not prove the given claims", "sidecar refused");

  private final Sidecar proving;
  private final Sidecar verifying;

  private LeanVmSidecar(final Sidecar proving, final Sidecar verifying) {
    this.proving = proving;
    this.verifying = verifying;
  }

  /**
   * Launches the {@code leanvm-sidecar} executable {@code binary} listening on {@code socket}, with
   * {@code threads} proving threads (0: one per core), and connects to it. {@link #close()} stops
   * it.
   */
  public static LeanVmSidecar start(final Path binary, final Path socket, final int threads) {
    final Sidecar proving = Sidecar.start(binary, socket, threads);
    try {
      final LeanVmSidecar sidecar = new LeanVmSidecar(proving, Sidecar.connect(socket));
      LOG.info("Started leanVM sidecar {} on {} ({} prover)", binary, socket, proving.prover());
      return sidecar;
    } catch (final RuntimeException e) {
      proving.close();
      throw e;
    }
  }

  /** Connects to a leanVM sidecar already listening on {@code socket}. */
  public static LeanVmSidecar connect(final Path socket) {
    final Sidecar proving = Sidecar.connect(socket);
    try {
      final LeanVmSidecar sidecar = new LeanVmSidecar(proving, Sidecar.connect(socket));
      LOG.info("Connected to leanVM sidecar on {} ({} prover)", socket, proving.prover());
      return sidecar;
    } catch (final RuntimeException e) {
      proving.close();
      throw e;
    }
  }

  /**
   * Proves that every signature signs its claim's message under its claim's public key.
   *
   * @throws LeanVmUnavailableException if the sidecar can't be reached or refuses the input
   */
  @Override
  public Bytes prove(final List<SignedClaim> signedClaims) {
    final List<SignedMessage> signed =
        signedClaims.stream()
            .map(
                signedClaim ->
                    new SignedMessage(
                        signedClaim.claim().publicKey().toArrayUnsafe(),
                        signedClaim.claim().message().toArrayUnsafe(),
                        signedClaim.signature().toArrayUnsafe()))
            .toList();
    try (Aggregate aggregate = proving.aggregate(List.of(), signed, List.of())) {
      return Bytes.wrap(aggregate.toBytes());
    } catch (final LeanPqException e) {
      throw new LeanVmUnavailableException("leanVM sidecar failed to prove: " + e.getMessage(), e);
    }
  }

  /**
   * One proof of the union of the children's claims, folding them {@link
   * #MAX_CHILDREN_PER_AGGREGATE} at a time, round after round. A child with no claims must have an
   * empty proof and is skipped; a single child is returned as it is. Each child is verified first.
   *
   * @return an empty proof when no child claims anything
   * @throws LeanVmUnavailableException if a child doesn't verify or the sidecar fails
   */
  @Override
  public Bytes merge(final List<ProofOfClaims> children) {
    final List<ProofOfClaims> provingChildren = new ArrayList<>();
    for (final ProofOfClaims child : children) {
      if (child.claims().isEmpty()) {
        if (!child.proof().isEmpty()) {
          throw new IllegalArgumentException("A proof of no claims must be empty");
        }
        continue;
      }
      provingChildren.add(child);
    }
    if (provingChildren.isEmpty()) {
      return Bytes.EMPTY;
    }
    if (provingChildren.size() == 1) {
      return provingChildren.getFirst().proof();
    }
    final List<Aggregate> level = new ArrayList<>();
    try {
      for (final ProofOfClaims child : provingChildren) {
        level.add(
            verifying.verify(child.proof().toArrayUnsafe(), toClaims(child.claims()), List.of()));
      }
      while (level.size() > 1) {
        final List<Aggregate> next = new ArrayList<>();
        for (int start = 0; start < level.size(); start += MAX_CHILDREN_PER_AGGREGATE) {
          final List<Aggregate> chunk =
              level.subList(start, Math.min(start + MAX_CHILDREN_PER_AGGREGATE, level.size()));
          if (chunk.size() == 1) {
            next.add(chunk.getFirst());
          } else {
            next.add(proving.aggregate(chunk, List.of(), List.of()));
            chunk.forEach(Aggregate::close);
          }
        }
        level.clear();
        level.addAll(next);
      }
      return Bytes.wrap(level.getFirst().toBytes());
    } catch (final LeanPqException e) {
      throw new LeanVmUnavailableException("leanVM sidecar failed to merge: " + e.getMessage(), e);
    } finally {
      level.forEach(Aggregate::close);
    }
  }

  /**
   * Whether {@code proof} proves exactly {@code claims}. No claims need an empty proof.
   *
   * @throws LeanVmUnavailableException if the sidecar can't be reached or speaks another protocol,
   *     so the proof's validity is unknown
   */
  @Override
  public boolean verify(final List<Claim> claims, final Bytes proof) {
    if (claims.isEmpty()) {
      return proof.isEmpty();
    }
    try (Aggregate ignored = verifying.verify(proof.toArrayUnsafe(), toClaims(claims), List.of())) {
      return true;
    } catch (final LeanPqException e) {
      final String message = Optional.ofNullable(e.getMessage()).orElse("");
      if (INVALID_PROOF_MESSAGES.stream().anyMatch(message::contains)) {
        LOG.debug("leanVM proof of {} claims is invalid: {}", claims.size(), message);
        return false;
      }
      throw new LeanVmUnavailableException("leanVM sidecar failed to verify: " + message, e);
    }
  }

  @Override
  public void close() {
    try {
      verifying.close();
    } finally {
      proving.close();
    }
  }

  private static List<SignatureClaim> toClaims(final List<Claim> claims) {
    return claims.stream()
        .map(
            claim ->
                new SignatureClaim(
                    claim.publicKey().toArrayUnsafe(), claim.message().toArrayUnsafe()))
        .toList();
  }

  /** A public key and the message it signed. */
  public record Claim(Bytes32 publicKey, Bytes32 message) {}

  /** A claim with the leanXMSS signature that backs it. */
  public record SignedClaim(Claim claim, Bytes signature) {}

  /** A proof and the claims it proves. */
  public record ProofOfClaims(List<Claim> claims, Bytes proof) {}

  /** The sidecar couldn't be reached or failed, so the result is unknown. */
  public static class LeanVmUnavailableException extends RuntimeException {
    public LeanVmUnavailableException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
