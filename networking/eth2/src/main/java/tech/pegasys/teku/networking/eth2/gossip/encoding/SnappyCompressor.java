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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;

/**
 * Implements snappy compression using the "block" format.
 *
 * <p>This class centralizes Teku's gossip-specific validation and exception handling. Subclasses
 * only provide raw byte-array operations for a concrete snappy implementation.
 */
public abstract class SnappyCompressor {

  /**
   * Decompresses snappy block-encoded data and validates the declared uncompressed length before
   * allocating the output buffer.
   *
   * @param compressedData snappy block-encoded bytes
   * @param lengthBounds expected SSZ byte-size bounds for the decoded value
   * @param maxUncompressedLengthInBytes hard cap on decoded message size
   * @return uncompressed bytes
   * @throws DecodingException if the compressed data is invalid or decodes outside the expected
   *     bounds
   */
  public final Bytes uncompress(
      final Bytes compressedData,
      final SszLengthBounds lengthBounds,
      final long maxUncompressedLengthInBytes)
      throws DecodingException {
    try {
      final byte[] compressedBytes = compressedData.toArrayUnsafe();
      final int uncompressedLength = getUncompressedLength(compressedBytes);
      validateUncompressedLength(uncompressedLength, lengthBounds, maxUncompressedLengthInBytes);

      final Bytes uncompressedData = uncompress(compressedBytes, uncompressedLength);
      if (uncompressedData.size() != uncompressedLength) {
        throw new DecodingException(
            String.format(
                "Expected %d uncompressed bytes but got %d",
                uncompressedLength, uncompressedData.size()));
      }
      return uncompressedData;
    } catch (DecodingException e) {
      throw e;
    } catch (Exception e) {
      throw new DecodingException("Failed to uncompress", e);
    }
  }

  /**
   * Reads the uncompressed length from snappy block-encoded data.
   *
   * @param compressedData snappy block-encoded bytes
   * @return declared uncompressed byte length
   * @throws Exception if the compressed data is invalid
   */
  protected abstract int getUncompressedLength(byte[] compressedData) throws Exception;

  /**
   * Decompresses snappy block-encoded bytes.
   *
   * <p>The supplied {@code uncompressedLength} has already passed gossip size validation. The
   * returned value must contain exactly {@code uncompressedLength} bytes.
   *
   * @param compressedData snappy block-encoded bytes
   * @param uncompressedLength expected decoded byte length
   * @return uncompressed bytes
   * @throws Exception if the compressed data is invalid
   */
  protected abstract Bytes uncompress(byte[] compressedData, int uncompressedLength)
      throws Exception;

  /**
   * Compresses bytes using snappy block encoding.
   *
   * @param data uncompressed bytes
   * @return snappy block-encoded bytes
   */
  public final Bytes compress(final Bytes data) {
    try {
      return compress(data.toArrayUnsafe());
    } catch (Exception e) {
      throw new RuntimeException("Unable to compress data", e);
    }
  }

  /**
   * Compresses raw bytes using snappy block encoding.
   *
   * @param data uncompressed bytes
   * @return snappy block-encoded bytes
   * @throws Exception if compression fails
   */
  protected abstract Bytes compress(byte[] data) throws Exception;

  private static void validateUncompressedLength(
      final int uncompressedLength,
      final SszLengthBounds lengthBounds,
      final long maxUncompressedLengthInBytes)
      throws DecodingException {
    if (uncompressedLength > maxUncompressedLengthInBytes) {
      throw new DecodingException(
          String.format(
              "Uncompressed length %d exceeds max length in bytes of %s",
              uncompressedLength, maxUncompressedLengthInBytes));
    }

    if (!lengthBounds.isWithinBounds(uncompressedLength)) {
      throw new DecodingException(
          String.format(
              "Uncompressed length %d is not within expected bounds %s",
              uncompressedLength, lengthBounds));
    }
  }
}
