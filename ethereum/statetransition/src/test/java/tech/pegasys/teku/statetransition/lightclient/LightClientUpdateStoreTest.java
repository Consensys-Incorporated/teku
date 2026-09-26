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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.LightClientUpdateChannel;
import tech.pegasys.teku.storage.api.StoredLightClientUpdate;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUpdateStoreTest {

  /** Blocks are on the canonical chain unless a test says otherwise. */
  private static final BiPredicate<UInt64, Bytes32> CANONICAL = (slot, root) -> true;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final LightClientUpdateChannel channel = mock(LightClientUpdateChannel.class);
  private LightClientUpdateStore store;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    when(channel.onNewBestLightClientUpdate(any(), any(), any())).thenReturn(SafeFuture.COMPLETE);
    when(channel.onRemoveBestLightClientUpdates(any())).thenReturn(SafeFuture.COMPLETE);
    when(channel.onPruneBestLightClientUpdatesBefore(any())).thenReturn(SafeFuture.COMPLETE);
    store = new LightClientUpdateStore(spec, channel);
  }

  @TestTemplate
  public void addUpdate_shouldPersistOnlyWhenTheBestUpdateChanges() {
    final LightClientUpdate better =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants()).build();
    final LightClientUpdate worse =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants() - 1)
            .build();
    final Bytes32 betterRoot = dataStructureUtil.randomBytes32();
    final Bytes32 worseRoot = dataStructureUtil.randomBytes32();

    store.addUpdate(better, betterRoot, CANONICAL);
    store.addUpdate(worse, worseRoot, CANONICAL);

    verify(channel).onNewBestLightClientUpdate(UInt64.ONE, better, betterRoot);
    verify(channel, never()).onNewBestLightClientUpdate(UInt64.ONE, worse, worseRoot);
  }

  @TestTemplate
  public void addUpdate_shouldNotPersistAnOrphanedUpdate() {
    store.addUpdate(
        createLightClientUpdateAtPeriod(1),
        dataStructureUtil.randomBytes32(),
        (slot, root) -> false);

    verifyNoInteractions(channel);
  }

  @TestTemplate
  public void pruneUpdatesBefore_shouldPruneStorage() {
    store.pruneUpdatesBefore(UInt64.ONE);

    verify(channel).onPruneBestLightClientUpdatesBefore(UInt64.ONE);
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldRemoveOnlyOrphanedPeriodsFromStorage() {
    final Bytes32 canonicalRoot = dataStructureUtil.randomBytes32();
    store.addUpdate(createLightClientUpdateAtPeriod(1), canonicalRoot, CANONICAL);
    store.addUpdate(
        createLightClientUpdateAtPeriod(2), dataStructureUtil.randomBytes32(), CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.ZERO, (slot, root) -> root.equals(canonicalRoot));

    verify(channel).onRemoveBestLightClientUpdates(List.of(UInt64.valueOf(2)));
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldNotTouchStorageWhenNothingIsOrphaned() {
    store.addUpdate(
        createLightClientUpdateAtPeriod(1), dataStructureUtil.randomBytes32(), CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.ZERO, CANONICAL);

    verify(channel, never()).onRemoveBestLightClientUpdates(any());
  }

  @TestTemplate
  public void loadUpdates_shouldRestoreUpdatesWithoutPersistingThemAgain() {
    final LightClientUpdate update = createLightClientUpdateAtPeriod(1);
    final Bytes32 root = dataStructureUtil.randomBytes32();

    store.loadUpdates(List.of(new StoredLightClientUpdate(UInt64.ONE, update, root)));

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(update);
    verifyNoInteractions(channel);

    store.removeNonCanonicalUpdates(UInt64.ZERO, (slot, blockRoot) -> blockRoot.equals(root));
    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(update);
  }

  @TestTemplate
  public void addUpdate_shouldPreferSupermajority() {
    final LightClientUpdate withSupermajority =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants()).build();
    final LightClientUpdate belowSupermajority =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants() - 1)
            .build();

    assertThat(getBestUpdateAfterAdding(withSupermajority, belowSupermajority))
        .isEqualTo(withSupermajority);
    assertThat(getBestUpdateAfterAdding(belowSupermajority, withSupermajority))
        .isEqualTo(withSupermajority);
  }

  @TestTemplate
  public void addUpdate_shouldPreferMoreParticipantsWhenNeitherHasSupermajority() {
    final LightClientUpdate more =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants() - 1)
            .build();
    final LightClientUpdate fewer =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants() - 2)
            .build();

    assertThat(getBestUpdateAfterAdding(more, fewer)).isEqualTo(more);
    assertThat(getBestUpdateAfterAdding(fewer, more)).isEqualTo(more);
  }

  @TestTemplate
  public void addUpdate_shouldNotCompareParticipationEarlyWhenBothHaveSupermajority() {
    final LightClientUpdate fewerWithSyncCommittee =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants())
            .syncCommitteeBranch(true)
            .build();
    final LightClientUpdate moreWithoutSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(false).build();

    assertThat(getBestUpdateAfterAdding(fewerWithSyncCommittee, moreWithoutSyncCommittee))
        .isEqualTo(fewerWithSyncCommittee);
    assertThat(getBestUpdateAfterAdding(moreWithoutSyncCommittee, fewerWithSyncCommittee))
        .isEqualTo(fewerWithSyncCommittee);
  }

  @TestTemplate
  public void addUpdate_shouldPreferRelevantSyncCommittee() {
    final LightClientUpdate withSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(true).build();
    final LightClientUpdate withoutSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(false).build();

    assertThat(getBestUpdateAfterAdding(withSyncCommittee, withoutSyncCommittee))
        .isEqualTo(withSyncCommittee);
    assertThat(getBestUpdateAfterAdding(withoutSyncCommittee, withSyncCommittee))
        .isEqualTo(withSyncCommittee);
  }

  @TestTemplate
  public void addUpdate_shouldPreferFinality() {
    final LightClientUpdate withFinality = createLightClientUpdate().finalityBranch(true).build();
    final LightClientUpdate withoutFinality =
        createLightClientUpdate().finalityBranch(false).build();

    assertThat(getBestUpdateAfterAdding(withFinality, withoutFinality)).isEqualTo(withFinality);
    assertThat(getBestUpdateAfterAdding(withoutFinality, withFinality)).isEqualTo(withFinality);
  }

  @TestTemplate
  public void addUpdate_shouldPreferSyncCommitteeFinality() {
    final LightClientUpdate finalizedInAttestedPeriod = createLightClientUpdate().build();
    final LightClientUpdate finalizedInEarlierPeriod =
        createLightClientUpdate().finalizedSlot(UInt64.ZERO).build();

    assertThat(getBestUpdateAfterAdding(finalizedInAttestedPeriod, finalizedInEarlierPeriod))
        .isEqualTo(finalizedInAttestedPeriod);
    assertThat(getBestUpdateAfterAdding(finalizedInEarlierPeriod, finalizedInAttestedPeriod))
        .isEqualTo(finalizedInAttestedPeriod);
  }

  @TestTemplate
  public void addUpdate_shouldSkipSyncCommitteeFinalityWhenNeitherHasFinality() {
    final LightClientUpdate moreParticipants =
        createLightClientUpdate().finalityBranch(false).finalizedSlot(UInt64.ZERO).build();
    final LightClientUpdate finalizedInAttestedPeriod =
        createLightClientUpdate()
            .finalityBranch(false)
            .syncCommitteeParticipants(supermajorityParticipants())
            .build();

    assertThat(getBestUpdateAfterAdding(moreParticipants, finalizedInAttestedPeriod))
        .isEqualTo(moreParticipants);
    assertThat(getBestUpdateAfterAdding(finalizedInAttestedPeriod, moreParticipants))
        .isEqualTo(moreParticipants);
  }

  @TestTemplate
  public void addUpdate_shouldPreferMoreParticipantsAsTiebreak() {
    final LightClientUpdate more = createLightClientUpdate().build();
    final LightClientUpdate fewer =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants()).build();

    assertThat(getBestUpdateAfterAdding(more, fewer)).isEqualTo(more);
    assertThat(getBestUpdateAfterAdding(fewer, more)).isEqualTo(more);
  }

  @TestTemplate
  public void addUpdate_shouldPreferOlderAttestedSlot() {
    final LightClientUpdate older = createLightClientUpdate().build();
    final LightClientUpdate newer =
        createLightClientUpdate().attestedSlot(periodOneStartSlot().increment()).build();

    assertThat(getBestUpdateAfterAdding(older, newer)).isEqualTo(older);
    assertThat(getBestUpdateAfterAdding(newer, older)).isEqualTo(older);
  }

  @TestTemplate
  public void addUpdate_shouldPreferOlderSignatureSlot() {
    final LightClientUpdate older = createLightClientUpdate().build();
    final LightClientUpdate newer =
        createLightClientUpdate().signatureSlot(periodOneStartSlot().increment()).build();

    assertThat(getBestUpdateAfterAdding(older, newer)).isEqualTo(older);
    assertThat(getBestUpdateAfterAdding(newer, older)).isEqualTo(older);
  }

  @TestTemplate
  public void addUpdate_shouldKeepExistingUpdateWhenIdentical() {
    // Every clause falls through to `signatureSlot < signatureSlot`, which must be false.
    final LightClientUpdate update = createLightClientUpdate().build();

    store.addUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(update);
  }

  @TestTemplate
  public void addUpdate_shouldRejectUpdateAttestedAndSignedInDifferentPeriods() {
    final LightClientUpdate update =
        createLightClientUpdate().signatureSlot(periodOneStartSlot().times(2)).build();

    store.addUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 3)).isEmpty();
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldReturnEmptyListWhenCountIsZero() {
    store.addUpdate(
        createLightClientUpdateAtPeriod(1), dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 0)).isEmpty();
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldReturnPeriodsInOrderWithinCount() {
    final LightClientUpdate periodOne = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate periodTwo = createLightClientUpdateAtPeriod(2);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);

    store.addUpdate(periodThree, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodOne, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodTwo, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 2)).containsExactly(periodOne, periodTwo);
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldStopAtFirstMissingPeriod() {
    final LightClientUpdate periodOne = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);
    store.addUpdate(periodOne, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodThree, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 500)).containsExactly(periodOne);
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldStartAtEarliestKnownPeriodInRange() {
    final LightClientUpdate periodTwo = createLightClientUpdateAtPeriod(2);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);
    store.addUpdate(periodTwo, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodThree, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 3)).containsExactly(periodTwo, periodThree);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldStoreFirstUpdate() {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot(), periodOneStartSlot());

    store.addFinalityUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getLatestFinalityUpdate()).contains(update);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldKeepHighestAttestedSlotEvenWhenFinalizedSlotIsLower() {
    final LightClientFinalityUpdate older =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot(), periodOneStartSlot().increment());
    final LightClientFinalityUpdate newer =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot().increment(), periodOneStartSlot());

    store.addFinalityUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addFinalityUpdate(newer, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);

    store.addFinalityUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldBreakEqualAttestedSlotBySignatureSlot() {
    final LightClientFinalityUpdate older =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot(), periodOneStartSlot(), periodOneStartSlot());
    final LightClientFinalityUpdate newer =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot(), periodOneStartSlot(), periodOneStartSlot().increment());

    store.addFinalityUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addFinalityUpdate(newer, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);

    store.addFinalityUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldStoreFirstUpdate() {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot());

    store.addOptimisticUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getLatestOptimisticUpdate()).contains(update);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldKeepHighestAttestedSlot() {
    final LightClientOptimisticUpdate older =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot());
    final LightClientOptimisticUpdate newer =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot().increment());

    store.addOptimisticUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addOptimisticUpdate(newer, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);

    store.addOptimisticUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldBreakEqualAttestedSlotBySignatureSlot() {
    final LightClientOptimisticUpdate older =
        dataStructureUtil.randomLightClientOptimisticUpdate(
            periodOneStartSlot(), periodOneStartSlot());
    final LightClientOptimisticUpdate newer =
        dataStructureUtil.randomLightClientOptimisticUpdate(
            periodOneStartSlot(), periodOneStartSlot().increment());

    store.addOptimisticUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addOptimisticUpdate(newer, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);

    store.addOptimisticUpdate(older, dataStructureUtil.randomBytes32(), CANONICAL);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  @TestTemplate
  public void pruneUpdateBefore_shouldDropPeriodsBelowGivenPeriod() {
    final LightClientUpdate periodZero = createLightClientUpdateAtPeriod(0);
    final LightClientUpdate periodOne = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate periodTwo = createLightClientUpdateAtPeriod(2);
    store.addUpdate(periodZero, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodOne, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(periodTwo, dataStructureUtil.randomBytes32(), CANONICAL);

    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 500))
        .containsExactly(periodZero, periodOne, periodTwo);

    store.pruneUpdatesBefore(UInt64.ONE);
    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 500)).containsExactly(periodOne, periodTwo);
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldDropOnlyUpdatesWhoseBlockIsOrphaned() {
    final Bytes32 canonicalRoot = dataStructureUtil.randomBytes32();
    final Bytes32 orphanedRoot = dataStructureUtil.randomBytes32();
    final LightClientUpdate kept = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate dropped = createLightClientUpdateAtPeriod(2);
    store.addUpdate(kept, canonicalRoot, CANONICAL);
    store.addUpdate(dropped, orphanedRoot, CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.ZERO, (slot, root) -> root.equals(canonicalRoot));

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(kept);
    assertThat(store.getBestUpdatesInRange(UInt64.valueOf(2), 1)).isEmpty();
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldTestAgainstTheSignatureSlot() {
    final LightClientUpdate update = createLightClientUpdate().build();
    final Bytes32 root = dataStructureUtil.randomBytes32();
    store.addUpdate(update, root, CANONICAL);

    store.removeNonCanonicalUpdates(
        UInt64.ZERO,
        (slot, blockRoot) ->
            slot.equals(update.getSignatureSlot().get()) && blockRoot.equals(root));

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(update);
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldNotTestPeriodsBelowFromPeriod() {
    final LightClientUpdate finalized = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate unfinalized = createLightClientUpdateAtPeriod(2);
    store.addUpdate(finalized, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addUpdate(unfinalized, dataStructureUtil.randomBytes32(), CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.valueOf(2), (slot, root) -> false);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(finalized);
    assertThat(store.getBestUpdatesInRange(UInt64.valueOf(2), 1)).isEmpty();
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldDropOrphanedFinalityAndOptimisticUpdates() {
    final LightClientFinalityUpdate finalityUpdate =
        dataStructureUtil.randomLightClientFinalityUpdate(periodOneStartSlot());
    final LightClientOptimisticUpdate optimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot());
    store.addFinalityUpdate(finalityUpdate, dataStructureUtil.randomBytes32(), CANONICAL);
    store.addOptimisticUpdate(optimisticUpdate, dataStructureUtil.randomBytes32(), CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.ZERO, (slot, root) -> false);

    assertThat(store.getLatestFinalityUpdate()).isEmpty();
    assertThat(store.getLatestOptimisticUpdate()).isEmpty();
  }

  @TestTemplate
  public void removeNonCanonicalUpdates_shouldKeepFinalityAndOptimisticUpdatesStillOnTheChain() {
    final Bytes32 canonicalRoot = dataStructureUtil.randomBytes32();
    final LightClientFinalityUpdate finalityUpdate =
        dataStructureUtil.randomLightClientFinalityUpdate(periodOneStartSlot());
    final LightClientOptimisticUpdate optimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot());
    store.addFinalityUpdate(finalityUpdate, canonicalRoot, CANONICAL);
    store.addOptimisticUpdate(optimisticUpdate, canonicalRoot, CANONICAL);

    store.removeNonCanonicalUpdates(UInt64.ZERO, (slot, root) -> root.equals(canonicalRoot));

    assertThat(store.getLatestFinalityUpdate()).contains(finalityUpdate);
    assertThat(store.getLatestOptimisticUpdate()).contains(optimisticUpdate);
  }

  @TestTemplate
  public void addUpdate_shouldRejectAnUpdateWhoseBlockIsAlreadyOrphaned() {
    final LightClientUpdate update = createLightClientUpdateAtPeriod(1);

    store.addUpdate(update, dataStructureUtil.randomBytes32(), (slot, root) -> false);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).isEmpty();
  }

  @TestTemplate
  public void addFinalityUpdate_shouldRejectAnUpdateWhoseBlockIsAlreadyOrphaned() {
    final LightClientFinalityUpdate finalityUpdate =
        dataStructureUtil.randomLightClientFinalityUpdate(periodOneStartSlot());

    store.addFinalityUpdate(
        finalityUpdate, dataStructureUtil.randomBytes32(), (slot, root) -> false);

    assertThat(store.getLatestFinalityUpdate()).isEmpty();
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldRejectAnUpdateWhoseBlockIsAlreadyOrphaned() {
    final LightClientOptimisticUpdate optimisticUpdate =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot());

    store.addOptimisticUpdate(
        optimisticUpdate, dataStructureUtil.randomBytes32(), (slot, root) -> false);

    assertThat(store.getLatestOptimisticUpdate()).isEmpty();
  }

  private LightClientUpdate getBestUpdateAfterAdding(
      final LightClientUpdate first, final LightClientUpdate second) {
    final LightClientUpdateStore comparisonStore = new LightClientUpdateStore(spec);
    comparisonStore.addUpdate(first, dataStructureUtil.randomBytes32(), CANONICAL);
    comparisonStore.addUpdate(second, dataStructureUtil.randomBytes32(), CANONICAL);
    return comparisonStore.getBestUpdatesInRange(UInt64.ONE, 1).getFirst();
  }

  private LightClientUpdate createLightClientUpdateAtPeriod(final long period) {
    return dataStructureUtil
        .createRandomLightClientUpdateBuilder(periodOneStartSlot().times(period))
        .build();
  }

  private DataStructureUtil.RandomLightClientUpdateBuilder createLightClientUpdate() {
    return dataStructureUtil.createRandomLightClientUpdateBuilder(periodOneStartSlot());
  }

  private UInt64 periodOneStartSlot() {
    return spec.computeStartSlotAtEpoch(
        spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
            .computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO));
  }

  private int supermajorityParticipants() {
    return (SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize() * 2 + 2)
        / 3;
  }
}
