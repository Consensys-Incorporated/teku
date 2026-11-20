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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_BUILDER_BID_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.xmss.XmssConstants;

class XmssLeafRecordTest {

  private static final UInt64 FIRST_ATTESTATION_LEAF = XMSS_RESERVED_LEAVES;
  private static final UInt64 FIRST_BUILDER_BID_LEAF =
      XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES);
  private static final UInt64 FIRST_PER_SLOT_LEAF =
      FIRST_BUILDER_BID_LEAF.plus(XMSS_BUILDER_BID_LEAVES);

  private static final Bytes32 ROOT_A = Bytes32.fromHexStringLenient("0xa1");
  private static final Bytes32 ROOT_B = Bytes32.fromHexStringLenient("0xb2");

  @Test
  void maySign_allowsAnUnusedLeafInEveryRegion() {
    for (final UInt64 leaf :
        new UInt64[] {
          UInt64.ZERO,
          FIRST_ATTESTATION_LEAF,
          FIRST_BUILDER_BID_LEAF,
          FIRST_PER_SLOT_LEAF,
          XmssConstants.MAX_LEAF_INDEX
        }) {
      final XmssLeafRecord record = XmssLeafRecord.empty().maySign(leaf, ROOT_A).orElseThrow();
      assertThat(record.getSigningRoot(leaf)).contains(ROOT_A);
    }
  }

  @Test
  void maySign_allowsTheSameRootAgainWithoutChangingTheRecord() {
    final UInt64 leaf = FIRST_PER_SLOT_LEAF.plus(80);
    final XmssLeafRecord record = XmssLeafRecord.empty().maySign(leaf, ROOT_A).orElseThrow();

    assertThat(record.maySign(leaf, ROOT_A)).containsSame(record);
  }

  @Test
  void maySign_refusesADifferentRootAtAUsedLeaf() {
    final UInt64 leaf = FIRST_ATTESTATION_LEAF.plus(7);
    final XmssLeafRecord record = XmssLeafRecord.empty().maySign(leaf, ROOT_A).orElseThrow();
    assertThat(record.maySign(leaf, ROOT_B)).isEmpty();

    final XmssLeafRecord reserved =
        XmssLeafRecord.empty().maySign(UInt64.valueOf(3), ROOT_A).orElseThrow();
    assertThat(reserved.maySign(UInt64.valueOf(3), ROOT_B)).isEmpty();
  }

  @Test
  void maySign_rejectsLeavesBeyondTheKey() {
    assertThatThrownBy(
            () -> XmssLeafRecord.empty().maySign(XmssConstants.MAX_LEAF_INDEX.plus(1), ROOT_A))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nextReservedLeaf_allocatesInIncreasingOrder() {
    XmssLeafRecord record = XmssLeafRecord.empty();
    assertThat(record.nextReservedLeaf()).contains(UInt64.ZERO);

    record = record.maySign(UInt64.ZERO, ROOT_A).orElseThrow();
    assertThat(record.nextReservedLeaf()).contains(UInt64.ONE);

    // A reserved leaf signed out of order moves allocation past it; lower unused leaves are skipped
    record = record.maySign(UInt64.valueOf(9), ROOT_B).orElseThrow();
    assertThat(record.nextReservedLeaf()).contains(UInt64.valueOf(10));
  }

  @Test
  void nextReservedLeaf_isEmptyOnceTheLastReservedLeafIsUsed() {
    final XmssLeafRecord record =
        XmssLeafRecord.empty().maySign(XMSS_RESERVED_LEAVES.minus(1), ROOT_A).orElseThrow();

    assertThat(record.nextReservedLeaf()).isEmpty();
  }

  @Test
  void maySign_refusesAttestationLeavesOlderThanTheWindow() {
    XmssLeafRecord record = XmssLeafRecord.empty();
    for (int epoch = 0; epoch < 100; epoch++) {
      record = record.maySign(FIRST_ATTESTATION_LEAF.plus(epoch), ROOT_A).orElseThrow();
    }

    // The newest 64 epochs keep their exact roots
    assertThat(record.maySign(FIRST_ATTESTATION_LEAF.plus(36), ROOT_A)).containsSame(record);
    assertThat(record.maySign(FIRST_ATTESTATION_LEAF.plus(36), ROOT_B)).isEmpty();
    // Older ones are refused, whatever the root
    assertThat(record.getSigningRoot(FIRST_ATTESTATION_LEAF.plus(35))).isEmpty();
    assertThat(record.maySign(FIRST_ATTESTATION_LEAF.plus(35), ROOT_A)).isEmpty();
    assertThat(record.maySign(FIRST_ATTESTATION_LEAF, ROOT_A)).isEmpty();
  }

  @Test
  void maySign_allowsPerSlotLeavesBelowOnesSignedAhead() {
    final UInt64 slot = UInt64.valueOf(1000);
    // Proposer preferences (offset 3) for a slot an epoch ahead
    XmssLeafRecord record =
        XmssLeafRecord.empty().maySign(perSlotLeaf(slot.plus(32), 3), ROOT_A).orElseThrow();

    // A payload attestation (offset 2) and a block (offset 0) for the current slot
    record = record.maySign(perSlotLeaf(slot, 2), ROOT_B).orElseThrow();
    assertThat(record.maySign(perSlotLeaf(slot, 0), ROOT_A)).isPresent();
  }

  @Test
  void maySign_keepsRegionsApart() {
    final XmssLeafRecord record =
        XmssLeafRecord.empty().maySign(FIRST_PER_SLOT_LEAF.plus(1L << 20), ROOT_A).orElseThrow();

    // A far per-slot leaf does not raise the attestation or reserved watermarks
    assertThat(record.maySign(FIRST_ATTESTATION_LEAF, ROOT_A)).isPresent();
    assertThat(record.maySign(UInt64.ZERO, ROOT_A)).isPresent();
  }

  @Test
  void toBytes_roundTrips() {
    XmssLeafRecord record = XmssLeafRecord.empty();
    record = record.maySign(UInt64.valueOf(2), ROOT_A).orElseThrow();
    for (int epoch = 0; epoch < 70; epoch++) {
      record = record.maySign(FIRST_ATTESTATION_LEAF.plus(epoch), ROOT_B).orElseThrow();
    }
    record = record.maySign(perSlotLeaf(UInt64.valueOf(12), 0), ROOT_A).orElseThrow();

    final XmssLeafRecord decoded = XmssLeafRecord.fromBytes(record.toBytes());

    assertThat(decoded).isEqualTo(record);
    assertThat(decoded.maySign(FIRST_ATTESTATION_LEAF.plus(5), ROOT_B)).isEmpty();
    assertThat(decoded.nextReservedLeaf()).contains(UInt64.valueOf(3));
    assertThat(XmssLeafRecord.fromBytes(XmssLeafRecord.empty().toBytes()))
        .isEqualTo(XmssLeafRecord.empty());
  }

  @Test
  void fromBytes_rejectsMalformedRecords() {
    final Bytes valid =
        XmssLeafRecord.empty().maySign(UInt64.valueOf(2), ROOT_A).orElseThrow().toBytes();

    assertThatThrownBy(() -> XmssLeafRecord.fromBytes(Bytes.EMPTY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> XmssLeafRecord.fromBytes(Bytes.concatenate(Bytes.of(2), valid.slice(1))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> XmssLeafRecord.fromBytes(valid.slice(0, valid.size() - 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> XmssLeafRecord.fromBytes(Bytes.concatenate(valid, Bytes.of(0))))
        .isInstanceOf(IllegalArgumentException.class);
    // The reserved region's one leaf (after version, watermark and count) replaced by an
    // attestation leaf
    final Bytes leafOutsideRegion =
        Bytes.concatenate(
            valid.slice(0, 13),
            Bytes.ofUnsignedLong(FIRST_ATTESTATION_LEAF.longValue(), ByteOrder.LITTLE_ENDIAN),
            valid.slice(21));
    assertThatThrownBy(() -> XmssLeafRecord.fromBytes(leafOutsideRegion))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static UInt64 perSlotLeaf(final UInt64 slot, final int offset) {
    return FIRST_PER_SLOT_LEAF.plus(slot.times(8)).plus(offset);
  }
}
