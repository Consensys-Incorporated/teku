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

package tech.pegasys.teku.statetransition.util;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;

public final class ShufflingDependentRootUtil {

  private static final UInt64 ONE = UInt64.ONE;

  private ShufflingDependentRootUtil() {}

  public static Optional<Bytes32> getShufflingDependentRoot(
      final Spec spec,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 blockRoot,
      final UInt64 proposalSlot) {
    final UInt64 proposalEpoch = spec.computeEpochAtSlot(proposalSlot);
    final UInt64 minSeedLookahead =
        UInt64.valueOf(spec.getSpecConfig(proposalEpoch).getMinSeedLookahead());
    final UInt64 dependentSlot =
        proposalEpoch.isLessThanOrEqualTo(minSeedLookahead)
            ? UInt64.ZERO
            : spec.computeStartSlotAtEpoch(proposalEpoch.minus(minSeedLookahead)).minus(ONE);
    return forkChoiceStrategy.getAncestor(blockRoot, dependentSlot);
  }

  public static Optional<UInt64> getShufflingDependentSlotForEpoch(
      final Spec spec, final UInt64 proposalEpoch) {
    final UInt64 minSeedLookahead =
        UInt64.valueOf(spec.getSpecConfig(proposalEpoch).getMinSeedLookahead());
    if (proposalEpoch.isLessThanOrEqualTo(minSeedLookahead)) {
      return Optional.empty();
    }
    return Optional.of(
        spec.computeStartSlotAtEpoch(proposalEpoch.minus(minSeedLookahead)).minus(ONE));
  }
}
