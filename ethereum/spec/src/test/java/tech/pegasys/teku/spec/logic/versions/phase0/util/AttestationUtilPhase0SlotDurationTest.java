/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * EIP-8198: attestation time checks across a slot duration change. Minimal preset with 6000 ms
 * slots and Heze at epoch 1 (slot 8, 48 s) switching to 5000 ms slots. Genesis is at time 0.
 */
class AttestationUtilPhase0SlotDurationTest {

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder -> builder.hezeForkEpoch(UInt64.ONE).slotDurationMsHeze(5000));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AttestationUtilPhase0 attestationUtil =
      (AttestationUtilPhase0) spec.forMilestone(SpecMilestone.HEZE).getAttestationUtil();

  @Test
  void propagationSlotRange_shouldEndAtTheSlotStartTimeAfterTheChange() {
    // ATTESTATION_PROPAGATION_SLOT_RANGE is 32, so an attestation at slot 7 may propagate until
    // the start of slot 40 (48,000 + 32 * 5,000 = 208,000 ms) plus 500 ms clock disparity
    final UInt64 attestationSlot = UInt64.valueOf(7);

    assertThat(
            attestationUtil.isCurrentTimeAfterAttestationPropagationSlotRange(
                attestationSlot, UInt64.ZERO, UInt64.valueOf(208_500)))
        .isFalse();
    assertThat(
            attestationUtil.isCurrentTimeAfterAttestationPropagationSlotRange(
                attestationSlot, UInt64.ZERO, UInt64.valueOf(208_501)))
        .isTrue();
  }

  @Test
  void isFromFarFuture_shouldUseTheSlotStartTimeAfterTheChange() {
    // An attestation at slot 20 is fork choice eligible at slot 21 (48,000 + 13 * 5,000 =
    // 113,000 ms), and attestations more than 3 slots (15,000 ms) ahead are from the far future
    final Attestation attestation =
        dataStructureUtil.randomAttestation(
            dataStructureUtil.randomAttestationData(
                UInt64.valueOf(20), dataStructureUtil.randomBytes32()));

    assertThat(attestationUtil.isFromFarFuture(attestation, UInt64.ZERO, UInt64.valueOf(98_000)))
        .isFalse();
    assertThat(attestationUtil.isFromFarFuture(attestation, UInt64.ZERO, UInt64.valueOf(97_999)))
        .isTrue();
  }
}
