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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class FastConfirmationEvent extends Event<FastConfirmationEvent.FastConfirmationData> {

  static final SerializableTypeDefinition<FastConfirmationData> FAST_CONFIRMATION_EVENT_TYPE =
      SerializableTypeDefinition.object(FastConfirmationData.class)
          .name("FastConfirmationEvent")
          .withField("block", BYTES32_TYPE, FastConfirmationData::getBlock)
          .withField("slot", UINT64_TYPE, FastConfirmationData::getSlot)
          .withField("current_slot", UINT64_TYPE, FastConfirmationData::getCurrentSlot)
          .build();

  FastConfirmationEvent(final Bytes32 block, final UInt64 slot, final UInt64 currentSlot) {
    super(FAST_CONFIRMATION_EVENT_TYPE, new FastConfirmationData(block, slot, currentSlot));
  }

  public static class FastConfirmationData {
    public final Bytes32 block;
    public final UInt64 slot;
    public final UInt64 currentSlot;

    FastConfirmationData(final Bytes32 block, final UInt64 slot, final UInt64 currentSlot) {
      this.block = block;
      this.slot = slot;
      this.currentSlot = currentSlot;
    }

    private Bytes32 getBlock() {
      return block;
    }

    private UInt64 getSlot() {
      return slot;
    }

    private UInt64 getCurrentSlot() {
      return currentSlot;
    }
  }
}
