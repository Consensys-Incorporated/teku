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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

public class VoteTrackerSerializerTest {
  private static final Bytes32 CURRENT_ROOT =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final Bytes32 NEXT_ROOT =
      Bytes32.fromHexString("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");

  private static final Spec SPEC =
      TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(2));
  private static final int SLOTS_PER_EPOCH = SPEC.getGenesisSpecConfig().getSlotsPerEpoch();
  private static final UInt64 FIRST_GLOAS_SLOT = SPEC.computeStartSlotAtEpoch(UInt64.valueOf(2));
  private final VoteTrackerSerializer serializer = new VoteTrackerSerializer(SPEC);

  @Test
  void roundTrip_gloas() {
    final VoteTracker vote =
        new VoteTracker(
            CURRENT_ROOT,
            NEXT_ROOT,
            true,
            false,
            FIRST_GLOAS_SLOT.plus(2),
            true,
            FIRST_GLOAS_SLOT.plus(1),
            false);
    final byte[] serialized = serializer.serialize(vote);
    assertThat(serialized).hasSize(84);

    final VoteTracker deserialized = serializer.deserialize(serialized);
    assertThat(deserialized).isEqualTo(vote);
  }

  @Test
  void roundTrip_gloasWithDefaultSlots() {
    final VoteTracker vote = new VoteTracker(CURRENT_ROOT, NEXT_ROOT, true, false);
    final byte[] serialized = serializer.serialize(vote);
    assertThat(serialized).hasSize(74);

    final VoteTracker deserialized = serializer.deserialize(serialized);
    assertThat(deserialized).isEqualTo(vote);
  }

  @Test
  void roundTrip_gloasDefault() {
    final byte[] serialized = serializer.serialize(VoteTracker.DEFAULT);
    assertThat(serialized).hasSize(74);

    final VoteTracker deserialized = serializer.deserialize(serialized);
    assertThat(deserialized).isEqualTo(VoteTracker.DEFAULT);
  }

  @Test
  void serialize_preGloasVote_usesLegacyFormatAndDowngradesOnRead() {
    final VoteTracker vote =
        new VoteTracker(
            CURRENT_ROOT,
            NEXT_ROOT,
            true,
            false,
            UInt64.valueOf(SLOTS_PER_EPOCH + 2L),
            false,
            UInt64.valueOf(SLOTS_PER_EPOCH + 1L),
            false);

    final byte[] serialized = serializer.serialize(vote);

    assertThat(serialized).hasSize(74);
    assertThat(serializer.deserialize(serialized))
        .isEqualTo(
            new VoteTracker(
                CURRENT_ROOT,
                NEXT_ROOT,
                true,
                false,
                UInt64.valueOf(SLOTS_PER_EPOCH),
                false,
                UInt64.ZERO,
                false));
  }

  @Test
  void deserialize_legacyPhase0WithoutEquivocation() {
    // 72-byte format: currentRoot(32) | nextRoot(32) | epoch(8)
    final byte[] legacyData =
        SSZ.encode(
                writer -> {
                  writer.writeFixedBytes(CURRENT_ROOT);
                  writer.writeFixedBytes(NEXT_ROOT);
                  writer.writeUInt64(5L);
                })
            .toArrayUnsafe();

    assertThat(legacyData).hasSize(72);
    final VoteTracker deserialized = serializer.deserialize(legacyData);
    // epoch 5 * slots_per_epoch
    assertThat(deserialized)
        .isEqualTo(
            new VoteTracker(
                CURRENT_ROOT,
                NEXT_ROOT,
                false,
                false,
                UInt64.valueOf(5L * SLOTS_PER_EPOCH),
                false,
                UInt64.ZERO,
                false));
  }

  @Test
  void deserialize_legacyPhase0WithEquivocation() {
    // 74-byte format: currentRoot(32) | nextRoot(32) | epoch(8) | nextEquiv(1) | curEquiv(1)
    final byte[] legacyData =
        SSZ.encode(
                writer -> {
                  writer.writeFixedBytes(CURRENT_ROOT);
                  writer.writeFixedBytes(NEXT_ROOT);
                  writer.writeUInt64(5L);
                  writer.writeBoolean(true);
                  writer.writeBoolean(false);
                })
            .toArrayUnsafe();

    assertThat(legacyData).hasSize(74);
    final VoteTracker deserialized = serializer.deserialize(legacyData);
    // epoch 5 * slots_per_epoch
    assertThat(deserialized)
        .isEqualTo(
            new VoteTracker(
                CURRENT_ROOT,
                NEXT_ROOT,
                true,
                false,
                UInt64.valueOf(5L * SLOTS_PER_EPOCH),
                false,
                UInt64.ZERO,
                false));
  }

  @Test
  void serialize_postGloasVote_usesNewFormat() {
    final VoteTracker vote =
        new VoteTracker(
            CURRENT_ROOT,
            NEXT_ROOT,
            true,
            false,
            FIRST_GLOAS_SLOT.plus(2),
            true,
            FIRST_GLOAS_SLOT.plus(1),
            false);

    final byte[] serialized = serializer.serialize(vote);

    assertThat(serialized).hasSize(84);
  }

  @Test
  void deserialize_rejectsUnknownLength() {
    assertThatThrownBy(() -> serializer.deserialize(new byte[85]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported VoteTracker serialized length: 85");
  }
}
