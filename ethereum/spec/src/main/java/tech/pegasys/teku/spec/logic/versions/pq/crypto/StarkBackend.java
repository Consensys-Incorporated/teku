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

package tech.pegasys.teku.spec.logic.versions.pq.crypto;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.xmss.XmssAggregator;

/**
 * Which STARK implementation PQ attestation proofs use: the specification's concatenation stand-in,
 * the default and what the reference tests expect, or recursive leanVM proofs ({@link LeanVmStark})
 * once a node has a leanVM sidecar, as PQ devnet clients use. Like {@code XmssConstants}, it is a
 * process-wide switch: the spec is built before the node starts its sidecar, so its verifier reads
 * the choice at each call.
 */
public final class StarkBackend {

  private static volatile Optional<LeanVmStark> leanVm = Optional.empty();

  private StarkBackend() {}

  /** Makes every PQ proof a leanVM proof from {@code aggregator}. */
  public static void useLeanVm(final XmssAggregator aggregator) {
    leanVm = Optional.of(new LeanVmStark(aggregator));
  }

  /** Back to the specification's concatenation stand-in. */
  public static void useConcatenation() {
    leanVm = Optional.empty();
  }

  public static boolean isLeanVm() {
    return leanVm.isPresent();
  }

  /** {@code stark.VerifyAggregates} with whichever backend is in use when it is called. */
  public static StarkProofVerifier verifier(final XmssSignatureVerifier xmssVerifier) {
    final StarkProofVerifier concatenated = StarkProofVerifier.concatenated(xmssVerifier);
    return (statements, proof) ->
        leanVm
            .<StarkProofVerifier>map(backend -> backend)
            .orElse(concatenated)
            .verifyAggregates(statements, proof);
  }

  /** {@code stark.ProveAggregates} and {@code stark.MergeProofs} with the backend in use. */
  public static StarkProver prover() {
    return new StarkProver() {
      @Override
      public Bytes proveAggregates(
          final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
        return current().proveAggregates(statements, signatures);
      }

      @Override
      public long proofSizeOf(final int signatureCount) {
        return current().proofSizeOf(signatureCount);
      }

      @Override
      public boolean isMergedProofSizeTheSumOfChildren() {
        return current().isMergedProofSizeTheSumOfChildren();
      }

      @Override
      public boolean isIncrementallyMergeable() {
        return current().isIncrementallyMergeable();
      }

      @Override
      public Bytes mergeProofs(final List<ProvenStatements> proofs) {
        return current().mergeProofs(proofs);
      }

      private StarkProver current() {
        return leanVm.<StarkProver>map(backend -> backend).orElse(StarkProver.CONCATENATED);
      }
    };
  }
}
