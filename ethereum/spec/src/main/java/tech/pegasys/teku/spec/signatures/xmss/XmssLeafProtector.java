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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssPublicKey;

/**
 * Guards leanXMSS keys against signing two different messages at one leaf (PQ validator.md,
 * "Protection best practices"). Every check that allows signing records the leaf durably before it
 * completes, so the record survives a crash between recording and publishing the signature.
 */
public interface XmssLeafProtector {

  /**
   * Records {@code signingRoot} at {@code leafIndex} for {@code publicKey} and completes with true
   * if the leaf was unused or already recorded with this signing root. Completes with false, and
   * records nothing, if signing would reuse the leaf.
   */
  SafeFuture<Boolean> maySign(XmssPublicKey publicKey, UInt64 leafIndex, Bytes32 signingRoot);

  /**
   * Allocates the next reserved leaf for {@code publicKey}, for a message without a slot of its
   * own, and records {@code signingRoot} at it. Completes with empty once every reserved leaf is
   * used.
   */
  SafeFuture<Optional<UInt64>> allocateReservedLeaf(XmssPublicKey publicKey, Bytes32 signingRoot);
}
