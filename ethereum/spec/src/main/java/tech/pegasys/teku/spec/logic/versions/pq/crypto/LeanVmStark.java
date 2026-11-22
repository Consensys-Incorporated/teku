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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.xmss.LeanVmSidecar.Claim;
import tech.pegasys.teku.xmss.LeanVmSidecar.ProofOfClaims;
import tech.pegasys.teku.xmss.LeanVmSidecar.SignedClaim;
import tech.pegasys.teku.xmss.XmssAggregator;

/**
 * {@code stark.ProveAggregates}, {@code stark.MergeProofs} and {@code stark.VerifyAggregates} as PQ
 * devnet clients implement them: recursive leanVM proofs from an {@link XmssAggregator}, in place
 * of the specification's concatenation stand-in.
 *
 * <p>A statement becomes one claim per public key: the key and the statement's message. The leaf
 * index isn't part of a claim, so a proof shows each key signed the message at some leaf, not
 * necessarily the statement's. A proof is of a set of claims, so repeated claims count once.
 */
public class LeanVmStark implements StarkProver, StarkProofVerifier {

  private final XmssAggregator aggregator;

  public LeanVmStark(final XmssAggregator aggregator) {
    this.aggregator = aggregator;
  }

  @Override
  public Bytes proveAggregates(
      final List<XmssAggregateStatement> statements, final List<List<Bytes>> signatures) {
    checkArgument(
        statements.size() == signatures.size(),
        "%s statements but signatures for %s",
        statements.size(),
        signatures.size());
    final List<SignedClaim> signedClaims = new ArrayList<>();
    for (int i = 0; i < statements.size(); i++) {
      final XmssAggregateStatement statement = statements.get(i);
      final List<Bytes> statementSignatures = signatures.get(i);
      checkArgument(
          statement.pubkeys().size() == statementSignatures.size(),
          "Statement %s has %s public keys but %s signatures",
          i,
          statement.pubkeys().size(),
          statementSignatures.size());
      for (int j = 0; j < statement.pubkeys().size(); j++) {
        signedClaims.add(
            new SignedClaim(
                new Claim(statement.pubkeys().get(j), statement.message()),
                statementSignatures.get(j)));
      }
    }
    return signedClaims.isEmpty() ? Bytes.EMPTY : aggregator.prove(signedClaims);
  }

  /** A recursive proof's size barely depends on its claims, so the budget doesn't count them. */
  @Override
  public long proofSizeOf(final int signatureCount) {
    return 0;
  }

  @Override
  public boolean isMergedProofSizeTheSumOfChildren() {
    return false;
  }

  @Override
  public boolean isIncrementallyMergeable() {
    return true;
  }

  @Override
  public Bytes mergeProofs(final List<ProvenStatements> proofs) {
    return aggregator.merge(
        proofs.stream()
            .map(proof -> new ProofOfClaims(claims(proof.statements()), proof.proof()))
            .toList());
  }

  @Override
  public boolean verifyAggregates(
      final List<XmssAggregateStatement> statements, final Bytes proof) {
    return aggregator.verify(claims(statements), proof);
  }

  static List<Claim> claims(final List<XmssAggregateStatement> statements) {
    final List<Claim> claims = new ArrayList<>();
    for (final XmssAggregateStatement statement : statements) {
      final Bytes32 message = statement.message();
      statement.pubkeys().forEach(pubkey -> claims.add(new Claim(pubkey, message)));
    }
    return claims;
  }
}
