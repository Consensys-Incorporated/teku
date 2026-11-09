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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface BlockProposalUtil {
  int getProposerLookAheadEpochs();

  Bytes32 getBlockProposalDependentRoot(
      Bytes32 headBlockRoot,
      Bytes32 previousTargetRoot,
      Bytes32 currentTargetRoot,
      UInt64 stateEpoch,
      UInt64 dutyEpoch);

  SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      UInt64 proposalSlot,
      int proposerIndex,
      BeaconState blockSlotState,
      Bytes32 parentBlockSigningRoot,
      Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder,
      BlockProductionPerformance blockProductionPerformance);

  UInt64 getStateSlotForProposerDuties(Spec spec, UInt64 stateEpoch, UInt64 dutiesEpoch);
}
