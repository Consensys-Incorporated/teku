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

package tech.pegasys.teku.validator.coordinator;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;

final class BlockProductionTestUtil {

  private BlockProductionTestUtil() {}

  static BlockProductionContext blockProductionContext(
      final Spec spec,
      final UInt64 proposalSlot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig,
      final BlockProductionPerformance blockProductionPerformance) {
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, proposalSlot.decrement());
    return blockProductionContext(
        parentRoot,
        blockSlotState,
        randaoReveal,
        graffiti,
        includePayload,
        builderConfig,
        blockProductionPerformance);
  }

  static BlockProductionContext blockProductionContext(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final Optional<UInt64> requestedBuilderBoostFactor,
      final BlockProductionPerformance blockProductionPerformance) {
    return blockProductionContext(
        parentRoot,
        blockSlotState,
        randaoReveal,
        graffiti,
        false,
        requestedBuilderBoostFactor.map(BuilderConfig::withBuilderBoostFactor),
        blockProductionPerformance);
  }

  static BlockProductionContext blockProductionContext(
      final Bytes32 parentRoot,
      final BeaconState blockSlotState,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig,
      final BlockProductionPerformance blockProductionPerformance) {
    return new BlockProductionContext(
        blockSlotState.getSlot(),
        blockSlotState,
        ForkChoiceNode.createBase(parentRoot),
        parentExecutionBlockHash(blockSlotState),
        randaoReveal,
        graffiti,
        includePayload,
        builderConfig,
        blockProductionPerformance);
  }

  private static Bytes32 parentExecutionBlockHash(final BeaconState state) {
    return state.toVersionGloas().map(BeaconStateGloas::getLatestBlockHash).orElse(Bytes32.ZERO);
  }
}
