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

package tech.pegasys.teku.spec.logic.versions.phase0.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BlockProposalUtilPhase0 implements BlockProposalUtil {

  private final BlockProcessor blockProcessor;
  private final SchemaDefinitions schemaDefinitions;

  public BlockProposalUtilPhase0(
      final SchemaDefinitions schemaDefinitions, final BlockProcessor blockProcessor) {
    this.schemaDefinitions = schemaDefinitions;
    this.blockProcessor = blockProcessor;
  }

  @Override
  public int getProposerLookAheadEpochs() {
    return 0;
  }

  @Override
  public Bytes32 getBlockProposalDependentRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 previousTargetRoot,
      final Bytes32 currentTargetRoot,
      final UInt64 stateEpoch,
      final UInt64 dutyEpoch) {
    checkArgument(
        dutyEpoch.isGreaterThanOrEqualTo(stateEpoch),
        "Attempting to calculate dependent root for duty epoch %s that is before the updated state epoch %s",
        dutyEpoch,
        stateEpoch);
    return stateEpoch.equals(dutyEpoch) ? currentTargetRoot : headBlockRoot;
  }

  @Override
  public UInt64 getStateSlotForProposerDuties(
      final Spec spec, final UInt64 stateEpoch, final UInt64 dutiesEpoch) {
    return spec.computeStartSlotAtEpoch(dutiesEpoch);
  }

  @Override
  public SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      final UInt64 proposalSlot,
      final int proposerIndex,
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder,
      final BlockProductionPerformance blockProductionPerformance) {
    checkArgument(
        blockSlotState.getSlot().equals(proposalSlot),
        "Block slot state from incorrect slot. Expected %s but got %s",
        proposalSlot,
        blockSlotState.getSlot());

    // Create block body
    final SafeFuture<? extends BeaconBlockBody> beaconBlockBody = createBlockBody(bodyBuilder);

    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    final SafeFuture<BeaconBlock> newBlock =
        beaconBlockBody.thenApply(
            body -> {
              final BeaconBlockSchema beaconBlockSchema =
                  body.isBlinded()
                      ? schemaDefinitions.getBlindedBeaconBlockSchema()
                      : schemaDefinitions.getBeaconBlockSchema();
              return beaconBlockSchema.create(
                  proposalSlot,
                  UInt64.valueOf(proposerIndex),
                  parentBlockSigningRoot,
                  tmpStateRoot,
                  body);
            });

    return newBlock
        .thenApplyChecked(
            block -> {
              blockProductionPerformance.beaconBlockCreated();
              // Run state transition and set state root
              // Skip verifying signatures as all operations are coming from our own pools.

              final BeaconState newState =
                  blockProcessor.processUnsignedBlock(
                      blockSlotState,
                      block,
                      IndexedAttestationCache.NOOP,
                      BLSSignatureVerifier.NOOP,
                      Optional.empty());

              blockProductionPerformance.stateTransition();

              final Bytes32 stateRoot = newState.hashTreeRoot();

              blockProductionPerformance.stateHashing();
              final BeaconBlock newCompleteBlock = block.withStateRoot(stateRoot);

              return new BeaconBlockAndState(newCompleteBlock, newState);
            })
        .exceptionallyCompose(
            error -> {
              if (ExceptionUtil.hasCause(error, BlockProcessingException.class)) {
                return SafeFuture.failedFuture(new StateTransitionException(error));
              }
              return SafeFuture.failedFuture(error);
            });
  }

  private SafeFuture<? extends BeaconBlockBody> createBlockBody(
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder) {
    final BeaconBlockBodyBuilder builder = schemaDefinitions.createBeaconBlockBodyBuilder();
    return bodyBuilder.apply(builder).thenApply(__ -> builder.build());
  }
}
