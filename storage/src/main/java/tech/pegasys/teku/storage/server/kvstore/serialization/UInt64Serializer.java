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

import com.google.common.primitives.Longs;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class UInt64Serializer implements KvStoreSerializer<UInt64> {

  @Override
  public UInt64 deserialize(final byte[] data) {
    if (data.length != Long.BYTES) {
      throw new KvStoreDeserializationException("Expected 8 bytes for UInt64, got " + data.length);
    }
    return UInt64.fromLongBits(Longs.fromByteArray(data));
  }

  @Override
  public byte[] serialize(final UInt64 value) {
    return Longs.toByteArray(value.longValue());
  }

  /**
   * Assumes data is at least 8 bytes long and offset is within bounds.
   *
   * @param data The byte array containing the UInt64 data.
   * @param offset The offset to the UInt64 data.
   * @return The deserialized UInt64 value.
   */
  public static UInt64 deserialize(final byte[] data, final int offset) {
    return UInt64.fromLongBits(
        Longs.fromBytes(
            data[offset],
            data[offset + 1],
            data[offset + 2],
            data[offset + 3],
            data[offset + 4],
            data[offset + 5],
            data[offset + 6],
            data[offset + 7]));
  }
}
