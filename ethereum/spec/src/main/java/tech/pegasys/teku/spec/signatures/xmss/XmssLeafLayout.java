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
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.xmss.XmssSigningKey;

/** Which leanXMSS bottom trees a validator signs from, given the spec's leaf assignment. */
public final class XmssLeafLayout {

  private XmssLeafLayout() {}

  /**
   * The bottom trees a validator signs from at {@code slot}, in leaf order: the tree of the
   * attestation leaf for the slot's epoch, and the tree of the slot's own leaves (block, aggregate,
   * payload attestation, preferences, envelope). Empty before the PQ fork.
   *
   * <p>Reserved leaves, used for exits and preregistrations, are not included: nothing times those
   * signatures.
   */
  public static List<Long> bottomTreesAt(final Spec spec, final UInt64 slot) {
    if (!(spec.atSlot(slot).miscHelpers() instanceof MiscHelpersPq miscHelpers)) {
      return List.of();
    }
    final UInt64 attestationLeaf =
        miscHelpers.computeXmssAttestationLeafIndex(miscHelpers.computeEpochAtSlot(slot));
    // A slot's leaves start at a multiple of their count, so they share one bottom tree
    final UInt64 firstSlotLeaf = miscHelpers.computeXmssLeafIndex(slot, UInt64.ZERO);
    return Stream.of(attestationLeaf, firstSlotLeaf)
        .map(XmssSigningKey::bottomTreeOf)
        .distinct()
        .sorted()
        .toList();
  }
}
