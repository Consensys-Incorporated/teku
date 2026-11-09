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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.BYTES32_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT256_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

/** Utility functions to extract data from the ssz bytes of execution payload envelopes. */
public class ExecutionPayloadEnvelopeInvariants {

  private static final int BYTES_PER_LENGTH_OFFSET = 4;
  private static final int BYTES_PER_LOGS_BLOOM = 256;

  private static final int EXECUTION_PAYLOAD_ENVELOPE_OFFSET_IN_SIGNED_ENVELOPE =
      BYTES_PER_LENGTH_OFFSET + SszSignatureSchema.INSTANCE.getSszFixedPartSize();

  // Fixed part of ExecutionPayloadEnvelope:
  // payload_offset(4) + execution_requests_offset(4) + builder_index(8) +
  // beacon_block_root(32) + parent_beacon_block_root(32)
  private static final int EXECUTION_PAYLOAD_ENVELOPE_FIXED_PART_SIZE =
      BYTES_PER_LENGTH_OFFSET
          + BYTES_PER_LENGTH_OFFSET
          + UINT64_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize();

  // Common fixed-part prefix shared by both ExecutionPayload and ExecutionPayloadHeader
  // up through block_hash (fields 0-12):
  // parent_hash(32) + fee_recipient(20) + state_root(32) + receipts_root(32) +
  // logs_bloom(256) + prev_randao(32) + block_number(8) + gas_limit(8) + gas_used(8) +
  // timestamp(8) + extra_data_offset(4) + base_fee_per_gas(32) + block_hash(32)
  private static final int COMMON_PAYLOAD_PREFIX_FIXED_SIZE =
      BYTES32_SCHEMA.getSszFixedPartSize()
          + Bytes20.SIZE
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + BYTES_PER_LOGS_BLOOM
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + BYTES_PER_LENGTH_OFFSET
          + UINT256_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize();

  // SLOT_NUMBER offset in ExecutionPayload fixed part:
  // after common prefix + transactions_offset(4) + withdrawals_offset(4) +
  // blob_gas_used(8) + excess_blob_gas(8) + block_access_list_offset(4)
  private static final int SLOT_NUMBER_OFFSET_IN_EXECUTION_PAYLOAD =
      COMMON_PAYLOAD_PREFIX_FIXED_SIZE
          + BYTES_PER_LENGTH_OFFSET
          + BYTES_PER_LENGTH_OFFSET
          + UINT64_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + BYTES_PER_LENGTH_OFFSET;

  // SLOT_NUMBER offset in ExecutionPayloadHeader fixed part:
  // after common prefix + transactions_root(32) + withdrawals_root(32) +
  // blob_gas_used(8) + excess_blob_gas(8) + block_access_list_root(32)
  // (header uses fixed Bytes32 roots instead of variable lists)
  private static final int SLOT_NUMBER_OFFSET_IN_EXECUTION_PAYLOAD_HEADER =
      COMMON_PAYLOAD_PREFIX_FIXED_SIZE
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + UINT64_SCHEMA.getSszFixedPartSize()
          + BYTES32_SCHEMA.getSszFixedPartSize();

  private ExecutionPayloadEnvelopeInvariants() {}

  public static UInt64 extractSignedExecutionPayloadEnvelopeSlot(final Bytes bytes) {
    return extractSlotFromSignedPayloadEnvelope(bytes, SLOT_NUMBER_OFFSET_IN_EXECUTION_PAYLOAD);
  }

  public static UInt64 extractSignedBlindedExecutionPayloadEnvelopeSlot(final Bytes bytes) {
    return extractSlotFromSignedPayloadEnvelope(
        bytes, SLOT_NUMBER_OFFSET_IN_EXECUTION_PAYLOAD_HEADER);
  }

  private static UInt64 extractSlotFromSignedPayloadEnvelope(
      final Bytes bytes, final int slotOffsetInPayload) {
    final int envelopeDataOffset =
        SszType.sszBytesToLength(bytes.slice(0, BYTES_PER_LENGTH_OFFSET));
    if (envelopeDataOffset != EXECUTION_PAYLOAD_ENVELOPE_OFFSET_IN_SIGNED_ENVELOPE) {
      throw new IllegalArgumentException("Unexpected signed execution payload envelope format");
    }
    final int payloadDataOffset =
        SszType.sszBytesToLength(bytes.slice(envelopeDataOffset, BYTES_PER_LENGTH_OFFSET));
    if (payloadDataOffset != EXECUTION_PAYLOAD_ENVELOPE_FIXED_PART_SIZE) {
      throw new IllegalArgumentException("Unexpected execution payload envelope format");
    }
    final Bytes slotData =
        bytes.slice(
            envelopeDataOffset + payloadDataOffset + slotOffsetInPayload,
            UINT64_SCHEMA.getSszFixedPartSize());
    return UINT64_SCHEMA.sszDeserialize(slotData).get();
  }
}
