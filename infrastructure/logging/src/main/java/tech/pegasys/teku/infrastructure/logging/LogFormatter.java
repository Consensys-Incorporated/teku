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

package tech.pegasys.teku.infrastructure.logging;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class LogFormatter {
  public static String formatHashRoot(final Bytes32 root) {
    return root.toUnprefixedHexString();
  }

  public static String formatAbbreviatedHashRoot(final Bytes32 root) {
    final String hash = root.toUnprefixedHexString();
    return String.format("%s..%s", hash.substring(0, 6), hash.substring(hash.length() - 4));
  }

  public static String formatBlock(final UInt64 slot, final Bytes32 root) {
    return String.format("%s (%s)", formatHashRoot(root), slot);
  }

  public static String formatBlobSidecar(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final UInt64 index,
      final String blob,
      final String kzgCommitment,
      final String kzgProof) {
    return String.format(
        "BlobSidecar[block %s (%s), index %s, blob %s, commitment %s, proof %s]",
        formatAbbreviatedHashRoot(blockRoot), slot, index, blob, kzgCommitment, kzgProof);
  }

  public static String formatDataColumnSidecar(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final UInt64 index,
      final String blob,
      final int kzgProofsSize) {
    return String.format(
        "DataColumnSidecar[block %s (%s), index %s, 1st cell %s, proofs %s]",
        formatAbbreviatedHashRoot(blockRoot), slot, index, blob, kzgProofsSize);
  }

  public static String formatExecutionPayload(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 builderIndex,
      final Bytes32 blockHash) {
    return String.format(
        "%s (beacon block root: %s, builder index: %s, block hash: %s)",
        slot, formatHashRoot(beaconBlockRoot), builderIndex, formatHashRoot(blockHash));
  }
}
