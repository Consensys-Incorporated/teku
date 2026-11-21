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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.xmss.XmssConstants;

/**
 * Builds the STARK proofs of XMSS signatures that PQ attestations carry instead of aggregate
 * signatures: {@code stark.ProveAggregates} and {@code stark.MergeProofs}.
 */
public interface StarkProver {

  /** The stub proof the specification's stand-in returns while XMSS is inactive. */
  Bytes STUB_PROOF = Bytes.EMPTY;

  /**
   * The specification's reference stand-in for a recursive STARK, which {@link
   * StarkProofVerifier#concatenated} checks: a proof is the signatures, concatenated in order.
   */
  StarkProver CONCATENATED =
      new StarkProver() {
        @Override
        public Bytes proveAggregates(
            final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
          if (XmssConstants.verificationDisabled) {
            return STUB_PROOF;
          }
          checkArgument(
              statements.size() == signatures.size(),
              "%s statements but signatures for %s",
              statements.size(),
              signatures.size());
          for (int i = 0; i < statements.size(); i++) {
            checkArgument(
                statements.get(i).pubkeys().size() == signatures.get(i).size(),
                "Statement %s has %s public keys but %s signatures",
                i,
                statements.get(i).pubkeys().size(),
                signatures.get(i).size());
          }
          return Bytes.wrap(signatures.stream().flatMap(List::stream).toArray(Bytes[]::new));
        }

        @Override
        public long proofSizeOf(final int signatureCount) {
          return XmssConstants.verificationDisabled
              ? STUB_PROOF.size()
              : (long) signatureCount * XMSS_SIGNATURE_SIZE;
        }

        @Override
        public Bytes mergeProofs(final List<ProvenStatements> proofs) {
          if (XmssConstants.verificationDisabled) {
            return STUB_PROOF;
          }
          return Bytes.wrap(proofs.stream().map(ProvenStatements::proof).toArray(Bytes[]::new));
        }
      };

  /**
   * {@code stark.ProveAggregates}: proves, for each statement, the signatures of its public keys in
   * order.
   */
  Bytes proveAggregates(List<XmssAggregateStatement> statements, List<List<Bytes>> signatures);

  /** The size of a proof of {@code signatureCount} signatures, before it is built. */
  long proofSizeOf(int signatureCount);

  /**
   * Whether a merged proof is as large as its children together, as the concatenation stand-in's
   * is, so a block's proof size budget must count each child. A recursive proof's size barely grows
   * with what it proves.
   */
  default boolean isMergedProofSizeTheSumOfChildren() {
    return true;
  }

  /**
   * {@code stark.MergeProofs}: one proof of the concatenation of the proofs' statements. Each proof
   * comes with its statements, which a recursive prover needs to fold it in.
   */
  Bytes mergeProofs(List<ProvenStatements> proofs);
}
