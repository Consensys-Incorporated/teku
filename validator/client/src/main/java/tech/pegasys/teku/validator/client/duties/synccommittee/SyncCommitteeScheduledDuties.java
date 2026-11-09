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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

public class SyncCommitteeScheduledDuties implements ScheduledDuties {
  private static final Logger LOG = LogManager.getLogger();
  private final Collection<ValidatorAndCommitteeIndices> assignments;
  private final UInt64 lastEpochInCommitteePeriod;

  private final ChainHeadTracker chainHeadTracker;
  private final ValidatorApiChannel validatorApiChannel;
  private final SyncCommitteeProductionDuty productionDuty;
  private final SyncCommitteeAggregationDuty aggregationDuty;
  private final ValidatorLogger validatorLogger;

  private volatile Optional<Bytes32> lastSignatureBlockRoot = Optional.empty();
  private volatile Optional<UInt64> lastSignatureSlot = Optional.empty();

  @VisibleForTesting
  SyncCommitteeScheduledDuties(
      final SyncCommitteeProductionDuty productionDuty,
      final SyncCommitteeAggregationDuty aggregationDuty,
      final ChainHeadTracker chainHeadTracker,
      final ValidatorApiChannel validatorApiChannel,
      final Collection<ValidatorAndCommitteeIndices> assignments,
      final ValidatorLogger validatorLogger,
      final UInt64 lastEpochInCommitteePeriod) {
    this.aggregationDuty = aggregationDuty;
    this.chainHeadTracker = chainHeadTracker;
    this.validatorApiChannel = validatorApiChannel;
    this.productionDuty = productionDuty;
    this.assignments = assignments;
    this.validatorLogger = validatorLogger;
    this.lastEpochInCommitteePeriod = lastEpochInCommitteePeriod;
  }

  public static SyncCommitteeScheduledDuties.Builder builder() {
    return new Builder();
  }

  @Override
  public boolean requiresRecalculation(final Bytes32 newDependentRoot) {
    return false;
  }

