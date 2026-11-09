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

package tech.pegasys.teku.statetransition.synccommittee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;
import static tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateAssert.assertThatSyncAggregate;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;

class SyncCommitteeContributionPoolTest {

  private final UInt64 forkEpoch = UInt64.ONE;
  private final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(forkEpoch);
  private final UInt64 forkSlot = spec.computeStartSlotAtEpoch(forkEpoch);
  private final UInt64 altairSlot = forkSlot.plus(2);
  private final SpecConfigAltair config =
      SpecConfigAltair.required(spec.forMilestone(SpecMilestone.ALTAIR).getConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<SignedContributionAndProof> subscriber =
      mock(OperationAddedSubscriber.class);

  private final SignedContributionAndProofValidator validator =
      mock(SignedContributionAndProofValidator.class);

  private final SyncCommitteeContributionPool pool =
      new SyncCommitteeContributionPool(spec, validator);

  @BeforeEach
  void setUp() {
    when(validator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
  }

  @Test
  void shouldNotifySubscribersWhenLocalValidContributionAdded() {
    pool.subscribeOperationAdded(subscriber);

    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof(10);
    addValidLocal(proof);

    verify(subscriber).onOperationAdded(proof, ACCEPT, false);
  }

  @Test
  void shouldNotifySubscribersWhenRemoteValidContributionAdded() {
    pool.subscribeOperationAdded(subscriber);

    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof(10);
    addValidRemote(proof);

    verify(subscriber).onOperationAdded(proof, ACCEPT, true);
  }

  @Test
  void shouldNotNotifySubscribersWhenProofIsInvalid() {
    pool.subscribeOperationAdded(subscriber);

    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof(10);
    when(validator.validate(proof)).thenReturn(SafeFuture.completedFuture(reject("Bad")));
    assertThat(pool.addLocal(proof)).isCompletedWithValue(reject("Bad"));

    verifyNoInteractions(subscriber);
  }

  @Test
  void shouldCreateEmptySyncAggregateWhenPoolIsEmpty() {
    final SyncAggregate result =
        pool.createSyncAggregateForBlock(altairSlot, dataStructureUtil.randomBytes32());

    assertThatSyncAggregate(result).isEmpty();
  }

  @Test
  void shouldCreateEmptySyncAggregateWhenNoContributionsMatchRequiredSlotAndBlockRoot() {
    final SignedContributionAndProof rightSlotWrongBlockRoot =
        dataStructureUtil.randomSignedContributionAndProof(10);
    addValidLocal(rightSlotWrongBlockRoot);
    final SignedContributionAndProof wrongSlotRightBlockRoot =
        dataStructureUtil.randomSignedContributionAndProof(9);

    addValidLocal(rightSlotWrongBlockRoot);
    addValidLocal(wrongSlotRightBlockRoot);

    final SyncAggregate result =
        pool.createSyncAggregateForBlock(
            rightSlotWrongBlockRoot.getMessage().getContribution().getSlot().plus(1),
            wrongSlotRightBlockRoot.getMessage().getContribution().getBeaconBlockRoot());
    assertThatSyncAggregate(result).isEmpty();
  }

  @Test
  void shouldCreateSyncAggregateFromSingleContribution() {
    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof(15);
    addValidLocal(proof);

    final SyncCommitteeContribution contribution = proof.getMessage().getContribution();
    final SyncAggregate result =
        pool.createSyncAggregateForBlock(
            contribution.getSlot().plus(1), contribution.getBeaconBlockRoot());

    assertSyncAggregateFromContribution(contribution, result);
  }

  @Test
  void shouldSelectBestContribution() {
    final SignedContributionAndProof proof = dataStructureUtil.randomSignedContributionAndProof(25);

    final SignedContributionAndProof bestProof = withParticipationBits(proof, 1, 2, 3);
    addValidLocal(withParticipationBits(proof, 1, 3));
    addValidLocal(bestProof);
    addValidLocal(withParticipationBits(proof, 2));

    final SyncCommitteeContribution contribution = bestProof.getMessage().getContribution();
    final SyncAggregate result =
        pool.createSyncAggregateForBlock(
            contribution.getSlot().plus(1), contribution.getBeaconBlockRoot());
    assertSyncAggregateFromContribution(contribution, result);
  }

  @Test
  void shouldCreateSyncAggregateForForkSlot() {
    final SyncAggregate result =
        pool.createSyncAggregateForBlock(forkSlot, dataStructureUtil.randomBytes32());

    assertThatSyncAggregate(result).isEmpty();
  }

  @Test
  void shouldPruneContributions() {
    final SignedContributionAndProof proof1 =
        dataStructureUtil.randomSignedContributionAndProof(11);
    final SignedContributionAndProof proof2 =
        dataStructureUtil.randomSignedContributionAndProof(12);
    final SignedContributionAndProof proof3 =
        dataStructureUtil.randomSignedContributionAndProof(13);
    final SignedContributionAndProof proof4 =
        dataStructureUtil.randomSignedContributionAndProof(14);
    final SignedContributionAndProof proof5 =
        dataStructureUtil.randomSignedContributionAndProof(15);
    addValidLocal(proof1);
    addValidLocal(proof2);
    addValidLocal(proof3);
    addValidLocal(proof4);
    addValidLocal(proof5);

    pool.onSlot(UInt64.valueOf(15));

    // Proof 1 and two were pruned
    assertThatSyncAggregate(getBlockSyncAggregateWithContribution(proof1)).isEmpty();
    assertThatSyncAggregate(getBlockSyncAggregateWithContribution(proof2)).isEmpty();

    // Proof 3 is kept to provide some clock sync tolerance
    assertSyncAggregateFromContribution(
        proof3.getMessage().getContribution(), getBlockSyncAggregateWithContribution(proof3));

    // Proof 4 is kept as its needed for the block at slot 5
    assertSyncAggregateFromContribution(
        proof4.getMessage().getContribution(), getBlockSyncAggregateWithContribution(proof4));

    // Proof 5 is kept as it's needed for the block at slot 6
    assertSyncAggregateFromContribution(
        proof5.getMessage().getContribution(), getBlockSyncAggregateWithContribution(proof5));
  }

  private SyncAggregate getBlockSyncAggregateWithContribution(
      final SignedContributionAndProof proof) {
    final SyncCommitteeContribution contribution = proof.getMessage().getContribution();
    return pool.createSyncAggregateForBlock(
        contribution.getSlot().plus(1), contribution.getBeaconBlockRoot());
  }

  private void addValidLocal(final SignedContributionAndProof proof) {
    assertThat(pool.addLocal(proof)).isCompletedWithValue(ACCEPT);
  }

  private void addValidRemote(final SignedContributionAndProof proof) {
    assertThat(pool.addRemote(proof, Optional.empty())).isCompletedWithValue(ACCEPT);
  }

  private void assertSyncAggregateFromContribution(
      final SyncCommitteeContribution contribution, final SyncAggregate result) {
    final int subcommitteeIndexOffset =
        config.getSyncCommitteeSize()
            / SYNC_COMMITTEE_SUBNET_COUNT
            * contribution.getSubcommitteeIndex().intValue();
    final IntList expectedParticipants =
        IntArrayList.toList(
            contribution
                .getAggregationBits()
                .getAllSetBits()
                .intStream()
                .map(index -> subcommitteeIndexOffset + index));
    assertThatSyncAggregate(result)
        .hasSyncCommitteeBits(expectedParticipants)
        .hasSignature(contribution.getSignature());
  }

  private SignedContributionAndProof withParticipationBits(
      final SignedContributionAndProof proof, final int... participationBits) {
    final SyncCommitteeContribution contribution = proof.getMessage().getContribution();
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(altairSlot);
    final SyncCommitteeContribution newContribution =
        syncCommitteeUtil.createSyncCommitteeContribution(
            contribution.getSlot(),
            contribution.getBeaconBlockRoot(),
            contribution.getSubcommitteeIndex(),
            IntList.of(participationBits),
            contribution.getSignature());

    return syncCommitteeUtil.createSignedContributionAndProof(
        syncCommitteeUtil.createContributionAndProof(
            proof.getMessage().getAggregatorIndex(),
            newContribution,
            proof.getMessage().getSelectionProof()),
        proof.getSignature());
  }
}
