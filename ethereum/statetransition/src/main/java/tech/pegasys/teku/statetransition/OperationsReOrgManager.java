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

package tech.pegasys.teku.statetransition;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.RecentChainData;

public class OperationsReOrgManager implements ChainHeadChannel, LateBlockReorgPreparationHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final OperationPool<SignedVoluntaryExit> exitPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final AttestationManager attestationManager;
  private final AggregatingAttestationPool attestationPool;
  private final MappedOperationPool<SignedBlsToExecutionChange> blsToExecutionOperationPool;
  private final RecentChainData recentChainData;

  public OperationsReOrgManager(
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<SignedVoluntaryExit> exitPool,
      final AggregatingAttestationPool attestationPool,
      final AttestationManager attestationManager,
      final MappedOperationPool<SignedBlsToExecutionChange> blsToExecutionOperationPool,
      final RecentChainData recentChainData) {
    this.exitPool = exitPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.attestationManager = attestationManager;
    this.attestationPool = attestationPool;
    this.blsToExecutionOperationPool = blsToExecutionOperationPool;
    this.recentChainData = recentChainData;
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    // Payload reorgs keep every block canonical: nothing to re-queue or remove
    optionalReorgContext
        .filter(ReorgContext::isBlockReorg)
        .ifPresent(
            reorgContext -> {
              final NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots =
                  recentChainData.getAncestorsOnFork(
                      reorgContext.commonAncestorSlot(), reorgContext.oldBestBlockRoot());
              final NavigableMap<UInt64, Bytes32> nowCanonicalBlockRoots =
                  recentChainData.getAncestorsOnFork(
                      reorgContext.commonAncestorSlot(), bestBlockRoot);

              if (!notCanonicalBlockRoots.isEmpty()) {
                attestationPool.onReorg(reorgContext.commonAncestorSlot());
              }
              processNonCanonicalBlockOperations(notCanonicalBlockRoots.values())
                  .alwaysRun(() -> processCanonicalBlockOperations(nowCanonicalBlockRoots.values()))
                  .finishError(LOG);
            });
  }

  @Override
  public SafeFuture<Void> onLateBlockReorgPreparation(
      final UInt64 commonAncestorSlot, final Bytes32 lateBlockRoot) {
    final NavigableMap<UInt64, Bytes32> notCanonicalBlockRoots =
        recentChainData.getAncestorsOnFork(commonAncestorSlot, lateBlockRoot);

    if (notCanonicalBlockRoots.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    attestationPool.onReorg(commonAncestorSlot);
    return processNonCanonicalBlockOperations(notCanonicalBlockRoots.values());
  }

  private SafeFuture<Void> processNonCanonicalBlockOperations(
      final Collection<Bytes32> nonCanonicalBlockRoots) {
    return SafeFuture.allOf(
        nonCanonicalBlockRoots.stream()
            .map(
                root -> {
                  final SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
                      recentChainData.retrieveBlockByRoot(root);
                  return maybeBlockFuture
                      .thenCompose(
                          maybeBlock -> {
                            if (maybeBlock.isPresent()) {
                              final BeaconBlockBody blockBody = maybeBlock.get().getBody();
                              proposerSlashingPool.addAll(blockBody.getProposerSlashings());
                              attesterSlashingPool.addAll(blockBody.getAttesterSlashings());
                              exitPool.addAll(blockBody.getVoluntaryExits());
                              blockBody
                                  .getOptionalBlsToExecutionChanges()
                                  .ifPresent(blsToExecutionOperationPool::addAll);

                              return processNonCanonicalBlockAttestations(
                                  blockBody.getAttestations().stream(), root);
                            }
                            LOG.debug(
                                "Failed to re-queue operations for now non-canonical block: {}",
                                root);

                            return SafeFuture.completedFuture(null);
                          })
                      .exceptionally(
                          err -> {
                            LOG.warn(
                                "Failed to re-queue operations for now non-canonical block: {}",
                                root,
                                err);
                            return null;
                          });
                }));
  }

  private SafeFuture<Void> processNonCanonicalBlockAttestations(
      final Stream<Attestation> attestations, final Bytes32 blockRoot) {
    // Attestations need to get re-processed through AttestationManager
    // because we don't have access to the state with which they were
    // verified anymore and we need to make sure later on
    // that they're being included on the correct fork.
    return SafeFuture.allOf(
        attestations.map(
            attestation ->
                attestationManager
                    .onAttestation(
                        ValidatableAttestation.fromReorgedBlock(
                            recentChainData.getSpec(), attestation))
                    .thenAccept(
                        result ->
                            result.ifInvalid(
                                reason ->
                                    LOG.debug(
                                        "Rejected re-queued attestation from block: {} due to: {}",
                                        blockRoot,
                                        reason)))
                    .exceptionally(
                        err -> {
                          LOG.error(
                              "Failed to process re-queued attestation from block: {}",
                              blockRoot,
                              err);
                          return null;
                        })));
  }

  private void processCanonicalBlockOperations(final Collection<Bytes32> canonicalBlockRoots) {
    canonicalBlockRoots.forEach(
        root -> {
          final SafeFuture<Optional<BeaconBlock>> maybeBlockFuture =
              recentChainData.retrieveBlockByRoot(root);
          maybeBlockFuture
              .thenAccept(
                  maybeBlock ->
                      maybeBlock.ifPresentOrElse(
                          block -> {
                            final BeaconBlockBody blockBody = block.getBody();
                            proposerSlashingPool.removeAll(blockBody.getProposerSlashings());
                            attesterSlashingPool.removeAll(blockBody.getAttesterSlashings());
                            exitPool.removeAll(blockBody.getVoluntaryExits());
                            attestationPool.onAttestationsIncludedInBlock(
                                block.getSlot(), blockBody.getAttestations());
                            blockBody
                                .getOptionalBlsToExecutionChanges()
                                .ifPresent(blsToExecutionOperationPool::removeAll);
                          },
                          () ->
                              LOG.debug(
                                  "Failed to remove operations from pools for now canonical block: {}",
                                  root)))
              .finish(
                  err ->
                      LOG.warn(
                          "Failed to remove operations from pools for now canonical block: {}",
                          root,
                          err));
        });
  }
}
