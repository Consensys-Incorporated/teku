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

package tech.pegasys.teku.statetransition.executionproofs;

import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.forkchoice.ExecutionProofsAvailabilityChecker;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ExecutionProofManagerImpl implements ExecutionProofManager {

  final ExecutionProofGossipValidator executionProofGossipValidator;

  private final Subscribers<ValidExecutionProofListener> receivedExecutionProofSubscribers =
      Subscribers.create(true);

  private final Map<Bytes32, Set<ExecutionProof>> validatedExecutionProofsByBlockRoot =
      new ConcurrentHashMap<>();
  private final Consumer<ExecutionProof> onCreatedProof;

  private static final Logger LOG = LogManager.getLogger();
  private final int attemptsToGetProof = 3;
  private final ExecutionProofGenerator executionProofGenerator;
  private final Duration proofGenerationDelay;
  private final AsyncRunner asyncRunner;
  private final boolean isProofGenerationEnabled;
  private final int minProofsRequired;

  public ExecutionProofManagerImpl(
      final ExecutionProofGossipValidator executionProofGossipValidator,
      final ExecutionProofGenerator executionProofGenerator,
      final Consumer<ExecutionProof> onCreatedProof,
      final boolean isProofGenerationEnabled,
      final int minProofsRequired,
      final Duration proofGenerationDelay,
      final AsyncRunner asyncRunner) {
    this.executionProofGossipValidator = executionProofGossipValidator;
    this.onCreatedProof = onCreatedProof;
    this.isProofGenerationEnabled = isProofGenerationEnabled;
    this.minProofsRequired = minProofsRequired;
    this.executionProofGenerator = executionProofGenerator;
    this.proofGenerationDelay = proofGenerationDelay;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public void onExecutionProofPublish(
      final ExecutionProof executionProof, final RemoteOrigin remoteOrigin) {
    LOG.trace("Published execution proof {}", executionProof);
  }

  @Override
  public SafeFuture<InternalValidationResult> onReceivedExecutionProofGossip(
      final ExecutionProof executionProof, final Optional<UInt64> arrivalTimestamp) {
    LOG.debug("Received execution proof for block {}", executionProof);
    return executionProofGossipValidator
        .validate(executionProof, executionProof.getSubnetId().get())
        .thenApply(
            result -> {
              if (result.isAccept()) {
                // TODO check if proof for same block and subnet already exists this could be a
                // different proof for same block and subnet
                // in this case do we want to replace a existing valid proof with a new one?
                LOG.debug("Adding execution proof for block {} to cache", executionProof);
                validatedExecutionProofsByBlockRoot
                    .computeIfAbsent(
                        executionProof.getBlockRoot().get(), k -> ConcurrentHashMap.newKeySet())
                    .add(executionProof);
                LOG.debug(
                    "Added execution proof to cache {}",
                    validatedExecutionProofsByBlockRoot.toString());
              } else {
                LOG.debug(
                    "Rejected execution proof for block {}: {}",
                    executionProof.getBlockRoot(),
                    result);
              }
              return result;
            });
  }

  @Override
  public void subscribeToValidExecutionProofs(
      final ValidExecutionProofListener executionProofListener) {
    receivedExecutionProofSubscribers.subscribe(executionProofListener);
  }

  @Override
  public AvailabilityChecker<ExecutionProof> createAvailabilityChecker(
      final SignedBeaconBlock block) {
    return new ExecutionProofsAvailabilityChecker(this, block);
  }

  @Override
  public SafeFuture<DataAndValidationResult<ExecutionProof>> validateBlockWithExecutionProofs(
      final SignedBeaconBlock block) {
    for (int attempt = 0; attempt < attemptsToGetProof; attempt++) {
      final DataAndValidationResult<ExecutionProof> result = checkForValidProofs(block);
      if (result.isValid()) {
        return SafeFuture.completedFuture(result);
      }
    }
    LOG.debug("Checking proofs for block {}", block.getRoot());

    return SafeFuture.completedFuture(DataAndValidationResult.notAvailable());
  }

  private DataAndValidationResult<ExecutionProof> checkForValidProofs(
      final SignedBeaconBlock block) {
    if (validatedExecutionProofsByBlockRoot.containsKey(block.getRoot())) {
      final List<ExecutionProof> proofs =
          validatedExecutionProofsByBlockRoot.get(block.getRoot()).stream().toList();
      LOG.debug(
          "Found {} previously validated proofs for block {}", proofs.size(), block.getRoot());
      if (proofs.size() >= minProofsRequired) {
        return DataAndValidationResult.validResult(proofs);
      } else {
        return DataAndValidationResult.invalidResult(proofs);
      }
    } else {
      return DataAndValidationResult.notAvailable();
    }
  }

  @Override
  public SafeFuture<Void> generateProofs(final SignedBlockContainer blockContainer) {

    if (!isProofGenerationEnabled) {
      return SafeFuture.completedFuture(null);
    }
    final Bytes32 blockRoot = blockContainer.getSignedBlock().getRoot();

    asyncRunner
        .runAsync(
            () -> {
              // Generate proofs for all subnets
              IntStream.range(0, (int) MAX_EXECUTION_PROOF_SUBNETS)
                  .forEach(
                      subnetIndex -> {
                        executionProofGenerator
                            .generateExecutionProof(
                                blockContainer, subnetIndex, proofGenerationDelay)
                            .finish(
                                proof -> {
                                  LOG.trace("Generated proof for subnet {}", proof.getSubnetId());
                                  validatedExecutionProofsByBlockRoot
                                      .computeIfAbsent(
                                          proof.getBlockRoot().get(),
                                          k -> ConcurrentHashMap.newKeySet())
                                      .add(proof);
                                  onCreatedProof.accept(proof);
                                },
                                error ->
                                    LOG.error(
                                        "Failed to generate proof for block {} subnet {}",
                                        blockRoot,
                                        subnetIndex,
                                        error));
                      });
            })
        .finish(
            () -> LOG.debug("Completed generating execution proofs for block {}", blockRoot),
            error ->
                LOG.error("Failed to generate execution proofs for block {}", blockRoot, error));

    return SafeFuture.completedFuture(null);
  }
}
