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

package tech.pegasys.teku.spec.constants;

import static java.nio.charset.StandardCharsets.US_ASCII;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Constants of the PQ fork (consensus-specs {@code pq} beacon-chain.md). */
public final class PqConstants {

  private PqConstants() {}

  public static final int AGGREGATORS_PER_COMMITTEE = 4;

  // XMSS leaves: reserved leaves, then one per attestation target epoch, then builder bids, then
  // XMSS_LEAVES_PER_SLOT per slot for the other message types
  public static final UInt64 XMSS_RESERVED_LEAVES = UInt64.valueOf(1L << 15);
  public static final UInt64 XMSS_ATTESTATION_LEAVES = UInt64.valueOf(1L << 29);
  public static final UInt64 XMSS_BUILDER_BID_LEAVES = UInt64.valueOf(1L << 30);
  public static final UInt64 XMSS_LEAVES_PER_SLOT = UInt64.valueOf(8);

  public static final UInt64 LEAF_OFFSET_BEACON_PROPOSER = UInt64.valueOf(0);
  public static final UInt64 LEAF_OFFSET_AGGREGATE_AND_PROOF = UInt64.valueOf(1);
  public static final UInt64 LEAF_OFFSET_PTC_ATTESTER = UInt64.valueOf(2);
  public static final UInt64 LEAF_OFFSET_PROPOSER_PREFERENCES = UInt64.valueOf(3);
  public static final UInt64 LEAF_OFFSET_EXECUTION_PAYLOAD_ENVELOPE = UInt64.valueOf(4);

  public static final int VRF_TREE_DEPTH = 31;

  /** VRF_LEAF_DOMAIN: a VRF tree's leaf commits to its secret under this tag (compute_vrf_leaf). */
  public static final Bytes VRF_LEAF_DOMAIN = Bytes.wrap("VRF_LEAF".getBytes(US_ASCII));

  /** VRF_SECRET_DOMAIN: a validator derives its VRF secrets under this tag (compute_vrf_secret). */
  public static final Bytes VRF_SECRET_DOMAIN = Bytes.wrap("VRF_SECRET".getBytes(US_ASCII));

  /** A post-upgrade request names 16 zero bytes followed by an XMSS public key. */
  public static final Bytes XMSS_DEPOSIT_PREFIX = Bytes.wrap(new byte[16]);

  public static final Bytes LEGACY_XMSS_PUBKEY_DOMAIN = Bytes.of(0x00);
  public static final Bytes LEGACY_VRF_ROOT_DOMAIN = Bytes.of(0x01);
}
