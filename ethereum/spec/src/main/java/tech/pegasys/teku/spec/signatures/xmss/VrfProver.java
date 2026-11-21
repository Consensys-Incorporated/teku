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

package tech.pegasys.teku.spec.signatures.xmss;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Computes a validator's VRF proofs, its RANDAO reveals, from its VRF tree. */
public interface VrfProver {

  /** The root of the validator's VRF tree, its registered VRF root. */
  Bytes32 getRoot();

  /** compute_vrf_proof: the leaf at index {@code slot}, followed by its Merkle branch. */
  List<Bytes32> computeProof(UInt64 slot);
}
