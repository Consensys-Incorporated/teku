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

import static tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;

/**
 * {@code stark.VerifyAggregates}: whether a proof proves, for each statement and each of its public
 * keys in order, an XMSS signature of the statement's message at its leaf index.
 */
public interface StarkProofVerifier {

  /** Accepts every proof, as the reference tests' disabled-XMSS mode does. */
  StarkProofVerifier NO_OP = (statements, proof) -> true;

  boolean verifyAggregates(List<XmssAggregateStatement> statements, Bytes proof);

  /**
   * The specification's reference stand-in for a recursive STARK: the proof is the concatenation of
   * the signatures, checked one by one with {@code xmssVerifier}.
   */
  static StarkProofVerifier concatenated(final XmssSignatureVerifier xmssVerifier) {
    return (statements, proof) -> {
      int offset = 0;
      for (final XmssAggregateStatement statement : statements) {
        for (final Bytes32 pubkey : statement.pubkeys()) {
          if (offset + XMSS_SIGNATURE_SIZE > proof.size()) {
            return false;
          }
          final Bytes signature = proof.slice(offset, XMSS_SIGNATURE_SIZE);
          if (!xmssVerifier.verify(pubkey, statement.leafIndex(), statement.message(), signature)) {
            return false;
          }
          offset += XMSS_SIGNATURE_SIZE;
        }
      }
      return offset == proof.size();
    };
  }
}
