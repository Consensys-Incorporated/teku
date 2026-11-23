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

import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Keccak-256 for the two inputs of a VRF tree, without allocating: a leaf {@code keccak256(seed ‖
 * uint64_le(index))} (40 bytes) and a node {@code keccak256(left ‖ right)} (64 bytes). Both fit in
 * one Keccak block, so each is one permutation.
 *
 * <p>Values are held as four little-endian 64-bit lanes, which is how Keccak absorbs bytes: a seed
 * or node is lanes 0-3 of the input, and the leaf index is lane 4 as it is. Deriving a VRF tree
 * hashes about 2^32 times, so this avoids the per-hash allocations of a general-purpose digest.
 *
 * <p>An instance keeps scratch state and is not thread-safe; use one per thread.
 */
final class VrfKeccak {

  static final int LANES = 4;

  // Keccak-256 absorbs 136 bytes per block; the pad's final bit is the top bit of lane 16
  private static final int LAST_RATE_LANE = 16;
  private static final long LAST_PAD_BIT = 0x8000000000000000L;
  // The legacy Keccak pad byte (not SHA3's 0x06)
  private static final long FIRST_PAD_BYTE = 0x01L;

  private static final long[] ROUND_CONSTANTS = {
    0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L,
    0x000000000000808BL, 0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L,
    0x000000000000008AL, 0x0000000000000088L, 0x0000000080008009L, 0x000000008000000AL,
    0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L, 0x8000000000008003L,
    0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
    0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
  };

  private final long[] state = new long[25];

  /** Hashes a leaf: {@code keccak256(seed ‖ uint64_le(index))} into {@code out[outOffset..+4]}. */
  void hashLeaf(final long[] seed, final long index, final long[] out, final int outOffset) {
    final long[] a = state;
    Arrays.fill(a, 0L);
    a[0] = seed[0];
    a[1] = seed[1];
    a[2] = seed[2];
    a[3] = seed[3];
    a[4] = index;
    a[5] = FIRST_PAD_BYTE;
    a[LAST_RATE_LANE] = LAST_PAD_BIT;
    permute(a);
    copyOut(a, out, outOffset);
  }

  /**
   * Hashes a node: {@code keccak256(left ‖ right)}, reading {@code nodes[leftOffset..+4]} and
   * {@code nodes[rightOffset..+4]}, into {@code out[outOffset..+4]}. {@code out} may be {@code
   * nodes}.
   */
  void hashPair(
      final long[] nodes,
      final int leftOffset,
      final int rightOffset,
      final long[] out,
      final int outOffset) {
    final long[] a = state;
    Arrays.fill(a, 0L);
    a[0] = nodes[leftOffset];
    a[1] = nodes[leftOffset + 1];
    a[2] = nodes[leftOffset + 2];
    a[3] = nodes[leftOffset + 3];
    a[4] = nodes[rightOffset];
    a[5] = nodes[rightOffset + 1];
    a[6] = nodes[rightOffset + 2];
    a[7] = nodes[rightOffset + 3];
    a[8] = FIRST_PAD_BYTE;
    a[LAST_RATE_LANE] = LAST_PAD_BIT;
    permute(a);
    copyOut(a, out, outOffset);
  }

  /** The four little-endian lanes of a 32-byte value. */
  static long[] toLanes(final Bytes32 value) {
    final long[] lanes = new long[LANES];
    for (int i = 0; i < LANES; i++) {
      lanes[i] = Long.reverseBytes(value.getLong(i * Long.BYTES));
    }
    return lanes;
  }

  /** The 32-byte value of {@code lanes[offset..+4]}. */
  static Bytes32 fromLanes(final long[] lanes, final int offset) {
    final byte[] bytes = new byte[Bytes32.SIZE];
    for (int i = 0; i < LANES; i++) {
      final long lane = lanes[offset + i];
      for (int b = 0; b < Long.BYTES; b++) {
        bytes[i * Long.BYTES + b] = (byte) (lane >>> (8 * b));
      }
    }
    return Bytes32.wrap(bytes);
  }

  private static void copyOut(final long[] a, final long[] out, final int outOffset) {
    out[outOffset] = a[0];
    out[outOffset + 1] = a[1];
    out[outOffset + 2] = a[2];
    out[outOffset + 3] = a[3];
  }

  /** Keccak-f[1600]; lane {@code x + 5y} of the state is {@code a[x + 5 * y]}. */
  private static void permute(final long[] a) {
    long a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3], a04 = a[4];
    long a05 = a[5], a06 = a[6], a07 = a[7], a08 = a[8], a09 = a[9];
    long a10 = a[10], a11 = a[11], a12 = a[12], a13 = a[13], a14 = a[14];
    long a15 = a[15], a16 = a[16], a17 = a[17], a18 = a[18], a19 = a[19];
    long a20 = a[20], a21 = a[21], a22 = a[22], a23 = a[23], a24 = a[24];

