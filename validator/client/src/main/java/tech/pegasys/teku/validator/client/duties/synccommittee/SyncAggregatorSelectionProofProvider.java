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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.client.Validator;

public class SyncAggregatorSelectionProofProvider {

  protected final Map<Pair<Integer, Integer>, SafeFuture<BLSSignature>> selectionProofFutures =
      new ConcurrentHashMap<>();

  public SyncAggregatorSelectionProofProvider() {}

  public SafeFuture<Void> prepareSelectionProofForSlot(
      final UInt64 slot,
      final Collection<ValidatorAndCommitteeIndices> assignments,
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo) {
    assignments.forEach(
        assignment -> {
          final IntStream syncSubCommitteeIndices =
              syncCommitteeUtil.getSyncSubcommittees(assignment.getCommitteeIndices()).intStream();

          syncSubCommitteeIndices.forEach(
              subcommitteeIndex -> {
                final SafeFuture<BLSSignature> signedProofFuture =
                    createAndSignSelectionData(
                        slot, syncCommitteeUtil, forkInfo, assignment, subcommitteeIndex);

                selectionProofFutures.put(
                    Pair.of(assignment.getValidatorIndex(), subcommitteeIndex), signedProofFuture);
              });
        });

    return SafeFuture.COMPLETE;
  }

  protected SafeFuture<BLSSignature> createAndSignSelectionData(
      final UInt64 slot,
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo,
      final ValidatorAndCommitteeIndices assignment,
      final int subcommitteeIndex) {
    final SyncAggregatorSelectionData selectionData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(
            slot, UInt64.valueOf(subcommitteeIndex));

    final Validator validator = assignment.getValidator();
    return validator.getSigner().signSyncCommitteeSelectionProof(selectionData, forkInfo);
  }

  public Optional<SafeFuture<BLSSignature>> getSelectionProofFuture(
      final int validatorIndex, final int subcommitteeIndex) {
    return Optional.ofNullable(
        selectionProofFutures.get(Pair.of(validatorIndex, subcommitteeIndex)));
  }
}
