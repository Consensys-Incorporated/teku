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

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

// Serialization formats (detected by presence/absence of epoch field):
//
// Legacy formats (contain epoch field after the two roots):
//   currentRoot(32) | nextRoot(32) | epoch(8)
//   currentRoot(32) | nextRoot(32) | epoch(8) | nextEquiv(1) | curEquiv(1)
//
// Gloas format (used only once vote rows reach Gloas slots):
//   currentRoot(32) | nextRoot(32) | nextSlot(8) |
//   nextEquiv(1) | curEquiv(1) | nextFullHint(1) | curSlot(8) | curFullHint(1)
//
// Before Gloas activation we intentionally keep writing the legacy epoch-based format so
// rollback remains possible. Once votes actually carry Gloas-era slots, we switch to the
// slot-aware format because old binaries are already protocol-incompatible at the fork boundary.
class VoteTrackerSerializer implements KvStoreSerializer<VoteTracker> {

  // Size of the two root fields that are common to all formats
  private static final int ROOTS_SIZE = Bytes32.SIZE + Bytes32.SIZE;
  private static final int LEGACY_SIZE = ROOTS_SIZE + 8;
  private static final int LEGACY_WITH_EQUIVOCATION_SIZE = LEGACY_SIZE + 1 + 1;
  // Size of the Gloas format written by serialize()
  private static final int GLOAS_SIZE = ROOTS_SIZE + 8 + 1 + 1 + 1 + 8 + 1;

  private final Optional<UInt64> gloasStartSlot;
  private final int slotsPerEpoch;

  VoteTrackerSerializer(final Spec spec) {
    this.slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    this.gloasStartSlot =
        spec.isMilestoneSupported(SpecMilestone.GLOAS)
            ? Optional.of(
                spec.computeStartSlotAtEpoch(
                    spec.getForkSchedule().getFork(SpecMilestone.GLOAS).getEpoch()))
            : Optional.empty();
  }

  @Override
  public VoteTracker deserialize(final byte[] data) {
    if (data.length != LEGACY_SIZE
        && data.length != LEGACY_WITH_EQUIVOCATION_SIZE
        && data.length != GLOAS_SIZE) {
      throw new IllegalArgumentException(
          "Unsupported VoteTracker serialized length: "
              + data.length
              + " (expected "
              + LEGACY_SIZE
              + ", "
              + LEGACY_WITH_EQUIVOCATION_SIZE
              + ", or "
              + GLOAS_SIZE
              + ")");
    }

    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final Bytes32 currentRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 nextRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));

          if (data.length == GLOAS_SIZE) {
            // Gloas format: no epoch field
            final UInt64 nextSlot = UInt64.fromLongBits(reader.readUInt64());
            final boolean nextEquivocating = reader.readBoolean();
            final boolean currentEquivocating = reader.readBoolean();
            final boolean nextFullPayloadHint = reader.readBoolean();
            final UInt64 currentSlot = UInt64.fromLongBits(reader.readUInt64());
            final boolean currentFullPayloadHint = reader.readBoolean();
            return new VoteTracker(
                currentRoot,
                nextRoot,
                nextEquivocating,
                currentEquivocating,
                nextSlot,
                nextFullPayloadHint,
                currentSlot,
                currentFullPayloadHint);
          }

          // Legacy formats: convert epoch to slot
          final long epoch = reader.readUInt64();
          final UInt64 slot = UInt64.fromLongBits(epoch * slotsPerEpoch);

          if (reader.isComplete()) {
            return new VoteTracker(
                currentRoot, nextRoot, false, false, slot, false, UInt64.ZERO, false);
          }

          final boolean nextEquivocating = reader.readBoolean();
          final boolean currentEquivocating = reader.readBoolean();
          return new VoteTracker(
              currentRoot,
              nextRoot,
              nextEquivocating,
              currentEquivocating,
              slot,
              false,
              UInt64.ZERO,
              false);
        });
  }

  @Override
  public byte[] serialize(final VoteTracker value) {
    if (!shouldUseGloasFormat(value)) {
      return serializeLegacy(value);
    }

    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytes(value.getCurrentRoot());
              writer.writeFixedBytes(value.getNextRoot());
              writer.writeUInt64(value.getNextSlot().longValue());
              writer.writeBoolean(value.isNextEquivocating());
              writer.writeBoolean(value.isCurrentEquivocating());
              writer.writeBoolean(value.isNextFullPayloadHint());
              writer.writeUInt64(value.getCurrentSlot().longValue());
              writer.writeBoolean(value.isCurrentFullPayloadHint());
            });
    return bytes.toArrayUnsafe();
  }

  private byte[] serializeLegacy(final VoteTracker value) {
    final UInt64 epoch = value.getNextSlot().dividedBy(slotsPerEpoch);
    return SSZ.encode(
            writer -> {
              writer.writeFixedBytes(value.getCurrentRoot());
              writer.writeFixedBytes(value.getNextRoot());
              writer.writeUInt64(epoch.longValue());
              writer.writeBoolean(value.isNextEquivocating());
              writer.writeBoolean(value.isCurrentEquivocating());
            })
        .toArrayUnsafe();
  }

  private boolean shouldUseGloasFormat(final VoteTracker value) {
    if (value.isNextFullPayloadHint() || value.isCurrentFullPayloadHint()) {
      return true;
    }
    return gloasStartSlot
        .map(
            startSlot ->
                value.getNextSlot().isGreaterThanOrEqualTo(startSlot)
                    || value.getCurrentSlot().isGreaterThanOrEqualTo(startSlot))
        .orElse(false);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VoteTrackerSerializer that = (VoteTrackerSerializer) o;
    return slotsPerEpoch == that.slotsPerEpoch
        && Objects.equals(gloasStartSlot, that.gloasStartSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gloasStartSlot, slotsPerEpoch);
  }
}
