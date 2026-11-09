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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;

public class SyncCommitteeAggregationDuty {

  private final Spec spec;
  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorLogger validatorLogger;
  private final Collection<ValidatorAndCommitteeIndices> assignments;
  private final SyncAggregatorSelectionProofProvider syncAggregatorSelectionProofProvider;

  public SyncCommitteeAggregationDuty(
      final Spec spec,
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorLogger validatorLogger,
      final Collection<ValidatorAndCommitteeIndices> assignments,
      final SyncAggregatorSelectionProofProvider syncAggregatorSelectionProofProvider) {
    this.spec = spec;
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorLogger = validatorLogger;
    this.assignments = assignments;
    this.syncAggregatorSelectionProofProvider = syncAggregatorSelectionProofProvider;
  }

  public SafeFuture<DutyResult> produceAggregates(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtils = spec.getSyncCommitteeUtil(slot);
    if (assignments.isEmpty() || maybeSyncCommitteeUtils.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtils.get();

    return forkProvider
        .getForkInfo(slot)
        .thenCompose(
            forkInfo ->
                performAggregationsForSlot(slot, beaconBlockRoot, syncCommitteeUtil, forkInfo))
        .thenCompose(this::sendAggregates)
        .exceptionally(
            error ->
                DutyResult.forError(
                    assignments.stream()
                        .map(assignment -> assignment.getValidator().getPublicKey())
                        .collect(Collectors.toSet()),
                    error));
  }

  private SafeFuture<List<AggregationResult>> performAggregationsForSlot(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo) {
    return syncAggregatorSelectionProofProvider
        .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
        .thenCompose(
            __ ->
                SafeFuture.collectAll(
                    assignments.stream()
                        .flatMap(
                            assignment ->
                                performAggregationsForValidator(
                                    slot,
                                    beaconBlockRoot,
                                    syncCommitteeUtil,
                                    forkInfo,
                                    assignment))));
  }

  private Stream<SafeFuture<AggregationResult>> performAggregationsForValidator(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo,
      final ValidatorAndCommitteeIndices assignment) {
    return syncCommitteeUtil
        .getSyncSubcommittees(assignment.getCommitteeIndices())
        .intStream()
        .mapToObj(
            subcommitteeIndex ->
                performAggregationIfRequired(
                    syncCommitteeUtil,
                    forkInfo,
                    assignment,
                    subcommitteeIndex,
                    slot,
                    beaconBlockRoot));
  }

  private SafeFuture<AggregationResult> performAggregationIfRequired(
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo,
      final ValidatorAndCommitteeIndices assignment,
      final int subcommitteeIndex,
      final UInt64 slot,
      final Bytes32 beaconBlockRoot) {

    final Validator validator = assignment.getValidator();
    return syncAggregatorSelectionProofProvider
        .getSelectionProofFuture(assignment.getValidatorIndex(), subcommitteeIndex)
        .orElse(
            SafeFuture.failedFuture(
                new RuntimeException(
                    "Can't find aggregation selection proof for validator = "
                        + assignment.getValidatorIndex()
                        + ", slot = "
                        + slot
                        + ", subcommittee index = "
                        + subcommitteeIndex)))
        .thenCompose(
            selectionProof -> {
              if (syncCommitteeUtil.isSyncCommitteeAggregator(selectionProof)) {
                return performAggregation(
                    syncCommitteeUtil,
                    forkInfo,
                    assignment,
                    subcommitteeIndex,
                    slot,
                    beaconBlockRoot,
                    selectionProof);
              } else {
                return SafeFuture.completedFuture(
                    new AggregationResult(validator.getPublicKey(), DutyResult.NOOP));
              }
            })
        .exceptionally(
            error ->
                new AggregationResult(
                    validator.getPublicKey(),
                    DutyResult.forError(validator.getPublicKey(), error)));
  }

  private SafeFuture<AggregationResult> performAggregation(
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo,
      final ValidatorAndCommitteeIndices assignment,
      final int subcommitteeIndex,
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final BLSSignature selectionProof) {
    final BLSPublicKey validatorPublicKey = assignment.getValidator().getPublicKey();
    return validatorApiChannel
        .createSyncCommitteeContribution(slot, subcommitteeIndex, beaconBlockRoot)
        .thenCompose(
            maybeContribution -> {
              if (maybeContribution.isEmpty()) {
                validatorLogger.syncCommitteeAggregationSkipped(slot);
                return SafeFuture.completedFuture(
                    new AggregationResult(validatorPublicKey, DutyResult.NOOP));
              }
              return signContribution(
                  syncCommitteeUtil,
                  forkInfo,
                  assignment,
                  selectionProof,
                  validatorPublicKey,
                  maybeContribution.get());
            });
  }

  private SafeFuture<DutyResult> sendAggregates(final List<AggregationResult> results) {
    final List<AggregationResult> producedAggregates =
        results.stream().filter(result -> result.signedContributionAndProof.isPresent()).toList();
    final DutyResult combinedFailures =
        combineResults(
            results.stream()
                .filter(result -> result.signedContributionAndProof.isEmpty())
                .toList());

    if (producedAggregates.isEmpty()) {
      return SafeFuture.completedFuture(combinedFailures);
    }

    return validatorApiChannel
        .sendSignedContributionAndProofs(
            producedAggregates.stream()
                .map(result -> result.signedContributionAndProof.orElseThrow())
                .toList())
        .thenApply(__ -> combineResults(producedAggregates).combine(combinedFailures))
        .exceptionally(
            error ->
                producedAggregates.stream()
                    .map(
                        aggregationResult ->
                            DutyResult.forError(aggregationResult.validatorPublicKey, error))
                    .reduce(DutyResult::combine)
                    .orElse(DutyResult.NOOP));
  }

  private DutyResult combineResults(final List<AggregationResult> results) {
    return results.stream()
        .map(result -> result.result)
        .reduce(DutyResult::combine)
        .orElse(DutyResult.NOOP);
  }

  private SafeFuture<AggregationResult> signContribution(
      final SyncCommitteeUtil syncCommitteeUtil,
      final ForkInfo forkInfo,
      final ValidatorAndCommitteeIndices assignment,
      final BLSSignature selectionProof,
      final BLSPublicKey validatorPublicKey,
      final SyncCommitteeContribution contribution) {
    final ContributionAndProof contributionAndProof =
        syncCommitteeUtil.createContributionAndProof(
            UInt64.valueOf(assignment.getValidatorIndex()), contribution, selectionProof);
    return assignment
        .getValidator()
        .getSigner()
        .signContributionAndProof(contributionAndProof, forkInfo)
        .thenApply(
            signature ->
                new AggregationResult(
                    validatorPublicKey,
                    syncCommitteeUtil.createSignedContributionAndProof(
                        contributionAndProof, signature)));
  }

  private static class AggregationResult {
    private final BLSPublicKey validatorPublicKey;
    private final Optional<SignedContributionAndProof> signedContributionAndProof;
    private final DutyResult result;

    private AggregationResult(final BLSPublicKey validatorPublicKey, final DutyResult result) {
      this.validatorPublicKey = validatorPublicKey;
      this.signedContributionAndProof = Optional.empty();
      this.result = result;
    }

    private AggregationResult(
        final BLSPublicKey validatorPublicKey,
        final SignedContributionAndProof signedContributionAndProof) {
      this.validatorPublicKey = validatorPublicKey;
      this.signedContributionAndProof = Optional.of(signedContributionAndProof);
      this.result =
          DutyResult.success(
              signedContributionAndProof.getMessage().getContribution().getBeaconBlockRoot());
    }
  }
}
