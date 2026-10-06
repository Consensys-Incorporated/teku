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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.client.ChainHead;

public record BlockProductionContext(
    UInt64 proposalSlot,
    BeaconState blockSlotState,
    ForkChoiceNode parentForkChoiceNode,
    Bytes32 parentExecutionBlockHash,
    BLSSignature randaoReveal,
    Optional<Bytes32> graffiti,
    boolean includePayload,
    Optional<BuilderConfig> builderConfig,
    BlockProductionPerformance blockProductionPerformance) {

  public static BlockProductionContext create(
      final Spec spec,
      final UInt64 proposalSlot,
      final BeaconState blockSlotState,
      final ChainHead parentChainHead,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> graffiti,
      final boolean includePayload,
      final Optional<BuilderConfig> builderConfig,
      final BlockProductionPerformance blockProductionPerformance) {
    checkArgument(
        blockSlotState.getSlot().equals(proposalSlot),
        "Block slot state for slot %s but should be for slot %s",
        blockSlotState.getSlot(),
        proposalSlot);
    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, proposalSlot.decrement());
    checkArgument(
        parentRoot.equals(parentChainHead.getRoot()),
        "Block slot state parent root %s does not match selected production parent root %s",
        parentRoot,
        parentChainHead.getRoot());
    return new BlockProductionContext(
        proposalSlot,
        blockSlotState,
        parentChainHead.getForkChoiceNode(),
        parentChainHead.getExecutionBlockHash(),
        randaoReveal,
        graffiti,
        includePayload,
        builderConfig,
        blockProductionPerformance);
  }

  public Bytes32 parentRoot() {
    return parentForkChoiceNode.blockRoot();
  }

  public ForkChoicePayloadStatus parentPayloadStatus() {
    return parentForkChoiceNode.payloadStatus();
  }
}
