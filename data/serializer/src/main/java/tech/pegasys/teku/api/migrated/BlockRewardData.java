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

package tech.pegasys.teku.api.migrated;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Represents the block rewards in GWei and the block proposer index */
public record BlockRewardData(
    UInt64 proposerIndex,
    long attestations,
    long syncAggregate,
    long proposerSlashings,
    long attesterSlashings) {

  public static BlockRewardData fromInternal(
      final tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil.BlockRewardData
          internal) {
    return new BlockRewardData(
        internal.proposerIndex(),
        internal.attestations(),
        internal.syncAggregate(),
        internal.proposerSlashings(),
        internal.attesterSlashings());
  }

  public long getTotal() {
    return attestations + syncAggregate + proposerSlashings + attesterSlashings;
  }
}