  @Override
  public SafeFuture<DutyResult> performProductionDuty(final UInt64 slot) {
    LOG.trace(
        "Performing sync committee duties at slot {}, {} assignments", slot, assignments.size());
    if (assignments.isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    try {
      lastSignatureBlockRoot = chainHeadTracker.getCurrentChainHead(slot);
    } catch (final ChainHeadBeyondSlotException | ChainHeadTooOldException ex) {
      return chainHeadSlotCheckFailure(ex);
    }
    lastSignatureSlot = Optional.of(slot);
    return lastSignatureBlockRoot
        .map(blockRoot -> productionDuty.produceMessages(slot, blockRoot))
        .orElse(SafeFuture.completedFuture(DutyResult.NODE_SYNCING));
  }

  private SafeFuture<DutyResult> chainHeadSlotCheckFailure(final RuntimeException ex) {
    return SafeFuture.completedFuture(DutyResult.forError(getAllValidatorKeys(), ex));
  }

  private Set<BLSPublicKey> getAllValidatorKeys() {
    return assignments.stream()
        .map(assignment -> assignment.getValidator().getPublicKey())
        .collect(Collectors.toSet());
  }

  @Override
  public String getProductionType() {
    return "sync_signature";
  }

  @Override
  public SafeFuture<DutyResult> performAggregationDuty(final UInt64 slot) {
    if (getAllValidatorKeys().isEmpty()) {
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    if (lastSignatureSlot.isEmpty()
        || lastSignatureBlockRoot.isEmpty()
        || !lastSignatureSlot.get().equals(slot)) {
      validatorLogger.syncCommitteeAggregationSkipped(slot);
      return SafeFuture.completedFuture(DutyResult.NOOP);
    }
    return aggregationDuty.produceAggregates(slot, lastSignatureBlockRoot.get());
  }

  @Override
  public String getAggregationType() {
    return "sync_contribution";
  }

  @Override
  public int countDuties() {
    return assignments.size();
  }

  public void subscribeToSubnets() {
    if (assignments.isEmpty()) {
      return;
    }
    final Set<SyncCommitteeSubnetSubscription> subscriptions =
        assignments.stream()
            .map(
                assignment ->
                    new SyncCommitteeSubnetSubscription(
                        assignment.getValidatorIndex(),
                        assignment.getCommitteeIndices(),
                        lastEpochInCommitteePeriod.increment()))
            .collect(Collectors.toSet());
    validatorApiChannel
        .subscribeToSyncCommitteeSubnets(subscriptions)
        .finish(error -> LOG.error("Failed to subscribe to sync committee subnets.", error));
  }

  public static class Builder {

    private ValidatorLogger validatorLogger = ValidatorLogger.VALIDATOR_LOGGER;
    private ValidatorApiChannel validatorApiChannel;
    private ForkProvider forkProvider;
    private final Int2ObjectMap<ValidatorAndCommitteeIndices> assignments =
        new Int2ObjectOpenHashMap<>();
    private Spec spec;
    private ChainHeadTracker chainHeadTracker;
    private UInt64 lastEpochInCommitteePeriod;
    private boolean useDvtEndpoint = false;

    public Builder validatorLogger(final ValidatorLogger validatorLogger) {
      this.validatorLogger = validatorLogger;
      return this;
    }

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder validatorApiChannel(final ValidatorApiChannel validatorApiChannel) {
      this.validatorApiChannel = validatorApiChannel;
      return this;
    }

    public Builder chainHeadTracker(final ChainHeadTracker chainHeadTracker) {
      this.chainHeadTracker = chainHeadTracker;
      return this;
    }

    public Builder forkProvider(final ForkProvider forkProvider) {
      this.forkProvider = forkProvider;
      return this;
    }

    public Builder committeeAssignment(
        final Validator validator, final int validatorIndex, final int committeeIndex) {
      assignments
          .computeIfAbsent(
              validatorIndex, __ -> new ValidatorAndCommitteeIndices(validator, validatorIndex))
          .addCommitteeIndex(committeeIndex);
      return this;
    }

    public Builder committeeAssignments(
        final Validator validator, final int validatorIndex, final IntCollection committeeIndices) {
      assignments
          .computeIfAbsent(
              validatorIndex, __ -> new ValidatorAndCommitteeIndices(validator, validatorIndex))
          .addCommitteeIndices(committeeIndices);
      return this;
    }

    public Builder lastEpochInCommitteePeriod(final UInt64 lastEpochInCommitteePeriod) {
      this.lastEpochInCommitteePeriod = lastEpochInCommitteePeriod;
      return this;
    }

    public Builder useDvtEndpoint(final boolean useDvtEndpoint) {
      this.useDvtEndpoint = useDvtEndpoint;
      return this;
    }

    public SyncCommitteeScheduledDuties build() {
      checkNotNull(spec, "Must provide a spec");
      checkNotNull(validatorApiChannel, "Must provide a validatorApiChannel");
      checkNotNull(chainHeadTracker, "Must provide a chainHeadTracker");
      checkNotNull(forkProvider, "Must provide a forkProvider");
      checkNotNull(lastEpochInCommitteePeriod, "Must provide lastEpochInCommitteePeriod");
      final SyncCommitteeProductionDuty productionDuty =
          new SyncCommitteeProductionDuty(
              spec, forkProvider, validatorApiChannel, assignments.values());
      final SyncCommitteeAggregationDuty aggregationDuty =
          new SyncCommitteeAggregationDuty(
              spec,
              forkProvider,
              validatorApiChannel,
              validatorLogger,
              assignments.values(),
              createSyncAggregatorSelectionProofProvider());
      return new SyncCommitteeScheduledDuties(
          productionDuty,
          aggregationDuty,
          chainHeadTracker,
          validatorApiChannel,
          assignments.values(),
          validatorLogger,
          lastEpochInCommitteePeriod);
    }

    private SyncAggregatorSelectionProofProvider createSyncAggregatorSelectionProofProvider() {
      return useDvtEndpoint
          ? new ObolDvtSyncAggregatorSelectionProofProvider(validatorApiChannel)
          : new SyncAggregatorSelectionProofProvider();
    }
  }
}