    for (int round = 0; round < 24; round++) {
      // theta
      long c0 = a00 ^ a05 ^ a10 ^ a15 ^ a20;
      long c1 = a01 ^ a06 ^ a11 ^ a16 ^ a21;
      final long c2 = a02 ^ a07 ^ a12 ^ a17 ^ a22;
      final long c3 = a03 ^ a08 ^ a13 ^ a18 ^ a23;
      final long c4 = a04 ^ a09 ^ a14 ^ a19 ^ a24;

      final long d1 = (c1 << 1 | c1 >>> 63) ^ c4;
      final long d2 = (c2 << 1 | c2 >>> 63) ^ c0;
      final long d3 = (c3 << 1 | c3 >>> 63) ^ c1;
      final long d4 = (c4 << 1 | c4 >>> 63) ^ c2;
      final long d0 = (c0 << 1 | c0 >>> 63) ^ c3;

      a00 ^= d1;
      a05 ^= d1;
      a10 ^= d1;
      a15 ^= d1;
      a20 ^= d1;
      a01 ^= d2;
      a06 ^= d2;
      a11 ^= d2;
      a16 ^= d2;
      a21 ^= d2;
      a02 ^= d3;
      a07 ^= d3;
      a12 ^= d3;
      a17 ^= d3;
      a22 ^= d3;
      a03 ^= d4;
      a08 ^= d4;
      a13 ^= d4;
      a18 ^= d4;
      a23 ^= d4;
      a04 ^= d0;
      a09 ^= d0;
      a14 ^= d0;
      a19 ^= d0;
      a24 ^= d0;

      // rho and pi
      c1 = a01 << 1 | a01 >>> 63;
      a01 = a06 << 44 | a06 >>> 20;
      a06 = a09 << 20 | a09 >>> 44;
      a09 = a22 << 61 | a22 >>> 3;
      a22 = a14 << 39 | a14 >>> 25;
      a14 = a20 << 18 | a20 >>> 46;
      a20 = a02 << 62 | a02 >>> 2;
      a02 = a12 << 43 | a12 >>> 21;
      a12 = a13 << 25 | a13 >>> 39;
      a13 = a19 << 8 | a19 >>> 56;
      a19 = a23 << 56 | a23 >>> 8;
      a23 = a15 << 41 | a15 >>> 23;
      a15 = a04 << 27 | a04 >>> 37;
      a04 = a24 << 14 | a24 >>> 50;
      a24 = a21 << 2 | a21 >>> 62;
      a21 = a08 << 55 | a08 >>> 9;
      a08 = a16 << 45 | a16 >>> 19;
      a16 = a05 << 36 | a05 >>> 28;
      a05 = a03 << 28 | a03 >>> 36;
      a03 = a18 << 21 | a18 >>> 43;
      a18 = a17 << 15 | a17 >>> 49;
      a17 = a11 << 10 | a11 >>> 54;
      a11 = a07 << 6 | a07 >>> 58;
      a07 = a10 << 3 | a10 >>> 61;
      a10 = c1;

      // chi
      c0 = a00 ^ (~a01 & a02);
      c1 = a01 ^ (~a02 & a03);
      a02 ^= ~a03 & a04;
      a03 ^= ~a04 & a00;
      a04 ^= ~a00 & a01;
      a00 = c0;
      a01 = c1;

      c0 = a05 ^ (~a06 & a07);
      c1 = a06 ^ (~a07 & a08);
      a07 ^= ~a08 & a09;
      a08 ^= ~a09 & a05;
      a09 ^= ~a05 & a06;
      a05 = c0;
      a06 = c1;

      c0 = a10 ^ (~a11 & a12);
      c1 = a11 ^ (~a12 & a13);
      a12 ^= ~a13 & a14;
      a13 ^= ~a14 & a10;
      a14 ^= ~a10 & a11;
      a10 = c0;
      a11 = c1;

      c0 = a15 ^ (~a16 & a17);
      c1 = a16 ^ (~a17 & a18);
      a17 ^= ~a18 & a19;
      a18 ^= ~a19 & a15;
      a19 ^= ~a15 & a16;
      a15 = c0;
      a16 = c1;

      c0 = a20 ^ (~a21 & a22);
      c1 = a21 ^ (~a22 & a23);
      a22 ^= ~a23 & a24;
      a23 ^= ~a24 & a20;
      a24 ^= ~a20 & a21;
      a20 = c0;
      a21 = c1;

      // iota
      a00 ^= ROUND_CONSTANTS[round];
    }

    a[0] = a00;
    a[1] = a01;
    a[2] = a02;
    a[3] = a03;
    a[4] = a04;
    a[5] = a05;
    a[6] = a06;
    a[7] = a07;
    a[8] = a08;
    a[9] = a09;
    a[10] = a10;
    a[11] = a11;
    a[12] = a12;
    a[13] = a13;
    a[14] = a14;
    a[15] = a15;
    a[16] = a16;
    a[17] = a17;
    a[18] = a18;
    a[19] = a19;
    a[20] = a20;
    a[21] = a21;
    a[22] = a22;
    a[23] = a23;
    a[24] = a24;
  }
}
