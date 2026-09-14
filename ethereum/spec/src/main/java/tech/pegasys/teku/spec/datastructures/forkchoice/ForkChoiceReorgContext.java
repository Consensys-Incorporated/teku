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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;

/** Read-only runtime context for proposer reorg evaluation. */
public interface ForkChoiceReorgContext {

  ReadOnlyStore getStore();

  Optional<BlockTimeliness> getBlockTimeliness(Bytes32 root);

  default boolean isProposerEquivocation(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    return false;
  }

  default boolean isPtcTimelyProposerEquivocation(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    return false;
  }

  boolean isValidatorConnected(int validatorIndex, UInt64 slot);

  BeaconState processSlots(BeaconState state, UInt64 slot)
      throws SlotProcessingException, EpochProcessingException;
}
