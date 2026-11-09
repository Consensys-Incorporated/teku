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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ObolDvtSyncAggregatorSelectionProofProvider
    extends SyncAggregatorSelectionProofProvider {
  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;

  public ObolDvtSyncAggregatorSelectionProofProvider(
      final ValidatorApiChannel validatorApiChannel) {
    this.validatorApiChannel = validatorApiChannel;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public SafeFuture<Void> prepareSelectionProofForSlot(
      final UInt64 slot,
      final Collection<ValidatorAndCommitteeIndices> assignments,
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo) {

    final Set<SafeFuture<SyncCommitteeSelectionProof>> partialSelectionProofFutures =
        new HashSet<>();

    assignments.forEach(
        assignment -> {
          final IntStream syncSubCommitteeIndices =
              syncCommitteeUtil.getSyncSubcommittees(assignment.getCommitteeIndices()).intStream();

          syncSubCommitteeIndices.forEach(
              subcommitteeIndex -> {
                selectionProofFutures.put(
                    Pair.of(assignment.getValidatorIndex(), subcommitteeIndex), new SafeFuture<>());

                final SafeFuture<SyncCommitteeSelectionProof> partialProofFuture =
                    createAndSignSelectionData(
                            slot, syncCommitteeUtil, forkInfo, assignment, subcommitteeIndex)
                        .thenApply(
                            partialProof ->
                                mapToSelectionProofRequest(
                                    partialProof,
                                    slot,
                                    assignment.getValidatorIndex(),
                                    subcommitteeIndex));

                partialSelectionProofFutures.add(partialProofFuture);
              });
        });

    return SafeFuture.allOfFailFast(partialSelectionProofFutures.toArray(new SafeFuture[0]))
        .thenCompose(
            __ -> {
              final List<SyncCommitteeSelectionProof> requests =
                  partialSelectionProofFutures.stream().map(SafeFuture::getImmediately).toList();

              return validatorApiChannel
                  .getSyncCommitteeSelectionProof(requests)
                  .thenCompose(
                      combinedSelectionProofs ->
                          combinedSelectionProofs
                              .map(
                                  response ->
                                      processCombinedSelectionProofsResponse(response, slot))
                              .orElseGet(
                                  () -> {
                                    LOG.error(
                                        "Error requesting sync committee selection proofs for slot {} - Empty "
                                            + "response from Obol DVT",
                                        slot);
                                    return SafeFuture.failedFuture(
                                        new ObolDvtIntegrationException(
                                            "Empty response from Obol DVT integration"));
                                  }));
            });
  }

  private SafeFuture<Void> processCombinedSelectionProofsResponse(
      final List<SyncCommitteeSelectionProof> combinedSelectionProofsResponse, final UInt64 slot) {
    selectionProofFutures.forEach(
        (key, selectionProofFuture) ->
            combinedSelectionProofsResponse.stream()
                .filter(
                    syncCommitteeSelectionProof ->
                        syncCommitteeSelectionProof.getValidatorIndex() == key.getLeft()
                            && syncCommitteeSelectionProof.getSubcommitteeIndex() == key.getRight())
                .findFirst()
                .ifPresentOrElse(
                    syncCommitteeSelectionProof ->
                        selectionProofFuture.complete(
                            syncCommitteeSelectionProof.getSelectionProofSignature()),
                    () ->
                        selectionProofFuture.completeExceptionally(
                            new ObolDvtIntegrationException(
                                "Missing selection proof for validator_index = "
                                    + key.getLeft()
                                    + ",  subcommittee_index = "
                                    + key.getRight()
                                    + ", slot = "
                                    + slot))));

    return SafeFuture.COMPLETE;
  }

  private SyncCommitteeSelectionProof mapToSelectionProofRequest(
      final BLSSignature partialProof,
      final UInt64 slot,
      final int validatorIndex,
      final int subcommitteeIndex) {
    return new SyncCommitteeSelectionProof.Builder()
        .slot(slot)
        .validatorIndex(validatorIndex)
        .subcommitteeIndex(subcommitteeIndex)
        .selectionProof(partialProof.toBytesCompressed().toHexString())
        .build();
  }
}
