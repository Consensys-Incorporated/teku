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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.xmss.LeanVmSidecar.Claim;
import tech.pegasys.teku.xmss.LeanVmSidecar.ProofOfClaims;
import tech.pegasys.teku.xmss.LeanVmSidecar.SignedClaim;

/** Proves, merges and verifies aggregate proofs of leanXMSS signatures, such as leanVM's. */
public interface XmssAggregator {

  /** A proof that every signature signs its claim. */
  Bytes prove(List<SignedClaim> signedClaims);

  /** One proof of the union of the children's claims; empty when no child claims anything. */
  Bytes merge(List<ProofOfClaims> children);

  /** Whether {@code proof} proves exactly {@code claims}; no claims need an empty proof. */
  boolean verify(List<Claim> claims, Bytes proof);
}
