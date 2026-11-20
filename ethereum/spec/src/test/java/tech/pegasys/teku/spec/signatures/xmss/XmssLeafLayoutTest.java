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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

class XmssLeafLayoutTest {

  // Per-slot leaves start in the middle of bottom tree 24576, so it holds 4096 slots of them
  private static final long FIRST_PER_SLOT_TREE = 24576;
  private static final long SLOTS_IN_FIRST_PER_SLOT_TREE = 4096;
  private static final long SLOTS_PER_BOTTOM_TREE = 8192;
  private static final long EPOCHS_IN_FIRST_ATTESTATION_TREE = 32768;

  private final Spec spec = TestSpecFactory.createMinimalPq();

  @Test
  void bottomTreesAt_atGenesisAreTheAttestationAndPerSlotTrees() {
    assertThat(XmssLeafLayout.bottomTreesAt(spec, UInt64.ZERO))
        .containsExactly(0L, FIRST_PER_SLOT_TREE);
  }

  @Test
  void bottomTreesAt_perSlotTreeChangesAfter4096SlotsThenEvery8192() {
    final long secondTreeStart = SLOTS_IN_FIRST_PER_SLOT_TREE;
    final long thirdTreeStart = secondTreeStart + SLOTS_PER_BOTTOM_TREE;

    assertThat(perSlotTreeAt(secondTreeStart - 1)).isEqualTo(FIRST_PER_SLOT_TREE);
    assertThat(perSlotTreeAt(secondTreeStart)).isEqualTo(FIRST_PER_SLOT_TREE + 1);
    assertThat(perSlotTreeAt(thirdTreeStart - 1)).isEqualTo(FIRST_PER_SLOT_TREE + 1);
    assertThat(perSlotTreeAt(thirdTreeStart)).isEqualTo(FIRST_PER_SLOT_TREE + 2);
  }

  @Test
  void bottomTreesAt_attestationTreeChangesAfter32768Epochs() {
    final UInt64 firstSlotOfSecondTree =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(EPOCHS_IN_FIRST_ATTESTATION_TREE));

    assertThat(XmssLeafLayout.bottomTreesAt(spec, firstSlotOfSecondTree.minus(1)).getFirst())
        .isEqualTo(0L);
    assertThat(XmssLeafLayout.bottomTreesAt(spec, firstSlotOfSecondTree).getFirst()).isEqualTo(1L);
  }

  @Test
  void bottomTreesAt_isEmptyBeforeThePqFork() {
    final Spec specWithLaterPq = TestSpecFactory.createMinimalWithPqForkEpoch(UInt64.ONE);
    final UInt64 forkSlot = specWithLaterPq.computeStartSlotAtEpoch(UInt64.ONE);

    assertThat(XmssLeafLayout.bottomTreesAt(specWithLaterPq, forkSlot.minus(1))).isEmpty();
    assertThat(XmssLeafLayout.bottomTreesAt(specWithLaterPq, forkSlot)).hasSize(2);
  }

  private long perSlotTreeAt(final long slot) {
    return XmssLeafLayout.bottomTreesAt(spec, UInt64.valueOf(slot)).getLast();
  }
}
