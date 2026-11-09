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

package tech.pegasys.teku.spec.datastructures.blocks;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

/** Utility functions to extract data from the ssz bytes of block variants */
public class BeaconBlockInvariants {

  private static final int BYTES_PER_LENGTH_OFFSET = 4;

  /**
   * {@link SignedBeaconBlockSchema} 4 (MESSAGE variable length offset) + 96 (SIGNATURE fixed-size
   * length)
   */
  private static final int BEACON_BLOCK_OFFSET_IN_SIGNED_BEACON_BLOCK =
      BYTES_PER_LENGTH_OFFSET + SszSignatureSchema.INSTANCE.getSszFixedPartSize();

  /**
   * Extract the slot value from any {@link BeaconBlock}.
   *
   * <p>Slot is the first field and recorded directly because it's fixed length. So just read the
   * first UInt64 worth of bytes.
   *
   * @param bytes the SSZ bytes to extract a slot from
   */
  public static UInt64 extractBeaconBlockSlot(final Bytes bytes) {
    final int size = UINT64_SCHEMA.getSszFixedPartSize();
    final Bytes slotData = bytes.slice(0, size);
    return UINT64_SCHEMA.sszDeserialize(slotData).get();
  }

  /**
   * Extract the slot value from any {@link SignedBlockContainer}.
   *
   * <p>The slot is the first field but is inside the variable length beacon block so a 4 byte
   * offset to the start of the beacon block data is recorded. Use that prefix to get the {@link
   * BeaconBlock} data or in case SignedBlockContents[Milestone] the {@link SignedBeaconBlock} data
   * and then find the slot as for an unsigned block.
   *
   * @param bytes the SSZ bytes to extract a slot from
   */
  public static UInt64 extractSignedBlockContainerSlot(final Bytes bytes) {
    int blockDataOffset = SszType.sszBytesToLength(bytes.slice(0, BYTES_PER_LENGTH_OFFSET));
    if (blockDataOffset == BEACON_BLOCK_OFFSET_IN_SIGNED_BEACON_BLOCK) {
      return extractBeaconBlockSlot(bytes.slice(blockDataOffset));
    }
    // first field in SignedBlockContents points to the SignedBeaconBlock data
    blockDataOffset +=
        SszType.sszBytesToLength(bytes.slice(blockDataOffset, BYTES_PER_LENGTH_OFFSET));
    return extractBeaconBlockSlot(bytes.slice(blockDataOffset));
  }
}
