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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;

public class BeaconStateBuilderGloas
    extends AbstractBeaconStateBuilder<
        BeaconStateGloas, MutableBeaconStateGloas, BeaconStateBuilderGloas> {
  private UInt64 nextWithdrawalIndex;
  private UInt64 nextWithdrawalValidatorIndex;

  private SszList<SszByte> previousEpochParticipation;
  private SszList<SszByte> currentEpochParticipation;
  private SszUInt64List inactivityScores;
  private SyncCommittee currentSyncCommittee;
  private SyncCommittee nextSyncCommittee;

  private UInt64 depositRequestsStartIndex;
  private UInt64 depositBalanceToConsume;
  private UInt64 exitBalanceToConsume;
  private UInt64 earliestExitEpoch;

  private UInt64 consolidationBalanceToConsume;

  private UInt64 earliestConsolidationEpoch;

  private SszList<PendingDeposit> pendingDeposits;
  private SszList<PendingPartialWithdrawal> pendingPartialWithdrawals;
  private SszList<PendingConsolidation> pendingConsolidations;
  private SszUInt64Vector proposerLookahead;

  private ExecutionPayloadBid latestExecutionPayloadBid;
  private SszList<Builder> builders;
  private UInt64 nextWithdrawalBuilderIndex;
  private SszBitvector executionPayloadAvailability;
  private SszVector<BuilderPendingPayment> builderPendingPayments;
  private SszList<BuilderPendingWithdrawal> builderPendingWithdrawals;
  private Bytes32 latestBlockHash;
  private SszList<Withdrawal> payloadExpectedWithdrawals;
  private SszVector<SszUInt64Vector> ptcWindow;

  protected BeaconStateBuilderGloas(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultBuilderCount,
      final int defaultItemsInSSZLists) {
    super(
        spec,
        dataStructureUtil,
        defaultValidatorCount,
        defaultBuilderCount,
        defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateGloas getEmptyState() {
    return BeaconStateSchemaGloas.create(
            spec.getConfig(), spec.getSchemaDefinitions().getSchemaRegistry())
        .createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateGloas state) {
    state.setPreviousEpochParticipation(previousEpochParticipation);
    state.setCurrentEpochParticipation(currentEpochParticipation);
    state.setInactivityScores(inactivityScores);
    state.setCurrentSyncCommittee(currentSyncCommittee);
    state.setNextSyncCommittee(nextSyncCommittee);
    // `latest_execution_payload_header` has been replaced with `latest_execution_payload_bid`
    state.setLatestExecutionPayloadBid(latestExecutionPayloadBid);
    state.setNextWithdrawalIndex(nextWithdrawalIndex);
    state.setNextWithdrawalValidatorIndex(nextWithdrawalValidatorIndex);
    state.setDepositRequestsStartIndex(depositRequestsStartIndex);
    state.setDepositBalanceToConsume(depositBalanceToConsume);
    state.setExitBalanceToConsume(exitBalanceToConsume);
    state.setEarliestExitEpoch(earliestExitEpoch);
    state.setConsolidationBalanceToConsume(consolidationBalanceToConsume);
    state.setEarliestConsolidationEpoch(earliestConsolidationEpoch);
    state.setPendingDeposits(pendingDeposits);
    state.setPendingPartialWithdrawals(pendingPartialWithdrawals);
    state.setPendingConsolidations(pendingConsolidations);
    state.setProposerLookahead(proposerLookahead);
    state.setBuilders(builders);
    state.setNextWithdrawalBuilderIndex(nextWithdrawalBuilderIndex);
    state.setExecutionPayloadAvailability(executionPayloadAvailability);
    state.setBuilderPendingPayments(builderPendingPayments);
    state.setBuilderPendingWithdrawals(builderPendingWithdrawals);
    state.setLatestBlockHash(latestBlockHash);
    state.setPayloadExpectedWithdrawals(payloadExpectedWithdrawals);
    state.setPtcWindow(ptcWindow);
  }

  public static BeaconStateBuilderGloas create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultBuilderCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderGloas(
        spec.forMilestone(SpecMilestone.GLOAS),
        dataStructureUtil,
        defaultValidatorCount,
        defaultBuilderCount,
        defaultItemsInSSZLists);
  }

  public BeaconStateBuilderGloas nextWithdrawalIndex(final UInt64 nextWithdrawalIndex) {
    checkNotNull(nextWithdrawalIndex);
    this.nextWithdrawalIndex = nextWithdrawalIndex;
    return this;
  }

  public BeaconStateBuilderGloas nextWithdrawalValidatorIndex(
      final UInt64 nextWithdrawalValidatorIndex) {
    checkNotNull(nextWithdrawalValidatorIndex);
    this.nextWithdrawalValidatorIndex = nextWithdrawalValidatorIndex;
    return this;
  }

  public BeaconStateBuilderGloas depositRequestsStartIndex(final UInt64 depositRequestsStartIndex) {
    checkNotNull(depositRequestsStartIndex);
    this.depositRequestsStartIndex = depositRequestsStartIndex;
    return this;
  }

  public BeaconStateBuilderGloas depositBalanceToConsume(final UInt64 depositBalanceToConsume) {
    checkNotNull(depositBalanceToConsume);
    this.depositBalanceToConsume = depositBalanceToConsume;
    return this;
  }

  public BeaconStateBuilderGloas pendingDeposits(final List<PendingDeposit> pendingDeposits) {
    checkNotNull(pendingDeposits);
    this.pendingDeposits =
        getBeaconStateSchema().getPendingDepositsSchema().createFromElements(pendingDeposits);
    return this;
  }

  public BeaconStateBuilderGloas pendingPartialWithdrawals(
      final List<PendingPartialWithdrawal> pendingPartialWithdrawals) {
    checkNotNull(pendingPartialWithdrawals);
    this.pendingPartialWithdrawals =
        getBeaconStateSchema()
            .getPendingPartialWithdrawalsSchema()
            .createFromElements(pendingPartialWithdrawals);
    return this;
  }

  public BeaconStateBuilderGloas pendingConsolidations(
      final List<PendingConsolidation> pendingConsolidations) {
    checkNotNull(pendingConsolidations);
    this.pendingConsolidations =
        getBeaconStateSchema()
            .getPendingConsolidationsSchema()
            .createFromElements(pendingConsolidations);
    return this;
  }

  public BeaconStateBuilderGloas proposerLookahead(final SszUInt64Vector proposerLookahead) {
    checkNotNull(proposerLookahead);
    this.proposerLookahead = proposerLookahead;
    return this;
  }

  public BeaconStateBuilderGloas latestExecutionPayloadBid(
      final ExecutionPayloadBid latestExecutionPayloadBid) {
    checkNotNull(latestExecutionPayloadBid);
    this.latestExecutionPayloadBid = latestExecutionPayloadBid;
    return this;
  }

  public BeaconStateBuilderGloas builders(final SszList<Builder> builders) {
    checkNotNull(builders);
    this.builders = builders;
    return this;
  }

  public BeaconStateBuilderGloas nextWithdrawalBuilderIndex(
      final UInt64 nextWithdrawalBuilderIndex) {
    checkNotNull(nextWithdrawalBuilderIndex);
    this.nextWithdrawalBuilderIndex = nextWithdrawalBuilderIndex;
    return this;
  }

  public BeaconStateBuilderGloas executionPayloadAvailability(
      final SszBitvector executionPayloadAvailability) {
    checkNotNull(executionPayloadAvailability);
    this.executionPayloadAvailability = executionPayloadAvailability;
    return this;
  }

  public BeaconStateBuilderGloas builderPendingPayments(
      final SszVector<BuilderPendingPayment> builderPendingPayments) {
    checkNotNull(builderPendingPayments);
    this.builderPendingPayments = builderPendingPayments;
    return this;
  }

  public BeaconStateBuilderGloas builderPendingWithdrawals(
      final SszList<BuilderPendingWithdrawal> builderPendingWithdrawals) {
    checkNotNull(builderPendingWithdrawals);
    this.builderPendingWithdrawals = builderPendingWithdrawals;
    return this;
  }

  public BeaconStateBuilderGloas latestBlockHash(final Bytes32 latestBlockHash) {
    checkNotNull(latestBlockHash);
    this.latestBlockHash = latestBlockHash;
    return this;
  }

  public BeaconStateBuilderGloas payloadExpectedWithdrawals(
      final SszList<Withdrawal> payloadExpectedWithdrawals) {
    checkNotNull(payloadExpectedWithdrawals);
    this.payloadExpectedWithdrawals = payloadExpectedWithdrawals;
    return this;
  }

  public BeaconStateBuilderGloas ptcWindow(final SszVector<SszUInt64Vector> ptcWindow) {
    checkNotNull(ptcWindow);
    this.ptcWindow = ptcWindow;
    return this;
  }

  private BeaconStateSchemaGloas getBeaconStateSchema() {
    return (BeaconStateSchemaGloas) spec.getSchemaDefinitions().getBeaconStateSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    final BeaconStateSchemaGloas schema = getBeaconStateSchema();

    previousEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getPreviousEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    currentEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getCurrentEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    inactivityScores =
        dataStructureUtil.randomSszUInt64List(
            schema.getInactivityScoresSchema(), defaultItemsInSSZLists);
    currentSyncCommittee = dataStructureUtil.randomSyncCommittee();
    nextSyncCommittee = dataStructureUtil.randomSyncCommittee();

    this.nextWithdrawalIndex = UInt64.ZERO;
    this.nextWithdrawalValidatorIndex =
        defaultValidatorCount > 0
            ? dataStructureUtil.randomUInt64(defaultValidatorCount)
            : UInt64.ZERO;

    this.depositRequestsStartIndex = SpecConfigFulu.UNSET_DEPOSIT_REQUESTS_START_INDEX;
    this.depositBalanceToConsume = UInt64.ZERO;
    this.exitBalanceToConsume = UInt64.ZERO;
    this.earliestExitEpoch = UInt64.ZERO;
    this.consolidationBalanceToConsume = UInt64.ZERO;
    this.earliestConsolidationEpoch = UInt64.ZERO;
    this.pendingDeposits = schema.getPendingDepositsSchema().createFromElements(List.of());
    this.pendingPartialWithdrawals =
        schema.getPendingPartialWithdrawalsSchema().createFromElements(List.of());
    this.pendingConsolidations =
        schema.getPendingConsolidationsSchema().createFromElements(List.of());

    this.proposerLookahead =
        dataStructureUtil.randomSszUInt64Vector(
            schema.getProposerLookaheadSchema(),
            schema.getProposerLookaheadSchema().getMaxLength(),
            () ->
                defaultValidatorCount > 0
                    ? dataStructureUtil.randomUInt64(defaultValidatorCount)
                    : UInt64.ZERO);

    this.latestExecutionPayloadBid = dataStructureUtil.randomExecutionPayloadBid();

    this.builders =
        dataStructureUtil.randomSszList(
            schema.getBuildersSchema(), defaultBuilderCount, dataStructureUtil::randomBuilder);
    this.nextWithdrawalBuilderIndex =
        defaultBuilderCount > 0 ? dataStructureUtil.randomUInt64(defaultBuilderCount) : UInt64.ZERO;
    this.executionPayloadAvailability =
        dataStructureUtil.randomSszBitvector(
            (int) schema.getExecutionPayloadAvailabilitySchema().getMaxLength());
    this.builderPendingPayments =
        schema.getBuilderPendingPaymentsSchema().createFromElements(List.of());
    this.builderPendingWithdrawals =
        schema.getBuilderPendingWithdrawalsSchema().createFromElements(List.of());
    this.latestBlockHash = Bytes32.ZERO;
    this.payloadExpectedWithdrawals =
        schema.getPayloadExpectedWithdrawalsSchema().createFromElements(List.of());
    this.ptcWindow = schema.getPtcWindowSchema().createFromElements(List.of());
  }
}
